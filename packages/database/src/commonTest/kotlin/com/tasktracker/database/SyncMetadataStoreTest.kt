package com.tasktracker.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

private val NOW = Instant.fromEpochSeconds(1_700_000_000)

class SyncMetadataStoreTest {
    private fun store() = RoomSyncMetadataStore(TaskTrackerDatabase.inMemory())

    @Test
    fun anUnknownScopeHasNoMetadataYet() = runTest {
        assertNull(store().metadata("projects"))
    }

    @Test
    fun markingAScopeSyncedCreatesTheRow() = runTest {
        val store = store()

        store.markSynced("projects", "cursor-1", NOW)

        val metadata = store.metadata("projects")
        assertEquals("cursor-1", metadata?.cursor)
        assertEquals(NOW, metadata?.lastSyncedAt)
    }

    @Test
    fun markingTheSameScopeAgainUpdatesTheOneRowRatherThanAddingAnother() = runTest {
        val store = store()
        store.markSynced("projects", "cursor-1", NOW)
        store.markSynced("projects", "cursor-2", NOW + 60.seconds)

        val metadata = store.metadata("projects")
        assertEquals("cursor-2", metadata?.cursor)
        // Still exactly one row, or a per-scope store would grow without bound.
        assertEquals(NOW + 60.seconds, metadata?.lastSyncedAt)
    }

    @Test
    fun scopesAreIndependent() = runTest {
        val store = store()
        store.markSynced("projects", "p", NOW)
        store.markSynced("workItems:abc", "w", NOW)

        assertEquals("p", store.metadata("projects")?.cursor)
        assertEquals("w", store.metadata("workItems:abc")?.cursor)
    }

    @Test
    fun aPartialSyncOfOneProjectDoesNotAdvanceTheWholeAppsPosition() = runTest {
        val store = store()
        store.markSynced("projects", "all-projects", NOW)
        store.markSynced("workItems:abc", "one-project", NOW)

        // The project cursor is untouched by the per-project pull, which is the whole
        // reason there is a row per scope instead of one row for the app.
        assertEquals("all-projects", store.metadata("projects")?.cursor)
        assertEquals("one-project", store.metadata("workItems:abc")?.cursor)
    }

    @Test
    fun aLaterSuccessClearsARecordedFailure() = runTest {
        val store = store()
        store.recordFailure("projects", "boom")
        assertEquals("boom", store.metadata("projects")?.lastError)

        store.markSynced("projects", "c", NOW)
        assertNull(store.metadata("projects")?.lastError)
    }

    @Test
    fun aRecordedFailureCanCreateTheRowItBelongsTo() = runTest {
        val store = store()
        store.recordFailure("workItems:xyz", "server said no")

        assertEquals("server said no", store.metadata("workItems:xyz")?.lastError)
    }

    @Test
    fun markingPushedRecordsThePushWithoutTouchingTheCursor() = runTest {
        val store = store()
        store.markSynced("projects", "cursor-1", NOW)

        store.markPushed("projects", NOW + 30.seconds)

        val metadata = store.metadata("projects")
        assertEquals(NOW + 30.seconds, metadata?.lastPushedAt)
        assertEquals("cursor-1", metadata?.cursor)
    }

    @Test
    fun aRecordedSnapshotReplacesTheRowForThatScopeWholesale() = runTest {
        val store = store()
        store.record(SyncMetadata("projects", null, null, null, 7, null))
        // Recording an older snapshot rewinds the version. `serverVersion` is monotonic on
        // the server; the local copy is a cache, and this store does not second-guess the
        // order it is handed. The original behaved the same way.
        store.record(SyncMetadata("projects", null, null, null, 3, null))

        assertEquals(3, store.metadata("projects")?.serverVersion)
    }

    @Test
    fun recordingASnapshotClearsFieldsItDoesNotCarry() = runTest {
        val store = store()
        store.recordFailure("projects", "boom")

        store.record(SyncMetadata("projects", "c", NOW, null, 0, null))

        // A wholesale replace, not a merge: a snapshot that omits the error is asserting
        // there is no error, which is what a caller that has just succeeded means.
        assertNull(store.metadata("projects")?.lastError)
    }

    @Test
    fun removeAllClearsEveryScope() = runTest {
        val store = store()
        store.markSynced("projects", "p", NOW)
        store.markSynced("workItems:abc", "w", NOW)

        store.removeAll()

        assertNull(store.metadata("projects"))
        assertNull(store.metadata("workItems:abc"))
    }
}

class SessionCacheTest {
    private fun cache() = RoomSessionCache(TaskTrackerDatabase.inMemory())

    @Test
    fun thereIsNoCachedSessionBeforeTheFirstSignIn() = runTest {
        assertNull(cache().current())
    }

    @Test
    fun aSignInIsCachedAndReadable() = runTest {
        val cache = cache()

        cache.save(CachedSession("u1", "a@b.com", "A", NOW, 4))

        val session = cache.current()
        assertEquals("u1", session?.userID)
        assertEquals("a@b.com", session?.email)
        assertEquals("A", session?.displayName)
        assertEquals(4, session?.serverVersion)
    }

    @Test
    fun signingInAgainUpdatesTheCachedIdentityInsteadOfDuplicatingIt() = runTest {
        val cache = cache()
        cache.save(CachedSession("u1", "a@b.com", "A", NOW, 1))
        cache.save(CachedSession("u1", "new@b.com", "B", NOW, 2))

        val session = cache.current()
        assertEquals("new@b.com", session?.email)
        assertEquals(2, session?.serverVersion)
    }

    @Test
    fun signingOutClearsIt() = runTest {
        val cache = cache()
        cache.save(CachedSession("u1", "a@b.com", null, NOW, 0))

        cache.clear()

        assertNull(cache.current())
    }

    @Test
    fun aDisplayNameCanBeClearedByASecondSignIn() = runTest {
        val cache = cache()
        cache.save(CachedSession("u1", "a@b.com", "A", NOW, 1))

        cache.save(CachedSession("u1", "a@b.com", null, NOW, 1))

        // A null has to overwrite, not be skipped as "absent" — otherwise a name removed
        // on the server stays on screen forever.
        assertNull(cache.current()?.displayName)
    }
}
