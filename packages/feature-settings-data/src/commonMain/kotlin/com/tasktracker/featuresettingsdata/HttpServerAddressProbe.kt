package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.ServerAddressProbe
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.KtorHttpClient
import com.tasktracker.network.RetryPolicy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ServerAddressProbe] over `GET /healthz`.
 *
 * The app-scoped [HttpClient] is bound to the *current* API root, so it cannot be used to
 * check a candidate the user has typed but not saved. This probe therefore opens its own
 * client for the duration of one check and closes it in `finally`; a save is rare, and an
 * engine kept alive per keystroke would be worse.
 *
 * The endpoint is the one the server already serves at the root, with no auth: a pairing
 * check has no session yet. The body must be exactly `ok` after trimming, because a proxy or
 * a captive portal that returns a `200` login page is not a TaskTracker server, and treating
 * any `200` as success would save the wrong address with confidence.
 *
 * @param connect opens a client bound to the candidate root. Injectable so the request shape
 *   and the failure mapping can be tested without a platform engine; production passes
 *   [KtorProbeClient]. The internal constructor is the test seam; the public one is the one
 *   composition-root will use in 6.2.
 */
class HttpServerAddressProbe internal constructor(
    private val connect: (String) -> ProbeClient,
) : ServerAddressProbe {

    constructor() : this(::KtorProbeClient)

    override suspend fun answersOk(baseUrl: String): Boolean {
        val client = connect(baseUrl)
        return try {
            val body = client.send(
                HttpRequest(
                    method = HttpMethod.Get,
                    path = HEALTH_PATH,
                    requiresAuth = false,
                    timeout = PROBE_TIMEOUT,
                ),
            )
            body.decodeToString().trim() == OK_BODY
        } catch (cancellation: CancellationException) {
            // A screen that was left must not be recorded as a server that failed to answer.
            throw cancellation
        } catch (cancelled: AppError.Cancelled) {
            // The same rule for the error-typed spelling of cancellation.
            throw CancellationException(cancelled.message ?: "cancelled")
        } catch (_: AppError) {
            false
        } finally {
            client.close()
        }
    }

    internal companion object {
        const val HEALTH_PATH: String = "/healthz"
        const val OK_BODY: String = "ok"

        /**
         * Five seconds: long enough for a slow LAN handshake and a TLS setup, short enough
         * that the field can report a dead address while the person is still looking at it.
         * The client's 30-second default is for sync traffic, not for an interactive check.
         */
        val PROBE_TIMEOUT: Duration = 5.seconds
    }
}

/**
 * One request against a candidate root, and the release of whatever is behind it.
 *
 * A seam, not a second abstraction: it exists so [HttpServerAddressProbe] can be tested
 * without a platform engine, and it has exactly the method the probe calls. `close` has a
 * default so a test fake that holds no resource does not have to write an empty one.
 */
internal interface ProbeClient {
    suspend fun send(request: HttpRequest): ByteArray
    fun close() = Unit
}

/** The production client: bound to the candidate, no retries, no auth interceptor chain. */
private class KtorProbeClient(baseUrl: String) : ProbeClient {

    private val client = KtorHttpClient(
        baseUrl = baseUrl,
        retryPolicy = RetryPolicy.None,
    )

    override suspend fun send(request: HttpRequest): ByteArray = client.send(request)

    override fun close() = client.close()
}
