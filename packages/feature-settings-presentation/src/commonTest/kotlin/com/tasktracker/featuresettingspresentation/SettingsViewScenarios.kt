package com.tasktracker.featuresettingspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * Every settings scenario, as test methods the three hosts inherit.
 *
 * The same 21 entry points used to be copied by hand into `SettingsViewJvmTest`,
 * `SettingsViewIosTest` and `SettingsViewAndroidHostTest`. Nothing connected the three lists, so a
 * scenario body could be written and never run on two of the three hosts, and the only symptom was
 * a test count nobody read. Inheriting the methods from one abstract class makes that omission a
 * compile error instead; the hosts keep only their runner annotations, because `@RunWith` is not
 * expressible in `commonTest` and `expect`/`actual` cannot be inherited.
 *
 * The pixel scenarios still skip honestly on the Android host — see [RasterSupport] there.
 */
@OptIn(ExperimentalTestApi::class)
abstract class SettingsViewScenarios {

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

    @Test
    fun theSelectedAccentSwatchCarriesTheGlassLens() =
        runComposeUiTest(block = AccentSwatchScenarios.theSelectedAccentSwatchCarriesTheGlassLens)

    @Test
    fun theAdminSectionIsHiddenFromAMember() =
        runComposeUiTest(block = SettingsScenarios.theAdminSectionIsHiddenFromAMember)

    @Test
    fun anAdminSeesTheManagementEntriesAndTheyDispatch() =
        runComposeUiTest(block = SettingsScenarios.anAdminSeesTheManagementEntriesAndTheyDispatch)

    @Test
    fun theServerRowShowsTheAddressInUse() =
        runComposeUiTest(block = SettingsScenarios.theServerRowShowsTheAddressInUse)

    @Test
    fun tappingTheServerRowOpensTheEditor() =
        runComposeUiTest(block = SettingsScenarios.tappingTheServerRowOpensTheEditor)

    @Test
    fun aRefusedAddressShowsItsReason() =
        runComposeUiTest(block = SettingsScenarios.aRefusedAddressShowsItsReason)

    @Test
    fun resetIsOfferedOnlyWhenAnOverrideExists() =
        runComposeUiTest(block = SettingsScenarios.resetIsOfferedOnlyWhenAnOverrideExists)

    @Test
    fun aCheckInFlightDisablesTheOtherActions() =
        runComposeUiTest(block = SettingsScenarios.aCheckInFlightDisablesTheOtherActions)

    @Test
    fun aSavedChangeRaisesTheRestartNotice() =
        runComposeUiTest(block = SettingsScenarios.aSavedChangeRaisesTheRestartNotice)

    @Test
    fun theNameSheetStagesAndCommitsANewName() =
        runComposeUiTest(block = SettingsScenarios.theNameSheetStagesAndCommitsANewName)

    @Test
    fun theNameSaveIsDisabledForABlankOrUnchangedName() =
        runComposeUiTest(block = SettingsScenarios.theNameSaveIsDisabledForABlankOrUnchangedName)

    @Test
    fun theStatusNoteCommitsOnDoneAndStopsAtTheLimit() =
        runComposeUiTest(block = SettingsScenarios.theStatusNoteCommitsOnDoneAndStopsAtTheLimit)

    @Test
    fun aFailedProfileWriteRaisesADismissibleBanner() =
        runComposeUiTest(block = SettingsScenarios.aFailedProfileWriteRaisesADismissibleBanner)
}
