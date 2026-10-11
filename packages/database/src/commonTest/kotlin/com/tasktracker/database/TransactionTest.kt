package com.tasktracker.database

import com.tasktracker.core.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * The store and the queue deliberately share one database: that is the real arrangement,
 * and the rollback test is meaningless across two stores.
 *
 * The original proved atomicity across a feature entity and the outbox. There is no
 * feature table yet — those land in phases 8 and 11 — so the same property is proved
 * across the two cross-cutting tables, which is the same shape: two tables, one
 * transaction, a throw in the middle, and neither row survives.
 */
class TransactionTest {
    private val origin = Instant.fromEpochSeconds(1_700_000_000)

    private fun queued(id: String) = NewOutboxEntry(
        id = id,
        entity = OutboxEntity.WorkItem,
        entityID = id,
        operation = OutboxOperation.Upsert,
        payload = """{"title":"t"}""".encodeToByteArray(),
        createdAt = origin,
    )

    private fun metadataRow(scope: String) = SyncMetadataRecord(
        scope = scope,
        cursor = "cursor-1",
        lastSyncedAt = origin,
        lastPushedAt = null,
        serverVersion = 0,
        lastError = null,
    )

    @Test
    fun anEntityAndItsQueuedChangeAreCommittedTogether() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val store = RoomSyncMetadataStore(database)
        val queue = RoomOutboxQueue(database)

        inTransaction(database) {
            store.markSynced("projects", "cursor-1", origin)
            queue.enqueue(queued("change-1"))
        }

        assertEquals("cursor-1", store.metadata("projects")?.cursor)
        assertEquals(1, queue.count(null))
    }

    @Test
    fun aFailureAfterTheWritesRollsBothBackSoNothingIsStranded() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val store = RoomSyncMetadataStore(database)
        val queue = RoomOutboxQueue(database)

        val failure = assertFailsWith<AppError.Storage> {
            inTransaction(database) {
                store.markSynced("projects", "cursor-1", origin)
                queue.enqueue(queued("ghost-change"))
                throw AppError.Storage("simulated failure after writing")
            }
        }

        assertEquals("simulated failure after writing", failure.detail)
        // The row must not survive a failed write, and an outbox row with no entity is
        // worse than nothing: it would be pushed to a server that never heard of the row.
        assertEquals(null, store.metadata("projects"))
        assertEquals(0, queue.count(null))
    }

    @Test
    fun theSameStoreKeepsWorkingAfterARollback() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val store = RoomSyncMetadataStore(database)
        val queue = RoomOutboxQueue(database)

        runCatching {
            inTransaction(database) {
                store.markSynced("projects", "cursor-1", origin)
                queue.enqueue(queued("ghost-change"))
                throw AppError.Storage("simulated failure after writing")
            }
        }
        inTransaction(database) {
            store.markSynced("workItems:abc", "cursor-2", origin)
            queue.enqueue(queued("real-change"))
        }

        // A rolled-back transaction must leave the connection usable; a store wedged after
        // one failure would turn a single bad write into a dead app.
        assertEquals(1, queue.count(null))
        assertEquals("cursor-2", store.metadata("workItems:abc")?.cursor)
    }

    @Test
    fun anUnknownErrorIsReportedAsAStorageError() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val queue = RoomOutboxQueue(database)

        val failure = assertFailsWith<AppError.Storage> {
            inTransaction(database) {
                queue.enqueue(queued("change-1"))
                throw IllegalStateException("something that is not ours")
            }
        }

        assertEquals("something that is not ours", failure.detail)
    }

    /**
     * A cancelled body stays a cancellation.
     *
     * The write before the throw is real, and it is not committed — but that is beside the
     * point. The point is what comes *out*. A `CancellationException` means the caller is
     * gone: a screen dismissed, a session scope torn down, a `runTest` body finished. Reported
     * as `AppError.Storage` it becomes a claim that the *store* is broken, and every caller
     * that treats a storage error as retryable will then do work for a coroutine that has
     * already been cancelled. A sign-out would retry writes into a scope it just reset.
     *
     * Asserted as a `CancellationException` and not as `AppError.Storage`, which is the whole
     * distinction. *Identity* is deliberately not asserted: Room's transaction machinery
     * recovers a failure out of the transaction and rethrows it, so the instance that leaves
     * is a copy carrying the same message. A test demanding the original object would be
     * asserting Room's internals rather than this function's contract, and it would fail for
     * a reason that has nothing to do with the bug.
     */
    @Test
    fun aCancelledBodyIsNotTurnedIntoAStorageFailure() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val queue = RoomOutboxQueue(database)

        val thrown = assertFailsWith<CancellationException> {
            inTransaction(database) {
                queue.enqueue(queued("change-1"))
                throw CancellationException("the caller went away")
            }
        }

        assertEquals("the caller went away", thrown.message)
        // And nothing was committed by a body that never finished, so the cancellation did
        // not leave a row behind for a reader to trip over.
        assertEquals(0, queue.count(null))
    }

    @Test
    fun thisModulesOwnErrorsPassThroughUnchanged() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val queue = RoomOutboxQueue(database)

        // `SyncService` needs to tell "this one queued row cannot be understood" apart from
        // "the database is broken". Flattening an OutboxError into a storage failure would
        // lose exactly that distinction, so it has to survive the boundary.
        val failure = assertFailsWith<OutboxError.UnknownEntity> {
            inTransaction(database) {
                throw OutboxError.UnknownEntity("taskV2")
            }
        }

        assertEquals("taskV2", failure.raw)
    }

    @Test
    fun aRollbackActuallyDiscardsTheWriteRatherThanLeavingItForTheNextReader() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val store = RoomSyncMetadataStore(database)

        runCatching {
            inTransaction(database) {
                store.markSynced("projects", "cursor-1", origin)
                throw AppError.Storage("simulated failure after writing")
            }
        }

        // Read on a *separate* transaction, so this cannot pass by reusing a cached
        // connection that never saw the write. A driver that had already committed, or one
        // whose rollback was a no-op, would show the row here.
        assertEquals(null, store.metadata("projects"))
        assertEquals(0, database.syncMetadataDao().byScope("projects")?.serverVersion ?: 0)
    }

    @Test
    fun nestedTransactionsCommitOrDiscardWithTheOutermostOne() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val store = RoomSyncMetadataStore(database)

        // Nesting is a savepoint rather than an error, so a helper that opens its own
        // transaction can be called from inside a caller's without either of them having
        // to know. The inner commit is still governed by the outer one.
        val failure = assertFailsWith<AppError.Storage> {
            inTransaction(database) {
                store.markSynced("projects", "cursor-1", origin)
                runCatching {
                    inTransaction(database) { store.markSynced("workItems:abc", "cursor-2", origin) }
                }
                throw AppError.Storage("outermost fails last")
            }
        }
        assertEquals("outermost fails last", failure.detail)

        // The inner transaction committed, but it was nested: the outer throw discards it.
        assertEquals(null, store.metadata("projects"))
        assertEquals(null, store.metadata("workItems:abc"))
    }

    @Test
    fun aTransactionThatReturnsAValueReturnsIt() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val store = RoomSyncMetadataStore(database)

        val claimed = inTransaction(database) {
            store.markSynced("projects", "cursor-1", origin)
            store.metadata("projects")
        }

        assertEquals("cursor-1", claimed?.cursor)
    }
}
