package com.tasktracker.featureauthpresentation

import androidx.compose.runtime.Composable
import com.tasktracker.core.AppError
import com.tasktracker.core.randomUserID
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Previews and tests stand in for a session with these.
 *
 * A real [AuthSessionPort] cannot be used: signing in needs a data source, and the whole
 * point of this module's dependency list is that it has none. These fakes live in
 * `commonMain` rather than a `debug` source set because the KMP convention gives this module
 * no `debug` variant, and a fixture in `commonTest` would not be reachable from a
 * `commonMain` preview. The convention's rule that mocks live in `commonMain` is what makes
 * this reachable at all.
 *
 * The plan spells the preview hook `.previewSession()`. It is the [previewSession] function
 * instead, because it provides a `CompositionLocal` and a `Modifier` cannot: a modifier
 * decorates a node, and providing an environment wraps content. The plan's shape is right
 * and its mechanism is not expressible in Compose.
 */
object AuthPreviewData {

    /** The identity a successful sign-in resolves to. */
    val identity = UserIdentity(
        id = randomUserID(),
        email = "amir@example.com",
        displayName = "Amir",
    )

    /**
     * A session that succeeds, publishing [SessionState.SignedIn] on sign-in.
     *
     * [latencyMillis] is not decoration. A request that returns in zero time never renders
     * the button's `Submitting` phase, so without it there is no way to preview or test the
     * one state where the form is inert.
     */
    fun workingSession(
        latencyMillis: Long = 0L,
    ): AuthSessionPort = FakeSession(initial = SessionState.SignedOut) { email, _ ->
        if (latencyMillis > 0) delay(latencyMillis)
        identity.copy(email = email)
    }

    /**
     * A session that always refuses, with [AppError.Unauthorized].
     *
     * **The copy here is wrong for this screen, and the fake deliberately still uses it.**
     * `AppError.Unauthorized` reads "Your session has expired. Please sign in again.", which
     * is correct for a request made *with* a dead session and actively misleading on the
     * sign-in screen, where the user is establishing a session and the honest text is "That
     * email or password is not right."
     *
     * Rewording it here would break the rule `SignInModel` is built on — a view that
     * re-words a failure is a view that will disagree with the data layer — and inventing a
     * new `AppError` case would change `:packages:core`, which is a finished phase and which
     * `:packages:core`'s own tests pin. So the fake produces the message the app can actually
     * produce, and the mismatch is left visible here rather than papered over. Adding a
     * rejected-credentials case to core is the real fix.
     */
    fun failingSession(): AuthSessionPort =
        FakeSession(initial = SessionState.SignedOut) { _, _ ->
            throw AppError.Unauthorized
        }

    /**
     * A session whose sign-in fails with a caller-chosen [AppError], for the model tests
     * that need each failure path — offline, timeout, unknown — rather than only the 401.
     */
    fun failingSessionWith(failure: () -> AppError): AuthSessionPort =
        FakeSession(initial = SessionState.SignedOut) { _, _ -> throw failure() }

    /** A [SignInModel] over a working session, for a preview of the form at rest. */
    fun signInModel(
        email: String = "",
        password: String = "",
        session: AuthSessionPort = workingSession(latencyMillis = 250L),
    ): SignInModel = SignInModel(session).apply {
        this.email = email
        this.password = password
    }
}

/**
 * The shared fake, so each fixture is a lambda rather than a repeated interface body.
 *
 * [sessionUpdates] is a real [MutableStateFlow] rather than an empty flow because a flow
 * that never emits is indistinguishable from one that has not loaded yet, and the
 * distinction is exactly what a session-restoring screen needs to get right.
 */
private class FakeSession(
    initial: SessionState,
    private val onSignIn: suspend (email: String, password: String) -> UserIdentity,
) : AuthSessionPort {

    private val updates = MutableStateFlow(initial)

    override suspend fun signIn(email: String, password: String): UserIdentity {
        val identity = onSignIn(email, password)
        updates.value = SessionState.SignedIn(identity)
        return identity
    }

    override suspend fun restore(): SessionState = updates.value

    override suspend fun signOut() {
        updates.value = SessionState.SignedOut
    }

    /**
     * The same transition, and deliberately the same line.
     *
     * A preview has no server, so the difference the two methods exist for — whether the
     * server was asked — is not something a fixture can show. Both end at [SessionState.SignedOut],
     * which is what every screen being previewed reacts to.
     */
    override suspend fun signOutLocally() {
        updates.value = SessionState.SignedOut
    }

    override val sessionUpdates: Flow<SessionState> = updates.asStateFlow()
}

/** Provides a working session to previews and tests. */
@Composable
fun previewSession(content: @Composable () -> Unit) {
    ProvideAuthSession(session = AuthPreviewData.workingSession(), content = content)
}
