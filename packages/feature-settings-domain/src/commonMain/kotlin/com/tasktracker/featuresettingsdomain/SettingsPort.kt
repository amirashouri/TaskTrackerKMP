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

    suspend fun userDetails(): UserDetails

    fun userDetailsUpdates(): Flow<UserDetails>

    suspend fun setUserDetails(details: UserDetails)

    /** Read once, not streamed: a running app's version cannot change. */
    fun appVersion(): AppVersion
}