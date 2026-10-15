package com.tasktracker.featureauthpresentation

import com.tasktracker.core.AppError
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SignInModel]'s state machine, with no Compose runtime involved.
 *
 * These are the tests that can be exhaustive, and they carry the load: every branch of
 * [SignInModel.Phase] and of the `catch` chain in [SignInModel.submit] is reachable here and
 * is not reachable from a UI test without building a composition per case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInModelTest {

    @Test
    fun startsIdleWithEmptyFieldsAndNoError() = runTest {
        val model = SignInModel(AuthPreviewData.workingSession())

        assertEquals(SignInModel.Phase.Idle, model.phase)
        assertEquals("", model.email)
        assertEquals("", model.password)
        assertNull(model.errorMessage)
        assertFalse(model.canSubmit)
    }

    @Test
    fun canSubmitNeedsBothFieldsAndNothingMore() = runTest {
        val model = SignInModel(AuthPreviewData.workingSession())

        model.email = VALID_EMAIL
        assertFalse(model.canSubmit, "an address alone is not a submittable form")

        model.password = VALID_PASSWORD
        assertTrue(model.canSubmit)

        // Deliberately not an address validation rule: the model must not grey out the button
        // for a typo the user is still in the middle of typing.
        model.email = "not-an-email"
        assertTrue(model.canSubmit, "formatting is SignIn's business, not the form's")
    }

    @Test
    fun submitSignsInAndResolvesToTheIdentity() = runTest {
        val model = SignInModel(AuthPreviewData.workingSession())
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD

        model.submit()

        val phase = assertIs<SignInModel.Phase.SignedIn>(model.phase)
        assertEquals(VALID_EMAIL, phase.identity.email)
        assertNull(model.errorMessage)
    }

    @Test
    fun submitNormalisesTheAddressBeforeSendingIt() = runTest {
        // The model is the reason this is not tested through a fake `AuthSessionPort`'s
        // captured argument: normalisation happens in `SignIn`, upstream of the port, and
        // the port is where a test would look for it.
        val session = RecordingSession()
        val model = SignInModel(session)
        model.email = "  Amir@Example.COM  "
        model.password = VALID_PASSWORD

        model.submit()

        assertEquals("amir@example.com", session.sentEmail)
    }

    @Test
    fun successClearsTheErrorFromAPreviousFailure() = runTest {
        // Not `failingSessionWith`, which throws on every call: the case under test is one
        // failure *followed by* a success, so the session has to change its mind.
        var attempts = 0
        val session = object : RecordingSession() {
            override suspend fun signIn(email: String, password: String): UserIdentity {
                attempts++
                if (attempts == 1) throw AppError.Offline
                return super.signIn(email, password)
            }
        }
        val model = SignInModel(session)
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD

        model.submit()
        assertNotNullMessage(model.errorMessage)

        // The password has to be typed again: a failure clears it, so submitting straight
        // after would be a no-op against a blank field rather than a second attempt.
        model.password = VALID_PASSWORD
        model.submit()

        assertNull(model.errorMessage, "a stale banner surviving a success is a wrong claim")
        assertIs<SignInModel.Phase.SignedIn>(model.phase)
    }

    @Test
    fun cannotSubmitAgainOnceSignedIn() = runTest {
        val model = SignInModel(AuthPreviewData.workingSession())
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD
        model.submit()
        assertIs<SignInModel.Phase.SignedIn>(model.phase)

        assertFalse(model.canSubmit)

        // And submitting anyway is a no-op rather than a second request, because `submit`
        // re-checks `canSubmit` rather than trusting the caller — a keyboard's "go" key and
        // an accessibility activation both reach it with the button disabled.
        val before = model.phase
        model.submit()
        assertEquals(before, model.phase)
    }

    @Test
    fun submitIsANoOpWhileBlank() = runTest {
        val session = RecordingSession()
        val model = SignInModel(session)
        model.email = VALID_EMAIL
        // No password.

        model.submit()

        assertEquals(0, session.signInCount)
        assertEquals(SignInModel.Phase.Idle, model.phase)
    }

    @Test
    fun failureClearsThePasswordAndShowsTheErrorsOwnText() = runTest {
        val model = SignInModel(AuthPreviewData.failingSession())
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD

        model.submit()

        assertEquals(SignInModel.Phase.Idle, model.phase)
        assertEquals("", model.password, "a rejected secret must not sit in the field")
        // Asserted against the error's own text, not a literal. `AppError.Unauthorized`'s copy
        // is "Your session has expired. Please sign in again.", which is wrong on this screen
        // — see `AuthPreviewData.failingSession` — but re-wording it here would test a string
        // the app cannot produce. The invariant worth pinning is that the banner is the
        // error's message, not a second wording of it.
        assertEquals(AppError.Unauthorized.message, model.errorMessage)
        assertEquals(VALID_EMAIL, model.email, "only the secret is discarded on a failure")
        assertFalse(
            model.canSubmit,
            "a blank password means the form is not submittable again until it is retyped",
        )
    }

    @Test
    fun everyAppErrorReachesTheBannerVerbatim() = runTest {
        // The rule this pins: the model never re-words a failure. A test that asserted a
        // literal would pass whether or not the message came from the error, so the
        // assertion is against the error's own text.
        val failures = listOf(
            AppError.Offline,
            AppError.Timeout,
            AppError.Unauthorized,
            AppError.Forbidden,
            AppError.Transport("no route"),
            AppError.Server(500, "boom"),
            AppError.Decoding("bad json"),
            AppError.Conflict("task"),
            AppError.Validation("Enter your email address."),
            AppError.Cancelled,
            AppError.Storage("disk"),
            AppError.Unknown("what"),
        )

        for (failure in failures) {
            val model = SignInModel(AuthPreviewData.failingSessionWith { failure })
            model.email = VALID_EMAIL
            model.password = VALID_PASSWORD

            model.submit()

            assertEquals(failure.message, model.errorMessage, "re-worded: $failure")
            assertEquals("", model.password, "secret retained after: $failure")
        }
    }

    @Test
    fun anUnknownFailureGetsGenericTextRatherThanItsOwn() = runTest {
        val model = SignInModel(
            AuthPreviewData.failingSessionWith { throw IllegalStateException("token=abc123") },
        )
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD

        model.submit()

        // The point is that the thrown message contains an internal identifier and does not
        // reach the user, and that the form still recovered rather than crashing.
        assertEquals("Could not sign in. Please try again.", model.errorMessage)
        assertEquals(SignInModel.Phase.Idle, model.phase)
    }

    @Test
    fun aCancellationIsRethrownAndIsNotAFailure() = runTest {
        val session = object : RecordingSession() {
            override suspend fun signIn(email: String, password: String): UserIdentity {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val model = SignInModel(session)
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val job = scope.launch {
            assertFailsWith<kotlinx.coroutines.CancellationException> { model.submit() }
        }
        session.started.await()
        // The request suspends forever, so the job has to be cancelled for `submit` to return
        // at all. That is also the condition under test: the cancellation must travel out of
        // `submit` rather than being caught into `errorMessage`.
        job.cancelAndJoin()

        assertNull(
            model.errorMessage,
            "a cancelled submit reported to a screen the user has already left",
        )
        assertEquals(VALID_PASSWORD, model.password, "a cancel is not a rejection")
    }

    @Test
    fun dismissErrorClearsTheBannerOnly() = runTest {
        val model = SignInModel(AuthPreviewData.failingSession())
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD
        model.submit()

        model.dismissError()

        assertNull(model.errorMessage)
        assertEquals(VALID_EMAIL, model.email)
        assertEquals(SignInModel.Phase.Idle, model.phase)
    }

    @Test
    fun aSubmittingModelReportsIsSubmitting() = runTest {
        val gate = CompletableDeferred<Unit>()
        val session = object : RecordingSession() {
            override suspend fun signIn(email: String, password: String): UserIdentity {
                gate.await()
                return AuthPreviewData.identity
            }
        }
        val model = SignInModel(session)
        model.email = VALID_EMAIL
        model.password = VALID_PASSWORD
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        scope.launch { model.submit() }
        // The deferred gate holds the request open, so the in-flight state is observable
        // without sleeping or polling a clock.
        testScheduler.runCurrent()

        assertTrue(model.isSubmitting)
        assertFalse(model.canSubmit, "an in-flight form must not accept a second submit")

        gate.complete(Unit)
        testScheduler.runCurrent()

        assertFalse(model.isSubmitting)
        assertIs<SignInModel.Phase.SignedIn>(model.phase)
    }
}

/** A port that records what it was asked for, so tests can assert on the call itself. */
private open class RecordingSession : AuthSessionPort {
    val started = CompletableDeferred<Unit>()
    var signInCount = 0
        private set
    var sentEmail: String? = null
        private set

    override suspend fun signIn(email: String, password: String): UserIdentity {
        signInCount++
        sentEmail = email
        started.complete(Unit)
        return AuthPreviewData.identity
    }

    override suspend fun restore(): SessionState = SessionState.SignedOut

    override suspend fun signOut() = Unit

    /** The sign-in screen never ends a session; the settings screen does. */
    override suspend fun signOutLocally() = Unit

    override val sessionUpdates: Flow<SessionState> = MutableStateFlow(SessionState.SignedOut).asStateFlow()
}

private const val VALID_EMAIL = "amir@example.com"
private const val VALID_PASSWORD = "correct horse"

private fun assertNotNullMessage(message: String?) {
    assertTrue(message != null, "expected a message, got none")
}
