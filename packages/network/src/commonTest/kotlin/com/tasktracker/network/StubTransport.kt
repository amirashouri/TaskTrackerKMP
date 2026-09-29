package com.tasktracker.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One canned answer. */
internal data class Stub(
    val statusCode: Int = 200,
    val body: String = "",
    /** Thrown instead of answered, to simulate a transport failure. */
    val failure: Throwable? = null,
) {
    companion object {
        fun json(body: String, status: Int = 200) = Stub(statusCode = status, body = body)
    }
}

/**
 * The KMP stand-in for the original's `StubURLProtocol`.
 *
 * Same shape as the original on purpose, because two of its decisions are the interesting
 * part and would be easy to get wrong on a second look:
 *
 * 1. **A per-path queue, not one flat queue.** The original's comment says a flat FIFO is
 *    unusable for the concurrent test: three interleaved requests consume each other's
 *    stubs, so one request's "401 then 200" becomes another's "401 then 401". Keying by
 *    path keeps each logical request's script independent of scheduling, and that is what
 *    lets the concurrency test assert *three* refreshes rather than one.
 * 2. **A request past the end of a script gets a 500.** An unexpected extra call should
 *    show up as a failure, not as a free pass — a stub answering 200 forever would let a
 *    replay loop pass its own test.
 *
 * A [Mutex] rather than a lock object because this is common code: `synchronized` does not
 * exist on Kotlin/Native, and the original's `NSLock.withLock` has no common equivalent.
 */
internal class StubTransport {

    private val lock = Mutex()
    private val scripts = mutableMapOf<String, ArrayDeque<Stub>>()
    private val fallback = ArrayDeque<Stub>()
    private val recordedInternal = mutableListOf<Recorded>()

    data class Recorded(
        val method: String,
        val path: String,
        val url: String,
        val authorization: String?,
        val contentType: String?,
        /**
         * The body content type, which is where Ktor 3 carries it.
         *
         * `HttpRequestData.headers` holds the headers the *caller* set; a real engine adds
         * `Content-Type` to the wire from `OutgoingContent.contentType` afterwards, and
         * `MockEngine` stops short of that step. So the observable fact about a body in a
         * test is the body's content type, not the request's headers.
         */
        val bodyContentType: String?,
    )

    val recorded: List<Recorded> get() = recordedInternal.toList()
    val requestCount: Int get() = recordedInternal.size

    fun enqueue(stub: Stub, forPath: String? = null) {
        if (forPath == null) {
            fallback.addLast(stub)
        } else {
            scripts.getOrPut(forPath) { ArrayDeque() }.addLast(stub)
        }
    }

    fun enqueueAll(vararg stubs: Stub) = stubs.forEach { enqueue(it) }

    fun reset() {
        scripts.clear()
        fallback.clear()
        recordedInternal.clear()
    }

    /** The engine to hand a [KtorHttpClient]. */
    fun engine(): MockEngine = MockEngine { request -> respondFor(request) }

    private suspend fun MockRequestHandleScope.respondFor(request: HttpRequestData) = lock.withLock {
        recordedInternal += Recorded(
            method = request.method.value,
            path = request.url.encodedPath,
            url = request.url.toString(),
            authorization = request.headers[HttpHeaders.Authorization],
            contentType = request.headers[HttpHeaders.ContentType],
            bodyContentType = request.body.contentType?.toString(),
        )
        val script = scripts[request.url.encodedPath]
        val stub = when {
            script != null && script.isNotEmpty() -> script.removeFirst()
            fallback.isNotEmpty() -> fallback.removeFirst()
            else -> Stub(statusCode = 500, body = "no stub for ${request.url.encodedPath}")
        }
        stub.failure?.let { throw it }

        respond(
            content = stub.body.encodeToByteArray(),
            status = HttpStatusCode.fromValue(stub.statusCode),
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
}

/**
 * Ktor's own timeout exception, constructed rather than triggered.
 *
 * The real thing is what the engine raises, so a stub that throws a *different* timeout
 * type would test the mapping's tolerance rather than its table. The coroutine timeout
 * (`TimeoutCancellationException`) is a separate case and is exercised in `ErrorMappingTest`
 * where it can actually be produced.
 */
internal fun stubTimeout(): Throwable =
    HttpRequestTimeoutException("simulated timeout", 30_000L, null)

/** A transport failure that is neither a timeout nor an [AppError]. */
internal fun stubTransportFailure(): Throwable = SimulatedTransportFailure("simulated connection failure")

private class SimulatedTransportFailure(message: String) : Exception(message)

/** A plain cancellation, which the client must not report as a network error. */
internal fun stubCancellation(): Throwable = CancellationException("cancelled by the test")
