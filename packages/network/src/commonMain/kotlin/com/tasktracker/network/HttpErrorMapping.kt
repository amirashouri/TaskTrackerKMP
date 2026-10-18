package com.tasktracker.network

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException

/**
 * Everything that turns bytes and exceptions into [AppError].
 *
 * One object, called from exactly one place in the client, because the mapping is the
 * part of this module with a table in it: status→error, exception→error, envelope→
 * message. A mapping that lived on the client class would be unreachable from a test
 * without constructing a client, which is how the original's two `map` statics ended up
 * as the only static functions in `NetworkService`.
 *
 * `internal` because nothing above this layer may name a Ktor type — and, more to the
 * point, because a caller that wants to re-map an error is a caller that has stopped
 * trusting the one place that maps them.
 */
internal object HttpErrorMapping {

    /**
     * Everything else becomes [AppError.Transport].
     *
     * **Not** `.offline`, and that is the one deliberate gap against the original, which
     * mapped `URLError.notConnectedToInternet` to `.offline` and a DNS failure to
     * `.transport`. The two are distinguishable on Darwin (`NSURLErrorNotConnectedTo-
     * Internet` against `NSURLErrorCannotFindHost`) and *not* distinguishable on OkHttp,
     * which raises `UnknownHostException` for both. A shared mapping would have to pick
     * one: guess `Offline` and a user with a DNS problem watches a "you are offline" banner
     * that fixing their DNS will not clear, or guess `Transport` and lose the honest
     * offline state everywhere. So `.offline` is only claimed where it is a *positive*
     * statement — [NetworkMonitor] reporting an unsatisfied path, which the client turns
     * into a fast local failure — and a connection failure stays `.transport`, which
     * renders a "try again" affordance rather than an offline diagnosis.
     */
    fun map(error: Throwable): AppError = when (error) {
        // An `AppError` thrown by a stub, or by a delegate called from an interceptor, is
        // already the answer. Re-wrapping would replace a precise
        // `.Conflict("work item")` with `.transport(AppError)` and lose the entity name.
        is AppError -> error
        is HttpRequestTimeoutException -> AppError.Timeout
        // Checked before the `CancellationException` case below, because it *is* one: the
        // same type means "the deadline passed" in a coroutine and "the caller went away"
        // everywhere else, and only the first is a network error.
        is TimeoutCancellationException -> AppError.Timeout
        // Rethrown rather than mapped. A cancelled screen must not come back as "Could not
        // reach the server" — it would log a phantom network failure, and the sync engine
        // would record a failed attempt for a sync the user navigated away from.
        is CancellationException -> throw error
        is IOException -> AppError.Transport(error.message ?: "The connection failed.")
        else -> AppError.Transport(error.message ?: "unknown")
    }

    /**
     * Statuses whose `error` field is a product-defined, user-safe sentence the client may
     * show verbatim.
     *
     * Everything else — 5xx and any unrecognised status — is server-internal: the user gets
     * a generic, client-owned message and the raw text is withheld rather than rendered. The
     * server is trusted for these four because their wording is part of the API contract
     * (docs/API.md), not an implementation detail that could leak a stack trace or a query.
     * 401/403 are deliberately absent: their user text is client-owned
     * (`AppError.Unauthorized`/`Forbidden`), so the server's words are never shown for them.
     */
    private val SURFACE_SAFE_STATUSES = setOf(400, 404, 409, 422)

    fun map(statusCode: Int, body: ByteArray): AppError {
        val raw = serverMessage(body)
        // Only statuses in the allowlist may surface the server's words; for the rest the
        // detail is withheld so a future unhandled path cannot leak internals to the user.
        val detail = if (statusCode in SURFACE_SAFE_STATUSES) sanitize(raw) else ""
        return when (statusCode) {
            400 -> AppError.Validation(detail.ifEmpty { "The server rejected the request." })
            401 -> AppError.Unauthorized
            403 -> AppError.Forbidden
            404 -> AppError.NotFound(detail.ifEmpty { "Not found (404)." })
            409 -> AppError.Conflict(entity = detail.ifEmpty { "item" })
            422 -> AppError.Validation(detail.ifEmpty { "The request was not processable." })
            in 500..599 -> AppError.Server(statusCode, detail)
            429 -> AppError.Unknown("Too many attempts. Please wait a moment and try again.")
            else -> AppError.Unknown("Unexpected response from the server. Please try again.")
        }
    }

    /**
     * Makes a server `error` string safe to put in a banner.
     *
     * A leaking server can return more than a sentence — a wrapped exception carries
     * newlines and tabs, and an over-long body was already a risk before the cap. Stripping
     * control characters and collapsing whitespace keeps one failure on one line, and the
     * length cap bounds what a proxy or a future bug can put in front of a user. A clean
     * sentence passes through unchanged.
     */
    private fun sanitize(text: String): String {
        if (text.isEmpty()) return ""
        val cleaned = text
            .replace(Regex("[\\p{Cntrl}&&[^\\r\\n\\t]]"), "")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return if (cleaned.length <= MAX_MESSAGE_LENGTH) {
            cleaned
        } else {
            cleaned.take(MAX_MESSAGE_LENGTH).trimEnd()
        }
    }

    @Serializable
    private data class Envelope(
        @SerialName("message") val message: String? = null,
        @SerialName("error") val error: String? = null,
    )

    /**
     * A human-readable message out of a `{ "message": … }` envelope, tolerating a body
     * that is not JSON at all — an HTML error page, or an empty 503.
     *
     * The 200-character cap is the original's. It bounds what a proxy can put in front of
     * a banner, and without it a 502 whose body is a stack trace becomes the message.
     * Truncation happens here rather than at the call site because every one of the seven
     * statuses above wants the same bounded text.
     */
    private fun serverMessage(body: ByteArray): String {
        if (body.isEmpty()) return ""
        val envelope = try {
            JSONCoding.json.decodeFromString(Envelope.serializer(), body.decodeToString())
        } catch (_: SerializationException) {
            null
        }
        if (envelope != null) return envelope.message ?: envelope.error ?: ""
        val text = body.decodeToString()
        return if (text.length <= MAX_MESSAGE_LENGTH) text else ""
    }

    /** Matches the original's cap, and the reason [serverMessage] documents it. */
    const val MAX_MESSAGE_LENGTH: Int = 200
}
