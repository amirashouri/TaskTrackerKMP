package com.tasktracker.network

import com.tasktracker.core.AppError
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exponential backoff with jitter.
 *
 * The policy is deliberately conservative and, more importantly, *method-aware*: a POST is
 * never replayed on a transport error unless the request explicitly opts in, because the
 * server may already have applied the change.
 *
 * @param maxRetries retries *after* the first attempt, so `2` means at most three
 *   requests. Named for what it bounds, which is how the original's callers read it.
 * @param random injected so the jitter band is testable; the band assertions hold for any
 *   generator, and a test that proves them with the global one proves them for one seed.
 */
class RetryPolicy(
    val maxRetries: Int = 2,
    val baseDelay: Duration = 400.milliseconds,
    val maxDelay: Duration = 6.seconds,
    val usesJitter: Boolean = true,
    private val random: Random = Random.Default,
) {

    /** [Duration.ZERO] for the first attempt, so a caller cannot sleep before trying. */
    fun delay(attempt: Int): Duration {
        if (attempt <= 0) return Duration.ZERO
        val exponential = min(maxDelay.inWholeMilliseconds, baseDelay.inWholeMilliseconds * twoTo(attempt - 1))
        if (!usesJitter) return exponential.milliseconds
        // Full jitter, matching the original: uniformly distributed in
        // [exponential/2, exponential]. Jitter below half would not spread a stampede out,
        // and jitter above the ceiling would exceed the cap the caller asked for.
        val half = exponential / 2
        return (half + random.nextLong(half + 1)).milliseconds
    }

    /**
     * Whether to retry after a *response*.
     *
     * `statusCode == null` means the call never got one — a transport failure — which is
     * retryable on its own terms. A 4xx is never retried: the server understood the
     * request and refused it, and sending it again asks the same question.
     */
    fun shouldRetry(
        method: HttpMethod,
        isIdempotent: Boolean,
        statusCode: Int?,
        attempt: Int,
    ): Boolean {
        if (!isIdempotent || attempt >= maxRetries) return false
        if (statusCode == null) return true // transport failure
        return statusCode in 500..599
    }

    /**
     * Whether to retry after a throw.
     *
     * Only connectivity failures, never a logical error. The two are not the same kind of
     * thing: `.offline` and `.timeout` describe a world where the same bytes sent later
     * would land, while `.validation("title is required")` describes a request the server
     * will refuse identically forever.
     */
    fun shouldRetry(
        method: HttpMethod,
        isIdempotent: Boolean,
        statusCode: Int?,
        error: AppError,
        attempt: Int,
    ): Boolean {
        if (!isIdempotent || attempt >= maxRetries) return false
        return error.isConnectivityFailure
    }

    /** `2^(attempt-1)` in integer arithmetic, so a large [maxRetries] cannot overflow. */
    private fun twoTo(exponent: Int): Int {
        var result = 1
        repeat(min(exponent, 30)) { result *= 2 }
        return result
    }

    companion object {
        val Default = RetryPolicy()

        /**
         * Used by the sync engine's dead-lettering tests and by sign-in, where a fast
         * failure beats a slow one.
         */
        val None = RetryPolicy(maxRetries = 0)
    }
}
