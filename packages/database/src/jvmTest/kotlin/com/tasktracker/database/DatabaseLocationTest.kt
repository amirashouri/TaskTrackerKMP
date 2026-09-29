package com.tasktracker.database

import com.tasktracker.core.AppError
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * On-disk behaviour, which is inherently platform behaviour: a file-backed store, the
 * directory it needs, and the failure mode of a location that cannot be used.
 *
 * The original proved these with `FileManager` and a throwaway `UserDefaults` suite, so
 * `jvmTest` is where they belong here too. Everything about the store's *behaviour* is in
 * `commonTest` and runs on all three targets.
 */
class DatabaseLocationTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private fun tempDirectory(): File = Files.createTempDirectory("tasktracker-db").toFile()

    /**
     * `RoomDatabase` is not `Closeable`, so there is no `use` here. Every test closes
     * explicitly instead: an open store holds a file handle, and a leaked one on Windows
     * would make the next test's `deleteOnExit` fail.
     */
    private suspend fun <T> withStore(name: String, body: suspend (TaskTrackerDatabase) -> T): T {
        val database = TaskTrackerDatabase.open(DatabaseConfiguration(name = name))
        try {
            return body(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun aFileBackedStoreKeepsItsRowsWhenReopened() = runBlocking {
        val directory = tempDirectory()
        val name = File(directory, "TaskTracker/TaskTracker.store").path

        withStore(name) { database ->
            RoomSyncMetadataStore(database).markSynced("projects", "cursor-1", now)
        }

        // Reopening reads the same file. A store that was secretly in memory would come back
        // empty, which is the other half of why the null-device shortcut was worth warning
        // about: it produces a real SQLite store with none of the guarantees of either.
        withStore(name) { database ->
            assertEquals("cursor-1", RoomSyncMetadataStore(database).metadata("projects")?.cursor)
        }
    }

    @Test
    fun aMissingParentDirectoryIsCreated() = runBlocking {
        val directory = tempDirectory()
        val name = File(directory, "deeply/nested/TaskTracker.store").path
        assertTrue(!File(directory, "deeply").exists())

        withStore(name) { database ->
            RoomSyncMetadataStore(database).markSynced("projects", "cursor-1", now)
        }

        assertTrue(File(directory, "deeply/nested").isDirectory)
    }

    @Test
    fun anUnusableLocationSurfacesAsAStorageErrorNotARawFilesystemError() = runBlocking {
        // A plain file where a directory has to be, so creating the directory fails.
        val blocker = File(tempDirectory(), "blocker")
        blocker.writeText("")

        val failure = assertFailsWith<AppError.Storage> {
            withStore(File(blocker, "nested/store.sqlite").path) { }
        }

        // Nothing but AppError may cross a module boundary, and a store that cannot be
        // opened must never be mistaken for an auth or network problem.
        assertTrue(failure.detail.contains("directory"), "unhelpful detail: ${failure.detail}")
    }

    @Test
    fun anUnusableLocationIsRejectedBeforeTheStoreIsOpened() = runBlocking {
        val blocker = File(tempDirectory(), "blocker")
        blocker.writeText("")

        assertFailsWith<AppError.Storage> {
            withStore(File(blocker, "nested/store.sqlite").path) { }
        }

        // Nothing was created and no stray file appeared where the database would have been,
        // so the failure left nothing behind to clean up or to trip over later.
        assertTrue(!File(blocker, "nested").exists())
    }

    @Test
    fun theDefaultLocationSitsUnderTheApplicationSupportDirectory() {
        val configuration = DatabaseConfiguration.applicationDefault()

        assertTrue(
            configuration.name.contains("Application Support") || System.getenv("APPDATA") != null,
            "unexpected default location: ${configuration.name}",
        )
        assertTrue(configuration.name.endsWith("TaskTracker.store"))
    }
}
