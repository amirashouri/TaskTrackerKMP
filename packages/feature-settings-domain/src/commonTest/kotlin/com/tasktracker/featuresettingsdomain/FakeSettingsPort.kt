package com.tasktracker.featuresettingsdomain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A port that answers from scripted values and counts what was asked of it.
 *
 * Mutable without an actor, for the same reason the original needed one: the port is `Sendable`
 * and the counters are written from suspending methods. `kotlinx.coroutines.test`'s `runTest`
 * runs every coroutine of a test on one thread, so plain fields are sufficient and a mutex
 * would only obscure what is being asserted.
 *
 * The two update methods return [emptyFlow], which is the Kotlin answer to the original's
 * `AsyncStream { $0.finish() }` — a stream that completes immediately and never emits, so a
 * collector of it runs to completion and stops rather than hanging a test on a subscription
 * nothing will ever fulfil.
 */
class FakeSettingsPort(
    connectivity: ConnectivitySummary = ConnectivitySummary.Online,
    canSyncNow: Boolean = true,
    outcome: SyncOutcome = SyncOutcome.Started,
    version: AppVersion = AppVersion(marketing = "1.0", build = "1"),
) : SettingsPort {

    private var connectivityValue: ConnectivitySummary = connectivity
    private var canSyncNowValue: Boolean = canSyncNow
    private var outcomeValue: SyncOutcome = outcome
    private val syncNowCallsMutable = mutableListOf<SyncOutcome>()
    private val endSessionCallsMutable = mutableListOf<Unit>()
    private val forcedOfflineValues = mutableListOf<Boolean>()

    val version: AppVersion = version

    val syncNowCount: Int get() = syncNowCallsMutable.size

    val sessionEndCount: Int get() = endSessionCallsMutable.size

    val forcedOfflineHistory: List<Boolean> get() = forcedOfflineValues.toList()

    fun setOutcome(next: SyncOutcome) {
        outcomeValue = next
    }

    fun setCanSyncNow(next: Boolean) {
        canSyncNowValue = next
    }

    override suspend fun connectivity(): ConnectivitySummary = connectivityValue

    override fun connectivityUpdates(): Flow<ConnectivitySummary> = emptyFlow()

    override suspend fun setForcedOffline(forced: Boolean) {
        forcedOfflineValues += forced
    }

    override suspend fun syncActivity(): SyncActivity = SyncActivity()

    override fun syncUpdates(): Flow<SyncActivity> = emptyFlow()

    override suspend fun canSyncNow(): Boolean = canSyncNowValue

    override suspend fun syncNow(): SyncOutcome {
        syncNowCallsMutable += outcomeValue
        return outcomeValue
    }

    override suspend fun endSession() {
        endSessionCallsMutable += Unit
    }

    override fun appVersion(): AppVersion = version
}