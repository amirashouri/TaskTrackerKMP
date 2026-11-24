package com.tasktracker.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlin.time.Instant

/**
 * The stored shape of an epic.
 *
 * ## `projectIDs` is one comma-separated column, not a join table
 *
 * The epic's project set is read and written only as a whole: the list screen shows the
 * chips, the editor rewrites the set wholesale, and no query ever asks "which epics span
 * this project" — a project's board deliberately gains no epic lane. A join table would
 * therefore be a second table, a second DAO and a second transaction to keep in step for
 * a column nobody queries independently, and this module's rules forbid exactly that
 * sprawl. [WorkItemRecord.blockedByIDs] and [ProjectRecord.memberIDs] encode the same kind
 * of set the same way.
 *
 * There is no foreign key to `projects`, for the reason [WorkItemRecord] states at length:
 * the references are strings the repository maintains, and a pull can legitimately deliver
 * an epic before every project it names.
 *
 * See [ProjectRecord] for why the schema lives in `:packages:database` at all.
 */
@Entity(tableName = "epics")
data class EpicRecord(
    @PrimaryKey val id: String,
    /**
     * The projects this epic spans, in the order the user chose, as comma-separated UUID
     * strings. Empty is the empty string, so a row and a freshly built epic read alike.
     */
    @ColumnInfo(defaultValue = "''") val projectIDs: String = "",
    val title: String,
    val details: String,
    val dueDate: Instant?,
    val isArchived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * The server's optimistic-locking token, stored so a queued update can carry the
     * version it was based on. Defaulted so a row written before this column existed
     * reads as unversioned.
     */
    @ColumnInfo(defaultValue = "''") val version: String = "",
    /** The server's owner user id; null only for a row written before this column existed. */
    val ownerID: String? = null,
)

/**
 * Reads and writes epics.
 *
 * Every statement a caller needs to be atomic with something else is a single `@Query`,
 * for the reason [ProjectDao] states.
 *
 * ## The statements about an epic that reach into `work_items` are deliberately absent
 *
 * Reading an epic's subtasks, clearing a link, and the archive-all batch all live on
 * [WorkItemDao]. `work_items` has one DAO and every statement against that table is in it
 * — see [SprintDao]'s KDoc for the same split and the same reason.
 */
@Dao
interface EpicDao {

    /**
     * Every epic, most recently touched first — the same read contract as
     * [ProjectDao.all], because the Epic segment is a list of every epic the device holds,
     * not of one project's.
     */
    @Query("SELECT * FROM epics ORDER BY updatedAt DESC")
    suspend fun all(): List<EpicRecord>

    @Query("SELECT * FROM epics WHERE id = :id LIMIT 1")
    suspend fun byID(id: String): EpicRecord?

    @Query("SELECT COUNT(*) FROM epics")
    suspend fun count(): Int

    /** ABORT for the same reason as [ProjectDao.insert]: a conflict is a bug, not a repair. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: EpicRecord)

    @Update
    suspend fun update(record: EpicRecord)

    @Query("DELETE FROM epics WHERE id = :id")
    suspend fun deleteByID(id: String)
}
