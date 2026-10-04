package com.tasktracker.database

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tasktracker.core.randomProjectID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

/**
 * A store written by version 3, opened by version 4.
 *
 * ## Why this is written by hand rather than with Room's helper
 *
 * Room's `MigrationTestHelper` is Android-only, and this module's tests do not run on the
 * Android host at all (`build.gradle.kts` disables `testAndroidHostTest` because Room's
 * Android builder needs a real `Context`). So the helper would put the one test that
 * matters most — the one that catches a destructive migration — behind a platform this
 * project cannot run Room tests on.
 *
 * Instead the version-3 file is built here, by executing [VersionThreeSchema] through the same
 * bundled SQLite driver the store itself uses, and then opened through
 * [TaskTrackerDatabase.open] so that Room runs **its own generated** `AutoMigration(3, 4)`.
 * Nothing about the migration is stubbed: if the generated statements are wrong, this
 * fails.
 *
 * ## What the failure mode actually is
 *
 * A destructive migration does not crash. It opens cleanly, reports the right version, and
 * returns an empty board — the rows went with the tables. So there is nothing here that
 * asserts "the migration ran"; every assertion is about a **row that was there before**.
 *
 * ## The literal cannot be allowed to drift
 *
 * [VersionThreeSchema] is a hand-written copy of `schemas/…/3.json`, and a hand-written copy is a
 * liability: somebody adds a column in a future version and this keeps claiming the old
 * shape, so the test passes against a version 3 that never existed. That is caught by
 * `SprintSchemaDriftTest` on the JVM, which executes this same literal and compares its
 * `PRAGMA table_info` against the committed JSON.
 */
class SprintMigrationTest {

    private val path: String = applicationSupportDirectory(null) +
        "/TaskTracker-migration-${randomProjectID().stringValue}.store"

    @AfterTest
    fun removeTheStore() {
        // Every run, pass or fail. A migration test that leaves its file behind starts the
        // next run from whatever the last one wrote, which is how one quietly stops
        // testing anything at all.
        deleteStoreFile(path)
    }

    @Test
    fun aStoreWrittenByVersionThreeKeepsEveryRowAndGainsTheNewColumnsAsNull() = runTest {
        writeAVersionThreeStore()

        val database = TaskTrackerDatabase.open(
            DatabaseConfiguration(name = path, inMemory = false),
        )
        try {
            // The projects and the work items, by id and by content. Asserting only a count
            // would pass on a store that kept the right *number* of rows and the wrong
            // ones, which is what a bad join in a migration produces.
            assertEquals(1, database.projectDao().count())
            val project = assertNotNull(database.projectDao().byID(PROJECT_ID))
            assertEquals("Apollo", project.name)
            assertEquals("TT", project.keyPrefix)
            assertEquals(7, project.nextItemNumber)

            assertEquals(2, database.workItemDao().count())
            val carried = assertNotNull(database.workItemDao().byID(ITEM_ID))
            assertEquals("TT-1", carried.key)
            assertEquals("Write the migration", carried.title)
            assertEquals("in_progress", carried.statusRaw)
            assertEquals(3, carried.rank)

            // The whole point of both columns being nullable: an item created before
            // sprints existed was unestimated and in the backlog, and that is exactly what
            // it migrates as. A NOT NULL column with no default would have forced Room to
            // rebuild the table instead.
            assertNull(carried.storyPoints)
            assertNull(carried.sprintID)
        } finally {
            database.close()
        }
    }

    @Test
    fun theNewSprintTableExistsAfterTheMigrationAndStartsEmpty() = runTest {
        writeAVersionThreeStore()

        val database = TaskTrackerDatabase.open(
            DatabaseConfiguration(name = path, inMemory = false),
        )
        try {
            // Empty rather than absent. A missing table here would fail loudly on this
            // call, but the distinction that matters is that it did not arrive already
            // holding something: the previous build could not have written a sprint.
            assertEquals(0, database.sprintDao().count())
        } finally {
            database.close()
        }
    }

    @Test
    fun anEstimateAndASprintSurviveBeingWrittenAndReadBack() = runTest {
        // The other half of 23.2.2, and the reason both new columns default to `null`:
        // a row that arrives with a value has to come back with that value, and a row
        // without one has to come back as `null` rather than as 0 or an empty string.
        val database = TaskTrackerDatabase.inMemory()
        database.projectDao().insert(projectRecord())
        database.workItemDao().insert(workItemRecord(ITEM_ID, "TT-1", 8, SPRINT_ID))
        database.workItemDao().insert(workItemRecord(OTHER_ITEM_ID, "TT-2", null, null))

        val estimated = assertNotNull(database.workItemDao().byID(ITEM_ID))
        assertEquals(8, estimated.storyPoints)
        assertEquals(SPRINT_ID, estimated.sprintID)

        val unestimated = assertNotNull(database.workItemDao().byID(OTHER_ITEM_ID))
        assertNull(unestimated.storyPoints)
        assertNull(unestimated.sprintID)
    }

    @Test
    fun movingAnItemIntoASprintTouchesItsUpdatedAtAndNeverItsRank() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        database.projectDao().insert(projectRecord())
        val original = workItemRecord(ITEM_ID, "TT-1", 3, null).copy(rank = 11)
        database.workItemDao().insert(original)

        database.workItemDao().assignToSprint(
            workItemIDs = listOf(original.id),
            sprintID = SPRINT_ID,
            now = Instant.fromEpochMilliseconds(LATER),
        )

        val moved = assertNotNull(database.workItemDao().byID(original.id))
        assertEquals(SPRINT_ID, moved.sprintID)
        assertEquals(Instant.fromEpochMilliseconds(LATER), moved.updatedAt)
        // Rank is the item's position in its status column and a sprint is not a column.
        // If this ever moves, the board reorders itself every time somebody tidies a sprint.
        assertEquals(11, moved.rank)
    }

    @Test
    fun deletingASprintReturnsItsItemsToTheBacklogRatherThanOrphaningThem() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        database.projectDao().insert(projectRecord())
        database.sprintDao().insert(sprintRecord())
        database.workItemDao().insert(workItemRecord(ITEM_ID, "TT-1", 1, SPRINT_ID))
        database.workItemDao().insert(workItemRecord(OTHER_ITEM_ID, "TT-2", 2, null))

        // One statement, so it cannot half-happen: an item left pointing at a sprint that
        // no longer exists is invisible to every screen in the app.
        database.workItemDao().releaseFromSprint(SPRINT_ID)
        database.sprintDao().deleteByID(SPRINT_ID)

        assertEquals(0, database.sprintDao().count())
        assertEquals(
            listOf(null, null),
            database.workItemDao().all().sortedBy { it.key }.map { it.sprintID },
        )
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private fun projectRecord() = ProjectRecord(
        id = PROJECT_ID,
        keyPrefix = "TT",
        name = "Apollo",
        details = "",
        isArchived = false,
        nextItemNumber = 7,
        createdAt = Instant.fromEpochMilliseconds(CREATED),
        updatedAt = Instant.fromEpochMilliseconds(CREATED),
    )

    private fun workItemRecord(id: String, key: String, points: Int?, sprint: String?) =
        WorkItemRecord(
            id = id,
            projectID = PROJECT_ID,
            key = key,
            title = key,
            details = "",
            statusRaw = "backlog",
            priorityRaw = 2,
            assignee = null,
            dueDate = null,
            rank = 1,
            createdAt = Instant.fromEpochMilliseconds(CREATED),
            updatedAt = Instant.fromEpochMilliseconds(CREATED),
            resolvedAt = null,
            storyPoints = points,
            sprintID = sprint,
        )

    private fun sprintRecord() = SprintRecord(
        id = SPRINT_ID,
        projectID = PROJECT_ID,
        name = "Sprint 1",
        goalStoryPoints = 21,
        startDate = Instant.fromEpochMilliseconds(CREATED),
        endDate = Instant.fromEpochMilliseconds(LATER),
        owner = "amir",
        isFinished = false,
        finishedAt = null,
        createdAt = Instant.fromEpochMilliseconds(CREATED),
        updatedAt = Instant.fromEpochMilliseconds(CREATED),
    )

    // --- building the version 3 store ------------------------------------------------------------

    /** Creates the file, puts the schema and the rows in, and closes it again. */
    private fun writeAVersionThreeStore() {
        deleteStoreFile(path)
        createParentDirectory(path)

        // Through the driver's own `open`, which is the portable entry point.
// `BundledSQLiteConnection` looks like the obvious constructor to reach for and is not
// one: on the JVM and Android its parameter is a raw native pointer, and only the native
// variant has a no-arg form - so a test written against it compiles on one host and is
// uncompilable on the other.
        val connection = BundledSQLiteDriver().open(path)
        try {
            VersionThreeSchema.create(connection)
            connection.execSQL(
                """
                INSERT INTO projects
                    (id, keyPrefix, name, details, memberIDs, isArchived, nextItemNumber,
                     createdAt, updatedAt)
                VALUES ('$PROJECT_ID', 'TT', 'Apollo', '', '', 0, 7, $CREATED, $CREATED)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO work_items
                    (id, projectID, key, title, details, statusRaw, priorityRaw, assignee,
                     dueDate, rank, createdAt, updatedAt, resolvedAt)
                VALUES ('$ITEM_ID', '$PROJECT_ID', 'TT-1', 'Write the migration', '',
                        'in_progress', 2, NULL, NULL, 3, $CREATED, $CREATED, NULL)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO work_items
                    (id, projectID, key, title, details, statusRaw, priorityRaw, assignee,
                     dueDate, rank, createdAt, updatedAt, resolvedAt)
                VALUES ('$OTHER_ITEM_ID', '$PROJECT_ID', 'TT-2', 'Review the schema', '',
                        'backlog', 1, 'sam', NULL, 1, $CREATED, $CREATED, NULL)
                """.trimIndent(),
            )
            // The version, and it is the only thing Room reads to decide whether to migrate.
            // Everything else about this file is the schema the committed 3.json describes.
            connection.execSQL("PRAGMA user_version = 3")
        } finally {
            connection.close()
        }
    }


    private companion object {
        const val PROJECT_ID = "0195project0000000000000001"
        const val ITEM_ID = "0195item00000000000000000001"
        const val OTHER_ITEM_ID = "0195item00000000000000000002"
        const val SPRINT_ID = "0195sprint0000000000000001"

        /** Epoch millis, which is what `DatabaseConverters` writes for an `Instant`. */
        const val CREATED = 1_700_000_000_000L
        const val LATER = 1_700_086_400_000L
    }
}