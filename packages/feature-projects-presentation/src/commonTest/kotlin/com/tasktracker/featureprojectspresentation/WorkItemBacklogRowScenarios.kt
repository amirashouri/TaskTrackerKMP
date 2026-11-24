package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    /**
     * A row draws two lines of the description — one more than the board card.
     *
     * Read off the real layout rather than the string, because semantics reports the whole
     * description either way: a row that printed all of it would pass a text assertion. The
     * fixed-width box makes the overflow a fact of this scenario, not of the host's viewport,
     * and the fixture is long enough that a third line would fit if the cap were raised.
     */
    val showsTwoLinesOfTheDescription: suspend ComposeUiTest.() -> Unit = {
        val description = "Reported on a cold install with no keychain entry, and it comes back " +
            "after every restart — which is the part that matters, and the part that has to be " +
            "cut off, because the row is two lines and the rest of it is a tap away from here"
        val item = ViewPreviewData.workItems
            .first { it.status == WorkItemStatus.Backlog }
            .copy(details = description)

        setContent {
            Themed {
                Box(modifier = Modifier.width(DESCRIPTION_SCENARIO_WIDTH)) {
                    WorkItemBacklogRow(
                        item = item,
                        onPutOnBoard = {},
                        onFileAway = {},
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        // Unmerged: the row is clickable, so its own node swallows the children's text.
        val action = onNodeWithTag(BacklogDescriptionTag(item), useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
        assertNotNull(action, "the row's description exposes no text layout, so its length is unreadable")
        action(layouts)

        val layout = layouts.single()
        assertEquals(
            2,
            layout.lineCount,
            "the description took ${layout.lineCount} lines, so one long item makes every row " +
                "in the list as tall as its text",
        )
        assertTrue(
            layout.hasVisualOverflow,
            "the fixture fits in one line, so this scenario proves nothing about truncation",
        )
    }

    /** An item with no details draws no line, rather than an empty gap under the title. */
    val omitsTheDescriptionWhenThereIsNone: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems
            .first { it.status == WorkItemStatus.Backlog }
            .copy(details = "")

        rowShowing(item = item, onCopy = null)

        onNodeWithTag(BacklogDescriptionTag(item), useUnmergedTree = true).assertDoesNotExist()
    }

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
     * The link action is offered for an unlinked item and disappears as soon as the item carries
     * an epic, which is what the row receives after a successful link reloads the backlog.
     */
    val theEpicLinkControlHidesOnceTheItemIsLinked: suspend ComposeUiTest.() -> Unit = {
        val epic = testEpic(listOf(ViewPreviewData.seededProject.id), title = "Ship it")
        val item = ViewPreviewData.workItems
            .first { it.status == WorkItemStatus.Backlog }
            .copy(epicID = null)
        var isLinked by mutableStateOf(false)

        setContent {
            Themed {
                WorkItemBacklogRow(
                    item = if (isLinked) item.copy(epicID = epic.id) else item,
                    onPutOnBoard = {},
                    onFileAway = {},
                    onLinkToEpic = { isLinked = true },
                )
            }
        }

        onNested(BacklogLinkEpicTag(item)).assertIsDisplayed()
        onNested(BacklogLinkEpicTag(item)).performClick()
        waitForIdle()

        onNested(BacklogLinkEpicTag(item)).assertDoesNotExist()
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

/** The width these row scenarios draw at, so a line break is a decision and not an accident. */
private val DESCRIPTION_SCENARIO_WIDTH = 300.dp

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
