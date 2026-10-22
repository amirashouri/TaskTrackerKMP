package com.tasktracker.database

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlin.time.Instant

/**
 * The stored shape of a project.
 *
 * ## Why this is in `:packages:database` and not in the feature's data module
 *
 * Room's `@Database(entities = [...])` is read by the annotation processor at compile time,
 * so the store cannot name an entity class it does not have a compile-time reference to. A
 * `ProjectRecord` declared in `:packages:feature-projects-data` would need `:packages:
 * database` to depend on the feature module, and the feature module already depends on this
 * one — a cycle Gradle cannot express.
 *
 * The alternative — a `@Database` per feature — is explicitly worse, and this module's whole
 * reason for existing is the reason: a repository has to write its entity *and* the outbox
 * row in one transaction, which is only possible when both tables are in the same database.
 * Two databases means two transactions, and then the queue can disagree with the store: a
 * mutation saved with nothing queued to push it, which is the exact failure the outbox
 * exists to prevent.
 *
 * So the *schema* lives here and the *behaviour* does not. Everything in
 * `:packages:feature-projects-data` that knows what a project is — the record↔domain
 * mapping, the repository, the sync sink — is in the feature module, which is the only
 * direction the architecture table allows to grow.
 */
@Entity(tableName = "projects")
data class ProjectRecord(
    @PrimaryKey val id: String,
    val keyPrefix: String,
    val name: String,
    val details: String,
    /**
     * The project's tag catalog, as a JSON array of `ProjectTag` objects.
     *
     * A JSON document rather than a table because the catalog is read as a whole, is
     * small (a few dozen entries at most), and has no query of its own — exactly the
     * shape [WorkItemRecord.blockedByIDs] avoids by being a comma-separated list.
     * Empty is the empty string, so an old row and a new project read the same way.
     */
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    /** Comma-separated stable UUIDs; the migration default leaves existing projects unassigned. */
    @ColumnInfo(defaultValue = "''") val memberIDs: String = "",
    val isArchived: Boolean,
    /**
     * The next number to hand out for a work item key.
     *
     * Stored rather than derived by counting rows, and the reason is not performance:
     * counting is *wrong* the moment an item is deleted. `TT-3` would be issued a second
     * time, and for a while two different tasks would answer to the same key — in the
     * archive, in a search, and in anything a user quoted from a previous message.
     *
     * A counter is also what two devices need in order to merge. If each device counted its
     * own rows they would both hand out `TT-1` for different work; with a counter the two
     * merge by taking the larger, which is why the sync sink refuses to move it backwards.
     */
    val nextItemNumber: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Reads and writes projects.
 *
 * Every statement a caller needs to be atomic with something else is a single `@Query`, so
 * the database is what makes it atomic rather than the caller's read-then-write.
 */
@Dao
interface ProjectDao {

    /**
     * Every project, most recently touched first.
     *
     * The ordering is part of the read contract rather than a display concern: the list is
     * the order projects appear in everywhere, and two queries that disagreed on it would
     * make a project's position depend on which screen asked.
     */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun all(): List<ProjectRecord>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun byID(id: String): ProjectRecord?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    /**
     * ABORT rather than REPLACE, deliberately.
     *
     * Every caller of this has already established that the row is absent, so a conflict is
     * a bug in that reasoning rather than something to paper over. A REPLACE here would hide
     * it — and would also silently drop the columns the incoming row leaves out, which is
     * the shape of bug 8.8's completeness test exists to prevent.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ProjectRecord)

    @Update
    suspend fun update(record: ProjectRecord)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteByID(id: String)
}
