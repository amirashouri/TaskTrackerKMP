package com.tasktracker.featuresettingsdomain

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The order and the failure mapping of [CheckServerAddress].
 *
 * The pure table belongs to [ServerAddressTest]; this is about what gets probed and what a
 * failed probe becomes.
 */
class CheckServerAddressTest {

    private class RecordingProbe(private val answersOk: Boolean) : ServerAddressProbe {
        val calls = mutableListOf<String>()

        override suspend fun answersOk(baseUrl: String): Boolean {
            calls += baseUrl
            return answersOk
        }
    }

    @Test
    fun aValidAddressIsProbedInItsNormalisedForm() = runTest {
        val probe = RecordingProbe(answersOk = true)

        val result = CheckServerAddress(probe).check("  HTTPS://Example.com/  ")

        assertEquals(ServerAddressValidation.Valid("https://example.com"), result)
        assertEquals(listOf("https://example.com"), probe.calls)
    }

    @Test
    fun anInvalidAddressIsNeverProbed() = runTest {
        val probe = RecordingProbe(answersOk = true)

        val result = CheckServerAddress(probe).check("example.com")

        assertEquals(ServerAddressValidation.Invalid(ServerAddressValidation.Reason.MissingScheme), result)
        assertEquals(emptyList(), probe.calls)
    }

    @Test
    fun aServerThatDoesNotAnswerOkIsUnreachable() = runTest {
        val probe = RecordingProbe(answersOk = false)

        val result = CheckServerAddress(probe).check("https://example.com")

        assertEquals(ServerAddressValidation.Invalid(ServerAddressValidation.Reason.Unreachable), result)
    }

    @Test
    fun cancellationIsNotReportedAsAProbeFailure() = runTest {
        val probe = ServerAddressProbe { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            CheckServerAddress(probe).check("https://example.com")
        }
    }
}
