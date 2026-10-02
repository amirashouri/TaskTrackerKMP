package com.tasktracker.featuresettingsdomain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vocabulary's own rules.
 *
 * ## The two title tests from the original live in the presentation module instead
 *
 * The original asserted that every [ConnectivitySummary] and every [SyncActivity.Phase] had a
 * distinct English label, and that [SyncOutcome.offline] carried a message mentioning that the
 * edits were "queued". All three assertions are about *wording*, which this module does not
 * hold — see the note on [ConnectivitySummary] — so the distinctness check became a check that
 * the two string resources per case exist and differ, in `:packages:feature-settings-presentation`.
 *
 * What stays here is everything that decides a value: which phases count as syncing, which
 * counts are worth a row, and when a version is hidden.
 */
class SettingsVocabularyTest {

    @Test
    fun aQueueDepthOfZeroIsNotWorthARowAndAnythingAboveItIs() {
        assertNull(SyncActivity(queueDepth = 0).outstandingCount)
        assertEquals(3, SyncActivity(queueDepth = 3).outstandingCount)
        assertNull(SyncActivity(deadLetterCount = 0).stuckCount)
        assertEquals(2, SyncActivity(deadLetterCount = 2).stuckCount)
    }

    @Test
    fun syncingIsThePhaseTheSyncNowControlIsDisabledOn() {
        assertTrue(SyncActivity(phase = SyncActivity.Phase.Syncing).isSyncing)
        assertFalse(SyncActivity(phase = SyncActivity.Phase.Idle).isSyncing)
        assertFalse(SyncActivity(phase = SyncActivity.Phase.WaitingForConnection).isSyncing)
    }

    @Test
    fun everyPhaseIsItsOwnStateAndOnlySyncingCountsAsRunning() {
        val phases = SyncActivity.Phase.entries

        // Seven states, and exactly one of them is "a cycle is running". Asserted as a count
        // because the whole point of the sealed enum is that the screen switches over all of
        // them: a phase added later has to make this number wrong until someone says so.
        assertEquals(7, phases.size)
        assertEquals(1, phases.count { SyncActivity(phase = it).isSyncing })
    }

    @Test
    fun onlyAnExplicitlyUnusablePathBlocksASync() {
        assertTrue(ConnectivitySummary.Online.allowsSync)
        assertFalse(ConnectivitySummary.Offline.allowsSync)
        assertFalse(ConnectivitySummary.NeedsConnection.allowsSync)
        // `Unknown` deliberately counts as "try": a freshly launched app has not heard from the
        // platform monitor yet, and treating that as offline would strand a queued queue on a
        // device with a perfectly good network.
        assertTrue(ConnectivitySummary.Unknown.allowsSync)
        assertTrue(ConnectivitySummary.Online.isOnline)
        assertFalse(ConnectivitySummary.Unknown.isOnline)
    }

    @Test
    fun aVersionWithNoValuesIsNotRenderedAtAll() {
        assertNull(AppVersion(marketing = "", build = "").display)
        // Whitespace is absence: a plist padded with spaces must not render as " (1)".
        assertNull(AppVersion(marketing = "  ", build = "1").display)
        assertEquals("1.0", AppVersion(marketing = "1.0", build = "").display)
        assertEquals("1.0", AppVersion(marketing = " 1.0 ", build = "  ").display)
        assertEquals("1.0 (42)", AppVersion(marketing = "1.0", build = "42").display)
    }

    @Test
    fun anActivityStartsIdleWithAnEmptyQueue() {
        val activity = SyncActivity()

        assertEquals(SyncActivity.Phase.Idle, activity.phase)
        assertEquals(0, activity.queueDepth)
        assertEquals(emptyList(), activity.failures)
        assertNull(activity.outstandingCount)
        assertNull(activity.stuckCount)
    }
}