package com.tasktracker.featureauthpresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.tasktracker.core.AppError
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.designsystem.TTBannerTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttTheme
import kotlin.math.abs
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

        onNodeWithTag(TTBannerTag).assertIsDisplayed()
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

    /**
     * The form is capped on a wide window, and still fills a phone.
     *
     * Both halves in one composition, because they are one decision rather than two. The cap is
     * chosen at the *narrow* end so that it never binds on a phone, and a test that only
     * measured a desktop would pass just as well if the form had been inset on phones too —
     * which is the way this change could plausibly go wrong.
     *
     * Measured on the email field rather than on its card, because the field is the thing the
     * user types into and `LabelledField`'s padding is the only thing between the two: a change
     * to either shows up as a change here. The expected numbers are derived from the tokens
     * rather than written down, so moving the gutter or the cap moves the expectation with it.
     */
    val theFormIsCappedOnAWideWindowAndStillFillsAPhone: suspend ComposeUiTest.() -> Unit = {
        var windowWidth by mutableStateOf(PHONE_FORM_WIDTH)
        themed {
            previewSession {
                Box(Modifier.width(windowWidth)) {
                    SignInView(onAuthenticated = {})
                }
            }
        }
        waitForIdle()

        // The gutter is the screen's, the inset is the field card's own padding, and the border
        // draws inside the bounds so it takes no space of its own.
        val gutter = ttSpacing.xxl.value
        val cardInset = ttSpacing.xxl.value

        val onPhone = fieldWidth()
        val phoneExpected = PHONE_FORM_WIDTH.value - 2 * gutter - 2 * cardInset
        assertTrue(
            abs(onPhone - phoneExpected) < 0.5f,
            "at ${PHONE_FORM_WIDTH.value}dp the field is ${onPhone}dp, and a phone is meant to " +
                "measure what it always did: the window minus the screen gutter and the card's " +
                "padding, which is ${phoneExpected}dp. A smaller number means the cap is " +
                "insetting a window that was already narrow enough",
        )

        windowWidth = WIDE_FORM_WIDTH
        waitForIdle()

        val onWide = fieldWidth()
        val wideExpected = ttSpacing.signInFormWidth.value - 2 * cardInset
        assertTrue(
            abs(onWide - wideExpected) < 0.5f,
            "at ${WIDE_FORM_WIDTH.value}dp the field is ${onWide}dp, so the form is not capped " +
                "at ttSpacing.signInFormWidth (${ttSpacing.signInFormWidth.value}dp); it is " +
                "${wideExpected}dp when it is. Uncapped, it is the window minus the gutter — the " +
                "form stretched to both edges that the cap exists to prevent",
        )
    }
}

/** A phone width: narrower than the form's cap, so the cap must not bind here. */
private val PHONE_FORM_WIDTH = 390.dp

/** A window wide enough that the cap is what decides the form's width, not the window. */
private val WIDE_FORM_WIDTH = 1024.dp

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.fieldWidth(): Float =
    onNodeWithTag(EmailFieldTag).getBoundsInRoot().let { it.right.value - it.left.value }

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
