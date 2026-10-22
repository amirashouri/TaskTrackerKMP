package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * The seven project-presentation scenario suites - card content rules, board column behaviour,
 * the board's lane row geometry, the archive round trip, the projects list, the sprint detail
 * card, and the sprint editor sheet - on the JVM host.
 *
 * There is no `commonTest` entry point for these on purpose. A Compose UI test on the
 * Android host needs Robolectric, and Robolectric only engages when a JUnit 4 runner is
 * attached; `@RunWith` is not expressible in `commonTest`, and a `commonTest` class runs on
 * *every* target, so one set of entry points there would fail on the host even with the
 * host's own file beside it. Every assertion lives in the scenario objects in `commonTest`;
 * these files are only entry points, which is why they are near-identical.
 *
 * The 51 entry points below are generated from the `val`s declared on those seven
 * objects, so a new scenario cannot be written and then quietly not run.
 *
 * The JVM host is the fast loop: the same scenario values run on the iOS simulator and the
 * Android host, and this one runs in seconds with no simulator, so a change to a project
 * screen is verified here first.
 *
 * Nothing here has to prepare a context or a graphics mode, which is what makes this the
 * right place to find out whether a change broke a screen rather than whether a platform
 * needed setting up. `jvmTest` is therefore the first target to run and the only one whose
 * failures are unambiguous.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsUiJvmTest {
    @Test
    fun theBacklogRowFilesItsOwnTaskAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.theBacklogRowFilesItsOwnTaskAway)

    @Test
    fun theBoardCardFilesItsOwnTaskAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.theBoardCardFilesItsOwnTaskAway)

    @Test
    fun aBusyCardDoesNotFileAnythingAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.aBusyCardDoesNotFileAnythingAway)

    @Test
    fun aDeliveredCardOffersNoFileAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.aDeliveredCardOffersNoFileAway)

    @Test
    fun everyLaneIsDrawnEvenWithNothingInIt() = runComposeUiTest(block = BoardColumnScenarios.everyLaneIsDrawnEvenWithNothingInIt)

    @Test
    fun theLanesAreTheBoardOrderAndNoOthers() = runComposeUiTest(block = BoardColumnScenarios.theLanesAreTheBoardOrderAndNoOthers)

    @Test
    fun theHeadingCarriesTheLanesOwnCount() = runComposeUiTest(block = BoardColumnScenarios.theHeadingCarriesTheLanesOwnCount)

    @Test
    fun aCardAppearsInTheLaneMatchingItsStatus() = runComposeUiTest(block = BoardColumnScenarios.aCardAppearsInTheLaneMatchingItsStatus)

    @Test
    fun tappingACardInALaneSelectsIt() = runComposeUiTest(block = BoardColumnScenarios.tappingACardInALaneSelectsIt)

    @Test
    fun theLaneRowReachesBothEdgesOfTheScreen() = runComposeUiTest(block = BoardLaneScenarios.theLaneRowReachesBothEdgesOfTheScreen)

    @Test
    fun theFirstLaneRestsAGutterFromTheLeftEdge() = runComposeUiTest(block = BoardLaneScenarios.theFirstLaneRestsAGutterFromTheLeftEdge)

    @Test
    fun theSwitcherIsInsetFromTheLeftEdge() = runComposeUiTest(block = BoardLaneScenarios.theSwitcherIsInsetFromTheLeftEdge)

    @Test
    fun theSwitcherIsNoWiderThanItsContentAllows() = runComposeUiTest(block = BoardLaneScenarios.theSwitcherIsNoWiderThanItsContentAllows)

    @Test
    fun theSwitcherShowsTheMonogramAndNotTheName() = runComposeUiTest(block = BoardLaneScenarios.theSwitcherShowsTheMonogramAndNotTheName)

    @Test
    fun aBoardWithoutAnActiveSprintIsNamedKanban() = runComposeUiTest(block = BoardLaneScenarios.aBoardWithoutAnActiveSprintIsNamedKanban)

    @Test
    fun aBoardWithAnActiveSprintNamesItInTheHeader() = runComposeUiTest(block = BoardLaneScenarios.aBoardWithAnActiveSprintNamesItInTheHeader)

    @Test
    fun theAssigneeStackOverlapsAndCountsWhatItHides() = runComposeUiTest(block = BoardLaneScenarios.theAssigneeStackOverlapsAndCountsWhatItHides)

    @Test
    fun anUnassignedBoardHasNoStack() = runComposeUiTest(block = BoardLaneScenarios.anUnassignedBoardHasNoStack)

    @Test
    fun everyLaneIsStillDrawn() = runComposeUiTest(block = BoardLaneScenarios.everyLaneIsStillDrawn)

    @Test
    fun listsEveryProject() = runComposeUiTest(block = ProjectsListScenarios.listsEveryProject)

    @Test
    fun anEmptyListSaysSo() = runComposeUiTest(block = ProjectsListScenarios.anEmptyListSaysSo)

    @Test
    fun noConfirmationUntilSomethingIsPressed() = runComposeUiTest(block = ProjectsListScenarios.noConfirmationUntilSomethingIsPressed)

    @Test
    fun unfinishedWorkIsArchivedNotDeleted() = runComposeUiTest(block = ProjectsListScenarios.unfinishedWorkIsArchivedNotDeleted)

    @Test
    fun noUnfinishedWorkIsDeletedForGood() = runComposeUiTest(block = ProjectsListScenarios.noUnfinishedWorkIsDeletedForGood)

    @Test
    fun confirmingReportsThatProject() = runComposeUiTest(block = ProjectsListScenarios.confirmingReportsThatProject)

    @Test
    fun cancellingReportsNothing() = runComposeUiTest(block = ProjectsListScenarios.cancellingReportsNothing)

    @Test
    fun swipingARowAsksBeforeRemovingIt() = runComposeUiTest(block = ProjectsListScenarios.swipingARowAsksBeforeRemovingIt)

    @Test
    fun aSwipeThatStopsShortDoesNothing() = runComposeUiTest(block = ProjectsListScenarios.aSwipeThatStopsShortDoesNothing)

    @Test
    fun aShortDragDoesNotOpenTheProject() = runComposeUiTest(block = ProjectsListScenarios.aShortDragDoesNotOpenTheProject)

    @Test
    fun everyRowIsInOneScrollingList() = runComposeUiTest(block = ProjectsListScenarios.everyRowIsInOneScrollingList)

    /** The new-project button is reachable with rows present. It used to be measured at zero height and drawn off the bottom of the screen. */
    @Test
    fun theNewProjectButtonWorksWithRowsPresent() = runComposeUiTest(block = ProjectsListScenarios.theNewProjectButtonWorksWithRowsPresent)

    /** The create action and the last row's delete action do not occupy the same pixels. It used to float over them and win the touch. */
    @Test
    fun theNewProjectActionDoesNotCoverTheLastRowsDelete() =
        runComposeUiTest(block = ProjectsListScenarios.theNewProjectActionDoesNotCoverTheLastRowsDelete)

    /** The backlog's add action and its rows' trailing actions do not occupy the same pixels. The button used to float over them and win the touch. */
    @Test
    fun theAddActionDoesNotCoverTheLastRow() =
        runComposeUiTest(block = ProjectBacklogScenarios.theAddActionDoesNotCoverTheLastRow)

    /** The add action trails the switcher and sits above the list, rather than floating over it. */
    @Test
    fun theAddActionTrailsTheSwitcher() =
        runComposeUiTest(block = ProjectBacklogScenarios.theAddActionTrailsTheSwitcher)

    @Test
    fun backlogRowShowsTheCopyControl() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.showsTheCopyControl)

    @Test
    fun backlogRowShowsTagsAsIconsWithoutTheirNames() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.showsTagsAsIconsWithoutTheirNames)

    @Test
    fun copyingABacklogRowHandsTheTitleAndDetailsToTheHost() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.copyingARowHandsTheTitleAndDetailsToTheHost)

    @Test
    fun aBacklogRowWithoutACopyHandlerOffersNoCopyControl() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.aRowWithoutACopyHandlerOffersNoCopyControl)

    @Test
    fun copyingABacklogRowConfirmsItOnTheBoard() =
        runComposeUiTest(block = ProjectBacklogScenarios.copyingARowConfirmsItOnTheBoard)

    /** The copy icon and the two triage verbs all stay inside the row at 320pt. */
    @Test
    fun theCopyControlFitsANarrowBacklogRow() =
        runComposeUiTest(block = ProjectBacklogScenarios.theCopyControlFitsANarrowRow)

    /** The list runs under the switcher and section picker, and the first row starts below them. */
    @Test
    fun theBacklogListScrollsUnderTheHeader() =
        runComposeUiTest(block = ProjectBacklogScenarios.theListScrollsUnderTheHeader)

    @Test
    fun showsTheKeyTitleAndPriority() = runComposeUiTest(block = WorkItemCardScenarios.showsTheKeyTitleAndPriority)

    @Test
    fun showsTheAssigneeWhenThereIsOne() = runComposeUiTest(block = WorkItemCardScenarios.showsTheAssigneeWhenThereIsOne)

    @Test
    fun omitsTheAssigneeWhenThereIsNone() = runComposeUiTest(block = WorkItemCardScenarios.omitsTheAssigneeWhenThereIsNone)

    @Test
    fun showsTheDueDateWhenThereIsOne() = runComposeUiTest(block = WorkItemCardScenarios.showsTheDueDateWhenThereIsOne)

    @Test
    fun onlyMembersAreOffered() =
        runComposeUiTest(block = MemberPickerMembershipScenarios.onlyMembersAreOffered)

    @Test
    fun aNonMemberMatchingTheQueryBetterIsStillNotOffered() =
        runComposeUiTest(block = MemberPickerMembershipScenarios.aNonMemberMatchingTheQueryBetterIsStillNotOffered)

    @Test
    fun anAssigneeWhoLeftTheProjectStillRendersTheirPill() =
        runComposeUiTest(block = MemberPickerMembershipScenarios.anAssigneeWhoLeftTheProjectStillRendersTheirPill)

    @Test
    fun aCardWithABlockerNamesIt() = runComposeUiTest(block = WorkItemCardDependenciesScenarios.aCardWithABlockerNamesIt)

    @Test
    fun twoBlockersAreBothNamed() = runComposeUiTest(block = WorkItemCardDependenciesScenarios.twoBlockersAreBothNamed)

    @Test
    fun beyondTwoNamesTwoAndCountsTheRest() =
        runComposeUiTest(block = WorkItemCardDependenciesScenarios.beyondTwoNamesTwoAndCountsTheRest)

    @Test
    fun aCardWithNoBlockersDrawsNoDependencyRow() =
        runComposeUiTest(block = WorkItemCardDependenciesScenarios.aCardWithNoBlockersDrawsNoDependencyRow)

    @Test
    fun aBlockerOutsideTheVisibleSetStillNamesItsKey() =
        runComposeUiTest(block = WorkItemCardDependenciesScenarios.aBlockerOutsideTheVisibleSetStillNamesItsKey)

    @Test
    fun omitsTheDueDateWhenThereIsNone() = runComposeUiTest(block = WorkItemCardScenarios.omitsTheDueDateWhenThereIsNone)

    @Test
    fun doesNotRepeatTheStatusTheColumnAlreadyNames() = runComposeUiTest(block = WorkItemCardScenarios.doesNotRepeatTheStatusTheColumnAlreadyNames)

    @Test
    fun tappingACardSelectsItsTask() = runComposeUiTest(block = WorkItemCardScenarios.tappingACardSelectsItsTask)

    @Test
    fun showsTheCopyControl() = runComposeUiTest(block = WorkItemCardScenarios.showsTheCopyControl)

    @Test
    fun showsTagsAsIconsWithoutTheirNames() =
        runComposeUiTest(block = WorkItemCardScenarios.showsTagsAsIconsWithoutTheirNames)

    @Test
    fun copyingACardHandsTheTitleAndDetailsToTheHost() =
        runComposeUiTest(block = WorkItemCardScenarios.copyingACardHandsTheTitleAndDetailsToTheHost)

    @Test
    fun copyingACardConfirmsItOnTheBoard() =
        runComposeUiTest(block = BoardCopyScenarios.copyingACardConfirmsItOnTheBoard)

    @Test
    fun aRowWithNoOpenerIsNotPressable() = runComposeUiTest(block = AttachmentRowScenarios.aRowWithNoOpenerIsNotPressable)

    @Test
    fun pressingTheNameAsksToOpenThatFile() = runComposeUiTest(block = AttachmentRowScenarios.pressingTheNameAsksToOpenThatFile)

    @Test
    fun anOpenInFlightReplacesTheStateCaption() = runComposeUiTest(block = AttachmentRowScenarios.anOpenInFlightReplacesTheStateCaption)

    @Test
    fun aRowThatIsNotOpeningShowsItsSyncState() = runComposeUiTest(block = AttachmentRowScenarios.aRowThatIsNotOpeningShowsItsSyncState)

    @Test
    fun aHeldPickReadsAsPendingNotAttached() = runComposeUiTest(block = AttachmentRowScenarios.aHeldPickReadsAsPendingNotAttached)

    @Test
    fun removingAHeldPickNamesItsPosition() = runComposeUiTest(block = AttachmentRowScenarios.removingAHeldPickNamesItsPosition)

    @Test
    fun theTitleFieldShowsWhatYouType() = runComposeUiTest(block = WorkItemEditorScenarios.theTitleFieldShowsWhatYouType)

    @Test
    fun theActionsFloatOverTheScrollingForm() = runComposeUiTest(block = WorkItemEditorScenarios.theActionsFloatOverTheScrollingForm)

    @Test
    fun aMemberSeesNoTagSectionWithoutACatalog() =
        runComposeUiTest(block = WorkItemEditorScenarios.aMemberSeesNoTagSectionWithoutACatalog)

    @Test
    fun anAdminCanOpenTheProjectEditorFromAnEmptyTagSection() =
        runComposeUiTest(block = WorkItemEditorScenarios.anAdminCanOpenTheProjectEditorFromAnEmptyTagSection)

    @Test
    fun clickingSortButtonCyclesSortOrders() = runComposeUiTest(block = BoardColumnScenarios.clickingSortButtonCyclesSortOrders)

    @Test
    fun theMyTasksFilterButtonIsDisplayed() = runComposeUiTest(block = BoardLaneScenarios.theMyTasksFilterButtonIsDisplayed)

    @Test
    fun aRunningSprintHidesEveryLaneAdd() = runComposeUiTest(block = BoardLaneScenarios.aRunningSprintHidesEveryLaneAdd)

    @Test
    fun kanbanKeepsEveryLaneAdd() = runComposeUiTest(block = BoardLaneScenarios.kanbanKeepsEveryLaneAdd)

    /** A held card can be carried to another lane and moved there on release. */
    @Test
    fun draggingACardToAnotherLaneMovesItThere() =
        runComposeUiTest(block = BoardDragScenarios.draggingACardToAnotherLaneMovesItThere)

    /** Releasing a card on its own lane is not a move. */
    @Test
    fun droppingACardOnItsOwnLaneDoesNotMoveIt() =
        runComposeUiTest(block = BoardDragScenarios.droppingACardOnItsOwnLaneDoesNotMoveIt)

    /** A quick swipe stays a scroll; it does not lift the card. */
    @Test
    fun aQuickSwipeOnACardDoesNotLiftIt() =
        runComposeUiTest(block = BoardDragScenarios.aQuickSwipeOnACardDoesNotLiftIt)

    @Test
    fun theCardShowsTheGoalAndItsEditButtonOpensTheEditor() = runComposeUiTest(block = SprintDetailScenarios.theCardShowsTheGoalAndItsEditButtonOpensTheEditor)

    @Test
    fun theCardOmitsTheGoalLineWhenThereIsNone() = runComposeUiTest(block = SprintDetailScenarios.theCardOmitsTheGoalLineWhenThereIsNone)

    @Test
    fun aBusyCardOffersNoEdit() = runComposeUiTest(block = SprintDetailScenarios.aBusyCardOffersNoEdit)

    @Test
    fun theLockedWindowFreezesTheCapAndTheDates() = runComposeUiTest(block = SprintEditorScenarios.theLockedWindowFreezesTheCapAndTheDates)

    @Test
    fun anUnlockedWindowDrawsNoNoteAndEverythingLive() = runComposeUiTest(block = SprintEditorScenarios.anUnlockedWindowDrawsNoNoteAndEverythingLive)

    @Test
    fun blockersRenderAsWrappedPillsEachWithARemoveCross() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.blockersRenderAsWrappedPillsEachWithARemoveCross)

    @Test
    fun noBlockersShowsAPlainMessageAndHidesThePillRow() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.noBlockersShowsAPlainMessageAndHidesThePillRow)

    @Test
    fun wrappingPillsDoNotOverflowTheFirstRow() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.wrappingPillsDoNotOverflowTheFirstRow)

    @Test
    fun eachBucketSegmentReadsItsOwnBucket() = runComposeUiTest(block = AdminProjectsListScenarios.eachBucketSegmentReadsItsOwnBucket)
}
