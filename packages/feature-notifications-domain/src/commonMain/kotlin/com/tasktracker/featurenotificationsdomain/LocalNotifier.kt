package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The platform half of local notifications.
 *
 * Deliberately minimal: the domain decides *when* and *what* to notify; the
 * platform decides *how* to deliver it. This keeps the scheduling logic
 * testable in common code while each platform contributes only the thing it
 * alone can do — put a notification on the user's screen.
 *
 * The shape mirrors [ConnectivityWatcher]: an interface with `attach`/`start`/`stop`
 * would be overkill because notifications are fire-and-forget, not a stream.
 */
interface LocalNotifier {

    /**
     * Schedules a local notification for [notification] at [trigger].
     *
     * If a notification with the same id is already scheduled, it is replaced.
     * The platform decides how to handle the trigger — exact alarm on Android,
     * time-interval trigger on iOS, no-op on JVM.
     */
    suspend fun schedule(notification: Notification, trigger: Trigger)

    /**
     * Cancels a scheduled notification by its id.
     *
     * Idempotent: cancelling an unknown id is a no-op.
     */
    suspend fun cancel(notificationId: NotificationID)

    /**
     * Cancels all scheduled notifications.
     *
     * Used on sign-out to clear the slate.
     */
    suspend fun cancelAll()
}

/**
 * When to fire the notification.
 */
sealed interface Trigger {
    /** Fire at a specific [instant]. */
    data class At(val instant: Instant) : Trigger

    /** Fire after a [delay] from now. */
    data class In(val delay: Duration) : Trigger
}

/**
 * The platform's local notifier.
 *
 * @param host Android's application `Context`, ignored elsewhere. A missing context
 *   is reported by name, the same way `:packages:database` reports a missing one.
 */
expect fun createLocalNotifier(host: Any?): LocalNotifier