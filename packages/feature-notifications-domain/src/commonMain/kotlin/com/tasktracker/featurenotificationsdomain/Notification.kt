package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import com.tasktracker.core.randomNotificationID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A notification in the user's inbox.
 *
 * The [kind] determines the UI template and the action taken when tapped.
 * The [payload] carries the contextual data needed for that action as JSON,
 * so the entity schema stays stable while the payload can evolve per kind.
 *
 * Notifications are scoped to a [userID] so the inbox is per-account and
 * cleared on sign-out (see [NotificationPort.deleteForUser]).
 */
@Serializable
data class Notification(
    val id: NotificationID = randomNotificationID(),
    /** The account this notification belongs to. */
    val userID: UserID,
    /** What kind of event produced this notification. */
    val kind: NotificationKind,
    /**
     * Structured data for the notification, serialized as JSON.
     *
     * Per-kind schema (non-exhaustive, evolvable):
     * - [NotificationKind.MentionedInComment]: `{ workItemID, workItemKey, projectID, projectKeyPrefix, commentID, commentBodyPreview, authorName }`
     * - [NotificationKind.TaskAssigned]: `{ workItemID, workItemKey, projectID, projectKeyPrefix, assignedByName }`
     * - [NotificationKind.DueToday]: `{ workItemID, workItemKey, projectID, projectKeyPrefix, dueDate }`
     * - [NotificationKind.StatusChanged]: `{ workItemID, workItemKey, projectID, projectKeyPrefix, fromStatus, toStatus, changedByName }`
     */
    val payload: String,
    /** Whether the user has seen this notification. */
    var isRead: Boolean = false,
    val createdAt: Instant,
    var updatedAt: Instant,
)

/**
 * The type of event that generated a notification.
 *
 * Each kind maps to a distinct row template in the inbox and a distinct
 * deep-link target when tapped. Adding a kind requires:
 * 1. A case here
 * 2. A payload schema definition in [Notification]'s KDoc
 * 3. A row composable in `feature-notifications-presentation`
 * 4. A deep-link route in `routing`
 * 5. A test in `NotificationKindTest`
 */
@Serializable
enum class NotificationKind {
    @SerialName("mentioned_in_comment")
    MentionedInComment,

    @SerialName("task_assigned")
    TaskAssigned,

    @SerialName("due_today")
    DueToday,

    @SerialName("status_changed")
    StatusChanged;

    companion object {
        /** Parses a [SerialName], or returns `null`. */
        fun fromWireValue(raw: String): NotificationKind? =
            entries.firstOrNull { it.wireValue == raw }

        /** The stable machine key for storage and the wire. */
        val all: List<NotificationKind> = entries.toList()
    }
}

val NotificationKind.wireValue: String
    get() = when (this) {
        NotificationKind.MentionedInComment -> "mentioned_in_comment"
        NotificationKind.TaskAssigned -> "task_assigned"
        NotificationKind.DueToday -> "due_today"
        NotificationKind.StatusChanged -> "status_changed"
    }