package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_move_prompt_body
import com.tasktracker.resources.board_move_prompt_title
import com.tasktracker.resources.board_selection_count
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A built-in status as the board names it when the project has no catalog. */
private fun lane(status: WorkItemStatus): StatusDefinition =
    StatusDefinition(id = status.wireValue, name = "", category = status)

private fun BoardProjection.column(status: WorkItemStatus): BoardColumn =
    columns.single { it.status.category == status }

/**
 * The board's lane-scoped selection, as scenarios.
 *
 * The selection is stateless in the screen, so every scenario here is the model: a tap reports an
 * id, this harness records it, and the same round trip recomposes. That is what lets the
 * assertions be about `selected` semantics and the bar's count rather than about a private flag.
 *
 * Two gestures are deliberately absent. A card long-press already picks the card up for drag, so
 * selection starts from the lane header; and a tap in another lane ticks nothing, because a
 * selection that spans lanes is one Archive button with two meanings.
 */
@OptIn(ExperimentalTestApi::class)
object BoardSelectionScenarios {

    /**
     * The lane header's Select starts a selection in that lane, and only that lane.
     *
     * The other lanes lose their Select control while the mode is on: pressing it would either be
     * ignored or silently move the set, and a control that can do neither should not be drawn.
     */
    val theLaneHeaderStartsItsOwnSelection: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val done = lane(WorkItemStatus.Done)
        val todo = lane(WorkItemStatus.Todo)
        boardWithSelection(projection)
        waitForIdle()

        onNodeWithTag(BoardColumnSelectTag(done)).selectControl()
        waitForIdle()

        onNodeWithTag(ProjectBoardSelectionBarTag).assertIsDisplayed()
        onNodeWithTag(ProjectBoardSelectionCountTag)
            .assertTextEquals(getString(Res.string.board_selection_count, 0))
        onNodeWithTag(BoardColumnSelectionClearTag(done)).assertExists()
        assertEquals(
            0,
            countOfTag(BoardColumnSelectTag(todo)),
            "another lane must not offer a Select while one lane's set is open",
        )
    }

    /**
     * A card tap ticks it and a second tap unticks it, without opening the editor.
     *
     * The card is a checkbox in this mode, which is what `assertIsSelected` reads: a selected
     * *state*, not just a drawn box, is what assistive technology announces.
     */
    val tappingACardTicksAndUnticksIt: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val done = lane(WorkItemStatus.Done)
        val card = projection.column(WorkItemStatus.Done).items.first()
        boardWithSelection(projection)
        waitForIdle()

        onNodeWithTag(BoardColumnSelectTag(done)).selectControl()
        waitForIdle()
        onNodeWithTag(WorkItemCardTag(card)).selectControl()
        waitForIdle()

        onNodeWithTag(WorkItemCardTag(card)).assertIsSelected()
        onNodeWithTag(ProjectBoardSelectionCountTag)
            .assertTextEquals(getString(Res.string.board_selection_count, 1))

        onNodeWithTag(WorkItemCardTag(card)).selectControl()
        waitForIdle()
        onNodeWithTag(WorkItemCardTag(card)).assertIsNotSelected()
    }

    /**
     * Select all ticks exactly the cards the lane is drawing.
     *
     * The lane's header only sees the column it was given, which the screen has already filtered
     * by the search query; the model intersects that with its own sprint and My Tasks filters. So
     * the assertion counts the lane's cards and no others.
     */
    val selectAllTakesTheLanesCards: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val done = lane(WorkItemStatus.Done)
        val cards = projection.column(WorkItemStatus.Done).items
        assertTrue(cards.size > 1, "the fixture needs more than one done card for this to mean anything")
        boardWithSelection(projection)
        waitForIdle()

        onNodeWithTag(BoardColumnSelectTag(done)).selectControl()
        waitForIdle()
        onNodeWithTag(BoardColumnSelectAllTag(done)).selectControl()
        waitForIdle()

        onNodeWithTag(ProjectBoardSelectionCountTag)
            .assertTextEquals(getString(Res.string.board_selection_count, cards.size))
        cards.forEach { card -> onNodeWithTag(WorkItemCardTag(card)).assertIsSelected() }
    }

    /** The bar archives the set, and clearing ends the mode. */
    val theBarArchivesTheSelectionAndClearEndsTheMode: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val done = lane(WorkItemStatus.Done)
        val card = projection.column(WorkItemStatus.Done).items.first()
        var archived = 0
        boardWithSelection(projection, onArchive = { archived += 1 })
        waitForIdle()

        onNodeWithTag(BoardColumnSelectTag(done)).selectControl()
        waitForIdle()
        onNodeWithTag(ProjectBoardSelectionArchiveTag).selectControl()
        waitForIdle()
        assertEquals(0, archived, "zero selected must not archive anything")

        onNodeWithTag(WorkItemCardTag(card)).selectControl()
        waitForIdle()
        onNodeWithTag(ProjectBoardSelectionArchiveTag).selectControl()
        waitForIdle()
        assertEquals(1, archived)

        onNodeWithTag(ProjectBoardSelectionClearTag).selectControl()
        waitForIdle()
        onNodeWithTag(ProjectBoardSelectionBarTag).assertDoesNotExist()
        // Outside the mode the card is a card again, not a checkbox: the tick is gone entirely.
        onNodeWithTag(WorkItemCardSelectionTickTag).assertDoesNotExist()
    }

    /**
     * "Move to" asks for a destination lane and reports the one chosen.
     *
     * The lane the set already lives in is not a destination: moving a card to where it already is
     * is a write that changes nothing, so offering it would be a button that lies.
     */
    val moveAsksForALaneAndReportsIt: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val done = lane(WorkItemStatus.Done)
        val todo = lane(WorkItemStatus.Todo)
        val cards = projection.column(WorkItemStatus.Done).items
        val moved = mutableListOf<String>()
        boardWithSelection(projection, onMove = { moved += it })
        waitForIdle()

        onNodeWithTag(BoardColumnSelectTag(done)).selectControl()
        waitForIdle()
        onNodeWithTag(BoardColumnSelectAllTag(done)).selectControl()
        waitForIdle()
        onNodeWithTag(ProjectBoardSelectionMoveTag).selectControl()
        waitForIdle()

        onNodeWithTag(PutOnBoardSheetTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.board_move_prompt_title)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.board_move_prompt_body, cards.size)).assertIsDisplayed()
        assertEquals(
            0,
            countOfTag(PutOnBoardStatusRowTag(done)),
            "the selection's own lane is not a destination",
        )

        onNodeWithTag(PutOnBoardStatusRowTag(todo)).performClick()
        waitForIdle()

        assertEquals(listOf(todo.id), moved)
        onNodeWithTag(PutOnBoardSheetTag).assertDoesNotExist()
    }
}

/**
 * The board with a hoisted selection, wired the way the model does it.
 *
 * [initialSelection] starts the mode directly from a lane; the empty default starts it from the
 * header, which is the path a user takes and the one the scenarios above exercise.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardWithSelection(
    projection: BoardProjection,
    laneID: String? = null,
    initialSelection: Set<WorkItemID> = emptySet(),
    onMove: (String) -> Unit = {},
    onArchive: () -> Unit = {},
) {
    setContent {
        Themed {
            var isSelecting by remember { mutableStateOf(initialSelection.isNotEmpty()) }
            var selectionLane by remember { mutableStateOf(if (initialSelection.isNotEmpty()) laneID else null) }
            var selection by remember { mutableStateOf(initialSelection) }
            Box(modifier = Modifier.fillMaxSize()) {
                ProjectBoardScreen(
                    projection = projection,
                    isLoading = false,
                    errorMessage = null,
                    movingItemID = null,
                    onSelect = {},
                    onFileAway = {},
                    onMove = { _, _ -> },
                    onDismissError = {},
                    isSelecting = isSelecting,
                    selectionLane = selectionLane,
                    selection = selection,
                    onStartSelecting = { lane ->
                        selectionLane = lane
                        isSelecting = true
                    },
                    onToggleSelected = { _, id ->
                        selection = if (id in selection) selection - id else selection + id
                    },
                    onSelectAll = { _, ids -> selection = ids.toSet() },
                    onClearSelection = {
                        selection = emptySet()
                        isSelecting = false
                        selectionLane = null
                    },
                    onMoveSelection = onMove,
                    onArchiveSelection = onArchive,
                )
            }
        }
    }
}

/**
 * Invokes a control's click action through semantics rather than a pointer.
 *
 * The board row scrolls horizontally, so the Done lane — the one this feature exists for — is
 * off the test viewport on a phone-sized host, and a pointer click at its centre lands outside the
 * root and hits nothing. The semantics action is the same click the layout would deliver, without
 * depending on where the lane currently sits in the scroll.
 */
@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.SemanticsNodeInteraction.selectControl() {
    performSemanticsAction(SemanticsActions.OnClick) { it() }
}
