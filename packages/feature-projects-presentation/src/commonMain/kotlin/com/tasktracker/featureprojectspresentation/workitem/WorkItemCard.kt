package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_copy
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Summary card representing a work item on the board with priority, assignee, due date, and copy actions.
 */
@Composable
fun WorkItemCard(
    item: WorkItem,
    onClick: () -> Unit,
    /** Dispatches formatted card summary text to clipboard. */
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** Resolved display name of assignee, or null if unassigned. */
    assigneeName: String? = null,
    /** Resolved keys of tasks blocking this item. */
    blockedByKeys: List<WorkItemKey> = emptyList(),
    /** Project tag catalog used to resolve tag IDs into icons. */
    projectTags: List<ProjectTag> = emptyList(),
) {
    Column(
        modifier = modifier
            .testTag(WorkItemCardTag(item))
            .clip(RoundedCornerShape(ttRadius.surface))
            .ttGlassCard()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(ttSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = item.key.toString(),
                style = ttTypography.key,
                color = ttLabelSecondary(),
                modifier = Modifier.weight(1f),
            )
            PriorityBadge(priority = item.priority)
        }
        ttText(
            text = item.title,
            style = if (isDelivered(item)) {
                ttTypography.callout.copy(textDecoration = TextDecoration.LineThrough)
            } else {
                ttTypography.callout
            },
            color = ttLabel(),
            maxLines = CARD_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        assigneeName?.takeIf { it.isNotBlank() }?.let { assignee ->
            AssigneeLabel(firstName = assigneeFirstName(assignee))
        }
        WorkItemCardDependencies(blockerKeys = blockedByKeys)
        WorkItemTagIcons(
            tags = projectTags.resolveFor(item.tagIDs),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.dueDate?.let { due ->
                DueDateChip(
                    dueDate = due,
                    isOverdue = isOverdue(item, due),
                )
            }
            Spacer(Modifier.weight(1f))
            ttGlassIconButton(
                image = ttActionIcons.copy,
                contentDescription = stringResource(Res.string.work_item_copy),
                onClick = { onCopy(clipboardText(item)) },
                modifier = Modifier.testTag(WorkItemCardCopyTag),
            )
            trailing?.invoke()
        }
    }
}

/** Formats work item title and trimmed details for clipboard export. */
internal fun clipboardText(item: WorkItem): String {
    val details = item.details.trim()
    return if (details.isEmpty()) item.title else "${item.title}\n\n$details"
}

@Composable
private fun AssigneeLabel(
    firstName: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Transparent)
            .border(ttSeparatorWidth, ttSeparator(), shape)
            .padding(horizontal = ttSpacing.sm, vertical = CHIP_PADDING_V)
            .testTag(WorkItemCardAssigneeTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = firstName,
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
        )
    }
}

/** Extracts first word of assignee name for compact chip rendering. */
internal fun assigneeFirstName(assignee: String): String = assignee.trim().substringBefore(' ')

/** True if item status represents successful completion ([WorkItemStatus.isComplete]). */
internal fun isDelivered(item: WorkItem): Boolean = item.status.isComplete

@Composable
private fun DueDateChip(
    dueDate: Instant,
    isOverdue: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isOverdue) ttLabel() else ttLabelSecondary()
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (isOverdue) ttLabelSecondary().copy(alpha = OVERDUE_FILL_ALPHA) else Color.Transparent)
            .border(ttSeparatorWidth, if (isOverdue) ttLabelSecondary() else ttSeparator(), shape)
            .padding(horizontal = ttSpacing.sm, vertical = CHIP_PADDING_V)
            .testTag(WorkItemCardDueTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = dueDateLabel(dueDate),
            style = ttTypography.captionSmall,
            color = tint,
        )
    }
}

/** True if item due date has passed and item is not yet completed. */
internal fun isOverdue(item: WorkItem, now: Instant): Boolean {
    val due = item.dueDate ?: return false
    return !item.status.isComplete && due < now
}

@Composable
internal fun dueDateLabel(dueDate: Instant): String {
    val local = dueDate.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.day} ${local.month.name.take(MONTH_ABBREVIATION_LENGTH)}"
}

const val WorkItemCardTagPrefix = "workItem.card"
const val WorkItemCardDueTag = "workItem.card.dueDate"
const val WorkItemCardAssigneeTag = "workItem.card.assignee"
const val WorkItemCardCopyTag = "workItem.card.copy"

fun WorkItemCardTag(item: WorkItem): String = "$WorkItemCardTagPrefix.${item.key}"

private const val CARD_TITLE_MAX_LINES = 3
private const val MONTH_ABBREVIATION_LENGTH = 3
private const val OVERDUE_FILL_ALPHA = 0.16f
private val CHIP_PADDING_V = 2.dp