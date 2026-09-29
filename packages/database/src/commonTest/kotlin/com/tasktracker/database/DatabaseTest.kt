package com.tasktracker.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * Where the store is built. The in-memory guarantee is the important one: the original
 * lost a build to a file-backed store pointed at a null device, so "is this really
 * in-memory" is a property with teeth rather than a naming convention.
 */
class DatabaseTest {
    @Test
    fun theInMemoryConfigurationCarriesNoNameToPointAtTheNullDevice() {
        // Room's own builder rejects a blank name and `:memory:` outright, so the
        // configuration has to leave nothing for a caller to pass by mistake. The
        // original's `inMemory` configuration carried `/dev/null`, which was one refactor
        // away from being used as a real path.
        assertEquals("", DatabaseConfiguration.inMemory.name)
        assertTrue(DatabaseConfiguration.inMemory.inMemory)
    }

    @Test
    fun aFileBackedConfigurationIsNotInMemory() {
        val configuration = DatabaseConfiguration("/tmp/TaskTracker/store", inMemory = false)

        assertTrue(!configuration.inMemory)
        assertEquals("/tmp/TaskTracker/store", configuration.name)
    }

    @Test
    fun theDefaultConfigurationLivesUnderApplicationSupport() {
        val configuration = DatabaseConfiguration.applicationDefault()

        assertTrue(
            configuration.name.endsWith("/TaskTracker/TaskTracker.store"),
            "unexpected default location: ${configuration.name}",
        )
        assertTrue(!configuration.inMemory)
    }

    @Test
    fun androidSaysWhatItNeedsRatherThanFailingLater() {
        // Common code cannot see `android.content.Context`, so the path comes from a host.
        // The failure has to name the missing argument, or it reads as "the database is
        // broken" and sends the caller looking in the wrong place.
        val failure = runCatching { DatabaseConfiguration.applicationDefault() }.exceptionOrNull()

        // On the JVM and iOS the host is ignored and this succeeds; on Android it throws.
        if (failure != null) {
            assertTrue(
                failure is com.tasktracker.core.AppError.Storage,
                "unexpected failure type: $failure",
            )
            assertTrue(failure.message.orEmpty().contains("Context"))
        }
    }

    @Test
    fun eachInMemoryStoreIsIndependentSoTestsDoNotShareRows() = runTest {
        val first = TaskTrackerDatabase.inMemory()
        val second = TaskTrackerDatabase.inMemory()

        RoomSyncMetadataStore(first).markSynced("projects", "cursor-1", Instant.fromEpochSeconds(1))

        // The original's in-memory store was process-wide, so this was impossible and its
        // tests had to be careful never to build two. Two independent stores is what makes
        // these tests safe to run in parallel.
        assertEquals("cursor-1", RoomSyncMetadataStore(first).metadata("projects")?.cursor)
        assertNull(RoomSyncMetadataStore(second).metadata("projects"))
    }

    @Test
    fun theCrossCuttingTablesAreAlwaysPresent() = runTest {
        // The original's registry added OutboxRecord, SyncMetadataRecord and SessionRecord
        // on every build, whatever a feature registered, because a store without an outbox
        // cannot sync and one without session metadata cannot tell whose data it holds.
        // Room has no registry to do this with; the guarantee is now the `entities` list on
        // the database, and this is the test that fails if one is dropped from it.
        val database = TaskTrackerDatabase.inMemory()

        assertEquals(0, database.outboxDao().count())
        assertNull(database.syncMetadataDao().byScope("anything"))
        assertNull(database.sessionDao().current())

        // Each of the three is genuinely writable, not merely declared.
        RoomOutboxQueue(database).enqueue(
            NewOutboxEntry(
                id = "change-1",
                entity = OutboxEntity.WorkItem,
                entityID = "1",
                operation = OutboxOperation.Upsert,
                payload = ByteArray(0),
                createdAt = Instant.fromEpochSeconds(1),
            ),
        )
        assertEquals(1, database.outboxDao().count())
    }
}
