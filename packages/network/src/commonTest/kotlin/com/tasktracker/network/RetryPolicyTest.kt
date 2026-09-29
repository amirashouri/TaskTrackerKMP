package com.tasktracker.network

import com.tasktracker.core.AppError
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {

    @Test
    fun `backoff grows exponentially and is capped`() {
        val policy = RetryPolicy(maxRetries = 5, baseDelay = 1.seconds, maxDelay = 8.seconds, usesJitter = false)

        assertEquals(0.milliseconds, policy.delay(0))
        assertEquals(1.seconds, policy.delay(1))
        assertEquals(2.seconds, policy.delay(2))
        assertEquals(4.seconds, policy.delay(3))
        assertEquals(8.seconds, policy.delay(4))
        assertEquals(8.seconds, policy.delay(9))
    }

    @Test
    fun `a negative attempt is treated as the first one`() {
        val policy = RetryPolicy(usesJitter = false)
        assertEquals(0.milliseconds, policy.delay(-1))
    }

    @Test
    fun `jitter keeps delays inside the expected band`() {
        // Seeded rather than random: 50 draws from the global generator would pass this
        // assertion by luck, and would keep passing by luck, which is not a test.
        val policy = RetryPolicy(maxRetries = 3, baseDelay = 4.seconds, maxDelay = 100.seconds, random = Random(7))
        repeat(50) {
            val delay = policy.delay(2) // exponential == 8s
            assertTrue(delay in 4.seconds..8.seconds, "delay was $delay")
        }
    }

    @Test
    fun `jitter actually varies the delay`() {
        val policy = RetryPolicy(baseDelay = 4.seconds, random = Random(3))
        val delays = (1..20).map { policy.delay(2) }
        assertTrue(delays.distinct().size > 1, "full jitter produced one value every time")
    }

    @Test
    fun `jitter never exceeds the cap`() {
        val policy = RetryPolicy(baseDelay = 4.seconds, maxDelay = 5.seconds, random = Random(11))
        repeat(50) {
            assertTrue(policy.delay(6) <= 5.seconds)
        }
    }

    @Test
    fun `a non-idempotent request is never retried`() {
        val policy = RetryPolicy(maxRetries = 3)
        assertFalse(
            policy.shouldRetry(
                method = HttpMethod.Post,
                isIdempotent = false,
                statusCode = 500,
                attempt = 0,
            ),
        )
    }

    @Test
    fun `an idempotent request is retried inside the budget`() {
        val policy = RetryPolicy(maxRetries = 3)
        assertTrue(
            policy.shouldRetry(
                method = HttpMethod.Get,
                isIdempotent = true,
                statusCode = 500,
                attempt = 0,
            ),
        )
    }

    @Test
    fun `the budget is not exceeded`() {
        val policy = RetryPolicy(maxRetries = 3)
        assertFalse(
            policy.shouldRetry(
                method = HttpMethod.Get,
                isIdempotent = true,
                statusCode = 500,
                attempt = 3,
            ),
        )
    }

    @Test
    fun `a four hundred is never retried`() {
        val policy = RetryPolicy(maxRetries = 3)
        for (status in listOf(400, 401, 403, 404, 409, 422)) {
            assertFalse(
                policy.shouldRetry(
                    method = HttpMethod.Get,
                    isIdempotent = true,
                    statusCode = status,
                    attempt = 0,
                ),
                "status $status should not be retried",
            )
        }
    }

    @Test
    fun `a missing status is a transport failure and is retried`() {
        val policy = RetryPolicy(maxRetries = 3)
        assertTrue(
            policy.shouldRetry(
                method = HttpMethod.Get,
                isIdempotent = true,
                statusCode = null,
                attempt = 0,
            ),
        )
    }

    @Test
    fun `only connectivity failures are retried and not logical errors`() {
        val policy = RetryPolicy(maxRetries = 3)
        fun shouldRetry(error: AppError) = policy.shouldRetry(
            method = HttpMethod.Get,
            isIdempotent = true,
            statusCode = null,
            error = error,
            attempt = 0,
        )

        assertTrue(shouldRetry(AppError.Offline))
        assertTrue(shouldRetry(AppError.Timeout))
        assertTrue(shouldRetry(AppError.Transport("connection reset")))

        // The same bytes sent later would be refused identically forever.
        assertFalse(shouldRetry(AppError.Validation("bad")))
        assertFalse(shouldRetry(AppError.Conflict("project")))
        assertFalse(shouldRetry(AppError.Cancelled))
        assertFalse(shouldRetry(AppError.Unauthorized))
    }

    @Test
    fun `maxRetries zero disables retries`() {
        assertFalse(
            RetryPolicy.None.shouldRetry(
                method = HttpMethod.Get,
                isIdempotent = true,
                statusCode = 500,
                attempt = 0,
            ),
        )
    }

    @Test
    fun `the defaults are the ones the original used`() {
        assertEquals(2, RetryPolicy.Default.maxRetries)
        assertEquals(400.milliseconds, RetryPolicy.Default.baseDelay)
        assertEquals(6.seconds, RetryPolicy.Default.maxDelay)
        assertTrue(RetryPolicy.Default.usesJitter)
        assertEquals(0, RetryPolicy.None.maxRetries)
    }
}
