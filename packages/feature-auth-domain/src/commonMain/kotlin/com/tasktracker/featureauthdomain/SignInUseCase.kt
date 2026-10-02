package com.tasktracker.featureauthdomain

import com.tasktracker.core.AppError
import com.tasktracker.domain.AuthRules
import kotlin.coroutines.cancellation.CancellationException

/**
 * Signs in, after checking the inputs.
 *
 * ## Why this is not a one-line pass-through
 *
 * [AuthRules] lives in `:packages:domain` because an email address is a property of the
 * account, not of a screen; what lives here is the *decision* to apply those rules before
 * the port is touched. That distinction is worth the extra file because the alternative —
 * letting each caller normalise — is how `Ada@Example.com ` and `ada@example.com` end up as
 * two accounts on the server, with the user staring at a "that password is wrong" message
 * that is really a normalisation bug.
 *
 * The rules are applied in the data layer as well, and that is not duplication to be
 * tidied away later: this is the layer the UI actually goes through, and a rejected attempt
 * should never reach the network or a store. The data-layer check is the backstop for
 * callers that are not this use case.
 */
class SignIn(
    private val session: AuthSessionPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(email: String, password: String): UserIdentity {
        val email = AuthRules.normaliseEmail(email)
        val password = AuthRules.normalisePassword(password)
        return session.signIn(email = email, password = password)
    }
}

/**
 * Re-establishes a session on launch.
 *
 * Separate from [AuthSessionPort.restore] even though the port declares it, for the same
 * reason [SignIn] is a separate file: the use case is the place where the *policy* about a
 * restore failure lives, and here the policy is that a restore which cannot revive the
 * session is not a crash.
 *
 * A stored refresh token the server has since expired is the ordinary end of a session that
 * outlived its credentials, and it settles on [SessionState.SignedOut]. Swallowing the
 * failure is what makes "reopen the app" work for a user who has been away a month — an
 * error surfaced here would be a launch that cannot complete.
 */
class RestoreSession(
    private val session: AuthSessionPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(): SessionState = try {
        session.restore()
    } catch (cancellation: CancellationException) {
        // A cancelled launch is a cancelled launch, not an expired session. Swallowing this
        // would publish SignedOut on the way out of a cancelled coroutine, which is how a
        // user gets signed out by navigating away during startup.
        throw cancellation
    } catch (_: AppError) {
        SessionState.SignedOut
    }
}

/**
 * Ends the session.
 *
 * Idempotence lives in the data layer, where the stored credentials actually are; this
 * exists so a caller does not have to check [AuthSessionPort.current] first and get the
 * answer wrong by racing it.
 */
class SignOut(
    private val session: AuthSessionPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke() {
        session.signOut()
    }
}
