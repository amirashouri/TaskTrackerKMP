package com.tasktracker.network

/**
 * A read-only view of a response, handed to interceptors so they can decide what to do
 * without knowing anything about Ktor.
 *
 * The original passes a `URLResponse` to nowhere and wraps `HTTPURLResponse` here
 * instead, for a reason that survives the port: [AuthInterceptor] decides between
 * "refresh and replay" and "end the session" from [isUnauthorized] and [isForbidden]
 * alone, and a rule that reads a Ktor response type cannot be tested without an engine.
 */
class HttpResponseSummary(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {

    val isSuccess: Boolean get() = statusCode in 200..299
    val isUnauthorized: Boolean get() = statusCode == 401
    val isForbidden: Boolean get() = statusCode == 403
    val isServerError: Boolean get() = statusCode >= 500

    /**
     * The same reason as [HttpRequest.toString]: a response body can be a payload, and
     * this value gets logged.
     */
    override fun toString(): String =
        "HttpResponseSummary(statusCode=$statusCode, headers=${headers.keys}, " +
            "body=${body.size} bytes)"
}
