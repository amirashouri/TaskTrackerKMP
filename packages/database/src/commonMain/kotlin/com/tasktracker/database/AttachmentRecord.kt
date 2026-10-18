package com.tasktracker.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
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
 * - **ABORT inserts, explicit `update` for the sync paths.** A duplicate id is a bug, so
 *   an insert never silently replaces a row. The two paths that legitimately change an
 *   existing row — caching downloaded bytes, and recording an upload — call [AttachmentDao.update]
 *   with a full record instead, so they cannot create a second row by accident.
 * - **A denormalised `projectID` column.** The server needs `project_id` to scope the file
 *   API by membership, and the sink needs it to announce the owning project, so it is
 *   carried rather than resolved from `workItemID` on every write.
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
     * Denormalised owner, the mirror of [CommentRecord]'s reasoning with one extra reason:
     * the sink needs it to announce the change, and the server requires `project_id` to
     * scope the file API by membership — so it is carried rather than resolved from
     * `workItemID` on every write. Read per work item on the way in; written per row.
     */
    val projectID: String,
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
    /**
     * Server key for the uploaded bytes, or `null` until they have been pushed. The client
     * never derives a path from this; it is the file API's handle, surfaced to the UI as
     * "synced / visible to teammates" once set.
     */
    val blobRef: String?,
    /**
     * When the bytes were pushed, or `null` while local-only / still uploading. A flag, not
     * a cache: the bytes themselves live at [blobPath] until re-downloaded on another device.
     */
    val uploadedAt: Instant?,
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

    /**
     * Replaces an existing row, matched on its primary key.
     *
     * The two legitimate callers both start from a [byID] read and change only fields the
     * local cache owns (`blobPath`, `sizeBytes`) or the upload reports (`blobRef`,
     * `uploadedAt`), so a full-record update is the safe shape: a caller that forgot to
     * carry a field would write a default over real data, which is why the read-then-copy
     * pattern is the documented one.
     */
    @Update
    suspend fun update(record: AttachmentRecord)
}
