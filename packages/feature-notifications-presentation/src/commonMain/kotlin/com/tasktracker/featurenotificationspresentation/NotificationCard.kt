package com.tasktracker.featurenotificationspresentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationKind
import com.tasktracker.resources.Res
import com.tasktracker.resources.notification_comment_added
import com.tasktracker.resources.notification_days_ago
import com.tasktracker.resources.notification_generic
import com.tasktracker.resources.notification_hours_ago
import com.tasktracker.resources.notification_just_now
import com.tasktracker.resources.notification_minutes_ago
import com.tasktracker.resources.notification_task_assigned
import com.tasktracker.resources.notifications_delete
import com.tasktracker.resources.notifications_mark_read
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * One inbox row.
 *
 * Two actions because the requirement names two: mark read, and delete. The whole card opens
 * the task (or, for a comment, the thread); the action row sits inside it and its own buttons
 * consume the tap, so opening and acting never conflict. In multi-select the card's tap means
 * "toggle" instead, and the per-row actions are withdrawn because the bulk bar owns them.
 */
@Composable
fun NotificationCard(
    notification: Notification,
    now: Instant,
    selectionMode: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onOpen: (Notification) -> Unit,
    onToggleSelected: (Notification) -> Unit,
    onMarkRead: (Notification) -> Unit,
    onDelete: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = ttAccent()
    val shape = RoundedCornerShape(ttRadius.surface)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NotificationCardTag(notification.id.stringValue))
            .ttGlassCard()
            .then(
                if (isSelected || isHighlighted) {
                    Modifier.border(
                        width = if (isHighlighted) 2.dp else 1.dp,
                        color = accent,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable {
                if (selectionMode) onToggleSelected(notification) else onOpen(notification)
            }
            .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        if (selectionMode) {
            ttIcon(
                image = ttActionIcons.select,
                contentDescription = null,
                tint = if (isSelected) accent else ttLabelSecondary(),
                modifier = Modifier.size(ttSpacing.md),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
                ttText(
                    text = notificationTitle(notification.kind),
                    style = ttTypography.subheadlineSelected,
                    color = ttLabel(),
                    modifier = Modifier.weight(1f),
                )
                if (!notification.isRead) {
                    // A dot, not a count: one row has only one read state, and the number of
                    // unread rows is already in the header.
                    ttIcon(
                        image = ttActionIcons.add,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .size(ttIconSize.close)
                            .testTag(NotificationUnreadDotTag),
                    )
                }
            }
            val description = notificationDescription(notification)
            if (description.isNotBlank()) {
                ttText(
                    text = description,
                    style = ttTypography.callout,
                    color = ttLabel(),
                    maxLines = 2,
                )
            }
            ttText(
                text = notificationAgeLabel(notificationAge(notification.createdAt, now)),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
            if (!selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!notification.isRead) {
                        ttGlassButton(
                            label = stringResource(Res.string.notifications_mark_read),
                            onClick = { onMarkRead(notification) },
                            modifier = Modifier.testTag(NotificationMarkReadTag),
                            style = TTGlassButtonStyle(),
                        )
                    }
                    ttGlassIconButton(
                        image = ttActionIcons.trash,
                        contentDescription = stringResource(Res.string.notifications_delete),
                        onClick = { onDelete(notification) },
                        modifier = Modifier.testTag(NotificationDeleteTag),
                        tint = ttError(),
                    )
                }
            }
        }
    }
}

@Composable
private fun notificationTitle(kind: NotificationKind): String = when (kind) {
    NotificationKind.TaskAssigned -> stringResource(Res.string.notification_task_assigned)
    NotificationKind.CommentAdded -> stringResource(Res.string.notification_comment_added)
    NotificationKind.MentionedInComment,
    NotificationKind.DueToday,
    NotificationKind.StatusChanged,
    -> stringResource(Res.string.notification_generic)
}

@Composable
private fun notificationAgeLabel(age: NotificationAge): String = when (age) {
    NotificationAge.JustNow -> stringResource(Res.string.notification_just_now)
    is NotificationAge.Minutes -> stringResource(Res.string.notification_minutes_ago, age.value)
    is NotificationAge.Hours -> stringResource(Res.string.notification_hours_ago, age.value)
    is NotificationAge.Days -> stringResource(Res.string.notification_days_ago, age.value)
    is NotificationAge.Date -> age.isoDate
}

fun NotificationCardTag(id: String): String = "inbox.card.$id"

const val NotificationMarkReadTag = "inbox.card.markRead"
const val NotificationDeleteTag = "inbox.card.delete"
const val NotificationUnreadDotTag = "inbox.card.unread"
