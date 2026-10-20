package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The request the probe sends and what it makes of the answer.
 *
 * The platform engine is not involved: [HttpServerAddressProbe]'s seam is the client, so the
 * path, the missing auth header and the exact-body rule are pinned here on every host. The
 * production wrapper ([KtorProbeClient]) is deliberately thin — construct, delegate, close —
 * and is exercised by the same `HttpClient` tests the rest of the network module uses.
 */
class HttpServerAddressProbeTest {

    private class FakeProbeClient(
        private val respond: suspend (HttpRequest) -> ByteArray,
    ) : ProbeClient {
        val requests = mutableListOf<HttpRequest>()
        var closed = false

        override suspend fun send(request: HttpRequest): ByteArray {
            requests += request
            return respond(request)
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun theProbeSendsAnUnauthenticatedHealthRequest() = runTest {
        val client = FakeProbeClient { "ok".encodeToByteArray() }
        val openedFor = mutableListOf<String>()
        val probe = HttpServerAddressProbe { url ->
            openedFor += url
            client
        }

        assertTrue(probe.answersOk("https://example.com"))

        assertEquals(listOf("https://example.com"), openedFor)
        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/healthz", request.path)
        assertFalse(request.requiresAuth, "a pairing probe has no session to authenticate with")
        assertEquals(HttpServerAddressProbe.PROBE_TIMEOUT, request.timeout)
        assertTrue(client.closed)
    }

    @Test
    fun aBodyOfOkIsAcceptedAfterTrimming() = runTest {
        val client = FakeProbeClient { " ok\n".encodeToByteArray() }

        assertTrue(HttpServerAddressProbe { client }.answersOk("https://example.com"))
    }

    @Test
    fun anyOtherBodyIsNotAnOk() = runTest {
        val bodies = listOf("", "OK", "down", "okay", "{\"status\":\"ok\"}")

        for (body in bodies) {
            val client = FakeProbeClient { body.encodeToByteArray() }

            assertFalse(
                HttpServerAddressProbe { client }.answersOk("https://example.com"),
                "expected body ${body.ifEmpty { "<empty>" }} to be rejected",
            )
            assertTrue(client.closed)
        }
    }

    @Test
    fun aTransportFailureIsNotOk() = runTest {
        val client = FakeProbeClient { throw AppError.Transport("connection refused") }

        assertFalse(HttpServerAddressProbe { client }.answersOk("https://example.com"))
        assertTrue(client.closed)
    }

    @Test
    fun aServerErrorIsNotOk() = runTest {
        // A 404 from a server that is not TaskTracker, or a 500 from a broken one, is still
        // "no usable server at this address" — the client maps both to AppError.
        val client = FakeProbeClient { throw AppError.NotFound("not found") }

        assertFalse(HttpServerAddressProbe { client }.answersOk("https://example.com"))
        assertTrue(client.closed)
    }

    @Test
    fun cancellationIsNotAFailedProbe() = runTest {
        val client = FakeProbeClient { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            HttpServerAddressProbe { client }.answersOk("https://example.com")
        }
        assertTrue(client.closed)
    }
}
