package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.TTSearchFieldClearTag
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_section_archived
import com.tasktracker.resources.backlog_section_kanban
import org.jetbrains.compose.resources.getString

/**
 * Typing in the board's and the backlog's search field, as scenarios.
 *
 * ## The clock is advanced by hand, on purpose
 *
 * The list settles [SEARCH_DEBOUNCE_MILLIS] after the last keystroke, so every scenario here
 * drives `mainClock` rather than sleeping. A wall-clock sleep passes on a fast machine and
 * flakes on a loaded runner, and the one claim these scenarios exist for — that the list does
 * *not* change before the window elapses — cannot be written with a sleep at all, because a
 * sleep long enough to be reliable is long enough to step over the thing being asserted.
 *
 * ## Why the lane's count is the assertion
 *
 * Lane headings carry their own count, and that number is derived from the same filtered list
 * the cards are. So "the board narrowed" is one comparison on a number the user can also read,
 * rather than a sweep of card titles that depends on which cards a horizontally scrolling row
 * happened to compose on this host.
 */
@OptIn(ExperimentalTestApi::class)
object WorkItemSearchScenarios {

    /** One key typed into the board narrows the lanes to the card it names. */
    val theBoardNarrowsToTheMatchingCard: suspend ComposeUiTest.() -> Unit = {
        boardShowing()
        assertText(BoardColumnCountTag(WorkItemStatus.InProgress), "2", useUnmerged = true)

        typeInBoardSearch("TT-3")

        assertText(
            BoardColumnCountTag(WorkItemStatus.InProgress),
            "1",
            useUnmerged = true,
        )
        assertText(BoardColumnCountTag(WorkItemStatus.Todo), "0", useUnmerged = true)
    }

    /**
     * Every lane survives a search, including the ones the query emptied.
     *
     * The lanes are the board's shape, and a drag target exists because the lane exists. A
     * filter that dropped an empty lane would also drop the ability to drag anything into it.
     */
    val theBoardKeepsEveryLaneWhileSearching: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        typeInBoardSearch("TT-3")

        WorkItemStatus.boardOrder.forEach { status ->
            onNodeWithTag(BoardColumnTag(status)).assertExists()
        }
    }

    /**
     * The list does not move until the debounce elapses.
     *
     * Two readings of the same number, 100ms and 300ms after the keystroke: the first is what a
     * filter applied per keystroke fails, the second is what a filter that never applies fails.
     * Neither alone proves a debounce — only the pair does.
     */
    val theBoardWaitsForTheDebounce: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        mainClock.autoAdvance = false
        onNodeWithTag(ProjectBoardSearchInputTag).performTextInput("TT-3")
        mainClock.advanceTimeBy(DEBOUNCE_FIRST_CHECK_MILLIS)

        // Still the unfiltered board: the query has not been still for the window yet.
        assertText(BoardColumnCountTag(WorkItemStatus.InProgress), "2", useUnmerged = true)

        mainClock.advanceTimeBy(DEBOUNCE_MILLIS)
        mainClock.advanceTimeByFrame()

        assertText(BoardColumnCountTag(WorkItemStatus.InProgress), "1", useUnmerged = true)

        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * A query that matches nothing replaces the lanes with the no-results state.
     *
     * Six empty lanes would say "this board is empty", which is a different and false claim — and
     * the clear control has to bring the lanes back, or the state is a dead end.
     */
    val theBoardShowsTheNoResultsState: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        typeInBoardSearch("billing")

        onNodeWithTag(ProjectBoardEmptySearchTag).assertIsDisplayed()
        assertTagCount(ProjectBoardColumnsTag, 0)

        onNodeWithTag(TTSearchFieldClearTag).performClick()
        waitForIdle()

        onNodeWithTag(ProjectBoardColumnsTag).assertIsDisplayed()
        assertTagCount(ProjectBoardEmptySearchTag, 0)
    }

    /**
     * Clearing restores the list on the next frame, not after the debounce.
     *
     * The clock is stopped and deliberately *not* advanced past the window, so a clear routed
     * through the same delay would still be showing the narrowed board here.
     */
    val clearingTheSearchRestoresTheListAtOnce: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        mainClock.autoAdvance = false
        onNodeWithTag(ProjectBoardSearchInputTag).performTextInput("TT-3")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()

        onNodeWithTag(TTSearchFieldClearTag).performClick()
        mainClock.advanceTimeByFrame()

        // The full lane, inside the window the typing would still have been waiting out.
        assertText(BoardColumnCountTag(WorkItemStatus.InProgress), "2", useUnmerged = true)

        mainClock.autoAdvance = true
        waitForIdle()
    }

    /** The backlog narrows the segment it is showing. */
    val theBacklogNarrowsTheSelectedSegment: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(projection = twoUntriagedBacklog)

        onNested(WorkItemBacklogRowTag(untriagedItem)).assertIsDisplayed()
        onNested(WorkItemBacklogRowTag(otherUntriagedItem)).assertIsDisplayed()

        typeInBacklogSearch("offline")

        onNested(WorkItemBacklogRowTag(untriagedItem)).assertIsDisplayed()
        assertTagCount(
            WorkItemBacklogRowTag(otherUntriagedItem),
            0,
            useUnmerged = true,
        )
        assertTagCount(ProjectBacklogEmptySearchTag, 0)
    }

    /**
     * A query that matches nothing in the active segment shows the search state, not the
     * segment's own "empty" copy.
     *
     * "The backlog is empty" is a claim about the project, and it is false while a query is
     * narrowing it. The two states have to be distinguishable, or the user is told their backlog
     * is empty while it is full.
     */
    val aSegmentWithNoMatchesSaysSo: suspend ComposeUiTest.() -> Unit = {
        backlogShowing()

        typeInBacklogSearch("billing")

        onNodeWithTag(ProjectBacklogEmptySearchTag).assertIsDisplayed()
        assertTagCount(ProjectBacklogEmptyBacklogTag, 0)
    }

    /**
     * Switching segments keeps the query, and each segment shows its own matches.
     *
     * The query is screen state like the selected segment, so a tab switch is a change of *view*
     * of the same narrowed pile rather than a reset. `TT-5` is a `Done` task, so it is both on
     * the board and in the archive — the one key that can be found from two segments, which is
     * what makes this a test of the query surviving rather than of one list happening to contain
     * something.
     */
    val switchingSegmentsKeepsTheQuery: suspend ComposeUiTest.() -> Unit = {
        backlogShowing()

        val kanban = getString(Res.string.backlog_section_kanban)
        val archived = getString(Res.string.backlog_section_archived)

        typeInBacklogSearch("TT-5")

        onNodeWithTag(SectionIndicatorTag(kanban)).performClick()
        waitForIdle()
        onNested(WorkItemBacklogRowTag(doneItem)).assertIsDisplayed()

        onNodeWithTag(SectionIndicatorTag(archived)).performClick()
        waitForIdle()
        onNested(WorkItemBacklogRowTag(doneItem)).assertIsDisplayed()

        // And the query is still in the field, so a further edit narrows from what is shown
        // rather than from an empty box. Read as editable text, because a field's value is not
        // `Text` in the semantics tree.
        onNodeWithTag(ProjectBacklogSearchInputTag).assertTextEquals("TT-5")
    }
}

/** Past the debounce window with a margin, without being a sleep. */
private const val DEBOUNCE_MILLIS = 200L

/** The first checkpoint: well inside the window, so nothing should have been applied yet. */
private const val DEBOUNCE_FIRST_CHECK_MILLIS = 100L

/** `TT-1`, "Fix the offline queue" — untriaged, so the backlog segment lists it. */
private val untriagedItem = ViewPreviewData.workItems.first { it.key.rawValue == "TT-1" }

/**
 * A second untriaged task, because the seeded project has exactly one.
 *
 * The seeded pile has a single `Backlog` item, so "the list narrowed" would be indistinguishable
 * from "the list is unchanged" without a second row to disappear. Copied from the first rather
 * than invented so the row's content is the fixture every other scenario renders.
 */
private val otherUntriagedItem = untriagedItem.copy(
    id = WorkItemID("00000000-0000-4000-8000-000000000090"),
    key = WorkItemKey("TT-90"),
    title = "Publish the release notes",
)

/** The seeded project's work plus that second untriaged row, for the narrowing scenario. */
private val twoUntriagedBacklog = BacklogSectionedProjection(
    project = ViewPreviewData.seededProject,
    items = ViewPreviewData.workItems.filter { it.projectID == ViewPreviewData.seededProject.id } +
        otherUntriagedItem,
)

/** `TT-5` — a finished task, so it is in the done lane and in the archive both. */
private val doneItem = ViewPreviewData.workItems.first { it.key.rawValue == "TT-5" }

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardShowing(
    projection: BoardProjection = ViewPreviewData.boardProjection(),
) {
    setContent {
        Themed {
            ProjectBoardScreen(
                projection = projection,
                isLoading = false,
                errorMessage = null,
                movingItemID = null,
                onSelect = {},
                onFileAway = {},
                onMove = { _, _ -> },
                onDismissError = {},
            )
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.backlogShowing(
    projection: BacklogSectionedProjection = ViewPreviewData.backlogSectionedProjection(),
) {
    setContent {
        Themed {
            ProjectBacklogScreen(
                projection = projection,
                isLoading = false,
                errorMessage = null,
                busyItemID = null,
                onSelect = {},
                onPutOnBoard = {},
                onFileAway = {},
                onUnarchive = {},
                onDismissError = {},
            )
        }
    }
}

/**
 * Type, then let the window pass.
 *
 * The field updates on the keystroke; the list is what waits, so every scenario that asserts a
 * *narrowed* list goes through here.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.typeInBoardSearch(text: String) {
    onNodeWithTag(ProjectBoardSearchInputTag).performTextInput(text)
    settleDebounce()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.typeInBacklogSearch(text: String) {
    onNodeWithTag(ProjectBacklogSearchInputTag).performTextInput(text)
    settleDebounce()
}

/** Past the debounce window, plus a frame so the recomposition that applies it has run. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.settleDebounce() {
    mainClock.advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + DEBOUNCE_MILLIS)
    mainClock.advanceTimeByFrame()
    waitForIdle()
}
