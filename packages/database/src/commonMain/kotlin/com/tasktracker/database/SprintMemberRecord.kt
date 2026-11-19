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
 * One person's capacity in one sprint.
 *
 * No foreign key for the same reason [SprintRecord] has none: a member row can
 * be pulled before its sprint exists on a local-first device, and a constraint
 * would reject the very write that repairs the gap. The id is composite so a
 * re-delivered pull lands on the same row.
 */
@Entity(
    tableName = "sprintMembers",
    indices = [Index(value = ["sprintID"])],
)
data class SprintMemberRecord(
    @PrimaryKey val id: String,
    val projectID: String,
    val sprintID: String,
    val userID: String,
    val availableStoryPoints: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Dao
interface SprintMemberDao {
    @Query("SELECT * FROM sprintMembers WHERE sprintID = :sprintID ORDER BY id")
    suspend fun bySprint(sprintID: String): List<SprintMemberRecord>

    @Query("SELECT * FROM sprintMembers WHERE id = :id LIMIT 1")
    suspend fun byID(id: String): SprintMemberRecord?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: SprintMemberRecord)

    @Update
    suspend fun update(record: SprintMemberRecord)

    @Query("DELETE FROM sprintMembers WHERE id = :id")
    suspend fun deleteByID(id: String)

    @Query("DELETE FROM sprintMembers WHERE sprintID = :sprintID")
    suspend fun deleteBySprint(sprintID: String)
}
