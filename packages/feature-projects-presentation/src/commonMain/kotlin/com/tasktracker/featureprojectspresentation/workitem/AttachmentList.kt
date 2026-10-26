package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.AttachmentID
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Attachment
import com.tasktracker.featureprojectsdomain.PickedFile
import com.tasktracker.resources.Res
import com.tasktracker.resources.attachment_add
import com.tasktracker.resources.attachment_empty
import com.tasktracker.resources.attachment_pending
import com.tasktracker.resources.attachment_remove
import com.tasktracker.resources.attachment_section_label
import com.tasktracker.resources.attachment_state_failed
import com.tasktracker.resources.attachment_state_local_only
import com.tasktracker.resources.attachment_state_opening
import com.tasktracker.resources.attachment_state_synced
import com.tasktracker.resources.attachment_state_uploading
import com.tasktracker.resources.attachment_state_waiting
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Lifecycle synchronization state for file attachments. */
enum class AttachmentSyncState {
    Synced,
    Uploading,
    Waiting,
    Failed,
    LocalOnly,
}

/**
 * Renders attached files with sync state indicators, file opening actions, and pending pre-save uploads.
 */
@Composable
fun AttachmentList(
    attachments: List<Attachment>,
    onAttachClick: () -> Unit,
    onRemove: (AttachmentID) -> Unit,
    syncStateOf: ((Attachment) -> AttachmentSyncState)? = null,
    onOpen: ((AttachmentID) -> Unit)? = null,
    isOpening: ((Attachment) -> Boolean)? = null,
    /** Files picked during item creation, held locally until item is saved. */
    pendingAttachments: List<PickedFile> = emptyList(),
    onRemovePending: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkItemAttachmentsTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.attachment_section_label),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
                modifier = Modifier.weight(1f),
            )
            ttGlassButton(
                label = stringResource(Res.string.attachment_add),
                onClick = onAttachClick,
                modifier = Modifier.testTag(WorkItemAttachmentAddTag),
            )
        }
        if (attachments.isEmpty() && pendingAttachments.isEmpty()) {
            ttText(
                text = stringResource(Res.string.attachment_empty),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(WorkItemAttachmentsEmptyTag),
            )
        }
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkItemAttachmentTag(attachment.id)),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val name = "${attachment.fileName} · ${formatAttachmentSize(attachment.sizeBytes)}"
                val open = onOpen
                ttText(
                    text = name,
                    style = ttTypography.callout,
                    color = ttLabel(),
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (open == null) {
                                Modifier
                            } else {
                                Modifier
                                    .testTag(WorkItemAttachmentOpenTag(attachment.id))
                                    .clickable(role = Role.Button) { open(attachment.id) }
                            },
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val opening = isOpening?.invoke(attachment) == true
                if (opening) {
                    ttText(
                        text = stringResource(Res.string.attachment_state_opening),
                        style = ttTypography.captionEmphasised,
                        color = ttLabelSecondary(),
                        modifier = Modifier.testTag(WorkItemAttachmentStateTag(attachment.id)),
                    )
                } else {
                    syncStateOf?.let { stateOf ->
                        val state = stateOf(attachment)
                        ttText(
                            text = state.label(),
                            style = ttTypography.captionEmphasised,
                            color = if (state == AttachmentSyncState.Failed) {
                                ttError()
                            } else {
                                ttLabelSecondary()
                            },
                            modifier = Modifier.testTag(WorkItemAttachmentStateTag(attachment.id)),
                        )
                    }
                }
                ttText(
                    text = stringResource(Res.string.attachment_remove),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier
                        .testTag(WorkItemAttachmentRemoveTag(attachment.id))
                        .clickable(role = Role.Button) { onRemove(attachment.id) },
                )
            }
        }
        pendingAttachments.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkItemPendingAttachmentTag(index)),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ttText(
                    text = "${file.fileName} · ${formatAttachmentSize(file.bytes.size.toLong())}",
                    style = ttTypography.callout,
                    color = ttLabel(),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ttText(
                    text = stringResource(Res.string.attachment_pending),
                    style = ttTypography.captionEmphasised,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(WorkItemPendingAttachmentStateTag(index)),
                )
                ttText(
                    text = stringResource(Res.string.attachment_remove),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier
                        .testTag(WorkItemPendingAttachmentRemoveTag(index))
                        .clickable(role = Role.Button) { onRemovePending(index) },
                )
            }
        }
    }
}

/** Exhaustive when expression ensures compilation errors if new sync states are added. */
@Composable
private fun AttachmentSyncState.label(): String = stringResource(
    when (this) {
        AttachmentSyncState.Synced -> Res.string.attachment_state_synced
        AttachmentSyncState.Uploading -> Res.string.attachment_state_uploading
        AttachmentSyncState.Waiting -> Res.string.attachment_state_waiting
        AttachmentSyncState.Failed -> Res.string.attachment_state_failed
        AttachmentSyncState.LocalOnly -> Res.string.attachment_state_local_only
    },
)

const val WorkItemAttachmentsTag = "workItem.editor.attachments"
const val WorkItemAttachmentsEmptyTag = "workItem.editor.attachments.empty"
const val WorkItemAttachmentAddTag = "workItem.editor.attachments.add"

fun WorkItemAttachmentTag(id: AttachmentID): String = "workItem.editor.attachment.$id"
fun WorkItemAttachmentRemoveTag(id: AttachmentID): String = "workItem.editor.attachment.remove.$id"
fun WorkItemAttachmentStateTag(id: AttachmentID): String = "workItem.editor.attachment.state.$id"
fun WorkItemAttachmentOpenTag(id: AttachmentID): String = "workItem.editor.attachment.open.$id"
fun WorkItemPendingAttachmentTag(index: Int): String = "workItem.editor.attachment.pending.$index"
fun WorkItemPendingAttachmentRemoveTag(index: Int): String = "workItem.editor.attachment.pending.remove.$index"
fun WorkItemPendingAttachmentStateTag(index: Int): String = "workItem.editor.attachment.pending.state.$index"

/** Formats byte sizes into compact units (B, KB, MB, GB) using 1024 byte boundaries. */
internal fun formatAttachmentSize(sizeBytes: Long): String = when {
    sizeBytes < 0 -> "0 B"
    sizeBytes < 1024L -> "$sizeBytes B"
    sizeBytes < 1024L * 1024L -> trimOneDecimal(sizeBytes, 1024L) + " KB"
    sizeBytes < 1024L * 1024L * 1024L -> trimOneDecimal(sizeBytes, 1024L * 1024L) + " MB"
    else -> trimOneDecimal(sizeBytes, 1024L * 1024L * 1024L) + " GB"
}

private fun trimOneDecimal(sizeBytes: Long, unit: Long): String {
    val tenths = (sizeBytes * 10L) / unit
    return "${tenths / 10}.${tenths % 10}"
}