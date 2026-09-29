package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.network.Connectivity
import com.tasktracker.network.ConnectivityWatcher
import com.tasktracker.network.NetworkMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The coordinator: when to sync, and the promise that only one cycle runs at a time.
 *
 * The monitor is driven by [FakeConnectivityWatcher] rather than a real path watcher, so the
 * tests do not depend on the machine they run on having a network — the same reason the
 * original forced an override.
 */
class SyncCoordinatorTest {

    /**
     * A watcher that says nothing until told.
     *
     * A monitor's starting value is `Unknown` precisely so that a launch cannot tell "no
     * path" from "not checked yet"; a double that reported `Satisfied` on `attach` would make
     * every launch look like a recovery and hide the rule under test.
     */
    private class FakeConnectivityWatcher : ConnectivityWatcher {
        private var onStatus: ((Connectivity) -> Unit)? = null

        fun report(connectivity: Connectivity) {
            onStatus?.invoke(connectivity)
        }

        override fun attach(onStatus: (Connectivity) -> Unit) {
            this.onStatus = onStatus
        }

        override fun start() = Unit

        override fun stop() {
            onStatus = null
        }
    }

    /**
     * A started monitor, with a forced status.
     *
     * Started here, not by the coordinator, because that is the division of labour in the
     * original: `App/CompositionRoot.swift` starts the one monitor the app shares with routing.
     * The coordinator only observes it.
     */
    private fun monitor(forced: Connectivity? = Connectivity.Satisfied): NetworkMonitor =
        NetworkMonitor.forWatcher(FakeConnectivityWatcher()).apply {
            setOverride(forced)
            start()
        }

    private fun subject(
        scope: TestScope,
        monitor: NetworkMonitor,
        transport: StubSyncTransport = StubSyncTransport(),
        queue: InMemoryOutboxQueue = InMemoryOutboxQueue(),
        schedule: SyncSchedule = SyncSchedule.manual,
    ): Pair<SyncCoordinator, InMemoryOutboxQueue> {
        val engine = SyncEngine(
            outbox = queue,
            metadata = InMemorySyncMetadataStore(),
            transport = transport,
            // `eager` with a budget of three attempts: a scripted failure needs an attempt
            // left over, or the first failure dead-letters and there is nothing to observe.
            configuration = SyncConfiguration.eager.copy(maxAttempts = 3),
        )
        return SyncCoordinator(
            engine = engine,
            monitor = monitor,
            schedule = schedule,
            scope = scope,
        ) to queue
    }

    // MARK: - Gating

    @Test
    fun `a cycle is skipped while there is no network path`() = runTest {
        val (subject, _) = subject(this, monitor(Connectivity.Unsatisfied))

        val report = subject.synchronize(SyncTrigger.Manual)

        assertNull(report)
        assertEquals(SyncStatus.WaitingForConnectivity, subject.currentStatus)
    }

    @Test
    fun `a path that needs a captive portal login is not worth a cycle`() = runTest {
        val (subject, _) = subject(this, monitor(Connectivity.RequiresConnection))

        assertNull(subject.synchronize(SyncTrigger.Manual))
    }

    @Test
    fun `an unknown path is treated as usable so a fresh launch can still sync`() = runTest {
        // `NWPathMonitor` reports `Unknown` before its first callback. Reading that as
        // "offline" would leave a perfectly connected device sitting on a queue of unsynced
        // work until the next launch.
        assertTrue(Connectivity.Unknown.allowsSync)
        assertTrue(Connectivity.Satisfied.allowsSync)
        assertFalse(Connectivity.Unsatisfied.allowsSync)
        assertFalse(Connectivity.RequiresConnection.allowsSync)

        val (subject, _) = subject(this, monitor(Connectivity.Unknown))

        assertNotNull(subject.synchronize(SyncTrigger.Manual))
    }

    @Test
    fun `a second trigger is dropped while a cycle is running so nothing is pushed twice`() = runTest {
        // The worst outcome this module could produce: two cycles claiming the same queued
        // change and applying a user's edit to the server twice.
        val transport = StubSyncTransport()
        transport.pauseOnPush()
        val (subject, queue) = subject(this, monitor(), transport)
        queue.enqueue(newEntry("change-1"))

        val first = async { subject.synchronize(SyncTrigger.Manual) }
        transport.waitUntilPaused()
        val second = subject.synchronize(SyncTrigger.Manual)
        transport.release()

        assertNotNull(first.await())
        assertNull(second)
        assertEquals(1, transport.pushCallCount)
    }

    // MARK: - Reporting

    @Test
    fun `a successful cycle is published to status subscribers`() = runTest {
        val (subject, _) = subject(this, monitor())
        val seen = subject.statusUpdates()

        val report = subject.synchronize(SyncTrigger.Manual)

        assertNotNull(report)
        assertTrue(subject.currentStatus is SyncStatus.Succeeded)
        assertNotNull(subject.lastSyncedAt)
        // The stream is a `StateFlow`, so a late subscriber sees the terminal status rather
        // than having missed it.
        assertTrue(seen.value is SyncStatus.Succeeded)
        assertFalse(subject.currentStatus.isSyncing)
    }

    @Test
    fun `a transient failure is published as deferred not as a failure`() = runTest {
        // A change must be queued, otherwise `claim` returns nothing, `push` is never called,
        // and the scripted failure never happens.
        val transport = StubSyncTransport()
        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Fail(AppError.Transport("no route")))
        val (subject, queue) = subject(this, monitor(), transport)
        queue.enqueue(newEntry("change-1"))

        val report = subject.synchronize(SyncTrigger.Manual)

        assertNotNull(report)
        assertTrue(report.isDeferred)
        assertTrue(subject.currentStatus is SyncStatus.Deferred)
    }

    @Test
    fun `a dead session is published as signed out`() = runTest {
        val transport = StubSyncTransport()
        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Fail(AppError.Forbidden))
        val (subject, queue) = subject(this, monitor(), transport)
        queue.enqueue(newEntry("change-1"))

        val report = subject.synchronize(SyncTrigger.Manual)

        assertNotNull(report)
        assertTrue(report.requiresSignOut)
        assertEquals(SyncStatus.SignedOut, subject.currentStatus)
    }

    @Test
    fun `a deferred cycle does not move the last synced time`() = runTest {
        // A "last synced" that ticks forward on every failed attempt is a lie the user reads
        // as "everything is up to date".
        val transport = StubSyncTransport()
        val (subject, queue) = subject(this, monitor(), transport)
        subject.synchronize(SyncTrigger.Manual)
        val afterSuccess = subject.lastSyncedAt
        assertNotNull(afterSuccess)

        transport.setPushBehaviour(StubSyncTransport.PushBehaviour.Fail(AppError.Transport("no route")))
        queue.enqueue(newEntry("change-1"))
        subject.synchronize(SyncTrigger.Manual)

        assertEquals(afterSuccess, subject.lastSyncedAt)
    }

    @Test
    fun `the current status is readable without subscribing`() = runTest {
        val (subject, _) = subject(this, monitor())

        assertEquals(SyncStatus.Idle(null), subject.currentStatus)
        subject.synchronize(SyncTrigger.Manual)

        assertNotNull(subject.currentStatus.report)
        assertFalse(subject.isSyncing)
    }

    // MARK: - Loop

    @Test
    fun `the periodic loop syncs on its own with no trigger`() = runTest {
        val transport = StubSyncTransport()
        val queue = InMemoryOutboxQueue()
        val (subject, _) = subject(
            this,
            monitor(),
            transport,
            queue,
            SyncSchedule(interval = 50.milliseconds, syncsOnStart = false, syncsOnReconnect = false),
        )
        queue.enqueue(newEntry("change-1"))

        subject.start()
        // Virtual time: the loop's `delay` is this test's clock, so "fifty milliseconds later"
        // is an instruction rather than a wait. Stopped explicitly because `runTest` would
        // otherwise wait for a loop that never ends.
        advanceTimeBy(50)
        runCurrent()
        subject.stop()

        assertNotNull(subject.lastReport)
        assertEquals(1, transport.pushCallCount)
    }

    @Test
    fun `the loop syncs on start when the schedule says so`() = runTest {
        val transport = StubSyncTransport()
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val (subject, _) = subject(
            this,
            monitor(),
            transport,
            queue,
            schedule = SyncSchedule(interval = 1.hours, syncsOnStart = true, syncsOnReconnect = false),
        )

        subject.start()
        runCurrent()
        subject.stop()

        assertEquals(1, transport.pushCallCount)
        assertEquals(SyncTrigger.AppLaunch, subject.lastReport?.trigger)
    }

    @Test
    fun `stopping the coordinator ends the loop`() = runTest {
        val transport = StubSyncTransport()
        val (subject, _) = subject(
            this,
            monitor(),
            transport,
            schedule = SyncSchedule(interval = 50.milliseconds, syncsOnStart = true),
        )

        subject.start()
        runCurrent()
        subject.stop()
        val afterStop = transport.pushCallCount
        advanceTimeBy(500)
        runCurrent()

        assertEquals(afterStop, transport.pushCallCount)
    }

    @Test
    fun `starting twice does not start two loops`() = runTest {
        val transport = StubSyncTransport()
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val (subject, _) = subject(
            this,
            monitor(),
            transport,
            queue,
            schedule = SyncSchedule(interval = 50.milliseconds, syncsOnStart = true, syncsOnReconnect = false),
        )

        subject.start()
        subject.start()
        runCurrent()
        subject.stop()

        // Two loops on a launch would mean two cycles claiming the same queue.
        assertEquals(1, transport.pushCallCount)
    }

    // MARK: - Connectivity

    @Test
    fun `a path coming back triggers a sync`() = runTest {
        val watcher = FakeConnectivityWatcher()
        val monitor = NetworkMonitor.forWatcher(watcher).apply { start() }
        val transport = StubSyncTransport()
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val (subject, _) = subject(this, monitor, transport, queue)

        subject.start()
        runCurrent()
        assertEquals(0, transport.pushCallCount)

        watcher.report(Connectivity.Satisfied)
        runCurrent()
        subject.stop()

        assertEquals(1, transport.pushCallCount)
        assertEquals(SyncTrigger.ConnectivityRestored, subject.lastReport?.trigger)
    }

    @Test
    fun `a path disappearing publishes waiting instead of syncing`() = runTest {
        val watcher = FakeConnectivityWatcher()
        val monitor = NetworkMonitor.forWatcher(watcher).apply { start() }
        val (subject, _) = subject(this, monitor)

        subject.start()
        watcher.report(Connectivity.Unsatisfied)
        runCurrent()
        subject.stop()

        assertEquals(SyncStatus.WaitingForConnectivity, subject.currentStatus)
    }

    @Test
    fun `the monitor reporting unknown on start is not a recovery`() = runTest {
        // `NWPathMonitor` reports `Unknown` first on every launch. Syncing on that would sync on
        // every single launch, before knowing whether there is a path at all.
        val transport = StubSyncTransport()
        val queue = InMemoryOutboxQueue()
        queue.enqueue(newEntry("change-1"))
        val monitor = NetworkMonitor.forWatcher(FakeConnectivityWatcher()).apply { start() }
        val (subject, _) = subject(this, monitor, transport, queue)

        subject.start()
        runCurrent()
        subject.stop()

        assertEquals(0, transport.pushCallCount)
    }
}
