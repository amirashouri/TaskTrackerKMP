package com.tasktracker.sync

import kotlin.time.Instant

/**
 * What one cycle actually did. Returned rather than thrown.
 *
 * A cycle is triggered by a timer and by connectivity events, and both of those callers
 * have nothing useful to do with an error; a report they can log and show is strictly
 * more informative than a boolean.
 *
 * A `data class` of `val`s, and the engine builds it with `copy` as it goes. The original
 * was a struct mutated through `inout`, which Kotlin has no equivalent of: passing a
 * mutable holder through suspend functions would make the report visible to another
 * coroutine while it is still being filled in, and the whole point of a report is that it
 * is a value you can hand out.
 */
data class SyncReport(
    val trigger: SyncTrigger,
    /** Rows found stuck in `inFlight` by a previous crash and returned to `pending`. */
    val recoveredStranded: Int = 0,
    /** Entries handed to the server. */
    val pushed: Int = 0,
    val accepted: Int = 0,
    /** Resolved in the server's favour, per the server-wins policy. */
    val conflicts: Int = 0,
    /** Dead-lettered on the spot because the server refused them permanently. */
    val rejected: Int = 0,
    /** Sent back to the queue for another attempt. */
    val retrying: Int = 0,
    /** Server changes written to the local store. */
    val applied: Int = 0,
    /** Entries still in the queue when the cycle ended, dead rows included. */
    val queueDepth: Int = 0,
    val deadLetterCount: Int = 0,
    val failures: List<SyncFailure> = emptyList(),
    /** The session is gone and the user must sign in again before anything can sync. */
    val requiresSignOut: Boolean = false,
) {
    /**
     * A cycle that reached the server and did its work.
     *
     * Dead-lettered entries do not make a cycle a failure: refusing to retry a change the
     * server will never accept is the correct outcome, and the report says so instead of
     * pretending everything landed.
     */
    val isSuccess: Boolean get() = failures.isEmpty() && !requiresSignOut

    /** A cycle that could not do its work but will be tried again later. */
    val isDeferred: Boolean get() = failures.isNotEmpty() && !requiresSignOut

    override fun toString(): String =
        "SyncReport(trigger=$trigger, pushed=$pushed, accepted=$accepted, " +
            "conflicts=$conflicts, rejected=$rejected, retrying=$retrying, applied=$applied, " +
            "queueDepth=$queueDepth, deadLetterCount=$deadLetterCount, " +
            "requiresSignOut=$requiresSignOut, failures=${failures.size})"
}

/**
 * The state a view can render, with no knowledge of the engine.
 *
 * [report] is a member of every case rather than a `when` in one place, so the three cases
 * that carry a report are `data class`es whose `report` *is* the payload, and the four that
 * do not say so in their own declaration. A single `when` would have needed the same four
 * `null` branches somewhere else, and this way "can I read a report off this status?" is
 * answered by the type rather than by reading a function.
 */
sealed interface SyncStatus {
    val report: SyncReport?

    val isSyncing: Boolean get() = this is Syncing

    /** Nothing has run yet, or the last run has been reported. */
    data class Idle(val lastSyncedAt: Instant?) : SyncStatus {
        override val report: SyncReport? get() = null
    }

    /** Blocked on a network path. Queued work is safe. */
    data object WaitingForConnectivity : SyncStatus {
        override val report: SyncReport? get() = null
    }

    data class Syncing(val phase: SyncPhase) : SyncStatus {
        override val report: SyncReport? get() = null
    }

    data class Succeeded(override val report: SyncReport, val at: Instant) : SyncStatus

    /** Failed for a reason that will be retried — a transport error, a 5xx, no connectivity. */
    data class Deferred(override val report: SyncReport) : SyncStatus

    /** Failed and will not be retried on its own. */
    data class Failed(override val report: SyncReport) : SyncStatus

    /** The session ended mid-cycle. Everything queued is preserved for the next sign-in. */
    data object SignedOut : SyncStatus {
        override val report: SyncReport? get() = null
    }
}
