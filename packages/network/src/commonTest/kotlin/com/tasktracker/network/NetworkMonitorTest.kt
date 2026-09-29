package com.tasktracker.network

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A watcher a test drives, standing in for `NWPathMonitor`/`ConnectivityManager`. */
private class FakeWatcher : ConnectivityWatcher {

    private var onStatus: ((Connectivity) -> Unit)? = null
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override fun attach(onStatus: (Connectivity) -> Unit) {
        this.onStatus = onStatus
    }

    override fun start() {
        startCount += 1
    }

    override fun stop() {
        stopCount += 1
    }

    fun report(status: Connectivity) = onStatus?.invoke(status)
}

class NetworkMonitorTest {

    @Test
    fun `a monitor that has not started is unknown`() {
        val monitor = NetworkMonitor(FakeWatcher())
        // Cold start: reporting offline here would bounce the UI through an offline banner
        // it has not earned, and park the sync queue before it has ever tried.
        assertEquals(Connectivity.Unknown, monitor.current)
    }

    @Test
    fun `the platform status is reported once started`() = runTest {
        val watcher = FakeWatcher()
        val monitor = NetworkMonitor(watcher)
        monitor.start()
        watcher.report(Connectivity.Satisfied)

        assertEquals(Connectivity.Satisfied, monitor.current)
    }

    @Test
    fun `starting twice attaches once`() = runTest {
        val watcher = FakeWatcher()
        val monitor = NetworkMonitor(watcher)

        monitor.start()
        monitor.start()

        assertEquals(1, watcher.startCount)
    }

    @Test
    fun `stopping twice stops once`() {
        val watcher = FakeWatcher()
        val monitor = NetworkMonitor(watcher)
        monitor.start()

        monitor.stop()
        monitor.stop()

        assertEquals(1, watcher.stopCount)
    }

    @Test
    fun `a repeated status does not re-emit`() = runTest {
        val watcher = FakeWatcher()
        val monitor = NetworkMonitor(watcher)
        val seen = mutableListOf<Connectivity>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.updates().collect { seen += it }
        }
        monitor.start()

        watcher.report(Connectivity.Satisfied)
        watcher.report(Connectivity.Satisfied)
        watcher.report(Connectivity.Unsatisfied)
        advanceUntilIdle()

        // The leading `Unknown` is the replay-on-subscribe value, which is the point of
        // choosing a StateFlow: a screen opened before the first report must render
        // something immediately rather than wait. The middle status is absent — a repeat
        // is not news, and a subscriber only ever wants the latest.
        assertEquals(listOf(Connectivity.Unknown, Connectivity.Satisfied, Connectivity.Unsatisfied), seen)
    }

    @Test
    fun `every collector gets the current value`() = runTest {
        val watcher = FakeWatcher()
        val monitor = NetworkMonitor(watcher)
        monitor.start()
        watcher.report(Connectivity.Unsatisfied)

        val first = mutableListOf<Connectivity>()
        val second = mutableListOf<Connectivity>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.updates().collect { first += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.updates().collect { second += it }
        }
        advanceUntilIdle()

        // Replayed on subscribe, which is what lets a screen opened while offline render
        // "Offline" immediately instead of waiting for the next change.
        assertEquals(listOf(Connectivity.Unsatisfied), first)
        assertEquals(listOf(Connectivity.Unsatisfied), second)

        watcher.report(Connectivity.Satisfied)
        advanceUntilIdle()
        assertEquals(listOf(Connectivity.Unsatisfied, Connectivity.Satisfied), first)
        assertEquals(listOf(Connectivity.Unsatisfied, Connectivity.Satisfied), second)
    }

    @Test
    fun `an override wins over the real path`() = runTest {
        val watcher = FakeWatcher()
        val monitor = NetworkMonitor(watcher)
        monitor.start()
        watcher.report(Connectivity.Satisfied)
        assertEquals(Connectivity.Satisfied, monitor.current)

        monitor.setOverride(Connectivity.Unsatisfied)

        assertEquals(Connectivity.Unsatisfied, monitor.current)
        // The real path is remembered, not overwritten: clearing the override must restore
        // it rather than leave the app stuck offline until the next network change.
        monitor.setOverride(null)
        assertEquals(Connectivity.Satisfied, monitor.current)
    }

    @Test
    fun `an override is published to subscribers`() = runTest {
        val monitor = NetworkMonitor(FakeWatcher())
        val seen = mutableListOf<Connectivity>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.updates().collect { seen += it }
        }

        monitor.setOverride(Connectivity.Unsatisfied)
        monitor.setOverride(Connectivity.RequiresConnection)
        advanceUntilIdle()

        assertEquals(listOf(Connectivity.Unknown, Connectivity.Unsatisfied, Connectivity.RequiresConnection), seen)
    }

    @Test
    fun `an offline override makes the client fail fast`() = runTest {
        // The Settings "Force Offline" switch, and the reason the override exists at all:
        // a simulator's network is always up, so without it the banner would be a lie.
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))
        val monitor = NetworkMonitor(FakeWatcher())
        monitor.setOverride(Connectivity.Unsatisfied)

        val error = runCatching {
            KtorHttpClient(
                baseUrl = "https://api.tasktracker.test",
                engine = transport.engine(),
                connectivity = monitor,
            ).send(HttpRequest(HttpMethod.Get, "/v1/projects", requiresAuth = false))
        }.exceptionOrNull()

        assertEquals(com.tasktracker.core.AppError.Offline, error)
        assertEquals(0, transport.requestCount)
    }

    @Test
    fun `only satisfied allows sync`() {
        assertTrue(Connectivity.Satisfied.allowsSync)
        // A captive portal has a network and cannot reach anything, and a cold-start
        // monitor knows nothing — neither is worth spending a request on.
        assertFalse(Connectivity.Unsatisfied.allowsSync)
        assertFalse(Connectivity.RequiresConnection.allowsSync)
        assertFalse(Connectivity.Unknown.allowsSync)
    }

    @Test
    fun `the client does not consult connectivity when it has no source`() = runTest {
        val transport = StubTransport()
        transport.enqueue(Stub.json("""{"ok":true}"""))

        KtorHttpClient(
            baseUrl = "https://api.tasktracker.test",
            engine = transport.engine(),
        ).send(HttpRequest(HttpMethod.Get, "/x", requiresAuth = false))

        assertEquals(1, transport.requestCount)
    }
}
