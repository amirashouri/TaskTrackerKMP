package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Desktop local notifications through the AWT system tray.
 *
 * `TrayIcon.displayMessage` is the one OS toast the JDK exposes without a platform
 * dependency, and it is available on macOS, Windows and most Linux desktops. Where there is
 * no tray — headless CI, a bare X session — the notifier logs and does nothing: a "sent"
 * message that no OS accepted would be a lie, and tests must stay headless-safe.
 *
 * Scheduling is off the caller's coroutine. A future trigger must not park a sync apply for
 * minutes; each notification owns a job that sleeps until its time, and the map lets a
 * read/dismiss cancel it before it fires. Delivered toasts cannot be withdrawn through AWT,
 * so `cancel` only stops pending ones.
 */
actual fun createLocalNotifier(host: Any?): LocalNotifier = JvmLocalNotifier()

private class JvmLocalNotifier : LocalNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val trayIcon: TrayIcon? = createTrayIcon()

    override suspend fun schedule(notification: Notification, trigger: Trigger) {
        val waitMillis = when (trigger) {
            is Trigger.At -> trigger.instant.toEpochMilliseconds() - System.currentTimeMillis()
            is Trigger.In -> trigger.delay.inWholeMilliseconds
        }.coerceAtLeast(0)

        val id = notification.id.stringValue
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch {
            if (waitMillis > 0) delay(waitMillis)
            show(notification)
        }
    }

    override suspend fun cancel(notificationId: NotificationID) {
        jobs.remove(notificationId.stringValue)?.cancel()
    }

    override suspend fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    private fun show(notification: Notification) {
        val icon = trayIcon
        if (icon == null) {
            TTLogger.notifications.info {
                logMessage {
                    append("local-notifier: no system tray; skipped ")
                    append(notification.id.stringValue)
                }
            }
            return
        }
        runCatching {
            icon.displayMessage(
                notification.localTitle(),
                notification.localBody(),
                TrayIcon.MessageType.INFO,
            )
        }.onFailure { error ->
            TTLogger.notifications.warning {
                logMessage {
                    append("local-notifier: display failed: ")
                    append(error.message ?: "unknown error")
                }
            }
        }
    }
}

private fun createTrayIcon(): TrayIcon? = runCatching {
    if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return@runCatching null

    val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
        val graphics = createGraphics()
        graphics.color = Color(0x2F, 0x6F, 0xED)
        graphics.fillOval(1, 1, 14, 14)
        graphics.dispose()
    }
    TrayIcon(image, "TaskTracker").apply {
        isImageAutoSize = true
        SystemTray.getSystemTray().add(this)
    }
}.getOrNull()

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
