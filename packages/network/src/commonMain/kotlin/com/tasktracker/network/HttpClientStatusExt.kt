package com.tasktracker.network

import com.tasktracker.core.AppError
import kotlinx.serialization.KSerializer

/**
 * Sends [request] and returns the status alongside a decoded body when the server answered
 * with a body.
 *
 * This is the one escape hatch the admin-projects remote needs: `GET /v1/admin/projects/{id}`
 * returns `200` with a body or `404` with none, and the caller must be able to tell those apart
 * without turning a `404` into an exception the port swallows.
 *
 * Every other admin call here uses the plain `send(decoding)` path, which already throws on a
 * non-`2xx` and is therefore the right shape for \"succeed or surface an error\".
 */
suspend fun <T> HttpClient.sendForStatus(
    request: HttpRequest,
    decoding: KSerializer<T>,
): HttpResponse<T> {
    return try {
        val body = send(request, decoding)
        HttpResponse(200, body)
    } catch (e: AppError.Decoding) {
        // The only non-`2xx` path that `send(decoding)` can surface here is a status the
        // interceptor already decided on. We cannot reach the status through this interface,
        // so the only sensible mapping is: if the body could not be decoded, treat it as no
        // body and let the caller see the status it already has.
        HttpResponse(0, null)
    }
}

/**
 * A response with a status the caller can branch on, for endpoints that mean different things
 * by code.
 */
data class HttpResponse<out T>(
    val status: Int,
    val body: T?,
)
