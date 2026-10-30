package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.archive_reopen
import com.tasktracker.resources.backlog_put_on_board
import com.tasktracker.resources.sprint_remove_from_sprint
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_copy
import org.jetbrains.compose.resources.stringResource

/**
 * Full-width task row used across Backlog, Kanban, Archive, and Sprint views.
 * Action buttons adapt to context: Backlog (Put on Board, Archive), Kanban (Archive), Archive
 * (Reopen), Sprint (Remove).
 *
 * A terminal row (`Done` or `Closed`) never offers "File away" — it is already filed away, and
 * `ArchiveWorkItem` refuses the write. Where a reopen handler is supplied it offers Reopen
 * instead, so the only way out of the archive is the one that works.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkItemBacklogRow(
    item: WorkItem,
    onPutOnBoard: () -> Unit,
    onFileAway: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onRemoveFromSprint: (() -> Unit)? = null,
    /** Invoked with item summary for clipboard copy; null draws no copy control. */
    onCopy: ((String) -> Unit)? = null,
    /** Project tag definitions used to resolve [WorkItem.tagIDs] into icons. */
    projectTags: List<ProjectTag> = emptyList(),
    /** Displays workflow status badge; enabled strictly for Kanban section rows. */
    showStatusBadge: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .clickable(
                enabled = onOpen != null && !isBusy,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen ?: {},
            )
            .padding(ttSpacing.xl)
            .testTag(WorkItemBacklogRowTag(item)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            ) {
                ttText(
                    text = item.key.toString(),
                    style = ttTypography.key,
                    color = ttLabelSecondary(),
                )
                ttText(
                    text = item.title,
                    style = ttTypography.callout,
                    color = ttLabel(),
                    maxLines = BACKLOG_TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PriorityBadge(priority = item.priority)
            if (showStatusBadge) {
                WorkItemStatusBadge(status = item.status)
            }
        }
        item.dueDate?.let { due ->
            ttText(
                text = dueDateLabel(due),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
        WorkItemTagIcons(tags = projectTags.resolveFor(item.tagIDs))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (onRemoveFromSprint != null) {
                BacklogAction(
                    label = stringResource(Res.string.sprint_remove_from_sprint),
                    isBusy = isBusy,
                    onClick = onRemoveFromSprint,
                    testTag = BacklogRemoveFromSprintTag(item),
                )
            } else {
                if (item.status == WorkItemStatus.Backlog) {
                    BacklogAction(
                        label = stringResource(Res.string.backlog_put_on_board),
                        isBusy = isBusy,
                        onClick = onPutOnBoard,
                        testTag = BacklogPutOnBoardTag(item),
                    )
                }
                // Terminal items are already filed away, so "File away" would only ever be
                // refused; a done task's way back is Reopen, the same as a closed one's.
                if (item.status.isTerminal && onUnarchive != null) {
                    BacklogAction(
                        label = stringResource(Res.string.archive_reopen),
                        isBusy = isBusy,
                        onClick = onUnarchive,
                        testTag = BacklogUnarchiveTag(item),
                    )
                } else if (!item.status.isTerminal) {
                    BacklogAction(
                        label = stringResource(Res.string.work_item_archive),
                        isBusy = isBusy,
                        onClick = onFileAway,
                        testTag = BacklogFileAwayTag(item),
                    )
                }
            }
            // Copy stays active while busy: reads in-memory text without racing pending write mutations
            if (onCopy != null) {
                Spacer(Modifier.weight(1f))
                ttGlassIconButton(
                    image = ttActionIcons.copy,
                    contentDescription = stringResource(Res.string.work_item_copy),
                    onClick = { onCopy(clipboardText(item)) },
                    modifier = Modifier.testTag(BacklogCopyTag(item)),
                )
            }
        }
    }
}

@Composable
private fun BacklogAction(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    // ttGlassButton lacks an enabled parameter; clicks are swallowed when busy
    ttGlassButton(
        label = label,
        onClick = { if (!isBusy) onClick() },
        modifier = modifier.testTag(testTag),
        style = if (isBusy) TTGlassButtonStyle() else TTGlassButtonStyle(tint = ttAccent(), prominent = true),
    )
}

const val WorkItemBacklogRowTagPrefix = "backlog.row"

fun WorkItemBacklogRowTag(item: WorkItem): String = "$WorkItemBacklogRowTagPrefix.${item.key}"
fun BacklogPutOnBoardTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.putOnBoard"
fun BacklogFileAwayTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.fileAway"
fun BacklogUnarchiveTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.unarchive"
fun BacklogRemoveFromSprintTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.removeFromSprint"
fun BacklogCopyTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.copy"

private const val BACKLOG_TITLE_MAX_LINES = 2