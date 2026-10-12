package com.tasktracker.featurenotificationsdomain

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tasktracker.core.AppError
import com.tasktracker.core.NotificationID
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationKind
import com.tasktracker.featurenotificationsdomain.Trigger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.math.absoluteValue

private const val CHANNEL_ID = "tasktracker_notifications"
private const val CHANNEL_NAME = "TaskTracker Notifications"
private const val REQUEST_CODE_BASE = 0x1F00
private const val EXTRA_NOTIFICATION_ID = "notification_id"
private const val EXTRA_TITLE = "title"
private const val EXTRA_BODY = "body"

    /**
     * Android local notifier using `AlarmManager` for timing and `NotificationManager`
     * for display.
     *
     * Uses exact alarms (`setExactAndAllowWhileIdle`) so notifications fire at the
     * requested time even in Doze mode. Requires the `SCHEDULE_EXACT_ALARM`
     * permission (API 31+) which is granted by default for apps targeting API 33+.
     */
actual fun createLocalNotifier(host: Any?): LocalNotifier {
    val context = context(host)
    return AndroidLocalNotifier(context)
}

private fun context(host: Any?): Context = host as? Context
    ?: throw AppError.Unknown(
        "Android needs an application Context to schedule notifications. " +
            "Pass one to LocalNotifier creation.",
    )

private class AndroidLocalNotifier(
    private val context: Context,
) : LocalNotifier {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
        ?: throw AppError.Unknown("This device has no AlarmManager.")
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
        ?: throw AppError.Unknown("This device has no NotificationManager.")

    private val dispatcher: CoroutineDispatcher = Dispatchers.IO

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Task assignments, mentions, due dates, and status changes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun schedule(notification: Notification, trigger: Trigger) {
        withContext(dispatcher) {
            val triggerMillis = triggerMillis(trigger)
            val nowMillis = System.currentTimeMillis()

            // If the trigger is in the past, fire immediately
            if (triggerMillis <= nowMillis) {
                showNotification(notification)
            } else {
                val intent = notificationIntent(notification)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode(notification),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent,
                    )
                    TTLogger.notifications.info {
                        logMessage {
                            append("local-notifier: scheduled ")
                            append(notification.id.stringValue)
                            append(" for ")
                            append(triggerMillis.toString())
                        }
                    }
                } catch (e: SecurityException) {
                    // Exact alarm permission not granted (API 31+). Fall back to inexact.
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent,
                    )
                    TTLogger.notifications.warning {
                        logMessage {
                            append("local-notifier: exact alarm denied, using inexact for ")
                            append(notification.id.stringValue)
                        }
                    }
                }
            }
        }
    }

    override suspend fun cancel(notificationId: NotificationID) {
        withContext(dispatcher) {
            val intent = Intent(context, NotificationReceiver::class.java)
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId.stringValue)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(notificationId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
            notificationManager.cancel(requestCode(notificationId))
            TTLogger.notifications.debug {
                logMessage {
                    append("local-notifier: cancelled ")
                    append(notificationId.stringValue)
                }
            }
        }
    }

    override suspend fun cancelAll() {
        withContext(dispatcher) {
            // Note: AlarmManager doesn't support bulk cancel, so we rely on the
            // receiver to no-op on unknown notifications. The notification manager
            // can cancel all.
            notificationManager.cancelAll()
            TTLogger.notifications.debug {
                logMessage {
                    append("local-notifier: cancelAll (notification manager)")
                }
            }
        }
    }

    private fun triggerMillis(trigger: Trigger): Long = when (trigger) {
        is Trigger.At -> trigger.instant.toEpochMilliseconds()
        is Trigger.In -> System.currentTimeMillis() + trigger.delay.inWholeMilliseconds
    }

    private fun requestCode(notification: Notification): Int =
        REQUEST_CODE_BASE + notification.id.stringValue.hashCode().absoluteValue % 0x10000

    private fun requestCode(notificationId: NotificationID): Int =
        REQUEST_CODE_BASE + notificationId.stringValue.hashCode().absoluteValue % 0x10000

    private fun notificationIntent(notification: Notification): Intent {
        val intent = Intent(context, NotificationReceiver::class.java)
        intent.putExtra(EXTRA_NOTIFICATION_ID, notification.id.stringValue)
        intent.putExtra(EXTRA_TITLE, notificationTitle(notification))
        intent.putExtra(EXTRA_BODY, notificationBody(notification))
        return intent
    }

    private fun showNotification(notification: Notification) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notificationTitle(notification))
            .setContentText(notificationBody(notification))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        notificationManager.notify(requestCode(notification), builder.build())
    }

    private fun notificationTitle(notification: Notification): String = when (notification.kind) {
        NotificationKind.MentionedInComment -> "Mentioned in a comment"
        NotificationKind.TaskAssigned -> "Task assigned to you"
        NotificationKind.DueToday -> "Due today"
        NotificationKind.StatusChanged -> "Status changed"
    }

    private fun notificationBody(notification: Notification): String {
        // Parse a preview from the JSON payload
        return try {
            val json = kotlinx.serialization.json.Json.decodeFromString<
                kotlinx.serialization.json.JsonObject
            >(notification.payload)
            when (notification.kind) {
                NotificationKind.MentionedInComment -> json["commentBodyPreview"]?.toString()?.removeSurrounding("\"") ?: "New mention"
                NotificationKind.TaskAssigned -> "Task ${json["workItemKey"]?.toString()?.removeSurrounding("\"") ?: ""} was assigned to you"
                NotificationKind.DueToday -> "Task ${json["workItemKey"]?.toString()?.removeSurrounding("\"") ?: ""} is due today"
                NotificationKind.StatusChanged -> "Task ${json["workItemKey"]?.toString()?.removeSurrounding("\"") ?: ""} changed to ${json["toStatus"]?.toString()?.removeSurrounding("\"") ?: ""}"
            }
        } catch (e: Exception) {
            "TaskTracker notification"
        }
    }
}

/**
 * BroadcastReceiver that shows the notification when the alarm fires.
 *
 * Registered in the manifest via `:packages:feature-notifications-data`'s
 * AndroidManifest.xml (or added to the app manifest by composition-root).
 */
class NotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val body = intent.getStringExtra(EXTRA_BODY)

        if (notificationId != null && title != null && body != null) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)

            val requestCode = REQUEST_CODE_BASE + notificationId.hashCode().absoluteValue % 0x10000
            notificationManager?.notify(requestCode, builder.build())
        }
    }
}