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
    indices = [Index(value = ["projectID", "statusRaw"]), Index(value = ["sprintID"])],
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
    /**
     * The item's estimate in points; `null` means unestimated.
     *
     * Nullable in the store for the reason it is nullable in the domain: a non-null `0`
     * makes "nobody estimated this" and "worth nothing" one value, and a sprint would then
     * be able to claim it had measured its whole scope. Both columns added here default to
     * `null`, which is what lets the `AutoMigration(3, 4)` bring every existing row across
     * unestimated and in the backlog — and which keeps every existing construction site in
     * `:packages:feature-projects-data` compiling without being edited.
     */
    val storyPoints: Int? = null,
    /**
     * The ids this item waits on, as comma-separated UUID strings, or `null` for none.
     *
     * Nullable so the `AutoMigration(6, 7)` brings every existing row across with no
     * blockers and keeps existing construction sites compiling — the same shape as
     * `storyPoints`/`sprintID` in 3 → 4.
     */
    val blockedByIDs: String? = null,
    /**
     * The sprint this item is in, or `null` for the backlog.
     *
     * Indexed because the sprint detail screen reads by sprint, not by project, and
     * `projectID, statusRaw` cannot serve it.
     */
    val sprintID: String? = null,
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
     * Every item in a sprint, in the board's own order.
     *
     * The four ordering columns are spelled out rather than shared with [byProject] on
     * purpose: a SQL fragment cannot be shared between two `@Query` annotations without a
     * constant that both interpolate, and the columns would then be stated once and
     * checked twice — which is better than stated once and checked never.
     */
    @Query(
        """
        SELECT * FROM work_items
        WHERE sprintID = :sprintID
        ORDER BY rank ASC, priorityRaw DESC, createdAt ASC, key ASC
        """,
    )
    suspend fun bySprint(sprintID: String): List<WorkItemRecord>

    /**
     * Moves items into a sprint, or back to the backlog when [sprintID] is `null`.
     *
     * `sprintID` and the [updatedAt] bump are set together, deliberately: a reassignment is
     * an edit, so it has to queue for sync like one and be stamped like one.
     *
     * **`rank` is not touched.** It is the item's position in its *status column* on the
     * board, and a sprint is not a column — letting the two interact is how a board starts
     * reordering itself every time somebody tidies a sprint.
     */
    @Query(
        """
        UPDATE work_items
        SET sprintID = :sprintID, updatedAt = :now
        WHERE id IN (:workItemIDs)
        """,
    )
    suspend fun assignToSprint(workItemIDs: List<String>, sprintID: String?, now: Instant)

    /**
     * Returns everything that was in [sprintID] to the backlog.
     *
     * One statement rather than a select-then-update pair, because a delete that orphaned
     * its items would leave work items pointing at a sprint that no longer exists with
     * nothing in the app to notice.
     */
    @Query("UPDATE work_items SET sprintID = NULL WHERE sprintID = :sprintID")
    suspend fun releaseFromSprint(sprintID: String)

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
