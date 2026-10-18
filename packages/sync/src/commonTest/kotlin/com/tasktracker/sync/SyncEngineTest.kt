package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

/**
 * The engine.
 *
 * Its job is the *interaction* with the queue — ordering, attempt accounting, what happens
 * to a change the server was silent about — so the assertions here are mostly about queue
 * state afterwards rather than about return values.
 */
class SyncEngineTest {

    /**
     * Deliberately *not* [SyncConfiguration.eager], which allows a single attempt. With one
     * attempt permitted, `claim` charges the only attempt and the first failure dead-letters,
     * so the retry path under test would never be exercised.
     */
    private val testConfiguration = SyncConfiguration(
        pushBatchSize = 10,
        maxPushBatchesPerCycle = 3,
        maxAttempts = 3,
        pullPageSize = 20,
        maxPullPagesPerScope = 3,
    )

    private fun engine(
        outbox: InMemoryOutboxQueue = InMemoryOutboxQueue(),
        metadata: InMemorySyncMetadataStore = InMemorySyncMetadataStore(),
        transport: SyncTransport = StubSyncTransport(),
        sinks: Map<OutboxEntity, SyncApplySink> = emptyMap(),
        configuration: SyncConfiguration = testConfiguration,
        scopes: List<SyncScope> = SyncScope.all,
        dateProvider: TestDateProvider = TestDateProvider(),
        pushAs: suspend () -> String? = { null },
    ): SyncEngine = SyncEngine(
        outbox = outbox,
        metadata = metadata,
        transport = transport,
        sinks = sinks,
        configuration = configuration.copy(scopes = scopes),
        dateProvider = dateProvider,
        pushAs = pushAs,
    )

    // MARK: - Push

    @Test
    fun `a queued change is pushed and then gone from the queue`() = runTest {
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue).run(SyncTrigger.LocalWrite)

        assertEquals(1, report.pushed)
        assertEquals(1, report.accepted)
        assertTrue(report.isSuccess)
        assertEquals(0, queue.count(null))
    }

    @Test
    fun `changes reach the server oldest first because the server applies them in order`() = runTest {
        val transport = StubSyncTransport()
        val queue = InMemoryOutboxQueue()
        // Enqueued newest-first on purpose: the ordering must come from the queue, not from
        // the order a repository happened to write them.
        queue.enqueue(newEntry("third", offsetSeconds = 20))
        queue.enqueue(newEntry("first", offsetSeconds = 0))
        queue.enqueue(newEntry("second", offsetSeconds = 10))

        engine(outbox = queue, transport = transport).run()

        assertEquals(listOf("first", "second", "third"), transport.pushedIDs)
    }

    @Test
    fun `a server conflict resolves the change instead of retrying it forever`() = runTest {
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.PerEntry(
                mapOf("change-1" to PushOutcome.Conflict("v9")),
            ),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        assertEquals(1, report.conflicts)
        assertEquals(0, report.accepted)
        // Resolved means gone from the queue. Left queued, the server would reject the same
        // losing change on every cycle from here on.
        assertEquals(0, queue.count(null))
        assertTrue(report.isSuccess)
    }

    @Test
    fun `a permanent rejection dead-letters at once without spending the attempt budget`() = runTest {
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.Verdict(PushOutcome.Rejected("title is required")),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        assertEquals(1, report.rejected)
        assertTrue(report.isSuccess)
        // Dead rows stay in the table so the user can see what did not make it; they are not
        // silently deleted.
        assertEquals(1, queue.count(OutboxStatus.Dead))
        assertEquals(1, report.deadLetterCount)
        // Exactly one attempt was used, not `maxAttempts`.
        assertEquals(1, queue.entries().first().attemptCount)
    }

    @Test
    fun `a retryable failure leaves the change queued for the next cycle`() = runTest {
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.Verdict(PushOutcome.RetryLater("503")),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        assertEquals(1, report.retrying)
        assertEquals(1, queue.count(OutboxStatus.Failed))
        assertEquals(0, queue.count(OutboxStatus.Dead))
    }

    @Test
    fun `a change the server never mentions is retried not assumed to have landed`() = runTest {
        // The dangerous failure mode: a server that answers 200 with an empty body. Treating
        // silence as success would delete a change the user was told was saved.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Silent)
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        assertEquals(0, report.accepted)
        assertEquals(1, report.retrying)
        assertEquals(1, queue.count(OutboxStatus.Failed))
    }

    @Test
    fun `a transport failure leaves nothing in flight`() = runTest {
        val transport = StubSyncTransport()
        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Fail(AppError.Offline))
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        // The failure is transient, so the report says deferred rather than failed.
        assertTrue(report.isDeferred)
        assertEquals(1, report.retrying)
        // Nothing may be left in flight: an in-flight row is invisible to the next claim, so
        // the change would hang there until the next launch.
        assertEquals(0, queue.count(OutboxStatus.InFlight))
        assertEquals(1, queue.count(OutboxStatus.Failed))
    }

    @Test
    fun `an expired session ends the cycle but keeps every queued change`() = runTest {
        val transport = StubSyncTransport()
        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Fail(AppError.Unauthorized))
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        assertTrue(report.requiresSignOut)
        // The user's unsynced work must survive a sign-out untouched.
        assertEquals(1, queue.count(null))
        assertEquals(0, queue.count(OutboxStatus.Dead))
    }

    @Test
    fun `a sign-out gives back the attempt it was charged`() = runTest {
        // The distinction the original's `release` exists for: `fail` charges an attempt
        // because a server that is merely unreachable must eventually stop being retried,
        // while a sign-out is not the change's fault — and a user signing out three times
        // must not dead-letter a queue of work they never had the chance to send.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Fail(AppError.Unauthorized))
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val subject = engine(outbox = queue, transport = transport)

        subject.run()
        subject.run()
        subject.run()

        assertEquals(0, queue.entries().first().attemptCount)
        assertEquals(OutboxStatus.Pending, queue.entries().first().status)
    }

    @Test
    fun `a crash mid-push is recovered and the change goes out on the next cycle`() = runTest {
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        // Stand in for a process that claimed the change and then died.
        queue.claim(limit = 10, maxAttempts = 3, userID = null)
        assertEquals(1, queue.count(OutboxStatus.InFlight))

        val report = engine(outbox = queue).run()

        assertEquals(1, report.recoveredStranded)
        assertEquals(1, report.pushed)
        assertEquals(0, queue.count(null))
    }

    @Test
    fun `a recovered stranded change keeps the attempt it never spent`() = runTest {
        // The reason `recoverStranded` refunds: an entry stranded on its last permitted
        // attempt can never be claimed again, so it would sit in the queue forever — never
        // pushed, never dead-lettered, never visible as a failure.
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        queue.freezeClock(kotlin.time.Instant.fromEpochSeconds(1))
        queue.claim(limit = 10, maxAttempts = 1, userID = null)
        assertEquals(1, queue.entries().first().attemptCount)

        // The refund itself: an attempt that was paid for by a claim the app never got to use.
        queue.recoverStrandedEntries()

        val refunded = queue.entries().single()
        assertEquals(OutboxStatus.Pending, refunded.status)
        assertEquals(0, refunded.attemptCount)
        // Without it the next claim's `attemptCount < maxAttempts` is false forever, so the
        // engine's cycle pushes the change out instead of dead-lettering it. (`recoveredStranded`
        // is covered above; here the recovery is done by hand to inspect the entry mid-way.)
        val report = engine(outbox = queue).run()

        assertEquals(1, report.pushed)
        assertEquals(0, report.retrying)
        assertEquals(0, queue.count(OutboxStatus.Dead))
        assertEquals(0, queue.count(null))
    }

    @Test
    fun `a server that refuses everything with retry later cannot spin the cycle forever`() = runTest {
        // The outbox hands failed rows back on the next claim, so without a seen-this-cycle
        // guard this would claim, push, fail, claim the same row again, and never return.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.Verdict(PushOutcome.RetryLater("503")),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val subject = engine(
            outbox = queue,
            transport = transport,
            configuration = SyncConfiguration(
                pushBatchSize = 1,
                maxPushBatchesPerCycle = 50,
                maxAttempts = 5,
            ),
        )

        val report = subject.run()

        // Claimed once, then recognised as already-tried and abandoned for this cycle.
        assertEquals(1, transport.pushCallCount)
        assertEquals(1, report.retrying)
    }

    @Test
    fun `verdicts are matched by change id and not by position in the reply`() = runTest {
        // A server that answers out of order must not cause "accepted" to be applied to the
        // wrong change, which would silently lose an edit.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.PerEntry(
                mapOf(
                    "first" to PushOutcome.Rejected("invalid"),
                    "second" to PushOutcome.Accepted,
                ),
            ),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("first", offsetSeconds = 0))
        queue.enqueue(newEntry("second", offsetSeconds = 1))

        engine(outbox = queue, transport = transport).run()

        // `second` was accepted and is gone; the row that survives is the rejected one, which
        // is the proof the verdicts did not land on each other's change.
        val survivors = queue.entries()
        assertEquals(1, survivors.size)
        assertEquals("first", survivors.single().id)
        assertEquals(OutboxStatus.Dead, survivors.single().status)
    }

    @Test
    fun `a repeated id invalidates that change's verdict entirely`() = runTest {
        // The server and the engine can now disagree about what a verdict applied to, so
        // nothing is trusted for that change: it is retried rather than half-resolved.
        val transport = ScriptedPushTransport(
            listOf(
                listOf(
                    PushResult.accepted("change-1"),
                    PushResult.accepted("change-1"),
                ),
            ),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))

        val report = engine(outbox = queue, transport = transport).run()

        assertEquals(0, report.accepted)
        assertEquals(1, report.retrying)
        assertTrue(report.failures.any { it.message.contains("contradictory") })
        assertEquals(1, queue.count(OutboxStatus.Failed))
    }

    @Test
    fun `the push loop stops after a retryable failure so ordering survives`() = runTest {
        // The next claim would hand back the same entries, and pushing a newer change past
        // one the server has not accepted would break the ordering the queue exists to keep.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.PerEntry(
                mapOf("first" to PushOutcome.RetryLater("503")),
            ),
        )
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("first", offsetSeconds = 0))
        queue.enqueue(newEntry("second", offsetSeconds = 1))

        // One change per batch, so the rule has a batch boundary to stop at: with both in one
        // batch the server has already seen `second` and the ordering is lost regardless.
        engine(
            outbox = queue,
            transport = transport,
            configuration = SyncConfiguration(pushBatchSize = 1),
        ).run()

        assertEquals(listOf("first"), transport.pushedIDs)
        assertEquals(1, queue.count(OutboxStatus.Failed))
        // `second` was never claimed, so it is still pending and still the next cycle's problem.
        assertEquals("second", queue.entries().single { it.status == OutboxStatus.Pending }.id)
    }

    @Test
    fun `a batch ceiling leaves the rest of the backlog for the next cycle`() = runTest {
        // A month of offline work trickles out over several cycles instead of one cycle
        // monopolising the radio until the app is killed.
        val queue = InMemoryOutboxQueue()
        repeat(5) { queue.enqueue(newEntry("change-$it", offsetSeconds = it.toLong())) }
        val transport = StubSyncTransport()

        engine(
            outbox = queue,
            transport = transport,
            configuration = SyncConfiguration(pushBatchSize = 2, maxPushBatchesPerCycle = 2),
        ).run()

        assertEquals(listOf("change-0", "change-1", "change-2", "change-3"), transport.pushedIDs)
        assertEquals(1, queue.count(null))
    }

    // MARK: - Pull

    @Test
    fun `incoming changes reach the local store through the feature's sink`() = runTest {
        val sink = RecordingSink()
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(
                PullPage(
                    changes = listOf(remoteChange("task-1"), remoteChange("task-2")),
                    nextCursor = "c1",
                    hasMore = false,
                ),
            ),
            scope = SyncScope.WorkItems,
        )

        val report = engine(
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
        ).run()

        assertEquals(2, report.applied)
        assertEquals(listOf("task-1", "task-2"), sink.appliedIDs)
    }

    /**
     * The attachments scope has to be in the default set.
     *
     * Every production graph passes `SyncScope.all`, so a scope that was defined but left out
     * of `all` would be pulled by nobody: attachment metadata would never arrive, every file
     * would stay local, and nothing anywhere would report an error — the engine would be
     * behaving exactly as configured. This assertion is the only thing that catches it.
     */
    @Test
    fun `the default scopes include attachment metadata`() = runTest {
        val transport = StubSyncTransport()

        engine(transport = transport).run()

        val pulled = transport.pullRequests.map { it.scope }
        assertTrue(
            SyncScope.Attachments in pulled,
            "attachment metadata must be pulled by default, but the engine pulled $pulled",
        )
    }

    @Test
    fun `an attachment change is routed to the attachment sink`() = runTest {
        val sink = RecordingSink()
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(
                PullPage(
                    changes = listOf(remoteChange("file-1", entity = OutboxEntity.Attachment)),
                    nextCursor = "c1",
                    hasMore = false,
                ),
            ),
            scope = SyncScope.Attachments,
        )

        val report = engine(
            transport = transport,
            sinks = mapOf(OutboxEntity.Attachment to sink),
        ).run()

        assertEquals(1, report.applied)
        assertEquals(listOf("file-1"), sink.appliedIDs)
    }

    @Test
    fun `the cursor is stored only after the page is safely applied`() = runTest {
        val sink = RecordingSink()
        val metadata = InMemorySyncMetadataStore()
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(PullPage(listOf(remoteChange("task-1")), nextCursor = "c1", hasMore = false)),
            scope = SyncScope.WorkItems,
        )

        engine(
            metadata = metadata,
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
        ).run()

        assertEquals("c1", metadata.metadata(SyncScope.WorkItems.wireValue)?.cursor)
        assertTrue(metadata.metadata(SyncScope.WorkItems.wireValue)?.lastSyncedAt != null)
    }

    @Test
    fun `a sink failure leaves the cursor where it was so nothing is skipped`() = runTest {
        // The sink failed to store the change. If the cursor advanced anyway, that change
        // would be gone for good — the server would not send it again.
        val sink = RecordingSink(AppError.Storage("disk full"))
        val metadata = InMemorySyncMetadataStore()
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(PullPage(listOf(remoteChange("task-1")), nextCursor = "c1", hasMore = false)),
            scope = SyncScope.WorkItems,
        )

        val report = engine(
            metadata = metadata,
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
        ).run()

        assertTrue(report.isDeferred)
        assertTrue(sink.appliedIDs.isEmpty())
        assertNull(metadata.metadata(SyncScope.WorkItems.wireValue)?.cursor)
    }

    @Test
    fun `a page is resumed from the stored cursor`() = runTest {
        val metadata = InMemorySyncMetadataStore()
        metadata.markSynced(SyncScope.WorkItems.wireValue, "c7", kotlin.time.Instant.fromEpochSeconds(1))
        val transport = StubSyncTransport()

        engine(metadata = metadata, transport = transport, configuration = SyncConfiguration.eager).run()

        // Scoped to workItems, because a full cycle also pulls `projects`.
        val workItemPulls = transport.pullRequests.filter { it.scope == SyncScope.WorkItems }
        assertEquals("c7", workItemPulls.first().cursor)
    }

    @Test
    fun `a resync throws the stale cursor away and re-reads from the start`() = runTest {
        // The server pruned changes this cursor had not read, so the page it just sent is a
        // partial view of the gap. Applying that page and advancing would skip what is gone
        // for good and still report a clean sync — the one outcome worse than a failure.
        val sink = RecordingSink()
        val metadata = InMemorySyncMetadataStore()
        metadata.markSynced(SyncScope.WorkItems.wireValue, "c7", kotlin.time.Instant.fromEpochSeconds(1))
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(
                PullPage(changes = emptyList(), nextCursor = null, hasMore = true, resyncRequired = true),
                PullPage(listOf(remoteChange("task-1")), nextCursor = "c1", hasMore = false),
            ),
            scope = SyncScope.WorkItems,
        )

        val report = engine(
            metadata = metadata,
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
            scopes = listOf(SyncScope.WorkItems),
        ).run()

        val pulls = transport.pullRequests.filter { it.scope == SyncScope.WorkItems }
        assertEquals("c7", pulls.first().cursor)
        // The retry drops the cursor rather than continuing from it, which is the whole point.
        assertNull(pulls[1].cursor)
        // The page that carried the resync flag was not applied; the surviving log was read
        // from the start instead.
        assertEquals(listOf("task-1"), sink.appliedIDs)
        assertEquals("c1", metadata.metadata(SyncScope.WorkItems.wireValue)?.cursor)
        assertTrue(report.isSuccess)
    }

    @Test
    fun `a resync asked for twice in one cycle fails instead of looping`() = runTest {
        // Two in one cycle means the server is pruning faster than this client can read.
        // Looping would not converge, and reporting success would claim data arrived that did
        // not — so the scope fails loudly and the next cycle tries again.
        val metadata = InMemorySyncMetadataStore()
        metadata.markSynced(SyncScope.WorkItems.wireValue, "c7", kotlin.time.Instant.fromEpochSeconds(1))
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(
                PullPage(changes = emptyList(), nextCursor = null, hasMore = true, resyncRequired = true),
                PullPage(changes = emptyList(), nextCursor = null, hasMore = true, resyncRequired = true),
            ),
            scope = SyncScope.WorkItems,
        )

        val report = engine(
            metadata = metadata,
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to RecordingSink()),
            scopes = listOf(SyncScope.WorkItems),
        ).run()

        assertTrue(report.failures.any { it.message.contains("resync") })
    }

    @Test
    fun `paging stops when the server says there is nothing more`() = runTest {
        val sink = RecordingSink()
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(PullPage(listOf(remoteChange("task-1")), nextCursor = "c1", hasMore = false)),
            scope = SyncScope.WorkItems,
        )

        engine(
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
            scopes = listOf(SyncScope.WorkItems),
        ).run()

        assertEquals(1, transport.pullRequests.size)
    }

    @Test
    fun `a page ceiling stops the pull without failing`() = runTest {
        val sink = RecordingSink()
        val transport = StubSyncTransport()
        // Three pages, `hasMore` true on every one: the ceiling is the only thing that stops
        // this pull, and it must stop it without reporting a failure, because the rest is the
        // next cycle's work rather than a problem.
        transport.setPages(
            listOf(
                PullPage(listOf(remoteChange("task-0")), nextCursor = "c0", hasMore = true),
                PullPage(listOf(remoteChange("task-1")), nextCursor = "c1", hasMore = true),
                PullPage(listOf(remoteChange("task-2")), nextCursor = "c2", hasMore = true),
            ),
            scope = SyncScope.WorkItems,
        )

        val report = engine(
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
            scopes = listOf(SyncScope.WorkItems),
            configuration = SyncConfiguration.eager.copy(maxPullPagesPerScope = 2),
        ).run()

        assertEquals(2, report.applied)
        // The third page was never asked for, so the cursor sent with the second request is
        // the first page's: the next cycle re-reads from there and gets it.
        assertEquals(2, transport.pullRequests.size)
        assertEquals("c0", transport.pullRequests.last().cursor)
        assertTrue(report.isSuccess)
    }

    @Test
    fun `a change nothing can store fails loudly instead of being skipped`() = runTest {
        // No sink registered. Advancing the cursor would drop the change permanently, so the
        // engine refuses to advance — a wiring bug has to be visible, not silent.
        val metadata = InMemorySyncMetadataStore()
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(PullPage(listOf(remoteChange("task-1")), nextCursor = "c1", hasMore = false)),
            scope = SyncScope.WorkItems,
        )

        val report = engine(metadata = metadata, transport = transport).run()

        assertEquals(0, report.applied)
        assertTrue(report.failures.any { it.message.contains("workItem") })
        assertNull(metadata.metadata(SyncScope.WorkItems.wireValue)?.cursor)
    }

    @Test
    fun `a failed pull is retried from the same cursor next cycle`() = runTest {
        val sink = RecordingSink()
        val metadata = InMemorySyncMetadataStore()
        val transport = StubSyncTransport()
        transport.setPullError(AppError.Transport("no route"))

        val report = engine(
            metadata = metadata,
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
        ).run()

        assertTrue(report.isDeferred)
        assertEquals(0, report.applied)
        assertNull(metadata.metadata(SyncScope.WorkItems.wireValue)?.cursor)
    }

    @Test
    fun `a dead session during a pull is reported as needing a sign-out`() = runTest {
        val transport = StubSyncTransport()
        transport.setPullError(AppError.Forbidden)

        val report = engine(transport = transport).run()

        assertTrue(report.requiresSignOut)
        // Not a deferred: the session is gone, so nothing here will be retried on its own.
        assertFalse(report.isDeferred)
    }

    // MARK: - Whole cycle

    @Test
    fun `a cycle that pushed and pulled reports success with its counts`() = runTest {
        val sink = RecordingSink()
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val transport = StubSyncTransport()
        transport.setPages(
            listOf(PullPage(listOf(remoteChange("task-9")), nextCursor = "c1", hasMore = false)),
            scope = SyncScope.WorkItems,
        )

        val report = engine(
            outbox = queue,
            transport = transport,
            sinks = mapOf(OutboxEntity.WorkItem to sink),
        ).run(SyncTrigger.Foreground)

        assertEquals(SyncTrigger.Foreground, report.trigger)
        assertEquals(1, report.pushed)
        assertEquals(1, report.accepted)
        assertEquals(1, report.applied)
        assertTrue(report.isSuccess)
        assertEquals(0, report.queueDepth)
    }

    @Test
    fun `a failed cycle leaves its reason on every scope it covers`() = runTest {
        // The settings screen shows a scope's last error, so a push failure has to be visible
        // next to the scope it belongs to and not only in the device log.
        val metadata = InMemorySyncMetadataStore()
        val transport = StubSyncTransport()
        transport.setPullError(AppError.Transport("no route"))

        engine(metadata = metadata, transport = transport).run()

        assertTrue(
            metadata.metadata(SyncScope.WorkItems.wireValue)?.lastError?.contains("no route") == true,
        )
        assertTrue(
            metadata.metadata(SyncScope.Projects.wireValue)?.lastError?.contains("no route") == true,
        )
    }

    @Test
    fun `the queue depth and dead letter count are reported at the end`() = runTest {
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val transport = StubSyncTransport()
        transport.setPushBehaviour(
            StubSyncTransport.PushBehaviour.PerEntry(
                mapOf("change-1" to PushOutcome.Rejected("nope")),
            ),
        )

        val report = engine(outbox = queue, transport = transport).run()

        assertEquals(1, report.queueDepth)
        assertEquals(1, report.deadLetterCount)
    }

    @Test
    fun `two cycles cannot overlap so no change is ever pushed twice`() = runTest {
        val transport = StubSyncTransport()
        transport.pauseOnPush()
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val subject = engine(outbox = queue, transport = transport)

        val first = async { subject.run() }
        transport.waitUntilPaused()
        val second = subject.run()
        transport.release()
        first.await()

        assertTrue(second.failures.any { it.message.contains("already running") })
        assertEquals(1, transport.pushCallCount)
        assertFalse(subject.isSyncing)
    }

    // MARK: - User scoping

    @Test
    fun `a cycle pushes only the signed-in account's queued changes`() = runTest {
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("ada-1", userID = "ada"))
        queue.enqueue(newEntry("bob-1", userID = "bob"))

        val report = engine(outbox = queue, pushAs = { "ada" }).run(SyncTrigger.Manual)

        assertEquals(1, report.pushed)
        assertEquals(1, report.accepted)
        // Bob's row survives the cycle untouched.
        assertEquals(1, queue.count(null))
    }

    @Test
    fun `with nobody signed in legacy unscoped rows still go out`() = runTest {
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("legacy-1"))

        val report = engine(
            outbox = queue,
            configuration = SyncConfiguration.eager,
            pushAs = { null },
        ).run(SyncTrigger.Manual)

        assertEquals(1, report.pushed)
        assertTrue(report.isSuccess)
    }

    @Test
    fun `the account is read once per cycle and not once per batch`() = runTest {
        // Re-reading per batch would let one cycle straddle two accounts: push half of ada's
        // work, then be signed in as bob and push the rest under the wrong session.
        val queue = InMemoryOutboxQueue()
        repeat(4) { queue.enqueue(newEntry("ada-$it", userID = "ada", offsetSeconds = it.toLong())) }
        val transport = StubSyncTransport()
        var reads = 0
        val subject = engine(
            outbox = queue,
            transport = transport,
            configuration = SyncConfiguration(pushBatchSize = 1, maxPushBatchesPerCycle = 4),
            pushAs = {
                reads++
                "ada"
            },
        )

        subject.run()

        assertEquals(1, reads)
        assertEquals(4, transport.pushCallCount)
    }
}

/**
 * A transport that answers with a fixed list of scripted batches.
 *
 * [StubSyncTransport] can produce a duplicate id only by being handed one, and the
 * duplicate-invalidation rule is worth a first-class double rather than a flag that
 * happens to express it.
 */
private class ScriptedPushTransport(private val batches: List<List<PushResult>>) : SyncTransport {
    private var next = 0

    override suspend fun push(entries: List<com.tasktracker.database.OutboxEntry>): List<PushResult> {
        val answer = batches.getOrElse(next) { emptyList() }
        next++
        return answer
    }

    override suspend fun pull(scope: SyncScope, cursor: String?, limit: Int): PullPage = PullPage.empty
}
