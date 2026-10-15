package com.tasktracker.database

import androidx.room.useReaderConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * The force sign-out's wipe, and the one thing it must not do.
 *
 * Two properties are under test and they pull in opposite directions. The wipe has to be
 * *complete* — a table left behind is a user's data still on a device they asked to be clean,
 * and the failure is invisible because the app looks signed out either way. And it has to be
 * *surgical* — `room_master_table` holds the identity hash Room validates on open, so a wipe
 * that includes it does not fail here, it fails on the next launch with a store Room refuses
 * to open. A force sign-out that bricks the app on restart is worse than the hang it fixes.
 *
 * The store is real rather than a double, because both properties are properties *of Room*:
 * which tables exist, and what Room needs to be able to reopen. A fake would only prove the
 * test agrees with itself.
 */
class LocalStoreEraserTest {

    @Test
    fun erasingEmptiesEveryTableTheAppOwns() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        seed(database)

        // The seed is asserted rather than assumed: a wipe that passes because the seed never
        // landed would be the most convincing kind of false green.
        assertTrue(database.countRows("projects") > 0, "seed did not reach projects")
        assertTrue(database.countRows("outbox") > 0, "seed did not reach outbox")
        assertTrue(database.countRows("session") > 0, "seed did not reach session")
        assertTrue(database.countRows("sync_metadata") > 0, "seed did not reach sync_metadata")

        RoomLocalStoreEraser(database).eraseAll()

        val remaining = database.erasableTables().filter { database.countRows(it) > 0 }
        assertEquals(emptyList(), remaining, "tables still holding rows after the wipe")
    }

    @Test
    fun theRoomsIdentityRowSurvivesSoTheStoreStillOpens() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        seed(database)

        RoomLocalStoreEraser(database).eraseAll()

        // The guard for the one name that looks like an ordinary table and is not. If this
        // row goes, the wipe succeeds and the *next* open fails, which is the failure mode a
        // test at this level is the only cheap place to catch.
        assertEquals(1, database.countRows("room_master_table"))
    }

    @Test
    fun aTableTheEraserWasNeverToldAboutIsStillEmptied() = runTest {
        // The schema-agnostic property, which is the reason the table list is read from
        // `sqlite_master` rather than written out. `projects` is seeded and never named
        // anywhere in the eraser, so a hardcoded list would leave it behind and this fails.
        val database = TaskTrackerDatabase.inMemory()
        database.projectDao().insert(project(id = "p1"))

        RoomLocalStoreEraser(database).eraseAll()

        assertEquals(0, database.countRows("projects"))
    }

    @Test
    fun erasingAnEmptyStoreIsHarmless() = runTest {
        // A force sign-out can follow a session that never wrote anything — a user who signed
        // in and immediately signed out with no server. The wipe has to be a no-op rather than
        // a failure, or the path the user is on gets stuck for a reason that is not theirs.
        val database = TaskTrackerDatabase.inMemory()
        val eraser = RoomLocalStoreEraser(database)

        eraser.eraseAll()
        eraser.eraseAll()

        assertEquals(1, database.countRows("room_master_table"))
    }

    // MARK: - Helpers

    private suspend fun seed(database: TaskTrackerDatabase) {
        val at = Instant.fromEpochSeconds(1)
        database.projectDao().insert(project(id = "p1"))
        database.sessionDao().upsert(
            SessionRecord(
                userID = "user-1",
                email = "ada@example.com",
                displayName = "Ada",
                lastSignedInAt = at,
                serverVersion = 0,
            ),
        )
        database.syncMetadataDao().upsert(
            SyncMetadataRecord(
                scope = "projects",
                cursor = "page-7",
                lastSyncedAt = at,
                lastPushedAt = null,
                serverVersion = 0,
                lastError = null,
            ),
        )
        database.outboxDao().insert(
            OutboxRecord(
                id = "row-1",
                entityKind = "project",
                entityID = "p1",
                operation = "create",
                payload = ByteArray(0),
                userID = "user-1",
                createdAt = at,
                status = "pending",
                attemptCount = 0,
                lastAttemptAt = null,
                lastError = null,
            ),
        )
    }

    private fun project(id: String) = ProjectRecord(
        id = id,
        keyPrefix = "TT",
        name = "Alpha",
        details = "",
        isArchived = false,
        nextItemNumber = 1,
        createdAt = Instant.fromEpochSeconds(1),
        updatedAt = Instant.fromEpochSeconds(1),
    )

    /**
     * The eraser's own predicate, restated here rather than shared with it.
     *
     * Sharing the constant would make the test pass for whatever the eraser currently
     * excludes — including a future exclusion added to dodge a failure. Writing it out means
     * a change to the eraser's filter is a change to two places, and the diff says so.
     */
    private suspend fun TaskTrackerDatabase.erasableTables(): List<String> =
        useReaderConnection { connection ->
            connection.usePrepared(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'",
            ) { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }

    private suspend fun TaskTrackerDatabase.countRows(table: String): Int =
        useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM \"$table\"") { statement ->
                statement.step()
                statement.getLong(0).toInt()
            }
        }
}
