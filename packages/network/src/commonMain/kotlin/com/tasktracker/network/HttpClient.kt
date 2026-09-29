package com.tasktracker.network

import com.tasktracker.core.JSONCoding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer

/**
 * The capability a feature's data layer depends on.
 *
 * An interface rather than a Ktor type, so `:packages:feature-*-data` can be written and
 * tested against a fake and never names a Ktor class. The cost of getting this wrong is
 * not stylistic: a data layer that depends on Ktor's `HttpClient` has to re-declare
 * `expectSuccess = false` and the 401 handling at every call site, and the first one that
 * forgets is the call that treats a 500 as a sign-out.
 */
interface HttpClient {

    /** Sends [request] and returns the raw response body. */
    suspend fun send(request: HttpRequest): ByteArray

    /** Sends [request] and decodes a JSON body. Throws `AppError.Decoding` on a mismatch. */
    suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T

    /** Sends [request] with [body] serialised as its JSON body. */
    suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T)
}

/**
 * A typed request description.
 *
 * Features implement this instead of hand-building [HttpRequest]s, which keeps URL shapes
 * in one place and makes them reviewable. The serializers are passed explicitly where the
 * original relied on `Encodable`/`Decodable` inference: Kotlin has no reified type
 * parameter that works on every target, so [jsonBody] and the [post] helper below are the
 * reified seams, and this interface stays free of them.
 */
interface Endpoint {
    val path: String
    val method: HttpMethod
    val queryItems: List<HttpQueryItem> get() = emptyList()
    val body: ByteArray? get() = null
    val requiresAuth: Boolean get() = true
    val isIdempotent: Boolean? get() = null

    val request: HttpRequest
        get() = HttpRequest(
            method = method,
            path = path,
            queryItems = queryItems,
            body = body,
            requiresAuth = requiresAuth,
            isIdempotent = isIdempotent,
        )
}

/**
 * A JSON body built with the shared encoder.
 *
 * `null` when [value] does not fit the contract, which is what the original's `try?`
 * meant. The catch is narrowed to [SerializationException] rather than left as a
 * catch-all: a bare `catch` here would also swallow a `CancellationException` if the
 * encoder ever gains a suspending path, and a cancelled request that goes on to "succeed"
 * with an empty body is a much worse bug than a missing body.
 */
inline fun <reified T> jsonBody(value: T): ByteArray? = try {
    JSONCoding.json.encodeToString(value).encodeToByteArray()
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    // `encodeToString` reports "no serializer for this class" as an
    // IllegalArgumentException rather than a SerializationException, so a caller that
    // built a `List<String>` where a DTO was expected gets `null` — the same outcome the
    // original's `try?` gave — instead of an exception from a helper whose contract is
    // "null when it does not fit".
    null
}

/** Sends a JSON body and decodes the reply. The shape most feature repositories want. */
suspend inline fun <reified Body, reified Response> HttpClient.post(
    path: String,
    body: Body,
    requiresAuth: Boolean = true,
): Response = send(
    HttpRequest(
        method = HttpMethod.Post,
        path = path,
        body = jsonBody(body),
        requiresAuth = requiresAuth,
    ),
    decoding = serializer<Response>(),
)
