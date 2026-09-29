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

    fun map(statusCode: Int, body: ByteArray): AppError {
        val message = serverMessage(body)
        return when (statusCode) {
            400 -> AppError.Validation(message.ifEmpty { "The server rejected the request." })
            401 -> AppError.Unauthorized
            403 -> AppError.Forbidden
            404 -> AppError.Unknown(message.ifEmpty { "Not found (404)." })
            409 -> AppError.Conflict(entity = message.ifEmpty { "record" })
            422 -> AppError.Validation(message.ifEmpty { "The request was not processable." })
            in 500..599 -> AppError.Server(statusCode, message)
            else -> AppError.Unknown("Unexpected status $statusCode. $message")
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
