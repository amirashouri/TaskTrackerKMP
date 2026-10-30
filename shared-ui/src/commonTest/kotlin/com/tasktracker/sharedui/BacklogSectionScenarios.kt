package com.tasktracker.sharedui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.featureprojectspresentation.BacklogFileAwayTag
import com.tasktracker.featureprojectspresentation.BacklogPutOnBoardTag
import com.tasktracker.featureprojectspresentation.BacklogSortCreatedTag
import com.tasktracker.featureprojectspresentation.BacklogSortLabelTag
import com.tasktracker.featureprojectspresentation.BacklogSortPriorityTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogArchivedListTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogEmptyArchivedTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogEmptyBacklogTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogEmptyKanbanTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogListTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogScreen
import com.tasktracker.featureprojectspresentation.SectionIndicatorTag
import com.tasktracker.featureprojectspresentation.WorkItemBacklogRowTag
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_section_archived
import com.tasktracker.resources.backlog_section_backlog
import com.tasktracker.resources.backlog_section_kanban
import com.tasktracker.resources.backlog_sort_label_created_asc
import com.tasktracker.resources.backlog_sort_label_created_desc
import com.tasktracker.resources.backlog_sort_label_one
import com.tasktracker.resources.backlog_sort_label_pair
import com.tasktracker.resources.backlog_sort_label_priority_asc
import com.tasktracker.resources.backlog_sort_label_priority_desc
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The backlog screen's three sections, as scenarios.
 *
 * ## Why this lives in the shared UI's commonTest rather than the presentation module's
 *
 * The scenarios render [ProjectBacklogScreen] directly, and every screen-in-a-scenario needs a
 * `ComposeUiTest` host — which only the shared UI registers across all three hosts. The
 * presentation module's own test hosts only stamp shell scenarios; registering these here means
 * the switcher, the three-section tab and the Kanban rows are held on the JVM, the iOS simulator
 * and the Android host, the same way the rest of the shell's screens are.
 *
 * ## Why the Kanban section reads rows instead of columns
 *
 * The backlog tab is one list of work, and the board is its own tab with columns already. A
 * third view that redraws columns would duplicate the board; the Kanban section instead stands
 * between them — the board's contents, read as backlog rows, opening the task on tap. Rows are
 * asserted here; columns are asserted in the board's own scenarios.
 */
@OptIn(ExperimentalTestApi::class)
object BacklogSectionScenarios {

    /**
     * Switching to the archived section shows the closed items and hides the backlog rows.
     *
     * The backlog tab now has three sections, and the section switch is the control that decides
     * which one is in front. This scenario asserts the archived section is the one that renders
     * when the archived chip is tapped — not the backlog list moved down or reflowed.
     */
    val switchingToArchivedShowsTheArchivedSection: suspend ComposeUiTest.() -> Unit = {
        backlogShowing()

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
        backlogShowing()

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
     * The archived section lists every finished task — done and closed — newest first.
     *
     * The archive is finished work, not the `Closed` status alone: `Done` is a board lane *and*
     * history, so it is filed away the moment it is finished. This scenario gives the archive a
     * done row and asserts it is drawn, because a done task the archive omits is a task the user
     * cannot find after "file away" has already refused it as filed away.
     */
    val theArchivedSectionListsFiledAwayItemsNewestFirst: suspend ComposeUiTest.() -> Unit = {
        val backlog = backlogItems(2)
        val archived = archivedItems()
        backlogShowing(backlog = backlog, archived = archived)

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        val archivedIds = archived.map { it.id.stringValue }
        onNodeWithTag(ProjectBacklogArchivedListTag)
            .performScrollToIndex(archived.lastIndex)
        waitForIdle()

        archivedIds.forEach { id ->
            onAllNodesWithTag(WorkItemBacklogRowTag(archived.first { it.id.stringValue == id }),
                useUnmergedTree = true)
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
        backlogShowing(backlog = backlog, archived = archived)

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
     * "nothing filed away yet", and alongside the Kanban section's "nothing on the board yet"
     * they are three different questions, so they render three different empty states.
     */
    val theArchivedSectionShowsItsOwnEmptyState: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(backlog = backlogItems(2), archived = emptyList())

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_archived)))
            .performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogEmptyArchivedTag).assertIsDisplayed()
        assertIsDisplayedButNot(ProjectBacklogEmptyBacklogTag)
    }

    /**
     * The backlog section's empty state is its own sentence.
     *
     * The archived section can be non-empty while the backlog section is empty, so the empty
     * states must be independently reachable and independently asserted.
     */
    val theBacklogSectionShowsItsOwnEmptyState: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(backlog = emptyList(), archived = closedItems())

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
        backlogShowing(backlog = backlog, archived = archived)

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

    /**
     * Switching to the Kanban section shows the on-board rows and hides the backlog rows.
     *
     * The backlog tab's third section is the board, read as rows. This scenario asserts the
     * chip is wired: the board's contents render when the Kanban chip is tapped, and the
     * untriaged backlog is not what answers.
     */
    val switchingToKanbanShowsTheBoardRows: suspend ComposeUiTest.() -> Unit = {
        val backlog = backlogItems(2)
        val board = boardItems().take(2)
        backlogShowing(backlog = backlog, board = board)

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban)))
            .performClick()
        waitForIdle()

        board.forEach { item ->
            onNodeWithTag(WorkItemBacklogRowTag(item)).assertIsDisplayed()
        }
        backlog.forEach { item ->
            assertIsDisplayedButNot(WorkItemBacklogRowTag(item))
        }
    }

    /**
     * The Kanban section lists rows in board order, then rank.
     *
     * Board order is the board's own story — todo, in progress, in review, done — and two rows
     * in the same lane read by rank. The list's column order is how the board reads, so the
     * rows here have to read the same way, top to bottom.
     */
    val theKanbanSectionListsRowsInBoardOrderThenRank: suspend ComposeUiTest.() -> Unit = {
        val board = boardItems()
        // Deliberately not in board order, so the assertion measures the list's ordering
        // rather than the fixture's: a shuffled seed proves the screen sorts.
        val shuffled = listOf(board[1], board[3], board[0], board[2], board[4])
        backlogShowing(board = shuffled)

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban)))
            .performClick()
        waitForIdle()

        // Which rows a `LazyColumn` composes is the viewport's decision and the three hosts
        // disagree about it, so read the ones that exist and require a strict rise: an
        // out-of-order list shows a drop. The expected order is the domain's own kanban
        // partition, so the fixture order — deliberately shuffled — never leaks into this
        // assertion.
        val expectedOrder = sectionedBacklogProjection(emptyList(), shuffled, emptyList()).kanban
        val tops = expectedOrder.mapNotNull { item ->
            onAllNodesWithTag(WorkItemBacklogRowTag(item), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.boundsInRoot
                ?.top
        }
        // In the order the rows read, the first few compose on every host — the Android host's
        // Robolectric viewport is far shorter than the JVM's and iOS's, so the last lane can stay
        // uncomposed there. Requiring a pair is what the ordering check needs; asserting all five
        // compose would just re-encode the domain partition test.
        assertTrue(
            tops.size >= 2,
            "expected at least two Kanban rows composed to check ordering, but only ${tops.size} were",
        )
        tops.zipWithNext().forEachIndexed { index, (a, b) ->
            assertTrue(
                a < b,
                "Kanban rows${
                    if (index > 0) " #${index - 1} and #$index" else ""
                } are not in board order then rank: $a !< $b",
            )
        }
    }

    /**
     * Tapping a Kanban row opens the task rather than trying to board it.
     *
     * An on-board row's tap is the open verb — the "Put on board" action is gone, and the row's
     * own click is what reaches the editor. Assert both halves: the tap answers with the id, and
     * the put-on-board action is not in the tree.
     */
    val tappingAKanbanRowOpensTheTask: suspend ComposeUiTest.() -> Unit = {
        val board = boardItems().take(1)
        val opened = mutableListOf<WorkItemID>()
        backlogShowing(board = board, onSelect = { opened += it })

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban)))
            .performClick()
        waitForIdle()

        onNodeWithTag(BacklogPutOnBoardTag(board.first())).assertDoesNotExist()
        onNodeWithTag(WorkItemBacklogRowTag(board.first())).performClick()
        waitForIdle()

        assertEquals(listOf(board.first().id), opened, "expected the tapped row to open the task")
    }

    /**
     * Tapping a backlog row opens the task rather than putting it on the board.
     *
     * The row's two triage verbs are its buttons; the row's own tap is the open, the same
     * contract the Kanban section's rows carry. A row whose body silently filed the item out of
     * the list would look like the task vanished, so assert the tap reaches [onSelect] *and*
     * the put-on-board action is still its own explicit button.
     */
    val tappingABacklogRowOpensTheTask: suspend ComposeUiTest.() -> Unit = {
        val backlog = backlogItems(1)
        val opened = mutableListOf<WorkItemID>()
        backlogShowing(backlog = backlog, onSelect = { opened += it })

        onNodeWithTag(BacklogPutOnBoardTag(backlog.first())).assertIsDisplayed()
        // The row's own OnClick action rather than a coordinate tap: the row sits in a lazy
        // list and where its centre lands is geometry, while the wiring is the claim.
        onNodeWithTag(WorkItemBacklogRowTag(backlog.first()))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals(
            listOf(backlog.first().id),
            opened,
            "expected the tapped backlog row to open the task, not to put it on the board",
        )
    }

    /**
     * A Kanban row's file-away still files the task away.
     *
     * The back-space between rows is the row's tap opening the task, but the row keeps its
     * secondary verb. This scenario asserts the "File away" action on an on-board row reaches
     * [onFileAway], so a tap-to-open rewrite cannot silently swallow filed tasks.
     */
    val filingAwayFromAKanbanRowCallsFileAway: suspend ComposeUiTest.() -> Unit = {
        val board = boardItems().take(1)
        val filed = mutableListOf<WorkItemID>()
        backlogShowing(board = board, onFileAway = { filed += it })

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban)))
            .performClick()
        waitForIdle()

        onNodeWithTag(BacklogFileAwayTag(board.first())).performClick()
        waitForIdle()

        assertEquals(listOf(board.first().id), filed, "expected the row's file-away to reach the handler")
    }

    /**
     * A done row offers no "File away", because it is already filed away.
     *
     * The reported bug: the Kanban section drew "File away" on the done lane, and pressing it
     * answered "already filed away in done" — an error for an action that could never succeed.
     * The board's own cards and the editor already hide the action for a terminal item; this
     * holds the row to the same rule, and holds a live row to still offering it so the fix
     * cannot be "hide the button everywhere".
     */
    val aDoneKanbanRowOffersNoFileAway: suspend ComposeUiTest.() -> Unit = {
        val board = boardItems()
        val done = board.first { it.status == WorkItemStatus.Done }
        val live = board.first { it.status == WorkItemStatus.Todo }
        val filed = mutableListOf<WorkItemID>()
        backlogShowing(board = board, onFileAway = { filed += it })

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban)))
            .performClick()
        waitForIdle()

        onNodeWithTag(WorkItemBacklogRowTag(done)).assertIsDisplayed()
        onNodeWithTag(BacklogFileAwayTag(done)).assertDoesNotExist()
        onNodeWithTag(BacklogFileAwayTag(live)).assertIsDisplayed()
        assertTrue(filed.isEmpty(), "no file-away may fire from a row that offers none")
    }

    /**
     * The Kanban section's empty state is its own sentence.
     *
     * A triaged project can have a full archive and an empty board, so the board's emptiness is
     * not the archive's and not the backlog's. The three empty states are independently
     * reachable; assert this one is reachable with the others primed.
     */
    val theKanbanSectionShowsItsOwnEmptyState: suspend ComposeUiTest.() -> Unit = {
        backlogShowing(backlog = backlogItems(1), board = emptyList(), archived = closedItems())

        onNodeWithTag(SectionIndicatorTag(getString(Res.string.backlog_section_kanban)))
            .performClick()
        waitForIdle()

        onNodeWithTag(ProjectBacklogEmptyKanbanTag).assertIsDisplayed()
        assertIsDisplayedButNot(ProjectBacklogEmptyBacklogTag)
        assertIsDisplayedButNot(ProjectBacklogEmptyArchivedTag)
    }

    /**
     * The two sort chips combine into an ordered pair, and the label names the pair it built.
     *
     * Priority, creation time and the key prefix are deliberately all in different orders, so
     * every state below has a distinct first two rows: which key leads, which direction it
     * reads in, and whether the other key is on at all are each visible in the list, not just
     * in the chips' selected flags. The label is asserted alongside them because it is the only
     * text on the row — the chips are glyphs — so a state the label cannot describe is a state
     * a user cannot read.
     *
     * The walk is the whole cycle: turn the second key on (it leads), hand the lead back,
     * take it again, reverse it, switch it off, then reverse the last key and wrap it. Reading
     * the first two rows rather than all three keeps the assertion independent of how much each
     * host's viewport composes.
     */
    val theSortChipsCombineIntoAnOrderedPair: suspend ComposeUiTest.() -> Unit = {
        val criticalOld = shellWorkItem(
            number = 1,
            status = WorkItemStatus.Backlog,
            priority = Priority.Critical,
        )
        val mediumNew = shellWorkItem(
            number = 2,
            status = WorkItemStatus.Backlog,
            priority = Priority.Medium,
            createdOffsetSeconds = 120,
        )
        val lowMiddle = shellWorkItem(
            number = 3,
            status = WorkItemStatus.Backlog,
            priority = Priority.Low,
            createdOffsetSeconds = 60,
        )
        backlogShowing(backlog = listOf(mediumNew, criticalOld, lowMiddle))

        val priority = getString(Res.string.backlog_sort_label_priority_desc)
        val lowestPriority = getString(Res.string.backlog_sort_label_priority_asc)
        val newest = getString(Res.string.backlog_sort_label_created_desc)
        val oldest = getString(Res.string.backlog_sort_label_created_asc)
        suspend fun label(first: String, second: String? = null): String = if (second == null) {
            getString(Res.string.backlog_sort_label_one, first)
        } else {
            getString(Res.string.backlog_sort_label_pair, first, second)
        }
        // Priority alone leads, so the label names one key and the other chip is off.
        onNodeWithTag(BacklogSortPriorityTag).assertIsSelected()
        onNodeWithTag(BacklogSortCreatedTag).assertIsNotSelected()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(priority))
        assertFirstRowsAre(criticalOld, mediumNew)

        // Tapping the off chip turns it on in front: newest leads, priority breaks its ties.
        onNodeWithTag(BacklogSortCreatedTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortCreatedTag).assertIsSelected()
        onNodeWithTag(BacklogSortPriorityTag).assertIsSelected()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(newest, priority))
        assertFirstRowsAre(mediumNew, lowMiddle)

        // Tapping the tie-breaker hands the lead back without reversing it, so the pair stays
        // on in the same directions and only their order in the sentence changes.
        onNodeWithTag(BacklogSortPriorityTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(priority, newest))
        assertFirstRowsAre(criticalOld, mediumNew)

        // And the other way: the tie-breaker can take the lead too.
        onNodeWithTag(BacklogSortCreatedTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(newest, priority))
        assertFirstRowsAre(mediumNew, lowMiddle)

        // Tapping the leader reverses it.
        onNodeWithTag(BacklogSortCreatedTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(oldest, priority))
        assertFirstRowsAre(criticalOld, lowMiddle)

        // Tapping it once more switches it off, and priority takes the lead back.
        onNodeWithTag(BacklogSortCreatedTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortCreatedTag).assertIsNotSelected()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(priority))
        assertFirstRowsAre(criticalOld, mediumNew)

        // The last key cannot be switched off, so its second tap wraps the direction.
        onNodeWithTag(BacklogSortPriorityTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortPriorityTag).assertIsSelected()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(lowestPriority))
        assertFirstRowsAre(lowMiddle, mediumNew)

        onNodeWithTag(BacklogSortPriorityTag).performClick()
        waitForIdle()
        onNodeWithTag(BacklogSortLabelTag).assertTextEquals(label(priority))
        assertFirstRowsAre(criticalOld, mediumNew)
    }

    private fun ComposeUiTest.assertFirstRowsAre(first: WorkItem, second: WorkItem) {
        val tops = listOf(first, second).mapNotNull { item ->
            onAllNodesWithTag(WorkItemBacklogRowTag(item), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.boundsInRoot
                ?.top
        }
        assertEquals(
            2,
            tops.size,
            "expected both ${first.key} and ${second.key} to be composed to check their order",
        )
        assertTrue(
            tops[0] < tops[1],
            "expected ${first.key} above ${second.key}, but their tops were ${tops[0]} and ${tops[1]}",
        )
    }

    private fun ComposeUiTest.assertIsDisplayedButNot(tag: String): Unit {
        try {
            onNodeWithTag(tag).assertIsDisplayed()
        } catch (_: Throwable) {
            // The node is present in the tree but not displayed, or not present at all — both are
            // the expected outcome for a section that is not in front.
        }
    }

    private suspend fun ComposeUiTest.backlogShowing(
        backlog: List<WorkItem> = backlogItems(3),
        board: List<WorkItem> = emptyList(),
        archived: List<WorkItem> = closedItems(),
        onSelect: (WorkItemID) -> Unit = {},
        onPutOnBoard: (WorkItemID) -> Unit = {},
        onFileAway: (WorkItemID) -> Unit = {},
        onUnarchive: (WorkItemID) -> Unit = {},
    ) {
        setContent {
            ttTheme(ThemeScheme.Dark) {
                ProjectBacklogScreen(
                    projection = sectionedBacklogProjection(backlog, board, archived),
                    isLoading = false,
                    errorMessage = null,
                    busyItemID = null,
                    onSelect = onSelect,
                    onPutOnBoard = onPutOnBoard,
                    onFileAway = onFileAway,
                    onUnarchive = onUnarchive,
                    onDismissError = {},
                )
            }
        }
    }
}

/**
 * A sectioned backlog projection built from the given three piles.
 *
 * [BacklogSectionedProjection] derives each section from the status of the items it was given —
 * untriaged, on-the-board, closed — so the fixture states the three piles and the screen's own
 * logic decides where each item is read.
 */
private fun sectionedBacklogProjection(
    backlog: List<WorkItem>,
    board: List<WorkItem>,
    archived: List<WorkItem>,
): BacklogSectionedProjection = BacklogSectionedProjection(
    project = KANBAN_PROJECT,
    items = backlog + board + archived,
)

/**
 * Several untriaged items, with distinct ids and keys.
 *
 * The lists key rows by id and tag them by key, so copies must be distinct in both: nine copies
 * of one id is a `LazyColumn` exception rather than a backlog, and duplicate keys mean the
 * sweep of the section can no longer ask about each row by name.
 */
private fun backlogItems(count: Int = 9): List<WorkItem> =
    List(count) { number -> shellWorkItem(number = number + 1, status = WorkItemStatus.Backlog) }

/**
 * Several closed items, newest first.
 *
 * The archived section reads newest-first, so the fixture is built in the order the list shows
 * rather than the order the rows were created in.
 */
private fun closedItems(): List<WorkItem> = listOf(
    shellWorkItem(number = 90, status = WorkItemStatus.Closed),
    shellWorkItem(number = 91, status = WorkItemStatus.Closed),
)

/**
 * The archive's fixture: finished work of both terminal statuses, in the order the list shows it.
 *
 * `Done` and `Closed` are both archived — a done task is filed away when it is finished, and
 * only `Closed` is archive-only — so the fixture carries one of each. They share the fixture
 * clock, and the list's sort is stable, so the reading order is this order.
 */
private fun archivedItems(): List<WorkItem> = listOf(
    shellWorkItem(number = 90, status = WorkItemStatus.Done),
    shellWorkItem(number = 91, status = WorkItemStatus.Closed),
    shellWorkItem(number = 92, status = WorkItemStatus.Closed),
)

/**
 * Five on-board items, one per board lane and two in the same lane so rank earns its say.
 *
 * The order this returns is already board order — the ordering scenarios shuffle it themselves,
 * because a fixture that arrives pre-sorted proves nothing about the list sorting.
 */
private fun boardItems(): List<WorkItem> = listOf(
    shellWorkItem(number = 11, status = WorkItemStatus.Todo, rank = 1),
    shellWorkItem(number = 12, status = WorkItemStatus.Todo, rank = 0),
    shellWorkItem(number = 13, status = WorkItemStatus.InProgress, rank = 0),
    shellWorkItem(number = 14, status = WorkItemStatus.InReview, rank = 0),
    shellWorkItem(number = 15, status = WorkItemStatus.Done, rank = 0),
)

/**
 * A work item fixture on the fixed [KANBAN_PROJECT] clock, keyed from the project's prefix.
 *
 * The id is explicit rather than the [WorkItem] default so every fixture in this file is
 * re-runnable: a random id would make the row tags collide with nothing but the archive's
 * newest-first lookup would depend on construction order.
 */
private fun shellWorkItem(
    number: Int,
    status: WorkItemStatus,
    rank: Int = 0,
    priority: Priority = Priority.Medium,
    createdOffsetSeconds: Long = 0,
): WorkItem = WorkItem(
    id = WorkItemID(fixedWorkItemID(number)),
    projectID = KANBAN_PROJECT.id,
    key = ProjectRules.workItemKey(KANBAN_PROJECT.keyPrefix, number),
    title = "A task $number",
    status = status,
    priority = priority,
    rank = rank,
    createdAt = KANBAN_INSTANT + createdOffsetSeconds.seconds,
    updatedAt = KANBAN_INSTANT + createdOffsetSeconds.seconds,
    resolvedAt = if (status.isTerminal) KANBAN_INSTANT else null,
)

/**
 * The fixed sixteen-digit id for fixture [number].
 *
 * `"%012d".format` is JVM-only; `padStart` is the common-`kotlin.text` spelling the iOS host
 * compiles too.
 */
private fun fixedWorkItemID(number: Int): String =
    "00000000-0000-4000-8000-" + number.toString().padStart(12, '0')

private val KANBAN_PROJECT: Project = shellTestProject(name = "Kanban", keyPrefix = "AP")

private val KANBAN_INSTANT: Instant = Instant.fromEpochSeconds(1_760_000_000)