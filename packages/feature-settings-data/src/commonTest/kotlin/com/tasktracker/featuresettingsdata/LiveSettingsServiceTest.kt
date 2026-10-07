package com.tasktracker.featuresettingsdata

import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.network.Connectivity
import com.tasktracker.network.ConnectivityWatcher
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The connectivity half of [LiveSettingsService]: the four-way translation, and the override.
 *
 * ## The sync half is deliberately not covered here
 *
 * The mapping from `SyncStatus` to `SyncActivity.Phase` is a total `when` over a sealed
 * interface, so the guarantee the port actually relies on — that a status added upstream
 * becomes a build error in this module rather than a screen that renders the wrong word — is
 * enforced by the compiler, not by a test. Reaching the sync half at runtime would mean
 * standing up a `SyncEngine` over an outbox queue, a metadata store and a transport, which is
 * the harness `:packages:sync` already owns in its own `commonTest`; duplicating those fakes
 * here to assert a `when` the compiler has already checked would be more code and less
 * assurance.
 *
 * What is *not* a compile-time property, and is covered: that the forced-offline override is
 * the one the user asked for, that clearing it hands the path back, and that the stream carries
 * this feature's vocabulary rather than the monitor's.
 */
class LiveSettingsServiceTest {

    /**
     * A watcher that says nothing until told.
     *
     * Silent on `attach` on purpose. A monitor starts at [Connectivity.Unknown] so that a
     * launch cannot tell "no path" from "not checked yet", and a double that reported
     * [Connectivity.Satisfied] on attach would make every test start online — hiding the very
     * rule the override tests below depend on.
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
     * A started service whose **real** path is [platform].
     *
     * Driven through the watcher rather than through `setOverride`, and the distinction is the
     * whole point: the override is the slot Settings' "Force Offline" writes to, so installing
     * the real path that way would leave the two fighting over one field — and clearing the
     * override would fall back to the monitor's `Unknown` start value instead of to the path the
     * test meant to establish. The platform value is the only one a watcher report touches.
     */
    private fun subject(platform: Connectivity): Pair<LiveSettingsService, FakeConnectivityWatcher> {
        val watcher = FakeConnectivityWatcher()
        val monitor = NetworkMonitor.forWatcher(watcher).apply { start() }
        watcher.report(platform)
        return service(monitor) to watcher
    }

    private fun service(monitor: NetworkMonitor) = LiveSettingsService(
        monitor = monitor,
        // Never asked to sync by the connectivity tests; see `UnusedSyncDependencies`.
        coordinator = unusedSyncCoordinator(),
        version = AppVersion(marketing = "1.0", build = "1"),
        endSession = {},
        defaults = UserDefaultsStore(InMemoryPreferences()),
    )

    @Test
    fun everyPlatformPathBecomesItsOwnSummary() = runTest {
        // One case per pair, and all four asserted: the risk is a transposition, where two
        // monitor states reach the same summary and a captive portal renders as "Online". Each
        // is installed by the platform watcher, so this exercises the service's own mapping
        // rather than a second copy of it written in the test.
        assertEquals(ConnectivitySummary.Online, connectivityWith(Connectivity.Satisfied))
        assertEquals(ConnectivitySummary.Offline, connectivityWith(Connectivity.Unsatisfied))
        assertEquals(
            ConnectivitySummary.NeedsConnection,
            connectivityWith(Connectivity.RequiresConnection),
        )
        assertEquals(ConnectivitySummary.Unknown, connectivityWith(Connectivity.Unknown))

        // The summary vocabulary is exactly as wide as the platform's, so a fifth platform
        // state cannot be reported as one of four words the screen already knows.
        assertEquals(4, ConnectivitySummary.entries.size)
    }

    private suspend fun connectivityWith(platform: Connectivity): ConnectivitySummary =
        subject(platform = platform).first.connectivity()

    @Test
    fun forcingOfflineReportsOfflineWhateverTheRealPathIs() = runTest {
        // A simulator's path is always up, which is the whole reason the override exists.
        val (service, _) = subject(platform = Connectivity.Satisfied)
        assertEquals(ConnectivitySummary.Online, service.connectivity())

        service.setForcedOffline(true)

        assertEquals(ConnectivitySummary.Offline, service.connectivity())
    }

    @Test
    fun clearingTheOverrideHandsTheRealPathBack() = runTest {
        // The path underneath is deliberately *not* online, so the assertion after the clear
        // cannot be satisfied by the override simply never having been installed.
        val (service, _) = subject(platform = Connectivity.RequiresConnection)

        service.setForcedOffline(true)
        assertEquals(ConnectivitySummary.Offline, service.connectivity())

        service.setForcedOffline(false)

        // Back to the captive portal the device is really on, not to "online" as a side effect
        // of clearing the override.
        assertEquals(ConnectivitySummary.NeedsConnection, service.connectivity())
    }

    @Test
    fun theStreamCarriesSummariesAndFollowsTheRealPath() = runTest {
        val (service, watcher) = subject(platform = Connectivity.Unknown)

        // Starts at the monitor's own `Unknown`, already translated into this feature's words.
        assertEquals(ConnectivitySummary.Unknown, service.connectivityUpdates().first())

        watcher.report(Connectivity.Satisfied)

        assertEquals(ConnectivitySummary.Online, service.connectivityUpdates().first())
    }

    @Test
    fun theVersionIsReportedUnchanged() {
        val version = AppVersion(marketing = "2.1", build = "77")
        val service = LiveSettingsService(
            monitor = NetworkMonitor.forWatcher(FakeConnectivityWatcher()),
            coordinator = unusedSyncCoordinator(),
            version = version,
            endSession = {},
            defaults = UserDefaultsStore(InMemoryPreferences()),
        )

        assertEquals(version, service.appVersion())
        assertEquals("2.1 (77)", service.appVersion().display)
    }
}