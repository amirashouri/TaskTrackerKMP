package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The decisions a view makes before anything reaches the store.
 *
 * Every declaration here is `suspend`, and Kotlin/Native **rejects** `@Throws`
 * on a suspend function unless `CancellationException` is among the listed
 * classes. The second class is also the correct contract: a cancelled screen
 * is not a failed save.
 */

/**
 * Loads all notifications for a user, newest first.
 */
class LoadNotifications(
    private val repository: NotificationPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(userID: UserID): List<Notification> =
        repository.notifications(userID)
}

/**
 * Gets the unread count for a user.
 */
class GetUnreadCount(
    private val repository: NotificationPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(userID: UserID): Int =
        repository.unreadCount(userID)
}

/**
 * Creates a notification for a user.
 *
 * This is the primary write path for notifications. Called from:
 * - `AddComment` use case (for mentions)
 * - `SaveWorkItem` use case (for task assigned, due today, status changed)
 * - Sync sink (for pulled notifications from server)
 */
class CreateNotification(
    private val repository: NotificationPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        userID: UserID,
        kind: NotificationKind,
        payload: String,
    ): Notification {
        return repository.createNotification(
            userID = userID,
            kind = kind,
            payload = payload,
            now = dateProvider.now(),
        )
    }
}

/**
 * Marks a notification as read or unread.
 */
class SetNotificationRead(
    private val repository: NotificationPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        id: NotificationID,
        isRead: Boolean,
    ): Notification {
        return repository.setRead(
            id = id,
            isRead = isRead,
            now = dateProvider.now(),
        )
    }
}

/**
 * Marks all notifications for a user as read.
 */
class MarkAllNotificationsRead(
    private val repository: NotificationPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(userID: UserID): Int {
        return repository.markAllRead(
            userID = userID,
            now = dateProvider.now(),
        )
    }
}

/**
 * Deletes all notifications for a user.
 *
 * Called on sign-out.
 */
class ClearNotificationsForUser(
    private val repository: NotificationPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(userID: UserID): Int =
        repository.deleteForUser(userID)
}

/**
 * Deletes notifications older than the cutoff.
 *
 * Retention policy: notifications older than 90 days are purged.
 */
class PurgeOldNotifications(
    private val repository: NotificationPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(): Int {
        // 90 days retention
        val cutoff = dateProvider.now() - 90.days
        return repository.deleteOlderThan(cutoff)
    }
}