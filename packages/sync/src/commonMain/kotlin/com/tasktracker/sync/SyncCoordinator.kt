package com.tasktracker.sync

import com.tasktracker.core.DateProvider
import com.tasktracker.core.SystemDateProvider
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.network.Connectivity
import com.tasktracker.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Instant

/**
 * Decides *when* to sync, and makes sure only one cycle runs at a time.
 *
 * [SyncEngine] knows how to sync; this knows when, which is a different problem. Three
 * callers want to sync — a timer, app lifecycle events, and the connectivity monitor — and
 * all three can fire at once. Without a single gate here they would each start a cycle, and
 * the worst case is two cycles claiming the same queued change and pushing it twice.
 *
 * ## Not an actor
 *
 * The original is a Swift `actor`, which forced every read into an `await` and made the
 * phase callback a capture of `self` (and the comment that goes with it, about the task
 * holding the coordinator until it finishes). Here the observable state lives in a
 * `MutableStateFlow`, which is thread-safe on its own, so `currentStatus` is a plain
 * property and the callback is a lambda with nothing to retain.
 */
class SyncCoordinator(
    private val engine: SyncEngine,
    private val monitor: NetworkMonitor,
    private val schedule: SyncSchedule = SyncSchedule(),
    /**
     * Where the periodic loop and the connectivity observer run.
     *
     * Injectable so tests get `runTest`'s virtual clock: the loop's `delay` is virtual time
     * there, so a 50 ms interval is asserted by advancing 50 ms rather than by sleeping and
     * polling a deadline. The default is a real one, and [stop] cancels the two children
     * rather than the scope — a test that hands in a scope does not want its own cancelled
     * out from under it.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dateProvider: DateProvider = SystemDateProvider(),
) {
    private val logger = TTLogger.sync

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(lastSyncedAt = null))

    /** The report and the time of the last *successful* cycle, in one value. */
    private val outcome = MutableStateFlow<Outcome?>(null)

    /**
     * Single-flight.
     *
     * `tryLock` rather than `lock` because dropping a trigger is the required behaviour:
     * a caller that is refused must get `null` back immediately, and a suspending `lock`
     * would turn the second caller into a third concurrent cycle once the first finished.
     */
    private val gate = Mutex()

    private var loop: Job? = null
    private var connectivityJob: Job? = null

    private class Outcome(val report: SyncReport, val syncedAt: Instant?)

    // MARK: - Observation

    val currentStatus: SyncStatus get() = status.value

    val lastReport: SyncReport? get() = outcome.value?.report

    val lastSyncedAt: Instant? get() = outcome.value?.syncedAt

    val isSyncing: Boolean get() = status.value.isSyncing

    /**
     * Whether a cycle would run right now. Lets a view enable or disable a "Sync now"
     * button without duplicating the offline rule.
     */
    val canSyncNow: Boolean get() = monitor.current.allowsSync

    /**
     * A fresh stream for each subscriber, so a view appearing and disappearing does not steal
     * the events a still-visible view is waiting for.
     *
     * A `StateFlow` rather than core's `Broadcaster`, for the same reason the network monitor
     * uses one: the original already dropped a status identical to the current one
     * (`guard next != status`), so nothing is lost by conflating — and replaying the current
     * value to a new subscriber is what lets a screen opened mid-cycle render the phase
     * immediately.
     */
    fun statusUpdates(): StateFlow<SyncStatus> = status.asStateFlow()

    // MARK: - Lifecycle

    /**
     * Starts the periodic loop and the connectivity observer. Idempotent.
     *
     * `start()` is `fun` and not `suspend`, where the original needed `await`: the work goes
     * into [scope] and returns immediately.
     */
    fun start() {
        if (loop != null || connectivityJob != null) return

        connectivityJob = scope.launch {
            monitor.updates().collect { connectivity -> handle(connectivity) }
        }

        loop = scope.launch {
            if (schedule.syncsOnStart) {
                synchronize(SyncTrigger.AppLaunch)
            }
            while (isActive) {
                delay(schedule.interval)
                if (!isActive) return@launch
                synchronize(SyncTrigger.Periodic)
            }
        }
        logger.info { logMessage { append("sync: coordinator started") } }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        connectivityJob?.cancel()
        connectivityJob = null
        logger.info { logMessage { append("sync: coordinator stopped") } }
    }

    // MARK: - Triggering

    /**
     * Runs a cycle unless one is already running.
     *
     * @return the report, or `null` when the trigger was dropped — a cycle is already in
     * flight, or there is no usable network path. `null` is not a failure: the work is either
     * already happening or safely queued.
     */
    suspend fun synchronize(trigger: SyncTrigger): SyncReport? {
        if (!gate.tryLock()) {
            logger.info {
                logMessage { append("sync: "); append(trigger.wireName); append(" dropped, a cycle is already running") }
            }
            return null
        }
        try {
            val connectivity = monitor.current
            if (!connectivity.allowsSync) {
                publish(SyncStatus.WaitingForConnectivity)
                logger.info {
                    logMessage {
                        append("sync: ")
                        append(trigger.wireName)
                        append(" skipped, network path is ")
                        append(connectivity.name)
                    }
                }
                return null
            }

            val report = engine.run(trigger) { phase -> publish(SyncStatus.Syncing(phase)) }
            val previous = outcome.value
            val now = dateProvider.now()
            outcome.value = Outcome(
                report = report,
                // Only a success moves the clock. A deferred cycle did not sync anything, and
                // a "last synced" that ticks forward on every failed attempt is a lie the
                // user reads as "everything is up to date".
                syncedAt = if (report.isSuccess) now else previous?.syncedAt,
            )

            publish(
                when {
                    report.requiresSignOut -> SyncStatus.SignedOut
                    report.isSuccess -> SyncStatus.Succeeded(report, at = now)
                    report.isDeferred -> SyncStatus.Deferred(report)
                    else -> SyncStatus.Failed(report)
                },
            )
            return report
        } finally {
            gate.unlock()
        }
    }

    /**
     * Syncs when a usable path appears.
     *
     * The transition is the signal, not the level: `NWPathMonitor` reports `Unknown` first
     * and then a real value, and treating that first report as "came online" would sync on
     * every single launch.
     */
    private suspend fun handle(connectivity: Connectivity) {
        if (!schedule.syncsOnReconnect || !connectivity.allowsSync) {
            if (!connectivity.allowsSync) {
                publish(SyncStatus.WaitingForConnectivity)
            }
            return
        }
        // `Unknown` is the monitor's starting value, not a recovery event.
        if (connectivity != Connectivity.Satisfied) return
        synchronize(SyncTrigger.ConnectivityRestored)
    }

    // MARK: - Status

    private fun publish(next: SyncStatus) {
        if (next == status.value) return
        status.value = next
    }
}

/**
 * Whether a sync cycle is worth starting.
 *
 * `Unknown` deliberately counts as "try". A freshly launched app has not heard from
 * `NWPathMonitor` yet, and treating that as offline would leave a device with a perfectly
 * good network sitting on an unsynced queue until the next launch. Only an explicit
 * negative path cancels work.
 *
 * This is the original's `allowsSync`, kept here rather than on `Connectivity` for the
 * reason the original kept it here: a view that wants to know should ask
 * [SyncCoordinator.canSyncNow] rather than learn the policy, so there is one place the rule
 * is written down. `:packages:network` answers the different question of whether a single
 * request is worth attempting, where `Unknown` is not good enough.
 */
val Connectivity.allowsSync: Boolean get() = !isKnownUnusable
