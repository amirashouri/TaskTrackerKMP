package com.tasktracker.featurenotificationspresentation

import com.tasktracker.core.CommentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.parseCommentID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.wireValue
import kotlin.time.Instant

/** Where a notification points, once its payload has been resolved. */
data class NotificationTarget(
    val projectID: ProjectID,
    val workItemID: WorkItemID,
    val commentID: CommentID? = null,
)

/** The task a notification opens, or `null` when its payload is unusable. */
fun notificationTarget(notification: Notification): NotificationTarget? {
    val payload = com.tasktracker.featurenotificationsdomain.decodeNotificationPayload(notification.payload)
        ?: return null
    val projectID = parseProjectID(payload.projectId) ?: return null
    val workItemID = parseWorkItemID(payload.workItemId) ?: return null
    return NotificationTarget(
        projectID = projectID,
        workItemID = workItemID,
        commentID = payload.commentId?.let(::parseCommentID),
    )
}

/**
 * A compact age label: "Just now", "12 min", "5 h", "3 d", then the ISO date.
 *
 * Deliberately arithmetic rather than a formatter: the inbox is a relative feed, and the only
 * case that needs a calendar is older than a week, where the stored ISO string's date half is
 * exactly the label a reader wants. This keeps the feature free of a date-formatting
 * dependency on every target.
 */
internal fun notificationAge(createdAt: Instant, now: Instant): NotificationAge {
    val seconds = (now - createdAt).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> NotificationAge.JustNow
        seconds < 3_600 -> NotificationAge.Minutes((seconds / 60).toInt())
        seconds < 86_400 -> NotificationAge.Hours((seconds / 3_600).toInt())
        seconds < 604_800 -> NotificationAge.Days((seconds / 86_400).toInt())
        else -> NotificationAge.Date(createdAt.toString().take(10))
    }
}

/** The age label's shape, rendered with strings in the card. */
internal sealed interface NotificationAge {
    data object JustNow : NotificationAge
    data class Minutes(val value: Int) : NotificationAge
    data class Hours(val value: Int) : NotificationAge
    data class Days(val value: Int) : NotificationAge
    data class Date(val isoDate: String) : NotificationAge
}

/** The one-line description: a brief note of the task, or of the comment. */
internal fun notificationDescription(notification: Notification): String {
    val payload = com.tasktracker.featurenotificationsdomain.decodeNotificationPayload(notification.payload)
        ?: return ""
    val preview = payload.commentPreview?.takeIf { it.isNotBlank() }
    return when {
        preview != null -> preview
        payload.taskTitle.isNotBlank() -> payload.taskTitle
        else -> payload.projectName
    }
}
