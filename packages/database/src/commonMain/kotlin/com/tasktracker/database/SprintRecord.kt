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
 * The stored shape of a sprint.
 *
 * ## `projectID` is a plain string, for the reason `WorkItemRecord.projectID` is
 *
 * No foreign key, and the same argument: deleting a project has to write an outbox row for
 * the project *and* for everything under it, because the rows have to exist for the
 * *server* to learn about the deletion — not for the local database to stay consistent. A
 * database-level cascade cannot write into `outbox`, so there is no way to get both.
 * See [WorkItemRecord] for the full argument.
 *
 * ## No foreign key from `work_items.sprintID` either, and this one is a different reason
 *
 * Even a cascade-free `FOREIGN KEY` on `sprint_id` would force SQLite to enforce an
 * ordering this store deliberately does not have. A work item pulled into a sprint arrives
 * from a server independently of the sprint row — that is the entire point of a local-first
 * sync — so the reference is regularly briefly dangling, and a constraint would reject the
 * push that repairs it. The id is an indexed string, and the one place that has to notice a
 * sprint going away is the repository.
 */
@Entity(
    tableName = "sprints",
    /**
     * The project read is the only one the app makes: the Sprints tab lists a project's
     * sprints, and the detail screen looks one up by id.
     */
    indices = [Index(value = ["projectID"])],
)
data class SprintRecord(
    @PrimaryKey val id: String,
    val projectID: String,
    val name: String,
    /**
     * The cap, in story points — the maximum that may be committed inside the sprint.
     *
     * A `NOT NULL DEFAULT 0` rather than a nullable column, because "no goal" and "a goal
     * of zero" are the same thing here — a sprint nobody set a target for has missed none —
     * and two columns for one state would be a second question to keep consistent. The
     * domain's `Sprint.goalStoryPoints` carries the same floor. The prose goal is the
     * separate nullable [goal] column; see `Sprint`'s KDoc on why there are two.
     */
    val goalStoryPoints: Int = 0,
    /**
     * The goal in prose, nullable — "no goal" and "cleared" are one state, and nullable is
     * also what makes the 4 → 5 auto-migration additive rather than a rebuild.
     */
    val goal: String? = null,
    val startDate: Instant,
    val endDate: Instant,
    /** Free text and nullable, exactly as `WorkItemRecord.assignee` is. See the entity. */
    val owner: String? = null,
    val isFinished: Boolean,
    val finishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Reads and writes sprints.
 *
 * Every statement a caller needs to be atomic with something else is a single `@Query`, for
 * the reason [ProjectDao] states.
 *
 * ## The statements about a sprint that reach into `work_items` are deliberately absent
 *
 * They live on [WorkItemDao] instead. Said explicitly because it is the kind of split a
 * reader assumes was an oversight: a sprint-scoped `UPDATE work_items` reads as though it
 * belonged to whoever owns sprints, and the next person adds theirs too. `work_items` has
 * one DAO and every statement against that table is in it — which is what makes "did the
 * sprint write touch the rank?" a question with one file to look in.
 */
@Dao
interface SprintDao {

    /**
     * A project's sprints, unfinished first and then by start date.
     *
     * The order is part of the read contract rather than a display concern: an unfinished
     * sprint is one the user can be working in, and the live ones have to lead every list
     * that shows them — the tab, the board's switcher, and the summary's back link — before
     * the finished history below. Several may be unfinished at once; the ordering does not
     * assume one.
     *
     * `isFinished ASC` rather than a `CASE`, so the intent is the column itself and a
     * future status cannot fall out of the ordering by being forgotten here.
     */
    @Query("SELECT * FROM sprints WHERE projectID = :projectID ORDER BY isFinished ASC, startDate ASC")
    suspend fun byProject(projectID: String): List<SprintRecord>

    @Query("SELECT * FROM sprints WHERE id = :id LIMIT 1")
    suspend fun byID(id: String): SprintRecord?

    @Query("SELECT * FROM sprints")
    suspend fun all(): List<SprintRecord>

    @Query("SELECT COUNT(*) FROM sprints")
    suspend fun count(): Int

    /** ABORT for the same reason as [ProjectDao.insert]: a conflict is a bug, not a repair. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: SprintRecord)

    @Update
    suspend fun update(record: SprintRecord)

    @Query("DELETE FROM sprints WHERE id = :id")
    suspend fun deleteByID(id: String)
}