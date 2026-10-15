package com.tasktracker.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * A store left behind by a *different* build is replaced, not migrated.
 *
 * Nothing has shipped, so there is no migration chain, and the whole answer to a version
 * bump is [TaskTrackerDatabase.open]'s destructive fallback. Until this file existed that
 * fallback had never run: every other test opens a store it has just created, so the
 * version recorded on disk was always the one the binary expects and Room's upgrade path
 * was dead code. A defect in it could not fail anything — which is how one shipped. The
 * fallback was configured with two calls, and the second silently cancelled the first, so
 * opening an older store threw *"A migration from 3 to 4 was required but not found"*
 * instead of recreating it.
 *
 * File-backed, so `jvmTest`, for the same reason as [DatabaseLocationTest]: "a store from
 * another build" is a property of a file on disk, and reopening one is a thing only a real
 * file can do.
 */
class StoreUpgradeTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    /**
     * A table that no current entity owns, standing in for the ones versions 2–9 declared
     * and later dropped. It is what makes the *kind* of destruction visible: see
     * [assertAStoreAtVersionIsRecreated].
     */
    private val obsoleteTable = "obsolete_from_an_earlier_build"

    private fun tempDirectory(): File = Files.createTempDirectory("tasktracker-upgrade").toFile()

    @Test
    fun aStoreFromAnEarlierBuildIsRecreatedRatherThanRefused() = runBlocking {
        // 3 is the version the reported failure named ("a migration from 3 to 4 was
        // required but not found"); any lower number takes the identical path.
        assertAStoreAtVersionIsRecreated(version = 3)
    }

    @Test
    fun aStoreFromANewerBuildIsRecreatedRatherThanRefused() = runBlocking {
        // The downgrade case, which Room treats as a separate condition: the file is ahead
        // of this binary, as it is after switching back to an older checkout. Nothing
        // about it depends on the upgrade path, so it is the guard that removing a call
        // from the fallback did not break the other direction.
        assertAStoreAtVersionIsRecreated(version = 99)
    }

    /**
     * The open succeeds at the current version, and the store is *recreated* rather than
     * merely emptied.
     *
     * The difference is the reason [obsoleteTable] exists. `dropAllTables = false` still
     * drops every table the current schema knows about, so a "the old rows are gone"
     * assertion passes under both flags and would certify the weaker one — a store that is
     * emptied but keeps whatever a previous schema left behind, which Room's own KDoc warns
     * "could leave obsolete data". `TaskTrackerDatabase`'s KDoc dates exactly that history
     * to schemas 2–9, so it is not a hypothetical: the flag has to be `true`, and this is
     * the assertion that says so.
     */
    private suspend fun assertAStoreAtVersionIsRecreated(version: Int) {
        val name = leaveBehindAStoreFromAnotherBuild(tempDirectory(), version)

        withStore(name) { database ->
            val cursor = RoomSyncMetadataStore(database).metadata("projects")?.cursor

            assertNull(
                cursor,
                "a store recording version $version was opened at the current version but " +
                    "its row survived (cursor=$cursor), so the store was not recreated",
            )
        }

        // Read the file after Room has closed it: two connections to one store is not the
        // property under test.
        val tables = tableNames(name)

        assertTrue(
            obsoleteTable !in tables,
            "a store recording version $version still holds '$obsoleteTable', which no " +
                "current entity owns (tables=$tables): it was emptied, not recreated, so " +
                "`dropAllTables` is not being honoured",
        )
    }

    /**
     * Writes a real store with a real row, then makes it look like one a different build
     * left behind: the schema version SQLite records, plus a table no current entity owns.
     *
     * `PRAGMA user_version` is the only thing Room reads to decide that a store predates —
     * or postdates — this build, so writing it directly is how another build's store is
     * produced without keeping a copy of its binary, which would need regenerating on every
     * schema change.
     */
    private suspend fun leaveBehindAStoreFromAnotherBuild(directory: File, version: Int): String {
        val name = File(directory, "TaskTracker.store").path

        withStore(name) { database ->
            RoomSyncMetadataStore(database).markSynced("projects", "cursor-1", now)

            // The row has to genuinely be on disk before the version is rewritten, or
            // "the row is gone afterwards" would be satisfied by a store that never
            // persisted anything and the recreation claim would be vacuous.
            assertEquals(
                "cursor-1",
                RoomSyncMetadataStore(database).metadata("projects")?.cursor,
                "the row did not reach the file, so this test cannot tell a recreated store " +
                    "from an empty one",
            )
        }

        BundledSQLiteDriver().open(name).use { connection ->
            connection.prepare("CREATE TABLE $obsoleteTable (id TEXT)").use { statement ->
                statement.step()
            }
            connection.prepare("PRAGMA user_version = $version").use { statement ->
                statement.step()
            }
        }

        return name
    }

    /** Every table the store file holds, read through the driver rather than through Room. */
    private fun tableNames(name: String): Set<String> =
        BundledSQLiteDriver().open(name).use { connection ->
            connection.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { statement ->
                buildSet {
                    while (statement.step()) {
                        add(statement.getText(0))
                    }
                }
            }
        }

    /**
     * `RoomDatabase` is not `Closeable`, so there is no `use` here; every test closes
     * explicitly. An open store holds a file handle, and a leaked one on Windows would
     * make the next test's cleanup of the temp directory fail.
     */
    private suspend fun <T> withStore(name: String, body: suspend (TaskTrackerDatabase) -> T): T {
        val database = TaskTrackerDatabase.open(DatabaseConfiguration(name = name))
        try {
            return body(database)
        } finally {
            database.close()
        }
    }
}
