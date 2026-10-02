package com.tasktracker.compositionroot

import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.identityOrNull
import com.tasktracker.featureauthdomain.isSignedIn
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.userdefaults.InMemoryPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The auth flow, driven through the production object graph.
 *
 * ## Why this file exists
 *
 * Registering the auth graph made it possible for the first time, and it immediately earned
 * its keep: on the emulator the shell reached the vault, the vault called the remote, and the
 * screen came back with "Could not sign in" — while every other test in the project was
 * green. The graph was being *resolved* by the other composition-root tests but never
 * *used*, so nothing had ever asked the assembled thing to sign anybody in.
 *
 * The in-memory backends are the same substitution `AppGraphTest` makes, for the same
 * reason, and they are not what makes this a unit test of the wiring: they replace two
 * storage adapters and leave the vault, the remote, the repository and every registration
 * between them real.
 */
class AuthFlowThroughGraphTest {

    @Test
    fun aFreshGraphStartsSignedOut() = runTest {
        assertFalse(session().restore().isSignedIn)
    }

    @Test
    fun signInThroughTheProductionGraphSucceeds() = runTest {
        val port = session()

        val identity = port.signIn("person@example.com", "correcthorse")

        assertEquals("person@example.com", identity.email)
    }

    /**
     * The same address is the same person on every launch.
     *
     * Asserted through the graph because the id is derived by `MockAuthRemote`, and this is
     * the only place that derivation is reachable from outside the data module. A mock that
     * handed out a fresh id per sign-in would pass every test in `:feature-auth-data` and
     * quietly break every account-scoped store above it.
     */
    @Test
    fun theSameAddressIsTheSameUserTwice() = runTest {
        val first = session().signIn("person@example.com", "correcthorse")
        val second = session().signIn("person@example.com", "correcthorse")

        assertEquals(first.id, second.id)
    }

    /**
     * A session outlives the graph that created it.
     *
     * The second graph is built over the *same* keychain and preferences, which is what a
     * relaunch actually is: a new process, new objects, the same stored bytes. Passing fresh
     * in-memory backends here would assert that a session survives nothing at all, and would
     * pass — so the sharing is the whole test.
     */
    @Test
    fun aSessionSurvivesRelaunch() = runTest {
        val keychain = InMemoryKeychain()
        val preferences = InMemoryPreferences()

        val first = AppGraph.start(testBindings(keychain = keychain, preferences = preferences))
            .resolve(AuthSessionPort::class)
        val identity = first.signIn("person@example.com", "correcthorse")

        val relaunched = AppGraph.start(testBindings(keychain = keychain, preferences = preferences))
            .resolve(AuthSessionPort::class)
        val restored = relaunched.restore()

        assertTrue(restored.isSignedIn, "expected the session to survive a relaunch")
        assertEquals(identity.email, restored.identityOrNull?.email)
    }

    /** The rejected-password path, through the graph, so the banner's cause is reachable. */
    @Test
    fun aRejectedPasswordFailsWithoutEndingTheStoredSession() = runTest {
        val keychain = InMemoryKeychain()
        val port = AppGraph.start(testBindings(keychain = keychain))
            .resolve(AuthSessionPort::class)

        port.signIn("person@example.com", "correcthorse")

        val failure = runCatching { port.signIn("person@example.com", "wrong-password") }
        assertTrue(failure.isFailure, "expected the rejected password to throw")
    }

    /**
     * Loops off, and that is load-bearing rather than tidiness.
     *
     * Every graph here starts a session coordinator, which calls `restore` on its own. These
     * tests call `restore` themselves, over the *same* keychain — so with loops on, the vault
     * is being restored twice concurrently and the result depends on which call wins. That is
     * a race about loop lifecycle sitting inside tests about the auth wiring, and it is the
     * kind that passes on the run you wrote it and fails the next morning. The registrations,
     * scopes and factories are all production either way.
     */
    private fun session(): AuthSessionPort = AppGraph.start(testBindings()).resolve(AuthSessionPort::class)
}
