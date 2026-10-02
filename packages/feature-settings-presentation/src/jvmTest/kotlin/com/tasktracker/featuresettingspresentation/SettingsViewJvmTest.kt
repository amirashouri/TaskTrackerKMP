package com.tasktracker.featuresettingspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [SettingsScenarios] on the JVM.
 *
 * The JVM host is the fast loop for this screen: the same scenario values run on the iOS
 * simulator (`SettingsViewIosTest`) and the Android host (`SettingsViewAndroidHostTest`), and
 * this one runs in seconds with no simulator, so a change to the settings screen is verified
 * here first.
 *
 * There is no `commonTest` entry point for these on purpose: a Compose UI test on the Android
 * host needs Robolectric, `@RunWith` is not expressible in `commonTest`, and a `commonTest`
 * class runs on *every* target — so one set of wrappers there would fail on the host even with
 * the host's own file beside it. Every assertion lives in [SettingsScenarios]; these are only
 * entry points.
 *
 * [SettingsModelTest] is the other half of this module and needs no wrappers: it touches no
 * composition, so it runs as an ordinary `commonTest` class on all three targets.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsViewJvmTest {

    @Test
    fun aQuietScreenSaysNothingAboutCounts() = runComposeUiTest(block = SettingsScenarios.aQuietScreenSaysNothingAboutCounts)

    @Test
    fun offlineWorkIsExplainedRatherThanHidden() =
        runComposeUiTest(block = SettingsScenarios.offlineWorkIsExplainedRatherThanHidden)

    @Test
    fun onlyARunningCycleDisablesSyncNow() = runComposeUiTest(block = SettingsScenarios.onlyARunningCycleDisablesSyncNow)

    @Test
    fun anOfflineTapIsAnsweredRatherThanIgnored() =
        runComposeUiTest(block = SettingsScenarios.anOfflineTapIsAnsweredRatherThanIgnored)

    @Test
    fun aStartedCycleSaysNothing() = runComposeUiTest(block = SettingsScenarios.aStartedCycleSaysNothing)

    @Test
    fun theControlsDispatchTheirCommands() = runComposeUiTest(block = SettingsScenarios.theControlsDispatchTheirCommands)

    @Test
    fun theVersionRowHidesItselfWithNoVersion() =
        runComposeUiTest(block = SettingsScenarios.theVersionRowHidesItselfWithNoVersion)
}