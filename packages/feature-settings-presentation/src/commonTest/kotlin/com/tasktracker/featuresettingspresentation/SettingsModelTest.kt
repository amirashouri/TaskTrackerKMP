package com.tasktracker.featuresettingspresentation

import com.tasktracker.core.AppError
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException

/**
 * [SettingsModel] against a port that answers from scripted values.
 *
 * ## What this module's fake is for
 *
 * `:packages:feature-settings-domain` keeps `FakeSettingsPort` for *its* tests, and it is not
 * published as a test fixture, so this is a second one — deliberately different in the one way
 * that matters here. This fake's two update methods return [MutableStateFlow]s a test can push
 * into, because what the model needs proving about the streams is that a value *arriving*
 * changes the state; the domain's own fake returns `emptyFlow` because there it only needs a
 * collector that terminates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsModelTest {

    @Test
    fun observingReadsBothValuesOnceSoTheFirstFrameIsNotTheDefault() = runTest {
        val port = RecordingSettingsPort(
            connectivity = ConnectivitySummary.NeedsConnection,
            activity = SyncActivity(phase = SyncActivity.Phase.Retrying, queueDepth = 4),
        )
        val model = SettingsModel(port)

        model.observe(this)

        advanceUntilIdle()
        assertEquals(ConnectivitySummary.NeedsConnection, model.connectivity)
        assertEquals(SyncActivity.Phase.Retrying, model.activity.phase)
        assertEquals(4, model.activity.queueDepth)

        model.stopObserving()
    }

    @Test
    fun aValueArrivingOnAStreamReplacesTheReadOne() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)
        model.observe(this)
        advanceUntilIdle()

        port.connectivityFlow.value = ConnectivitySummary.Offline
        port.activityFlow.value = SyncActivity(phase = SyncActivity.Phase.WaitingForConnection, queueDepth = 2)
        advanceUntilIdle()

        assertEquals(ConnectivitySummary.Offline, model.connectivity)
        assertEquals(2, model.activity.queueDepth)

        model.stopObserving()
    }

    @Test
    fun observingTwiceKeepsOneSubscription() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)

        model.observe(this)
        model.observe(this)
        advanceUntilIdle()

        // Two `observe` calls would mean two collectors writing the same field, and the tell
        // is a second read of the one-shot values: the streams here emit their current value on
        // subscription, so a doubled subscription is visible as a doubled `reload`.
        assertEquals(1, port.connectivityReads)

        model.stopObserving()
    }

    @Test
    fun stoppingEndsEveryStream() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        model.observe(scope)
        scope.advanceUntilIdle()

        model.stopObserving()
        port.connectivityFlow.value = ConnectivitySummary.Offline
        scope.advanceUntilIdle()

        assertEquals(ConnectivitySummary.Online, model.connectivity)
    }

    @Test
    fun theVersionIsReadOnceBecauseARunningAppCannotChangeIt() = runTest {
        val port = RecordingSettingsPort(version = AppVersion(marketing = "2.3", build = "77"))
        val model = SettingsModel(port)

        assertEquals("2.3 (77)", model.version.display)
        model.observe(this)
        model.requestSync()
        advanceUntilIdle()

        assertEquals(1, port.versionReads)
        model.stopObserving()
    }

    @Test
    fun forcingOfflineStoresTheChoiceAndNeverDerivesItFromTheMonitor() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)
        model.observe(this)
        advanceUntilIdle()

        model.applyForceOffline(true)

        assertTrue(model.isForceOffline)
        assertEquals(listOf(true), port.forcedOfflineHistory)
        // The monitor still reports Online, and the flag stays set: that is the whole reason
        // the two are separate state.
        assertEquals(ConnectivitySummary.Online, model.connectivity)
        model.stopObserving()

        model.applyForceOffline(false)
        assertFalse(model.isForceOffline)
        assertEquals(listOf(true, false), port.forcedOfflineHistory)
    }

    @Test
    fun restoringAChoiceSetsTheFlagWithoutDispatchingIt() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)

        model.restoreForceOffline(true)

        assertTrue(model.isForceOffline)
        // A remembered choice must not be re-published on every composition: doing so would
        // re-assert the override over whatever has happened on the real network since.
        assertEquals(emptyList(), port.forcedOfflineHistory)
    }

    @Test
    fun aSyncRequestCarriesThePortsAnswerIntoTheOutcome() = runTest {
        val port = RecordingSettingsPort(canSyncNow = false)
        val model = SettingsModel(port)

        model.requestSync()
        // Offline reaches the port and is answered by the domain, not by the model.
        assertEquals(SyncOutcome.Offline, model.commandOutcome)
        assertEquals(0, port.syncNowCalls)

        port.canSync = true
        model.requestSync()
        assertEquals(SyncOutcome.Started, model.commandOutcome)
        assertEquals(1, port.syncNowCalls)
    }

    @Test
    fun aRefusedRequestIsReportedAndCanBeDismissed() = runTest {
        val port = RecordingSettingsPort(canSyncNow = true)
        val model = SettingsModel(port)

        model.requestSync()
        assertEquals(SyncOutcome.Started, model.commandOutcome)

        model.dismissCommandOutcome()
        assertNull(model.commandOutcome)
    }

    @Test
    fun signingOutEndsTheSessionThroughThePortAndNotTheAuthFeature() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)

        model.signOut()

        assertEquals(1, port.sessionEndCalls)
        // Nothing to answer on the happy path, which is what keeps the popup off a screen where
        // the sign-out simply worked.
        assertNull(model.signOutFailure)
    }

    @Test
    fun aSignOutTheServerCouldNotConfirmIsHeldRatherThanThrown() = runTest {
        val port = RecordingSettingsPort().apply { endSessionFailure = AppError.Timeout }
        val model = SettingsModel(port)

        // No throw: the failure is the screen's state, not the caller's problem. A model that
        // rethrew would leave the shell launching a coroutine whose exception has nowhere to go.
        model.signOut()

        assertEquals(AppError.Timeout, model.signOutFailure)
        assertEquals(1, port.sessionEndCalls)
    }

    @Test
    fun aCancelledSignOutDoesNotRaiseThePopup() = runTest {
        val port = RecordingSettingsPort().apply {
            endSessionFailure = CancellationException("the tab was left mid-sign-out")
        }
        val model = SettingsModel(port)

        // Caught here rather than in the model: the screen is disposed while this call is in
        // flight whenever the user leaves the tab, and a `runCatching` in the model would read
        // that as a failed sign-out and raise a popup over whatever the user navigated to next.
        val thrown = try {
            model.signOut()
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertNotNull(thrown, "a cancellation must be rethrown, not absorbed")
        assertNull(model.signOutFailure)
    }

    @Test
    fun forcingASignOutTakesTheForceCallAndClearsTheFailure() = runTest {
        val port = RecordingSettingsPort().apply { endSessionFailure = AppError.Offline }
        val model = SettingsModel(port)
        model.signOut()
        assertEquals(AppError.Offline, model.signOutFailure)

        model.forceSignOut()

        // The second port method, not the first: `forceEndSession` is the one that erases, and a
        // screen wired to the wrong one would sign the user out and leave their data behind.
        assertEquals(1, port.forceEndSessionCalls)
        assertEquals(1, port.sessionEndCalls)
        // Cleared *before* the call, so a recomposition in the window between this returning and
        // the route change it causes cannot re-raise a question the user has just answered.
        assertNull(model.signOutFailure)
    }

    @Test
    fun decliningToForceLeavesTheSessionExactlyAsItWas() = runTest {
        val port = RecordingSettingsPort().apply { endSessionFailure = AppError.Timeout }
        val model = SettingsModel(port)
        model.signOut()

        model.dismissSignOutFailure()

        assertNull(model.signOutFailure)
        // Nothing was revoked and nothing was erased, so the session is still live. Asserted
        // because a dismiss that quietly forced the sign-out would look identical from the
        // popup's side and would destroy the user's data against their answer.
        assertEquals(0, port.forceEndSessionCalls)
        assertEquals(1, port.sessionEndCalls)
    }

    @Test
    fun observationCanBeResumedAfterStopping() = runTest {
        val port = RecordingSettingsPort()
        val model = SettingsModel(port)

        model.observe(this)
        advanceUntilIdle()
        model.stopObserving()
        port.connectivityFlow.value = ConnectivitySummary.Unknown
        advanceUntilIdle()
        assertEquals(ConnectivitySummary.Online, model.connectivity)

        model.observe(this)
        advanceUntilIdle()
        assertEquals(ConnectivitySummary.Unknown, model.connectivity)
        model.stopObserving()
    }
}

/**
 * A port whose readings are settable and whose calls are countable.
 *
 * [connectivityFlow] and [activityFlow] are public so a test can publish an update without a
 * channel it would then have to close; a model that stopped collecting would simply keep the
 * old value and the assertion would fail, which is the failure this is meant to catch.
 */
class RecordingSettingsPort(
    connectivity: ConnectivitySummary = ConnectivitySummary.Online,
    private val activity: SyncActivity = SyncActivity(),
    canSyncNow: Boolean = true,
    private val outcome: SyncOutcome = SyncOutcome.Started,
    private val version: AppVersion = AppVersion(marketing = "1.0", build = "1"),
) : SettingsPort {

    val connectivityFlow = MutableStateFlow(connectivity)
    val activityFlow = MutableStateFlow(activity)

    var canSync: Boolean = canSyncNow

    var connectivityReads: Int = 0
        private set

    var versionReads: Int = 0
        private set

    var syncNowCalls: Int = 0
        private set

    var sessionEndCalls: Int = 0
        private set

    /**
     * When set, [endSession] fails with it instead of ending the session.
     *
     * The server-facing half of a sign-out is the only part that can fail, and this is how a
     * model test reaches the popup that failure exists to raise. A `Throwable` rather than an
     * [AppError] so a test can also script the one failure the model must *not* catch:
     * cancellation, which arrives when the user leaves the tab mid-sign-out.
     */
    var endSessionFailure: Throwable? = null

    var forceEndSessionCalls: Int = 0
        private set

    val forcedOfflineHistory = mutableListOf<Boolean>()

    override suspend fun connectivity(): ConnectivitySummary {
        connectivityReads++
        return connectivityFlow.value
    }

    override fun connectivityUpdates(): Flow<ConnectivitySummary> = connectivityFlow

    override suspend fun setForcedOffline(forced: Boolean) {
        forcedOfflineHistory += forced
    }

    override suspend fun syncActivity(): SyncActivity = activity

    override fun syncUpdates(): Flow<SyncActivity> = activityFlow

    override suspend fun canSyncNow(): Boolean = canSync

    override suspend fun syncNow(): SyncOutcome {
        syncNowCalls++
        return outcome
    }

    override suspend fun endSession() {
        sessionEndCalls++
        endSessionFailure?.let { throw it }
    }

    override suspend fun forceEndSession() {
        forceEndSessionCalls++
    }

    override fun appVersion(): AppVersion {
        versionReads++
        return version
    }

    override suspend fun userDetails(): UserDetails = UserDetails()

    override fun userDetailsUpdates(): kotlinx.coroutines.flow.Flow<UserDetails> = kotlinx.coroutines.flow.flowOf(UserDetails())

    override suspend fun setUserDetails(details: UserDetails) = Unit
}

