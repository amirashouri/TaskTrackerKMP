package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationOptions
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.math.max
import kotlin.time.Clock

/**
 * iOS local notifications through `UNUserNotificationCenter`.
 *
 * Authorization is requested on the first schedule rather than at launch: the prompt is
 * explained by the notification the user just received, and a user who never receives one is
 * never asked. iOS remembers the answer, so this is a one-time prompt.
 *
 * Both trigger forms become a time-interval trigger, because that is the only trigger a local
 * notification can use without a calendar component. iOS requires at least one second, so an
 * immediate notification is scheduled one second out rather than rejected.
 */
actual fun createLocalNotifier(host: Any?): LocalNotifier = IosLocalNotifier()

private class IosLocalNotifier : LocalNotifier {

    // Lazy: `currentNotificationCenter()` throws when the process has no bundle (a test
    // binary), and the graph constructs this notifier even when no notification is sent.
    private val center by lazy { UNUserNotificationCenter.currentNotificationCenter() }

    override suspend fun schedule(notification: Notification, trigger: Trigger) {
        withContext(Dispatchers.Main) {
            val seconds = secondsUntil(trigger)

            // The platform bindings expose the mutable content's title/body as read-only
            // properties inherited from UNNotificationContent, so they are set through the
            // Objective-C setters rather than Kotlin property assignment.
            val content = UNMutableNotificationContent().apply {
                setTitle(notification.localTitle())
                setBody(notification.localBody())
                setSound(UNNotificationSound.defaultSound)
            }
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = notification.id.stringValue,
                content = content,
                trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                    timeInterval = seconds,
                    repeats = false,
                ),
            )

            val options: UNAuthorizationOptions =
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound
            center.requestAuthorizationWithOptions(options) { granted, error ->
                if (error != null) {
                    TTLogger.notifications.warning {
                        logMessage {
                            append("local-notifier: authorization failed: ")
                            append(error.localizedDescription)
                        }
                    }
                    return@requestAuthorizationWithOptions
                }
                if (!granted) {
                    TTLogger.notifications.info {
                        logMessage { append("local-notifier: notifications not authorized") }
                    }
                    return@requestAuthorizationWithOptions
                }
                center.addNotificationRequest(request) { addError ->
                    if (addError != null) {
                        TTLogger.notifications.warning {
                            logMessage {
                                append("local-notifier: could not add request: ")
                                append(addError.localizedDescription)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun cancel(notificationId: NotificationID) {
        withContext(Dispatchers.Main) {
            val identifiers = listOf(notificationId.stringValue)
            center.removePendingNotificationRequestsWithIdentifiers(identifiers)
            center.removeDeliveredNotificationsWithIdentifiers(identifiers)
        }
    }

    override suspend fun cancelAll() {
        withContext(Dispatchers.Main) {
            center.removeAllPendingNotificationRequests()
            center.removeAllDeliveredNotifications()
        }
    }

    private fun secondsUntil(trigger: Trigger): Double {
        val millis = when (trigger) {
            is Trigger.At -> trigger.instant.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()
            is Trigger.In -> trigger.delay.inWholeMilliseconds
        }
        // iOS rejects a zero/negative interval; one second is the shortest local notification.
        return max(1.0, millis / 1_000.0)
    }
}

private fun Notification.localTitle(): String = when (kind) {
    NotificationKind.TaskAssigned -> "You have been assigned"
    NotificationKind.CommentAdded -> "New comment"
    NotificationKind.MentionedInComment -> "Mentioned in a comment"
    NotificationKind.DueToday -> "Due today"
    NotificationKind.StatusChanged -> "Status changed"
}

private fun Notification.localBody(): String {
    val payload = decodeNotificationPayload(payload)
    return payload?.commentPreview?.takeIf { it.isNotBlank() }
        ?: payload?.taskTitle?.takeIf { it.isNotBlank() }
        ?: "TaskTracker notification"
}
