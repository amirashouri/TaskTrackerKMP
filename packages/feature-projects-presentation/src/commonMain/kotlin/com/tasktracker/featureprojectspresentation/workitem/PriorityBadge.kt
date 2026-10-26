package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectspresentation.statusLabel

/** Tinted priority badge pill featuring low-alpha wash fills and a distinguishing action dot. */
@Composable
fun PriorityBadge(
    priority: Priority,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(priorityFill(priority))
            .border(ttSeparatorWidth, priorityRim(priority), shape)
            .padding(horizontal = ttSpacing.sm, vertical = BADGE_PADDING_V)
            .testTag(PriorityBadgeTag(priority)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PriorityDot(filled = priority.actsNow)
        ttText(
            text = priorityLabel(priority),
            style = ttTypography.captionSmall,
            color = ttLabel(),
        )
    }
}

@Composable
private fun priorityFill(priority: Priority): Color = when (priority) {
    Priority.Critical -> ttError().copy(alpha = CRITICAL_FILL_ALPHA)
    Priority.High -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    Priority.Lowest, Priority.Low, Priority.Medium -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
}

@Composable
private fun priorityRim(priority: Priority): Color = when (priority) {
    Priority.Critical -> ttError()
    Priority.High -> ttAccent()
    Priority.Lowest, Priority.Low, Priority.Medium -> ttSeparator()
}

@Composable
private fun PriorityDot(
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(BADGE_DOT)
            .clip(shape)
            .background(if (filled) ttLabel() else Color.Transparent)
            .border(ttSeparatorWidth, ttLabelSecondary(), shape),
    )
}

private val Priority.actsNow: Boolean get() = this == Priority.Critical || this == Priority.High

fun PriorityBadgeTag(priority: Priority): String = "${PriorityBadgeTagPrefix}.${priority.wireValue}"
const val PriorityBadgeTagPrefix = "workItem.priorityBadge"

private val BADGE_PADDING_V: Dp = 2.dp
private val BADGE_DOT: Dp = 5.dp
private const val CRITICAL_FILL_ALPHA = 0.18f
private const val HIGH_FILL_ALPHA = 0.14f
private const val RESTING_FILL_ALPHA = 0.08f

/** Workflow status badge pill (Todo, InProgress, Done, etc.). */
@Composable
fun WorkItemStatusBadge(
    status: WorkItemStatus,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(statusFill(status))
            .border(ttSeparatorWidth, statusRim(status), shape)
            .padding(horizontal = ttSpacing.sm, vertical = BADGE_PADDING_V)
            .testTag(WorkItemStatusBadgeTag(status)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(filled = status.isTerminal)
        ttText(
            text = statusLabel(status),
            style = ttTypography.captionSmall,
            color = ttLabel(),
        )
    }
}

@Composable
private fun statusFill(status: WorkItemStatus): Color = when (status) {
    WorkItemStatus.Backlog -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
    WorkItemStatus.Todo -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    WorkItemStatus.InProgress -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    WorkItemStatus.InReview -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    WorkItemStatus.Done -> ttError().copy(alpha = CRITICAL_FILL_ALPHA)
    WorkItemStatus.Closed -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
}

@Composable
private fun statusRim(status: WorkItemStatus): Color = when (status) {
    WorkItemStatus.Backlog -> ttSeparator()
    WorkItemStatus.Todo -> ttAccent()
    WorkItemStatus.InProgress -> ttAccent()
    WorkItemStatus.InReview -> ttAccent()
    WorkItemStatus.Done -> ttError()
    WorkItemStatus.Closed -> ttSeparator()
}

@Composable
private fun StatusDot(
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(BADGE_DOT)
            .clip(shape)
            .background(if (filled) ttLabel() else Color.Transparent)
            .border(ttSeparatorWidth, ttLabelSecondary(), shape),
    )
}

fun WorkItemStatusBadgeTag(status: WorkItemStatus): String = "${WorkItemStatusBadgeTagPrefix}.${status.wireValue}"
const val WorkItemStatusBadgeTagPrefix = "workItem.statusBadge"