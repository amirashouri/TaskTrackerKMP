package com.tasktracker.database

/**
 * The seam `SyncService` depends on.
 *
 * It speaks in [OutboxEntry] values and knows nothing about Room, so the sync engine can
 * be tested against an in-memory queue with no store behind it.
 */
interface OutboxQueue {
    /**
     * Atomically takes up to [limit] due changes, oldest first, and marks them in flight.
     * Claiming and marking happen together so a crash cannot double-push.
     *
     * [userID] scopes the claim to one account's rows. Null claims everything, which is
     * the shape a store with no session concept takes; a queue fed by `SessionCache`
     * passes the signed-in user so another account's queued work is never pushed under
     * the wrong session.
     */
    suspend fun claim(limit: Int, maxAttempts: Int, userID: String?): List<OutboxEntry>

    /** Deletes entries the server accepted. */
    suspend fun complete(ids: List<String>)

    /**
     * Records a retryable failure, moving entries to `dead` once they exceed
     * [maxAttempts].
     */
    suspend fun fail(ids: List<String>, message: String, maxAttempts: Int)

    /**
     * Dead-letters entries immediately, skipping the remaining attempts.
     *
     * A separate operation from [fail] because a server that answers "this can never be
     * accepted" should not be asked again four more times: each retry is a wakeup, a
     * radio, and a guaranteed identical answer, and the entry's attempt budget is better
     * spent on changes that might still land.
     *
     * The body is `fail` with a zero attempt cap, which is exactly "there are no attempts
     * left". A default method rather than a separate requirement so an in-memory double
     * only has to implement [fail]; a backend that can dead-letter without a write is free
     * to override this. It has to be a default *method* and not an extension: Kotlin
     * members always shadow extensions, so a same-signature extension would simply never
     * be called, which is a quietly dead API rather than a compile error.
     */
    suspend fun reject(ids: List<String>, message: String) {
        fail(ids, message, maxAttempts = 0)
    }

    /**
     * Returns entries stranded in `inFlight` by a crash or a kill to `pending`, giving
     * back the attempt they were charged at claim time.
     */
    suspend fun recoverStrandedEntries(): Int

    /**
     * Returns in-flight entries to `pending` **without** charging an attempt.
     *
     * For failures that are not the change's fault — the session ended, the user signed
     * out. The attempt was already counted when the batch was claimed, and spending it
     * here would mean a sign-out could dead-letter work the user never had the chance to
     * send. [fail] deliberately does charge, because a server that is merely unreachable
     * must eventually stop being retried.
     */
    suspend fun release(ids: List<String>, message: String)

    suspend fun entries(): List<OutboxEntry>

    suspend fun count(status: OutboxStatus?): Int

    suspend fun removeAll()

    /**
     * Appends a change. Not part of [OutboxQueue] because repositories enqueue inside the
     * same transaction as their entity write, which must not be split in two.
     */
    suspend fun enqueue(entry: NewOutboxEntry)
}

/** The unscoped claim: every due row, regardless of which account queued it. */
suspend fun OutboxQueue.claim(limit: Int, maxAttempts: Int): List<OutboxEntry> =
    claim(limit = limit, maxAttempts = maxAttempts, userID = null)
