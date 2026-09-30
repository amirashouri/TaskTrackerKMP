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
 * The stored shape of a work item.
 *
 * ## `projectID` is a plain string, not a foreign key
 *
 * A real `FOREIGN KEY ... ON DELETE CASCADE` would save the repository work, and it is
 * exactly the wrong trade. This store is local-first against a server that also deletes, so
 * deleting a project has to write an outbox row for the project *and* for every item under
 * it. A database-level cascade would remove the children as part of the same statement that
 * queued the deletes — and the delete rows have to exist for the *server* to learn about
 * them, not for the local database to stay consistent. A cascade also cannot write a row
 * into `outbox`, so there is no way to get both.
 *
 * Holding the id as a plain indexed string keeps the lookup a single index seek and keeps
 * the cascade explicit in the one place that has to queue rows for it.
 * See [ProjectRecord] for why this type is in `:packages:database` at all.
 */
@Entity(
    tableName = "work_items",
    /**
     * One composite index rather than two singles.
     *
     * Every read of a board is `WHERE projectID = ?`, and every question asked *within* a
     * project adds a status — [minRank] for "where does a new card go", and the board
     * columns themselves. A `(projectID, statusRaw)` index serves the project-wide read as
     * a prefix scan and the per-column read exactly, so a second index on `projectID` alone
     * would only be a smaller copy of the left-hand edge of this one.
     */
    indices = [Index(value = ["projectID", "statusRaw"])],
)
data class WorkItemRecord(
    @PrimaryKey val id: String,
    val projectID: String,
    val key: String,
    val title: String,
    val details: String,
    /**
     * Enums are stored as their raw value and looked up through a fallible parse.
     *
     * A `WorkItemStatus` column would read better, but an unknown value arriving from an
     * older or newer server would then fail inside SQLite's own decoding and take the whole
     * fetch with it — one bad row emptying an entire board. A raw string that maps to a
     * known case, or deliberately does not, keeps the damage to the one row.
     */
    val statusRaw: String,
    val priorityRaw: Int,
    val assignee: String?,
    val dueDate: Instant?,
    val rank: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * When the item last reached a terminal status.
     *
     * Additive rather than required, so a store written before the column existed migrates
     * in with every row `null`. Which is why the read path reconciles it against the status
     * instead of trusting it — see `domainValue` in `:packages:feature-projects-data`.
     */
    val resolvedAt: Instant?,
)

/**
 * Reads and writes work items.
 */
@Dao
interface WorkItemDao {

    /**
     * Every item in a project, in the board's own order.
     *
     * The `ORDER BY` is [WorkItemRecord]'s four ordering columns, stated in SQL rather than
     * left to the caller: rank ascending, priority *descending* (the highest priority sorts
     * first, so the stored ordinal has to be inverted), then creation time, then key as the
     * final tie-break. Two items can legitimately share a rank and a creation instant —
     * seeded data does it on every launch — and without that last column their order would
     * depend on the sort's mood, which shows up as cards swapping places for no reason.
     *
     * The domain still re-sorts through `WorkItem.orderingKey` when it builds a projection.
     * That is not duplication to be removed: this ordering makes the read cheap and stable,
     * the projection's is the authority, and 8.9 asserts the two agree rather than trusting
     * that they do.
     */
    @Query(
        """
        SELECT * FROM work_items
        WHERE projectID = :projectID
        ORDER BY rank ASC, priorityRaw DESC, createdAt ASC, key ASC
        """,
    )
    suspend fun byProject(projectID: String): List<WorkItemRecord>

    @Query("SELECT * FROM work_items WHERE id = :id LIMIT 1")
    suspend fun byID(id: String): WorkItemRecord?

    /**
     * The lowest rank already used in one column, or `null` when the column is empty.
     *
     * An aggregate rather than "fetch the project and reduce it in Kotlin", because the
     * original did the latter and paid for a full read of every item in the project on every
     * card created. One indexed seek instead.
     *
     * `null` is load-bearing rather than incidental: `null` means "nothing here yet", and the
     * caller floors it to 1, which is what makes a new card sort *above* the existing ones.
     */
    @Query(
        "SELECT MIN(rank) FROM work_items WHERE projectID = :projectID AND statusRaw = :statusRaw",
    )
    suspend fun minRank(projectID: String, statusRaw: String): Int?

    /**
     * The highest rank already used in one column, or `null` when the column is empty.
     *
     * The other half of [minRank], and needed for a different instruction: a new card goes to
     * the *top* of a column, but a card moved into one lands at the *bottom* of it. One
     * aggregate cannot serve both, and using the minimum for both would make every drag
     * reorder a lane around the card that came in.
     */
    @Query(
        "SELECT MAX(rank) FROM work_items WHERE projectID = :projectID AND statusRaw = :statusRaw",
    )
    suspend fun maxRank(projectID: String, statusRaw: String): Int?

    /** Column counts for the board, in one pass rather than one query per column. */
    @Query("SELECT statusRaw, COUNT(*) AS total FROM work_items WHERE projectID = :projectID GROUP BY statusRaw")
    suspend fun countsByStatus(projectID: String): List<StatusCount>

    @Query("SELECT * FROM work_items")
    suspend fun all(): List<WorkItemRecord>

    @Query("SELECT COUNT(*) FROM work_items")
    suspend fun count(): Int

    /** ABORT for the same reason as [ProjectDao.insert]: the caller has already established
     *  the row is absent, so a conflict is a bug rather than something to overwrite. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: WorkItemRecord)

    @Update
    suspend fun update(record: WorkItemRecord)

    @Query("DELETE FROM work_items WHERE id = :id")
    suspend fun deleteByID(id: String)

    @Query("DELETE FROM work_items WHERE projectID = :projectID")
    suspend fun deleteByProject(projectID: String)
}

/**
 * One row of [WorkItemDao.countsByStatus].
 *
 * A projection rather than a `Map`, because Room cannot return a map from a `@Query` and
 * building one in Kotlin would mean a second pass over a result the database already had
 * counted. `statusRaw` rather than a parsed [WorkItemStatus], for the same reason the column
 * is a raw string: a status this build does not know still has a count, and dropping it
 * would make the board's total disagree with the sum of its columns.
 */
data class StatusCount(
    val statusRaw: String,
    val total: Int,
)
