package com.tasktracker.featuresettingspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.RetryStuckChanges
import com.tasktracker.featuresettingsdomain.ServerAddressSettings
import com.tasktracker.featuresettingsdomain.ServerAddressValidation
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
 * The open server-address editor: what the user has typed, whether 6.3's check is in flight,
 * and the check's own answer.
 *
 * Its **null-ness is the sheet's visibility** — a non-null editor is the only reason the screen
 * shows the sheet, so there is no second boolean that could disagree with it and leave a sheet
 * open over a closed state. The failure is the domain [ServerAddressValidation.Invalid] rather
 * than a sentence, for the reason [SettingsModel] gives about `commandOutcome`: a model is a
 * plain class and must not read a resource.
 */
data class ServerAddressEditor(
    val draft: String,
    val isChecking: Boolean = false,
    val failure: ServerAddressValidation.Invalid? = null,
)

/**
 * What the last server-address command did, in the model's words rather than the screen's.
 *
 * Both cases share one promise — the preference is written and the running clients are not
 * reconfigured until the next launch — which is why the screen renders them through the same
 * notice and only the verb differs.
 */
enum class ServerAddressNotice { Saved, Reset }

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

    /**
     * Named differently from the method that invokes it, and that is a requirement rather than
     * a preference. A property and a function may share a name, and inside the function the
     * *function* wins — so `suspend fun retryStuckChanges() { retryStuckChanges() }` would call
     * itself forever instead of calling this. See `LiveSettingsService`'s constructor, where
     * that exact mistake was live.
     */
    private val retryStuck = RetryStuckChanges(port)

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

    /**
     * The last profile write the server refused, or `null` when there is nothing to report.
     *
     * Held as the [AppError] rather than as a sentence, for the reason [signOutFailure] is: the
     * model cannot read a resource. The screen renders it as a banner over the identity block
     * and offers a dismiss, because the failed edit has already been reverted and there is no
     * second action that could make it succeed.
     */
    var profileFailure: AppError? by mutableStateOf(null)
        private set

    /**
     * The last value the server confirmed, i.e. the value to fall back to when a write fails.
     *
     * Not Compose state: the screen never renders it, and it must not cause a recomposition.
     * Set by [reload] and by every successful commit, so the revert lands on the same value the
     * adapter restored rather than on the optimistic one the user just saw rejected.
     */
    private var confirmedUserDetails: UserDetails? = null

    /**
     * The address this launch is using, and the address saved for the next, as one value.
     *
     * Two fields rather than one current address, because they genuinely differ between a save
     * and the next launch; see [ServerAddressSettings].
     */
    var serverAddress: ServerAddressSettings by mutableStateOf(
        ServerAddressSettings(inUse = null, savedOverride = null),
    )
        private set

    /** The open editor, or `null` when the sheet is closed. See [ServerAddressEditor]. */
    var serverAddressEditor: ServerAddressEditor? by mutableStateOf(null)
        private set

    /**
     * The last save or reset, or `null` when there is nothing to say.
     *
     * Cleared when the editor is opened again rather than on a timer: a notice that faded on
     * its own would leave the row showing the old address with nothing explaining why.
     */
    var serverAddressNotice: ServerAddressNotice? by mutableStateOf(null)
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
        confirmedUserDetails = userDetails
        serverAddress = port.serverAddress()
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
     * Puts the refused changes back in the queue and asks for a cycle, reporting what became
     * of the cycle.
     *
     * The same outcome state as [requestSync], because it answers the same question — whether
     * a cycle could start — and the screen must not grow a second banner saying the same
     * thing in different words. What the retry *did* to the queue is visible in [activity],
     * which the stream updates: the "Could not be sent" row drops to nothing and the "Waiting
     * to sync" row takes its place if the changes have not gone yet.
     */
    suspend fun retryStuckChanges() {
        commandOutcome = retryStuck()
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

    /**
     * Writes the edits staged by [applyUserDetails] to the signed-in user's record.
     *
     * The optimistic value is already on screen, so this is the step that either confirms it or
     * takes it back. On failure the adapter has restored the last confirmed profile and emitted
     * it; the local assignment here makes the revert immediate, so the banner and the reverted
     * field appear on the same frame rather than whenever the collector resumes.
     *
     * [CancellationException] is rethrown for the reason [signOut] gives: leaving the tab
     * mid-write must not arm a failure banner for the screen the user navigated to. The adapter
     * does not revert on cancellation either — the process is disposing the screen, and the
     * next [reload] reads the server's truth.
     */
    suspend fun saveUserDetails() {
        try {
            val saved = port.commitUserDetails()
            userDetails = saved
            confirmedUserDetails = saved
            profileFailure = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            confirmedUserDetails?.let { userDetails = it }
            profileFailure = failure
        }
    }

    /**
     * Clears [profileFailure], for a user who has read the banner.
     *
     * Dismissing leaves the reverted profile in place: the server kept the old value and the
     * screen now shows it, so there is nothing to undo and nothing to retry without a new edit.
     */
    fun dismissProfileFailure() {
        profileFailure = null
    }

    // MARK: - Server Address

    /**
     * Opens the editor, prefilled with the pending pairing address or what the next launch will
     * use.
     *
     * [prefill] is a `tasktracker://connect` link's address. It wins over both the saved
     * override and the in-use root, because it is the address the customer just chose by
     * scanning the CLI's code; showing them anything else would make the link a no-op. The
     * saved override then wins over the in-use root, because the field edits the *preference*:
     * a user who saved an address and reopened the sheet before restarting means to adjust what
     * they just typed, not to retype the old root.
     */
    fun openServerAddressEditor(prefill: String? = null) {
        serverAddressEditor = ServerAddressEditor(
            draft = prefill ?: serverAddress.savedOverride ?: serverAddress.inUse.orEmpty(),
        )
        serverAddressNotice = null
    }

    fun dismissServerAddressEditor() {
        serverAddressEditor = null
    }

    fun updateServerAddressDraft(value: String) {
        serverAddressEditor = serverAddressEditor?.copy(draft = value)
    }

    /**
     * Asks the port to check and save the draft, and holds the answer.
     *
     * The port is the whole operation — validate, probe, write — so "saved" here cannot mean
     * less than "checked". On [ServerAddressValidation.Valid] the editor closes and the notice
     * says a restart is needed; the row keeps showing [serverAddress]'s in-use root, which is
     * still what is on the wire. On [ServerAddressValidation.Invalid] the editor stays open with
     * the reason and the preference is untouched.
     *
     * `isChecking` is set before the call because the probe can take seconds, which is the only
     * feedback the field can give while it waits. A cancelled call leaves it set, deliberately:
     * the screen that would have rendered it is being disposed with the same scope.
     */
    suspend fun saveServerAddress() {
        val editor = serverAddressEditor ?: return
        serverAddressEditor = editor.copy(isChecking = true, failure = null)
        when (val result = port.setServerAddress(editor.draft)) {
            is ServerAddressValidation.Valid -> {
                serverAddress = port.serverAddress()
                serverAddressEditor = null
                serverAddressNotice = ServerAddressNotice.Saved
            }
            is ServerAddressValidation.Invalid -> {
                serverAddressEditor = editor.copy(isChecking = false, failure = result)
            }
        }
    }

    /**
     * Forgets any saved address so the next launch falls back to the build-time default.
     *
     * No probe: there is nothing to reach, only something to remove. The notice is raised for
     * the same reason a save raises one — the running clients keep the old root until the next
     * launch.
     */
    suspend fun resetServerAddress() {
        port.resetServerAddress()
        serverAddress = port.serverAddress()
        serverAddressEditor = null
        serverAddressNotice = ServerAddressNotice.Reset
    }

    /** Clears [commandOutcome], for a screen that shows the banner only until it is read. */
    fun dismissCommandOutcome() {
        commandOutcome = null
    }
}