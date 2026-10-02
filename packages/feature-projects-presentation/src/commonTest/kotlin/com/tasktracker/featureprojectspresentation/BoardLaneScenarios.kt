package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BoardProjection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the board's lane row sits on the screen, as scenarios.
 *
 * ## What is being held still
 *
 * The lane row is the one thing on this screen that runs to the edges. Everything else - the
 * project switcher, the error banner, the empty state - is inset by the screen gutter, because
 * text and buttons need air around them and a full-bleed button reads as a bug.
 *
 * The row is the exception because it *scrolls*. A gutter applied to the row's own container
 * becomes a gutter on the scroll viewport: the row stops 32dp short of each edge no matter
 * where it has been dragged to, so a lane arriving from the right slides up to a strip of bare
 * canvas and stops there. On a page whose background shows through, that strip reads as a
 * panel lying on top of the lanes rather than behind them.
 *
 * So the gutter moves *inside* the row - to a leading spacer that scrolls away with the
 * content, the way a `LazyRow`'s `contentPadding` behaves. Lane 0 then rests with the gutter
 * visible at the left, exactly as before, and no lane is ever inset from the right.
 *
 * These scenarios assert bounds rather than appearance. Whether a lane *looks* flush with the
 * edge is a pixel question; whether its container reaches the edge is a fact about the layout
 * that survives a theme change, and it is the one that went wrong.
 */
@OptIn(ExperimentalTestApi::class)
object BoardLaneScenarios {

    /** The lane row's own container runs the whole width of the screen. */
    val theLaneRowReachesBothEdgesOfTheScreen: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        val screen = onRoot().fetchSemanticsNode().size
        val row = onNodeWithTag(ProjectBoardColumnsTag).fetchSemanticsNode()

        assertEquals(
            0f,
            row.boundsInRoot.left,
            "the row's viewport starts at the left edge, so a lane arriving from the left is not inset",
        )
        assertEquals(
            screen.width.toFloat(),
            row.boundsInRoot.right,
            "and ends at the right edge, so a lane arriving from the right is not inset either",
        )
    }

    /**
     * The first lane still rests with a gutter beside it.
     *
     * The counterpart to the scenario above, and the reason the row is not simply given
     * `padding(0.dp)`: reaching the edge is what the *viewport* should do, and lane 0 is not
     * the viewport's left edge. Without a leading spacer it would sit against the bezel.
     */
    val theFirstLaneRestsAGutterFromTheLeftEdge: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        val row = onNodeWithTag(ProjectBoardColumnsTag).fetchSemanticsNode()
        val todo = onNodeWithTag(BoardColumnTag(WorkItemStatus.Todo)).fetchSemanticsNode()
        val gutter = densityPixels(ttSpacing.screenGutterLarge)

        assertEquals(
            gutter,
            todo.boundsInRoot.left - row.boundsInRoot.left,
            "lane 0 is held off the edge by the gutter, which scrolls away with it",
        )
    }

    /** The switcher, which is not the row, is still inset from both edges. */
    val theSwitcherIsStillInsetFromTheEdges: suspend ComposeUiTest.() -> Unit = {
        setContent {
            Themed {
                ProjectBoardScreen(
                    projection = ViewPreviewData.boardProjection(),
                    isLoading = false,
                    errorMessage = null,
                    movingItemID = null,
                    onSelect = {},
                    onFileAway = {},
                    onMove = { _, _ -> },
                    onDismissError = {},
                    onOpenSwitcher = {},
                )
            }
        }

        val screen = onRoot().fetchSemanticsNode().size
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode()
        val gutter = densityPixels(ttSpacing.screenGutterLarge)

        assertEquals(gutter, switcher.boundsInRoot.left, "insets itself from the left")
        assertEquals(screen.width.toFloat() - gutter, switcher.boundsInRoot.right, "and from the right")
    }

    /** Every lane is still drawn, edge-to-edge layout notwithstanding. */
    val everyLaneIsStillDrawn: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        WorkItemStatus.boardOrder.forEach { status ->
            onNodeWithTag(BoardColumnTag(status)).assertIsDisplayed()
        }
        assertTrue(
            onNodeWithTag(BoardColumnTag(WorkItemStatus.Done)).fetchSemanticsNode()
                .boundsInRoot.right > onRoot().fetchSemanticsNode().size.width / 2,
            "and the last lane is off past the middle, i.e. the row really does scroll",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardShowing(projection: BoardProjection = ViewPreviewData.boardProjection()) {
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

/**
 * [dp] in the density this test is running at.
 *
 * The scenarios above compare a `Dp` from the spacing ramp against a measured `Float` in root
 * coordinates, and root coordinates are pixels. Reading the density off the node that carries
 * the tag keeps the comparison honest at whatever density the host runs - a JVM host at 1.0 and
 * an Android host at 3.0 are both 1.0 and 3.0 respectively, and hardcoding either would fail on
 * the other.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.densityPixels(dp: Dp): Float = with(density) { dp.toPx() }