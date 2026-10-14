package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [ShellScenarios] on the JVM.
 *
 * The JVM host is the fast loop for the shell: the same scenarios run on the iOS simulator
 * * (`TaskTrackerAppIosTest`) and the Android host (`TaskTrackerAppAndroidHostTest`), and
 * * this one runs in seconds with no simulator, so a change to the app root is verified here
 * * first.
 *
 * No `commonTest` entry point, for the reason [ShellScenarios] gives: an Android-host
 * Compose UI test only runs under Robolectric, and that runner annotation is not
 * expressible in `commonTest`.
 */
@OptIn(ExperimentalTestApi::class)
class TaskTrackerAppJvmTest {

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
    fun theTabBarFloatsAboveTheBottomEdge() = runComposeUiTest(block = ShellScenarios.theTabBarFloatsAboveTheBottomEdge)
    @Test
    fun theTabBarIsWiderThanItsTabs() = runComposeUiTest(block = ShellScenarios.theTabBarIsWiderThanItsTabs)
    @Test
    fun fiveTabsFitAtPhoneWidth() = runComposeUiTest(block = ShellScenarios.fiveTabsFitAtPhoneWidth)

    @Test
    fun aNarrowShellDrawsTheBarAndNoRail() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellDrawsTheBarAndNoRail)

    @Test
    fun aWideShellDrawsTheRailAndNoBar() =
        runComposeUiTest(block = ShellScenarios.aWideShellDrawsTheRailAndNoBar)

    @Test
    fun theRailIsALeadingColumnRatherThanAStretchedOne() =
        runComposeUiTest(block = ShellScenarios.theRailIsALeadingColumnRatherThanAStretchedOne)

    @Test
    fun aWideShellDrawsTheSprintListAndDetailSideBySide() =
        runComposeUiTest(block = ShellScenarios.aWideShellDrawsTheSprintListAndDetailSideBySide)

    @Test
    fun aNarrowShellReplacesTheSprintListWithTheDetail() =
        runComposeUiTest(block = ShellScenarios.aNarrowShellReplacesTheSprintListWithTheDetail)

    @Test
    fun choosingAnotherSprintChangesTheDetailWithoutGrowingTheStack() =
        runComposeUiTest(block = ShellScenarios.choosingAnotherSprintChangesTheDetailWithoutGrowingTheStack)

    @Test
    fun exactlyOneRailDestinationIsMarkedSelected() =
        runComposeUiTest(block = ShellScenarios.exactlyOneRailDestinationIsMarkedSelected)


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
    fun theArchivedSectionListsOnlyClosedItemsNewestFirst() = runComposeUiTest(block = ShellScenarios.projectBacklogScenarios[2])
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
}

/**
 * The JVM host carries a 1000dp shell, so the wide-layout scenarios run here.
 *
 * This host is also the Desktop app's own suite — `:shared-ui`'s `jvmTest` *is* the Desktop test
 * host — so "the rail exists at desktop widths" is asserted on the same host that runs the
 * Desktop app, which is the only arrangement in which that claim is worth anything.
 */
internal actual fun thisHostExpressesDesktopWidth(): Boolean = true
