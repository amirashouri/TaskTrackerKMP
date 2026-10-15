package com.tasktracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Reads and writes the outbox.
 *
 * Every statement that a caller needs to be atomic with something else is a single
 * `@Query` rather than a read-modify-write pair, so the database — not the caller — is
 * what makes it atomic. [claimable] plus `markInFlight` are the exception, and they are
 * only ever called inside one [inTransaction] by the queue.
 */
@Dao
interface OutboxDao {
    /**
     * The due rows, oldest first, capped at [limit].
     *
     * Ordering is the subtle part. Changes are claimed strictly oldest-first because the
     * server applies them in order: pushing "task 7 updated" before "task 7 created"
     * would be rejected as an unknown id, and the queue would wedge. Ties break on id so
     * two changes written in the same millisecond still have one defined order.
     *
     * A null [userID] claims every account's rows, which is the shape a store with no
     * session concept takes. Otherwise only this account's rows, plus null-user rows:
     * those are legacy entries written before the queue was user-scoped, and they belong
     * to whoever is signed in now, because there is no one else they could belong to.
     *
     * Both the scoped and unscoped forms are one query here where the original needed two
     * fetch descriptors, because `:userID IS NULL` is exactly the unscoped predicate.
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE status IN ('pending', 'failed')
          AND attemptCount < :maxAttempts
          AND (:userID IS NULL OR userID IS NULL OR userID = :userID)
        ORDER BY createdAt ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun claimable(userID: String?, maxAttempts: Int, limit: Int): List<OutboxRecord>

    @Update
    suspend fun update(records: List<OutboxRecord>)

    @Query("SELECT * FROM outbox WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<OutboxRecord>

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * A retryable failure, dead-lettering whatever has run out of attempts.
     *
     * The attempt was already counted when the row was claimed, so exceeding the cap is
     * what dead-letters, and the row stays visible instead of vanishing. `attemptCount` is
     * read inside the same statement so a row is never judged against a stale count.
     */
    @Query(
        """
        UPDATE outbox
        SET lastError = :message,
            status = CASE WHEN attemptCount >= :maxAttempts THEN 'dead' ELSE 'failed' END
        WHERE id IN (:ids)
        """,
    )
    suspend fun fail(ids: List<String>, message: String, maxAttempts: Int)

    /**
     * Dead-letters immediately, skipping the remaining attempts.
     *
     * A separate statement from [fail] because a server that answers "this can never be
     * accepted" should not be asked again four more times: each retry is a wakeup, a
     * radio, and a guaranteed identical answer, and the entry's attempt budget is better
     * spent on changes that might still land.
     */
    @Query("UPDATE outbox SET lastError = :message, status = 'dead' WHERE id IN (:ids)")
    suspend fun reject(ids: List<String>, message: String)

    /**
     * The rows [reject] took out of circulation, oldest first.
     *
     * Oldest first for the same reason [claimable] is: a caller putting them back has to
     * put them back in the order the server will apply them, and re-queueing out of order
     * is how a work item ends up ahead of the project that has to exist first.
     *
     * Scoped like [claimable] and with the same predicate, so the set a caller sees here is
     * exactly the set it could later claim.
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE status = 'dead'
          AND (:userID IS NULL OR userID IS NULL OR userID = :userID)
        ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun deadLetters(userID: String?): List<OutboxRecord>

    /**
     * Puts one dead row back in the queue, with [payload] replacing what it was refused for.
     *
     * **The id and `createdAt` are deliberately untouched.** They are the row's position in
     * the queue, and a retry that re-dated it would put a change back *after* everything
     * queued since — which is the wrong order for the changes that depend on each other.
     * Rewriting the payload in place is what keeps a retried batch in the order the user's
     * actions actually happened.
     *
     * `attemptCount` resets rather than continuing. The budget exists to stop a server that
     * is merely unreachable from being retried forever; a row the user has asked to send
     * again has earned a fresh one, and keeping the old count would make the very next
     * failure dead-letter it on the spot.
     *
     * `lastError` goes with it: the row is queued again, so the verdict it just had is
     * answered and carrying it forward would show a stale refusal against a change that has
     * not been sent yet.
     *
     * The `status = 'dead'` guard is the whole of the write's safety. A row that is in
     * flight, or that was already re-queued by a second press, is not touched — so pressing
     * the control twice cannot demote a row that is mid-push back to pending underneath the
     * engine holding it.
     *
     * @return 1 when the row was revived, 0 when it was not dead any more.
     */
    @Query(
        """
        UPDATE outbox
        SET payload = :payload,
            status = 'pending',
            attemptCount = 0,
            lastError = NULL,
            lastAttemptAt = NULL
        WHERE id = :id AND status = 'dead'
        """,
    )
    suspend fun revive(id: String, payload: ByteArray): Int

    /**
     * Back to pending **without** charging an attempt.
     *
     * For failures that are not the change's fault — the session ended, the user signed
     * out. The attempt was already counted when the batch was claimed, and spending it
     * here would mean a sign-out could dead-letter work the user never had the chance to
     * send. [fail] deliberately does charge, because a server that is merely unreachable
     * must eventually stop being retried.
     *
     * `max(attemptCount - 1, 0)` is two-argument `max`, the scalar one, not the
     * aggregate — a row released more times than it was claimed settles at zero instead
     * of going negative and staying unclaimable forever.
     */
    @Query(
        "UPDATE outbox SET lastError = :message, status = 'pending', " +
            "attemptCount = max(attemptCount - 1, 0) WHERE id IN (:ids)",
    )
    suspend fun release(ids: List<String>, message: String)

    /**
     * Returns entries stranded in `inFlight` by a crash or a kill to `pending`, giving
     * back the attempt they were charged at claim time.
     *
     * Giving the attempt back is not cosmetic. The queue charges an attempt up front, and
     * a row that was claimed and then stranded has no verdict from the server — the
     * process died mid-push. Leaving the charge in place means an entry stranded on its
     * last permitted attempt can never be claimed again (`attemptCount < maxAttempts` is
     * false from then on), so it sits in the queue forever: never pushed, never
     * dead-lettered, never visible as a failure. The user is told their change is saved
     * and it silently never syncs.
     */
    @Query(
        "UPDATE outbox SET status = 'pending', attemptCount = max(attemptCount - 1, 0) " +
            "WHERE status = 'inFlight'",
    )
    suspend fun recoverStranded(): Int

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC, id ASC")
    suspend fun all(): List<OutboxRecord>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("DELETE FROM outbox")
    suspend fun deleteAll()

    /**
     * Appends a change. Not part of the queue's protocol because repositories enqueue
     * inside the same transaction as their entity write, which must not be split in two.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: OutboxRecord)
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE scope = :scope")
    suspend fun byScope(scope: String): SyncMetadataRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SyncMetadataRecord)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session LIMIT 1")
    suspend fun current(): SessionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SessionRecord)

    @Query("DELETE FROM session")
    suspend fun deleteAll()
}
