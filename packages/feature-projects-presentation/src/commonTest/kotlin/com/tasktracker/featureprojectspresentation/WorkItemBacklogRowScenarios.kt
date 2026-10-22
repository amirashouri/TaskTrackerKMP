package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.core.randomTagID
import com.tasktracker.domain.ProjectTag
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
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.rowShowing(
    item: WorkItem,
    onCopy: ((String) -> Unit)?,
    onOpen: (() -> Unit)? = null,
    projectTags: List<ProjectTag> = emptyList(),
) {
    setContent {
        Themed {
            WorkItemBacklogRow(
                item = item,
                onPutOnBoard = {},
                onFileAway = {},
                onOpen = onOpen,
                onCopy = onCopy,
                projectTags = projectTags,
            )
        }
    }
}
