package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.assertEquals

/**
 * Dragging a board card between lanes, as a hand does it: press, wait, move, release.
 *
 * ## Why the press is held and not immediate
 *
 * The cards sit inside a lane that scrolls vertically and a row that scrolls horizontally.
 * A drag that started on the first pixel of movement would have to steal those gestures, and
 * the lane would stop scrolling from wherever a card happens to be - which is most of it.
 * The long press is the gesture that says "this one, not the page", so the scenarios below
 * hold still for the timeout and the ones that do not hold are asserted *not* to lift a card.
 *
 * ## Why the target is the Done lane on every host
 *
 * Done is the last lane, and on a phone it is two lanes off-screen when the board is at rest.
 * That is the move the user asked for (In Progress to Done) and it is only reachable because
 * the row scrolls while the finger is held near its edge. The same gesture scrolls the row to
 * its end on every host, then settles onto Done's centre before releasing; one scenario, both
 * geometries, and the assertion does not branch on the host.
 */
@OptIn(ExperimentalTestApi::class)
object BoardDragScenarios {

    /** A card can be held, carried to another lane, and released there. */
    val draggingACardToAnotherLaneMovesItThere: suspend ComposeUiTest.() -> Unit = {
        val todo = todoCard()
        val moves = mutableListOf<Pair<WorkItemID, WorkItemStatus>>()
        boardShowing { id, status -> moves += id to status }

        val screenWidth = onRoot().fetchSemanticsNode().size.width
        val from = onNodeWithTag(WorkItemCardTag(todo)).fetchSemanticsNode().boundsInRoot.center

        holdAndDrag(
            from = from,
            // The edge is where the row scrolls, not where it is dropped. Holding there brings
            // the last lane into reach on every window; at that point Done's centre is always
            // this far from the right edge, so the release lands on the lane and not on the
            // 16dp trailing gutter, whatever width the host's test window happens to be.
            to = Offset(screenWidth - 24f, from.y),
            settleAt = Offset(screenWidth - DONE_CENTRE_FROM_RIGHT_EDGE, from.y),
        )

        assertEquals(
            listOf(todo.id to WorkItemStatus.Done),
            moves,
            "the card should have been moved to the lane it was released over",
        )
    }

    /**
     * Releasing a card back where it came from moves nothing.
     *
     * The drop target exists while the card hovers over its own lane - that is what the outline
     * means - but a release there is not an edit. Sending `onMove(id, sameStatus)` would write
     * the item for no reason, and on a slow connection would turn a cancelled drag into a
     * spinner on a card that never left.
     */
    val droppingACardOnItsOwnLaneDoesNotMoveIt: suspend ComposeUiTest.() -> Unit = {
        val todo = todoCard()
        val moves = mutableListOf<Pair<WorkItemID, WorkItemStatus>>()
        boardShowing { id, status -> moves += id to status }

        val from = onNodeWithTag(WorkItemCardTag(todo)).fetchSemanticsNode().boundsInRoot.center

        holdAndDrag(from = from, to = from + Offset(24f, 0f))

        assertEquals(emptyList(), moves, "a release over the source lane is not a move")
    }

    /**
     * A quick swipe over a card scrolls the lane instead of lifting the card.
     *
     * The assertion is the absence of the ghost as much as the absence of a move: after a
     * short swipe the card must exist once, not once in the lane and once in the air. This is
     * the scenario that fails if the gesture is ever changed to `detectDragGestures` without
     * the long-press gate, which would take vertical scrolling away from every card.
     */
    val aQuickSwipeOnACardDoesNotLiftIt: suspend ComposeUiTest.() -> Unit = {
        val todo = todoCard()
        val moves = mutableListOf<Pair<WorkItemID, WorkItemStatus>>()
        boardShowing { id, status -> moves += id to status }

        val screenWidth = onRoot().fetchSemanticsNode().size.width
        val from = onNodeWithTag(WorkItemCardTag(todo)).fetchSemanticsNode().boundsInRoot.center

        onRoot().performTouchInput {
            // Across the board rather than up the lane: if the long-press gate is ever removed,
            // this swipe becomes a drag that ends over another lane and *does* move the card.
            // A vertical swipe would end back on its own lane and could pass by accident.
            swipe(
                start = from,
                end = Offset(screenWidth - 24f, from.y),
                durationMillis = 80,
            )
        }
        waitForIdle()

        assertEquals(emptyList(), moves, "a swipe is not a drag, so nothing should be moved")
        onAllNodesWithTag(WorkItemCardTag(todo), useUnmergedTree = true)
            .assertCountEquals(1)
    }
}

/** The board's Todo card in the preview fixture, which is the one these scenarios pick up. */
private fun todoCard(): WorkItem =
    ViewPreviewData.workItems.first { it.status == WorkItemStatus.Todo }

/**
 * Press, hold past the long-press timeout, travel, optionally settle, release.
 *
 * [to] is where the edge scroll runs; [settleAt] is where the card is actually dropped, which
 * must be away from the edge so the row stops moving before the release.
 */
@OptIn(ExperimentalTestApi::class)
private suspend fun ComposeUiTest.holdAndDrag(from: Offset, to: Offset, settleAt: Offset? = null) {
    // The drop target scrolls the row on every frame while the finger is at an edge, so the
    // test's clock would never be idle and `performTouchInput` would wait forever for it. The
    // frames are driven by hand instead; the gesture itself is a real injection either way.
    mainClock.autoAdvance = false
    try {
        onRoot().performTouchInput { down(from) }
        // The timeout itself runs on the test clock; holding the finger still is what lets it
        // fire. Moving before this would be a swipe, which the lane's scroll owns.
        mainClock.advanceTimeBy(LONG_PRESS_TIMEOUT_MS)
        onRoot().performTouchInput { moveTo(to) }
        // Frames at the edge so the row can scroll a lane that was off-screen into reach. On a
        // window where Done is already under the finger this changes no target.
        mainClock.advanceTimeBy(EDGE_SCROLL_MS)
        if (settleAt != null) {
            onRoot().performTouchInput { moveTo(settleAt) }
        }
        onRoot().performTouchInput { up() }
    } finally {
        mainClock.autoAdvance = true
    }
    waitForIdle()
}

/** Comfortably past the platform's 500ms long-press timeout. */
private const val LONG_PRESS_TIMEOUT_MS = 700L

/** Long enough for the edge scroll to cross two lanes on the narrowest host. */
private const val EDGE_SCROLL_MS = 2_000L

/**
 * At maximum scroll the trailing gutter sits at the window's right edge, so Done's centre is
 * its 16dp gutter plus half of its 300dp width away from that edge.
 */
private const val DONE_CENTRE_FROM_RIGHT_EDGE = 166f

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardShowing(onMove: (WorkItemID, WorkItemStatus) -> Unit) {
    setContent {
        Themed {
            ProjectBoardScreen(
                projection = ViewPreviewData.boardProjection(),
                isLoading = false,
                errorMessage = null,
                movingItemID = null,
                onSelect = {},
                onFileAway = {},
                onMove = onMove,
                onDismissError = {},
            )
        }
    }
}
