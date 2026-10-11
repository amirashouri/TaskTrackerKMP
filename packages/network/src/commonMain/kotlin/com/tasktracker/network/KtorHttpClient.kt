package com.tasktracker.network

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.content.ByteArrayContent
import io.ktor.client.plugins.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * Ktor-backed [HttpClient] with an interceptor chain and a retry policy.
 *
 * The original notes that it is a `struct` of `let`s rather than an `actor` because
 * `URLSession` is already thread-safe. That argument survives: Ktor's client is
 * thread-safe too, so wrapping this in a mutex would serialise every in-flight request for
 * nothing. The concurrency control that *is* needed lives in the auth delegate
 * (`TokenVault`'s refresh gate), not here.
 *
 * @param baseUrl the API root. A trailing slash is tolerated.
 * @param engine the transport. Defaults to the platform's — Darwin's is Foundation's
 *   `URLSession`, OkHttp's is the JVM/Android HTTP stack — which is why the engine is an
 *   `expect`/`actual` and not a common dependency.
 * @param sleeper how to wait between retries. Injected because the policy is a value and
 *   the tests are about *which* delays were asked for; a test that really slept 400ms
 *   would assert on a stopwatch.
 * @param connectivity optional. When the path is [Connectivity.RequiresConnection] or
 *   [Connectivity.Unsatisfied] the call fails immediately with [AppError.Offline] instead
 *   of spending a round trip to be told the same thing — see [HttpErrorMapping] for why
 *   the reverse (guessing `Offline` from a connection failure) is not done.
 */
class KtorHttpClient(
    private val baseUrl: String,
    engine: HttpClientEngine = createDefaultHttpEngine(),
    private val interceptors: List<RequestInterceptor> = emptyList(),
    private val retryPolicy: RetryPolicy = RetryPolicy.Default,
    private val json: Json = JSONCoding.json,
    private val sleeper: suspend (Duration) -> Unit = { delay(it) },
    private val connectivity: ConnectivitySource? = null,
) : HttpClient {

    private val client = KtorClient(engine) {
        // The whole auth design depends on *seeing* a 401 rather than having it thrown:
        // `AuthInterceptor` decides between "refresh once and replay" and "end the session"
        // from the status code, and Ktor's own `expectSuccess` (or an
        // `HttpResponseValidator`) would raise before any interceptor ran. The original
        // never had this problem because `URLSession` returns a 401 as a perfectly
        // ordinary response. Validation happens in [validated], after the chain.
        expectSuccess = false
    }

    /** Releases the engine. The DI container owns one client for the app's lifetime. */
    fun close() = client.close()

    // MARK: - HttpClient

    override suspend fun send(request: HttpRequest): ByteArray {
        // At most one replay for the whole call. This is the guard that makes a
        // 401 -> refresh -> 401 loop impossible.
        var replayed = false

        while (true) {
            // The adapt chain runs on *every* pass, which is what makes a replay pick up
            // the token the interceptor has just refreshed.
            var outgoing = request
            for (interceptor in interceptors) {
                outgoing = interceptor.adapt(outgoing)
            }

            val response = performWithRetries(outgoing)

            var wantsReplay = false
            for (interceptor in interceptors) {
                val decision = interceptor.retry(request, response, hasReplayed = replayed)
                if (decision == InterceptorDecision.Retry) {
                    wantsReplay = true
                    break
                }
            }

            if (wantsReplay && !replayed) {
                replayed = true
                TTLogger.network.info {
                    logMessage {
                        append("network: replaying ")
                        append(request.method.wireName)
                        append(' ')
                        append(request.path)
                        append(" after auth refresh")
                    }
                }
                continue
            }

            // Either nothing asked for a replay, or we already spent our one replay and
            // must accept the answer — including a second 401, which is terminal.
            return validated(response)
        }
    }

    override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T {
        val data = send(request)
        return try {
            json.decodeFromString(decoding, data.decodeToString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SerializationException) {
            throw AppError.Decoding("Could not read the response for ${request.path}.")
        } catch (_: IllegalArgumentException) {
            // Thrown by `decodeToString` for bytes that are not UTF-8, which is a server
            // contract failure like any other and not a programming error here.
            throw AppError.Decoding("Could not read the response for ${request.path}.")
        }
    }

    override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
        val payload = try {
            json.encodeToString(encoding, body).encodeToByteArray()
        } catch (_: SerializationException) {
            throw AppError.Validation("The request body does not match the API's contract.")
        } catch (_: IllegalArgumentException) {
            // Same for "no serializer for this class", which kotlinx reports as an
            // IllegalArgumentException rather than a SerializationException. A body that
            // cannot be encoded is a client-side bug and says so, rather than leaving the
            // caller to decode a failure as a server rejection.
            throw AppError.Validation("The request body does not match the API's contract.")
        }
        val withBody = request.copy(
            body = payload,
            headers = if (request.header("Content-Type") == null) {
                request.headers + ("Content-Type" to "application/json")
            } else {
                request.headers
            },
        )
        send(withBody)
    }

    // MARK: - Internals

    private suspend fun performWithRetries(request: HttpRequest): HttpResponseSummary {
        var attempt = 0
        while (true) {
            try {
                val response = perform(request)
                if (response.isSuccess || !retryPolicy.shouldRetry(
                        method = request.method,
                        isIdempotent = request.effectiveIdempotency,
                        statusCode = response.statusCode,
                        attempt = attempt,
                    )
                ) {
                    return response
                }
                attempt += 1
                sleepBeforeRetry(attempt)
            } catch (error: AppError) {
                if (!retryPolicy.shouldRetry(
                        method = request.method,
                        isIdempotent = request.effectiveIdempotency,
                        statusCode = null,
                        error = error,
                        attempt = attempt,
                    )
                ) {
                    throw error
                }
                attempt += 1
                sleepBeforeRetry(attempt)
            }
        }
    }

    private suspend fun perform(request: HttpRequest): HttpResponseSummary {
        // Checked once per call, not per attempt: a device that regained its connection
        // between two retries should use it, and re-asking is free either way.
        if (connectivity != null && connectivity.current.isKnownUnusable) {
            throw AppError.Offline
        }

        // Parsed before the try, and therefore outside the mapping below: a caller's
        // malformed content type is a Validation error, and letting it be caught as a
        // transport failure would report a local bug as "could not reach the server".
        val bodyContentType = request.body?.let { request.contentType() }

        val response: HttpResponse = try {
            client.request(urlString(request)) {
                // Fully qualified: this module's own `HttpMethod` enum is the one in
                // scope here, and Ktor's builder wants its own. Spelling out which is
                // which is cheaper than aliasing either.
                method = io.ktor.http.HttpMethod.parse(request.method.wireName)
                timeout { requestTimeoutMillis = request.timeout.inWholeMilliseconds }
                header("Accept", "application/json")
                for ((name, value) in request.headers) {
                    if (name.equals("Content-Type", ignoreCase = true)) continue
                    header(name, value)
                }
                // The content type travels on the body, not as a plain header. Ktor
                // renders `OutgoingContent.contentType` into the request itself and
                // overwrites a hand-set `Content-Type` header while doing so, so a header
                // set here would either be dropped or be one of two values — and the
                // server is left to pick which one it believes.
                if (request.body != null && bodyContentType != null) {
                    setBody(ByteArrayContent(request.body, bodyContentType))
                }
            }
        } catch (error: Throwable) {
            throw HttpErrorMapping.map(error)
        }

        return HttpResponseSummary(
            statusCode = response.status.value,
            headers = response.headers.entries().associate { (name, values) ->
                name to values.joinToString(", ")
            },
            body = response.bodyAsBytes(),
        )
    }

    /**
     * The absolute URL for [request].
     *
     * Built here rather than left to `takeFrom(baseUrl)` + `appendPathSegments` alone,
     * because a base URL with a path of its own (`https://host/api`) has to keep it. Each
     * path segment is encoded separately, so a segment containing a literal `/` is a bug
     * in the endpoint rather than something that silently becomes a different route.
     */
    /**
     * The body content type: the caller's, or JSON.
     *
     * A caller's value is parsed rather than passed through, and a malformed one is
     * refused by name — an unparseable content type is a caller's bug, and sending the
     * request anyway would produce a server rejection that points nowhere near it.
     */
    private fun HttpRequest.contentType(): ContentType {
        val declared = header("Content-Type") ?: return ContentType.Application.Json
        return try {
            ContentType.parse(declared)
        } catch (_: Exception) {
            // `Exception` and not `IllegalArgumentException`, because Ktor's
            // `BadContentTypeFormatException` extends `Exception` — the one Ktor type here
            // that does not follow the convention, and it is checked on every platform
            // rather than only the JVM.
            throw AppError.Validation("\"$declared\" is not a content type.")
        }
    }

    private fun urlString(request: HttpRequest): String {
        val builder = URLBuilder(baseUrl)
        val segments = request.path.split('/').filter { it.isNotEmpty() }
        if (segments.isNotEmpty()) {
            builder.appendPathSegments(segments)
        }
        for (item in request.queryItems) {
            builder.parameters.append(item.name, item.value)
        }
        return builder.buildString()
    }

    private fun validated(response: HttpResponseSummary): ByteArray {
        if (!response.isSuccess) throw HttpErrorMapping.map(response.statusCode, response.body)
        return response.body
    }

    private suspend fun sleepBeforeRetry(attempt: Int) {
        val wait = retryPolicy.delay(attempt)
        if (wait > Duration.ZERO) sleeper(wait)
    }
}

/** The platform's HTTP engine. `expect` because the transport is the one real difference. */
expect fun createDefaultHttpEngine(): HttpClientEngine
