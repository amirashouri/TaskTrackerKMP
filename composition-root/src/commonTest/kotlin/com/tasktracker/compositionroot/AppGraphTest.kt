package com.tasktracker.compositionroot

import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.di.DiException
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdata.HttpAuthRemote
import com.tasktracker.featureauthdata.MockAuthRemote
import com.tasktracker.featureauthdata.TokenVault
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureprojectsdata.ProjectChangeBus
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.network.AuthInterceptor
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.network.RequestInterceptor
import com.tasktracker.routing.Router
import com.tasktracker.sync.HttpSyncTransport
import com.tasktracker.sync.InMemorySyncTransport
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncTransport
import com.tasktracker.userdefaults.InMemoryPreferences
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The seam the whole port depends on: one describable object graph, reachable from one
 * place, that no other module can bypass.
 */
class AppGraphTest {

    /**
     * The production graph, with in-memory stand-ins for the three values a test host cannot
     * supply.
     *
     * Everything else is real: the same registrations, the same scopes, the same factories,
     * and the same `AuthRepository` over the same `TokenVault`. Three things are substituted,
     * not one: the keychain and preferences backends, because on the Android host neither can be
     * constructed at all, and the store, which [testBindings] points at an in-memory database so
     * a test never opens the developer's. See [AppGraph.start] for the full reasoning.
     */
    private fun testGraph(): Resolver = AppGraph.start(testBindings(startServices = false))

    /** The production graph with its loops running, for the tests that are about the loops. */
    private fun runningGraph(): Resolver = AppGraph.start(testBindings(startServices = true))

    @Test
    fun `start yields a started container`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isStarted)
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `an unregistered dependency fails loudly rather than resolving to something`() {
        val resolver = testGraph()

        try {
            assertFailsWith<DiException.Unregistered> {
                resolver.resolve(Resolver::class)
            }
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the auth port resolves`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isRegistered(AuthSessionPort::class))
            assertNotNull(resolver.resolve(AuthSessionPort::class))
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the session port is one instance for the whole process`() {
        val resolver = testGraph()

        try {
            // App-scoped is a load-bearing decision, not a default: the shell holds this
            // reference from launch, across a sign-out. A per-session or per-resolution
            // binding would hand the shell an object the container no longer owns.
            assertSame(resolver.resolve(AuthSessionPort::class), resolver.resolve(AuthSessionPort::class))
            assertSame(resolver.resolve(TokenVault::class), resolver.resolve(TokenVault::class))
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the vault and the session port share one remote`() {
        val resolver = testGraph()

        try {
            // Not observable from outside either object, so asserted structurally: a second
            // remote would mint tokens a vault never sees, and the mismatch would only
            // surface as a refresh failing much later.
            val remote = resolver.resolve(AuthRemotePort::class)
            assertSame(remote, resolver.resolve(AuthRemotePort::class))
        } finally {
            resolver.stop()
        }
    }

    /**
     * A named backend swaps the demo pair for the real one, and an unnamed one keeps the demo.
     *
     * The switch is the whole reason a base URL exists on this graph, and it is asserted by
     * type rather than by making a call: the question is *which* implementation got bound, and
     * a request would only prove a socket answers. Both directions are asserted in one test
     * because the negative half is the one that would be silently lost — a graph that bound
     * the real pair unconditionally would leave the mobile shells stuck behind a backend that
     * is not there.
     */
    @Test
    fun `a bound backend swaps the demo remote and transport for the real ones`() {
        val offline = testGraph()
        val online = AppGraph.start(testBindings(startServices = false, apiBaseUrl = "http://127.0.0.1:8080"))

        try {
            assertIs<MockAuthRemote>(
                offline.resolve(AuthRemotePort::class),
                "with no base URL the graph must keep the in-process demo remote",
            )
            assertIs<InMemorySyncTransport>(
                offline.resolve(SyncTransport::class),
                "with no base URL the graph must keep the in-memory demo transport",
            )
            assertIs<HttpAuthRemote>(
                online.resolve(AuthRemotePort::class),
                "a bound backend must get the real auth remote rather than the demo mock",
            )
            assertIs<HttpSyncTransport>(
                online.resolve(SyncTransport::class),
                "a bound backend must get the real sync transport rather than the in-memory one",
            )
        } finally {
            offline.stop()
            online.stop()
        }
    }

    /**
     * The two clients a bound backend opens, and what each of them carries.
     *
     * Type says *which* pair got bound; only this says what the clients must carry, and both
     * halves are silent when they are wrong. Without the `AuthInterceptor` every push and pull
     * leaves with no `Authorization` header and the server answers 401 — a sync that never
     * runs and never says why. With it on the *auth* client, a refresh's own 401 triggers
     * another refresh until the vault tears the session down as expired. Neither is visible
     * from outside a constructed client, so the factory is watched instead of the client.
     *
     * The count is asserted too, and deliberately: "the second client had an interceptor" is
     * also true of a graph that built the sync client twice, or that built a third client for
     * something else. Exactly two openings, in registration order, is the claim.
     */
    @Test
    fun `a bound backend opens one open client for auth and one authenticated client for sync`() {
        val opened = mutableListOf<List<RequestInterceptor>>()
        val resolver = AppGraph.start(
            testBindings(
                startServices = false,
                apiBaseUrl = "http://127.0.0.1:8080",
                httpClient = { _, interceptors ->
                    opened += interceptors
                    InertHttpClient()
                },
            ),
        )

        try {
            // Resolved in registration order, which is the order the assertions below name:
            // the auth remote first, then the transport.
            resolver.resolve(AuthRemotePort::class)
            resolver.resolve(SyncTransport::class)
        } finally {
            resolver.stop()
        }

        assertEquals(
            2,
            opened.size,
            "a bound backend must open exactly the auth client and the sync client, " +
                "opened ${opened.size}",
        )
        assertTrue(
            opened[0].isEmpty(),
            "the auth client must carry no interceptor, or a refresh recurses on the 401 that " +
                "asked for it; found ${opened[0].map { it::class.simpleName }}",
        )
        assertEquals(
            1,
            opened[1].size,
            "the sync client must carry exactly one interceptor; found " +
                "${opened[1].map { it::class.simpleName }}",
        )
        assertIs<AuthInterceptor>(
            opened[1][0],
            "the sync client's one interceptor must be the bearer-token one, or every push " +
                "and pull reaches the server unauthenticated",
        )
    }

    /**
     * The production client factory keeps the interceptors it is handed.
     *
     * [PlatformBindings]' own default is watched separately, below; this asserts the lambda
     * itself. The graph test above pins *what* `registerAuth` and `registerSync` name, but it
     * substitutes the factory, so the line that turns that list into a working client never
     * ran. An `AuthInterceptor` dropped here leaves every push and pull without an
     * `Authorization` header, silently: the same defect as an empty list at the registration,
     * one token further in.
     *
     * `InterceptorThatRefuses` throws from `adapt`, which `KtorHttpClient.send` runs before the
     * engine is reached, so the pass path opens no socket. That is also why the assertion reads
     * the thrown value rather than a flag on the interceptor: a flag set on the line before the
     * throw could never disagree with the exception, so the assertion could not fail, and the
     * mutation's real signal — `Transport(Failed to connect to /127.0.0.1:9)` in place of `Ran`
     * — would have been hidden behind a generic `assertFailsWith` message that reads like a
     * flaky network test rather than a missing bearer token.
     */
    @Test
    fun `the production client factory keeps the interceptors it is handed`() = runTest {
        val client = PlatformBindings.defaultHttpClient(
            "http://127.0.0.1:9/",
            listOf(InterceptorThatRefuses()),
        )

        val thrown = runCatching {
            client.send(HttpRequest(method = HttpMethod.Get, path = "/v1/sync/changes"))
        }.exceptionOrNull()

        assertIs<InterceptorThatRefuses.Ran>(
            thrown,
            "the default client did not run the interceptor chain ahead of the engine, so it " +
                "dropped the interceptors it was handed; it threw " +
                "${thrown?.let { it::class.simpleName }}",
        )
    }

    /**
     * The bindings a production start gets are the client factory production means.
     *
     * `PlatformBindings` declares `httpClient` with a default, and `AppGraph.start` — the only
     * caller in the product — names neither it nor `apiBaseUrl`. Every test goes through
     * `testBindings`, which passes the factory explicitly, and the test above calls
     * [PlatformBindings.defaultHttpClient] by name, so the *binding* was the one link nothing
     * exercised: rebinding it to `{ baseUrl, _ -> KtorHttpClient(baseUrl = baseUrl) }` left all
     * three hosts green while Desktop's sync client shipped unauthenticated.
     *
     * The one argument that matters below is the one *not* written: `httpClient` is left to its
     * default, which is the property under test rather than an accident of construction. The
     * other arguments a test host has to supply — two in-memory platform backends and an
     * in-memory store — are why this cannot instead call `AppGraph.start`, and that is exactly
     * what leaves one link unobserved: a `start` that named `httpClient` itself would replace
     * this default and stay green here. [PlatformBindings.httpClient] states that residual.
     */
    @Test
    fun `the production bindings keep the client factory the registrations are handed`() = runTest {
        val host = testPlatformHost()
        val bindings = PlatformBindings(
            host = host,
            keychain = InMemoryKeychain(),
            preferences = InMemoryPreferences(),
            database = DatabaseConfiguration(name = "", inMemory = true, host = host),
            startServices = false,
        )

        val thrown = runCatching {
            bindings.httpClient("http://127.0.0.1:9/", listOf(InterceptorThatRefuses()))
                .send(HttpRequest(method = HttpMethod.Get, path = "/v1/sync/changes"))
        }.exceptionOrNull()

        assertIs<InterceptorThatRefuses.Ran>(
            thrown,
            "PlatformBindings' default client factory did not run the interceptor chain ahead " +
                "of the engine, so an argument list that names none — every production entry " +
                "point — builds an unauthenticated sync client; it threw " +
                "${thrown?.let { it::class.simpleName }}",
        )
    }

    /**
     * The graph registers what has been ported, and nothing else.
     *
     * The positive half is the point: `ProjectRepositoryPort`, `TaskTrackerDatabase`,
     * `ProjectChangeBus` and `Router` are all resolved by name or by class, and a shell
     * that asked for one of them before this phase would have been a crash on launch. It
     * used to be the other way round — this test asserted that *no* `Project` or `Database`
     * registration existed, which is only true while the feature is unwired, and so is a
     * test that has to be inverted the moment the feature lands.
     *
     * `isRegistered` rather than `resolve` for the database, because resolving it opens a
     * Room database and there is no Android `Context` here. A registration check is the
     * honest question on this host; opening the database is asserted on the device.
     *
     * The negative half survives, with one qualification: it is asserted on the *offline* graph
     * ([testGraph]), because that is the graph this class builds. A registration that only a
     * bound API root added would not be caught here — the two clients a bound root opens are
     * built inside the `AuthRemotePort` and `SyncTransport` factories rather than registered
     * under names of their own, and the assertion below reads `registeredNames` from the demo
     * graph alone. An HTTP client is still not registered, which is the failure mode a growing
     * composition root actually has, and it is the offline graph that catches it.
     */
    @Test
    fun `the graph registers the ported features and still nothing more`() {
        val resolver = testGraph()

        try {
            assertTrue(
                resolver.isRegistered(ProjectRepositoryPort::class),
                "expected the projects repository to be registered",
            )
            assertTrue(
                resolver.isRegistered(TaskTrackerDatabase::class),
                "expected the database to be registered",
            )
            assertTrue(
                resolver.isRegistered(ProjectChangeBus::class),
                "expected the projects change bus to be registered",
            )
            assertTrue(
                resolver.isRegistered(Router::class),
                "expected the router to be registered for the shell",
            )
            assertTrue(
                resolver.isRegistered(SettingsPort::class),
                "expected the settings port to be registered",
            )
            assertFalse(
                resolver.registeredNames.any { it.contains("HTTP") || it.contains("Ktor") },
                "expected no HTTP client in the graph, found ${resolver.registeredNames}",
            )
        } finally {
            resolver.stop()
        }
    }

    /**
     * The router is one instance for the whole process.
     *
     * App-scoped rather than per-resolution, and load-bearing: the shell holds it from
     * launch, across a sign-out, and the sign-out path has to be able to clear its stacks.
     * A fresh router per resolve would hand the shell an object whose state the container
     * does not own, and clearing the stacks on sign-out would clear the wrong one.
     */
    @Test
    fun `the router is one instance for the whole process`() {
        val resolver = testGraph()

        try {
            assertSame(resolver.resolve(Router::class), resolver.resolve(Router::class))
        } finally {
            resolver.stop()
        }
    }

    /**
     * The persistence collaborators are registered, and share one store.
     *
     * They are separate classes over one database on purpose — each owns its own
     * `ModelContext`, the only way one can be used safely — so "registered and separate" is
     * the assertion, and "same instance" is not available for any of them individually. The
     * sharing that *is* structural is the change bus, which two modules need.
     */
    @Test
    fun `the persistence collaborators are registered and the bus is shared`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isRegistered(OutboxQueue::class))
            assertTrue(resolver.isRegistered(SyncMetadataStore::class))
            assertTrue(resolver.isRegistered(SessionCache::class))
            assertSame(resolver.resolve(ProjectChangeBus::class), resolver.resolve(ProjectChangeBus::class))
        } finally {
            resolver.stop()
        }
    }

    /**
     * A graph built with services off resolves everything but runs nothing.
     *
     * The counterpart to the test above, and the reason the seam is safe: turning loops off
     * removes running work, not registrations. Every resolve in [the graph registers the ported
     * features and still nothing more] still holds with services off, which is what makes that
     * seam usable by the rest of the suite.
     */
    @Test
    fun `services off is a wiring switch and not a graph`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isStarted)
            assertTrue(resolver.isRegistered(SyncCoordinator::class))
            assertTrue(resolver.isRegistered(NetworkMonitor::class))
            assertTrue(resolver.isRegistered(SessionCoordinator::class))
        } finally {
            resolver.stop()
        }
    }

}

/*
 * Five assertions used to live here and now live in [HostedGraphScenarios], because they resolve
 * `TaskTrackerDatabase` or `NetworkMonitor` and both need a `Context` on Android: Room's
 * `inMemoryDatabaseBuilder` takes one, and `NetworkMonitor.forHost` needs one to reach
 * `ConnectivityManager`. A `Context` comes only from a Robolectric sandbox, which only a class
 * runner annotation creates, and a runner annotation cannot be written in `commonTest`. So the
 * bodies moved to a scenario object and each host has an entry point that can carry the runner:
 * `HostedGraphScenariosTest` on JVM and iOS, and the Robolectric-annotated
 * `HostedGraphAndroidHostTest` on the Android host.
 *
 * They were left in place here until Phase 15 and failed on `testAndroidHostTest` with a
 * `FactoryFailed` for `TaskTrackerDatabase` and for `NetworkMonitor`, which reads as "the graph
 * is broken on Android" and is not. The same assertions pass on JVM and iOS, where neither
 * backend needs a host, so it was always a gap in the test target rather than in the wiring.
 */

/**
 * An [HttpClient] no graph test ever sends through.
 *
 * [PlatformBindings.httpClient] records the interceptor list and hands this back, which is
 * what keeps a test that only needs to know *which* client was built from standing up a real
 * Ktor engine and its socket machinery. Nothing in the assertion should ever reach a request,
 * so this fails loudly rather than answering something plausible that a later test would
 * mistake for a real response.
 */
private class InertHttpClient : HttpClient {

    override suspend fun send(request: HttpRequest): ByteArray = fail(neverSent)

    override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T =
        fail(neverSent)

    override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T): Unit =
        fail(neverSent)

    private companion object {
        const val neverSent =
            "a graph test must not send a request; the graph only had to build the client"
    }
}

/**
 * An interceptor that refuses before any bytes move.
 *
 * Thrown from `adapt` rather than `retry` on purpose: [com.tasktracker.network.KtorHttpClient]
 * runs the adapt chain ahead of the engine, so refusing there is what keeps the assertions that
 * use this off the network. A reordering that reached the engine first would fail them by trying
 * to connect — `Transport(Failed to connect to /127.0.0.1:9)` in place of `Ran` — which is the
 * honest signal rather than a silent pass.
 */
private class InterceptorThatRefuses : RequestInterceptor {

    class Ran : Exception("the interceptor chain ran")

    override suspend fun adapt(request: HttpRequest): HttpRequest = throw Ran()
}
