package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import androidx.compose.ui.unit.dp
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_empty_column
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a board column behaves, as scenarios.
 *
 * ## Every lane draws, always
 *
 * This is the load-bearing rule of the whole screen. A lane that only appears once it holds a
 * card means the *first* card of a new column has nowhere to be dropped, and a board that
 * grows and shrinks under the user's hand is much harder to read than one that does not move.
 * So the scenarios below assert that all four lanes are present with an empty projection -
 * not that empty lanes are hidden, and not that they are merely "handled".
 *
 * `WorkItemStatus.boardOrder` is four lanes - `Backlog` and `Closed` are absent because
 * `appearsOnBoard` says so - and the test asserts the *count* as well as each tag, so a
 * status that quietly started appearing on the board would fail rather than pass by adding a
 * fifth column nobody looked at.
 *
 * ## The count is the column's, and it is not a badge
 *
 * One number, summarising the lane, in the heading. Asserted as text because that is all a
 * user can see.
 */
@OptIn(ExperimentalTestApi::class)
object BoardColumnScenarios {

    /** All four lanes are drawn, with nothing in them, and each says it is empty. */
    val everyLaneIsDrawnEvenWithNothingInIt: suspend ComposeUiTest.() -> Unit = {
        boardWith(emptyMap())

        assertEquals(4, WorkItemStatus.boardOrder.size, "four lanes, from appearsOnBoard")
        WorkItemStatus.boardOrder.forEach { status ->
            onNodeWithTag(BoardColumnTag(status)).assertIsDisplayed()
            // Addressed per lane: all four empty lanes say the same sentence, so the text
            // on its own matches four nodes and `onNodeWithText` would reject it.
            assertText(BoardColumnEmptyTag(status), getString(Res.string.board_empty_column))
        }
        assertTagCount(BoardColumnEmptyTag(WorkItemStatus.Todo), expected = 1)
    }

    /** The lanes are exactly the board order, in it. */
    val theLanesAreTheBoardOrderAndNoOthers: suspend ComposeUiTest.() -> Unit = {
        boardWith(emptyMap())

        assertEquals(
            listOf(WorkItemStatus.Todo, WorkItemStatus.InProgress, WorkItemStatus.InReview, WorkItemStatus.Done),
            WorkItemStatus.boardOrder,
        )
        assertTrue(
            WorkItemStatus.boardOrder.none { it == WorkItemStatus.Backlog },
            "the backlog is not a lane; it has its own screen",
        )
        assertTrue(
            WorkItemStatus.boardOrder.none { it == WorkItemStatus.Closed },
            "a filed-away task is in the archive, not on the board",
        )
    }

    /** The heading carries the lane's count, and it is the lane's own. */
    val theHeadingCarriesTheLanesOwnCount: suspend ComposeUiTest.() -> Unit = {
        val inProgress = ViewPreviewData.workItems.filter { it.status == WorkItemStatus.InProgress }
        boardWith(mapOf(WorkItemStatus.InProgress to inProgress, WorkItemStatus.Todo to emptyList()))

        // The count is read off the node that carries it. Searching for the number instead
        // found three matches, because a board full of lanes has a `0` and a `2` in several
        // places at once - the number is not a unique handle, the lane is.
        assertText(BoardColumnCountTag(WorkItemStatus.InProgress), inProgress.size.toString())
        assertText(BoardColumnCountTag(WorkItemStatus.Todo), "0")
    }

    /** A card that is in the lane is in that lane. */
    val aCardAppearsInTheLaneMatchingItsStatus: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Todo }
        boardWith(mapOf(WorkItemStatus.Todo to listOf(item)))

        onNodeWithTag(BoardColumnTag(WorkItemStatus.Todo)).assertIsDisplayed()
        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        onNodeWithText(item.title).assertIsDisplayed()
    }

    /** Tapping a card selects that card's task. */
    val tappingACardInALaneSelectsIt: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Todo }
        var selected: WorkItem? = null

        setContent {
            Themed {
                BoardColumnView(
                    column = BoardColumn(WorkItemStatus.Todo, listOf(item)),
                    onSelect = { selected = it },
                )
            }
        }
        onNodeWithTag(WorkItemCardTag(item)).performClick()

        assertEquals(item, selected)
    }
}

/** Draws one board: a column per lane, populated from [itemsByStatus]. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardWith(itemsByStatus: Map<WorkItemStatus, List<WorkItem>>) {
    setContent {
        Themed {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkItemStatus.boardOrder.forEach { status ->
                    BoardColumnView(
                        column = BoardColumn(status = status, items = itemsByStatus[status].orEmpty()),
                        onSelect = {},
                    )
                }
            }
        }
    }
}
