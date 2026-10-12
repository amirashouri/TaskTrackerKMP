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
 * The stored shape of an `Attachment`: one file's index entry in a work item's list.
 *
 * ## Follows [CommentRecord] where the reasons are the same
 *
 * - **No foreign key.** The package rule: a delete has to write an outbox row per child,
 *   and a DB-level cascade cannot write into `outbox`. `workItemID` is a plain indexed
 *   string; the repository deletes a work item's attachments in the same transaction that
 *   deletes the item. Attachments are even *more* committed to this than comments —
 *   there is a blob on disk to remove per row, and SQL cannot remove a file.
 * - **A monotonic [sequence] with a unique [id] index.** Same tie-break story as the
 *   thread: two attachments stored inside one clock tick need an order that is total
 *   and defined rather than inherited from SQLite's row order.
 * - **ABORT inserts.** A duplicate id is a bug, handled by an explicit exists-check on
 *   the write path — there is no pull path to re-deliver here, which makes a conflict
 *   even more certainly a genuine defect.
 * - **No `projectID` column.** Read per work item, deleted per work item; a column with
 *   no query does not earn its place. A project delete collects the project's work items
 *   first and drops each list by its item id, exactly like the thread.
 *
 * ## What is not stored
 *
 * The file itself. [blobPath] names a file under the attachments root; the bytes live
 * on disk (see `FileBlobStore`), never in a column — a TEXT column would serialise them
 * into JSON the first time the outbox touched the row, which is exactly the transport
 * that must not exist.
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["workItemID"]), Index(value = ["id"], unique = true)],
)
data class AttachmentRecord(
    /** Insertion order; the tie-break the domain's `createdAt` cannot provide. See above. */
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val workItemID: String,
    /**
     * The name the file was picked under. Display only — the blob's path is [blobPath]
     * and never derives from this, so a hostile or duplicate name is a rendering
     * question, not a write one.
     */
    val fileName: String,
    /** Best-effort, from the picker; `null` when the platform could not say. */
    val mimeType: String?,
    val sizeBytes: Long,
    /**
     * Path of the stored blob, relative to the attachments root — never absolute, so a
     * store relocated between installs or machines stays readable.
     */
    val blobPath: String,
    val createdAt: Instant,
)

/**
 * Reads and writes attachment lists.
 *
 * One DAO owns the `attachments` table, per the package rule — including the deletes
 * the repository performs while tearing down a work item or a project.
 */
@Dao
interface AttachmentDao {

    /**
     * One item's attachments, oldest first, insertion order breaking a `createdAt` tie.
     *
     * The two-column sort is the contract in [AttachmentRecord.sequence] expressed as
     * SQL — the mirror of [CommentDao.byWorkItem], because a list of files reads like a
     * thread even though nothing replies to anything. Sorting again in Kotlin would be
     * a second answer to the same question.
     */
    @Query("SELECT * FROM attachments WHERE workItemID = :workItemID ORDER BY createdAt ASC, sequence ASC")
    suspend fun byWorkItem(workItemID: String): List<AttachmentRecord>

    /** For the write path's idempotency checks and for tests asserting on storage itself. */
    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byID(id: String): AttachmentRecord?

    @Query("DELETE FROM attachments WHERE workItemID = :workItemID")
    suspend fun deleteByWorkItem(workItemID: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteByID(id: String)

    /**
     * Appends, never replaces.
     *
     * ABORT on the unique `id` index: a conflict means the same attachment id was
     * written twice, which is a genuine defect this surface must report rather than
     * silently repair — more so than a comment, because the blob would silently replace
     * the wrong file while the row pointed elsewhere.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: AttachmentRecord)
}
