package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * A task's urgency, as a tinted pill.
 *
 * ## Why the fill is a wash rather than the colour
 *
 * The badge is the only place in a card where a colour carries meaning, so it is the only
 * place a colour has to survive being small, adjacent, and repeated. The fill is a
 * *low-alpha* wash, which keeps the label's contrast governed by [ttLabel] against a
 * near-canvas background instead of by the priority colour against white - a solid red pill
 * with white text is what made the original's badges unreadable in dark mode.
 *
 * ## Why the top two are not merely "more saturated"
 *
 * `Critical` and `High` are the levels a user acts on, and they are the two that get a
 * filled pill; the rest are tinted outlines. That is a second, redundant channel, carried
 * by fill weight rather than hue, so the difference survives greyscale and colour vision
 * that cannot separate two of the five. It is also why this is an exhaustive `when` over
 * five cases rather than a threshold such as `priority >= High`: a threshold reads as
 * though `Critical` were simply "more High", which is exactly the collapse the strings file
 * was written to prevent.
 */
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

/** The wash behind the pill. Low alpha on purpose - see the KDoc. */
@Composable
private fun priorityFill(priority: Priority): Color = when (priority) {
    Priority.Critical -> ttError().copy(alpha = CRITICAL_FILL_ALPHA)
    Priority.High -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    Priority.Lowest, Priority.Low, Priority.Medium -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
}

/** The hairline. The two acted-on levels take the colour; the rest take a separator. */
@Composable
private fun priorityRim(priority: Priority): Color = when (priority) {
    Priority.Critical -> ttError()
    Priority.High -> ttAccent()
    Priority.Lowest, Priority.Low, Priority.Medium -> ttSeparator()
}

/**
 * The dot that makes the filled levels distinguishable without reading the text.
 *
 * Hollow for the levels that need no action, solid for the two that do - the same
 * redundant channel the fill weight carries, expressed as a shape so it does not depend on
 * hue at all.
 */
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

/** The two levels that warrant interrupting somebody about. */
private val Priority.actsNow: Boolean get() = this == Priority.Critical || this == Priority.High

/**
 * The badge's tag, suffixed with the priority's own wire name.
 *
 * `workItem.priorityBadge.critical` rather than a bare `workItem.priorityBadge`: a card
 * carries exactly one badge, so the unsuffixed form would be unambiguous - but the
 * suffixed form also lets a test assert *which* level a card is showing, which is the part
 * worth pinning. [Priority.wireValue] is used rather than `name` because it is the
 * lower-case token the API and the persisted rows use, so a tag cannot disagree with the
 * data.
 */
fun PriorityBadgeTag(priority: Priority): String = "${PriorityBadgeTagPrefix}.${priority.wireValue}"

const val PriorityBadgeTagPrefix = "workItem.priorityBadge"

private val BADGE_PADDING_V: Dp = 2.dp
private val BADGE_DOT: Dp = 5.dp
private const val CRITICAL_FILL_ALPHA = 0.18f
private const val HIGH_FILL_ALPHA = 0.14f
private const val RESTING_FILL_ALPHA = 0.08f


/**
 * A work item's workflow status, as a tinted pill matching [PriorityBadge] styling.
 *
 * Only shown for items on the board (Kanban section) — backlog items are triaged, not
 * worked, and closed items are already resolved. The badge uses the same low-alpha wash
 * and hairline rim as [PriorityBadge], with a solid dot for the two terminal states
 * [WorkItemStatus.isComplete] and [WorkItemStatus.isTerminal && !isComplete] (Done/Closed).
 */
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

/** The wash behind the pill. Low alpha on purpose - matches PriorityBadge. */
@Composable
private fun statusFill(status: WorkItemStatus): Color = when (status) {
    WorkItemStatus.Backlog -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
    WorkItemStatus.Todo -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    WorkItemStatus.InProgress -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    WorkItemStatus.InReview -> ttAccent().copy(alpha = HIGH_FILL_ALPHA)
    WorkItemStatus.Done -> ttError().copy(alpha = CRITICAL_FILL_ALPHA)
    WorkItemStatus.Closed -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
}

/** The hairline rim. Active workflow states take the accent; terminal/backlog take separator. */
@Composable
private fun statusRim(status: WorkItemStatus): Color = when (status) {
    WorkItemStatus.Backlog -> ttSeparator()
    WorkItemStatus.Todo -> ttAccent()
    WorkItemStatus.InProgress -> ttAccent()
    WorkItemStatus.InReview -> ttAccent()
    WorkItemStatus.Done -> ttError()
    WorkItemStatus.Closed -> ttSeparator()
}

/**
 * The dot that makes terminal states distinguishable without reading the text.
 *
 * Solid for Done/Closed (terminal), hollow for active workflow states - the same
 * redundant channel the fill weight carries, expressed as a shape.
 */
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

/** Tag for testing, suffixed with the status wire value. */
fun WorkItemStatusBadgeTag(status: WorkItemStatus): String = "${WorkItemStatusBadgeTagPrefix}.${status.wireValue}"

const val WorkItemStatusBadgeTagPrefix = "workItem.statusBadge"
