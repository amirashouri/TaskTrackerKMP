package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * The project-presentation scenario suites - card content rules, board column
 * behaviour, the board's lane row geometry, the archive round trip, the projects list,
 * the sprint detail card, and the sprint editor sheet - on the iOS simulator.
 *
 * There is no `commonTest` entry point for these on purpose. A Compose UI test on the
 * Android host needs Robolectric, and Robolectric only engages when a JUnit 4 runner is
 * attached; `@RunWith` is not expressible in `commonTest`, and a `commonTest` class runs on
 * *every* target, so one set of entry points there would fail on the host even with the
 * host's own file beside it. Every assertion lives in the scenario objects in `commonTest`;
 * these files are only entry points, which is why they are near-identical.
 *
 * Each entry point below is generated from a `val` declared on one of those objects, so a new
 * scenario cannot be written and then quietly not run.
 *
 * The same entry points as `ProjectsUiJvmTest`, on the platform where the real iOS
 * rendering and the real text shaping happen. Two things are worth running here that no
 * other target can check: `ttText`'s `overflow` parameter only does anything under Skia's
 * text measurer, and a backtick test name has to survive being mangled into an Objective-C
 * selector - a name containing a comma compiles for `jvmTest` and fails this target.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectsUiIosTest {
    @Test
    fun theBacklogRowFilesItsOwnTaskAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.theBacklogRowFilesItsOwnTaskAway)

    @Test
    fun theBoardCardFilesItsOwnTaskAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.theBoardCardFilesItsOwnTaskAway)

    @Test
    fun aBusyCardDoesNotFileAnythingAway() = runComposeUiTest(block = ArchiveRoundTripScenarios.aBusyCardDoesNotFileAnythingAway)

    @Test
    fun aDeliveredCardOffersClose() = runComposeUiTest(block = ArchiveRoundTripScenarios.aDeliveredCardOffersClose)

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
    fun aShorterHoldStillLiftsTheCard() =
        runComposeUiTest(block = BoardDragScenarios.aShorterHoldStillLiftsTheCard)

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

    @Test
    fun wideLayoutRendersEveryProjectInGrid() =
        runComposeUiTest(block = ProjectsListScenarios.wideLayoutRendersEveryProjectInGrid)

    @Test
    fun wideLayoutDirectDeleteAsksConfirmation() =
        runComposeUiTest(block = ProjectsListScenarios.wideLayoutDirectDeleteAsksConfirmation)

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
    fun backlogRowShowsTwoLinesOfTheDescription() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.showsTwoLinesOfTheDescription)

    @Test
    fun backlogRowOmitsTheDescriptionWhenThereIsNone() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.omitsTheDescriptionWhenThereIsNone)

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
    fun aBacklogRowWithADesyncedLaneShowsTheLaneStatus() =
        runComposeUiTest(block = WorkItemBacklogRowScenarios.aRowWithADesyncedLaneShowsTheLaneStatus)

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

    /** The header's glass housing appears only while rows are scrolled under it. */
    @Test
    fun theHeaderHousingAppearsOnlyWhenRowsScrollUnderIt() =
        runComposeUiTest(block = ProjectBacklogScenarios.theHeaderHousingAppearsOnlyWhenRowsScrollUnderIt)

    @Test
    fun theSectionPickerSharesTheHeadersLeadingEdge() =
        runComposeUiTest(block = ProjectBacklogScenarios.theSectionPickerSharesTheHeadersLeadingEdge)

    /** The sort row and the field share the picker's width, and the field is below the sort. */
    @Test
    fun theSortAndSearchRowsShareThePickersWidth() =
        runComposeUiTest(block = ProjectBacklogScenarios.theSortAndSearchRowsShareThePickersWidth)

    /** A wide header puts the picker between the project selector and the two icon actions. */
    @Test
    fun theWideHeaderPutsThePickerBetweenTheSelectorAndTheActions() =
        runComposeUiTest(block = ProjectBacklogScenarios.theWideHeaderPutsThePickerBetweenTheSelectorAndTheActions)

    /** Opening the search beside the sort row makes room for it with travel, not a jump. */
    @Test
    fun theWideSearchShrinksTheSortRowAsItReveals() =
        runComposeUiTest(block = ProjectBacklogScenarios.theWideSearchShrinksTheSortRowAsItReveals)

    /** A section switch leaves the open field in the same row and trailing slot. */
    @Test
    fun theWideSearchKeepsItsSlotAcrossSections() =
        runComposeUiTest(block = ProjectBacklogScenarios.theWideSearchKeepsItsSlotAcrossSections)

    /** One board column: the resolver boards into it on the tap, with no picker. */
    @Test
    fun puttingOnBoardWithASingleColumnBoardsDirectly() =
        runComposeUiTest(block = ProjectBacklogScenarios.puttingOnBoardWithASingleColumnBoardsDirectly)

    /** Several columns and no remembered default: the tap opens the picker instead of boarding. */
    @Test
    fun puttingOnBoardWithSeveralColumnsOpensThePicker() =
        runComposeUiTest(block = ProjectBacklogScenarios.puttingOnBoardWithSeveralColumnsOpensThePicker)

    /** Picking a column in the sheet boards into it and dismisses the sheet. */
    @Test
    fun choosingAColumnInThePickerBoardsIntoIt() =
        runComposeUiTest(block = ProjectBacklogScenarios.choosingAColumnInThePickerBoardsIntoIt)

    /** A remembered default the catalog still has boards directly, no picker. */
    @Test
    fun aRememberedDefaultTheProjectStillHasBoardsDirectly() =
        runComposeUiTest(block = ProjectBacklogScenarios.aRememberedDefaultTheProjectStillHasBoardsDirectly)

    /** A remembered default the catalog no longer contains is ignored and the picker opens. */
    @Test
    fun aRememberedDefaultTheCatalogDroppedOpensThePicker() =
        runComposeUiTest(block = ProjectBacklogScenarios.aRememberedDefaultTheCatalogDroppedOpensThePicker)

    /** A project with no catalog falls back to the legacy Todo lane, no picker. */
    @Test
    fun puttingOnBoardWithoutACatalogBoardsToLegacyTodo() =
        runComposeUiTest(block = ProjectBacklogScenarios.puttingOnBoardWithoutACatalogBoardsToLegacyTodo)

    /** The header's Select control enters multi-select and the bar names the count. */
    @Test
    fun theHeaderSelectEntersTheMode() =
        runComposeUiTest(block = ProjectBacklogScenarios.theHeaderSelectEntersTheMode)

    /** A select-mode row tap ticks and unticks instead of opening the editor. */
    @Test
    fun tappingARowInSelectModeTicksIt() =
        runComposeUiTest(block = ProjectBacklogScenarios.tappingARowInSelectModeTicksIt)

    /** A long press enters the mode with the pressed row ticked. */
    @Test
    fun longPressingARowEntersTheModeWithItTicked() =
        runComposeUiTest(block = ProjectBacklogScenarios.longPressingARowEntersTheModeWithItTicked)

    /** The bar boards the whole set into one lane with one commit. */
    @Test
    fun theSelectionBarBoardsTheSetIntoOneLane() =
        runComposeUiTest(block = ProjectBacklogScenarios.theSelectionBarBoardsTheSetIntoOneLane)

    /** Archive is refused at zero selected and acts on a ticked row. */
    @Test
    fun theArchiveActionWaitsForATick() =
        runComposeUiTest(block = ProjectBacklogScenarios.theArchiveActionWaitsForATick)

    /** Switching to another section ends selection rather than leaving ticks behind. */
    @Test
    fun leavingTheBacklogSectionEndsSelection() =
        runComposeUiTest(block = ProjectBacklogScenarios.leavingTheBacklogSectionEndsSelection)

    /** Several columns: the selection asks for one lane and boards the set into it. */
    @Test
    fun theSelectionWithSeveralColumnsAsksForOneLane() =
        runComposeUiTest(block = ProjectBacklogScenarios.theSelectionWithSeveralColumnsAsksForOneLane)

    /** While a batch runs the bar says so and refuses both actions. */
    @Test
    fun theBarRefusesActionsAndNamesTheWorkInProgress() =
        runComposeUiTest(block = ProjectBacklogScenarios.theBarRefusesActionsAndNamesTheWorkInProgress)

    /** The lane header's Select starts a selection scoped to that lane. */
    @Test
    fun theLaneHeaderStartsItsOwnSelection() =
        runComposeUiTest(block = BoardSelectionScenarios.theLaneHeaderStartsItsOwnSelection)

    /** Only the lane that owns the selection draws ticks. */
    @Test
    fun onlyTheSelectionLaneDrawsTicks() =
        runComposeUiTest(block = BoardSelectionScenarios.onlyTheSelectionLaneDrawsTicks)

    /** A card tap ticks and unticks it without opening the editor. */
    @Test
    fun tappingACardTicksAndUnticksIt() =
        runComposeUiTest(block = BoardSelectionScenarios.tappingACardTicksAndUnticksIt)

    /** Select all ticks exactly the lane's cards. */
    @Test
    fun selectAllTakesTheLanesCards() =
        runComposeUiTest(block = BoardSelectionScenarios.selectAllTakesTheLanesCards)

    /** The bar archives the set, and Clear leaves the mode. */
    @Test
    fun theBarArchivesTheSelectionAndClearEndsTheMode() =
        runComposeUiTest(block = BoardSelectionScenarios.theBarArchivesTheSelectionAndClearEndsTheMode)

    /** Move to asks for a destination lane and reports the chosen one. */
    @Test
    fun moveAsksForALaneAndReportsIt() =
        runComposeUiTest(block = BoardSelectionScenarios.moveAsksForALaneAndReportsIt)

    @Test
    fun wideLayoutClampsContentWidthAndCenters() =
        runComposeUiTest(block = ProjectBacklogScenarios.wideLayoutClampsContentWidthAndCenters)

    @Test
    fun wideLayoutHeaderAlignsWithListContent() =
        runComposeUiTest(block = ProjectBacklogScenarios.wideLayoutHeaderAlignsWithListContent)

    @Test
    fun showsTheKeyTitleAndPriority() = runComposeUiTest(block = WorkItemCardScenarios.showsTheKeyTitleAndPriority)

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
    fun thePillsRemoveCrossHasAFullHitArea() =
        runComposeUiTest(block = MemberPickerMembershipScenarios.thePillsRemoveCrossHasAFullHitArea)

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
    fun showsTheAssigneeWhenThereIsOne() = runComposeUiTest(block = WorkItemCardScenarios.showsTheAssigneeWhenThereIsOne)

    @Test
    fun omitsTheAssigneeWhenThereIsNone() = runComposeUiTest(block = WorkItemCardScenarios.omitsTheAssigneeWhenThereIsNone)

    @Test
    fun showsTheDueDateWhenThereIsOne() = runComposeUiTest(block = WorkItemCardScenarios.showsTheDueDateWhenThereIsOne)

    @Test
    fun omitsTheDueDateWhenThereIsNone() = runComposeUiTest(block = WorkItemCardScenarios.omitsTheDueDateWhenThereIsNone)

    @Test
    fun doesNotRepeatTheStatusTheColumnAlreadyNames() = runComposeUiTest(block = WorkItemCardScenarios.doesNotRepeatTheStatusTheColumnAlreadyNames)

    @Test
    fun tappingACardSelectsItsTask() = runComposeUiTest(block = WorkItemCardScenarios.tappingACardSelectsItsTask)

    @Test
    fun showsTheCopyControl() = runComposeUiTest(block = WorkItemCardScenarios.showsTheCopyControl)

    @Test
    fun showsOneLineOfTheDescription() =
        runComposeUiTest(block = WorkItemCardScenarios.showsOneLineOfTheDescription)

    @Test
    fun omitsTheDescriptionWhenThereIsNone() =
        runComposeUiTest(block = WorkItemCardScenarios.omitsTheDescriptionWhenThereIsNone)

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
    fun tappingAFieldCardFocusesItsField() = runComposeUiTest(block = WorkItemEditorScenarios.tappingAFieldCardFocusesItsField)

    @Test
    fun theActionsFloatOverTheScrollingForm() = runComposeUiTest(block = WorkItemEditorScenarios.theActionsFloatOverTheScrollingForm)

    @Test
    fun aMemberSeesNoTagSectionWithoutACatalog() =
        runComposeUiTest(block = WorkItemEditorScenarios.aMemberSeesNoTagSectionWithoutACatalog)

    @Test
    fun anAdminCanOpenTheProjectEditorFromAnEmptyTagSection() =
        runComposeUiTest(block = WorkItemEditorScenarios.anAdminCanOpenTheProjectEditorFromAnEmptyTagSection)

    @Test
    fun cancellingACleanProjectEditorClosesIt() =
        runComposeUiTest(block = ProjectEditorScenarios.cancellingACleanEditorClosesIt)

    @Test
    fun cancellingADirtyProjectEditorAsks() =
        runComposeUiTest(block = ProjectEditorScenarios.cancellingADirtyEditorAsksBeforeDiscarding)

    @Test
    fun cancellingACleanProjectSheetClosesIt() =
        runComposeUiTest(block = ProjectEditorScenarios.cancellingACleanProjectSheetClosesIt)

    @Test
    fun cancellingADirtyProjectSheetAsks() =
        runComposeUiTest(block = ProjectEditorScenarios.cancellingADirtyProjectSheetAsksBeforeDiscarding)

    /** A member's edit cannot save a repository URL, so its form must not offer the field. */
    @Test
    fun theMemberProjectEditSheetOffersNoRepositoryField() =
        runComposeUiTest(block = ProjectEditorScenarios.theMemberProjectEditSheetOffersNoRepositoryField)

    /** The admin's form keeps the field it can save, and its text reaches the model. */
    @Test
    fun theAdminProjectFormKeepsTheRepositoryField() =
        runComposeUiTest(block = ProjectEditorScenarios.theAdminProjectFormKeepsTheRepositoryField)

    @Test
    fun theStatusCatalogSectionEditsAndRemovesStages() =
        runComposeUiTest(block = ProjectEditorScenarios.theStatusCatalogSectionEditsAndRemovesStages)

    @Test
    fun addingAStageSendsTheComposerValues() =
        runComposeUiTest(block = ProjectEditorScenarios.addingAStageSendsTheComposerValues)

    @Test
    fun theTagCatalogSectionEditsAnExistingIcon() =
        runComposeUiTest(block = ProjectEditorScenarios.theTagCatalogSectionEditsAnExistingIcon)

    @Test
    fun cancellingACleanWorkItemEditorClosesIt() =
        runComposeUiTest(block = WorkItemEditorScenarios.cancellingACleanEditorClosesIt)

    @Test
    fun cancellingADirtyWorkItemEditorAsks() =
        runComposeUiTest(block = WorkItemEditorScenarios.cancellingADirtyEditorAsksBeforeDiscarding)

    @Test
    fun aFiledAwayWorkItemShowsAReadOnlyStatusChip() =
        runComposeUiTest(block = WorkItemEditorScenarios.aFiledAwayItemShowsAReadOnlyStatusChip)

    @Test
    fun everyWorkItemEditorPickerOptionStaysOnOneLine() =
        runComposeUiTest(block = WorkItemEditorScenarios.everyPickerOptionStaysOnOneLine)

    @Test
    fun aWorkItemEditorPickerDoesNotStretchAcrossAWideForm() =
        runComposeUiTest(block = WorkItemEditorScenarios.aPickerDoesNotStretchAcrossAWideForm)

    @Test
    fun aWorkItemEditorPickerFillsAPhoneWidthForm() =
        runComposeUiTest(block = WorkItemEditorScenarios.aPickerFillsAPhoneWidthForm)

    @Test
    fun clickingSortButtonCyclesSortOrders() = runComposeUiTest(block = BoardColumnScenarios.clickingSortButtonCyclesSortOrders)

    @Test
    fun theCardShowsTheGoalAndItsEditButtonOpensTheEditor() = runComposeUiTest(block = SprintDetailScenarios.theCardShowsTheGoalAndItsEditButtonOpensTheEditor)

    @Test
    fun theCardOmitsTheGoalLineWhenThereIsNone() = runComposeUiTest(block = SprintDetailScenarios.theCardOmitsTheGoalLineWhenThereIsNone)

    @Test
    fun aBusyCardOffersNoEdit() = runComposeUiTest(block = SprintDetailScenarios.aBusyCardOffersNoEdit)

    @Test
    fun theSummaryListsMembersAndTheirFinishedPoints() = runComposeUiTest(block = SprintMemberSummaryScenarios.theSummaryListsMembersAndTheirFinishedPoints)

    @Test
    fun theProgressScreenShowsCapacityAndUnclaimedWork() = runComposeUiTest(block = SprintMemberSummaryScenarios.theProgressScreenShowsCapacityAndUnclaimedWork)

    @Test
    fun theMemberSheetPicksExactlyOnePersonForOneCapacity() = runComposeUiTest(block = SprintMemberSummaryScenarios.theMemberSheetPicksExactlyOnePersonForOneCapacity)

    @Test
    fun theMemberSheetOnlySavesAWholeNonNegativeCapacity() = runComposeUiTest(block = SprintMemberSummaryScenarios.theMemberSheetOnlySavesAWholeNonNegativeCapacity)

    @Test
    fun theLockedWindowFreezesTheCapAndTheDates() = runComposeUiTest(block = SprintEditorScenarios.theLockedWindowFreezesTheCapAndTheDates)

    @Test
    fun anUnlockedWindowDrawsNoNoteAndEverythingLive() = runComposeUiTest(block = SprintEditorScenarios.anUnlockedWindowDrawsNoNoteAndEverythingLive)

    @Test
    fun aPassedErrorMessageRendersAsABannerAboveSave() = runComposeUiTest(block = SprintEditorScenarios.aPassedErrorMessageRendersAsABannerAboveSave)

    @Test
    fun blockersRenderAsWrappedPillsEachWithARemoveCross() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.blockersRenderAsWrappedPillsEachWithARemoveCross)

    @Test
    fun noBlockersShowsAPlainMessageAndHidesThePillRow() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.noBlockersShowsAPlainMessageAndHidesThePillRow)

    @Test
    fun wrappingPillsDoNotOverflowTheFirstRow() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.wrappingPillsDoNotOverflowTheFirstRow)

    @Test
    fun theRemoveCrossHasAFullHitAreaWithoutOpeningTheBlocker() = runComposeUiTest(block = WorkItemDependencyPillsScenarios.theRemoveCrossHasAFullHitAreaWithoutOpeningTheBlocker)

    @Test
    fun eachBucketSegmentReadsItsOwnBucket() = runComposeUiTest(block = AdminProjectsListScenarios.eachBucketSegmentReadsItsOwnBucket)

    @Test
    fun theTrashAsksThenDeletes() = runComposeUiTest(block = AdminProjectsListScenarios.theTrashAsksThenDeletes)

    @Test
    fun openWorkIsArchivedInsteadOfDeleted() = runComposeUiTest(block = AdminProjectsListScenarios.openWorkIsArchivedInsteadOfDeleted)

    @Test
    fun theBoardNarrowsToTheMatchingCard() = runComposeUiTest(block = WorkItemSearchScenarios.theBoardNarrowsToTheMatchingCard)

    @Test
    fun theBoardKeepsEveryLaneWhileSearching() = runComposeUiTest(block = WorkItemSearchScenarios.theBoardKeepsEveryLaneWhileSearching)

    @Test
    fun theBoardWaitsForTheDebounce() = runComposeUiTest(block = WorkItemSearchScenarios.theBoardWaitsForTheDebounce)

    @Test
    fun theBoardShowsTheNoResultsState() = runComposeUiTest(block = WorkItemSearchScenarios.theBoardShowsTheNoResultsState)

    @Test
    fun clearingTheSearchRestoresTheListAtOnce() = runComposeUiTest(block = WorkItemSearchScenarios.clearingTheSearchRestoresTheListAtOnce)

    @Test
    fun theBacklogNarrowsTheSelectedSegment() = runComposeUiTest(block = WorkItemSearchScenarios.theBacklogNarrowsTheSelectedSegment)

    @Test
    fun aSegmentWithNoMatchesSaysSo() = runComposeUiTest(block = WorkItemSearchScenarios.aSegmentWithNoMatchesSaysSo)

    @Test
    fun switchingSegmentsKeepsTheQuery() = runComposeUiTest(block = WorkItemSearchScenarios.switchingSegmentsKeepsTheQuery)

    @Test
    fun theBoardHidesTheSearchUntilAsked() = runComposeUiTest(block = WorkItemSearchScenarios.theBoardHidesTheSearchUntilAsked)

    @Test
    fun closingTheBoardSearchClearsTheQuery() = runComposeUiTest(block = WorkItemSearchScenarios.closingTheBoardSearchClearsTheQuery)

    @Test
    fun theFieldUnfoldsRatherThanSnapping() = runComposeUiTest(block = WorkItemSearchScenarios.theFieldUnfoldsRatherThanSnapping)

    @Test
    fun theWideBoardOpensTheFieldBesideItsToggle() =
        runComposeUiTest(block = WorkItemSearchScenarios.theWideBoardOpensTheFieldBesideItsToggle)

    @Test
    fun reducedMotionRevealsTheFieldWithoutTravel() = runComposeUiTest(block = WorkItemSearchScenarios.reducedMotionRevealsTheFieldWithoutTravel)

    @Test
    fun theBacklogHidesTheSearchUntilAsked() = runComposeUiTest(block = WorkItemSearchScenarios.theBacklogHidesTheSearchUntilAsked)

    @Test
    fun theRosterShowsEachMachineItsHostAndHealth() =
        runComposeUiTest(block = AgentManagementScenarios.theRosterShowsEachMachineItsHostAndHealth)

    @Test
    fun anEmptyRosterSaysSo() = runComposeUiTest(block = AgentManagementScenarios.anEmptyRosterSaysSo)

    @Test
    fun tappingRefreshReReadsTheRoster() = runComposeUiTest(block = AgentManagementScenarios.tappingRefreshReReadsTheRoster)

    @Test
    fun thePairingHostChoiceReachesTheServer() =
        runComposeUiTest(block = AgentManagementScenarios.thePairingHostChoiceReachesTheServer)

    @Test
    fun theAgentEnabledToggleReportsItsState() =
        runComposeUiTest(block = AgentManagementScenarios.theAgentEnabledToggleReportsItsState)

    @Test
    fun theDispatchTargetPickerRoutesTheChosenMachine() =
        runComposeUiTest(block = AgentManagementScenarios.theDispatchTargetPickerRoutesTheChosenMachine)

    @Test
    fun aTerminalRunWithADiffOpensTheDiffSheet() =
        runComposeUiTest(block = AgentManagementScenarios.aTerminalRunWithADiffOpensTheDiffSheet)

    @Test
    fun closingTheBacklogSearchClearsTheQuery() = runComposeUiTest(block = WorkItemSearchScenarios.closingTheBacklogSearchClearsTheQuery)

    /** A wide header puts the sprint picker between the project selector and the add action. */
    @Test
    fun theWideSprintHeaderPutsThePickerBetweenTheSelectorAndTheAction() =
        runComposeUiTest(block = SprintListScenarios.theWideHeaderPutsThePickerBetweenTheSelectorAndTheAction)

    /** A compact header keeps the sprint picker on its own row under the actions. */
    @Test
    fun theCompactSprintHeaderKeepsThePickerBelowTheActions() =
        runComposeUiTest(block = SprintListScenarios.theCompactHeaderKeepsThePickerBelowTheActions)

    /** A desktop window caps the sprint header and rows and centres them. */
    @Test
    fun wideLayoutClampsSprintContentWidthAndCenters() =
        runComposeUiTest(block = SprintListScenarios.wideLayoutClampsContentWidthAndCenters)
}
