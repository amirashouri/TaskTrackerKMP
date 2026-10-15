package com.tasktracker.featuresettingsdata

import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.network.Connectivity
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncStatus
import com.tasktracker.sync.SyncTrigger
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [SettingsPort] over the real services.
 *
 * The only file that knows a settings screen is fed by [NetworkMonitor] and [SyncCoordinator],
 * and the only place the two vocabularies meet. Every `when` below is deliberately total — no
 * `else` — so a case added upstream lands here as a build error rather than silently
 * disappearing from the screen.
 *
 * Ending the session arrives as a closure rather than as a second port. The thing that owns
 * signing out is the app's composition root, which is also the only object allowed to see both
 * the auth feature and the session teardown; a settings package that named either would be
 * reaching across a feature boundary to no benefit.
 *
 * There are **two** such closures, and the pair is the point rather than an accident. The
 * ordinary sign-out can fail — it asks the server to revoke the refresh token family first —
 * and the failure is what the screen's popup exists to answer, so the screen has to be able to
 * tell the two apart. One closure taking a flag would put that distinction inside the app and
 * leave the service forwarding a boolean it does not understand.
 *
 * ## Both update methods are plain `map`s, and that is the whole simplification
 *
 * The original wrapped each upstream stream in an `AsyncStream` with a task, a continuation and
 * an `onTermination` that cancelled the reader — three pieces of machinery whose entire purpose
 * was to tie the consumer's cancellation to a fan-out it had started itself. Both upstreams here
 * are already `StateFlow`s, so there is nothing to start and nothing to cancel: `map` returns a
 * cold flow that collects the upstream for as long as it is collected, and cancelling the
 * collector cancels the collection. The `onTermination` bug the original had to guard against —
 * a monitor keeping a fan-out alive for a screen that has gone — cannot arise, because no fan-out
 * is ever created.
 */
class LiveSettingsService(
    private val monitor: NetworkMonitor,
    private val coordinator: SyncCoordinator,
    private val version: AppVersion,
    private val endSession: suspend () -> Unit,
    private val forceEndSession: suspend () -> Unit,
    private val defaults: UserDefaultsStore,
) : SettingsPort {

    // MARK: - Connectivity

    override suspend fun connectivity(): ConnectivitySummary = summaryFor(monitor.current)

    override fun connectivityUpdates(): Flow<ConnectivitySummary> =
        monitor.updates().map { summaryFor(it) }

    /**
     * The user's intent, translated into the one underlying state that means "forced offline".
     *
     * [ConnectivitySummary.Offline] is the answer rather than something invented here, because
     * the monitor already has a value for it and a settings-specific sentinel would have to be
     * translated back at every read.
     */
    override suspend fun setForcedOffline(forced: Boolean) {
        monitor.setOverride(if (forced) Connectivity.Unsatisfied else null)
    }

    // MARK: - Sync

    override suspend fun syncActivity(): SyncActivity = activityFrom(coordinator.currentStatus)

    override fun syncUpdates(): Flow<SyncActivity> = coordinator.statusUpdates().map { activityFrom(it) }

    override suspend fun canSyncNow(): Boolean = coordinator.canSyncNow

    /**
     * `null` means the coordinator dropped the trigger. Offline has already been ruled out by
     * `SyncNow`, so what is left is a cycle already in flight.
     */
    override suspend fun syncNow(): SyncOutcome =
        if (coordinator.synchronize(SyncTrigger.Manual) == null) {
            SyncOutcome.AlreadyRunning
        } else {
            SyncOutcome.Started
        }

    // MARK: - Session

    override suspend fun endSession() {
        endSession()
    }

    /**
     * The device-only sign-out, forwarded to the app's own closure.
     *
     * Nothing is added here — no retry, no fallback to the ordinary path. The decision about
     * what a force sign-out erases belongs to the app, and this service's whole job with these
     * two methods is to be the seam that lets a screen ask without being able to reach.
     */
    override suspend fun forceEndSession() {
        forceEndSession()
    }

    // MARK: - Version

    override fun appVersion(): AppVersion = version

    // MARK: - User Details

    private val userDetailsFlow = MutableStateFlow(UserDetails())

    override suspend fun userDetails(): UserDetails {
        // Seed from persisted values on first read. After that the flow owns the state and
        // the store is only written to, never read from again unless the flow is empty.
        if (!userDetailsFlow.value.fullName.isNotBlank() &&
            !userDetailsFlow.value.email.isNotBlank() &&
            userDetailsFlow.value.avatarId == null &&
            userDetailsFlow.value.position == null
        ) {
            defaults.value<UserDetails>(DefaultsKey.UserProfile)?.let { persisted ->
                userDetailsFlow.value = persisted
            }
            // If no persisted profile yet, seed the email from the cached auth identity so
            // the screen can always show something beside the avatar.
            if (!userDetailsFlow.value.email.isNotBlank()) {
                defaults.value<String>(DefaultsKey.CachedUserEmail)?.let { email ->
                    userDetailsFlow.value = userDetailsFlow.value.copy(email = email)
                }
            }
        }
        return userDetailsFlow.value
    }

    override fun userDetailsUpdates(): Flow<UserDetails> = userDetailsFlow

    override suspend fun setUserDetails(details: UserDetails) {
        userDetailsFlow.value = details
        // Persist so the choice survives a process restart — avatarId, fullName, position
        // and email are all kept together so a picked portrait comes back on the next launch.
        defaults.set(details, DefaultsKey.UserProfile)
    }

    // MARK: - Translation

    /**
     * Re-reads [SyncCoordinator.isSyncing] rather than trusting the published status.
     *
     * The engine publishes a phase when it changes, so a cycle that started and has not
     * reported a phase yet is invisible in `status` alone.
     */
    private fun activityFrom(status: SyncStatus): SyncActivity {
        val report = status.report
        return SyncActivity(
            // Running wins over the published phase. A cycle that has been started but has not
            // yet reported its first phase would otherwise render as whatever the last cycle
            // finished with, which reads as "nothing is happening" while it is.
            phase = if (coordinator.isSyncing) SyncActivity.Phase.Syncing else phaseFor(status),
            queueDepth = report?.queueDepth ?: 0,
            deadLetterCount = report?.deadLetterCount ?: 0,
            // Flattened here rather than carried as `SyncFailure`: the stage a failure came
            // from is a debugging detail, and the message is the whole of what a user can act
            // on.
            failures = report?.failures?.map { it.message } ?: emptyList(),
        )
    }

    /**
     * Total on purpose: no `else`. A case added to [Connectivity] upstream becomes a build
     * error in this one file, which is the only file that has to change for it — rather than
     * a screen that quietly renders the wrong word for a path the user is looking at.
     */
    private fun summaryFor(connectivity: Connectivity): ConnectivitySummary = when (connectivity) {
        Connectivity.Satisfied -> ConnectivitySummary.Online
        Connectivity.Unsatisfied -> ConnectivitySummary.Offline
        Connectivity.RequiresConnection -> ConnectivitySummary.NeedsConnection
        Connectivity.Unknown -> ConnectivitySummary.Unknown
    }

    /** Total for the same reason as [summaryFor], and one case per published status. */
    private fun phaseFor(status: SyncStatus): SyncActivity.Phase = when (status) {
        is SyncStatus.Idle -> SyncActivity.Phase.Idle
        SyncStatus.WaitingForConnectivity -> SyncActivity.Phase.WaitingForConnection
        is SyncStatus.Syncing -> SyncActivity.Phase.Syncing
        is SyncStatus.Succeeded -> SyncActivity.Phase.UpToDate
        is SyncStatus.Deferred -> SyncActivity.Phase.Retrying
        is SyncStatus.Failed -> SyncActivity.Phase.Failed
        SyncStatus.SignedOut -> SyncActivity.Phase.SignedOut
    }
}