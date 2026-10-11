package com.tasktracker.database

import kotlin.time.Clock

/**
 * Room-backed outbox.
 *
 * A plain class holding the database rather than an actor, and that is a deliberate
 * difference from the original rather than a simplification. SwiftData's `ModelContext`
 * explicitly refuses `Sendable` and every store had to live inside a `@ModelActor` just
 * to satisfy that; a `RoomDatabase` is thread-safe and serialises its own transactions,
 * so an actor here would add a hop to every access and then have to be careful not to
 * hold it across a transaction. Room is the thing providing the isolation, so the stores
 * get out of the way.
 */
class RoomOutboxQueue(
    private val database: TaskTrackerDatabase,
) : OutboxQueue {
    private val dao: OutboxDao get() = database.outboxDao()

    override suspend fun claim(
        limit: Int,
        maxAttempts: Int,
        userID: String?,
    ): List<OutboxEntry> {
        if (limit <= 0) return emptyList()
        return inTransaction(database) {
            val due = dao.claimable(userID, maxAttempts, limit)
            if (due.isEmpty()) return@inTransaction emptyList()
            val now = Clock.System.now()
            val claimed = due.map { record ->
                record.copy(
                    status = OutboxStatus.InFlight.wireValue,
                    attemptCount = record.attemptCount + 1,
                    lastAttemptAt = now,
                )
            }
            dao.update(claimed)
            // Decoded after the write, so a row this build cannot understand leaves the
            // database exactly as it found it: the throw rolls the update back.
            claimed.map { it.toEntry() }
        }
    }

    override suspend fun complete(ids: List<String>) {
        if (ids.isEmpty()) return
        inTransaction(database) { dao.deleteByIds(ids) }
    }

    override suspend fun fail(ids: List<String>, message: String, maxAttempts: Int) {
        if (ids.isEmpty()) return
        inTransaction(database) { dao.fail(ids, message, maxAttempts) }
    }

    override suspend fun reject(ids: List<String>, message: String) {
        if (ids.isEmpty()) return
        inTransaction(database) { dao.reject(ids, message) }
    }

    override suspend fun release(ids: List<String>, message: String) {
        if (ids.isEmpty()) return
        inTransaction(database) { dao.release(ids, message) }
    }

    override suspend fun recoverStrandedEntries(): Int = inTransaction(database) {
        dao.recoverStranded()
    }

    override suspend fun entries(): List<OutboxEntry> = inTransaction(database) {
        dao.all().map { it.toEntry() }
    }

    override suspend fun count(status: OutboxStatus?): Int = inTransaction(database) {
        if (status == null) dao.count() else dao.countByStatus(status.wireValue)
    }

    override suspend fun removeAll() {
        inTransaction(database) { dao.deleteAll() }
    }

    override suspend fun enqueue(entry: NewOutboxEntry) {
        inTransaction(database) { dao.insert(entry.toRecord()) }
    }
}
