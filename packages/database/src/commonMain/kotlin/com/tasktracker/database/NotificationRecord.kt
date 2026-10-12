package com.tasktracker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlin.time.Instant

/**
 * The stored shape of a `Notification`.
 *
 * ## Follows [CommentRecord] where the reasons are the same
 *
 * - **No foreign key.** The package rule: a delete has to write an outbox row
 *   per child, and a DB-level cascade cannot write into `outbox`. `userID` is
 *   a plain indexed string; the repository deletes a user's notifications in
 *   the same transaction that clears their session.
 * - **A monotonic [sequence] with a unique [id] index.** `createdAt` is epoch
 *   milliseconds, so two notifications inside one clock tick need a tie-break
 *   that is total and defined rather than inherited from SQLite's row order.
 * - **ABORT inserts.** A duplicate id is a bug (a re-delivered pull is handled
 *   by an explicit exists-check on the write path, not by silently replacing a
 *   row).
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["userID"]),
        Index(value = ["id"], unique = true),
        Index(value = ["createdAt"]),
    ],
)
data class NotificationRecord(
    /** Insertion order; the tie-break the domain's `createdAt` cannot provide. */
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val userID: String,
    val kind: String,
    val payload: String,
    val isRead: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Reads and writes notifications.
 *
 * One DAO owns the `notifications` table, per the package rule.
 */
@Dao
interface NotificationDao {

    /**
     * All notifications for a user, newest first.
     *
     * Newest first because that is the order the inbox shows them in.
     */
    @Query("SELECT * FROM notifications WHERE userID = :userID ORDER BY createdAt DESC, sequence DESC")
    suspend fun byUser(userID: String): List<NotificationRecord>

    /**
     * Unread count for a user.
     */
    @Query("SELECT COUNT(*) FROM notifications WHERE userID = :userID AND isRead = 0")
    suspend fun unreadCount(userID: String): Int

    /**
     * Single notification by ID. For the pull path's exists-check and tests.
     */
    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun byID(id: String): NotificationRecord?

    /**
     * Appends, never replaces.
     *
     * ABORT on the unique `id` index: a conflict means the same notification
     * id was queued twice, which the outbox protocol has to surface rather
     * than silently repair. The pull path checks [byID] before inserting.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: NotificationRecord)

    /**
     * Updates the read flag.
     */
    @Query("UPDATE notifications SET isRead = :isRead, updatedAt = :now WHERE id = :id")
    suspend fun setRead(id: String, isRead: Boolean, now: Instant)

    /**
     * Marks all notifications for a user as read.
     */
    @Query("UPDATE notifications SET isRead = 1, updatedAt = :now WHERE userID = :userID AND isRead = 0")
    suspend fun markAllRead(userID: String, now: Instant): Int

    /**
     * Deletes all notifications for a user.
     */
    @Query("DELETE FROM notifications WHERE userID = :userID")
    suspend fun deleteByUser(userID: String): Int

    /**
     * Deletes notifications older than the cutoff.
     */
    @Query("DELETE FROM notifications WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Instant): Int

    /** Raw rows in insertion order. For tests and debugging. */
    @Query("SELECT * FROM notifications ORDER BY sequence ASC")
    suspend fun all(): List<NotificationRecord>
}