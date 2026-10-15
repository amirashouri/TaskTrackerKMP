package com.tasktracker.featureauthdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.randomUserID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * An in-memory stand-in for the session port, so the use cases can be tested with no keychain
 * and no network.
 *
 * Records what it was asked to do, because the interesting assertions about [SignIn] are
 * about the arguments the port received — a normalised address, or no call at all.
 */
class FakeAuthSession : AuthSessionPort {

    private var failureToThrow: AppError? = null
    private var identityToReturn: UserIdentity =
        UserIdentity(id = randomUserID(), email = "ada@example.com", displayName = "Ada")
    private var stateToRestore: SessionState = SessionState.SignedOut

    val signInCalls: MutableList<Pair<String, String>> = mutableListOf()
    var restoreCount: Int = 0
        private set
    var signOutCount: Int = 0
        private set
    var signOutLocallyCount: Int = 0
        private set

    // MARK: Scripting

    /** Makes the next call throw, for proving a use case surfaces a failure rather than
     *  swallowing it. */
    fun setFailure(error: AppError?) {
        failureToThrow = error
    }

    fun setIdentityToReturn(identity: UserIdentity) {
        identityToReturn = identity
    }

    /** What [restore] settles on, so a test can assert the use case's return value. */
    fun setStateToRestore(state: SessionState) {
        stateToRestore = state
    }

    // MARK: AuthSessionPort

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signIn(email: String, password: String): UserIdentity {
        signInCalls += email to password
        failureToThrow?.let { throw it }
        return identityToReturn
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun restore(): SessionState {
        restoreCount += 1
        failureToThrow?.let { throw it }
        return stateToRestore
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signOut() {
        signOutCount += 1
    }

    /**
     * Counted separately from [signOutCount], because the difference between the two is the
     * whole point of the pair: a test that could not tell them apart could not tell a
     * server-facing sign-out from a device-only one.
     */
    @Throws(AppError::class, CancellationException::class)
    override suspend fun signOutLocally() {
        signOutLocallyCount += 1
    }

    /**
     * Empty rather than a real bus: no use-case test asserts on published states, and an
     * empty stream is the honest "this fake observes nothing" rather than a stream that would
     * silently swallow events a test should have noticed were missing.
     */
    override val sessionUpdates: Flow<SessionState> = emptyFlow()
}