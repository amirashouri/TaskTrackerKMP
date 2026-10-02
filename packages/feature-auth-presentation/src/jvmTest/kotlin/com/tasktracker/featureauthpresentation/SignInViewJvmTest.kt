package com.tasktracker.featureauthpresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [SignInScenarios] on the JVM.
 *
 * The JVM host is the fast loop for this screen: the same scenario values run on the iOS
 * simulator (`SignInViewIosTest`) and the Android host (`SignInViewAndroidHostTest`), and
 * this one runs in seconds with no simulator, so a change to the sign-in form is verified
 * here first.
 *
 * There is no `commonTest` entry point for these on purpose — see [SignInScenarios].
 */
@OptIn(ExperimentalTestApi::class)
class SignInViewJvmTest {

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
