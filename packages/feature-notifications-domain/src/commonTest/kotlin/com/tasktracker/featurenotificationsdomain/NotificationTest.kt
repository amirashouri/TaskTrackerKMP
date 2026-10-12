package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import com.tasktracker.core.randomNotificationID
import com.tasktracker.core.randomUserID
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class NotificationTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)
    private val userID = randomUserID()
    private val notificationID = randomNotificationID()

    @Test
    fun notificationSerializesRoundTrip() {
        val notification = Notification(
            id = notificationID,
            userID = userID,
            kind = NotificationKind.MentionedInComment,
            payload = """{"workItemID":"wi-1","commentBodyPreview":"Hello @alice"}""",
            isRead = false,
            createdAt = now,
            updatedAt = now,
        )

        val json = Json.encodeToString(Notification.serializer(), notification)
        val decoded = Json.decodeFromString(Notification.serializer(), json)

        assertEquals(notification, decoded)
    }

    @Test
    fun notificationKindWireValues() {
        assertEquals("mentioned_in_comment", NotificationKind.MentionedInComment.wireValue)
        assertEquals("task_assigned", NotificationKind.TaskAssigned.wireValue)
        assertEquals("due_today", NotificationKind.DueToday.wireValue)
        assertEquals("status_changed", NotificationKind.StatusChanged.wireValue)
    }

    @Test
    fun notificationKindFromWireValue() {
        assertEquals(NotificationKind.MentionedInComment, NotificationKind.fromWireValue("mentioned_in_comment"))
        assertEquals(NotificationKind.TaskAssigned, NotificationKind.fromWireValue("task_assigned"))
        assertEquals(NotificationKind.DueToday, NotificationKind.fromWireValue("due_today"))
        assertEquals(NotificationKind.StatusChanged, NotificationKind.fromWireValue("status_changed"))
        assertNull(NotificationKind.fromWireValue("unknown"))
    }
}