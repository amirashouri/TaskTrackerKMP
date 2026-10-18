package com.tasktracker.network

import com.tasktracker.core.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class Envelope(val ok: Boolean)

/** A payload whose encoder throws, standing in for a DTO that no longer matches. */
@Serializable
private data class ExplodingBody(
    @Serializable(with = ExplodingSerializer::class) val value: String,
)

private object ExplodingSerializer : kotlinx.serialization.KSerializer<String> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Exploding",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING,
    )

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: String,
    ) = throw kotlinx.serialization.SerializationException("cannot encode this payload")

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String =
        throw kotlinx.serialization.SerializationException("cannot decode this payload")
}

/**
 * The original's `FakeAuthSession`, minus the actor.
 *
 * A [Mutex] rather than an `actor` or a plain counter, because the concurrent test really
 * does run these three calls on `Dispatchers.Default`: `runTest`'s scheduler would
 * serialise them and the test would prove nothing. `NSLock.withLock` has no common
 * equivalent, which is the same reason `StubTransport` uses one.
 */
private class FakeAuthSession(
    private var token: String = "token-v1",
    private var refreshedToken: String = "token-v2",
    private var refreshFails: Boolean = false,
) : AuthSessionDelegate {

    private val lock = Mutex()
    private var refreshes = 0
    private val invalidations = mutableListOf<SessionInvalidationReason>()

    val refreshCount: Int get() = refreshes
    val invalidationReasons: List<SessionInvalidationReason> get() = invalidations.toList()

    override suspend fun validAccessToken(forceRefresh: Boolean): String = lock.withLock {
        if (forceRefresh) {
            refreshes += 1
            if (refreshFails) throw AppError.Unauthorized
            token = refreshedToken
        }
        token
    }

    override suspend fun invalidateSession(reason: SessionInvalidationReason) = lock.withLock {
        invalidations += reason
    }
}

private class FakeConnectivity(override var current: Connectivity) : ConnectivitySource

class KtorHttpClientTest {

    private val baseUrl = "https://api.tasktracker.test"

    private fun client(
        transport: StubTransport,
        auth: FakeAuthSession? = null,
        policy: RetryPolicy = RetryPolicy.None,
        connectivity: ConnectivitySource? = null,
        baseUrl: String = this.baseUrl,
    ) = KtorHttpClient(
        baseUrl = baseUrl,
        engine = transport.engine(),
        interceptors = listOfNotNull(auth?.let { AuthInterceptor(it) }),
        retryPolicy = policy,
        sleeper = { },
        connectivity = connectivity,
    )

    /** Backoff small enough to be instant, but still a real policy rather than none. */
    private val fast = RetryPolicy(maxRetries = 2, baseDelay = 1.milliseconds, maxDelay = 2.milliseconds, usesJitter = false)

    // MARK: Auth flow

    @Test
    fun `attaches the bearer token to authenticated requests`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))
        val auth = FakeAuthSession(token = "abc123")

        val data = client(transport, auth).send(HttpRequest(HttpMethod.Get, "/v1/projects"))

        assertEquals("""{"ok":true}""", data.decodeToString())
        assertEquals("Bearer abc123", transport.recorded.single().authorization)
    }

    @Test
    fun `does not attach a token to the sign-in call itself`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))
        val auth = FakeAuthSession()

        client(transport, auth)
            .send(HttpRequest(HttpMethod.Post, "/v1/auth/login", requiresAuth = false))

        assertNull(transport.recorded.single().authorization)
    }

    @Test
    fun `a 401 refreshes the token and replays the request exactly once`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(
            Stub.json("""{"error":"expired"}""", status = 401),
            Stub.json("""{"ok":true}"""),
        )
        val auth = FakeAuthSession(token = "stale", refreshedToken = "fresh")

        val data = client(transport, auth).send(HttpRequest(HttpMethod.Get, "/v1/projects"))

        assertEquals("""{"ok":true}""", data.decodeToString())
        assertEquals(2, transport.requestCount)
        assertEquals(1, auth.refreshCount)
        // The replay must carry the *new* token, not the stale one — that is the whole
        // reason the adapt chain runs again on every pass.
        assertEquals(
            listOf("Bearer stale", "Bearer fresh"),
            transport.recorded.map { it.authorization },
        )
    }

    @Test
    fun `a second 401 does not loop and it ends the session`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(
            Stub.json("", status = 401),
            Stub.json("", status = 401),
            Stub.json("", status = 401),
        )
        val auth = FakeAuthSession()

        val error = assertFailsWith<AppError.Unauthorized> {
            client(transport, auth).send(HttpRequest(HttpMethod.Get, "/v1/projects"))
        }

        assertEquals(AppError.Unauthorized, error)
        // Two attempts, not three: the replay budget is one, and the third stub is there to
        // make a third request a visible failure rather than a silent success.
        assertEquals(2, transport.requestCount)
        // And it must not burn a second refresh: a 401 after a successful refresh proves
        // the session is dead, so it is invalidated instead.
        assertEquals(1, auth.refreshCount)
        assertEquals(listOf(SessionInvalidationReason.Unauthorized), auth.invalidationReasons)
    }

    @Test
    fun `a 401 whose refresh is rejected ends the session`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub.json("", status = 401), Stub.json("", status = 401))
        val auth = FakeAuthSession(refreshFails = true)

        assertFailsWith<AppError.Unauthorized> {
            client(transport, auth).send(HttpRequest(HttpMethod.Get, "/v1/projects"))
        }
        assertEquals(listOf(SessionInvalidationReason.RefreshRejected), auth.invalidationReasons)
    }

    @Test
    fun `a 403 ends the session immediately and is never retried`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"error":"forbidden"}""", status = 403))
        val auth = FakeAuthSession()

        assertFailsWith<AppError.Forbidden> {
            client(transport, auth).send(HttpRequest(HttpMethod.Get, "/v1/projects"))
        }
        assertEquals(1, transport.requestCount)
        assertEquals(0, auth.refreshCount)
        assertEquals(listOf(SessionInvalidationReason.Forbidden), auth.invalidationReasons)
    }

    @Test
    fun `a 403 on an unauthenticated call does not refresh or invalidate`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"error":"bad credentials"}""", status = 403))
        val auth = FakeAuthSession()

        assertFailsWith<AppError.Forbidden> {
            client(transport, auth)
                .send(HttpRequest(HttpMethod.Post, "/v1/auth/login", requiresAuth = false))
        }
        assertEquals(0, auth.refreshCount)
        assertTrue(auth.invalidationReasons.isEmpty())
    }

    @Test
    fun `concurrent requests that each hit a 401 all recover`() = runTest {
        val transport = StubTransport()
        // Scripted per path, so the interleaving of the three calls cannot matter.
        for (path in listOf("/a", "/b", "/c")) {
            transport.enqueue(Stub.json("", status = 401), forPath = path)
            transport.enqueue(Stub.json("""{"ok":true}"""), forPath = path)
        }
        val auth = FakeAuthSession()
        val http = client(transport, auth)

        val results = withContext(Dispatchers.Default) {
            coroutineScope {
                listOf("/a", "/b", "/c")
                    .map { path -> async { http.send(HttpRequest(HttpMethod.Get, path)) } }
                    .awaitAll()
            }
        }

        assertEquals(3, results.size)
        assertEquals(6, transport.requestCount)
        // This double has no single-flight gate, so each request refreshed for itself.
        // Collapsing concurrent refreshes into one is the real vault's job, covered by the
        // refresh-gate tests in :packages:feature-auth-data.
        assertEquals(3, auth.refreshCount)
        assertTrue(auth.invalidationReasons.isEmpty())
    }

    // MARK: Status mapping and decoding

    @Test
    fun `a 200 decodes`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        val value = client(transport).send(
            HttpRequest(HttpMethod.Get, "/x", requiresAuth = false),
            decoding = serializer<Envelope>(),
        )
        assertTrue(value.ok)
    }

    @Test
    fun `a 409 maps to a conflict which is not a sign-out`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("", status = 409))

        val error = assertFailsWith<AppError.Conflict> {
            client(transport).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        // The server's 409 body is a full sentence, not an entity noun, so it is never
        // surfaced as `entity`; the client owns a correct, generic message instead.
        assertEquals("item", error.entity)
    }

    @Test
    fun `a 400 surfaces the server's validation message`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"error":"email and password are required"}""", status = 400))

        val error = assertFailsWith<AppError.Validation> {
            client(transport).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertEquals("email and password are required", error.detail)
    }

    @Test
    fun `a 500 does not surface the server's internal message`() = runTest {
        val transport = StubTransport()
        transport.enqueue(
            Stub.json(
                """{"error":"pq: duplicate key value violates unique constraint \"projects_pkey\""}""",
                status = 500,
            ),
        )

        val error = assertFailsWith<AppError.Server> {
            client(transport).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertEquals(500, error.statusCode)
        assertTrue(
            !error.message!!.contains("duplicate"),
            "server internals must not reach the user: ${error.message}",
        )
    }

    @Test
    fun `a 429 maps to a friendly rate-limit message`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"error":"too many sign-in attempts"}""", status = 429))

        val error = assertFailsWith<AppError.Unknown> {
            client(transport).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertTrue(error.message!!.contains("wait"), "expected a friendly message, got ${error.message}")
    }

    @Test
    fun `a malformed success body becomes a decoding error`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("not json at all"))

        val error = assertFailsWith<AppError.Decoding> {
            client(transport).send(
                HttpRequest(HttpMethod.Get, "/x", requiresAuth = false),
                decoding = serializer<Envelope>(),
            )
        }
        assertTrue(error.detail.contains("/x"))
    }

    @Test
    fun `a body that does not match the contract becomes a decoding error`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":"yes"}"""))

        assertFailsWith<AppError.Decoding> {
            client(transport).send(
                HttpRequest(HttpMethod.Get, "/x", requiresAuth = false),
                decoding = serializer<Envelope>(),
            )
        }
    }

    @Test
    fun `an encoded body is sent as json`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        client(transport).send(
            HttpRequest(HttpMethod.Post, "/x", requiresAuth = false),
            encoding = serializer<Envelope>(),
            body = Envelope(true),
        )

        assertEquals("application/json", transport.recorded.single().bodyContentType)
    }

    @Test
    fun `a caller's own content type wins over the json default`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        client(transport).send(
            HttpRequest(
                method = HttpMethod.Post,
                path = "/x",
                headers = mapOf("Content-Type" to "text/plain; charset=utf-8"),
                requiresAuth = false,
            ),
            encoding = serializer<Envelope>(),
            body = Envelope(true),
        )

        assertEquals("text/plain; charset=utf-8", transport.recorded.single().bodyContentType)
    }

    @Test
    fun `a content type that is not one is refused before the request`() = runTest {
        val transport = StubTransport()

        val error = assertFailsWith<AppError.Validation> {
            client(transport).send(
                HttpRequest(
                    method = HttpMethod.Post,
                    path = "/x",
                    headers = mapOf("Content-Type" to "application"),
                    requiresAuth = false,
                ),
                encoding = serializer<Envelope>(),
                body = Envelope(true),
            )
        }

        assertTrue(error.message!!.contains("application"))
        assertEquals(0, transport.requestCount)
    }

    @Test
    fun `a body that cannot be encoded is a validation error and never leaves the device`() = runTest {
        val transport = StubTransport()

        assertFailsWith<AppError.Validation> {
            client(transport).send(
                HttpRequest(HttpMethod.Post, "/x", requiresAuth = false),
                encoding = serializer<ExplodingBody>(),
                body = ExplodingBody("anything"),
            )
        }
        // Caught before the request, so the server is never told about a body this build
        // cannot produce — and the caller gets a precise reason instead of a 400.
        assertEquals(0, transport.requestCount)
    }

    @Test
    fun `the url keeps the base path and appends the query`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        client(transport, baseUrl = "https://api.tasktracker.test/v2/").send(
            HttpRequest(
                method = HttpMethod.Get,
                path = "/v1/projects",
                queryItems = listOf(HttpQueryItem("limit", "50"), HttpQueryItem("q", "a&b")),
                requiresAuth = false,
            ),
        )

        val url = transport.recorded.single().url
        // The base URL's own path survives, the request's is appended per segment, and a
        // query value containing an ampersand is escaped rather than splitting the query.
        assertTrue(
            url.startsWith("https://api.tasktracker.test/v2/v1/projects?"),
            "url was $url",
        )
        assertTrue(url.contains("limit=50"), "url was $url")
        assertTrue(url.contains("q=a%26b"), "url was $url")
    }

    @Test
    fun `the post helper encodes the body and decodes the reply`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        val value: Envelope = client(transport).post(
            path = "/v1/projects",
            body = Envelope(true),
            requiresAuth = false,
        )

        assertTrue(value.ok)
        val sent = transport.recorded.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/projects", sent.path)
        assertEquals("application/json", sent.bodyContentType)
    }

    // MARK: Retries

    @Test
    fun `a GET is retried up to the limit on 503`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(
            Stub.json("", status = 503),
            Stub.json("", status = 503),
            Stub.json("", status = 503),
        )

        assertFailsWith<AppError.Server> {
            client(transport, policy = fast).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        // The first attempt plus two retries.
        assertEquals(3, transport.requestCount)
    }

    @Test
    fun `a POST is never replayed because the server may already have applied it`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub.json("", status = 503), Stub.json("""{"ok":true}"""))

        assertFailsWith<AppError.Server> {
            client(transport, policy = fast)
                .send(HttpRequest(HttpMethod.Post, "/x", requiresAuth = false))
        }
        assertEquals(1, transport.requestCount)
    }

    @Test
    fun `a POST that declares itself idempotent is retried`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub.json("", status = 503), Stub.json("""{"ok":true}"""))

        val value = client(transport, policy = fast).send(
            HttpRequest(HttpMethod.Post, "/x", requiresAuth = false, isIdempotent = true),
            decoding = serializer<Envelope>(),
        )
        assertEquals(2, transport.requestCount)
        assertTrue(value.ok)
    }

    @Test
    fun `a retry recovers when the second attempt succeeds`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub.json("", status = 503), Stub.json("""{"ok":true}"""))

        val value = client(transport, policy = fast).send(
            HttpRequest(HttpMethod.Get, "/x", requiresAuth = false),
            decoding = serializer<Envelope>(),
        )
        assertTrue(value.ok)
    }

    @Test
    fun `a 4xx is never retried`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub.json("", status = 404), Stub.json("""{"ok":true}"""))

        assertFailsWith<AppError.NotFound> {
            client(transport, policy = fast).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertEquals(1, transport.requestCount)
    }

    @Test
    fun `the delays handed to the sleeper are the policy's`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(
            Stub.json("", status = 503),
            Stub.json("", status = 503),
            Stub.json("", status = 503),
        )
        val waited = mutableListOf<Duration>()

        assertFailsWith<AppError.Server> {
            KtorHttpClient(
                baseUrl = baseUrl,
                engine = transport.engine(),
                retryPolicy = RetryPolicy(
                    maxRetries = 2,
                    baseDelay = 400.milliseconds,
                    maxDelay = 6.seconds,
                    usesJitter = false,
                ),
                sleeper = { waited += it },
            ).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }

        // Injected rather than really slept: the policy is the thing under test, and a
        // test that waited 1.2s to assert on two numbers would be a slow, flaky copy of
        // RetryPolicyTest.
        assertEquals(listOf(400.milliseconds, 800.milliseconds), waited)
    }

    // MARK: Transport failures

    @Test
    fun `a timeout maps to timeout and is retried while idempotent`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub(failure = stubTimeout()), Stub.json("""{"ok":true}"""))

        val value = client(transport, policy = fast).send(
            HttpRequest(HttpMethod.Get, "/x", requiresAuth = false),
            decoding = serializer<Envelope>(),
        )
        assertTrue(value.ok)
        assertEquals(2, transport.requestCount)
    }

    @Test
    fun `a timeout on a POST is reported and not replayed`() = runTest {
        val transport = StubTransport()
        transport.enqueueAll(Stub(failure = stubTimeout()), Stub.json("""{"ok":true}"""))

        val error = assertFailsWith<AppError.Timeout> {
            client(transport, policy = fast)
                .send(HttpRequest(HttpMethod.Post, "/x", requiresAuth = false))
        }
        assertEquals(AppError.Timeout, error)
        assertEquals(1, transport.requestCount)
    }

    @Test
    fun `a connection failure is transport rather than offline`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub(failure = stubTransportFailure()))

        val error = assertFailsWith<AppError.Transport> {
            client(transport).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertTrue(error.isConnectivityFailure)
    }

    @Test
    fun `a cancelled request is not reported as a network failure`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub(failure = stubCancellation()))

        assertFailsWith<CancellationException> {
            client(transport).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
    }

    @Test
    fun `an unsatisfied path fails immediately as offline`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))
        val connectivity = FakeConnectivity(Connectivity.Unsatisfied)

        val error = assertFailsWith<AppError.Offline> {
            client(transport, connectivity = connectivity)
                .send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertEquals(AppError.Offline, error)
        // No round trip spent: the platform already said there is no route.
        assertEquals(0, transport.requestCount)
    }

    @Test
    fun `a path that needs a login is also offline for the client`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        assertFailsWith<AppError.Offline> {
            client(transport, connectivity = FakeConnectivity(Connectivity.RequiresConnection))
                .send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))
        }
        assertEquals(0, transport.requestCount)
    }

    @Test
    fun `a satisfied path goes out normally`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        client(transport, connectivity = FakeConnectivity(Connectivity.Satisfied))
            .send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))

        assertEquals(1, transport.requestCount)
    }

    @Test
    fun `an unknown path is not treated as offline`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        // `Unknown` means the monitor has not heard from the platform yet. Failing fast on
        // it would park the queue on every cold start.
        client(transport, connectivity = FakeConnectivity(Connectivity.Unknown))
            .send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))

        assertEquals(1, transport.requestCount)
    }
}
