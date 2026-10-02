package com.tasktracker.featuresettingsdomain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncNowTest {

    @Test
    fun refusesToStartWithNoUsableNetworkPath() = runTest {
        val port = FakeSettingsPort(canSyncNow = false, outcome = SyncOutcome.Started)

        val outcome = SyncNow(port)()

        assertEquals(SyncOutcome.Offline, outcome)
        // The point of the guard: a request that cannot run must not reach the coordinator at
        // all, so nothing can quietly start a cycle the user was told would not happen.
        assertEquals(0, port.syncNowCount)
    }

    @Test
    fun runsACycleAndReportsThatItStarted() = runTest {
        val port = FakeSettingsPort(canSyncNow = true, outcome = SyncOutcome.Started)

        val outcome = SyncNow(port)()

        assertEquals(SyncOutcome.Started, outcome)
        assertEquals(1, port.syncNowCount)
    }

    @Test
    fun passesThroughACycleThatWasAlreadyRunning() = runTest {
        val port = FakeSettingsPort(canSyncNow = true, outcome = SyncOutcome.AlreadyRunning)

        val outcome = SyncNow(port)()

        assertEquals(SyncOutcome.AlreadyRunning, outcome)
        assertEquals(1, port.syncNowCount)
    }

    @Test
    fun theOfflineDecisionIsRereadOnEveryRequest() = runTest {
        val port = FakeSettingsPort(canSyncNow = false)
        val syncNow = SyncNow(port)

        assertEquals(SyncOutcome.Offline, syncNow())

        // The rule is not cached in the use case, so a path that comes back is usable on the
        // very next tap rather than the next time the screen is rebuilt.
        port.setCanSyncNow(true)

        assertEquals(SyncOutcome.Started, syncNow())
        assertEquals(1, port.syncNowCount)
    }

    @Test
    fun endingASessionIsCountedOnce() = runTest {
        val port = FakeSettingsPort()

        port.endSession()

        assertEquals(1, port.sessionEndCount)
    }

    @Test
    fun forcingOfflineIsRecordedInOrder() = runTest {
        val port = FakeSettingsPort()

        port.setForcedOffline(true)
        port.setForcedOffline(false)

        // The port takes the user's *intent*, so the history is the toggle sequence a user
        // performed rather than a list of network states the adapter happened to install.
        assertEquals(listOf(true, false), port.forcedOfflineHistory)
    }
}