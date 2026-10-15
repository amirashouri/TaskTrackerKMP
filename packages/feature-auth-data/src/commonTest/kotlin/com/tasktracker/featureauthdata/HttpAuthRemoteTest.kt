package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A sign-in body, decoded without reaching into the data layer's private shape. */
@Serializable
private data class SignInRequestBodyFixture(
    val email: String,
    val password: String,
)

/**
 * The real remote's wire shape.
 *
 * `AuthRemotePort` is otherwise only ever exercised against `ScriptedAuthRemote` and
 * `MockAuthRemote`, so before this file the one type that actually performs a sign-in had no
 * coverage at all — and the properties worth checking are invisible in a response. A stub
 * answering with a valid token envelope proves nothing if the request that produced it went
 * out with `requiresAuth = true` (it would recurse through `AuthInterceptor`) or without
 * `isIdempotent = true` (a dropped connection would read as a failed sign-in, because `POST`
 * is not replayable by default).
 */
class HttpAuthRemoteTest {

    private val envelope: ByteArray = """
        {
          "access_token": "access-1",
          "refresh_token": "refresh-1",
          "user_id": "3F2504E0-4F89-41D3-9A0C-0305E82C3301",
          "email": "ada@example.com",
          "display_name": "Ada"
        }
    """.trimIndent().encodeToByteArray()

    private fun remote(client: StubHttpClient) = HttpAuthRemote(client)

    @Test
    fun signInPostsToTheLoginPath() = runTest {
        val client = StubHttpClient(envelope)
        remote(client).signIn(email = "ada@example.com", password = "hunter2")

        val request = assertNotNull(client.requests.firstOrNull())
        assertEquals("/v1/auth/login", request.path)
        assertEquals(HttpMethod.Post, request.method)
    }

    /**
     * The one that would loop. A login that carried a bearer token would be refreshed by
     * `AuthInterceptor` on its own `401`, refresh, `401` again, until the client's single
     * replay ran out and the session was torn down — a sign-in reporting "session expired".
     */
    @Test
    fun signInDoesNotAskToBeAuthenticated() = runTest {
        val client = StubHttpClient(envelope)
        remote(client).signIn(email = "ada@example.com", password = "hunter2")

        assertEquals(false, assertNotNull(client.requests.first()).requiresAuth)
    }

    @Test
    fun refreshDoesNotAskToBeAuthenticatedEither() = runTest {
        val client = StubHttpClient(envelope)
        remote(client).refresh(refreshToken = "refresh-1")

        // The server is being asked for a replacement precisely because the old token is not
        // good enough, so the replacement must not carry it.
        assertEquals(false, assertNotNull(client.requests.first()).requiresAuth)
    }

    /**
     * A retried login cannot half-apply, and a retried refresh returns either the same
     * rotated pair or a fresh one — never two live sessions, because the server invalidates
     * the token it just consumed. Both opt in explicitly, since `POST` is not replayable by
     * default.
     */
    @Test
    fun bothAuthCallsDeclareThemselvesSafeToReplay() = runTest {
        val client = StubHttpClient(envelope)
        remote(client).signIn(email = "ada@example.com", password = "hunter2")
        remote(client).refresh(refreshToken = "refresh-1")

        assertEquals(2, client.requests.size)
        assertTrue(client.requests.all { it.effectiveIdempotency })
    }

    @Test
    fun signInSendsTheAddressAndPasswordAsJson() = runTest {
        val client = StubHttpClient(envelope)
        remote(client).signIn(email = "ada@example.com", password = "hunter2")

        val body = assertNotNull(assertNotNull(client.requests.first()).body)
        val decoded = JSONCoding.json.decodeFromString(
            SignInRequestBodyFixture.serializer(),
            body.decodeToString(),
        )
        assertEquals("ada@example.com", decoded.email)
        assertEquals("hunter2", decoded.password)
    }

    /**
     * The snake_case key is the whole reason `RefreshRequestBody` carries a `@SerialName`,
     * and a decoder with the wrong strategy would fail at runtime against a real server only.
     */
    @Test
    fun refreshSendsTheTokenUnderTheServersSnakeCaseKey() = runTest {
        val client = StubHttpClient(envelope)
        remote(client).refresh(refreshToken = "refresh-1")

        val request = assertNotNull(client.requests.first())
        assertEquals("/v1/auth/refresh", request.path)
        val object_ = JSONCoding.json.parseToJsonElement(
            assertNotNull(request.body).decodeToString(),
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("refresh-1", object_["refresh_token"].toString().trim('"'))
        assertNull(
            object_["refreshToken"],
            "the camelCase spelling must not reach the wire",
        )
    }

    @Test
    fun aTokenEnvelopeDecodesIntoTokens() = runTest {
        val client = StubHttpClient(envelope)

        val tokens = remote(client).signIn(email = "ada@example.com", password = "hunter2")

        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
    }

    /**
     * The envelope's identity fields are decoded and then dropped. A server that identifies
     * an account only inside the token must not produce a second copy of the profile, and a
     * test is the only place that can hold the port to that.
     */
    @Test
    fun anEnvelopeWithoutIdentityFieldsStillDecodes() = runTest {
        val client = StubHttpClient(
            """{"access_token":"a","refresh_token":"r"}""".encodeToByteArray(),
        )

        val tokens = remote(client).signIn(email = "ada@example.com", password = "hunter2")

        assertEquals("a", tokens.accessToken)
        assertEquals("r", tokens.refreshToken)
    }

    @Test
    fun anEnvelopeMissingTheTokensIsADecodingFailure() = runTest {
        val client = StubHttpClient("""{"email":"ada@example.com"}""".encodeToByteArray())

        // A 200 with no credentials is a server bug, and the only honest answer is a failure
        // rather than a session with an empty access token.
        assertFailsWith<AppError.Decoding> {
            remote(client).signIn(email = "ada@example.com", password = "hunter2")
        }
    }

    /**
     * The client's half of the rule that a response may not gain a field.
     *
     * `JSONCoding` leaves `ignoreUnknownKeys` off on purpose, so a key this DTO does not
     * declare is a decoding failure rather than a value it skips. That is what makes the
     * server's "a response may not gain a field" a requirement rather than a style note —
     * and it is why the envelope below, which is the five keys the client declares plus the
     * two the server used to add, broke every sign-in until those two were removed.
     *
     * The strictness is asserted from this side because this is the side that owns it. A
     * server test cannot see it, and the contract test over there only holds while its
     * transcription of this DTO is faithful — which, for `token_type` and `expires_in`, it
     * was not.
     */
    @Test
    fun anUndeclaredKeyInTheEnvelopeIsADecodingFailure() = runTest {
        val client = StubHttpClient(
            """
            {
              "access_token": "access-1",
              "refresh_token": "refresh-1",
              "token_type": "Bearer",
              "expires_in": 3600
            }
            """.trimIndent().encodeToByteArray(),
        )

        assertFailsWith<AppError.Decoding> {
            remote(client).signIn(email = "ada@example.com", password = "hunter2")
        }
    }
}
