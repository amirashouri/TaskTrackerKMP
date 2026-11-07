package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.core.randomTagID
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * What a backlog row does with its copy control, as scenarios.
 *
 * ## Why the payload is asserted here and the confirmation elsewhere
 *
 * The row owns the control and the text it hands over; the *confirmation* belongs to the screen
 * that hosts the clipboard (`ClipboardCopyHost`), so it is tested in `ProjectBacklogScenarios`
 * against the whole screen. The clipboard write itself is deliberately not asserted: there is no
 * portable way to read the platform clipboard from `commonTest`, and the rule that decides what
 * goes on it is `clipboardText`, unit-tested in `WorkItemCardRulesTest`.
 */
@OptIn(ExperimentalTestApi::class)
object WorkItemBacklogRowScenarios {

    /** The control is on every row the backlog wires, including one with no due date or assignee. */
    val showsTheCopyControl: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Backlog }

        rowShowing(item = item, onCopy = {})

        onNested(BacklogCopyTag(item)).assertIsDisplayed()
    }

    /** The tag strip draws icons alone, on the same row shape as the board card. */
    val showsTagsAsIconsWithoutTheirNames: suspend ComposeUiTest.() -> Unit = {
        val tag = ProjectTag(name = "Bug", color = TagColor.Red, icon = TagIcon.Bug)
        val item = ViewPreviewData.workItems
            .first { it.status == WorkItemStatus.Backlog }
            .copy(tagIDs = listOf(tag.id, randomTagID()))

        rowShowing(item = item, onCopy = {}, projectTags = listOf(tag))

        onNested(WorkItemTagChipTag(tag)).assertIsDisplayed()
        onNodeWithText("Bug").assertDoesNotExist()
    }

    /**
     * Copying hands the host the title and the details, and does not also open the row.
     *
     * The expected payload is restated as a literal rather than produced by `clipboardText`:
     * asking the function under test what it should have handed over means a row that copied the
     * wrong text still passes, because both sides move together.
     *
     * Both halves are one scenario because they are one gesture. A copy control that also opened
     * the row would still hand the right string over, and only asserting both at once holds the
     * whole of what tapping it is supposed to do.
     */
    val copyingARowHandsTheTitleAndDetailsToTheHost: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems
            .first { it.status == WorkItemStatus.Backlog }
            .copy(details = "Reported on a cold install with no keychain entry.")
        var copied: String? = null
        var opened = false

        rowShowing(
            item = item,
            onCopy = { copied = it },
            onOpen = { opened = true },
        )
        onNested(BacklogCopyTag(item)).performClick()

        assertEquals(
            "${item.title}\n\n${item.details.trim()}",
            copied,
            "a copy should be the title, then a blank line, then the details",
        )
        assertFalse(
            opened,
            "tapping the copy control must not also open the row it copied",
        )
    }

    /**
     * A surface with no clipboard draws no control.
     *
     * The sprint detail shares this row and passes no `onCopy`; a control there would be a press
     * that silently does nothing, which is worse than not offering it.
     */
    val aRowWithoutACopyHandlerOffersNoCopyControl: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Backlog }

        rowShowing(item = item, onCopy = null)

        onNested(BacklogCopyTag(item)).assertDoesNotExist()
    }

    /**
     * A Kanban row reads the stored lane (`statusKey`), not the raw `status`, so a desynced item
     * agrees with the editor and the board.
     *
     * The bug this pins: an item with `status = Done` but `statusKey` naming an "In Progress" lane
     * showed a "Done" badge on the board while the editor showed "In Progress". The editor and the
     * board grouping both resolve the lane first; the badge was the only reader of `item.status`
     * raw. Hand the row a project with an In Progress lane and that exact item, and the badge must
     * say In Progress (never Done), and the row must offer File away rather than Reopen — the same
     * effective status drives both, so the board and the editor can no longer disagree about it.
     */
    val aRowWithADesyncedLaneShowsTheLaneStatus: suspend ComposeUiTest.() -> Unit = {
        val doing = StatusDefinition(id = "col-doing", name = "Doing", category = WorkItemStatus.InProgress, order = 0)
        val project = ViewPreviewData.seededProject.copy(statuses = listOf(doing))
        val item = ViewPreviewData.workItems
            .first { it.status == WorkItemStatus.Backlog }
            .copy(status = WorkItemStatus.Done, statusKey = doing.id)

        rowShowing(
            item = item,
            onCopy = {},
            project = project,
            showStatusBadge = true,
            onFileAway = {},
            onUnarchive = {},
        )

        onNodeWithTag(WorkItemStatusBadgeTag(WorkItemStatus.InProgress), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(WorkItemStatusBadgeTag(WorkItemStatus.Done), useUnmergedTree = true).assertDoesNotExist()
        onNested(BacklogFileAwayTag(item)).assertIsDisplayed()
        onNested(BacklogUnarchiveTag(item)).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.rowShowing(
    item: WorkItem,
    onCopy: ((String) -> Unit)?,
    onOpen: (() -> Unit)? = null,
    projectTags: List<ProjectTag> = emptyList(),
    project: Project? = null,
    showStatusBadge: Boolean = false,
    onFileAway: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
) {
    setContent {
        Themed {
            WorkItemBacklogRow(
                item = item,
                onPutOnBoard = {},
                onFileAway = onFileAway ?: {},
                onOpen = onOpen,
                onCopy = onCopy,
                onUnarchive = onUnarchive,
                projectTags = projectTags,
                project = project,
                showStatusBadge = showStatusBadge,
            )
        }
    }
}
