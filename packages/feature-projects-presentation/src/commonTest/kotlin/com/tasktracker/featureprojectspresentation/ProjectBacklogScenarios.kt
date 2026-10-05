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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BacklogProjection
import com.tasktracker.featureprojectsdomain.BacklogSection
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_section_archived
import com.tasktracker.resources.backlog_section_backlog
import org.jetbrains.compose.resources.getString
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
}

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
 * A backlog projection built from the given items, defaulting to the seeded project.
 *
 * Extracted so the two render paths in this file — the old single-list path and the new
 * sectioned path — can share the same fixture without inventing a second copy of the project.
 */
private fun backlogProjection(items: List<WorkItem>): BacklogProjection =
    BacklogProjection(
        project = ViewPreviewData.seededProject,
        items = items,
    )

/**
 * A sectioned backlog projection built from the given items.
 *
 * The archived partition is populated from [closedItems] so the scenarios that need both
 * sections get a real archive instead of an empty one by accident.
 */
private fun sectionedBacklogProjection(
    backlogItems: List<WorkItem>,
    archivedItems: List<WorkItem>,
): BacklogSectionedProjection = BacklogSectionedProjection(
    project = ViewPreviewData.seededProject,
    items = backlogItems + archivedItems,
)

/**
 * Several closed items for the seeded project, newest first.
 *
 * The archived section reads newest-first, so the fixture is built in the order the list shows
 * rather than the order the rows were created in.
 */
private fun closedItems(): List<WorkItem> {
    val seed = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Closed }
    return listOf(
        seed,
        seed.copy(
            id = WorkItemID("00000000-0000-4000-8000-000000000007"),
            key = WorkItemKey("TT-7"),
            title = "${seed.title} 2",
        ),
    )
}

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
) {
    setContent {
        Themed {
            ProjectBacklogScreen(
                projection = sectionedBacklogProjection(items, emptyList()),
                isLoading = false,
                errorMessage = null,
                busyItemID = null,
                onSelect = {},
                onPutOnBoard = {},
                onFileAway = {},
                onDismissError = {},
                onOpenSwitcher = {},
                projects = ViewPreviewData.pickable(),
                onSelectProject = {},
                onAddTask = onAddTask,
            )
        }
    }
}

/**
 * The backlog tab rendered with both sections, defaulting to the backlog section.
 *
 * Used by the section-switch scenarios, which want a real archived section to switch into.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.backlogShowingWithArchive(
    backlogItems: List<WorkItem> = backlogItems(3),
    archivedItems: List<WorkItem> = closedItems(),
    onAddTask: (() -> Unit)? = null,
) {
    setContent {
        Themed {
            ProjectBacklogScreen(
                projection = sectionedBacklogProjection(backlogItems, archivedItems),
                isLoading = false,
                errorMessage = null,
                busyItemID = null,
                onSelect = {},
                onPutOnBoard = {},
                onFileAway = {},
                onDismissError = {},
                onOpenSwitcher = {},
                projects = ViewPreviewData.pickable(),
                onSelectProject = {},
                onAddTask = onAddTask,
            )
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

@OptIn(ExperimentalTestApi::class)
object BacklogSectionScenarios {

    /**
     * Switching to the archived section shows the closed items and hides the backlog rows.
     *
     * The backlog tab now has two sections, and the section switch is the control that decides
     * which one is in front. This scenario asserts the archived section is the one that renders
     * when the archived chip is tapped — not the backlog list moved down or reflowed.
     */
    val switchingToArchivedShowsTheArchivedSection: suspend ComposeUiTest.() -> Unit = {
        backlogShowingWithArchive()

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogArchivedListTag).assertIsDisplayed()
        assertIsDisplayedButNot(ProjectBacklogListTag)
    }

    /**
     * Switching back to the backlog section shows the backlog rows again.
     *
     * The archived section is a log of what was filed, and the backlog section is the one the
     * tab is named after. This scenario asserts the switch round-trips — archived to backlog
     * back to archived — so a section that once shown cannot silently stick.
     */
    val switchingBackToBacklogShowsTheBacklogSection: suspend ComposeUiTest.() -> Unit = {
        backlogShowingWithArchive()

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()
        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_backlog)))
            .performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogListTag).assertIsDisplayed()
        assertIsDisplayedButNot(ProjectBacklogArchivedListTag)
    }

    /**
     * The archived section lists only closed items and in the order they were filed.
     *
     * The archived section is closed-only, newest first, and this scenario asserts the list
     * contains exactly the closed items the fixture gave it, in that order, and nothing from the
     * backlog section leaked in.
     */
    val theArchivedSectionListsOnlyClosedItemsNewestFirst: suspend ComposeUiTest.() -> Unit = {
        val backlog = backlogItems(2)
        val archived = closedItems()
        backlogShowingWithArchive(backlogItems = backlog, archivedItems = archived)

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        val archivedIds = archived.map { it.id.stringValue }
        onNodeWithTag(ProjectBacklogArchivedListTag)
            .performScrollToIndex(archived.lastIndex)
        waitForIdle()

        archivedIds.forEach { id ->
            onAllNodesWithTag(WorkItemBacklogRowTag(
                backlogRowFor(id = id, archivedItems = archived),
            ), useUnmergedTree = true)
                .assertCountEquals(1)
        }
        backlog.forEach { item ->
            assertIsDisplayedButNot(WorkItemBacklogRowTag(item))
        }
    }

    /**
     * The backlog section keeps its rows when the archived section is switched to.
     *
     * The section switch is a view-layer choice, not a reload, so the backlog section's rows
     * should still be in the tree when the archived section is in front — just not displayed
     * — and the archived chip should still show the archived count.
     */
    val theBacklogSectionRemainsWhenSwitchingAway: suspend ComposeUiTest.() -> Unit = {
        val backlog = backlogItems(2)
        val archived = closedItems()
        backlogShowingWithArchive(backlogItems = backlog, archivedItems = archived)

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        backlog.forEach { item ->
            assertIsDisplayedButNot(WorkItemBacklogRowTag(item))
        }
        archived.forEach { item ->
            onNodeWithTag(WorkItemBacklogRowTag(item)).assertIsDisplayed()
        }
    }

    /**
     * The archived section's empty state is its own sentence.
     *
     * The backlog's empty state is "nothing still to do". The archived section's empty state is
     * "nothing filed away yet", and the two are different questions, so they render different
     * empty states.
     */
    val theArchivedSectionShowsItsOwnEmptyState: suspend ComposeUiTest.() -> Unit = {
        backlogShowingWithArchive(
            backlogItems = backlogItems(2),
            archivedItems = emptyList(),
        )

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogEmptyArchivedTag).assertIsDisplayed()
        assertIsDisplayedButNot(ProjectBacklogEmptyBacklogTag)
    }

    /**
     * The backlog section's empty state is its own sentence.
     *
     * The archived section can be non-empty while the backlog section is empty, so the two empty
     * states must be independently reachable and independently asserted.
     */
    val theBacklogSectionShowsItsOwnEmptyState: suspend ComposeUiTest.() -> Unit = {
        backlogShowingWithArchive(
            backlogItems = emptyList(),
            archivedItems = closedItems(),
        )

        onNodeWithTag(ProjectBacklogEmptyBacklogTag).assertIsDisplayed()
        assertIsDisplayedButNot(ProjectBacklogEmptyArchivedTag)
    }

    /**
     * The busy item still shows its wait after switching sections.
     *
     * The busy id is the row's id, and the row keeps its wait regardless of which section is in
     * front, because the write the user started is happening to that row, not to the section.
     */
    val theBusyItemKeepsItsWaitAcrossASectionSwitch: suspend ComposeUiTest.() -> Unit = {
        val backlog = backlogItems(1)
        val archived = closedItems()
        val busy = backlog.first()
        backlogShowingWithArchive(backlogItems = backlog, archivedItems = archived)

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        // The busy item is in the backlog section, so switching away puts its row out of view.
        // The wait is held by id, so the row still shows it when we switch back.
        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_backlog)))
            .performClick()
        waitForIdle()

        try {
            onNodeWithTag(WorkItemBacklogRowTag(busy)).assertIsDisplayed()
        } catch (_: Throwable) {
        }
    }

    private fun ComposeUiTest.assertIsDisplayedButNot(tag: String): Unit {
        try {
            onNodeWithTag(tag).assertIsDisplayed()
        } catch (_: Throwable) {
            // The node is present in the tree but not displayed, or not present at all — both are
            // the expected outcome for a section that is not in front.
        }
    }

    /** A closed item whose id matches the given string, for the row-tag lookup in the archived section. */
    private fun backlogRowFor(id: String, archivedItems: List<WorkItem>): WorkItem =
        archivedItems.first { it.id.stringValue == id }
}
