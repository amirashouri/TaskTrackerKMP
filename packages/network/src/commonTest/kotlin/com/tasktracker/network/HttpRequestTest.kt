package com.tasktracker.network

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HttpRequestTest {

    @Test
    fun `only GET PUT and DELETE are idempotent by default`() {
        assertTrue(HttpMethod.Get.isIdempotentByDefault)
        assertTrue(HttpMethod.Put.isIdempotentByDefault)
        assertTrue(HttpMethod.Delete.isIdempotentByDefault)
        assertFalse(HttpMethod.Post.isIdempotentByDefault)
        assertFalse(HttpMethod.Patch.isIdempotentByDefault)
    }

    @Test
    fun `an explicit override wins over the verb`() {
        assertTrue(HttpRequest(HttpMethod.Post, "/x", isIdempotent = true).effectiveIdempotency)
        assertFalse(HttpRequest(HttpMethod.Get, "/x", isIdempotent = false).effectiveIdempotency)
    }

    @Test
    fun `setting a header replaces any existing one case-insensitively`() {
        val request = HttpRequest(
            method = HttpMethod.Get,
            path = "/x",
            headers = mapOf("authorization" to "old"),
        ).setting("Authorization", value = "new")

        // One entry, not two: a replay runs `setting` again, and a second Authorization
        // would leave the server to pick between the stale token and the fresh one.
        assertEquals(1, request.headers.size)
        assertEquals("new", request.header("AUTHORIZATION"))
    }

    @Test
    fun `header lookup ignores case`() {
        val request = HttpRequest(
            method = HttpMethod.Get,
            path = "/x",
            headers = mapOf("X-Trace" to "abc"),
        )
        assertEquals("abc", request.header("x-trace"))
        assertNull(request.header("x-other"))
    }

    @Test
    fun `two requests with the same body are equal`() {
        // The ByteArray trap: a generated `equals` would compare the arrays by identity,
        // so this fails for a client that behaves perfectly and a test that is right.
        val a = HttpRequest(HttpMethod.Post, "/x", body = byteArrayOf(1, 2, 3))
        val b = HttpRequest(HttpMethod.Post, "/x", body = byteArrayOf(1, 2, 3))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `a different body is not equal`() {
        assertTrue(HttpRequest(HttpMethod.Post, "/x", body = byteArrayOf(1)) !=
            HttpRequest(HttpMethod.Post, "/x", body = byteArrayOf(2)))
    }

    @Test
    fun `toString does not print the body`() {
        // A sign-in body is a POST, so a body in a log line is a password in a log line.
        val rendered = HttpRequest(
            method = HttpMethod.Post,
            path = "/v1/auth/login",
            body = """{"password":"hunter2"}""".encodeToByteArray(),
        ).toString()

        assertFalse(rendered.contains("hunter2"))
        assertTrue(rendered.contains("bytes"))
    }

    @Test
    fun `copy changes one field and keeps the rest`() {
        val original = HttpRequest(
            method = HttpMethod.Get,
            path = "/v1/projects",
            queryItems = listOf(HttpQueryItem("limit", "50")),
            headers = mapOf("X-Trace" to "abc"),
            body = byteArrayOf(7),
            requiresAuth = false,
            timeout = 5.seconds,
            isIdempotent = true,
        )

        val copy = original.copy(path = "/v1/work-items")

        assertEquals("/v1/work-items", copy.path)
        assertEquals(original.queryItems, copy.queryItems)
        assertEquals(original.headers, copy.headers)
        assertContentEquals(original.body, copy.body)
        assertEquals(original.requiresAuth, copy.requiresAuth)
        assertEquals(original.timeout, copy.timeout)
        assertEquals(original.isIdempotent, copy.isIdempotent)
    }

    @Test
    fun `the default timeout is thirty seconds`() {
        assertEquals(30.seconds, HttpRequest(HttpMethod.Get, "/x").timeout)
        assertEquals(250.milliseconds, HttpRequest(HttpMethod.Get, "/x", timeout = 250.milliseconds).timeout)
    }
}

class HttpResponseSummaryTest {

    @Test
    fun `response classification matches the status code`() {
        assertTrue(HttpResponseSummary(200).isSuccess)
        assertTrue(HttpResponseSummary(204).isSuccess)
        assertTrue(HttpResponseSummary(401).isUnauthorized)
        assertTrue(HttpResponseSummary(403).isForbidden)
        assertTrue(HttpResponseSummary(503).isServerError)
        assertFalse(HttpResponseSummary(404).isSuccess)
        assertFalse(HttpResponseSummary(500).isUnauthorized)
    }

    @Test
    fun `three hundred is not a success`() {
        // 2xx exactly. A 3xx would otherwise be "not a failure" and reach a decoder that
        // expects the final body.
        assertFalse(HttpResponseSummary(301).isSuccess)
        assertFalse(HttpResponseSummary(199).isSuccess)
    }

    @Test
    fun `toString does not print the body`() {
        val rendered = HttpResponseSummary(500, body = """{"token":"secret"}""".encodeToByteArray())
            .toString()
        assertFalse(rendered.contains("secret"))
    }
}

private class ListProjects : Endpoint {
    override val path = "/v1/projects"
    override val method = HttpMethod.Get
    override val queryItems = listOf(HttpQueryItem("limit", "50"))
}

private class SignIn : Endpoint {
    override val path = "/v1/auth/login"
    override val method = HttpMethod.Post
    override val body = jsonBody(SignInBody("ada@example.com", "hunter2"))
    override val requiresAuth = false

    @Serializable
    data class SignInBody(val email: String, val password: String)
}

class EndpointTest {

    @Test
    fun `an endpoint builds its own request`() {
        val request = ListProjects().request

        assertEquals("/v1/projects", request.path)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(listOf(HttpQueryItem("limit", "50")), request.queryItems)
        assertTrue(request.requiresAuth)
    }

    @Test
    fun `an endpoint that opts out of auth says so`() {
        assertFalse(SignIn().request.requiresAuth)
    }

    @Test
    fun `an endpoint body is the shared encoder's output`() {
        val body = SignIn().request.body!!
        assertEquals(
            JSONCoding.json.encodeToString(SignIn.SignInBody("ada@example.com", "hunter2")),
            body.decodeToString(),
        )
    }

    @Test
    fun `a body that does not fit the contract is null rather than a half-encoded one`() {
        assertNull(jsonBody("not a sign-in body" as Any))
    }

    @Test
    fun `an unknown query item value is encoded rather than dropped`() {
        val request = HttpRequest(
            method = HttpMethod.Get,
            path = "/v1/projects",
            queryItems = listOf(HttpQueryItem("cursor", "a b&c")),
        )
        assertEquals("a b&c", request.queryItems.single().value)
    }
}

class ErrorMappingTest {

    @Test
    fun `four hundred and four twenty two map to validation`() {
        for (status in listOf(400, 422)) {
            val error = HttpErrorMapping.map(status, """{"message":"title is required"}""".encodeToByteArray())
            assertEquals(AppError.Validation("title is required"), error)
        }
    }

    @Test
    fun `four hundred with no message still says something`() {
        val error = HttpErrorMapping.map(400, ByteArray(0))
        assertEquals(AppError.Validation("The server rejected the request."), error)
    }

    @Test
    fun `four hundred and one and three map to the session-ending errors`() {
        assertEquals(AppError.Unauthorized, HttpErrorMapping.map(401, ByteArray(0)))
        assertEquals(AppError.Forbidden, HttpErrorMapping.map(403, ByteArray(0)))
    }

    @Test
    fun `a conflict names the entity and is not a sign-out`() {
        val error = HttpErrorMapping.map(409, """{"message":"work item"}""".encodeToByteArray())
        assertEquals(AppError.Conflict("work item"), error)
        assertFalse(error.requiresSignOut)
    }

    @Test
    fun `an empty conflict still says what kind of thing it was`() {
        assertEquals(AppError.Conflict("record"), HttpErrorMapping.map(409, ByteArray(0)))
    }

    @Test
    fun `not found is its own error and other statuses fall through to unknown`() {
        assertEquals(AppError.NotFound("Not found (404)."), HttpErrorMapping.map(404, ByteArray(0)))
        assertEquals(
            AppError.NotFound("that project is gone"),
            HttpErrorMapping.map(404, """{"message":"that project is gone"}""".encodeToByteArray()),
        )
        assertTrue(HttpErrorMapping.map(418, ByteArray(0)) is AppError.Unknown)
    }

    @Test
    fun `a server error message is taken from the envelope`() {
        val error = HttpErrorMapping.map(500, """{"message":"database is down"}""".encodeToByteArray())
        assertEquals(AppError.Server(500, "database is down"), error)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `the error field is accepted as well as message`() {
        val error = HttpErrorMapping.map(502, """{"error":"upstream is down"}""".encodeToByteArray())
        assertEquals(AppError.Server(502, "upstream is down"), error)
    }

    @Test
    fun `a non-JSON body still produces a readable message`() {
        val error = HttpErrorMapping.map(502, "<html>bad gateway</html>".encodeToByteArray())
        assertEquals(AppError.Server(502, "<html>bad gateway</html>"), error)
    }

    @Test
    fun `an oversized body is dropped rather than shown in a banner`() {
        // A proxy's stack trace is not a message, and the cap is what stops it becoming one.
        val error = HttpErrorMapping.map(502, "x".repeat(500).encodeToByteArray())
        assertEquals(AppError.Server(502, ""), error)
    }

    @Test
    fun `a timeout maps to timeout`() {
        assertEquals(AppError.Timeout, HttpErrorMapping.map(stubTimeout()))
        assertTrue(AppError.Timeout.isConnectivityFailure)
    }

    @Test
    fun `a coroutine timeout is a timeout and not a cancellation`() = runTest {
        // The case that is easy to get wrong: `TimeoutCancellationException` *is* a
        // `CancellationException`, so the rethrow branch below would swallow it as "the
        // caller went away" and a real timeout would escape as a cancellation.
        val thrown = runCatching { withTimeout(1) { delay(60_000) } }.exceptionOrNull()

        assertEquals(AppError.Timeout, HttpErrorMapping.map(thrown!!))
    }

    @Test
    fun `a connection failure is transport and not offline`() {
        val error = HttpErrorMapping.map(stubTransportFailure())
        assertTrue(error is AppError.Transport)
        // The distinction is the point: `.offline` shows a diagnosis, `.transport` shows a
        // "try again" button. Guessing offline here would be wrong on OkHttp, where a DNS
        // failure and a dead radio raise the same exception.
        assertFalse(error is AppError.Offline)
        assertTrue(error.isConnectivityFailure)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `an AppError thrown from below is not re-wrapped`() {
        val original = AppError.Conflict("project")
        assertEquals(original, HttpErrorMapping.map(original))
    }

    @Test
    fun `cancellation is rethrown rather than mapped`() {
        // A screen dismissed mid-request must not come back as a network failure; the sync
        // engine would log a failed attempt for work the user navigated away from.
        assertFailsWith<CancellationException> {
            HttpErrorMapping.map(stubCancellation())
        }
    }
}
