package com.tasktracker.featureauthpresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tasktracker.core.AppError
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import kotlin.test.assertTrue

/**
 * The sign-in screen's behaviour, as a set of scenarios with no test framework in them.
 *
 * ## Why these are not `@Test` methods
 *
 * [SignInViewTest] runs them on the JVM and the iOS simulator. `SignInViewAndroidHostTest`
 * runs the same values on the Android host, and that second file needs
 * `@RunWith(RobolectricTestRunner::class)`.
 *
 * An Android-host Compose UI test only runs under Robolectric — against the host's stub
 * `android.jar`, `Build.FINGERPRINT` is null and Compose's own `RobolectricIdlingStrategy`
 * dereferences it, so every test dies in its constructor before an assertion. Robolectric,
 * in turn, only takes effect when a JUnit 4 runner is attached, and a runner annotation is
 * not expressible in `commonTest`.
 *
 * So the assertions live here and each target contributes only the entry points. One copy of
 * the behaviour, three platforms, no duplicated expectations, and no JUnit 5 migration of the
 * existing host suite — which is the alternative, and a far larger change than this problem
 * warrants.
 *
 * Each value is a *suspend* `ComposeUiTest` receiver rather than a function taking one, so a
 * scenario reads as the interaction it performs, and the suspend matters: `setContent` and
 * `waitForIdle` are both suspend, and a non-suspend function type is not assignable to a
 * suspend one.
 */
@OptIn(ExperimentalTestApi::class)
object SignInScenarios {

    /** Both fields render and the button is inert until the form has something to submit. */
    val rendersTheFormWithBothFieldsAndADisabledSubmit: suspend ComposeUiTest.() -> Unit = {
        signInFormWith(AuthPreviewData.workingSession())

        onNodeWithTag(EmailFieldTag).assertIsDisplayed()
        onNodeWithTag(PasswordFieldTag).assertIsDisplayed()
        onNodeWithText("Email").assertIsDisplayed()
        onNodeWithText("Password").assertIsDisplayed()
        onNodeWithTag(PrimaryButtonTag).assertIsNotEnabled()
        onNodeWithText("Sign In").assertIsDisplayed()
    }

    /** The button enables on the second field, not the first. */
    val submitEnablesOnlyOnceBothFieldsHaveContent: suspend ComposeUiTest.() -> Unit = {
        signInFormWith(AuthPreviewData.workingSession())

        onNodeWithTag(EmailFieldTag).performTextInput("amir@example.com")
        onNodeWithTag(PrimaryButtonTag).assertIsNotEnabled()

        onNodeWithTag(PasswordFieldTag).performTextInput("correct horse")
        onNodeWithTag(PrimaryButtonTag).assertIsEnabled()
    }

    /**
     * A success reports out of the screen and nowhere else.
     *
     * The screen must not navigate: the shell subscribes to the session bus and owns the
     * transition, so all this screen can do is say the attempt succeeded. A test that only
     * asserted "a session now exists" would pass just as well if the screen had navigated,
     * which is the dependency this module is not allowed to have.
     */
    val successIsReportedToTheCaller: suspend ComposeUiTest.() -> Unit = {
        var signedIn = false
        themed {
            previewSession {
                SignInView(onAuthenticated = { signedIn = true })
            }
        }

        signInWith("amir@example.com", "correct horse")

        assertTrue(signedIn, "the caller was never told the attempt succeeded")
    }

    /**
     * A refusal shows the error's own text, queryable as text.
     *
     * The `hasText` assertion is the reason [ErrorBanner] does not collapse its own semantics:
     * `clearAndSetSemantics` with the message as a description passes a `contentDescription`
     * check and fails this one, because the message is no longer in the text tree.
     *
     * `AppError`'s constructor takes a non-null `String`, but the property is nullable
     * because it shadows `Exception.message`.
     */
    val refusalShowsTheBannerAndClearsThePassword: suspend ComposeUiTest.() -> Unit = {
        signInFormWith(AuthPreviewData.failingSession())

        signInWith("amir@example.com", "wrong")

        onNodeWithTag(ErrorBannerTag).assertIsDisplayed()
        onNode(hasText(requireNotNull(AppError.Unauthorized.message) {
            "AppError always carries text; the nullability is inherited from Exception"
        })).assertIsDisplayed()

        // The secret is gone, so the button is inert again and the form is a retry away
        // rather than a resend of what the server just rejected.
        onNodeWithTag(PrimaryButtonTag).assertIsNotEnabled()
        onNodeWithText("wrong").assertDoesNotExist()
    }

    /**
     * A failure keeps the address and drops the password.
     *
     * Asserted on the fields' own editable text rather than on the banner, because the pair
     * is the only shape that catches both failure modes: a model rebuilt across the
     * recomposition the phase change causes would drop both values, and a model that failed to
     * clear the secret would keep the password.
     */
    val failureKeepsTheAddressAndClearsThePassword: suspend ComposeUiTest.() -> Unit = {
        signInFormWith(AuthPreviewData.failingSession())

        signInWith("amir@example.com", "wrong")

        onNodeWithTag(EmailFieldTag).assert(hasText("amir@example.com"))
        onNodeWithTag(PasswordFieldTag).assert(hasText(""))
    }

    /**
     * No session means a visible gate, not a form whose submit silently does nothing.
     *
     * This is the test that failed first and found a real bug: [SignInView] originally ignored
     * `currentAuthSession` entirely, so the gate from 11.1 was unreachable code.
     */
    val aMissingSessionShowsTheGate: suspend ComposeUiTest.() -> Unit = {
        themed {
            CompositionLocalProvider(LocalAuthSession provides null) {
                SignInView(onAuthenticated = {})
            }
        }

        onNodeWithTag(MissingAuthSessionTag).assertIsDisplayed()
        onNodeWithTag(EmailFieldTag).assertDoesNotExist()
    }

    /** And the gate stays out of the way when a session is there. */
    val aProvidedSessionShowsTheForm: suspend ComposeUiTest.() -> Unit = {
        signInFormWith(AuthPreviewData.workingSession())

        onNodeWithTag(MissingAuthSessionTag).assertDoesNotExist()
        onNodeWithTag(EmailFieldTag).assertIsDisplayed()
    }
}

/** Every scenario renders in the same theme, so none of them is an unthemed outlier. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.themed(content: @Composable () -> Unit) {
    setContent { ttTheme(ThemeScheme.System) { content() } }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.signInFormWith(session: AuthSessionPort) {
    themed {
        ProvideAuthSession(session) { SignInView(onAuthenticated = {}) }
    }
}

/** Fills both fields and taps submit, then waits for the model to settle. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.signInWith(email: String, password: String) {
    onNodeWithTag(EmailFieldTag).performTextInput(email)
    onNodeWithTag(PasswordFieldTag).performTextInput(password)
    onNodeWithTag(PrimaryButtonTag).performClick()
    waitForIdle()
}
