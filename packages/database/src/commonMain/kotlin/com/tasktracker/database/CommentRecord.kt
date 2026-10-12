package com.tasktracker.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlin.time.Instant

/**
 * The stored shape of a `Comment`: one entry in a work item's thread.
 *
 * ## Follows [StatusChangeRecord] where the reasons are the same, and not where they differ
 *
 * The same, for the same reasons:
 *
 * - **No foreign key.** The package rule: a delete has to write an outbox row per child,
 *   and a DB-level cascade cannot write into `outbox`. `workItemID` is a plain indexed
 *   string; the repository deletes a work item's comments in the same transaction that
 *   deletes the item.
 * - **A monotonic [sequence] with a unique [id] index.** `createdAt` is epoch
 *   milliseconds, so two comments inside one clock tick — the normal case for a fast
 *   reply — need a tie-break that is total and defined rather than inherited from
 *   SQLite's row order. Same lesson as the history table, one column in and one
 *   declared rather than borrowed for the same reason.
 * - **ABORT inserts.** A duplicate id is a bug (a re-delivered pull is handled by an
 *   explicit exists-check on the write path, not by silently replacing a row).
 *
 * Different, and deliberately so: no `projectID` column. The thread is read per work
 * item and deleted per work item — a project-scoped read does not exist, and a column
 * with no query does not earn its place. The one project-scoped operation, a project
 * delete, collects the project's work items first and drops each thread by its item id,
 * which is two queries in one transaction rather than a denormalised column kept honest
 * by every future write.
 *
 * A comment is **immutable** here: no edit use case exists yet, so the upsert path on a
 * pull inserts only when the row is absent. When editing lands, this becomes an
 * `apply(comment)` like [WorkItemRecord]'s — not before.
 */
@Entity(
    tableName = "comments",
    indices = [Index(value = ["workItemID"]), Index(value = ["id"], unique = true)],
)
data class CommentRecord(
    /** Insertion order; the tie-break the domain's `createdAt` cannot provide. See above. */
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val workItemID: String,
    val authorID: String,
    val body: String,
    val createdAt: Instant,
    /** A plain string, like every other cross-table reference in this module. */
    val parentID: String?,
)

/**
 * Reads and writes comment threads.
 *
 * One DAO owns the `comments` table, per the package rule — including the deletes the
 * repository performs while tearing down a work item or a project.
 */
@Dao
interface CommentDao {

    /**
     * One item's thread, oldest first, insertion order breaking a `createdAt` tie.
     *
     * The two-column sort is the contract in [CommentRecord.sequence] expressed as SQL —
     * the mirror of [StatusChangeDao.byWorkItem], which sorts the same two columns in
     * the opposite direction for the opposite reading (a history is a stack, a thread is
     * a conversation). Sorting again in Kotlin would be a second answer to the same
     * question.
     */
    @Query("SELECT * FROM comments WHERE workItemID = :workItemID ORDER BY createdAt ASC, sequence ASC")
    suspend fun byWorkItem(workItemID: String): List<CommentRecord>

    /** For the pull path's exists-check and for tests asserting on storage itself. */
    @Query("SELECT * FROM comments WHERE id = :id")
    suspend fun byID(id: String): CommentRecord?

    /** Raw rows in insertion order. */
    @Query("SELECT * FROM comments ORDER BY sequence ASC")
    suspend fun all(): List<CommentRecord>

    @Query("DELETE FROM comments WHERE workItemID = :workItemID")
    suspend fun deleteByWorkItem(workItemID: String)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteByID(id: String)

    /**
     * Appends, never replaces.
     *
     * ABORT on the unique `id` index: a conflict means the same comment id was queued
     * twice, which the outbox protocol has to surface rather than silently repair —
     * and the pull path checks [byID] before inserting, so a re-delivery never lands here.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CommentRecord)
}
