package com.tasktracker.featureprojectspresentation

/**
 * The backlog screen, as scenarios.
 *
 * ## What these are here for
 *
 * The backlog carried a floating create button in the bottom trailing corner, over a list whose
 * `contentPadding` reserved the tab bar and nothing else - the same arrangement as the projects
 * list, but not the same defect. Here the button sat on the *rows*, which are clickable across
 * their full width, rather than on their action buttons. See
 * [theAddActionDoesNotCoverTheLastRow] for the measurement that settled which it was.
 *
 * Nothing tested this screen's layout at all before now, which is the other half of why it
 * survived: there was no assertion here that could notice either shape.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.EpicID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.TTToastTag
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.domain.Epic
import com.tasktracker.domain.Project
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_put_on_board_prompt_body_many
import com.tasktracker.resources.backlog_selection_count
import com.tasktracker.resources.backlog_selection_working
import com.tasktracker.resources.backlog_section_backlog
import com.tasktracker.resources.backlog_section_kanban
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
object ProjectBacklogScenarios {

    /**
     * The add action and the rows themselves do not occupy the same pixels.
     *
     * ## The defect this exists to catch
     *
     * The button used to float in the bottom trailing corner, over a `LazyColumn` whose
     * `contentPadding` reserved the tab bar and nothing else. So the last row reaching the bottom
     * of the screen had a 56dp circle sitting on it, and the circle won the touch.
     *
     * ## Why the rows and not their action buttons - a correction
     *
     * This scenario was first written to assert the add action missed "Put on board" and
     * "File away", on the assumption that a row's actions are trailing the way `ProjectRow`'s
     * are. They are not: `WorkItemBacklogRow` lays its action row out with
     * `Arrangement.spacedBy(ttSpacing.sm)` and no `Alignment.End`, so those buttons sit at the
     * *leading* edge, measured at x 42..237 on a 1008px-wide window, while a bottom trailing
     * control sits at x 964..1008. They were never going to collide, and the assertion passed
     * against a button that was genuinely floating over the list.
     *
     * What the floating button actually intercepted was the **row**, which is
     * `Modifier.clickable(onClick = onPutOnBoard)` across its full width: tapping the bottom right
     * of a backlog did nothing instead of filing the task to the board. That is the defect, and
     * the row is what it has to be measured against.
     *
     * The header floats over the list now, so a row scrolled up behind it is deliberately under
     * the add action. The **last** row is what this scenario keeps measuring: it is the one the
     * floating button covered, and it is still the one at the bottom when the list ends.
     */
    val theAddActionDoesNotCoverTheLastRow: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems()
        backlogShowing(items = items, onAddTask = {})

        // Scrolled to the end, because that is where the defect lived. At scroll position zero
        // the final rows are clipped away by the bottom of the window and a corner control
        // overlaps nothing, so the assertion would have passed against the bug.
        onNodeWithTag(ProjectBacklogListTag).performScrollToIndex(items.lastIndex)
        waitForIdle()

        val create = onNodeWithTag(BacklogAddTaskTag).fetchSemanticsNode().boundsInRoot
        val lastRows = composedRow(items.last())

        assertTrue(
            lastRows.isNotEmpty(),
            "expected the last row to be composed, or this asserts nothing",
        )
        // The last row, not every composed row: the header now floats over the list, so rows
        // that have scrolled up behind it are *meant* to sit under the add action. The last row
        // is the one the old floating button covered, and touching it must reach it.
        for (row in lastRows) {
            assertFalse(
                create.overlaps(row),
                "the add action sits on the last row: add=$create row=$row",
            )
        }
    }

    /**
     * The add action sits on the switcher's row, to its right, and clear of the first row.
     *
     * The overlap assertion above would pass for a button placed *anywhere* clear of the rows -
     * a top app bar, a bottom bar, anywhere - so on its own it does not pin down what was asked
     * for. This one does: the action is to the right of the switcher, and above the first row.
     *
     * The row is the reference, not the list: the list's viewport deliberately starts at the top
     * of the screen so scrolled rows can pass under the header, so `list.top` is above the add
     * action by design. What must not happen is the action covering content, and the first row is
     * the content that is there when the list is at rest.
     *
     * Two rectangles and a vertical comparison, rather than a screenshot, because "is it beside
     * this other thing" survives being read off the tree.
     */
    val theAddActionTrailsTheSwitcher: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems()
        backlogShowing(items = items, onAddTask = {})

        val add = onNodeWithTag(BacklogAddTaskTag).fetchSemanticsNode().boundsInRoot
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val firstRow = onNested(WorkItemBacklogRowTag(items.first())).fetchSemanticsNode().boundsInRoot

        assertTrue(
            add.left >= switcher.right,
            "the add action should trail the switcher, not sit under it: " +
                "switcher=$switcher add=$add",
        )
        assertTrue(
            add.bottom <= firstRow.top,
            "the add action should be above the first row rather than over it: " +
                "add=$add firstRow=$firstRow",
        )
    }

    /**
     * The rows scroll under the switcher and the section picker, and start clear of them.
     *
     * Both halves are the feature. At rest the first row must be below the header, or it would be
     * unreadable and untappable behind the glass. Once scrolled, the viewport must have rows
     * *behind* the header's band, or the list would still be stopping at a strip above it and
     * there would be no glass to see the content through.
     *
     * Bounds rather than a screenshot: "the list reaches the top of the screen", "the first row
     * is below the picker" and "a row overlaps the picker's band" are three claims the semantics
     * tree already reports, and they are the same three on every host.
     */
    val theListScrollsUnderTheHeader: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems()
        backlogShowing(items = items, onAddTask = {})

        val list = onNodeWithTag(ProjectBacklogListTag).fetchSemanticsNode().boundsInRoot
        val sections = onNodeWithTag(ProjectBacklogSectionsTag).fetchSemanticsNode().boundsInRoot
        val firstRow = onNested(WorkItemBacklogRowTag(items.first())).fetchSemanticsNode().boundsInRoot

        assertTrue(
            list.top < sections.top,
            "the list viewport should start above the header, so rows can pass under it: " +
                "list=$list sections=$sections",
        )
        assertTrue(
            firstRow.top >= sections.bottom,
            "at rest the first row should start below the picker: sections=$sections row=$firstRow",
        )

        onNodeWithTag(ProjectBacklogListTag).performTouchInput { swipeUp() }
        waitForIdle()

        val rows = items.flatMap { composedRow(it) }
        val underHeader = rows.filter { it.top < sections.bottom && it.bottom > sections.top }

        assertTrue(
            underHeader.isNotEmpty(),
            "after scrolling, a row should be visible under the picker's band: " +
                "sections=$sections rows=$rows",
        )
    }

    /**
     * The header's housing appears only while rows are behind it, and goes away at rest.
     *
     * The housing is a backdrop for rows scrolling under the header, not chrome the screen always
     * wears. At scroll position zero the rows begin below the card, so there is nothing to blur and
     * drawing one would put a panel of empty glass around controls that already carry their own.
     * The node is composed only above zero alpha, so both halves of that - present when scrolled,
     * absent at rest - are readable from the semantics tree rather than inferred from a pixel.
     */
    val theHeaderHousingAppearsOnlyWhenRowsScrollUnderIt: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(items = backlogItems(), onAddTask = {})
        waitForIdle()

        onNodeWithTag(ProjectBacklogHeaderHousingTag).assertDoesNotExist()

        onNodeWithTag(ProjectBacklogListTag).performTouchInput { swipeUp() }
        waitForIdle()

        onNodeWithTag(ProjectBacklogHeaderHousingTag).assertIsDisplayed()

        // Back to the top: the rows are clear of the header again, so the housing must go with them.
        onNodeWithTag(ProjectBacklogListTag).performScrollToIndex(0)
        waitForIdle()

        onNodeWithTag(ProjectBacklogHeaderHousingTag).assertDoesNotExist()
    }

    /**
     * The section picker shares the header's leading edge, and the sort row's.
     *
     * ## The defect this exists for
     *
     * The picker carried its own `xxl` horizontal gutter on top of the header column's, so it
     * began one gutter further in than the project switcher above it and the sort controls
     * below it: three stacked controls with two different indents, which read as a picker that
     * had slipped rather than as the middle of a column. Only the picker was wrong, so the
     * assertion is that the three edges agree.
     *
     * The sort row is measured through its own control node: the glass surface is the row's
     * leading edge, and the summary it holds starts at that surface's `md` inset inside it.
     */
    val theSectionPickerSharesTheHeadersLeadingEdge: suspend ComposeUiTest.() -> Unit = {
        // A compact header: on a wide one the picker is in the selector's row by design and
        // this stack no longer exists — see the wide-header scenario below.
        backlogShowing(items = backlogItems(), onAddTask = {}, width = COMPACT_HEADER_WIDTH)

        val picker = onNodeWithTag(ProjectBacklogSectionsTag).fetchSemanticsNode().boundsInRoot
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val sortRow = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            abs(picker.left - switcher.left) <= EDGE_TOLERANCE_PX,
            "the section picker starts at ${picker.left}px and the project switcher above it " +
                "at ${switcher.left}px, so the middle of the header is indented from its top: " +
                "picker=$picker switcher=$switcher",
        )
        assertTrue(
            abs(picker.left - sortRow.left) <= EDGE_TOLERANCE_PX,
            "the section picker starts at ${picker.left}px and the sort row below it at " +
                "${sortRow.left}px, so the picker is indented from the filter row: " +
                "picker=$picker sort=$sortRow",
        )
    }

    /**
     * A compact search overlays the actions row without moving the picker, sort row or list.
     *
     * The field used to be a fourth row under the sort controls. It shared the picker's width,
     * but it also grew the measured header and pushed the first row down on every frame of the
     * reveal. It now sits in the actions row, ending before the toggle. Presence alone cannot
     * tell a settled overlay from a field that took a row, so the picker, the sort row and the
     * first list row are measured before and after opening.
     */
    val theSortAndSearchRowsShareThePickersWidth: suspend ComposeUiTest.() -> Unit = {
        // The compact stack. The wide arrangement is a row, not a column of equal widths, and
        // has its own scenario below.
        val items = backlogItems()
        backlogShowing(items = items, onAddTask = {}, width = COMPACT_HEADER_WIDTH)
        waitForIdle()

        val pickerClosed = onNodeWithTag(ProjectBacklogSectionsTag).fetchSemanticsNode().boundsInRoot
        val sortClosed = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot
        val firstRowClosed = composedRow(items.first()).minBy { it.top }

        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        waitForIdle()

        val pickerOpen = onNodeWithTag(ProjectBacklogSectionsTag).fetchSemanticsNode().boundsInRoot
        val sortOpen = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot
        val firstRowOpen = composedRow(items.first()).minBy { it.top }
        val search = onNodeWithTag(ProjectBacklogSearchFieldTag).fetchSemanticsNode().boundsInRoot
        val toggle = onNodeWithTag(ProjectBacklogSearchToggleTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            abs(pickerOpen.top - pickerClosed.top) <= EDGE_TOLERANCE_PX &&
                abs(sortOpen.top - sortClosed.top) <= EDGE_TOLERANCE_PX,
            "opening search moved the header rows from picker=${pickerClosed.top}, " +
                "sort=${sortClosed.top} to picker=${pickerOpen.top}, sort=${sortOpen.top}: " +
                "the field must overlay a row, not add one",
        )
        assertTrue(
            abs(pickerOpen.left - pickerClosed.left) <= EDGE_TOLERANCE_PX &&
                abs(pickerOpen.width - pickerClosed.width) <= EDGE_TOLERANCE_PX &&
                abs(sortOpen.left - sortClosed.left) <= EDGE_TOLERANCE_PX &&
                abs(sortOpen.width - sortClosed.width) <= EDGE_TOLERANCE_PX,
            "opening search resized the header rows: sort=$sortOpen picker=$pickerOpen",
        )
        assertTrue(
            abs(firstRowOpen.top - firstRowClosed.top) <= EDGE_TOLERANCE_PX,
            "the first row moved from ${firstRowClosed.top}px to ${firstRowOpen.top}px when " +
                "search opened: the measured list inset changed instead of the field overlaying",
        )

        assertTrue(
            abs(search.left - pickerOpen.left) <= EDGE_TOLERANCE_PX,
            "the search field starts at ${search.left}px and the picker at ${pickerOpen.left}px, " +
                "so they do not share a column: search=$search picker=$pickerOpen",
        )
        assertTrue(
            search.right <= toggle.left + EDGE_TOLERANCE_PX,
            "the field ends at ${search.right}px and the toggle starts at ${toggle.left}px, " +
                "so the overlay covers the only way to close it: search=$search toggle=$toggle",
        )
        val fieldCentre = (search.top + search.bottom) / 2f
        val toggleCentre = (toggle.top + toggle.bottom) / 2f
        assertTrue(
            abs(fieldCentre - toggleCentre) <= EDGE_TOLERANCE_PX,
            "the field and its toggle are not on one centre line: search=$search toggle=$toggle",
        )
        assertTrue(
            search.top < sortOpen.top,
            "the field (${search.top}..${search.bottom}) is below the sort row " +
                "(${sortOpen.top}..${sortOpen.bottom}) instead of overlaying the actions row",
        )
        assertTrue(
            abs(search.height - sortOpen.height) <= EDGE_TOLERANCE_PX,
            "the compact search field is ${search.height}px tall against a ${sortOpen.height}px " +
                "sort row, so the two layouts of the same field disagree: " +
                "search=$search sort=$sortOpen",
        )
    }

    /**
     * A wide header is one row: selector, section picker, then the two icon actions.
     *
     * ## The arrangement this pins
     *
     * On a desktop the picker used to keep the phone stack, so the header spent a whole row on
     * a control that belongs beside the project it switches within. The wide branch centres it
     * in the gap between the selector and the add action, and gives the sort row the full width
     * underneath. The search toggle is deliberately *not* in this row: it sits in the sort row's
     * trailing slot, under the add action, so the field opens where the toggle already is.
     *
     * Read as relationships between rectangles, not absolute x positions, so the assertion says
     * "between" rather than a number that only holds at one window size. The viewport is forced
     * wider than the host on purpose: the Android and iOS hosts are phone-sized, and a plain
     * width would be coerced back to them and silently measure the compact header.
     */
    val theWideHeaderPutsThePickerBetweenTheSelectorAndTheActions: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(items = backlogItems(), onAddTask = {}, width = WIDE_HEADER_WIDTH)
        waitForIdle()

        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val picker = onNodeWithTag(ProjectBacklogSectionsTag).fetchSemanticsNode().boundsInRoot
        val toggle = onNodeWithTag(ProjectBacklogSearchToggleTag).fetchSemanticsNode().boundsInRoot
        val add = onNodeWithTag(BacklogAddTaskTag).fetchSemanticsNode().boundsInRoot
        val sort = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            picker.left >= switcher.right - EDGE_TOLERANCE_PX,
            "the section picker starts at ${picker.left}px and the project selector ends at " +
                "${switcher.right}px, so the picker is not on the selector's row to its right: " +
                "picker=$picker switcher=$switcher",
        )
        assertTrue(
            picker.top < switcher.bottom && switcher.top < picker.bottom,
            "the section picker (${picker.top}..${picker.bottom}) and the project selector " +
                "(${switcher.top}..${switcher.bottom}) do not overlap vertically, so they are " +
                "stacked rather than sharing a row",
        )
        assertTrue(
            add.left >= picker.right - EDGE_TOLERANCE_PX,
            "the add action is at ${add.left}px, left of the picker's right edge " +
                "${picker.right}px, so the picker is not between the selector and the add " +
                "action: picker=$picker add=$add",
        )
        val gapCentre = (switcher.right + add.left) / 2f
        val pickerCentre = (picker.left + picker.right) / 2f
        assertTrue(
            abs(pickerCentre - gapCentre) <= EDGE_TOLERANCE_PX,
            "the picker's centre is at ${pickerCentre}px where the gap between the selector " +
                "(${switcher.right}px) and the add action (${add.left}px) is centred at " +
                "${gapCentre}px, so the picker is not in the middle of that space: " +
                "picker=$picker switcher=$switcher add=$add",
        )
        assertTrue(
            toggle.top >= add.bottom - EDGE_TOLERANCE_PX,
            "the search toggle (${toggle.top}..${toggle.bottom}) is not below the add action " +
                "(ends at ${add.bottom}px), so it is still in the top row",
        )
        assertTrue(
            abs(toggle.right - add.right) <= EDGE_TOLERANCE_PX,
            "the search toggle ends at ${toggle.right}px and the add action at " +
                "${add.right}px, so the toggle is not in the trailing slot under the add " +
                "action: toggle=$toggle add=$add",
        )
        assertTrue(
            sort.top >= picker.bottom - EDGE_TOLERANCE_PX,
            "the sort row (${sort.top}..${sort.bottom}) is not below the header row " +
                "(ends at ${picker.bottom}px), so it is sharing the row instead of filling the " +
                "width underneath it",
        )
        // Full width means the toggle is the only thing it shares the row with: one gap
        // between the sort row and the trailing toggle, and nothing else.
        val rowGap = densityPixels(ttSpacing.sm)
        assertTrue(
            abs(sort.left - switcher.left) <= EDGE_TOLERANCE_PX &&
                abs(sort.right + rowGap - toggle.left) <= EDGE_TOLERANCE_PX * 2,
            "the sort row is ${sort.left}..${sort.right}px where the header row it sits under " +
                "spans ${switcher.left}..${toggle.left}px before the toggle, so it does not " +
                "fill the header's width: sort=$sort switcher=$switcher toggle=$toggle",
        )
    }

    /**
     * Opening the search beside the sort row shrinks the sort row with travel, not a jump.
     *
     * The wide header puts the field in the sort row, so the two controls share one width: the
     * field's slot grows and the sort row's `weight` gives the space back. The sample is taken
     * half way through the reveal with the clock stopped: at that point the sort row must be
     * narrower than it was closed and wider than it settles, and the field must already exist.
     * A build that resized in one frame would sit at one end, which is the defect this exists
     * for.
     */
    val theWideSearchShrinksTheSortRowAsItReveals: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(items = backlogItems(), onAddTask = {}, width = WIDE_HEADER_WIDTH)
        waitForIdle()

        val sortClosed = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot

        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        waitForIdle()

        val sortOpen = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot
        val fieldOpen = onNodeWithTag(ProjectBacklogSearchFieldTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            sortOpen.width < sortClosed.width - EDGE_TOLERANCE_PX,
            "the sort row is ${sortOpen.width}px wide with the field open against " +
                "${sortClosed.width}px closed, so the field did not take any of its width: " +
                "sort=$sortOpen field=$fieldOpen",
        )
        assertTrue(
            fieldOpen.left >= sortOpen.right - EDGE_TOLERANCE_PX,
            "the field starts at ${fieldOpen.left}px and the sort row ends at ${sortOpen.right}px, " +
                "so they are not beside each other: sort=$sortOpen field=$fieldOpen",
        )
        assertTrue(
            fieldOpen.top < sortOpen.bottom && sortOpen.top < fieldOpen.bottom,
            "the field (${fieldOpen.top}..${fieldOpen.bottom}) and the sort row " +
                "(${sortOpen.top}..${sortOpen.bottom}) do not overlap vertically, so the field " +
                "is not in the sort row",
        )

        // One height for the row's three controls, or the shorter field and the toggle read as
        // sitting on a different line from the sort row.
        val toggle = onNodeWithTag(ProjectBacklogSearchToggleTag).fetchSemanticsNode().boundsInRoot
        assertTrue(
            abs(fieldOpen.height - sortOpen.height) <= EDGE_TOLERANCE_PX,
            "the field is ${fieldOpen.height}px tall against a ${sortOpen.height}px sort row, " +
                "so the two controls do not share the row's height: field=$fieldOpen sort=$sortOpen",
        )
        val rowCentre = (sortOpen.top + sortOpen.bottom) / 2f
        val toggleCentre = (toggle.top + toggle.bottom) / 2f
        assertTrue(
            abs(toggleCentre - rowCentre) <= EDGE_TOLERANCE_PX,
            "the search toggle's centre is at ${toggleCentre}px where the row's centre is " +
                "${rowCentre}px, so the button is not centred on the field and sort row it " +
                "belongs to: toggle=$toggle row=$sortOpen",
        )

        // Close, then reopen with the clock stopped so "half way" is a real sample.
        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        waitForIdle()

        mainClock.autoAdvance = false
        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        mainClock.advanceTimeBy(SEARCH_REVEAL_MILLIS / 2L)
        mainClock.advanceTimeByFrame()

        val sortMidway = onNodeWithTag(BacklogSortControlsTag).fetchSemanticsNode().boundsInRoot
        assertTrue(
            sortMidway.width < sortClosed.width - EDGE_TOLERANCE_PX &&
                sortMidway.width > sortOpen.width + EDGE_TOLERANCE_PX,
            "half way through the reveal the sort row is ${sortMidway.width}px wide, between " +
                "${sortOpen.width}px open and ${sortClosed.width}px closed it should be strictly " +
                "between the two: the field snapped into place instead of making room with travel",
        )
        onNodeWithTag(ProjectBacklogSearchFieldTag).assertExists()

        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * Switching to Kanban or Archived keeps the open field in its row, and lets it take the
     * width the sort row was using.
     *
     * The sort row is a Backlog control, so a section switch removes it; the field is not, so it
     * must not move to another reveal. The wide header keeps one row for every section: Backlog
     * is sort + field + toggle, and Kanban/Archived is field (filling the space) + toggle.
     *
     * Asserted as: same row (top), and in Kanban the field starts at the row's leading edge and
     * ends where the toggle begins — the whole width, not the Backlog portion. That is the
     * difference between "the field stayed put" and "the field was re-laid out by another
     * parent", which is what the previous implementation did.
     *
     * The ending also opens the field from a closed Kanban header. That section has no sort
     * row, so the field's row is the only thing giving the header height; if that height is not
     * reserved, opening moves the whole list down through the measured top inset.
     */
    val theWideSearchKeepsItsSlotAcrossSections: suspend ComposeUiTest.() -> Unit = {
        // One real Kanban row: the section's empty state would pass a row-position check while
        // hiding the header-height defect this scenario also covers.
        val kanbanItem = backlogItems().first().copy(
            id = WorkItemID("00000000-0000-4000-8000-000000000099"),
            key = WorkItemKey("TT-99"),
            status = WorkItemStatus.Todo,
        )
        val items = backlogItems() + kanbanItem
        backlogShowing(
            items = items,
            onAddTask = {},
            width = WIDE_HEADER_WIDTH,
            project = ViewPreviewData.seededProject.withBoardStatuses(singleBoardStatus.first()),
        )
        waitForIdle()

        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        waitForIdle()

        val fieldBefore = onNodeWithTag(ProjectBacklogSearchFieldTag).fetchSemanticsNode().boundsInRoot
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        onNodeWithTag(BacklogSortControlsTag).assertExists()

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban))).performClick()
        waitForIdle()

        val fieldOnKanban = onNodeWithTag(ProjectBacklogSearchFieldTag).fetchSemanticsNode().boundsInRoot
        val toggle = onNodeWithTag(ProjectBacklogSearchToggleTag).fetchSemanticsNode().boundsInRoot
        onNodeWithTag(BacklogSortControlsTag).assertDoesNotExist()

        assertTrue(
            abs(fieldBefore.top - fieldOnKanban.top) <= EDGE_TOLERANCE_PX,
            "the search field moved rows on the section switch: it was at y " +
                "${fieldBefore.top} and is now at ${fieldOnKanban.top}, so it is not in the " +
                "sort row any more: before=$fieldBefore after=$fieldOnKanban",
        )
        val leadingInset = densityPixels(ttSpacing.sm)
        assertTrue(
            abs(fieldOnKanban.left - (switcher.left + leadingInset)) <= EDGE_TOLERANCE_PX * 2,
            "on Kanban the field starts at ${fieldOnKanban.left}px instead of the row's " +
                "leading edge ${switcher.left}px plus the row's own ${leadingInset}px inset, so " +
                "it is either still taking only a portion of the row or flush against the " +
                "header edge: field=$fieldOnKanban",
        )
        assertTrue(
            fieldOnKanban.width > fieldBefore.width + EDGE_TOLERANCE_PX,
            "on Kanban the field is ${fieldOnKanban.width}px wide against " +
                "${fieldBefore.width}px in Backlog, so it did not take the width the sort row " +
                "gave up: before=$fieldBefore after=$fieldOnKanban",
        )
        // The row spaces its children, so the field's right edge is one gap short of the toggle.
        val rowGap = densityPixels(ttSpacing.sm)
        assertTrue(
            abs(fieldOnKanban.right + rowGap - toggle.left) <= EDGE_TOLERANCE_PX * 2,
            "on Kanban the field ends at ${fieldOnKanban.right}px and the toggle begins at " +
                "${toggle.left}px, so the field does not fill the row up to the toggle: " +
                "field=$fieldOnKanban toggle=$toggle",
        )

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_backlog))).performClick()
        waitForIdle()

        onNodeWithTag(BacklogSortControlsTag).assertExists()
        val fieldBack = onNodeWithTag(ProjectBacklogSearchFieldTag).fetchSemanticsNode().boundsInRoot
        assertTrue(
            abs(fieldBack.left - fieldBefore.left) <= EDGE_TOLERANCE_PX &&
                abs(fieldBack.top - fieldBefore.top) <= EDGE_TOLERANCE_PX &&
                abs(fieldBack.width - fieldBefore.width) <= EDGE_TOLERANCE_PX,
            "returning to Backlog did not restore the field to the slot it left: " +
                "before=$fieldBefore after=$fieldBack",
        )

        // Kanban has no sort row, so its field row used to have zero height while closed. The
        // header is measured, so the click grew it and pushed every row down. Opening from the
        // closed state is the only way to catch that; switching sections with the field already
        // open cannot.
        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        waitForIdle()
        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban))).performClick()
        waitForIdle()
        val kanbanRowClosed = composedRow(kanbanItem).minBy { it.top }

        onNodeWithTag(ProjectBacklogSearchToggleTag).performClick()
        waitForIdle()
        val kanbanRowOpen = composedRow(kanbanItem).minBy { it.top }
        onNodeWithTag(ProjectBacklogSearchFieldTag).assertExists()
        assertTrue(
            abs(kanbanRowOpen.top - kanbanRowClosed.top) <= EDGE_TOLERANCE_PX,
            "opening search in Kanban moved the first row from ${kanbanRowClosed.top}px to " +
                "${kanbanRowOpen.top}px: the field's row must keep its height while closed",
        )
    }

    /**
     * Copying a row raises the confirmation, and it leaves on its own.
     *
     * The clock is stopped for the click so the two states can both be observed: with
     * auto-advance on, `waitForIdle` would run out the toast's two seconds before the first
     * assertion and the scenario would assert its absence twice.
     */
    val copyingARowConfirmsItOnTheBoard: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems().first()
        backlogShowing(items = listOf(item), onAddTask = null)
        waitForIdle()

        mainClock.autoAdvance = false
        onNested(BacklogCopyTag(item)).performClick()
        mainClock.advanceTimeByFrame()

        onNodeWithTag(TTToastTag).assertIsDisplayed()

        mainClock.advanceTimeBy(COPIED_TOAST_MILLIS + 1)
        mainClock.advanceTimeByFrame()
        onNodeWithTag(TTToastTag).assertDoesNotExist()

        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * At 320pt the copy control keeps its 44dp tap target by wrapping, not by shrinking.
     *
     * That is the width this placement exists for. The row's content is 264dp after the screen
     * gutter and the row's padding; the two labelled verbs' outer widths total ~223dp, so the
     * icon's 44dp plus one 6dp gap does not fit on the same line. Measured with all three forced
     * onto one row, the icon collapsed to **35dp** — under the design-system minimum. It must
     * stay a 44dp target and land either after the pair or on its own line, never overlapping.
     * Bounds rather than a screenshot, because all three of those survive being read off the
     * semantics tree, at whatever density the host runs.
     */
    val theCopyControlFitsANarrowRow: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems().first()
        backlogShowing(items = listOf(item), onAddTask = null, width = 320.dp)
        waitForIdle()

        val row = onNested(WorkItemBacklogRowTag(item)).fetchSemanticsNode().boundsInRoot
        val copy = onNested(BacklogCopyTag(item)).fetchSemanticsNode().boundsInRoot
        val put = onNested(BacklogPutOnBoardTag(item)).fetchSemanticsNode().boundsInRoot
        val file = onNested(BacklogFileAwayTag(item)).fetchSemanticsNode().boundsInRoot

        assertTrue(
            put.right <= file.left + EDGE_TOLERANCE_PX,
            "the two triage verbs should not overlap: put=$put file=$file",
        )
        assertTrue(
            copy.width >= densityPixels(44.dp) - EDGE_TOLERANCE_PX,
            "the copy control must keep its 44dp tap target at 320dp: row=$row copy=$copy " +
                "put=$put file=$file",
        )
        assertTrue(
            copy.right <= row.right + EDGE_TOLERANCE_PX,
            "the copy control must stay inside the row at 320dp: row=$row copy=$copy",
        )
        assertTrue(
            copy.top >= file.bottom - EDGE_TOLERANCE_PX ||
                copy.left >= file.right - EDGE_TOLERANCE_PX,
            "copy must sit after the triage pair or on its own line, never over it: " +
                "file=$file copy=$copy",
        )
    }

/**
 * Half a pixel of slack for a bounds comparison.
 *
 * Layout works in integers at the host's density, but the comparison is in floating-point
 * pixels; touching edges are a correct layout, so they must not be reported as an overlap.
 */
private const val EDGE_TOLERANCE_PX = 0.5f

/**
 * [dp] in the density this test is running at.
 *
 * Bounds are root pixels, so a tap target compared against a `Dp` has to be converted. A JVM
 * host at 1.0 and an Android host at 3.0 are 44 and 132 pixels for the same 44dp control;
 * hardcoding either would fail the other.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.densityPixels(dp: Dp): Float = with(density) { dp.toPx() }

/**
 * Nine untriaged items for one project.
 *
 * Copied from a seeded backlog item with distinct ids and keys, because the list keys rows by id
 * and nine copies of one id is a `LazyColumn` exception rather than a backlog. [WorkItemKey] is
 * also what [WorkItemBacklogRowTag] is built from, so distinct keys mean distinct tags, which is
 * what lets the sweep below ask about each row by name.
 */
private fun backlogItems(count: Int = 9): List<WorkItem> {
    val seed = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Backlog }
    return List(count) { index ->
        seed.copy(
            id = WorkItemID("00000000-0000-4000-8000-00000000000$index"),
            key = WorkItemKey("TT-${index + 1}"),
            title = "${seed.title} ${index + 1}",
        )
    }
}

/**
 * A sectioned backlog projection built from the given items.
 *
 * The archived partition is the caller's choice here — the section-switch scenarios that need a
 * real archive moved into the shared UI's commonTest, where the same fixture lives next to the
 * screen being asserted — so this helper takes the two piles separately instead of guessing.
 */
private fun sectionedBacklogProjection(
    backlogItems: List<WorkItem>,
    archivedItems: List<WorkItem>,
): BacklogSectionedProjection = BacklogSectionedProjection(
    project = ViewPreviewData.seededProject,
    items = backlogItems + archivedItems,
)

/**
 * [item]'s row, or nothing if the viewport never composed it.
 *
 * Every item is asked about rather than only the last one, because which rows a `LazyColumn`
 * composes is the viewport's decision and the three test hosts disagree about it. Rows that were
 * not composed answer with an empty list, which is what keeps the assertion identical on all
 * three. The row is read from the unmerged tree: it is clickable, so on the merged tree its own
 * bounds are folded into the node above it.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.composedRow(item: WorkItem): List<Rect> =
    onAllNodesWithTag(WorkItemBacklogRowTag(item), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.backlogShowing(
    items: List<WorkItem>,
    onAddTask: (() -> Unit)?,
    width: Dp? = null,
    /** Override the project so a scenario can hand the screen a catalog with board columns. */
    project: Project = ViewPreviewData.seededProject,
    /** Captures every "Put on board" commit: the item id and the column key the resolver chose. */
    onPutOnBoard: (WorkItemID, String?) -> Unit = { _, _ -> },
    /** The shell-remembered column for this project, as [ProjectBacklogScreen] reads it. */
    putOnBoardDefaultStatusKey: String? = null,
    isSelecting: Boolean = false,
    selection: Set<WorkItemID> = emptySet(),
    onStartSelecting: (() -> Unit)? = null,
    onToggleSelected: (WorkItemID) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onPutSelectionOnBoard: (String?) -> Unit = {},
    onArchiveSelection: () -> Unit = {},
    availableEpics: List<Epic> = emptyList(),
    isLoadingEpics: Boolean = false,
    onLoadEpics: () -> Unit = {},
    onLinkItemToEpic: (WorkItemID, EpicID) -> Unit = { _, _ -> },
) {
    setContent {
        Themed {
            // A fixed width is how a scenario asks the screen a question about a phone or a
            // desktop header; the host's own window is only one of those. `requiredWidth`, not
            // `width`: a preferred width is coerced back to a phone-sized host, so the wide
            // arrangement could never be rendered on the Android and iOS hosts. Only the left
            // edge has to be on screen, and every control measured starts there.
            Box(
                modifier = if (width == null) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.requiredWidth(width).fillMaxHeight()
                },
            ) {
                ProjectBacklogScreen(
                    projection = BacklogSectionedProjection(project = project, items = items),
                    isLoading = false,
                    errorMessage = null,
                    busyItemID = null,
                    onSelect = {},
                    onPutOnBoard = onPutOnBoard,
                    onFileAway = {},
                    onUnarchive = {},
                    onDismissError = {},
                    onOpenSwitcher = {},
                    projects = ViewPreviewData.pickable(),
                    onSelectProject = {},
                    onAddTask = onAddTask,
                    putOnBoardDefaultStatusKey = putOnBoardDefaultStatusKey,
                    isSelecting = isSelecting,
                    selection = selection,
                    onStartSelecting = onStartSelecting,
                    onToggleSelected = onToggleSelected,
                    onClearSelection = onClearSelection,
                    onPutSelectionOnBoard = onPutSelectionOnBoard,
                    onArchiveSelection = onArchiveSelection,
                    availableEpics = availableEpics,
                    isLoadingEpics = isLoadingEpics,
                    onLoadEpics = onLoadEpics,
                    onLinkItemToEpic = onLinkItemToEpic,
                )
            }
        }
    }

    }

    /**
     * The row's link action opens the project's epic list and the tapped epic is the one
     * committed. The sheet's title is asserted before the tap because an empty sheet would also
     * close on a row tap that had nothing to do with the epic.
     */
    val linkingAnItemOpensTheProjectsEpicsAndCommitsTheChoice: suspend ComposeUiTest.() -> Unit = {
        val project = ViewPreviewData.seededProject
        val item = backlogItems().first().copy(epicID = null)
        val epic = testEpic(listOf(project.id), title = "Ship it")
        var loaded = false
        var linkedItem: WorkItemID? = null
        var linkedEpic: EpicID? = null

        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = project,
            availableEpics = listOf(epic),
            onLoadEpics = { loaded = true },
            onLinkItemToEpic = { workItemID, epicID ->
                linkedItem = workItemID
                linkedEpic = epicID
            },
        )

        onNested(BacklogLinkEpicTag(item)).assertIsDisplayed()
        onNested(BacklogLinkEpicTag(item)).performClick()
        waitForIdle()

        assertTrue(loaded, "opening the picker must ask the host for this project's epics")
        onNodeWithTag(BacklogEpicPickerTag).assertIsDisplayed()
        onNodeWithTag(BacklogEpicPickerRowTag(epic.id.stringValue)).assertIsDisplayed()
        onNodeWithTag(BacklogEpicPickerRowTag(epic.id.stringValue)).performClick()
        waitForIdle()

        assertEquals(item.id, linkedItem)
        assertEquals(epic.id, linkedEpic)
    }

    // ---- Multi-select ----

    /**
     * The backlog in select mode, with the selection hoisted into the test.
     *
     * The screen is stateless about selection, so a scenario that wants to tick more than one row
     * has to be the state: [ProjectBacklogScreen] reports a tap, this harness records it, and the
     * recomposition is the same round trip the model does in the app. Handing the screen a fixed
     * `selection` instead would let a scenario assert the *drawing* of a tick but never the tap
     * that produces it.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.selectableBacklog(
        items: List<WorkItem>,
        project: Project = ViewPreviewData.seededProject,
        onSelect: (WorkItemID) -> Unit = {},
        isBatchRunning: Boolean = false,
        onPutOnBoard: (String?) -> Unit = {},
        onArchive: () -> Unit = {},
    ) {
        setContent {
            Themed {
                var isSelecting by remember { mutableStateOf(false) }
                var selection by remember { mutableStateOf(emptySet<WorkItemID>()) }
                Box(modifier = Modifier.fillMaxSize()) {
                    ProjectBacklogScreen(
                        projection = BacklogSectionedProjection(project = project, items = items),
                        isLoading = false,
                        errorMessage = null,
                        busyItemID = null,
                        onSelect = onSelect,
                        onPutOnBoard = { _, _ -> },
                        onFileAway = {},
                        onUnarchive = {},
                        onDismissError = {},
                        onOpenSwitcher = {},
                        projects = ViewPreviewData.pickable(),
                        onSelectProject = {},
                        onAddTask = null,
                        isSelecting = isSelecting,
                        selection = selection,
                        isBatchRunning = isBatchRunning,
                        onStartSelecting = { isSelecting = true },
                        onToggleSelected = { id ->
                            selection = if (id in selection) selection - id else selection + id
                        },
                        onClearSelection = {
                            selection = emptySet()
                            isSelecting = false
                        },
                        onPutSelectionOnBoard = onPutOnBoard,
                        onArchiveSelection = onArchive,
                    )
                }
            }
        }
    }

    /**
     * The header's Select control enters the mode and the bar names an empty selection.
     *
     * Zero selected is a state the user can land in — the mode is entered before the first tick —
     * and the count is what says so: a bar that stayed hidden until a row was ticked would leave
     * the tick boxes on screen with no explanation of how to act on them.
     */
    val theHeaderSelectEntersTheMode: suspend ComposeUiTest.() -> Unit = {
        selectableBacklog(items = backlogItems(count = 2))
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectionBarTag).assertIsDisplayed()
        onNodeWithTag(ProjectBacklogSelectionCountTag)
            .assertTextEquals(getString(Res.string.backlog_selection_count, 0))
        // The count is a live region, so ticking a row is announced rather than only drawn.
        onNodeWithTag(ProjectBacklogSelectionCountTag).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion),
        )
        // The same control is the way out: Select became Clear in place.
        onNodeWithTag(ProjectBacklogSelectTag).assertDoesNotExist()
        onNodeWithTag(ProjectBacklogSelectionClearTag).assertIsDisplayed()
    }

    /**
     * In select mode a row tap ticks instead of opening, and a second tap unticks.
     *
     * The editor is the thing this must not do: `onSelect` still opens work everywhere else on the
     * screen, so a select-mode tap that fell through to it would navigate away mid-batch.
     */
    val tappingARowInSelectModeTicksIt: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems(count = 2)
        val opened = mutableListOf<WorkItemID>()
        // Entered through the header, then ticked: the mode and the tick are separate gestures in
        // the app, and a scenario that started with `isSelecting = true` would skip the entry.
        selectableBacklog(items = items, onSelect = { opened += it })
        waitForIdle()
        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()

        val first = items.first()
        onNodeWithTag(WorkItemBacklogRowTag(first)).performClick()
        waitForIdle()
        onNodeWithTag(WorkItemBacklogRowTag(first)).assertIsSelected()
        onNodeWithTag(ProjectBacklogSelectionCountTag)
            .assertTextEquals(getString(Res.string.backlog_selection_count, 1))

        onNodeWithTag(WorkItemBacklogRowTag(first)).performClick()
        waitForIdle()
        onNodeWithTag(WorkItemBacklogRowTag(first)).assertIsNotSelected()
        assertTrue(opened.isEmpty(), "a select-mode tap must tick, never open the editor")
    }

    /**
     * A long press is the touch shortcut into the mode, seeded with the row under the finger.
     *
     * The mode has to be entered *and* that row ticked by one gesture, or the first press would
     * only replace the row's usual action with an empty checklist.
     */
    val longPressingARowEntersTheModeWithItTicked: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems(count = 1).first()
        selectableBacklog(items = listOf(item))
        waitForIdle()

        // The row's long-click *action*, not a synthetic hold: whether a press becomes a long
        // press is the gesture detector's timeout against the host's clock, and the Android host's
        // test clock does not advance it inside a touch-injection block. What this scenario owns is
        // that the row exposes the action and that it enters the mode seeded with that row.
        onNodeWithTag(WorkItemBacklogRowTag(item))
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectionBarTag).assertIsDisplayed()
        onNodeWithTag(WorkItemBacklogRowTag(item)).assertIsSelected()
        onNodeWithTag(ProjectBacklogSelectionCountTag)
            .assertTextEquals(getString(Res.string.backlog_selection_count, 1))
    }

    /**
     * The bar's Add to board commits the whole set into one lane, resolved once.
     *
     * One callback for the set, not one per row: the screen is what decides the lane, and a
     * per-row report would let a batch split across columns when the catalog changed mid-loop.
     */
    val theSelectionBarBoardsTheSetIntoOneLane: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems(count = 2)
        val boarded = mutableListOf<String?>()
        selectableBacklog(
            items = items,
            project = ViewPreviewData.seededProject.withBoardStatuses(singleBoardStatus.first()),
            onPutOnBoard = { key -> boarded += key },
        )
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()
        items.forEach { item ->
            onNodeWithTag(WorkItemBacklogRowTag(item)).performClick()
            waitForIdle()
        }
        onNodeWithTag(ProjectBacklogSelectionPutOnBoardTag).performClick()
        waitForIdle()

        assertEquals(
            listOf<String?>(singleBoardStatus.first().id),
            boarded,
            "the set boards once, into the single column the resolver can commit alone",
        )
    }

    /**
     * Archive does nothing until a row is ticked, and then acts on the selection.
     *
     * The button is refused rather than hidden so the bar does not reflow under the finger that
     * just unticked the last row; the assertion is on the callback, because a refused press must
     * not reach the model.
     */
    val theArchiveActionWaitsForATick: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems(count = 2)
        var archived = 0
        selectableBacklog(items = items, onArchive = { archived += 1 })
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()
        onNodeWithTag(ProjectBacklogSelectionArchiveTag).performClick()
        waitForIdle()
        assertEquals(0, archived, "zero selected must not archive anything")

        onNodeWithTag(WorkItemBacklogRowTag(items.first())).performClick()
        waitForIdle()
        onNodeWithTag(ProjectBacklogSelectionArchiveTag).performClick()
        waitForIdle()
        assertEquals(1, archived)
    }

    /**
     * Several columns and no remembered default: the selection asks for one lane.
     *
     * The batch cannot commit on its own here, and the sheet has to say that it is asking about a
     * set rather than naming one task. The chosen lane is then the one the whole set reports.
     */
    val theSelectionWithSeveralColumnsAsksForOneLane: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems(count = 2)
        val boarded = mutableListOf<String?>()
        selectableBacklog(
            items = items,
            project = ViewPreviewData.seededProject.withBoardStatuses(*pairedBoardStatuses.toTypedArray()),
            onPutOnBoard = { key -> boarded += key },
        )
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()
        items.forEach { item ->
            onNodeWithTag(WorkItemBacklogRowTag(item)).performClick()
            waitForIdle()
        }
        onNodeWithTag(ProjectBacklogSelectionPutOnBoardTag).performClick()
        waitForIdle()

        onNodeWithTag(PutOnBoardSheetTag).assertIsDisplayed()
        onNodeWithText(
            getString(Res.string.backlog_put_on_board_prompt_body_many, items.size),
        ).assertIsDisplayed()
        assertTrue(boarded.isEmpty(), "the set must not move before a lane is chosen")

        onNodeWithTag(PutOnBoardStatusRowTag(pairedBoardStatuses[1])).performClick()
        waitForIdle()

        assertEquals(listOf<String?>(pairedBoardStatuses[1].id), boarded)
        onNodeWithTag(PutOnBoardSheetTag).assertDoesNotExist()
    }

    /**
     * While a batch runs the bar says so and refuses both actions.
     *
     * A second press during the first batch would queue the same items twice, and the buttons keep
     * their labels while disabled so the bar never reflows under the finger that started it.
     */
    val theBarRefusesActionsAndNamesTheWorkInProgress: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems(count = 1)
        var boards = 0
        var archives = 0
        selectableBacklog(
            items = items,
            isBatchRunning = true,
            onPutOnBoard = { boards += 1 },
            onArchive = { archives += 1 },
        )
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()
        onNodeWithTag(WorkItemBacklogRowTag(items.first())).performClick()
        waitForIdle()

        onNodeWithText(getString(Res.string.backlog_selection_working)).assertIsDisplayed()
        onNodeWithTag(ProjectBacklogSelectionPutOnBoardTag).performClick()
        onNodeWithTag(ProjectBacklogSelectionArchiveTag).performClick()
        waitForIdle()

        assertEquals(0, boards)
        assertEquals(0, archives)
    }

    /**
     * Leaving the Backlog section ends the mode; Archived rows are never selection targets.
     */
    val leavingTheBacklogSectionEndsSelection: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems(count = 2)
        selectableBacklog(items = items)
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectTag).performClick()
        waitForIdle()
        onNodeWithTag(WorkItemBacklogRowTag(items.first())).performClick()
        waitForIdle()

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban))).performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogSelectionBarTag).assertDoesNotExist()
        onNodeWithTag(ProjectBacklogSelectionClearTag).assertDoesNotExist()
        onNodeWithTag(ProjectBacklogSelectTag).assertDoesNotExist()
    }

    // ---- "Put on board" column resolver ----

    /**
     * One board column: the resolver boards into it on the tap, with no picker.
     *
     * The screen only opens the column sheet when it cannot decide alone, and a single lane leaves
     * it nothing to ask — so the commit carries that lane's key and the sheet never appears.
     */
    val puttingOnBoardWithASingleColumnBoardsDirectly: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems().first()
        val boarded = mutableListOf<Pair<WorkItemID, String?>>()
        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = ViewPreviewData.seededProject.withBoardStatuses(singleBoardStatus.first()),
            onPutOnBoard = { id, key -> boarded.add(id to key) },
        )
        waitForIdle()
        onNested(BacklogPutOnBoardTag(item)).performClick()
        waitForIdle()
        assertEquals(1, boarded.size, "a single column should board without a prompt")
        assertEquals(item.id, boarded.first().first)
        assertEquals(singleBoardStatus.first().id, boarded.first().second)
        onNodeWithTag(PutOnBoardSheetTag).assertDoesNotExist()
    }

    /**
     * Several columns and no remembered default: the tap opens the picker instead of boarding.
     *
     * The resolver has more than one lane and no stored answer, so it defers — the commit must not
     * fire until a column is chosen, which is what keeps a tap from silently dropping the task into
     * whichever lane the model would have guessed.
     */
    val puttingOnBoardWithSeveralColumnsOpensThePicker: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems().first()
        val boarded = mutableListOf<Pair<WorkItemID, String?>>()
        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = ViewPreviewData.seededProject.withBoardStatuses(*pairedBoardStatuses.toTypedArray()),
            onPutOnBoard = { id, key -> boarded.add(id to key) },
        )
        waitForIdle()
        onNested(BacklogPutOnBoardTag(item)).performClick()
        waitForIdle()
        onNodeWithTag(PutOnBoardSheetTag).assertIsDisplayed()
        assertEquals(0, boarded.size, "the picker should intercept the tap until a column is chosen")
    }

    /**
     * Picking a column in the sheet boards into it and dismisses the sheet.
     *
     * A single tap on a lane is the commit — there is no separate confirm — so the choice both
     * moves the task and reports the column back so the next boarding in this project skips the
     * prompt. The sheet must be gone afterwards, or the picker would linger over the list.
     */
    val choosingAColumnInThePickerBoardsIntoIt: suspend ComposeUiTest.() -> Unit = {
        val chosen = pairedBoardStatuses[1]
        val item = backlogItems().first()
        val boarded = mutableListOf<Pair<WorkItemID, String?>>()
        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = ViewPreviewData.seededProject.withBoardStatuses(*pairedBoardStatuses.toTypedArray()),
            onPutOnBoard = { id, key -> boarded.add(id to key) },
        )
        waitForIdle()
        onNested(BacklogPutOnBoardTag(item)).performClick()
        waitForIdle()
        onNodeWithTag(PutOnBoardStatusRowTag(chosen)).performClick()
        waitForIdle()
        assertEquals(1, boarded.size, "choosing a column should commit the board move")
        assertEquals(item.id, boarded.first().first)
        assertEquals(chosen.id, boarded.first().second)
        onNodeWithTag(PutOnBoardSheetTag).assertDoesNotExist()
    }

    /**
     * A remembered default the catalog still has boards directly, no picker.
     *
     * The shell hands the screen the last column the user picked for this project; because that key
     * still names a real lane, the resolver treats it as the answer and boards on the tap — the
     * picker would be redundant for a preference the user has already expressed.
     */
    val aRememberedDefaultTheProjectStillHasBoardsDirectly: suspend ComposeUiTest.() -> Unit = {
        val remembered = pairedBoardStatuses[1]
        val item = backlogItems().first()
        val boarded = mutableListOf<Pair<WorkItemID, String?>>()
        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = ViewPreviewData.seededProject.withBoardStatuses(*pairedBoardStatuses.toTypedArray()),
            onPutOnBoard = { id, key -> boarded.add(id to key) },
            putOnBoardDefaultStatusKey = remembered.id,
        )
        waitForIdle()
        onNested(BacklogPutOnBoardTag(item)).performClick()
        waitForIdle()
        assertEquals(1, boarded.size, "a remembered default should board without a prompt")
        assertEquals(remembered.id, boarded.first().second)
        onNodeWithTag(PutOnBoardSheetTag).assertDoesNotExist()
    }

    /**
     * A remembered default the catalog no longer contains is ignored and the picker opens.
     *
     * If the project dropped or renamed the stored lane, the key names nothing on the board, and the
     * resolver must not board into a ghost column — it falls back to asking, which is the safe read of
     * a preference that no longer describes this project.
     */
    val aRememberedDefaultTheCatalogDroppedOpensThePicker: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems().first()
        val boarded = mutableListOf<Pair<WorkItemID, String?>>()
        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = ViewPreviewData.seededProject.withBoardStatuses(*pairedBoardStatuses.toTypedArray()),
            onPutOnBoard = { id, key -> boarded.add(id to key) },
            putOnBoardDefaultStatusKey = "col-gone",
        )
        waitForIdle()
        onNested(BacklogPutOnBoardTag(item)).performClick()
        waitForIdle()
        onNodeWithTag(PutOnBoardSheetTag).assertIsDisplayed()
        assertEquals(0, boarded.size, "a stale default must not board into a column that no longer exists")
    }

    /**
     * A project with no catalog (no `statuses`) falls back to the legacy `Todo` lane, no picker.
     *
     * `boardStatuses` is empty without a catalog, so the resolver has exactly one thing to do and no
     * column to ask about; the commit carries a null key, which the model maps onto `Todo`.
     */
    val puttingOnBoardWithoutACatalogBoardsToLegacyTodo: suspend ComposeUiTest.() -> Unit = {
        val item = backlogItems().first()
        val boarded = mutableListOf<Pair<WorkItemID, String?>>()
        backlogShowing(
            items = listOf(item),
            onAddTask = null,
            project = ViewPreviewData.seededProject.withBoardStatuses(),
            onPutOnBoard = { id, key -> boarded.add(id to key) },
        )
        waitForIdle()
        onNested(BacklogPutOnBoardTag(item)).performClick()
        waitForIdle()
        assertEquals(1, boarded.size, "a catalog-free project should board without a prompt")
        assertEquals(item.id, boarded.first().first)
        assertEquals(null, boarded.first().second)
        onNodeWithTag(PutOnBoardSheetTag).assertDoesNotExist()
    }

    /**
     * On wide viewports (e.g. 1400dp), the backlog list container and rows are clamped
     * to BacklogMaxContentWidth (960dp) and horizontally centered, rather than stretching infinitely.
     */
    val wideLayoutClampsContentWidthAndCenters: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems()
        backlogShowing(items = items, onAddTask = {}, width = 1400.dp)
        waitForIdle()

        val screenNode = onNodeWithTag(ProjectBacklogScreenTag).fetchSemanticsNode()
        val listNode = onNodeWithTag(ProjectBacklogListTag).fetchSemanticsNode()
        val rowNode = onNested(WorkItemBacklogRowTag(items.first())).fetchSemanticsNode()

        val maxAllowedPx = densityPixels(BacklogMaxContentWidth) + EDGE_TOLERANCE_PX
        assertTrue(
            listNode.size.width <= maxAllowedPx,
            "list width should be clamped to at most $maxAllowedPx: actual=${listNode.size.width}",
        )
        assertTrue(
            rowNode.size.width <= maxAllowedPx,
            "row width should be clamped to at most $maxAllowedPx: actual=${rowNode.size.width}",
        )

        // Centred against the screen rather than the host window: the scenario asks for a
        // 1400dp screen, which can be wider than the test host, so the host itself may be
        // narrower than the screen under test.
        val expectedLeft = screenNode.boundsInRoot.left +
            (screenNode.size.width - listNode.size.width) / 2f
        assertEquals(
            expectedLeft,
            listNode.boundsInRoot.left,
            2f,
            "list should be horizontally centered on wide viewports",
        )
    }

    /**
     * The floating header is capped with the list, so its leading and trailing controls sit on the
     * rows' edges rather than drifting out to the window's.
     *
     * Both edges are read against the list's own rectangle: the header's outer inset plus its inner
     * padding is the list's `xxl` gutter, so a cap applied to only one of the two would show up
     * here as a horizontal offset between the selector and the rows.
     */
    val wideLayoutHeaderAlignsWithListContent: suspend ComposeUiTest.() -> Unit = {
        val items = backlogItems()
        backlogShowing(items = items, onAddTask = {}, width = 1400.dp)
        waitForIdle()

        val list = onNodeWithTag(ProjectBacklogListTag).fetchSemanticsNode().boundsInRoot
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val add = onNodeWithTag(BacklogAddTaskTag).fetchSemanticsNode().boundsInRoot

        assertEquals(
            list.left,
            switcher.left,
            2f,
            "the header's leading control should share the list's left edge: " +
                "list=$list switcher=$switcher",
        )
        assertEquals(
            list.right,
            add.right,
            2f,
            "the header's trailing action should share the list's right edge: " +
                "list=$list add=$add",
        )
    }
}

/**
 * A project whose catalog lists the given [statuses], copied from [ViewPreviewData.seededProject]
 * so every other field (tags, prefix, history) is unchanged and only the board columns differ.
 *
 * [Project.boardStatuses] is derived from `statuses` filtered by `category.appearsOnBoard`, so a
 * scenario passes only the lanes it wants the resolver to see — one entry boards directly, several
 * open the picker.
 */
private fun Project.withBoardStatuses(vararg statuses: StatusDefinition): Project =
    copy(statuses = statuses.toList())

/** A header width under `BacklogHeaderWideMinimumWidth`, so the compact stack is in force. */
private val COMPACT_HEADER_WIDTH = 400.dp

/**
 * A header width over `BacklogHeaderWideMinimumWidth`.
 *
 * Imposed with `requiredWidth` by [backlogShowing], because the host windows are phone-sized
 * and a preferred width would be coerced back down — the wide arrangement would never be
 * rendered and the scenario would pass against a compact header it never asked for.
 */
private val WIDE_HEADER_WIDTH = 900.dp

/** Two board columns a resolver test can hand a project: a single Todo lane, and a Todo + doing pair. */
private val singleBoardStatus = listOf(
    StatusDefinition(id = "col-todo", name = "To Do", category = WorkItemStatus.Todo, order = 0),
)
private val pairedBoardStatuses = listOf(
    StatusDefinition(id = "col-todo", name = "To Do", category = WorkItemStatus.Todo, order = 0),
    StatusDefinition(id = "col-doing", name = "Doing", category = WorkItemStatus.InProgress, order = 1),
)
