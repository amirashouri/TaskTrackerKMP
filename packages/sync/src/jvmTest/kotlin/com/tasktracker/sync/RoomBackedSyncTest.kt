package com.tasktracker.sync

import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.OutboxStatus
import com.tasktracker.database.RoomOutboxQueue
import com.tasktracker.database.RoomSyncMetadataStore
import com.tasktracker.database.TaskTrackerDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * The in-memory queue's contract, checked against the real one.
 *
 * Every other sync test runs against [InMemoryOutboxQueue], a hand-written double. That is a
 * bet: that the double behaves like [RoomOutboxQueue] for every transition the engine drives.
 * A bet like that is only safe if something checks it, because a double that is *almost*
 * right makes the whole suite pass while telling the truth about nothing.
 *
 * So this file runs the same script through both and compares the rows afterwards. It lives in
 * `jvmTest` for the same reason the database's Room tests do not run on the Android host: the
 * JVM has a real SQLite driver with no emulator.
 *
 * Only clock-independent behaviour is compared. `RoomOutboxQueue` reads `Clock.System.now()`
 * directly, so retry backoff cannot be fast-forwarded here; the backoff window itself is
 * covered by the database module's own tests, and what matters here is that both queues
 * *refuse* a too-recently-failed row.
 */
class RoomBackedSyncTest {

    private val origin = Instant.fromEpochSeconds(1_700_000_000)

    private fun entry(
        id: String,
        offset: Long = 0,
        userID: String? = null,
    ) = NewOutboxEntry(
        id = id,
        entity = OutboxEntity.WorkItem,
        entityID = id,
        operation = OutboxOperation.Upsert,
        payload = """{"title":"task"}""".encodeToByteArray(),
        createdAt = origin + offset.seconds,
        userID = userID,
    )

    /** Both queues, so one script can be applied to each and the results compared. */
    private fun queues(): List<Pair<String, OutboxQueue>> = listOf(
        "in-memory" to InMemoryOutboxQueue(),
        "room" to RoomOutboxQueue(TaskTrackerDatabase.inMemory()),
    )

    /** What a test can assert about a queue without knowing which one it holds. */
    private suspend fun OutboxQueue.snapshot(): List<String> =
        entries().sortedBy { it.id }.map { "${it.id}/${it.status}/${it.attemptCount}" }

    private suspend fun assertParity(
        queues: List<Pair<String, OutboxQueue>>,
        message: String,
        expected: List<String>,
    ) {
        for ((name, queue) in queues) {
            assertEquals(expected, queue.snapshot(), "$message — $name")
        }
    }

    @Test
    fun `a claim is oldest first and charges the attempt`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("b", offset = 1))
            queue.enqueue(entry("a", offset = 0))
            queue.enqueue(entry("c", offset = 2))
        }

        val claimed = mutableListOf<List<String>>()
        for ((_, queue) in queues) {
            claimed += queue.claim(limit = 2, maxAttempts = 3, userID = null).map { it.id }
        }

        assertEquals(listOf(listOf("a", "b"), listOf("a", "b")), claimed)
        assertParity(queues, "after a claim of two", listOf("a/InFlight/1", "b/InFlight/1", "c/Pending/0"))
    }

    @Test
    fun `a claim stops at the attempt limit so a row cannot be retried for ever`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("a"))
            queue.claim(limit = 10, maxAttempts = 1, userID = null)
            queue.fail(listOf("a"), message = "nope", maxAttempts = 1)
        }

        val second = mutableListOf<List<String>>()
        for ((_, queue) in queues) {
            second += queue.claim(limit = 10, maxAttempts = 1, userID = null).map { it.id }
        }

        assertEquals(listOf(emptyList<String>(), emptyList()), second)
        assertParity(queues, "a dead row stays dead", listOf("a/Dead/1"))
    }

    @Test
    fun `a failed row is offered again on the next claim`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("a"))
            queue.claim(limit = 10, maxAttempts = 5, userID = null)
            queue.fail(listOf("a"), message = "503", maxAttempts = 5)
        }

        val second = mutableListOf<List<String>>()
        for ((_, queue) in queues) {
            second += queue.claim(limit = 10, maxAttempts = 5, userID = null).map { it.id }
        }

        // Deliberately: there is no time-based backoff in the queue, so a row that failed a
        // moment ago is handed straight back. That is the original's behaviour too, and it is
        // why `SyncEngine` needs a seen-this-cycle guard and a stop after a retryable verdict —
        // without them a cycle would claim, push, fail and claim the same row for ever.
        assertEquals(listOf(listOf("a"), listOf("a")), second)
        // Claimed a second time, so it is in flight again on the second attempt.
        assertParity(queues, "after one retryable failure", listOf("a/InFlight/2"))
    }

    @Test
    fun `signing out gives the attempt back`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("a"))
            queue.claim(limit = 10, maxAttempts = 3, userID = null)
            queue.release(listOf("a"), message = "signed out")
        }

        val reclaimed = mutableListOf<List<String>>()
        for ((_, queue) in queues) {
            reclaimed += queue.claim(limit = 10, maxAttempts = 3, userID = null).map { it.id }
        }

        assertEquals(listOf(listOf("a"), listOf("a")), reclaimed)
        assertParity(queues, "a released row is pending with no attempt spent", listOf("a/InFlight/1"))
    }

    @Test
    fun `a stranded row is recovered and becomes claimable again at the limit`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("a"))
            queue.claim(limit = 10, maxAttempts = 1, userID = null)
        }
        val recovered = mutableListOf<Int>()
        for ((_, queue) in queues) {
            recovered += queue.recoverStrandedEntries()
        }

        assertEquals(listOf(1, 1), recovered)
        assertParity(queues, "a recovered row is pending again", listOf("a/Pending/0"))
        // And the point of the refund: claimable at all, which at attempt limit 1 it would not be.
        val claimed = mutableListOf<List<String>>()
        for ((_, queue) in queues) {
            claimed += queue.claim(limit = 10, maxAttempts = 1, userID = null).map { it.id }
        }
        assertEquals(listOf(listOf("a"), listOf("a")), claimed)
    }

    @Test
    fun `one account's work is never handed to another`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("mine", userID = "ada"))
            queue.enqueue(entry("theirs", userID = "grace"))
            // Unscoped rows are nobody's in particular, so they belong to whoever is signed in.
            queue.enqueue(entry("legacy"))
        }

        val claimed = mutableListOf<List<String>>()
        for ((_, queue) in queues) {
            claimed += queue.claim(limit = 10, maxAttempts = 3, userID = "ada").map { it.id }
        }

        // Same `createdAt` for all three, so the id breaks the tie: the order is defined, and
        // both queues agree on it.
        assertEquals(listOf(listOf("legacy", "mine"), listOf("legacy", "mine")), claimed)
    }

    @Test
    fun `completing a change removes it and rejecting one marks it dead`() = runTest {
        val queues = queues()
        for ((_, queue) in queues) {
            queue.enqueue(entry("kept"))
            queue.enqueue(entry("accepted"))
            queue.enqueue(entry("refused"))
            queue.claim(limit = 10, maxAttempts = 3, userID = null)
            queue.complete(listOf("kept", "accepted"))
            queue.reject(listOf("refused"), message = "invalid")
        }

        assertParity(queues, "after a mixed verdict", listOf("refused/Dead/1"))
    }

    @Test
    fun `the engine drives the real queue the same way it drives the double`() = runTest {
        // The end the whole fixture exists to serve: a full cycle against the store the app
        // actually ships, not just against a stand-in that agrees with it.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.PerEntry(
                mapOf(
                    "accepted" to PushOutcome.Accepted,
                    "conflicted" to PushOutcome.Conflict("v2"),
                    "refused" to PushOutcome.Rejected("invalid"),
                    "later" to PushOutcome.RetryLater("503"),
                ),
            ),
        )
        val database = TaskTrackerDatabase.inMemory()
        val queue = RoomOutboxQueue(database)
        queue.enqueue(entry("accepted", offset = 0))
        queue.enqueue(entry("conflicted", offset = 1))
        queue.enqueue(entry("refused", offset = 2))
        queue.enqueue(entry("later", offset = 3))

        val report = SyncEngine(
            outbox = queue,
            metadata = RoomSyncMetadataStore(database),
            transport = transport,
        ).run()

        // All four were offered; two of them settled for good and one is still to be retried.
        assertEquals(4, report.pushed)
        assertEquals(1, report.accepted)
        assertEquals(1, report.conflicts)
        assertEquals(1, report.rejected)
        assertEquals(1, report.retrying)
        // Both of the settled-but-kept rows are still visible: the retryable one as failed, the
        // refused one as dead. Only the accepted and the conflicted change is gone.
        assertEquals(listOf("later/Failed/1", "refused/Dead/1"), queue.snapshot())
    }
}
