package com.tasktracker.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tunables for one sync cycle.
 *
 * Every bound here exists to stop a cycle from running unbounded. A phone that has been
 * offline for a month wakes up with thousands of queued changes, and a sync loop with no
 * ceiling would keep the radio and the CPU busy until the app was killed — taking the
 * user's unsynced work with it.
 */
data class SyncConfiguration(
    /** How many entries go to the server in one push. */
    val pushBatchSize: Int = DEFAULT_PUSH_BATCH_SIZE,
    /** Ceiling on push batches per cycle. Bounding this leaves the *next* cycle to finish
     * the backlog, so a huge queue trickles out instead of monopolising one long run. */
    val maxPushBatchesPerCycle: Int = DEFAULT_MAX_PUSH_BATCHES,
    /** Attempts before an entry is dead-lettered. */
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    /** How many changes one pull page asks for. */
    val pullPageSize: Int = DEFAULT_PULL_PAGE_SIZE,
    /** Ceiling on pages per scope per cycle, for the same reason as the push bound. */
    val maxPullPagesPerScope: Int = DEFAULT_MAX_PULL_PAGES,
    /** Streams this app pulls. */
    val scopes: List<SyncScope> = SyncScope.all,
) {
    init {
        // The engine coerces every bound with `max(1, …)` at the loop, because a zero here
        // would otherwise mean "no loop at all" — a configuration typo silently turning sync
        // off, rather than a configuration that is obviously wrong. Refusing it at
        // construction makes the mistake loud and immediate instead.
        require(pushBatchSize > 0) { "pushBatchSize must be positive, was $pushBatchSize" }
        require(maxPushBatchesPerCycle > 0) {
            "maxPushBatchesPerCycle must be positive, was $maxPushBatchesPerCycle"
        }
        require(maxAttempts > 0) { "maxAttempts must be positive, was $maxAttempts" }
        require(pullPageSize > 0) { "pullPageSize must be positive, was $pullPageSize" }
        require(maxPullPagesPerScope > 0) {
            "maxPullPagesPerScope must be positive, was $maxPullPagesPerScope"
        }
    }

    /**
     * The push loop's actual iteration count.
     *
     * `max(1, …)` is kept in the engine as well as checked here, so a configuration built
     * by hand inside the module's own tests cannot reintroduce a zero-batch cycle.
     */
    val pushBatchCeiling: Int get() = maxOf(1, maxPushBatchesPerCycle)

    /** The pull loop's actual iteration count, per scope. */
    val pullPageCeiling: Int get() = maxOf(1, maxPullPagesPerScope)

    companion object {
        const val DEFAULT_PUSH_BATCH_SIZE: Int = 50
        const val DEFAULT_MAX_PUSH_BATCHES: Int = 10
        const val DEFAULT_MAX_ATTEMPTS: Int = 5
        const val DEFAULT_PULL_PAGE_SIZE: Int = 100
        const val DEFAULT_MAX_PULL_PAGES: Int = 10

        /** Fast failure and a single attempt, for tests and for a "retry now" button where
         * the user is watching. */
        val eager: SyncConfiguration = SyncConfiguration(
            pushBatchSize = 10,
            maxPushBatchesPerCycle = 1,
            maxAttempts = 1,
            pullPageSize = 20,
            maxPullPagesPerScope = 1,
        )
    }
}

/** Timing for [SyncCoordinator]'s periodic loop. */
data class SyncSchedule(
    val interval: Duration = 60.seconds,
    /** Whether to sync on start. Almost always yes: a launch is the moment the app is most
     * likely to have a network path and enough battery to use it. */
    val syncsOnStart: Boolean = true,
    /** Whether to react to connectivity returning. */
    val syncsOnReconnect: Boolean = true,
) {
    init {
        require(interval > Duration.ZERO) { "interval must be positive, was $interval" }
    }

    companion object {
        /** Used by tests: a long interval, so only explicit triggers run a cycle. */
        val manual: SyncSchedule = SyncSchedule(
            interval = 3600.seconds,
            syncsOnStart = false,
            syncsOnReconnect = true,
        )
    }
}
