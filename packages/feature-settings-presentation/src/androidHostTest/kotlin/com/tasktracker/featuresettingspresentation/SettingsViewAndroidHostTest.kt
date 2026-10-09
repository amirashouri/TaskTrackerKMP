package com.tasktracker.featuresettingspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SettingsScenarios] on the Android host, under Robolectric.
 *
 * The counterpart to [SettingsViewJvmTest] and [SettingsViewIosTest]. Only the entry points are
 * here; every assertion lives in [SettingsScenarios].
 *
 * ## Why this file exists
 *
 * Robolectric only engages when a JUnit 4 runner is attached, and `@RunWith` cannot be written in
 * `commonTest`. A `commonTest` Compose UI class compiles but then fails identically on this target,
 * before reaching an assertion — which is why the entry points are split per host rather than
 * shared.
 *
 * [GraphicsMode.NATIVE] is what actually rasterises the composition. In Robolectric's legacy mode
 * nothing is drawn, and any assertion depending on a node having been laid out then fails on
 * geometry rather than on behaviour.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsViewAndroidHostTest {

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

    @Test
    fun theStatusFieldSitsCentredInItsPill() =
        runComposeUiTest(block = SettingsScenarios.theStatusFieldSitsCentredInItsPill)

    /**
     * [GlassIntensityScenarios] on the Android host.
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
     * [ThemeOptionScenarios] on the Android host.
     *
     * The caption scenario runs here. The wash scenario measures rendered pixels and skips on
     * this host (the skip records as a pass) because Robolectric's rasteriser disagrees with
     * Skia — same reason as the design system's own pixel gates.
     */

    @Test
    fun theSelectedSchemeOptionIsWashedWithAccent() =
        runComposeUiTest(block = ThemeOptionScenarios.theSelectedSchemeOptionIsWashedWithAccent)

    @Test
    fun theLongAccentCaptionWrapsInsteadOfClipping() =
        runComposeUiTest(block = ThemeOptionScenarios.theLongAccentCaptionWrapsInsteadOfClipping)

    @Test
    fun theSelectedAccentSwatchCarriesTheGlassLens() =
        runComposeUiTest(block = AccentSwatchScenarios.theSelectedAccentSwatchCarriesTheGlassLens)
}
