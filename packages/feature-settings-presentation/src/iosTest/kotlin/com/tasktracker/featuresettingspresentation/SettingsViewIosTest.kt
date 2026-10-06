package com.tasktracker.featuresettingspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [SettingsScenarios] on the iOS simulator.
 *
 * Identical entry points to [SettingsViewJvmTest] and [SettingsViewAndroidHostTest], and the
 * reason they are three files rather than one in `commonTest` is the Android host's Robolectric
 * runner, which `commonTest` cannot carry — see [SettingsViewJvmTest].
 */
@OptIn(ExperimentalTestApi::class)
class SettingsViewIosTest {

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

    /**
     * [GlassIntensityScenarios] on the iOS simulator.
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
     * [ThemeOptionScenarios] on the iOS simulator.
     *
     * The ring scenario reads rendered pixels — this host is one of the two that can.
     */

    @Test
    fun theSelectedSchemeOptionDrawsAnAccentRing() =
        runComposeUiTest(block = ThemeOptionScenarios.theSelectedSchemeOptionDrawsAnAccentRing)

    @Test
    fun theLongAccentCaptionWrapsInsteadOfClipping() =
        runComposeUiTest(block = ThemeOptionScenarios.theLongAccentCaptionWrapsInsteadOfClipping)
}
