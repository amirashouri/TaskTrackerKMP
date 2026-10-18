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
import com.tasktracker.resources.Res
import com.tasktracker.resources.attachment_add
import com.tasktracker.resources.attachment_empty
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

/**
 * What the list can honestly say about one file's journey to the server.
 *
 * Five states rather than the two this slice started with, because "no blob ref" conflates
 * three different situations and the difference is the whole point of the feature. A file
 * that is queued, a file whose bytes are moving, and a file the server refused all have no
 * ref — and a row that called all three "uploading…" would be telling two lies. The fifth,
 * [LocalOnly], exists because a graph with no server is a supported build: there is nothing
 * to wait for there, and "waiting" would leave a spinner on a screen that has no network.
 *
 * The model decides the state; this file only gives the states names a user can read.
 */
enum class AttachmentSyncState {
    /** The bytes are on the server, so every member of the project can fetch them. */
    Synced,

    /** An upload is in flight right now. */
    Uploading,

    /** Nothing on the server yet and nothing in flight: the next refresh retries. */
    Waiting,

    /** Refused for a reason a retry cannot fix — too large, or the bytes are gone locally. */
    Failed,

    /** This build has no file API, so the file stays on this device. */
    LocalOnly,
}

/**
 * One work item's attachments: the files, plus the button to add one.
 *
 * ## A listing that opens, but only where it can
 *
 * Rows show the picked name, the formatted size and — when the caller has a server — how far
 * the file has got towards it. [onOpen] turns the name into a doorway: pressing it fetches
 * the bytes if this device does not hold them and hands them to the platform. It is nullable,
 * and `null` means this caller has no opener, so the row is not pressable at all. A row that
 * looks tappable and opens nothing is the mock this repo refuses; the absence is the honest
 * rendering of an absent platform seam.
 *
 * ## Sync state is optional, and null is not "synced"
 *
 * [syncStateOf] is null when the caller has no sync information to give — a preview, or a
 * build with no file API. A null then renders no state at all rather than a default of
 * "Synced", because a default would put a checkmark next to a file that never left the
 * device. This mirrors the editor's own convention for [attachments]: the caller that has
 * nothing to say passes nothing, and the section says nothing.
 *
 * ## Why opening is not a sync state
 *
 * [isOpening] is separate from [syncStateOf] because the two answer different questions:
 * sync state is a durable fact about where the file is, and opening is a few seconds of
 * activity while its bytes are fetched. Folding "opening" into the sync enum would put a
 * transient activity in a slot that otherwise never lies about the file's location. The list
 * still renders both in one place — the caption slot — because two captions per row is worse.
 */
@Composable
fun AttachmentList(
    attachments: List<Attachment>,
    onAttachClick: () -> Unit,
    onRemove: (AttachmentID) -> Unit,
    syncStateOf: ((Attachment) -> AttachmentSyncState)? = null,
    onOpen: ((AttachmentID) -> Unit)? = null,
    isOpening: ((Attachment) -> Boolean)? = null,
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
        if (attachments.isEmpty()) {
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
                    // The size rides with the name: a listing that needs a second look
                    // to see how big a file is has hidden the one fact that decides
                    // whether touching it is cheap. The formatter is file-local below —
                    // it is this view's contract, not a shared token, and two callers
                    // must never be able to disagree about the same number.
                    //
                    // The name is the press target rather than the whole row: a full-width
                    // tap area would sit under the Remove action and turn a near-miss into a
                    // file opening instead of a file being removed.
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
                // One caption slot, two sources: an open in flight wins because it is the
                // thing that just happened, and the sync state comes back when it ends.
                val opening = isOpening?.invoke(attachment) == true
                if (opening) {
                    ttText(
                        text = stringResource(Res.string.attachment_state_opening),
                        style = ttTypography.captionEmphasised,
                        color = ttLabelSecondary(),
                        modifier = Modifier.testTag(WorkItemAttachmentStateTag(attachment.id)),
                    )
                } else {
                    // The state rides beside the name for the same reason the size does: it
                    // is a fact about this file, not about the list, and a column of it reads
                    // at a glance. Only `Failed` takes the error colour — the other four are
                    // progress, and painting "waiting" red would call a normal queue a fault.
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
    }
}



/**
 * The words for one state. A `when` with no `else`, so adding a state to the enum is a
 * compile error here rather than a row that silently renders nothing.
 */
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

/**
 * The structural tags this component renders, keyed by role so a test can assert on the
 * list, the empty state, the add button and each row without reaching into its
 * internals. The row tags carry [Attachment.id] because several lists on screen can hold
 * attachments for different work items at once — a tag without an id cannot name a row.
 */
const val WorkItemAttachmentsTag = "workItem.editor.attachments"
const val WorkItemAttachmentsEmptyTag = "workItem.editor.attachments.empty"
const val WorkItemAttachmentAddTag = "workItem.editor.attachments.add"

fun WorkItemAttachmentTag(id: AttachmentID): String = "workItem.editor.attachment.$id"

fun WorkItemAttachmentRemoveTag(id: AttachmentID): String = "workItem.editor.attachment.remove.$id"

fun WorkItemAttachmentStateTag(id: AttachmentID): String = "workItem.editor.attachment.state.$id"

/**
 * The press target that opens a file, which is the name rather than the row.
 *
 * A separate tag from [WorkItemAttachmentTag] so a test can tell "this row is on screen"
 * from "this row can be opened" — the difference the nullable `onOpen` exists to express.
 */
fun WorkItemAttachmentOpenTag(id: AttachmentID): String = "workItem.editor.attachment.open.$id"

/**
 * Bytes to a one-decimal read, in this view's own words.
 *
 * One decimal, integer arithmetic, no locale: a size that renders "2.3 MB" on the JVM
 * and "2,3 MB" on iOS is two answers to the same question, and a size whose text the
 * scenario cannot assert on byte-exactly is a measurement that cannot fail. The
 * boundaries are powers of 1024, not 1000 — bytes are addressed, not counted.
 */
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
