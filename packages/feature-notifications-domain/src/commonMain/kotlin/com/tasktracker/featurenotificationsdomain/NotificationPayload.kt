package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.parseCommentID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseWorkItemID
import kotlinx.serialization.Serializable

/**
 * The JSON document the server puts in a notification's `payload` string.
 *
 * Both server kinds carry the task and project context; a comment additionally
 * carries the comment id and a one-line preview. Every field is defaulted so the
 * same type decodes an assignment (which omits the comment fields) and a comment
 * (which includes them), and the server may add a key without breaking this
 * decoder — the payload is a free-form string on the wire and is deliberately
 * not part of the strict top-level contract.
 */
@Serializable
data class NotificationPayload(
    val workItemId: String = "",
    val workItemKey: String = "",
    val taskTitle: String = "",
    val projectId: String = "",
    val projectKeyPrefix: String = "",
    val projectName: String = "",
    val actorName: String = "",
    val commentId: String? = null,
    val commentPreview: String? = null,
)

/** Decodes a stored payload, or `null` for a malformed or older document. */
fun decodeNotificationPayload(raw: String): NotificationPayload? =
    runCatching { JSONCoding.json.decodeFromString(NotificationPayload.serializer(), raw) }.getOrNull()

/** The task this notification points at, resolved from its payload. */
val Notification.taskID
    get() = decodeNotificationPayload(payload)?.workItemId?.let(::parseWorkItemID)

/** The project this notification is scoped to. */
val Notification.projectID
    get() = decodeNotificationPayload(payload)?.projectId?.let(::parseProjectID)

/** The comment to focus when this notification is opened, for a comment notification. */
val Notification.commentID
    get() = decodeNotificationPayload(payload)?.commentId?.let(::parseCommentID)
