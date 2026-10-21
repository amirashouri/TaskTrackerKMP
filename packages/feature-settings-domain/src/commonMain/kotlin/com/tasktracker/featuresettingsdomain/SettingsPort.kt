package com.tasktracker.featuresettingsdomain

import com.tasktracker.core.AppError
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the Settings screen needs, and nothing else.
 *
 * A port rather than the real services, for the reason every port here exists: the screen
 * must not be able to reach `SyncCoordinator` or `NetworkMonitor`. A view that can name an
 * actor can also await it, and a view that can await the coordinator will eventually start
 * deciding when to sync rather than asking.
 *
 * Every method is a question or a command expressed in the feature's own vocabulary, so
 * `:packages:feature-settings-data` is the only module in the workspace that knows these calls
 * are answered by a network path monitor and a sync coordinator.
 */
interface SettingsPort {

    suspend fun connectivity(): ConnectivitySummary

    /**
     * A fresh stream per subscriber.
     *
     * `Flow`, not `StateFlow`, because a replaying stream would make this method
     * unnecessary — the port could return one and [connectivity] would go away. Declaring
     * `Flow` leaves the choice to the data layer, which is where it belongs: the original had
     * to hand out a fresh `AsyncStream` per caller because `AsyncStream.Continuation` is
     * single-consumer and would silently starve the second listener, and it kept both methods
     * only because the read had to be `async` anyway. Kotlin has no such hazard, so this is
     * the one place the original's shape is kept for its own sake rather than a constraint.
     */
    fun connectivityUpdates(): Flow<ConnectivitySummary>

    /**
     * Forces the reported connectivity regardless of the real path, or clears the override.
     *
     * Phrased as the user's intent rather than as a value to install: which underlying state
     * stands for "forced offline" is the adapter's business, so the screen never has to know
     * that the answer is [ConnectivitySummary.Offline].
     */
    suspend fun setForcedOffline(forced: Boolean)

    suspend fun syncActivity(): SyncActivity

    fun syncUpdates(): Flow<SyncActivity>

    /**
     * Whether a cycle would run right now. Asked rather than worked out from connectivity,
     * because the offline rule belongs to the coordinator and a second copy of it in a view
     * would be the copy that drifts.
     */
    suspend fun canSyncNow(): Boolean

    /**
     * Runs one cycle, and says what happened to the request.
     *
     * [SyncOutcome.AlreadyRunning] is what a dropped trigger means once the offline case has
     * been ruled out by [canSyncNow]: the coordinator answers `null` for both, and only a
     * caller that has already asked can tell them apart.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun syncNow(): SyncOutcome

    /**
     * Puts the changes the server refused permanently back in the queue, rebuilding any the
     * store itself made un-sendable.
     *
     * The counterpart to the "Could not be sent" row, and it exists because that row is
     * otherwise a dead end: a refused change is never claimed again, so no number of taps on
     * [syncNow] can move it, and the only other way out was to delete the work and make it
     * again — which loses the work.
     *
     * Nothing here touches the network. Whether the re-queued changes are then *accepted* is
     * [syncNow]'s answer, which is why the screen offers both and why this one does not
     * pretend to report the outcome of a push.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun retryStuckChanges()

    /**
     * Ends the session — forgetting the credentials, tearing down the session scope and
     * routing back to sign-in.
     *
     * A closure the app injects rather than a reference to an auth type, because this module
     * may not depend on `:packages:feature-auth-domain` and must not name the session
     * coordinator: the settings screen asks for the *intent* to end a session and the shell
     * decides what that tears down.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun endSession()

    /**
     * Ends the session without asking the server, and erases what it left behind.
     *
     * The other half of [endSession], and the one a user reaches for after it has failed.
     * [endSession] asks the server to revoke the refresh token family before it forgets
     * anything, so a server that cannot be reached leaves the session standing and [endSession]
     * throwing — deliberately, rather than signing the user out behind their back while a
     * possibly-live token sits on the server. This is the answer to that: nothing is asked of
     * the network, so nothing can fail for want of one.
     *
     * What it discards is deliberately wider than [endSession]. The credentials and the session
     * caches go either way; this also empties the local store and every cached preference,
     * because the user has been told the clean sign-out could not be completed and has chosen
     * to leave nothing behind on this device. That is a decision they make, which is why it is
     * a separate method rather than a parameter on [endSession]: the two are different
     * operations with different outcomes, and only the screen knows which one was chosen.
     *
     * A closure the app injects, for the reason [endSession] gives.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun forceEndSession()

    suspend fun userDetails(): UserDetails

    fun userDetailsUpdates(): Flow<UserDetails>

    suspend fun setUserDetails(details: UserDetails)

    /**
     * Writes the pending [userDetails] edits to the signed-in user's own record.
     *
     * The screen applies an edit locally through [setUserDetails] first, so a picker moves on
     * the same frame; this is the step that makes it durable on the server. The returned value
     * is the server's confirmed copy, which may differ from the optimistic one (the server
     * trims, and it always echoes `presence`).
     *
     * Only the fields that differ from the last confirmed profile are sent. On failure the
     * adapter restores that confirmed profile before throwing, so the screen does not keep
     * showing a value the server refused; the caller reports the [AppError] and does not queue
     * the edit, because a profile write is not one of the changes the sync engine replays.
     *
     * With no server session (the offline demo) there is nothing to write to and this returns
     * the current details unchanged.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun commitUserDetails(): UserDetails

    /** Read once, not streamed: a running app's version cannot change. */
    fun appVersion(): AppVersion

    /**
     * The address this process is using, and the address saved for the next one.
     *
     * Two values rather than one, and the split is the feature: the API root is bound once,
     * while the app-scoped HTTP clients are built, so a saved change cannot take effect until
     * the next launch. The screen shows [ServerAddressSettings.inUse] and offers to edit
     * [ServerAddressSettings.savedOverride].
     */
    suspend fun serverAddress(): ServerAddressSettings

    /**
     * Checks [input] as a TaskTracker server and, only when it answers, saves the normalized
     * address for the next launch.
     *
     * Validation, the health probe and the write are one operation here rather than three the
     * screen has to sequence, because "saved" *is* "checked": a caller cannot forget the middle
     * step. The return value is the check's own answer — the screen renders the failure reason
     * and does not repeat the rule.
     */
    suspend fun setServerAddress(input: String): ServerAddressValidation

    /**
     * Forgets any saved address, so the next launch falls back to the build-time default.
     *
     * The counterpart to [setServerAddress], and the reason the setting is reversible: a
     * customer who mistypes an address, or moves off a temporary exposure, is not stranded with
     * a value only a reinstall could clear.
     */
    suspend fun resetServerAddress()
}