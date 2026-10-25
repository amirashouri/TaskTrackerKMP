package com.tasktracker.featuresettingsdomain

import com.tasktracker.core.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val retryStuckCallsMutable = mutableListOf<Unit>()
    private val endSessionCallsMutable = mutableListOf<Unit>()
    private val forceEndSessionCallsMutable = mutableListOf<Unit>()
    private val forcedOfflineValues = mutableListOf<Boolean>()

    val version: AppVersion = version

    val syncNowCount: Int get() = syncNowCallsMutable.size

    val sessionEndCount: Int get() = endSessionCallsMutable.size

    val sessionForceEndCount: Int get() = forceEndSessionCallsMutable.size

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

    /** One submitted change, in the order the model sent it: the credential first, then what
     * the credential was standing in for. */
    data class PasswordChange(val newPassword: String, val oldPasswordOrCode: String)

    private val passwordChangesMutable = mutableListOf<PasswordChange>()

    /** What [changePassword] was asked to do, attempt by attempt — failures included, since
     * a refused attempt is still an attempt a test may want to count. */
    val passwordChanges: List<PasswordChange> get() = passwordChangesMutable.toList()

    /** When set, [changePassword] throws it after recording the attempt. */
    var changePasswordFailure: AppError? = null

    override suspend fun changePassword(newPassword: String, oldPasswordOrCode: String) {
        passwordChangesMutable += PasswordChange(newPassword = newPassword, oldPasswordOrCode = oldPasswordOrCode)
        changePasswordFailure?.let { throw it }
    }

    /**
     * Counted, because the rule worth asserting is the *order*: the queue has to be put back
     * before a cycle is asked for, and a double that only recorded "a retry happened" could
     * not tell the two orders apart.
     */
    override suspend fun retryStuckChanges() {
        retryStuckCallsMutable += Unit
    }

    /** How many times the refused changes were put back. */
    val retryStuckCount: Int get() = retryStuckCallsMutable.size

    override suspend fun endSession() {
        endSessionCallsMutable += Unit
    }

    /**
     * Counted separately from [sessionEndCount], because the difference between the two is the
     * whole point of the pair: a screen that could not tell them apart could not tell a
     * server-facing sign-out from a device-only one, and only the second erases data.
     */
    override suspend fun forceEndSession() {
        forceEndSessionCallsMutable += Unit
    }

    override fun appVersion(): AppVersion = version

    private var userDetailsValue: UserDetails = UserDetails()
    private val userDetailsFlow = MutableStateFlow(UserDetails())

    /** The last confirmed profile, which is what a failed commit reverts to in the real service. */
    private var confirmedUserDetails: UserDetails = UserDetails()

    /** When set, [commitUserDetails] throws it instead of confirming. */
    var commitUserDetailsFailure: AppError? = null

    var commitUserDetailsCount: Int = 0
        private set

    override suspend fun userDetails(): UserDetails = userDetailsValue

    override fun userDetailsUpdates(): Flow<UserDetails> = userDetailsFlow

    override suspend fun setUserDetails(details: UserDetails) {
        // Only the visible value; the confirmed one moves on commit, exactly as the real
        // service's snapshot does. A fake that moved both would make a revert assertion pass
        // even if the model forgot to restore anything.
        userDetailsFlow.value = details
    }

    /** Seeds both halves, for a test that starts from a signed-in profile. */
    fun seedUserDetails(details: UserDetails) {
        userDetailsValue = details
        confirmedUserDetails = details
        userDetailsFlow.value = details
    }

    override suspend fun commitUserDetails(): UserDetails {
        commitUserDetailsCount += 1
        commitUserDetailsFailure?.let { throw it }
        confirmedUserDetails = userDetailsFlow.value
        userDetailsValue = confirmedUserDetails
        return confirmedUserDetails
    }

    /**
     * The server-address half is inert here on purpose.
     *
     * This fake exists for the domain rules that consume the *other* methods; the save/probe
     * order and the raw-key write are asserted against `LiveSettingsService` in the data
     * module, where the real preference store and a fake probe are available. A second
     * implementation of that rule in a test double would be a second answer to it.
     */
    override suspend fun serverAddress(): ServerAddressSettings = ServerAddressSettings(
        inUse = null,
        savedOverride = null,
    )

    override suspend fun setServerAddress(input: String): ServerAddressValidation =
        ServerAddressValidation.Valid(input)

    override suspend fun resetServerAddress() = Unit
}