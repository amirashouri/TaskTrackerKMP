package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.CheckServerAddress
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.ServerAddressProbe
import com.tasktracker.featuresettingsdomain.ServerAddressSettings
import com.tasktracker.featuresettingsdomain.ServerAddressValidation
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.UserPosition
import com.tasktracker.featuresettingsdomain.UserProfileChanges
import com.tasktracker.featuresettingsdomain.UserProfileGateway
import com.tasktracker.featuresettingsdomain.UserStatus
import com.tasktracker.network.Connectivity
import com.tasktracker.network.ConnectivityWatcher
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.userdefaults.API_BASE_URL_PREFERENCE_KEY
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException

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

    private fun service(
        monitor: NetworkMonitor,
        apiBaseUrl: String? = null,
        checkServerAddress: CheckServerAddress = CheckServerAddress(ServerAddressProbe { true }),
        defaults: UserDefaultsStore = UserDefaultsStore(InMemoryPreferences()),
        profileGateway: UserProfileGateway? = null,
    ) = LiveSettingsService(
        monitor = monitor,
        // Never asked to sync by the connectivity tests; see `UnusedSyncDependencies`.
        coordinator = unusedSyncCoordinator(),
        version = AppVersion(marketing = "1.0", build = "1"),
        onEndSession = {},
        onForceEndSession = {},
        apiBaseUrl = apiBaseUrl,
        checkServerAddress = checkServerAddress,
        defaults = defaults,
        profileGateway = profileGateway,
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
            onEndSession = {},
            onForceEndSession = {},
            apiBaseUrl = null,
            checkServerAddress = CheckServerAddress(ServerAddressProbe { true }),
            defaults = UserDefaultsStore(InMemoryPreferences()),
        )

        assertEquals(version, service.appVersion())
        assertEquals("2.1 (77)", service.appVersion().display)
    }

    // MARK: - Server Address

    /**
     * A probe that records every address it is asked about.
     *
     * The list is the assertion, not the boolean: a save path that validated the shape but
     * skipped the network would still persist a well-formed address, so the only thing that
     * tells "checked" apart from "looked valid" is that the probe was asked.
     */
    private class RecordingProbe : ServerAddressProbe {
        val asked = mutableListOf<String>()

        override suspend fun answersOk(baseUrl: String): Boolean {
            asked += baseUrl
            return true
        }
    }

    private fun emptyMonitor() = NetworkMonitor.forWatcher(FakeConnectivityWatcher())

    @Test
    fun theServerAddressReportsTheInUseRootWithNoOverrideYet() = runTest {
        val service = service(
            monitor = emptyMonitor(),
            apiBaseUrl = "https://tasks.example.com",
        )

        assertEquals(
            ServerAddressSettings(inUse = "https://tasks.example.com", savedOverride = null),
            service.serverAddress(),
        )
        assertFalse(service.serverAddress().isCustom)
    }

    @Test
    fun aCheckedAddressIsProbedAndSavedInItsNormalisedForm() = runTest {
        val probe = RecordingProbe()
        val store = UserDefaultsStore(InMemoryPreferences())
        val service = service(
            monitor = emptyMonitor(),
            checkServerAddress = CheckServerAddress(probe),
            defaults = store,
        )

        val result = service.setServerAddress("HTTPS://Tasks.Example.COM/")

        assertEquals(ServerAddressValidation.Valid("https://tasks.example.com"), result)
        // The raw key 6.1's resolver reads at startup, and the normalised form rather than the
        // user's typing — the two must be the same string or the row and the wire disagree.
        assertEquals("https://tasks.example.com", store.value(API_BASE_URL_PREFERENCE_KEY))
        assertEquals("https://tasks.example.com", service.serverAddress().savedOverride)
        assertEquals(listOf("https://tasks.example.com"), probe.asked)
    }

    @Test
    fun anUnreachableAddressIsNotSaved() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        val service = service(
            monitor = emptyMonitor(),
            checkServerAddress = CheckServerAddress(ServerAddressProbe { false }),
            defaults = store,
        )

        val result = service.setServerAddress("https://tasks.example.com")

        assertEquals(
            ServerAddressValidation.Invalid(ServerAddressValidation.Reason.Unreachable),
            result,
        )
        assertNull(store.value(API_BASE_URL_PREFERENCE_KEY))
        assertNull(service.serverAddress().savedOverride)
    }

    @Test
    fun aMalformedAddressIsNeverProbedAndNotSaved() = runTest {
        val probe = RecordingProbe()
        val store = UserDefaultsStore(InMemoryPreferences())
        val service = service(
            monitor = emptyMonitor(),
            checkServerAddress = CheckServerAddress(probe),
            defaults = store,
        )

        val result = service.setServerAddress("not a url")

        assertTrue(result is ServerAddressValidation.Invalid)
        assertEquals(emptyList(), probe.asked)
        assertNull(store.value(API_BASE_URL_PREFERENCE_KEY))
    }

    @Test
    fun aBlankStoredValueReadsAsNoOverride() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set("", API_BASE_URL_PREFERENCE_KEY)
        val service = service(monitor = emptyMonitor(), defaults = store)

        assertNull(service.serverAddress().savedOverride)
        assertFalse(service.serverAddress().isCustom)
    }

    @Test
    fun resetRemovesTheSavedOverride() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set("https://tasks.example.com", API_BASE_URL_PREFERENCE_KEY)
        val service = service(monitor = emptyMonitor(), defaults = store)
        assertEquals("https://tasks.example.com", service.serverAddress().savedOverride)

        service.resetServerAddress()

        assertNull(store.value(API_BASE_URL_PREFERENCE_KEY))
        assertNull(service.serverAddress().savedOverride)
    }

    // MARK: - Self profile

    @Test
    fun openingSettingsReadsTheServerProfileAndConfirmsIt() = runTest {
        val profile = UserDetails(
            fullName = "Ada",
            position = UserPosition.Designer,
            avatarId = 3,
            email = "ada@example.com",
            status = UserStatus.Away,
            statusText = "At the dentist",
        )
        val gateway = FakeProfileGateway(loaded = profile)
        val store = UserDefaultsStore(InMemoryPreferences())
        val service = service(monitor = emptyMonitor(), defaults = store, profileGateway = gateway)

        assertEquals(profile, service.userDetails())
        // One read for the screen, not one per field: the header, the pickers and the note all
        // render from the same returned profile.
        assertEquals(1, gateway.loads)
        // The read is also the cache write, so the next launch has the profile before the GET
        // answers — and an offline launch has it at all.
        assertEquals(profile, store.value<UserDetails>(DefaultsKey.UserProfile))

        // A commit with no local edit sends nothing: the profile already matches the confirmed
        // copy, so there is no request to make and no answer to wait for.
        assertEquals(profile, service.commitUserDetails())
        assertTrue(gateway.changes.isEmpty())
    }

    @Test
    fun aReadFailureKeepsThePersistedProfile() = runTest {
        val profile = UserDetails(fullName = "Ada", email = "ada@example.com")
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set(profile, DefaultsKey.UserProfile)
        val gateway = FakeProfileGateway(loadFailure = AppError.Transport("down"))
        val service = service(monitor = emptyMonitor(), defaults = store, profileGateway = gateway)

        // Settings must still open offline; the cached profile is the answer, not an error.
        assertEquals(profile, service.userDetails())
    }

    @Test
    fun aDecodeFailureKeepsThePersistedProfile() = runTest {
        // A server that added or renamed a key fails strict decoding rather than yielding a
        // half-filled profile. That is still a read failure for Settings, not a reason to show
        // the user an empty identity.
        val profile = UserDetails(fullName = "Ada", email = "ada@example.com")
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set(profile, DefaultsKey.UserProfile)
        val gateway = FakeProfileGateway(loadFailure = AppError.Decoding("unknown key"))
        val service = service(monitor = emptyMonitor(), defaults = store, profileGateway = gateway)

        assertEquals(profile, service.userDetails())
    }

    @Test
    fun commitSendsOnlyTheChangedFieldAndStoresTheServerCopy() = runTest {
        val confirmed = UserDetails(
            fullName = "Ada",
            position = UserPosition.Designer,
            avatarId = 3,
            email = "ada@example.com",
            status = UserStatus.Away,
            statusText = "At the dentist",
        )
        val edited = confirmed.copy(fullName = "Ada Lovelace")
        val gateway = FakeProfileGateway(loaded = confirmed, saved = edited)
        val store = UserDefaultsStore(InMemoryPreferences())
        val service = service(monitor = emptyMonitor(), defaults = store, profileGateway = gateway)

        service.userDetails()
        service.setUserDetails(edited)
        // Staging is local and synchronous; the write is the explicit commit below.
        assertTrue(gateway.changes.isEmpty(), "an edit on screen must not reach the server by itself")
        assertEquals(edited, service.commitUserDetails())

        val change = gateway.changes.single()
        assertEquals("Ada Lovelace", change.fullName)
        // Everything the user did not touch is absent, so the server keeps its own values for
        // them. A `position` or `presence` in this body would be a write nobody asked for.
        assertNull(change.position)
        assertNull(change.avatarId)
        assertNull(change.status)
        assertNull(change.statusText)

        assertEquals(edited, store.value<UserDetails>(DefaultsKey.UserProfile))
    }

    @Test
    fun aFreeTextPositionIsNotClearedByANameEdit() = runTest {
        // The server holds something like "Senior Designer"; the picker cannot represent it and
        // reports `null`. `null` therefore means "unchanged", not "the user chose nothing": an
        // unrelated name edit must not send `position` and wipe the admin's column.
        val confirmed = UserDetails(fullName = "Ada", position = null, email = "ada@example.com")
        val edited = confirmed.copy(fullName = "Ada Lovelace")
        val gateway = FakeProfileGateway(loaded = confirmed, saved = edited)
        val service = service(monitor = emptyMonitor(), profileGateway = gateway)

        service.userDetails()
        service.setUserDetails(edited)
        service.commitUserDetails()

        val change = gateway.changes.single()
        assertEquals("Ada Lovelace", change.fullName)
        assertNull(change.position)
        assertNull(change.status)
        assertNull(change.avatarId)
        assertNull(change.statusText)
    }

    @Test
    fun aFailedCommitRestoresTheConfirmedProfileBeforeThrowing() = runTest {
        val confirmed = UserDetails(fullName = "Ada", email = "ada@example.com")
        val edited = confirmed.copy(statusText = "On vacation")
        val gateway = FakeProfileGateway(loaded = confirmed)
        val store = UserDefaultsStore(InMemoryPreferences())
        val service = service(monitor = emptyMonitor(), defaults = store, profileGateway = gateway)

        service.userDetails()
        service.setUserDetails(edited)
        gateway.updateFailure = AppError.Transport("the connection dropped")

        assertFailsWith<AppError.Transport> { service.commitUserDetails() }

        // The optimistic value is gone from both places the screen can read: an observer of the
        // stream sees the confirmed profile, and a relaunch reads it back from the store.
        assertEquals(confirmed, service.userDetailsUpdates().first())
        assertEquals(confirmed, store.value<UserDetails>(DefaultsKey.UserProfile))
    }

    @Test
    fun aCancelledCommitDoesNotRevertTheOptimisticValue() = runTest {
        val confirmed = UserDetails(fullName = "Ada", email = "ada@example.com")
        val edited = confirmed.copy(statusText = "On vacation")
        val gateway = FakeProfileGateway(loaded = confirmed)
        val service = service(monitor = emptyMonitor(), profileGateway = gateway)

        service.userDetails()
        service.setUserDetails(edited)
        gateway.updateFailure = CancellationException("the screen went away")

        assertFailsWith<CancellationException> { service.commitUserDetails() }

        // Cancellation is a disposed screen, not a refusal: the request may have reached the
        // server, so reverting would show a value the server might not hold. The next read
        // corrects it; that is what opening Settings does.
        assertEquals(edited, service.userDetailsUpdates().first())
    }

    @Test
    fun aCommitWithoutAGatewayIsALocalNoOp() = runTest {
        // The offline demo has no server and therefore no gateway. A picker must still move,
        // and Save must not turn into a failure for a build that never had a backend.
        val service = service(monitor = emptyMonitor())
        val local = UserDetails(fullName = "Local user")

        service.setUserDetails(local)
        assertEquals(local, service.commitUserDetails())
    }
}

/**
 * A gateway whose load and save answers are scripted, and whose change sets are recorded.
 *
 * The real service's contract is "send only the delta, restore the confirmed copy on failure";
 * a fake that applied the change set itself would be a second implementation of that contract
 * and could pass while the service was wrong.
 */
private class FakeProfileGateway(
    private val loaded: UserDetails = UserDetails(),
    var saved: UserDetails = loaded,
    var loadFailure: AppError? = null,
) : UserProfileGateway {

    var updateFailure: Throwable? = null

    var loads: Int = 0
        private set

    val changes = mutableListOf<UserProfileChanges>()

    override suspend fun load(): UserDetails {
        loads += 1
        loadFailure?.let { throw it }
        return loaded
    }

    override suspend fun update(changes: UserProfileChanges): UserDetails {
        this.changes += changes
        updateFailure?.let { throw it }
        return saved
    }
}