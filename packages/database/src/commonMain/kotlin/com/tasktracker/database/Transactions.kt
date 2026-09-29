package com.tasktracker.database

import androidx.room.TypeConverter
import com.tasktracker.core.AppError
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlin.time.Instant

/**
 * Room has no built-in mapping for [Instant], and neither should it: an instant is not a
 * number, and a number in a column is exactly how time zones and sign errors get in.
 *
 * Stored as epoch **milliseconds**, which is the same granularity the original's ordering
 * guarantee assumed when it broke ties between changes "written in the same millisecond".
 * Sub-millisecond precision is not preserved, and nothing needs it: a tie is broken by id.
 */
class DatabaseConverters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.let { it.toEpochMilliseconds() }

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }
}

/**
 * Runs [body] and commits it as one unit, or leaves nothing behind.
 *
 * This is the whole point of the module's design. A repository has to write its entity
 * *and* the outbox row inside one transaction, and if those two writes can land in
 * different transactions the queue can disagree with the store: a mutation saved with
 * nothing queued to push it, or an outbox row pointing at an entity that was never
 * written. Neither is recoverable by a retry.
 *
 * Room's transaction is a real `BEGIN`/`ROLLBACK` around the SQLite connection, so a
 * throw inside [body] discards everything the body wrote — including the writes of a DAO
 * call made from inside it, which is why a caller may freely mix `insert` and `update`
 * statements here.
 *
 * This is strictly better than the original's `ModelContext.rollback()`, which had to be
 * called by hand and relied on nothing having been saved yet: SwiftData has no transaction
 * spanning `save()` calls, so the original could only offer "mutate, save once, and
 * remember to roll back". Here the rollback is the transaction's own behaviour and cannot
 * be forgotten.
 *
 * Nested calls are the one thing to avoid: Room throws rather than nesting, because the
 * inner transaction is a different connection. Compose repository work with a single call
 * that does the whole unit, not two that each open their own.
 */
suspend fun <T> inTransaction(
    database: TaskTrackerDatabase,
    body: suspend () -> T,
): T = try {
    database.useWriterConnection { connection -> connection.immediateTransaction { body() } }
} catch (failure: Throwable) {
    // This module's own errors are meaningful to callers and are allowed through
    // untouched — `SyncService` needs to tell "this one queued row cannot be understood"
    // apart from "the database is broken", and flattening both into a storage failure
    // would lose that. Anything unrecognised, such as a raw SQLite error, becomes a
    // storage failure: a store that cannot be written is not a recoverable condition and
    // must never be mistaken for an auth or network problem.
    throw when (failure) {
        is AppError, is OutboxError -> failure
        else -> AppError.Storage(failure.readable())
    }
}

/**
 * A message worth showing a user, from an exception that may not have one.
 *
 * `SimpleDateFormat`-style messages are not the issue; the issue is that an SQLite
 * failure can arrive as a bare `SQLiteException` with an empty message, and
 * "Could not open the local database: " tells nobody anything. Falls back through the
 * type name so the cause is at least nameable.
 */
internal fun Throwable.readable(): String {
    val message = message?.trim().orEmpty()
    if (message.isNotEmpty()) return message
    val type = this::class.simpleName
    return if (type.isNullOrEmpty()) "unknown error" else type
}
