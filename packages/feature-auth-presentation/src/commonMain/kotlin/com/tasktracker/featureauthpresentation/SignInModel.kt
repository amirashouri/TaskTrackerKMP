package com.tasktracker.featureauthpresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SignIn
import com.tasktracker.featureauthdomain.UserIdentity
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drives the sign-in screen.
 *
 * ## The fields are Compose state, and that is the whole design decision
 *
 * The original is a `@MainActor @Observable` class, and the port keeps it as a plain class
 * whose properties are `mutableStateOf`. Not a `ViewModel`: this module builds for the JVM
 * host as well as for Android and iOS, and an AndroidX `ViewModel` would make the model
 * untestable on two of its three test targets, which is most of the reason it exists. Not a
 * `StateFlow` either, for the same reason in the other direction — a flow needs a collector,
 * and a sign-in form is read in a composition, not a stream.
 *
 * ## The transition out of sign-in is deliberately not modelled
 *
 * On success the session port publishes [com.tasktracker.featureauthdomain.SessionState.SignedIn]
 * and the app's own coordinator is what moves the router. [Phase.SignedIn] therefore exists
 * to record that *this* attempt succeeded and to stop the form accepting input — not to
 * drive navigation. A feature that navigated itself would need a dependency on
 * `:packages:routing` to do it, and the auth feature having an opinion about the app's
 * navigation stack is the wrong dependency to add.
 *
 * ## Every failure becomes a [String], never re-worded
 *
 * [AppError] already carries user-presentable text in its `message`, and a view that
 * re-words a failure is a view that will disagree with it. The only case that gets a
 * fallback string is one that is not an [AppError] at all, which should not happen — and
 * which gets a generic message rather than a `toString()` for exactly that reason.
 */
class SignInModel(
    session: AuthSessionPort,
) {

    /**
     * The screen's state, as three cases rather than a pair of booleans.
     *
     * A sealed interface because `isSubmitting` and `isSignedIn` are the two-sources-of-truth
     * shape this type exists to avoid: with a `Phase` the impossible combinations are
     * unrepresentable and a `when` over it is exhaustive, so a fourth state cannot be added
     * without every reader handling it.
     */
    sealed interface Phase {
        /** Nothing in flight. The only state the form accepts input in. */
        data object Idle : Phase

        /** A request is in flight. The form is inert. */
        data object Submitting : Phase

        /** The session is live. The screen does not navigate on this — see the class note. */
        data class SignedIn(val identity: UserIdentity) : Phase
    }

    /** The address as typed. Normalisation is [SignIn]'s job, not this screen's. */
    var email: String by mutableStateOf("")

    /**
     * The password as typed, and deliberately cleared on every failure.
     *
     * Not a stored field elsewhere: clearing it here is what stops a retry from re-sending a
     * secret the server has just rejected, and it leaves the user with a visibly empty field
     * rather than one that looks accepted.
     */
    var password: String by mutableStateOf("")

    var phase: Phase by mutableStateOf(Phase.Idle)
        private set

    /** The last failure's text, or `null`. Cleared when a new attempt starts. */
    var errorMessage: String? by mutableStateOf(null)
        private set

    private val signIn = SignIn(session)

    /**
     * False while a request is in flight, or while either field is blank.
     *
     * Deliberately *not* "while the address is invalid": the screen has no business deciding
     * what a valid address is — [SignIn] owns that — and a rule that greys out the button for
     * a typo the user is still typing makes the form feel broken. The address is only
     * normalised once it is actually submitted.
     *
     * ## A recorded deviation: the original also allows this after a success
     *
     * `canSubmit` is `case .idle, .signedIn: !email.isEmpty && !password.isEmpty` in the
     * original, so its documentation — "false while a request is in flight, or while either
     * field is blank" — is accurate but narrower than the implementation: a signed-in form
     * is still submittable. In a correctly wired app the difference is unobservable, because
     * the session bus publishes and the screen is replaced before a second tap is possible.
     * It becomes observable only in a *mis*wired one, where this returns false and the
     * original would let the user re-authenticate against a live session.
     *
     * Treating a documented invariant as narrower than the code around it is how that code
     * ends up with a behaviour nobody chose, so the implementation was brought in line with
     * the intent: once signed in, the form is not a form any more.
     */
    val canSubmit: Boolean
        get() = when (phase) {
            Phase.Submitting -> false
            is Phase.SignedIn -> false
            Phase.Idle -> email.isNotEmpty() && password.isNotEmpty()
        }

    /** True while the request is in flight, which is what swaps the label for a spinner. */
    val isSubmitting: Boolean get() = phase is Phase.Submitting

    /**
     * Authenticates, if the form is in a state that allows it.
     *
     * Re-checks [canSubmit] rather than trusting the caller: the button's enabled state is a
     * rendering of this property, and a disabled control is not a guarantee — a keyboard's
     * "go" action and an accessibility activation can both reach here regardless.
     */
    suspend fun submit() {
        if (!canSubmit) return

        phase = Phase.Submitting
        errorMessage = null
        try {
            val identity = signIn(email = email, password = password)
            phase = Phase.SignedIn(identity)
        } catch (cancellation: CancellationException) {
            // A cancelled submit is a cancelled submit, not a failed one. Catching it into
            // `errorMessage` would show "could not sign in" for a screen the user navigated
            // away from, and — worse — would set state on a composition that is going away.
            throw cancellation
        } catch (failure: AppError) {
            password = ""
            phase = Phase.Idle
            errorMessage = failure.message
        } catch (_: Exception) {
            // Nothing should reach here: the port throws AppError and CancellationException,
            // and both are handled. A catch-all exists because this is the one place a
            // user-visible string is produced, and an uncaught exception here is a crash on a
            // form rather than a failed attempt. The text is generic on purpose: an unknown
            // failure's own message may contain a stack trace or an internal identifier.
            password = ""
            phase = Phase.Idle
            errorMessage = "Could not sign in. Please try again."
        }
    }

    /** Clears the banner, for a dismiss action or a fresh attempt. */
    fun dismissError() {
        errorMessage = null
    }
}
