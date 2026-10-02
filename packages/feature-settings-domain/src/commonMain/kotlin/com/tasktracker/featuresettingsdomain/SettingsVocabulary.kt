package com.tasktracker.featuresettingsdomain

/**
 * What the device's network path looks like, in this feature's own words.
 *
 * Deliberately not `com.tasktracker.network.Connectivity`. A shared type would mean the
 * screen renders whatever the monitor grows next, and the mapping from the monitor's four
 * states to these would be implicit in a `when` somebody has to remember to update. Naming
 * them here puts the translation in the data layer, where a missing case is a compiler error
 * in the one file that has to be fixed.
 *
 * ## There is no `title` here, and that is the one real deviation from the original
 *
 * The original gave each case a `title` returning English text. This module may not depend on
 * `:packages:design-system` — the architecture table allows a domain module `:packages:core`
 * and `:packages:domain` and nothing else — so a title here would either be a hardcoded
 * string the project's recorded "Strings" decision forbids, or a resource lookup this module
 * cannot name. Presentation maps each case to a `Res.string` instead, which is the same
 * decision the other two features already made.
 */
enum class ConnectivitySummary {
    Online,
    Offline,
    NeedsConnection,
    Unknown,
    ;

    val isOnline: Boolean get() = this == Online

    /** Whether a sync cycle is worth starting. Asked here rather than derived per caller. */
    val allowsSync: Boolean get() = this != Offline && this != NeedsConnection
}

/**
 * The state of sync as a screen renders it, with no knowledge of the engine.
 *
 * One case per published sync status rather than a boolean plus a message, because the seven
 * states are genuinely different things to a user: "waiting for a connection" is not a
 * failure, and "signed out" is not something to retry. Collapsing them would force the screen
 * to invent the distinction back out of the report.
 *
 * Not `com.tasktracker.sync.SyncStatus` for the reason [ConnectivitySummary] is not
 * `Connectivity`: the engine's status carries a `SyncReport` and an `Instant`, and a screen
 * that could read those would start reading them.
 */
data class SyncActivity(
    val phase: Phase = Phase.Idle,
    /** Entries still in the queue, dead rows included. */
    val queueDepth: Int = 0,
    /** Rows the server refused permanently. Kept rather than dropped, so this can be
     * non-zero and is then the only sign that something is stuck. */
    val deadLetterCount: Int = 0,
    val failures: List<String> = emptyList(),
) {
    /**
     * The seven states, and not seven booleans.
     *
     * `Idle` is also the default, so an activity constructed with nothing — what a screen
     * starts on before anything has reported — is a memberwise default rather than a
     * `companion object` constant, because the "nothing yet" value is only ever the one the
     * model starts on.
     */
    enum class Phase {
        /** Nothing has run yet, or the last cycle has already been reported. */
        Idle,

        /** Blocked on a network path. Queued work is safe. */
        WaitingForConnection,
        Syncing,
        UpToDate,

        /** Failed for a reason that will be retried. */
        Retrying,

        /** Failed and will not be retried on its own. */
        Failed,
        SignedOut,
    }

    /** Whether a cycle is running, which is what the Sync Now control is disabled on. */
    val isSyncing: Boolean get() = phase == Phase.Syncing

    /**
     * How many of the user's edits are still only on this device, or `null` when there is
     * nothing worth saying. The view renders the row only when it has a number, so the rule
     * for "is the queue worth mentioning" is written once instead of in a `>` at the call
     * site.
     */
    val outstandingCount: Int? get() = queueDepth.takeIf { it > 0 }

    /** Dead rows, or `null` when there are none. */
    val stuckCount: Int? get() = deadLetterCount.takeIf { it > 0 }
}

/**
 * What a request to sync actually did, from the user's point of view.
 *
 * `SyncCoordinator` answers a trigger with an optional report, where `null` means "dropped" —
 * which is right for a timer and useless for a button. A person who taps Sync Now and sees
 * nothing happen has been told nothing, so the three outcomes are named separately and the
 * screen renders the difference.
 *
 * No `message` property, for the reason [ConnectivitySummary] has no `title`: presentation
 * owns the wording.
 */
enum class SyncOutcome {
    /** The cycle started. The happy path, and the one with nothing to say. */
    Started,

    /** A cycle was already in flight, so this trigger was dropped. */
    AlreadyRunning,

    /** No usable network path, so nothing was asked of the coordinator at all. */
    Offline,
}

/**
 * The app's version, as the About section reports it.
 *
 * Both parts can be empty, and then [display] is `null` so the row is hidden. That is the
 * honest answer for a build whose bundle has no version keys: a fallback number would be a
 * number nobody shipped.
 */
data class AppVersion(val marketing: String, val build: String) {

    /**
     * `"1.0 (1)"`, or just the marketing version when there is no build number.
     *
     * `trim()` on both, so a plist padded with whitespace reports as absent rather than
     * rendering a version with leading spaces in it.
     *
     * Formatting rather than wording, which is why this one survived the move out of the
     * original: it decides *which* value is shown, not *what it says*.
     */
    val display: String?
        get() {
            val shownMarketing = marketing.trim()
            if (shownMarketing.isEmpty()) return null
            val shownBuild = build.trim()
            return if (shownBuild.isEmpty()) shownMarketing else "$shownMarketing ($shownBuild)"
        }
}