package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * The shell scenarios that need a viewport wider than the compact threshold.
 *
 * ## Why these are a second base class
 *
 * They used to live beside the compact scenarios in [TaskTrackerAppScenarios] and ask
 * `thisHostExpressesDesktopWidth()` before asserting. That gate existed because one class ran
 * on a phone host — where the shell those scenarios describe cannot be built — and on the
 * desktop-width hosts. The gate made a phone host skip instead of going red, but it also meant
 * the *only* way to run these scenarios was a host whose viewport happened to be wide.
 *
 * Robolectric can be asked for a wide viewport (`@Config(qualifiers = "w1400dp-h1000dp")`), it
 * just cannot do that and lay out a 390dp phone in the same class. So the split is by viewport
 * rather than by capability: [TaskTrackerAppScenarios] is the compact host's class, and this
 * one is the wide host's. The Android host now has one entry point of each, and the gate is
 * gone because a scenario in this class is only ever instantiated where the window is wide.
 *
 * The JVM and iOS hosts also get a second entry point ([TaskTrackerWideJvmTest] and
 * [TaskTrackerWideIosTest]) so their coverage is unchanged; `scripts/check-test-matrix.py`
 * enforces that every scenario here has an entry point on every host.
 */
@OptIn(ExperimentalTestApi::class)
abstract class TaskTrackerWideScenarios {

    @Test
    fun aWideShellDropsTheEditorWindowAtOnce() =
        runComposeUiTest(block = ShellScenarios.aWideShellDropsTheEditorWindowAtOnce)
    @Test
    fun theSidebarIsALeadingColumnRatherThanAStretchedOne() =
        runComposeUiTest(block = ShellScenarios.theSidebarIsALeadingColumnRatherThanAStretchedOne)
    @Test
    fun choosingAnotherSprintChangesTheDetailWithoutGrowingTheStack() =
        runComposeUiTest(block = ShellScenarios.choosingAnotherSprintChangesTheDetailWithoutGrowingTheStack)
    @Test
    fun aWideBacklogPutsTheChosenItemBesideTheList() =
        runComposeUiTest(block = ShellScenarios.aWideBacklogPutsTheChosenItemBesideTheList)
    @Test
    fun tappingASidebarProjectOpensItOnBoard() =
        runComposeUiTest(block = ShellScenarios.tappingASidebarProjectOpensItOnBoard)
    @Test
    fun aWideShellDrawsTheSprintListAndDetailSideBySide() =
        runComposeUiTest(block = ShellScenarios.aWideShellDrawsTheSprintListAndDetailSideBySide)
    @Test
    fun shrinkingTheWindowPastTheThresholdShedsTheRailInset() =
        runComposeUiTest(block = ShellScenarios.shrinkingTheWindowPastTheThresholdShedsTheRailInset)
    @Test
    fun switchingProjectFromSettingsLandsOnBoard() =
        runComposeUiTest(block = ShellScenarios.switchingProjectFromSettingsLandsOnBoard)
    @Test
    fun theSidebarKeepsItsDestinationsWhenTheProjectListScrolls() =
        runComposeUiTest(
            block = ShellScenarios.theSidebarKeepsItsDestinationsWhenTheProjectListScrolls,
        )
    @Test
    fun exactlyOneRailDestinationIsMarkedSelected() =
        runComposeUiTest(block = ShellScenarios.exactlyOneRailDestinationIsMarkedSelected)
    @Test
    fun creatingAProjectShowsItInTheSidebarWithoutARelaunch() =
        runComposeUiTest(
            block = ShellScenarios.creatingAProjectShowsItInTheSidebarWithoutARelaunch,
        )
    @Test
    fun aSelectedAdminProjectOpensTheEditorBesideTheList() =
        runComposeUiTest(block = ShellScenarios.aSelectedAdminProjectOpensTheEditorBesideTheList)
    @Test
    fun aWideShellDrawsTheProjectsListAndTheCreateFormSideBySide() =
        runComposeUiTest(block = ShellScenarios.aWideShellDrawsTheProjectsListAndTheCreateFormSideBySide)
    @Test
    fun aWideShellDrawsTheRailAndNoBar() =
        runComposeUiTest(block = ShellScenarios.aWideShellDrawsTheRailAndNoBar)
    @Test
    fun everySidebarDestinationIsNamedAndCarriesTheTabRole() =
        runComposeUiTest(block = ShellScenarios.everySidebarDestinationIsNamedAndCarriesTheTabRole)
    @Test
    fun aWideShellDrawsTheMembersListAndTheAddFormSideBySide() =
        runComposeUiTest(block = ShellScenarios.aWideShellDrawsTheMembersListAndTheAddFormSideBySide)
    @Test
    fun aSidebarWidthShellDrawsTheSidebarAndNoRailAndNoBar() =
        runComposeUiTest(block = ShellScenarios.aSidebarWidthShellDrawsTheSidebarAndNoRailAndNoBar)
    @Test
    fun growingTheWindowPastTheThresholdDropsTheBarInset() =
        runComposeUiTest(block = ShellScenarios.growingTheWindowPastTheThresholdDropsTheBarInset)
    @Test
    fun theSidebarListsProjectsInTheSwitchersOrder() =
        runComposeUiTest(block = ShellScenarios.theSidebarListsProjectsInTheSwitchersOrder)
    @Test
    fun aWideBacklogHasNoDetailPaneUntilARowIsChosen() =
        runComposeUiTest(block = ShellScenarios.aWideBacklogHasNoDetailPaneUntilARowIsChosen)
    @Test
    fun aWideShellShowsAPlaceholderWhenNothingIsSelected() =
        runComposeUiTest(block = ShellScenarios.aWideShellShowsAPlaceholderWhenNothingIsSelected)
    @Test
    fun resizingSidebarToRailAndBackShedsBothInsets() =
        runComposeUiTest(block = ShellScenarios.resizingSidebarToRailAndBackShedsBothInsets)
    @Test
    fun theRailIsALeadingColumnRatherThanAStretchedOne() =
        runComposeUiTest(block = ShellScenarios.theRailIsALeadingColumnRatherThanAStretchedOne)
    @Test
    fun anEmptySidebarSwitcherStillCarriesEveryDestination() =
        runComposeUiTest(
            block = ShellScenarios.anEmptySidebarSwitcherStillCarriesEveryDestination,
        )
    @Test
    fun aWideShellOpensTheNewSprintFormBesideTheList() =
        runComposeUiTest(block = ShellScenarios.aWideShellOpensTheNewSprintFormBesideTheList)
    @Test
    fun exactlyOneSidebarDestinationIsMarkedSelected() =
        runComposeUiTest(block = ShellScenarios.exactlyOneSidebarDestinationIsMarkedSelected)
    @Test
    fun aWideShellCentresTheSheetLikeAWindow() =
        runComposeUiTest(block = ShellScenarios.aWideShellCentresTheSheetLikeAWindow)
    @Test
    fun theSidebarFollowsAProjectChosenFromTheProjectsList() =
        runComposeUiTest(
            block = ShellScenarios.theSidebarFollowsAProjectChosenFromTheProjectsList,
        )
}
