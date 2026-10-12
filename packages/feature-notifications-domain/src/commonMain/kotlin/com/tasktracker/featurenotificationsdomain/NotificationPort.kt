package com.tasktracker.featurenotificationsdomain

import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The port through which the notifications feature reads and writes its data.
 *
 * Declared in the domain layer and implemented in the data layer, so the
 * dependency points inwards. Presentation depends on this interface and never
 * on the Room implementation.
 *
 * Two things are deliberate about the shape:
 *
 * 1. **Every value crossing this interface is immutable.** A Room entity is
 *    mutable and the `ModelContext` equivalent explicitly refuses to be shared,
 *    so a record can never appear in a signature here. Domain values in, domain
 *    values out.
 * 2. **Writes return the value they produced.** A caller that creates a
 *    notification gets the stored version back rather than having to re-read.
 *
 * `now` is a parameter on every write rather than something the implementation
 * reads from a clock. A store that calls `Instant.now()` itself is a store whose
 * tests depend on wall-clock time, and a store that takes the instant from its
 * caller can be exercised with a fixed one.
 */
interface NotificationPort {

    // MARK: Reads

    /**
     * All notifications for [userID], newest first.
     *
     * Newest first because that is the order the inbox shows them in.
     */
    suspend fun notifications(userID: UserID): List<Notification>

    /**
     * Unread count for [userID].
     *
     * Used for the inbox badge on the tab bar.
     */
    suspend fun unreadCount(userID: UserID): Int

    /**
     * Emits once after every committed write — local edits and pulled remote
     * changes alike.
     *
     * A cold [Flow], with one subscriber per collection. Events that arrive
     * before a caller starts collecting are not replayed.
     */
    fun changes(userID: UserID): Flow<NotificationChange>

    // MARK: Writes

    /**
     * Stores one notification and queues its outbox row **in the same
     * transaction**, returning the stored value.
     *
     * The transaction is the whole rule from this port's KDoc applied to a new
     * entity: the notification write and its queued upsert commit together or
     * not at all.
     */
    suspend fun createNotification(
        userID: UserID,
        kind: NotificationKind,
        payload: String,
        now: Instant,
    ): Notification

    /**
     * Marks a notification as read (or unread).
     */
    suspend fun setRead(id: NotificationID, isRead: Boolean, now: Instant): Notification

    /**
     * Marks all notifications for [userID] as read.
     */
    suspend fun markAllRead(userID: UserID, now: Instant): Int

    /**
     * Deletes all notifications for [userID].
     *
     * Called on sign-out so the inbox does not leak data between accounts.
     */
    suspend fun deleteForUser(userID: UserID): Int

    /**
     * Deletes notifications older than [cutoff].
     *
     * Retention policy hook — the inbox is not an infinite archive.
     */
    suspend fun deleteOlderThan(cutoff: Instant): Int
}

/**
 * What changed, so a subscriber can decide whether it cares.
 */
data class NotificationChange(
    val kind: Kind,
    val userID: UserID,
) {
    enum class Kind {
        Notifications,
    }
}