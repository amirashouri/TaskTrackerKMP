package com.tasktracker.database

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * Each test builds its own in-memory store, which is cheap and keeps tests independent
 * and parallelisable. The original had to document that as a hazard — its in-memory store
 * was process-wide, so only one could exist per test process — because `ModelContainer`
 * could not be closed and reopened cheaply. Here a store is a value that is simply
 * dropped, so there is nothing to leak between tests.
 */
private val ORIGIN = Instant.fromEpochSeconds(1_700_000_000)

private const val PAYLOAD = """{"title":"task"}"""

class OutboxQueueTest {
    private fun queue(): OutboxQueue = RoomOutboxQueue(TaskTrackerDatabase.inMemory())

    private fun entry(
        id: String,
        entity: OutboxEntity = OutboxEntity.WorkItem,
        entityID: String? = null,
        operation: OutboxOperation = OutboxOperation.Upsert,
        offset: Double = 0.0,
        userID: String? = null,
    ) = NewOutboxEntry(
        id = id,
        entity = entity,
        entityID = entityID ?: id,
        operation = operation,
        payload = PAYLOAD.encodeToByteArray(),
        createdAt = ORIGIN + offset.seconds,
        userID = userID,
    )

    @Test
    fun aQueuedChangeIsClaimableAndCarriesItsPayloadIntact() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))

        val claimed = queue.claim(limit = 10, maxAttempts = 3)

        assertEquals(1, claimed.size)
        assertEquals("change-1", claimed[0].id)
        assertEquals(OutboxEntity.WorkItem, claimed[0].entity)
        assertEquals(OutboxOperation.Upsert, claimed[0].operation)
        assertContentEquals(PAYLOAD.encodeToByteArray(), claimed[0].payload)
    }

    @Test
    fun changesAreClaimedOldestFirstBecauseTheServerAppliesThemInOrder() = runTest {
        val queue = queue()
        // Enqueued newest-first on purpose: the queue must sort, not trust arrival.
        queue.enqueue(entry("third", offset = 20.0))
        queue.enqueue(entry("first", offset = 0.0))
        queue.enqueue(entry("second", offset = 10.0))

        val claimed = queue.claim(limit = 10, maxAttempts = 3)

        assertEquals(listOf("first", "second", "third"), claimed.map { it.id })
    }

    @Test
    fun tiesAreBrokenByIdSoSameMillisecondChangesStillHaveOneOrder() = runTest {
        val queue = queue()
        // All three at the same instant, which is the case the sort cannot resolve on
        // its own. Without the id tie-break the order would be whatever SQLite happened
        // to return, and the server would apply "created" after "updated".
        queue.enqueue(entry("c"))
        queue.enqueue(entry("a"))
        queue.enqueue(entry("b"))

        val claimed = queue.claim(limit = 10, maxAttempts = 3)

        assertEquals(listOf("a", "b", "c"), claimed.map { it.id })
    }

    @Test
    fun claimingRespectsTheBatchLimit() = runTest {
        val queue = queue()
        for (index in 0..4) queue.enqueue(entry("change-$index", offset = index.toDouble()))

        assertEquals(2, queue.claim(limit = 2, maxAttempts = 3).size)
        assertEquals(3, queue.claim(limit = 100, maxAttempts = 3).size)
        assertTrue(queue.claim(limit = 0, maxAttempts = 3).isEmpty())
    }

    @Test
    fun aNegativeLimitClaimsNothingRatherThanThrowing() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))

        assertTrue(queue.claim(limit = -1, maxAttempts = 3).isEmpty())
        // Still claimable afterwards, so the early return did not consume it.
        assertEquals(1, queue.claim(limit = 10, maxAttempts = 3).size)
    }

    @Test
    fun claimingMarksTheChangeInFlightSoItCannotBePushedTwice() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))

        val first = queue.claim(limit = 10, maxAttempts = 3)
        val second = queue.claim(limit = 10, maxAttempts = 3)

        assertEquals(1, first.size)
        assertTrue(second.isEmpty(), "an in-flight change must not be claimable again")
        assertEquals(OutboxStatus.InFlight, queue.entries()[0].status)
        assertEquals(1, queue.entries()[0].attemptCount)
    }

    @Test
    fun anAcceptedChangeLeavesTheQueue() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        val claimed = queue.claim(limit = 10, maxAttempts = 3)

        queue.complete(claimed.map { it.id })

        assertEquals(0, queue.count(null))
    }

    @Test
    fun completingAnEmptyListIsANoOpRatherThanInvalidSql() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))

        // `IN ()` is a syntax error in SQLite, so the guard has to happen in Kotlin. A
        // sync engine that completes nothing must not take the whole push down with it.
        queue.complete(emptyList())

        assertEquals(1, queue.count(null))
    }

    @Test
    fun aFailedChangeIsRetriedUntilItExhaustsItsAttemptsThenDeadLettered() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        val maxAttempts = 2

        // Attempt 1: fails, below the cap, so it stays claimable.
        val first = queue.claim(limit = 10, maxAttempts = maxAttempts)
        queue.fail(first.map { it.id }, "no network", maxAttempts)
        assertEquals(OutboxStatus.Failed, queue.entries()[0].status)
        assertEquals("no network", queue.entries()[0].lastError)

        // Attempt 2: claiming is the last thing that happens before the cap is reached.
        val second = queue.claim(limit = 10, maxAttempts = maxAttempts)
        assertEquals(1, second.size)
        queue.fail(second.map { it.id }, "no network", maxAttempts)

        // Dead, and still present so the user can see it never made it.
        assertEquals(OutboxStatus.Dead, queue.entries()[0].status)
        assertEquals(1, queue.count(OutboxStatus.Dead))
        assertTrue(queue.claim(limit = 10, maxAttempts = maxAttempts).isEmpty())
    }

    @Test
    fun theLastFailureIsKeptUntilSomethingReplacesIt() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))

        val claimed = queue.claim(limit = 10, maxAttempts = 5)
        queue.fail(claimed.map { it.id }, "no network", 5)

        // Claiming again does not clear the message. It describes the last failure, not the
        // outcome of the attempt now in flight — and the original behaved the same way, so
        // this is preserved rather than tidied. What a sync engine shows the user before the
        // push completes is the reason the last one failed, which is the useful answer.
        queue.claim(limit = 10, maxAttempts = 5)

        assertEquals(OutboxStatus.InFlight, queue.entries()[0].status)
        assertEquals("no network", queue.entries()[0].lastError)
    }

    @Test
    fun changesStrandedInFlightByACrashGoBackToPending() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        queue.claim(limit = 10, maxAttempts = 3)
        assertEquals(1, queue.count(OutboxStatus.InFlight))

        val recovered = queue.recoverStrandedEntries()

        assertEquals(1, recovered)
        assertEquals(1, queue.count(OutboxStatus.Pending))
        assertEquals(1, queue.claim(limit = 10, maxAttempts = 3).size)
    }

    @Test
    fun aChangeStrandedByACrashIsClaimableAgain() = runTest {
        // Claim charges the attempt up front. A row stranded by a crash never got a
        // verdict, so recovery has to hand the attempt back — otherwise a row stranded on
        // its last permitted attempt can never be claimed again
        // (`attemptCount < maxAttempts` stays false), and the user's change sits in the
        // queue forever: never pushed, never dead-lettered, never shown as failed.
        val queue = queue()
        queue.enqueue(entry("change-1"))
        // A single attempt permitted, so the charge exhausts the budget immediately.
        queue.claim(limit = 10, maxAttempts = 1)
        assertEquals(1, queue.count(OutboxStatus.InFlight))

        val recovered = queue.recoverStrandedEntries()
        val claimed = queue.claim(limit = 10, maxAttempts = 1)

        assertEquals(1, recovered)
        assertEquals(listOf("change-1"), claimed.map { it.id })
    }

    @Test
    fun recoveryOnlyTouchesStrandedRowsAndReportsHowManyItFound() = runTest {
        val queue = queue()
        queue.enqueue(entry("pending-one"))
        queue.enqueue(entry("stranded"))
        queue.enqueue(entry("failed-one"))
        // Two rows in flight, one of them failed separately below.
        queue.claim(limit = 2, maxAttempts = 5)
        val failed = queue.claim(limit = 1, maxAttempts = 5)
        queue.fail(failed.map { it.id }, "no network", 5)

        val recovered = queue.recoverStrandedEntries()

        assertEquals(2, recovered)
        assertEquals(0, queue.count(OutboxStatus.InFlight))
        // The failed row was not in flight, so recovery left its status alone.
        assertEquals(1, queue.count(OutboxStatus.Failed))
    }

    @Test
    fun recoveryOnAnEmptyQueueReportsNothingRecovered() = runTest {
        val queue = queue()
        queue.enqueue(entry("pending-one"))

        assertEquals(0, queue.recoverStrandedEntries())
        assertEquals(OutboxStatus.Pending, queue.entries()[0].status)
    }

    @Test
    fun countsCanBeFilteredByStatus() = runTest {
        val queue = queue()
        queue.enqueue(entry("live"))
        queue.enqueue(entry("done"))

        val claimed = queue.claim(limit = 1, maxAttempts = 3)
        queue.complete(claimed.map { it.id })

        assertEquals(1, queue.count(null))
        assertEquals(1, queue.count(OutboxStatus.Pending))
        assertEquals(0, queue.count(OutboxStatus.Dead))
    }

    @Test
    fun signingOutClearsTheQueue() = runTest {
        val queue = queue()
        queue.enqueue(entry("a"))
        queue.enqueue(entry("b"))

        queue.removeAll()

        assertEquals(0, queue.count(null))
    }

    @Test
    fun aDeleteIsQueuedAsADeleteNotAsAnUpsertOfNothing() = runTest {
        val queue = queue()
        queue.enqueue(entry("gone", operation = OutboxOperation.Delete))

        val claimed = queue.claim(limit = 10, maxAttempts = 3)

        assertEquals(OutboxOperation.Delete, claimed[0].operation)
    }

    @Test
    fun aRecordWrittenByANewerBuildSurfacesAnErrorInsteadOfCrashing() = runTest {
        val database = TaskTrackerDatabase.inMemory()
        val queue = RoomOutboxQueue(database)
        // Room has no typed column here, which is what makes this case reachable: a string
        // this build does not recognise is stored happily and has to be refused on read.
        database.outboxDao().insert(
            OutboxRecord(
                id = "from-the-future",
                entityKind = "taskV2",
                entityID = "x",
                operation = OutboxOperation.Upsert.wireValue,
                payload = ByteArray(0),
                userID = null,
                createdAt = ORIGIN,
                status = OutboxStatus.Pending.wireValue,
                attemptCount = 0,
                lastAttemptAt = null,
                lastError = null,
            ),
        )

        val failure = assertFailsWith<OutboxError.UnknownEntity> {
            queue.claim(limit = 10, maxAttempts = 3)
        }

        assertEquals("taskV2", failure.raw)
        // The decode happens after the in-flight write, so the throw had to roll that write
        // back: an unreadable row must not also be left marked as pushed and charged an
        // attempt. Read through the DAO, not `entries()`, which would decode the bad row
        // again and throw the same error a second time.
        val row = database.outboxDao().all().single()
        assertEquals(OutboxStatus.Pending.wireValue, row.status)
        assertEquals(0, row.attemptCount)
    }

    // Dead-lettering and release: the three ways a claimed entry leaves the queue, and
    // the accounting each one owes.

    @Test
    fun aRejectedEntryIsDeadLetteredAtOnceWithoutWaitingOutItsAttempts() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        queue.claim(limit = 10, maxAttempts = 5)

        queue.reject(listOf("change-1"), "title is required")

        assertEquals(1, queue.count(OutboxStatus.Dead))
        // One attempt was spent, not the five it was allowed.
        assertEquals(1, queue.entries()[0].attemptCount)
        assertEquals("title is required", queue.entries()[0].lastError)
    }

    @Test
    fun aReleasedEntryGoesBackToPendingAndIsClaimableAgain() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        queue.claim(limit = 10, maxAttempts = 5)

        queue.release(listOf("change-1"), "session ended")

        assertEquals(1, queue.count(OutboxStatus.Pending))
        assertEquals(0, queue.count(OutboxStatus.InFlight))
        val reclaimed = queue.claim(limit = 10, maxAttempts = 5)
        assertEquals(listOf("change-1"), reclaimed.map { it.id })
    }

    @Test
    fun aReleasedEntryIsNotChargedForTheAttemptSoASignOutCannotDeadLetterAUsersWork() = runTest {
        // The session ending is not the change's fault. `claim` already charged the
        // attempt; charging it again here would let repeated sign-outs walk a queued
        // change over the dead-letter limit without it ever reaching the server.
        val queue = queue()
        queue.enqueue(entry("change-1"))
        queue.claim(limit = 10, maxAttempts = 3)

        queue.release(listOf("change-1"), "session ended")

        assertEquals(0, queue.entries()[0].attemptCount)
    }

    @Test
    fun anEntryReleasedMoreOftenThanClaimedSettlesAtZeroAttemptsNotBelow() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        queue.claim(limit = 10, maxAttempts = 5)

        // Three releases against one claim. `max(attemptCount - 1, 0)` has to be the
        // two-argument scalar max, not the aggregate, or a negative count would settle
        // here and `attemptCount < maxAttempts` would stay true forever.
        repeat(3) { queue.release(listOf("change-1"), "session ended") }

        assertEquals(0, queue.entries()[0].attemptCount)
        assertEquals(1, queue.claim(limit = 10, maxAttempts = 5).size)
    }

    @Test
    fun releasingABatchDoesNotDisturbEntriesThatWereNeverClaimed() = runTest {
        val queue = queue()
        queue.enqueue(entry("claimed", offset = 0.0))
        queue.enqueue(entry("untouched", offset = 1.0))
        queue.claim(limit = 1, maxAttempts = 5)

        queue.release(listOf("claimed"), "session ended")

        val entries = queue.entries()
        assertEquals(OutboxStatus.Pending, entries.first { it.id == "claimed" }.status)
        assertEquals(OutboxStatus.Pending, entries.first { it.id == "untouched" }.status)
        assertEquals(2, queue.count(null))
    }

    @Test
    fun releasingAnEmptyListIsANoOpRatherThanInvalidSql() = runTest {
        val queue = queue()
        queue.enqueue(entry("change-1"))
        queue.claim(limit = 10, maxAttempts = 5)

        queue.release(emptyList(), "session ended")

        assertEquals(OutboxStatus.InFlight, queue.entries()[0].status)
    }
}

class OutboxUserScopingTest {
    private fun queue(): OutboxQueue = RoomOutboxQueue(TaskTrackerDatabase.inMemory())

    private fun entry(id: String, userID: String?, offset: Double = 0.0) = NewOutboxEntry(
        id = id,
        entity = OutboxEntity.WorkItem,
        entityID = id,
        operation = OutboxOperation.Upsert,
        payload = PAYLOAD.encodeToByteArray(),
        createdAt = ORIGIN + offset.seconds,
        userID = userID,
    )

    @Test
    fun aScopedClaimTakesOnlyThatAccountsRows() = runTest {
        val queue = queue()
        queue.enqueue(entry("ada-1", "ada"))
        queue.enqueue(entry("bob-1", "bob"))
        queue.enqueue(entry("ada-2", "ada"))

        val claimed = queue.claim(limit = 10, maxAttempts = 3, userID = "ada")

        assertEquals(listOf("ada-1", "ada-2"), claimed.map { it.id })
    }

    @Test
    fun legacyRowsWrittenWithNoUserAreClaimableByAnySession() = runTest {
        val queue = queue()
        queue.enqueue(entry("legacy", null))
        queue.enqueue(entry("bobs", "bob"))

        val claimed = queue.claim(limit = 10, maxAttempts = 3, userID = "ada")

        assertEquals(listOf("legacy"), claimed.map { it.id })
    }

    @Test
    fun anUnscopedClaimTakesEverythingAcrossAccounts() = runTest {
        val queue = queue()
        queue.enqueue(entry("ada-1", "ada"))
        queue.enqueue(entry("bob-1", "bob"))
        queue.enqueue(entry("legacy", null))

        val claimed = queue.claim(limit = 10, maxAttempts = 3)

        assertEquals(3, claimed.size)
    }

    @Test
    fun aClaimedRowCarriesTheUserThatQueuedIt() = runTest {
        val queue = queue()
        queue.enqueue(entry("ada-1", "ada"))

        val claimed = queue.claim(limit = 10, maxAttempts = 3, userID = "ada")

        assertEquals("ada", claimed[0].userID)
    }

    @Test
    fun aScopedClaimRespectsTheLimitAcrossAccountsSoOneUserCannotStarveAnother() = runTest {
        val queue = queue()
        queue.enqueue(entry("ada-1", "ada", offset = 0.0))
        queue.enqueue(entry("bob-1", "bob", offset = 1.0))
        queue.enqueue(entry("ada-2", "ada", offset = 2.0))

        val claimed = queue.claim(limit = 2, maxAttempts = 3, userID = "ada")

        // Only Ada's rows, and the oldest of them: the limit is applied after the
        // predicate, not before it.
        assertEquals(listOf("ada-1", "ada-2"), claimed.map { it.id })
    }

    @Test
    fun anotherAccountsWorkIsLeftUntouchedByAScopedClaim() = runTest {
        val queue = queue()
        queue.enqueue(entry("ada-1", "ada"))
        queue.enqueue(entry("bob-1", "bob"))

        queue.claim(limit = 10, maxAttempts = 3, userID = "ada")

        // Exactly Ada's row is in flight. Bob's was never claimed and never charged an
        // attempt, so signing in as Bob later still finds his change waiting rather than
        // half-pushed under someone else's session.
        assertEquals(1, queue.count(OutboxStatus.InFlight))
        assertEquals(0, queue.entries().first { it.id == "bob-1" }.attemptCount)
        assertEquals(listOf("bob-1"), queue.claim(limit = 10, maxAttempts = 3, userID = "bob").map { it.id })
    }
}
