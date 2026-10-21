package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.CheckServerAddress
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.ServerAddressSettings
import com.tasktracker.featuresettingsdomain.ServerAddressValidation
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.UserProfileChanges
import com.tasktracker.featuresettingsdomain.UserProfileGateway
import com.tasktracker.network.Connectivity
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncStatus
import com.tasktracker.sync.SyncTrigger
import com.tasktracker.userdefaults.API_BASE_URL_PREFERENCE_KEY
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

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
    /**
     * The three injected calls, and they are **not** named after the methods that forward to
     * them.
     *
     * That is not a style choice. A constructor property and a member function may share a
     * name — they compile, because one becomes `getEndSession()` and the other `endSession()`
     * — and inside the method body the *function* wins, so `override fun endSession() {
     * endSession() }` is unbounded recursion, not a call to the closure. It compiles, it reads
     * as correct, and the only thing it does at runtime is a `StackOverflowError` on the frame
     * that was supposed to end the session. The `on` prefix makes the two names distinct, so
     * the mistake cannot be written here at all.
     */
    private val onEndSession: suspend () -> Unit,
    private val onForceEndSession: suspend () -> Unit,
    /**
     * Re-queues the changes the server refused permanently.
     *
     * A closure rather than a port, for the reason the two sign-out closures are: what can
     * rebuild a refused payload is the projects repository, which lives in a feature module
     * this one is not allowed to see. The composition root is the only object holding both
     * sides, so it supplies the call and this service's whole job is to be the seam.
     *
     * Defaulted, unlike the sign-out pair, because it is not a second answer to anything —
     * a build wired without it simply has no way to un-stick a queue, which is the state
     * every build was in before this existed.
     */
    private val onRetryStuckChanges: suspend () -> Unit = {},
    /**
     * The API root this process bound its app-scoped clients to, or `null` in the offline demo.
     *
     * Passed in rather than read from the preference, and that is the decision the whole Server
     * field turns on: the preference is the *next* launch's root, while this is the one already
     * on the wire. Reading the preference here would make the row report a change the running
     * clients have not taken.
     */
    private val apiBaseUrl: String?,
    /**
     * The validate-then-probe operation, as 6.3 landed it.
     *
     * Injected rather than constructed here so the save path is testable without a socket, and
     * so this data service never has to name the Ktor probe — the composition root builds the
     * pair.
     */
    private val checkServerAddress: CheckServerAddress,
    private val defaults: UserDefaultsStore,
    /**
     * The self-profile transport, or `null` in a build with no API root (the offline demo).
     *
     * Defaulted rather than required so the demo composition and every settings test that does
     * not touch the identity block keep compiling; a null gateway means the profile stays a
     * local preference, which is exactly what those builds already do.
     */
    private val profileGateway: UserProfileGateway? = null,
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
        onEndSession()
    }

    /**
     * The device-only sign-out, forwarded to the app's own closure.
     *
     * Nothing is added here — no retry, no fallback to the ordinary path. The decision about
     * what a force sign-out erases belongs to the app, and this service's whole job with these
     * two methods is to be the seam that lets a screen ask without being able to reach.
     */
    override suspend fun forceEndSession() {
        onForceEndSession()
    }

    /**
     * Forwarded to the app's own closure, which is the only thing that can reach the store
     * that rebuilds a refused payload.
     *
     * No cycle is started here and no offline check is made, deliberately: both belong to
     * `RetryStuckChanges`, which is the object that knows the two steps have an order. A
     * service that also pushed would be a second place that decides when a cycle runs.
     */
    override suspend fun retryStuckChanges() {
        onRetryStuckChanges()
    }

    // MARK: - Version

    override fun appVersion(): AppVersion = version

    // MARK: - User Details

    private val userDetailsFlow = MutableStateFlow(UserDetails())

    /**
     * The last profile the server confirmed, and the base for the next delta.
     *
     * Distinct from [userDetailsFlow] on purpose. The flow is what the screen shows and carries
     * optimistic edits; this is what the server is known to hold, so a failed write can be
     * reverted to it and the next write sends only what actually differs.
     */
    private var confirmedUserDetails: UserDetails? = null

    /**
     * Serialises commits.
     *
     * Two pickers tapped in quick succession stage two optimistic values; without this, the
     * first response could land after the second edit and overwrite it with an older profile.
     * The mutex makes the second commit read the first one's confirmed value.
     */
    private val profileMutex = Mutex()

    /**
     * Reads the profile, preferring the server and falling back to the persisted copy.
     *
     * A GET failure is not surfaced: Settings must open on a cached profile when the device is
     * offline, and there is no field to attach the error to. The fallback is recorded as the
     * confirmed value so a later edit still sends a delta against what the user is looking at.
     */
    override suspend fun userDetails(): UserDetails {
        seedUserDetails()

        val gateway = profileGateway ?: return userDetailsFlow.value
        return try {
            val remote = gateway.load()
            confirmedUserDetails = remote
            userDetailsFlow.value = remote
            defaults.set(remote, DefaultsKey.UserProfile)
            remote
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AppError) {
            confirmedUserDetails = userDetailsFlow.value
            userDetailsFlow.value
        }
    }

    /**
     * Seeds from persisted values on first read. After that the flow owns the state and the
     * store is only written to, never read from again unless the flow is empty.
     */
    private suspend fun seedUserDetails() {
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
    }

    override fun userDetailsUpdates(): Flow<UserDetails> = userDetailsFlow

    override suspend fun setUserDetails(details: UserDetails) {
        userDetailsFlow.value = details
        // Persist so the choice survives a process restart — avatarId, fullName, position
        // and email are all kept together so a picked portrait comes back on the next launch.
        defaults.set(details, DefaultsKey.UserProfile)
    }

    /**
     * Sends only the fields the user changed since the last confirmed profile.
     *
     * ## Why the snapshot is taken before the call, and restored on failure
     *
     * The screen has already applied the edit, so by the time this runs [userDetailsFlow] is the
     * optimistic value. If the server refuses, leaving that value in the flow would make the
     * store and the screen agree on something the server never accepted. Restoring the snapshot
     * first, then rethrowing, means every observer sees the same reverted profile the caller
     * reports the failure for.
     *
     * ## Cancellation does not revert
     *
     * A cancelled commit is a screen being disposed mid-write, not a refused write: the request
     * may have reached the server. Reverting locally would show a value the server might not
     * hold; leaving the optimistic value lets the next read correct it, and the next read is
     * what opening Settings does anyway.
     */
    override suspend fun commitUserDetails(): UserDetails = profileMutex.withLock {
        val gateway = profileGateway ?: return@withLock userDetailsFlow.value

        val edited = userDetailsFlow.value
        val confirmed = confirmedUserDetails ?: edited
        val changes = changesBetween(confirmed, edited)

        if (changes.isEmpty) {
            confirmedUserDetails = edited
            return@withLock edited
        }

        try {
            val saved = gateway.update(changes)
            confirmedUserDetails = saved
            userDetailsFlow.value = saved
            defaults.set(saved, DefaultsKey.UserProfile)
            saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            userDetailsFlow.value = confirmed
            defaults.set(confirmed, DefaultsKey.UserProfile)
            throw failure
        }
    }

    /**
     * The nullable fields are compared as a whole: `null` here means "the screen could not
     * represent the server's value" (free-text position, no cached presence), and treating that
     * as "unchanged" is what keeps an unrelated name edit from clearing it.
     */
    private fun changesBetween(confirmed: UserDetails, edited: UserDetails): UserProfileChanges =
        UserProfileChanges(
            fullName = edited.fullName.takeIf { it != confirmed.fullName },
            position = edited.position.takeIf { it != confirmed.position },
            avatarId = edited.avatarId.takeIf { it != confirmed.avatarId },
            status = edited.status.takeIf { it != confirmed.status },
            statusText = edited.statusText.takeIf { it != confirmed.statusText },
        )

    // MARK: - Server Address

    /**
     * The in-use root from the constructor, plus the raw override the next launch will read.
     *
     * The override is read through the store's **raw**-key overload, so the value is exactly the
     * one 6.1's resolver reads at startup — same key, same verbatim spelling, no namespace. A
     * blank value is treated as no override: an empty key is not a server address, and reporting
     * it as `isCustom` would offer a Reset that has nothing to clear.
     */
    override suspend fun serverAddress(): ServerAddressSettings = ServerAddressSettings(
        inUse = apiBaseUrl,
        savedOverride = defaults.value(API_BASE_URL_PREFERENCE_KEY)?.takeIf { it.isNotBlank() },
    )

    /**
     * Saves only what the probe has just answered for.
     *
     * The validator normalises the address, so what lands in the preference is the canonical
     * form rather than the user's typing; that is the same string the resolver would have to
     * build a client from, and normalising at read time would be a second chance to disagree.
     * An `Invalid` result writes nothing — the sheet stays open on the reason.
     *
     * The write uses the raw-key overload and ignores its boolean, for the reason 6.1's read
     * ignores a missing key: this store's `set(String, String)` returns `true` unconditionally.
     * A preference write is local and synchronous; the failure mode this would otherwise have to
     * model does not exist here.
     */
    override suspend fun setServerAddress(input: String): ServerAddressValidation {
        val result = checkServerAddress.check(input)
        if (result is ServerAddressValidation.Valid) {
            defaults.set(result.baseUrl, API_BASE_URL_PREFERENCE_KEY)
        }
        return result
    }

    override suspend fun resetServerAddress() {
        defaults.remove(API_BASE_URL_PREFERENCE_KEY)
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