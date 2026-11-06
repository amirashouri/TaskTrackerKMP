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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.TTToastTag
import com.tasktracker.domain.Project
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
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
                )
            }
        }
    }

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

/** Two board columns a resolver test can hand a project: a single Todo lane, and a Todo + doing pair. */
private val singleBoardStatus = listOf(
    StatusDefinition(id = "col-todo", name = "To Do", category = WorkItemStatus.Todo, order = 0),
)
private val pairedBoardStatuses = listOf(
    StatusDefinition(id = "col-todo", name = "To Do", category = WorkItemStatus.Todo, order = 0),
    StatusDefinition(id = "col-doing", name = "Doing", category = WorkItemStatus.InProgress, order = 1),
)

/**
 * Whether two rectangles share any area.
 *
 * Touching edges are not an overlap: a control whose right edge meets a row's left edge covers
 * nothing, and counting that as a collision would fail a layout that is correct.
 */
private fun Rect.overlaps(other: Rect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom
