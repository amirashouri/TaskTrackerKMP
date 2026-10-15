package com.tasktracker.featuresettingspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncNow
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drives the Settings screen.
 *
 * Holds the three readings the screen renders and the three commands it dispatches, and owns
 * no rule beyond the one [SyncNow] states out loud: a request that cannot start has to say
 * why. Whether a cycle is *worth* running, and what a dead network path means for queued work,
 * are the coordinator's answers, fetched through the port so there is one copy of each.
 *
 * ## [errorMessage] is an outcome, not a sentence
 *
 * The original's `errorMessage` held `SyncOutcome.message`, and the project's recorded
 * "Strings" decision forbids that: a model is a plain class and must stay testable without a
 * composition, so it cannot read a resource, and formatting the message here would put the
 * wording in a second place from every other user-facing sentence in the app. So this holds
 * the [SyncOutcome] itself and the screen maps it to the string it owns — the same trade
 * `RemovalNotice` makes in the projects feature.
 *
 * ## [isForceOffline] is held as intent and never derived from [connectivity]
 *
 * An override and a genuinely dead path both report [ConnectivitySummary.Offline], so a
 * derived switch would light up for a device that is merely in a tunnel — and the user would
 * then turn *off* a setting that was never on. This is the reason [connectivity] and this
 * flag are separate state and not one value.
 *
 * ## Observation is the caller's scope, as in the projects feature
 *
 * `observe(scope)` rather than a `CoroutineScope` of its own, so the model cannot outlive the
 * composition that shows it. The three collections run as three children of the caller's
 * scope, so [stopObserving] cancels all three at once.
 */
class SettingsModel(private val port: SettingsPort) {

    private val syncNow = SyncNow(port)

    var connectivity: ConnectivitySummary by mutableStateOf(ConnectivitySummary.Unknown)
        private set

    var activity: SyncActivity by mutableStateOf(SyncActivity())
        private set

    /**
     * The outcome of the last command, or `null` when there is nothing to say.
     *
     * Only the failures that a control could not express are here. A sync that cannot start
     * because there is no network is not an error state the screen sits in — it is an answer
     * to a tap.
     */
    var commandOutcome: SyncOutcome? by mutableStateOf(null)
        private set

    /**
     * The sign-out the server could not confirm, or `null` when there is nothing to answer.
     *
     * Held as the [AppError] rather than as a sentence, for the reason [commandOutcome] is held
     * as a [SyncOutcome]: a model is a plain class and must stay testable without a composition,
     * so it cannot read a resource, and formatting the message here would put the wording in a
     * second place from every other user-facing sentence in the app.
     *
     * Non-null is what the screen renders the force-sign-out popup for. It is the only state in
     * this model the user has to *answer* rather than merely read — see [forceSignOut].
     */
    var signOutFailure: AppError? by mutableStateOf(null)
        private set

    /** Whether the user has asked to force the offline state. See the class note. */
    var isForceOffline: Boolean by mutableStateOf(false)
        private set

    var userDetails: UserDetails by mutableStateOf(UserDetails())
        private set

    /** Read once, because a running app's version cannot change. */
    val version: AppVersion = port.appVersion()

    private var observation: Job? = null

    /**
     * Begins watching. Idempotent, and safe to call again after [stopObserving].
     *
     * [reload] first, then the two streams, so the first frame a user sees is the current
     * answer rather than the defaults. Both streams are taken up front rather than reached for
     * inside a child: taking them before any child starts is what removes the window in which
     * an update could be published to nobody.
     */
    fun observe(scope: CoroutineScope) {
        if (observation != null) return
        observation = scope.launch {
            reload()
            val connectivityUpdates: Flow<ConnectivitySummary> = port.connectivityUpdates()
            val syncUpdates: Flow<SyncActivity> = port.syncUpdates()
            val userDetailsUpdates: Flow<UserDetails> = port.userDetailsUpdates()
            launch { connectivityUpdates.collect { connectivity = it } }
            launch { syncUpdates.collect { activity = it } }
            launch { userDetailsUpdates.collect { userDetails = it } }
        }
    }

    fun stopObserving() {
        observation?.cancel()
        observation = null
    }

    /** Reads both values once, which is what the screen shows until a stream says otherwise. */
    suspend fun reload() {
        connectivity = port.connectivity()
        activity = port.syncActivity()
        userDetails = port.userDetails()
    }

    // MARK: - Commands

    /**
     * Dispatches the switch's new value.
     *
     * This *does* set [isForceOffline], unlike the original, which deliberately did not: there
     * the switch owned the binding and re-reading it back would undo the move for the one case
     * that matters — forcing offline while genuinely offline. Here the model owns the flag and
     * the screen renders a checkbox bound to [isForceOffline] and calls this on change, so the
     * flag is already correct and there is nothing to re-read. Both halves of the original's
     * rule survive: the value the user chose is what is stored, and it is never derived from
     * what the monitor reports.
     */
    suspend fun applyForceOffline(forced: Boolean) {
        isForceOffline = forced
        port.setForcedOffline(forced)
    }

    /** Sets the flag without dispatching, for a screen restoring a remembered choice. */
    fun restoreForceOffline(forced: Boolean) {
        isForceOffline = forced
    }

    /** Asks for a cycle and reports what became of the request. */
    suspend fun requestSync() {
        commandOutcome = syncNow()
    }

    /**
     * Ends the session. What that tears down, and where it routes, belongs to the app — which
     * is why this is a port call and not a call to the auth feature.
     *
     * ## A failure is held rather than thrown, and holding it is what raises the popup
     *
     * The port's sign-out asks the server to revoke the refresh token family before it forgets
     * anything, so a server that cannot be reached leaves the session standing — deliberately,
     * rather than signing a user out behind their back while a possibly-live token sits on the
     * server. The failure therefore arrives here as an [AppError], and the screen turns it into
     * the one thing the user can do about it: end the session on this device anyway, which is
     * [forceSignOut].
     *
     * ## [CancellationException] is rethrown, and that is not tidiness
     *
     * The screen is disposed while this call is in flight whenever the user leaves the tab
     * mid-sign-out. A `runCatching` here would read that as a failed sign-out and arm a popup
     * for a screen that no longer exists — a dialog raised over whatever the user navigated to
     * next, which is the worst shape this bug can take because it looks like a different bug.
     */
    suspend fun signOut() {
        try {
            port.endSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            signOutFailure = failure
        }
    }

    /**
     * Ends the session without asking the server, and erases what it left behind.
     *
     * The action the popup offers. [signOutFailure] is cleared *before* the call rather than
     * after, so the failure the user has just answered cannot be re-raised by a recomposition
     * in the window between this returning and the route change it causes.
     */
    suspend fun forceSignOut() {
        signOutFailure = null
        port.forceEndSession()
    }

    /**
     * Clears [signOutFailure], for a user who declined to force the sign-out.
     *
     * Dismissing leaves the session exactly as it was — still signed in, still on Settings —
     * which is the honest outcome: nothing was revoked and nothing was erased, so there is
     * nothing the screen should pretend happened.
     */
    fun dismissSignOutFailure() {
        signOutFailure = null
    }

    suspend fun applyUserDetails(details: UserDetails) {
        userDetails = details
        port.setUserDetails(details)
    }

    fun restoreUserDetails(details: UserDetails) {
        userDetails = details
    }

    /** Clears [commandOutcome], for a screen that shows the banner only until it is read. */
    fun dismissCommandOutcome() {
        commandOutcome = null
    }
}