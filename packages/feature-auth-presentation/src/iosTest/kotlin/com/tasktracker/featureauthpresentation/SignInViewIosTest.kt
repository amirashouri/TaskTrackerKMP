package com.tasktracker.featureauthpresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [SignInScenarios] on the iOS simulator.
 *
 * Identical entry points to `SignInViewJvmTest` and `SignInViewAndroidHostTest`, and the
 * reason they are three files rather than one in `commonTest` is in [SignInScenarios]: the
 * Android host needs a Robolectric runner that `commonTest` cannot carry, and a `commonTest`
 * class runs on *every* target — so a single set of wrappers there would fail on the host
 * even with the host's own file beside it.
 */
@OptIn(ExperimentalTestApi::class)
class SignInViewIosTest {

    @Test
    fun rendersTheFormWithBothFieldsAndADisabledSubmit() = runComposeUiTest(block = SignInScenarios.rendersTheFormWithBothFieldsAndADisabledSubmit)

    @Test
    fun submitEnablesOnlyOnceBothFieldsHaveContent() = runComposeUiTest(block = SignInScenarios.submitEnablesOnlyOnceBothFieldsHaveContent)

    @Test
    fun successIsReportedToTheCaller() = runComposeUiTest(block = SignInScenarios.successIsReportedToTheCaller)

    @Test
    fun refusalShowsTheBannerAndClearsThePassword() = runComposeUiTest(block = SignInScenarios.refusalShowsTheBannerAndClearsThePassword)

    @Test
    fun failureKeepsTheAddressAndClearsThePassword() = runComposeUiTest(block = SignInScenarios.failureKeepsTheAddressAndClearsThePassword)

    @Test
    fun aMissingSessionShowsTheGate() = runComposeUiTest(block = SignInScenarios.aMissingSessionShowsTheGate)

    @Test
    fun aProvidedSessionShowsTheForm() = runComposeUiTest(block = SignInScenarios.aProvidedSessionShowsTheForm)
}
