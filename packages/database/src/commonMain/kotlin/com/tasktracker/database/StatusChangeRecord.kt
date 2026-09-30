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
 * The stored shape of a `StatusChange`: one recorded move of a work item between columns.
 *
 * ## Append-only, and deliberately not related to [WorkItemRecord]
 *
 * Two reasons, and both matter.
 *
 * 1. **The cascade has to be explicit anyway.** [WorkItemRecord] holds `projectID` as a plain
 *    string precisely because the delete still has to be written as a row per child; a
 *    relationship would save nothing and would remove the children before those rows could
 *    be queued.
 * 2. **A history row must never reattach itself to a reused id.** A dangling history row is
 *    unreachable *and* actively dangerous: the moment anything reused that work item id, the
 *    old history would appear under it, claiming moves that item's predecessor made. This is
 *    why a delete removes the history in the same transaction that removes the item.
 *
 * `fromRaw` and `changedByUserID` are both optional *strings* rather than enums or ids for
 * the reason [WorkItemRecord.statusRaw] is: a value this build cannot read must cost one
 * row, not the whole history.
 */
@Entity(
    tableName = "status_changes",
    // Two indexes rather than one composite: the two deletes and the one read are each
    // scoped to a *single* column, so `(workItemID, projectID)` would only ever be used as
    // a prefix scan and `projectID` would need a second index anyway.
    indices = [Index(value = ["workItemID"]), Index(value = ["projectID"]), Index(value = ["id"], unique = true)],
)
data class StatusChangeRecord(
    /**
     * Insertion order, and the reason this table has two columns where the domain has one.
     *
     * The domain's `StatusChange` has a random `StatusChangeID` uuid, and a history is read
     * **newest first with the later-recorded change winning a tie** — "newest" being the
     * instant, per `ProjectRepositoryPort.statusChanges`. A card created and triaged inside
     * one clock tick writes two rows with the same `changedAt`, and on top of that
     * `changedAt` is stored as epoch *milliseconds*, so a tie is not an edge case at all.
     *
     * That leaves the tie to be broken, and neither existing column can do it: the uuid is
     * random, so ordering by it would hand back the two rows in an arbitrary order, and
     * ordering by nothing inherits the stability of whatever sort runs. The original sorted
     * on the instant alone and got insertion order by accident — Swift's `sort` is
     * introsort and makes no stability promise, so the property it happened to rely on was
     * never guaranteed by anything.
     *
     * A monotonic integer is the smallest thing that makes the order *total* and *defined*.
     * SQLite's implicit `rowid` would do, but Room's query verifier resolves columns against
     * the entity and rejects it, so the counter is declared rather than borrowed. It is the
     * one column in this module with no domain counterpart, and 8.8's completeness test
     * carries it as a declared exemption for that reason.
     */
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    val workItemID: String,
    val projectID: String,
    /** `null` means "created here", which is a real state and not an absent value. */
    val fromRaw: String?,
    val toRaw: String,
    val changedAt: Instant,
    /** The signed-in user, or `null` when the app could not tell. */
    val changedByUserID: String?,
)

/**
 * Reads and writes status history.
 */
@Dao
interface StatusChangeDao {

    /**
     * One item's history, newest first, later-recorded winning a tie.
     *
     * The two-column sort is the contract in [StatusChangeRecord.sequence] expressed as SQL.
     * `sequence DESC` rather than ASC because the list is newest-first, which is the order
     * the editor shows it in — asking every caller to remember to reverse a chronological
     * list is how a history ends up reading backwards in one screen and forwards in another.
     */
    @Query("SELECT * FROM status_changes WHERE workItemID = :workItemID ORDER BY changedAt DESC, sequence DESC")
    suspend fun byWorkItem(workItemID: String): List<StatusChangeRecord>

    @Query("SELECT * FROM status_changes WHERE projectID = :projectID")
    suspend fun byProject(projectID: String): List<StatusChangeRecord>

    /** Raw rows in insertion order, so a test can assert on the storage and not only on the
     *  repository's own reading of it. */
    @Query("SELECT * FROM status_changes ORDER BY sequence ASC")
    suspend fun all(): List<StatusChangeRecord>

    @Query("DELETE FROM status_changes WHERE workItemID = :workItemID")
    suspend fun deleteByWorkItem(workItemID: String)

    @Query("DELETE FROM status_changes WHERE projectID = :projectID")
    suspend fun deleteByProject(projectID: String)

    /**
     * Appends, never replaces.
     *
     * ABORT on the `id` index for the same reason as the other inserts: history is append-only
     * by definition, so a conflict is a bug, and a REPLACE would quietly discard a real
     * entry to make room for a duplicate of it.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: StatusChangeRecord)
}
