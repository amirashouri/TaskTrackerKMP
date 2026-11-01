package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * The compact shell scenarios, as test methods the three hosts inherit.
 *
 * The wide-layout scenarios are in [TaskTrackerWideScenarios], because they need a viewport the
 * phone host cannot provide — see that class for the split.
 *
 * ## Why a base class
 *
 * The same 66 entry points used to be copied by hand into `TaskTrackerAppJvmTest`,
 * `TaskTrackerAppIosTest` and `TaskTrackerAppAndroidHostTest`. Nothing connected the three
 * lists: a scenario body could be written, referenced from one host, and never run on the
 * other two, and the only symptom was a test count nobody read. Inheriting the methods from
 * one abstract class makes that omission a compile error instead.
 *
 * ## What each host still supplies
 *
 * Only its runner annotations. Android needs `@RunWith(RobolectricTestRunner::class)`,
 * `@Config` and `@GraphicsMode(NATIVE)`; the JVM and iOS hosts need none. The scenario
 * assertions stay in [ShellScenarios], and the host capability actuals stay in each host's
 * file, because `expect`/`actual` cannot be inherited.
 *
 * Kotlin/Native test discovery is the one risk this shape carries: if the iOS runner does
 * not discover inherited `@Test` methods, the iOS class reports zero tests and
 * `check-baseline.sh` fails on the host. `scripts/check-test-matrix.py` is the guard either
 * way — it compares the declared scenarios against each host file.
 */
@OptIn(ExperimentalTestApi::class)
abstract class TaskTrackerAppScenarios {

    /**
     * Wired here and nowhere else before this base class existed: the scenario existed, and all
     * three hosts silently skipped it. The matrix check caught it on the first run.
     */
    @Test
    fun aSessionThatEndsTearsDownTheSignedInTree() =
        runComposeUiTest(block = ShellScenarios.aSessionThatEndsTearsDownTheSignedInTree)

    @Test
    fun aSignedOutSessionShowsSignIn() = runComposeUiTest(block = ShellScenarios.aSignedOutSessionShowsSignIn)
    @Test
    fun signingInFromTheShellReachesTheSessionPort() = runComposeUiTest(block = ShellScenarios.signingInFromTheShellReachesTheSessionPort)
    @Test
    fun aRestoreInFlightShowsNeitherScreen() = runComposeUiTest(block = ShellScenarios.aRestoreInFlightShowsNeitherScreen)
    @Test
    fun aRestoredSessionShowsTheProjectsList() = runComposeUiTest(block = ShellScenarios.aRestoredSessionShowsTheProjectsList)
    @Test
    fun aGraphWithoutProjectsSaysSo() = runComposeUiTest(block = ShellScenarios.aGraphWithoutProjectsSaysSo)
    @Test
    fun theSettingsTabEndsTheSession() = runComposeUiTest(block = ShellScenarios.theSettingsTabEndsTheSession)
    @Test
    fun aConnectLinkOpensSettingsWithTheAddressPrefilled() =
        runComposeUiTest(block = ShellScenarios.aConnectLinkOpensSettingsWithTheAddressPrefilled)
    @Test
    fun signingOutClearsTheNavigationStack() = runComposeUiTest(block = ShellScenarios.signingOutClearsTheNavigationStack)
    @Test
    fun openingAProjectShowsItsBoard() = runComposeUiTest(block = ShellScenarios.openingAProjectShowsItsBoard)
    @Test
    fun filingACardAwayFromTheBoardReachesTheStore() = runComposeUiTest(block = ShellScenarios.filingACardAwayFromTheBoardReachesTheStore)
    @Test
    fun filingAwayInTheEditorClosesTheSheet() = runComposeUiTest(block = ShellScenarios.filingAwayInTheEditorClosesTheSheet)
    @Test
    fun theBoardReturnsToTheList() = runComposeUiTest(block = ShellScenarios.theBoardReturnsToTheList)
    @Test
    fun signingOutStopsTheProjectsObservation() = runComposeUiTest(block = ShellScenarios.signingOutStopsTheProjectsObservation)
    @Test
    fun creatingAProjectAddsItToTheList() = runComposeUiTest(block = ShellScenarios.creatingAProjectAddsItToTheList)
    @Test
    fun everyTabGetsToItsOwnScreen() = runComposeUiTest(block = ShellScenarios.everyTabGetsToItsOwnScreen)
    @Test
    fun switchingTabsKeepsTheProject() = runComposeUiTest(block = ShellScenarios.switchingTabsKeepsTheProject)
    @Test
    fun switchingProjectsFromTheSprintsTabCallsOpenProject() = runComposeUiTest(block = ShellScenarios.switchingProjectsFromTheSprintsTabCallsOpenProject)
    @Test
    fun aSingleProjectIsChosenByDefaultOnTheBoard() = runComposeUiTest(block = ShellScenarios.aSingleProjectIsChosenByDefaultOnTheBoard)
    @Test
    fun theTabBarFloatsAboveTheBottomEdge() = runComposeUiTest(block = ShellScenarios.theTabBarFloatsAboveTheBottomEdge)
    @Test
    fun theTabBarIsWiderThanItsTabs() = runComposeUiTest(block = ShellScenarios.theTabBarIsWiderThanItsTabs)
    @Test
    fun fiveTabsFitAtPhoneWidth() = runComposeUiTest(block = ShellScenarios.fiveTabsFitAtPhoneWidth)

    @Test
    fun aNarrowShellDrawsTheBarAndNoRail() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellDrawsTheBarAndNoRail)

    @Test
    fun aNarrowShellReplacesTheSprintListWithTheDetail() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellReplacesTheSprintListWithTheDetail)

    @Test
    fun aNarrowShellStillOpensTheNewSprintFormAsASheet() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellStillOpensTheNewSprintFormAsASheet)

    @Test
    fun theAdminMembersRowOpensTheSectionAndSystemBackPopsOutOfIt() =
        runComposeUiTest(block = ShellScenarios.theAdminMembersRowOpensTheSectionAndSystemBackPopsOutOfIt)

    @Test
    fun aNarrowShellDrawsTheMemberFormOverTheList() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellDrawsTheMemberFormOverTheList)

    @Test
    fun theAdminProjectsRowOpensTheProjectsSection() =
        runComposeUiTest(block = ShellScenarios.theAdminProjectsRowOpensTheProjectsSection)

    @Test
    fun aNarrowShellDrawsTheProjectFormOverTheAdminList() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellDrawsTheProjectFormOverTheAdminList)

    @Test
    fun aNarrowShellAnchorsTheSheetToTheBottomEdge() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellAnchorsTheSheetToTheBottomEdge)

    @Test
    fun aNarrowShellHoldsTheEditorOnThePathWhileItsSlideFinishes() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellHoldsTheEditorOnThePathWhileItsSlideFinishes)

    @Test
    fun anAdminSeesTheLiveCatalogWhenTheLocalRowHasNone() =
        runComposeUiTest(block = ShellScenarios.anAdminSeesTheLiveCatalogWhenTheLocalRowHasNone)


    @Test
    fun aSprintOpensAndALinkLandsOnIt() = runComposeUiTest(block = ShellScenarios.aSprintOpensAndALinkLandsOnIt)
    @Test
    fun aDeepLinkToOneSprintLandsOnItsScreen() = runComposeUiTest(block = ShellScenarios.aDeepLinkToOneSprintLandsOnItsScreen)
    @Test
    fun addingASelectedTaskToASprintFromTheShell() = runComposeUiTest(block = ShellScenarios.addingASelectedTaskToASprintFromTheShell)
    @Test
    fun removingATaskFromASprintFromTheShell() = runComposeUiTest(block = ShellScenarios.removingATaskFromASprintFromTheShell)
    @Test
    fun aLockedSprintOffersNoTaskEdits() = runComposeUiTest(block = ShellScenarios.aLockedSprintOffersNoTaskEdits)
    @Test
    fun tappingASprintTaskRowOpensTheEditor() = runComposeUiTest(block = ShellScenarios.tappingASprintTaskRowOpensTheEditor)
    @Test
    fun systemBackPopsTheSprintDetail() = runComposeUiTest(block = ShellScenarios.systemBackPopsTheSprintDetail)
    @Test
    fun everyTabScrollsBehindTheTabBar() = runComposeUiTest(block = ShellScenarios.everyTabScrollsBehindTheTabBar)
    @Test
    fun theLastRowOfEachListScrollsClearOfTheTabBar() = runComposeUiTest(block = ShellScenarios.theLastRowOfEachListScrollsClearOfTheTabBar)
    @Test
    fun switchingToTheArchivedSectionShowsClosedWorkAndHidesBacklogRows() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[0])
    @Test
    fun switchingBackToTheBacklogSectionShowsBacklogRowsAgain() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[1])
    @Test
    fun theArchivedSectionListsFiledAwayItemsNewestFirst() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[2])
    @Test
    fun theBacklogSectionRemainsWhenSwitchingAway() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[3])
    @Test
    fun theArchivedSectionShowsItsOwnEmptyState() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[4])
    @Test
    fun theBacklogSectionShowsItsOwnEmptyState() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[5])
    @Test
    fun theBusyItemKeepsItsWaitAcrossASectionSwitch() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[6])
    @Test
    fun switchingToKanbanShowsTheBoardRows() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[7])
    @Test
    fun theKanbanSectionListsRowsInBoardOrderThenRank() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[8])
    @Test
    fun tappingAKanbanRowOpensTheTask() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[9])
    @Test
    fun filingAwayFromAKanbanRowCallsFileAway() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[10])
    @Test
    fun theKanbanSectionShowsItsOwnEmptyState() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[11])
    @Test
    fun tappingABacklogRowOpensTheTask() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[12])
    @Test
    fun theSortChipsCombineIntoAnOrderedPair() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[13])
    @Test
    fun aDoneKanbanRowOffersNoFileAway() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[14])
    @Test
    fun theBacklogSortSurvivesLeavingTheTab() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[15])
    @Test
    fun exactlyOneTabIsMarkedSelected() = runComposeUiTest(block = ShellScenarios.exactlyOneTabIsMarkedSelected)
    @Test
    fun everyTabIsNamedAndCarriesTheTabRole() = runComposeUiTest(block = ShellScenarios.everyTabIsNamedAndCarriesTheTabRole)
    @Test
    fun theSplashHoldsUntilBothTheFloorAndTheSession() = runComposeUiTest(block = ShellScenarios.theSplashHoldsUntilBothTheFloorAndTheSession)
    @Test
    fun theSplashWaitsForARestoreThatHasNotFinished() = runComposeUiTest(block = ShellScenarios.theSplashWaitsForARestoreThatHasNotFinished)
    @Test
    fun aChosenThemeOutlivesTheProcess() = runComposeUiTest(block = ShellScenarios.aChosenThemeOutlivesTheProcess)
    @Test
    fun forcedOfflineSurvivesTheSessionEnding() = runComposeUiTest(block = ShellScenarios.forcedOfflineSurvivesTheSessionEnding)

    /**
     * The shell's motion inputs, asserted where the material reads them.
     *
     * [ShellMotionScenarios] rather than [ShellScenarios] because those are the shell's screens
     * and these are its platform inputs; the app root's use of them is covered by every
     * scenario above, since they all render `TaskTrackerApp`. Wired here rather than in the
     * host files so a scenario cannot be written and then run on one host only.
     */
    @Test
    fun theShellPassesTheReducedMotionPreferenceToTheMaterial() =
        runComposeUiTest(block = ShellMotionScenarios.theShellPassesTheReducedMotionPreferenceToTheMaterial)

    @Test
    fun everyGlassSurfaceUnderTheShellSharesOneMotionController() =
        runComposeUiTest(block = ShellMotionScenarios.everyGlassSurfaceUnderTheShellSharesOneMotionController)

    @Test
    fun theShellsMotionControllerStopsWhenTheShellLeaves() =
        runComposeUiTest(block = ShellMotionScenarios.theShellsMotionControllerStopsWhenTheShellLeaves)
}
