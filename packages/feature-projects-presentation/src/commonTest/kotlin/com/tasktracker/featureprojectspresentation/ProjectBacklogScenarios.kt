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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.TTToastTag
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val rows = items.flatMap { composedRow(it) }

        assertTrue(
            rows.isNotEmpty(),
            "expected at least one row to be composed, or this asserts nothing",
        )
        for (row in rows) {
            assertFalse(
                create.overlaps(row),
                "the add action sits on a row: add=$create row=$row",
            )
        }
    }

    /**
     * The add action sits on the switcher's row, to its right, and not in the list.
     *
     * The overlap assertion above would pass for a button placed *anywhere* clear of the rows -
     * a top app bar, a bottom bar, anywhere - so on its own it does not pin down what was asked
     * for. This one does: the action is to the right of the switcher, and above the list.
     *
     * Two rectangles and a vertical comparison, rather than a screenshot, because "is it beside
     * this other thing" survives being read off the tree.
     */
    val theAddActionTrailsTheSwitcher: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(items = backlogItems(), onAddTask = {})

        val add = onNodeWithTag(BacklogAddTaskTag).fetchSemanticsNode().boundsInRoot
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val list = onNodeWithTag(ProjectBacklogListTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            add.left >= switcher.right,
            "the add action should trail the switcher, not sit under it: " +
                "switcher=$switcher add=$add",
        )
        assertTrue(
            add.bottom <= list.top,
            "the add action should be above the list rather than over it: add=$add list=$list",
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
) {
    setContent {
        Themed {
            // A fixed width is how the narrow-row scenario asks the screen the question a
            // 320pt phone asks; the host's own window is wider than any phone. The default
            // fills the test window, so the older scenarios' measured bounds are unchanged.
            Box(
                modifier = if (width == null) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.width(width).fillMaxHeight()
                },
            ) {
                ProjectBacklogScreen(
                    projection = sectionedBacklogProjection(items, emptyList()),
                    isLoading = false,
                    errorMessage = null,
                    busyItemID = null,
                    onSelect = {},
                    onPutOnBoard = {},
                    onFileAway = {},
                    onUnarchive = {},
                    onDismissError = {},
                    onOpenSwitcher = {},
                    projects = ViewPreviewData.pickable(),
                    onSelectProject = {},
                    onAddTask = onAddTask,
                )
            }
        }
    }
}

/**
 * Whether two rectangles share any area.
 *
 * Touching edges are not an overlap: a control whose right edge meets a row's left edge covers
 * nothing, and counting that as a collision would fail a layout that is correct.
 */
private fun Rect.overlaps(other: Rect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom
