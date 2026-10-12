package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.Trigger
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * JVM local notifier — no-op that logs instead of scheduling.
 *
 * There is no portable way to show a desktop notification from a JVM process
 * without pulling in a native library. Logging is the honest answer for a
 * platform that cannot know, and it is what the test suite expects: a wrong
 * "notification sent" would make tests pass that should fail.
 */
actual fun createLocalNotifier(host: Any?): LocalNotifier = JvmLocalNotifier()

private class JvmLocalNotifier : LocalNotifier {

    override suspend fun schedule(notification: Notification, trigger: Trigger) {
        val whenStr = when (trigger) {
            is Trigger.At -> "at ${trigger.instant}"
            is Trigger.In -> "in ${trigger.delay}"
            // Exhaustive: Trigger is sealed with only At and In
        }
        TTLogger.notifications.info {
            logMessage {
                append("local-notifier: schedule ")
                append(notification.id.stringValue)
                append(" (")
                append(notification.kind.wireValue)
                append(") ")
                append(whenStr)
            }
        }
    }

    override suspend fun cancel(notificationId: NotificationID) {
        TTLogger.notifications.debug {
            logMessage {
                append("local-notifier: cancel ")
                append(notificationId.stringValue)
            }
        }
    }

    override suspend fun cancelAll() {
        TTLogger.notifications.debug {
            logMessage {
                append("local-notifier: cancelAll")
            }
        }
    }
}