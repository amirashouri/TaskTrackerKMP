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

    @Test
    fun theUserDetailsSectionIsOnThePage() =
        runComposeUiTest(block = SettingsScenarios.theUserDetailsSectionIsOnThePage)

    @Test
    fun choosingAnAvatarRoutesItsIdIntoTheDetails() =
        runComposeUiTest(block = SettingsScenarios.choosingAnAvatarRoutesItsIdIntoTheDetails)

    /**
     * [GlassIntensityScenarios] on the JVM.
     *
     * The same six scenarios run on every host; these are entry points only, and every
     * assertion lives in the scenarios object.
     */

    @Test
    fun theSliderOpensAtTheAmountTheAppIsShowing() = runComposeUiTest(block = GlassIntensityScenarios.theSliderOpensAtTheAmountTheAppIsShowing)

    @Test
    fun theReadoutIsTheRoundedPercentage() = runComposeUiTest(block = GlassIntensityScenarios.theReadoutIsTheRoundedPercentage)

    @Test
    fun theBottomOfTheTravelIsNamedReduceTransparency() = runComposeUiTest(block = GlassIntensityScenarios.theBottomOfTheTravelIsNamedReduceTransparency)

    @Test
    fun assistiveTechnologyCanSetTheAmount() = runComposeUiTest(block = GlassIntensityScenarios.assistiveTechnologyCanSetTheAmount)

    @Test
    fun aDragPreviewsEveryStepAndPersistsOnce() = runComposeUiTest(block = GlassIntensityScenarios.aDragPreviewsEveryStepAndPersistsOnce)

    @Test
    fun aTapOnTheTrackMovesItWithoutADrag() = runComposeUiTest(block = GlassIntensityScenarios.aTapOnTheTrackMovesItWithoutADrag)

    /**
     * [ThemeOptionScenarios] on the JVM.
     *
     * The wash scenario reads rendered pixels, so it runs only where a frame can be measured —
     * see [com.tasktracker.designsystem.TextRasterSupport] for why the Android host skips it.
     */

    @Test
    fun theSelectedSchemeOptionIsWashedWithAccent() =
        runComposeUiTest(block = ThemeOptionScenarios.theSelectedSchemeOptionIsWashedWithAccent)

    @Test
    fun theLongAccentCaptionWrapsInsteadOfClipping() =
        runComposeUiTest(block = ThemeOptionScenarios.theLongAccentCaptionWrapsInsteadOfClipping)
}
