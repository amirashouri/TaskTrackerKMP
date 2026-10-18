package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.Attachment
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * What an attachment row is allowed to promise.
 *
 * ## The rule these exist for
 *
 * A row is pressable **only when the caller can open something**. `onOpen` is nullable, and
 * `null` must render a row that is not a press target at all — because a row that looks
 * tappable and does nothing is the mock this feature has refused at every step. That is a
 * rendering fact a model test cannot see, which is why it is a scenario.
 *
 * The second rule is the caption slot: an open in flight shows "Opening…" and the sync state
 * comes back when it ends. One slot, because two captions per row would be a column of
 * competing statuses; and the transient one wins because it is what just happened.
 *
 * Each value is a `suspend ComposeUiTest.() -> Unit` so a scenario reads as the interaction it
 * performs. See `WorkItemCardScenarios` for why these are shared values with per-target entry
 * points.
 */
@OptIn(ExperimentalTestApi::class)
object AttachmentRowScenarios {

    /** No opener means no doorway: the row is on screen, the press target is not. */
    val aRowWithNoOpenerIsNotPressable: suspend ComposeUiTest.() -> Unit = {
        val attachment = aFile()

        setContent { listShowing(attachment, onOpen = null) }

        onNodeWithTag(WorkItemAttachmentTag(attachment.id)).assertIsDisplayed()
        onNodeWithTag(WorkItemAttachmentOpenTag(attachment.id)).assertDoesNotExist()
    }

    /** With an opener, pressing the name names the file it would open. */
    val pressingTheNameAsksToOpenThatFile: suspend ComposeUiTest.() -> Unit = {
        val attachment = aFile()
        var opened: AttachmentID? = null

        setContent { listShowing(attachment, onOpen = { opened = it }) }

        onNodeWithTag(WorkItemAttachmentOpenTag(attachment.id)).performClick()

        assertEquals(attachment.id, opened)
    }

    /** An open in flight owns the caption slot, and gives it back. */
    val anOpenInFlightReplacesTheStateCaption: suspend ComposeUiTest.() -> Unit = {
        val attachment = aFile()

        setContent {
            listShowing(
                attachment,
                onOpen = {},
                syncStateOf = { AttachmentSyncState.Synced },
                isOpening = { true },
            )
        }

        onNodeWithText("Opening…").assertIsDisplayed()
        onNodeWithText("Synced").assertDoesNotExist()
    }

    /** The same row, not opening: the durable state is what shows. */
    val aRowThatIsNotOpeningShowsItsSyncState: suspend ComposeUiTest.() -> Unit = {
        val attachment = aFile()

        setContent {
            listShowing(
                attachment,
                onOpen = {},
                syncStateOf = { AttachmentSyncState.Synced },
                isOpening = { false },
            )
        }

        onNodeWithText("Synced").assertIsDisplayed()
        onNodeWithText("Opening…").assertDoesNotExist()
    }

    private fun aFile() = Attachment(
        id = randomAttachmentID(),
        workItemID = ViewPreviewData.workItems.first().id,
        fileName = "report.txt",
        mimeType = "text/plain",
        sizeBytes = 3L,
        createdAt = Instant.fromEpochSeconds(1_700_000_000),
    )

    @Composable
    private fun listShowing(
        attachment: Attachment,
        onOpen: ((AttachmentID) -> Unit)?,
        syncStateOf: ((Attachment) -> AttachmentSyncState)? = null,
        isOpening: ((Attachment) -> Boolean)? = null,
    ) {
        ttTheme(ThemeScheme.Light) {
            AttachmentList(
                attachments = listOf(attachment),
                onAttachClick = {},
                onRemove = {},
                syncStateOf = syncStateOf,
                onOpen = onOpen,
                isOpening = isOpening,
            )
        }
    }
}
