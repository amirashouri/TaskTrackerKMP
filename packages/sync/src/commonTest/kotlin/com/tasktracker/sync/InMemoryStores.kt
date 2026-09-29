package com.tasktracker.sync

import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxEntry
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.OutboxStatus
import com.tasktracker.database.SyncMetadata
import com.tasktracker.database.SyncMetadataStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An in-memory [OutboxQueue] and [SyncMetadataStore] for the common test suite.
 *
 * ## Why this is here at all
 *
 * The original tested the engine against the *real* `SwiftDataOutboxQueue`, and said why:
 * the interesting bugs in this module live in the interaction with the queue — claim
 * ordering, the pending/failed/inFlight transitions, dead-lettering — and a fake queue
 * would only assert that the engine calls the methods the test author expected it to call.
 *
 * That argument is still right, and it is not available on all three hosts: opening any Room
 * store, in memory or not, needs a platform `Context` on Android, so a common suite built on
 * `RoomOutboxQueue` would have to be switched off there exactly as `:packages:database`
 * switched off its own (see that module's build file). Losing 40 tests of Android coverage
 * to avoid a second copy of a SQL query is the wrong trade.
 *
 * So both, which is more than the original had:
 *
 *  - this queue mirrors `RoomOutboxQueue`'s statements one for one, including the two queries
 *    whose subtlety *is* the behaviour (`fail`'s post-claim attempt comparison and
 *    `recoverStranded`'s refund), and the whole common suite runs on all three targets;
 *  - `RoomBackedSyncTest` in `jvmTest` runs the same claim / fail / dead-letter / release
 *    interactions against the real Room queue, so a divergence between this copy and the SQL
 *    fails the build instead of hiding until Android.
 *
 * Rows are held as a private record and projected to [OutboxEntry] on the way out, because
 * that is how the real store works too: `OutboxRecord` is a mutable Room row and
 * `OutboxEntry` is the immutable value the engine sees. An `OutboxEntry` has no `copy`, so a
 * queue that tried to mutate entries in place could not be written at all.
 */
class InMemoryOutboxQueue : OutboxQueue {

    private data class Record(
        val id: String,
        val entity: OutboxEntity,
        val entityID: String,
        val operation: OutboxOperation,
        val payload: ByteArray,
        val userID: String?,
        val createdAt: Instant,
        val status: OutboxStatus,
        val attemptCount: Int,
        val lastAttemptAt: Instant?,
        val lastError: String?,
    ) {
        fun toEntry(): OutboxEntry = OutboxEntry(
            id = id,
            entity = entity,
            entityID = entityID,
            operation = operation,
            payload = payload,
            userID = userID,
            createdAt = createdAt,
            status = status,
            attemptCount = attemptCount,
            lastError = lastError,
        )
    }

    private val mutex = Mutex()
    private val rows = mutableListOf<Record>()
    private var now: () -> Instant = { Clock.System.now() }

    /** The attempt charge and the last-attempt stamp come from a fixed clock, so a test can
     * assert them without reading a wall clock it cannot control. */
    fun freezeClock(at: Instant) {
        now = { at }
    }

    override suspend fun claim(limit: Int, maxAttempts: Int, userID: String?): List<OutboxEntry> =
        mutex.withLock {
            if (limit <= 0) return@withLock emptyList()
            // `RoomOutboxQueue.claim`'s `claimable` query, in order: pending or failed, under
            // the attempt cap, this account's rows or the unscoped ones, oldest first with the
            // id as a tie-break so two changes written in the same millisecond still have one
            // defined order.
            val due = rows
                .filter { it.status == OutboxStatus.Pending || it.status == OutboxStatus.Failed }
                .filter { it.attemptCount < maxAttempts }
                .filter { userID == null || it.userID == null || it.userID == userID }
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
                .take(limit)
            if (due.isEmpty()) return@withLock emptyList()

            val at = now()
            val claimed = due.map {
                it.copy(status = OutboxStatus.InFlight, attemptCount = it.attemptCount + 1, lastAttemptAt = at)
            }
            commit(claimed)
            // Decoded after the write, exactly as the Room queue does it: a row this build
            // cannot understand must leave the store as it was found.
            claimed.map { it.toEntry() }
        }

    override suspend fun complete(ids: List<String>) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        rows.removeAll { ids.contains(it.id) }
    }

    override suspend fun fail(ids: List<String>, message: String, maxAttempts: Int) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        // The attempt was already counted when the row was claimed, so exceeding the cap is
        // what dead-letters, and the row stays visible instead of vanishing.
        forEachByIds(ids) {
            val status = if (it.attemptCount >= maxAttempts) OutboxStatus.Dead else OutboxStatus.Failed
            it.copy(status = status, lastError = message)
        }
    }

    override suspend fun reject(ids: List<String>, message: String) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        forEachByIds(ids) { it.copy(status = OutboxStatus.Dead, lastError = message) }
    }

    override suspend fun release(ids: List<String>, message: String) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        // Back to pending without charging an attempt, and never below zero: a row released
        // more times than it was claimed settles at zero instead of going negative and
        // staying unclaimable forever.
        forEachByIds(ids) {
            it.copy(
                status = OutboxStatus.Pending,
                attemptCount = maxOf(0, it.attemptCount - 1),
                lastError = message,
            )
        }
    }

    override suspend fun recoverStrandedEntries(): Int = mutex.withLock {
        var recovered = 0
        val stranded = rows.filter { it.status == OutboxStatus.InFlight }
        for (record in stranded) {
            recovered++
            commit(
                listOf(
                    record.copy(
                        status = OutboxStatus.Pending,
                        attemptCount = maxOf(0, record.attemptCount - 1),
                    ),
                ),
            )
        }
        recovered
    }

    override suspend fun entries(): List<OutboxEntry> = mutex.withLock { rows.map { it.toEntry() } }

    override suspend fun count(status: OutboxStatus?): Int = mutex.withLock {
        if (status == null) rows.size else rows.count { it.status == status }
    }

    override suspend fun removeAll() = mutex.withLock { rows.clear() }

    override suspend fun enqueue(entry: NewOutboxEntry): Unit = mutex.withLock {
        rows.add(
            Record(
                id = entry.id,
                entity = entry.entity,
                entityID = entry.entityID,
                operation = entry.operation,
                payload = entry.payload,
                userID = entry.userID,
                createdAt = entry.createdAt,
                status = OutboxStatus.Pending,
                attemptCount = 0,
                lastAttemptAt = null,
                lastError = null,
            ),
        )
        Unit
    }

    /** Replaces the stored rows for these ids, keyed the way the primary key is. */
    private fun commit(updated: List<Record>) {
        for (record in updated) {
            val index = rows.indexOfFirst { it.id == record.id }
            if (index >= 0) rows[index] = record
        }
    }

    private fun forEachByIds(ids: List<String>, transform: (Record) -> Record) {
        commit(rows.filter { ids.contains(it.id) }.map(transform))
    }
}

/**
 * The metadata store, in a map.
 *
 * A [Mutex] for the same reason as the queue: the engine writes a scope's row from
 * `markSynced` on one coroutine and reads it back from `metadata` on another.
 */
class InMemorySyncMetadataStore : SyncMetadataStore {
    private val mutex = Mutex()
    private val rows = mutableMapOf<String, SyncMetadata>()

    override suspend fun metadata(scope: String): SyncMetadata? = mutex.withLock { rows[scope] }

    override suspend fun record(metadata: SyncMetadata) = mutex.withLock { rows[metadata.scope] = metadata }

    override suspend fun markSynced(scope: String, cursor: String?, at: Instant) = mutex.withLock {
        val row = recordFor(scope)
        rows[scope] = SyncMetadata(
            scope = row.scope,
            cursor = cursor,
            lastSyncedAt = at,
            lastPushedAt = row.lastPushedAt,
            serverVersion = row.serverVersion,
            lastError = null,
        )
    }

    override suspend fun markPushed(scope: String, at: Instant) = mutex.withLock {
        val row = recordFor(scope)
        rows[scope] = SyncMetadata(
            scope = row.scope,
            cursor = row.cursor,
            lastSyncedAt = row.lastSyncedAt,
            lastPushedAt = at,
            serverVersion = row.serverVersion,
            lastError = null,
        )
    }

    override suspend fun recordFailure(scope: String, message: String) = mutex.withLock {
        val row = recordFor(scope)
        rows[scope] = SyncMetadata(
            scope = row.scope,
            cursor = row.cursor,
            lastSyncedAt = row.lastSyncedAt,
            lastPushedAt = row.lastPushedAt,
            serverVersion = row.serverVersion,
            lastError = message,
        )
    }

    override suspend fun removeAll() = mutex.withLock { rows.clear() }

    private fun recordFor(scope: String): SyncMetadata = rows[scope] ?: SyncMetadata(
        scope = scope,
        cursor = null,
        lastSyncedAt = null,
        lastPushedAt = null,
        serverVersion = 0,
        lastError = null,
    )
}

private val ORIGIN: Instant = Instant.fromEpochSeconds(1_700_000_000)

/** An outbox entry to queue. */
fun newEntry(
    id: String,
    entity: OutboxEntity = OutboxEntity.WorkItem,
    entityID: String? = null,
    operation: OutboxOperation = OutboxOperation.Upsert,
    offsetSeconds: Long = 0,
    userID: String? = null,
): NewOutboxEntry = NewOutboxEntry(
    id = id,
    entity = entity,
    entityID = entityID ?: id,
    operation = operation,
    payload = """{"title":"task"}""".encodeToByteArray(),
    createdAt = ORIGIN + offsetSeconds.seconds,
    userID = userID,
)

/** A change the server has, for a pull. */
fun remoteChange(
    entityID: String,
    entity: OutboxEntity = OutboxEntity.WorkItem,
    operation: OutboxOperation = OutboxOperation.Upsert,
    version: String? = null,
): RemoteChange = RemoteChange(
    entity = entity,
    entityID = entityID,
    operation = operation,
    payload = """{"title":"from server"}""".encodeToByteArray(),
    version = version,
)
