package com.tasktracker.featureauthpresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SignInScenarios] on the Android host, under Robolectric.
 *
 * The counterpart to [SignInViewTest], which runs the same values on the JVM and the iOS
 * simulator. Only the entry points are here; every assertion lives in [SignInScenarios].
 *
 * ## Why this file exists
 *
 * A Compose UI test on the Android host needs Robolectric, and Robolectric only engages when
 * a JUnit 4 runner is attached. `@RunWith` is not expressible in `commonTest`, so a
 * `commonTest` class cannot bring itself up on this target: it compiles, and every one of its
 * tests then fails identically in `runComposeUiTest` before reaching an assertion —
 *
 *   NullPointerException: Cannot invoke "String.toLowerCase()" because
 *   "android.os.Build.FINGERPRINT" is null
 *
 * The alternative — moving the whole host suite to JUnit 5 and registering Robolectric's
 * extension by autodetection — would change how all 540 existing host tests run to fix
 * seven. Splitting the entry points is the smaller change, and it makes the split visible
 * rather than hiding a platform difference in a convention plugin.
 *
 * ## The SDK is pinned
 *
 * Robolectric defaults to the manifest's `targetSdk`, which follows `androidTargetSdk` in the
 * version catalog. A Compose test that breaks whenever that number is bumped is a test that
 * gets deleted rather than fixed, so the SDK is named here where the dependency is visible.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Native graphics is what actually rasterises the composition. Without it Robolectric's
// legacy mode draws nothing, and any assertion that depends on a node having been laid out
// fails on geometry rather than on behaviour.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInViewAndroidHostTest {

    @Test
    fun rendersTheFormWithBothFieldsAndADisabledSubmit() =
        runComposeUiTest(block = SignInScenarios.rendersTheFormWithBothFieldsAndADisabledSubmit)

    @Test
    fun submitEnablesOnlyOnceBothFieldsHaveContent() =
        runComposeUiTest(block = SignInScenarios.submitEnablesOnlyOnceBothFieldsHaveContent)

    @Test
    fun successIsReportedToTheCaller() = runComposeUiTest(block = SignInScenarios.successIsReportedToTheCaller)

    @Test
    fun refusalShowsTheBannerAndClearsThePassword() =
        runComposeUiTest(block = SignInScenarios.refusalShowsTheBannerAndClearsThePassword)

    @Test
    fun failureKeepsTheAddressAndClearsThePassword() =
        runComposeUiTest(block = SignInScenarios.failureKeepsTheAddressAndClearsThePassword)

    @Test
    fun aMissingSessionShowsTheGate() = runComposeUiTest(block = SignInScenarios.aMissingSessionShowsTheGate)

    @Test
    fun aProvidedSessionShowsTheForm() = runComposeUiTest(block = SignInScenarios.aProvidedSessionShowsTheForm)

    @Test
    fun theFormIsCappedOnAWideWindowAndStillFillsAPhone() =
        runComposeUiTest(block = SignInScenarios.theFormIsCappedOnAWideWindowAndStillFillsAPhone)
}
