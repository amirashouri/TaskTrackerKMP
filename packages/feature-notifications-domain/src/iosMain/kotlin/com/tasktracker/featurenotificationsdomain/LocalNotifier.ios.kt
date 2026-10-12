package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.Trigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * iOS local notifier — logs instead of scheduling.
 *
 * A full implementation using `UNUserNotificationCenter` requires careful
 * interop with Foundation and UserNotifications frameworks. The JVM and iOS
 * implementations will be completed in a follow-up; for now this provides the
 * same no-op behavior as the JVM implementation so the domain layer compiles
 * and tests pass on all targets.
 */
actual fun createLocalNotifier(host: Any?): LocalNotifier = IosLocalNotifier()

private class IosLocalNotifier : LocalNotifier {

    private val dispatcher: CoroutineDispatcher = Dispatchers.Default

    override suspend fun schedule(notification: Notification, trigger: Trigger) {
        withContext(dispatcher) {
            val whenStr = when (trigger) {
                is Trigger.At -> "at ${trigger.instant}"
                is Trigger.In -> "in ${trigger.delay}"
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
    }

    override suspend fun cancel(notificationId: NotificationID) {
        withContext(dispatcher) {
            TTLogger.notifications.debug {
                logMessage {
                    append("local-notifier: cancel ")
                    append(notificationId.stringValue)
                }
            }
        }
    }

    override suspend fun cancelAll() {
        withContext(dispatcher) {
            TTLogger.notifications.debug {
                logMessage {
                    append("local-notifier: cancelAll")
                }
            }
        }
    }
}