package com.tasktracker.featurenotificationspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationKind
import com.tasktracker.resources.Res
import com.tasktracker.resources.notification_comment_added
import com.tasktracker.resources.notification_generic
import com.tasktracker.resources.notification_task_assigned
import com.tasktracker.resources.notifications_search_close
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The in-app arrival popup.
 *
 * Tapping it opens the inbox and flashes the row; dragging it up dismisses it. It is stateless
 * about time: the shell owns the three-second timer, because the timer survives this composable
 * being re-keyed by the next arrival and must be cancelled when the app leaves the foreground.
 * The drag is a local offset only — a dismissal threshold, not a physics simulation — so the
 * popup never steals a tap meant for its own open action.
 */
@Composable
fun NotificationArrivalBanner(
    notification: Notification,
    onOpen: (Notification) -> Unit,
    onDismiss: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragY by remember(notification.id) { mutableFloatStateOf(0f) }
    val accent = ttAccent()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NotificationArrivalTag)
            .alpha((1f - (dragY / DISMISS_DRAG_PX)).coerceIn(0.2f, 1f))
            .offset { IntOffset(x = 0, y = dragY.roundToInt()) }
            .pointerInput(notification.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragY < -DISMISS_DRAG_PX) onDismiss(notification)
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                ) { _, amount ->
                    // Upward only: a downward drag is not a request to close, and letting it
                    // move the card down would read as a sheet being pulled over the page.
                    dragY = (dragY + amount).coerceAtMost(0f)
                }
            }
            .ttGlassCard()
            .clickable { onOpen(notification) }
            .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttIcon(
            image = ttTabIcons.notifications,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(ttSpacing.iconButtonSize),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            ttText(
                text = arrivalTitle(notification.kind),
                style = ttTypography.subheadlineSelected,
                color = ttLabel(),
            )
            val description = notificationDescription(notification)
            if (description.isNotBlank()) {
                ttText(
                    text = description,
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    maxLines = 2,
                )
            }
        }
        ttGlassIconButton(
            image = ttActionIcons.close,
            contentDescription = stringResource(Res.string.notifications_search_close),
            onClick = { onDismiss(notification) },
            modifier = Modifier.testTag(NotificationArrivalDismissTag),
        )
    }
}

@Composable
private fun arrivalTitle(kind: NotificationKind): String = when (kind) {
    NotificationKind.TaskAssigned -> stringResource(Res.string.notification_task_assigned)
    NotificationKind.CommentAdded -> stringResource(Res.string.notification_comment_added)
    NotificationKind.MentionedInComment,
    NotificationKind.DueToday,
    NotificationKind.StatusChanged,
    -> stringResource(Res.string.notification_generic)
}

const val NotificationArrivalTag = "inbox.arrival"
const val NotificationArrivalDismissTag = "inbox.arrival.dismiss"

/** How far up the popup must be dragged before it dismisses. */
private const val DISMISS_DRAG_PX = 96f
