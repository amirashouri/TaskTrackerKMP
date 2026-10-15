package com.tasktracker.compositionroot

import com.tasktracker.core.randomUserID
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.routing.RootDestination
import com.tasktracker.routing.Router
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/**
 * The session layer's behaviour, with every collaborator a stand-in.
 *
 * The graph wires these types against Room stores and a live coordinator, neither of which a
 * `commonTest` can reach — there is no `Context` for the database, and a real coordinator
 * wants a real network. What is under test is the *decision* each type makes, and each
 * decision is observable through a port: which route the router took, whether the metadata
 * store was cleared, whether the cache was written.
 *
 * [SessionTeardown] is tested through the real class rather than a double, because its whole
 * job is coordinating those stores and a double would only prove the test agrees with
 * itself. [SessionCoordinator] is tested with the real teardown too, for the same reason —
 * the interesting assertion is that a sign-out runs it at all.
 */
class SessionCoordinatorTest {

    @Test
    fun `a restored signed-out session does not tear anything down`() = runTest {
        // The load-bearing guard. A fresh install restores to `SignedOut` because there are no
        // credentials, which is the app's normal initial state; tearing down there would clear
        // pull metadata and stop sync on every cold start, discarding unsynced work for a
        // session that never existed.
        val session = RecordingSessionPort().apply { restoreResult = SessionState.SignedOut }
        val stores = InMemoryStores().apply { seed() }
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()
        harness.coordinator.stop()

        assertEquals(0, stores.metadata.removeAllCalls)
        assertEquals(0, stores.session.clearCalls)
        // The metadata is still there, which is the assertion the guard exists for: a cold
        // launch must not discard where the last session's pull had got to.
        assertEquals(setOf("projects", "workItems"), stores.metadata.scopes())
    }

    @Test
    fun `a restored signed-in session enters the main area and caches the identity`() = runTest {
        val identity = UserIdentity(randomUserID(), "ada@example.com", "Ada")
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(identity)
        }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.drain()
        harness.coordinator.stop()

        assertEquals(RootDestination.Main, harness.router.root)
        // The cache write is what lets a later status change be credited to an author.
        val cached = assertNotNull(harness.stores.session.saved)
        assertEquals(identity.id.stringValue, cached.userID)
        assertEquals(identity.email, cached.email)
        assertEquals(identity.displayName, cached.displayName)
    }

    @Test
    fun `signing in after the fact caches and routes without a restore`() = runTest {
        val session = RecordingSessionPort().apply { restoreResult = SessionState.SignedOut }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.drain()

        session.publish(SessionState.SignedIn(UserIdentity(randomUserID(), "grace@example.com")))
        harness.drain()

        assertEquals(RootDestination.Main, harness.router.root)
        assertNotNull(harness.stores.session.saved)
        harness.coordinator.stop()
    }

    @Test
    fun `signing out tears down and returns to the auth gate`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val stores = InMemoryStores().apply { seed() }
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()

        harness.coordinator.signOut()
        // `signOut` goes through the port rather than the teardown, because the port is what
        // forgets the credentials. Asserted through the port's own count, so a coordinator
        // that tore down without signing out is a failure here rather than a silent one.
        assertEquals(1, session.signOutCalls)
        harness.drain()

        assertEquals(1, stores.metadata.removeAllCalls)
        assertEquals(1, stores.session.clearCalls)
        assertEquals(RootDestination.Auth, harness.router.root)
        harness.coordinator.stop()
    }

    @Test
    fun `an expiry tears down and reports it as an unauthorized end`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.drain()

        // What the vault publishes when a token cannot be refreshed. No `pendingReason`, so the
        // coordinator has to supply a reason itself rather than report nothing.
        session.publish(SessionState.SignedOut)
        harness.drain()

        assertEquals(1, harness.stores.metadata.removeAllCalls)
        assertEquals(RootDestination.Auth, harness.router.root)
        harness.coordinator.stop()
    }

    /**
     * The failure the settings screen's popup exists for.
     *
     * A sign-out asks the server to revoke the refresh token family before it forgets anything,
     * so a server that cannot be reached leaves the session standing. This asserts the whole of
     * that outcome: the port was asked, nothing was torn down, and the router did not move —
     * which is exactly the state that leaves the user on Settings with a live session and
     * something to decide.
     */
    @Test
    fun `a sign-out the server could not confirm leaves the session standing`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val stores = InMemoryStores().apply { seed() }
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()

        session.failSignOut = true
        val failure = runCatching { harness.coordinator.signOut() }
        harness.drain()

        // Rethrown rather than absorbed: the caller is what turns this into the offer to force,
        // and a coordinator that swallowed it would leave the user looking at a button that
        // silently did nothing.
        assertTrue(failure.isFailure, "a failed sign-out must reach the caller")
        assertEquals(1, session.signOutCalls)
        assertEquals(0, stores.metadata.removeAllCalls)
        assertEquals(setOf("projects", "workItems"), stores.metadata.scopes())
        assertEquals(RootDestination.Main, harness.router.root)
        harness.coordinator.stop()
    }

    @Test
    fun `a failed sign-out does not arm a reason for the next one`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.drain()

        session.failSignOut = true
        runCatching { harness.coordinator.signOut() }
        harness.drain()

        // The user retries and this time the server answers. Exactly one teardown, and it
        // belongs to *this* sign-out: a reason left armed by the failure would have been
        // consumed by the retry, and the log would name a session that never ended.
        session.failSignOut = false
        harness.coordinator.signOut()
        harness.drain()

        assertEquals(1, harness.stores.metadata.removeAllCalls)
        assertEquals(RootDestination.Auth, harness.router.root)
        harness.coordinator.stop()
    }

    @Test
    fun `a forced sign-out erases the store and never asks the server`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val stores = InMemoryStores().apply { seed() }
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()

        harness.coordinator.forceSignOut()
        harness.drain()

        assertEquals(1, session.signOutLocallyCalls)
        // The distinction the two port methods exist for. A force asks nothing of the network,
        // which is what makes it unable to fail for want of one — and the server-facing call is
        // precisely the one that can.
        assertEquals(0, session.signOutCalls)
        assertEquals(1, stores.eraser.eraseAllCalls)
        assertEquals(1, stores.metadata.removeAllCalls)
        assertEquals(RootDestination.Auth, harness.router.root)
        harness.coordinator.stop()
    }

    /**
     * The branch's other side, asserted through the coordinator rather than the teardown.
     *
     * `handle` is where the force flag is read, so this is the only place that can show the
     * flag does not leak into the ordinary path. A wipe reaching a normal sign-out would erase
     * the data of a user who never chose to lose it.
     */
    @Test
    fun `an ordinary sign-out does not erase the store`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val stores = InMemoryStores().apply { seed() }
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()

        harness.coordinator.signOut()
        harness.drain()

        assertEquals(0, stores.eraser.eraseAllCalls)
        assertEquals(1, stores.metadata.removeAllCalls)
        assertEquals(RootDestination.Auth, harness.router.root)
        harness.coordinator.stop()
    }

    @Test
    fun `a restoring session is reported to the router`() = runTest {
        val session = RecordingSessionPort().apply { restoreResult = SessionState.Restoring }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.drain()

        // Not "signed out" and not "signed in": the launch screen needs to know the session is
        // being read, or a returning user sees the sign-in form flash before their content.
        assertTrue(harness.router.isRestoringSession)
        assertEquals(RootDestination.Auth, harness.router.root)
        harness.coordinator.stop()
    }

    @Test
    fun `a failed restore leaves the session alone and does not tear down`() = runTest {
        val session = RecordingSessionPort().apply { failRestore = true }
        val stores = InMemoryStores().apply { seed() }
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()
        harness.coordinator.stop()

        // The port's contract is that a failed restore leaves the previous state untouched, so
        // there is nothing to end and nothing to tear down. Treating the throw as a sign-out is
        // what would wipe a live session's metadata.
        assertEquals(0, stores.metadata.removeAllCalls)
        assertEquals(setOf("projects", "workItems"), stores.metadata.scopes())
    }

    @Test
    fun `start twice restores once`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.coordinator.start()
        harness.drain()

        // A second listener would write the same router twice per transition and tear down
        // twice per sign-out — the shape of the "a recomposition re-ran the effect" bug. The
        // count is on `restore` because that is the observable half of a second `start`.
        assertEquals(1, session.restoreCalls)
        harness.coordinator.stop()
    }

    @Test
    fun `a second signed-out does not tear down twice`() = runTest {
        val session = RecordingSessionPort().apply {
            restoreResult = SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com"))
        }
        val stores = InMemoryStores()
        val harness = harness(session = session, stores = stores)

        harness.coordinator.start()
        harness.drain()

        session.publish(SessionState.SignedOut)
        harness.drain()
        // A redundant sign-out publishes `SignedOut` again. Tearing down a second time would
        // clear metadata the *next* session has already begun writing.
        session.publish(SessionState.SignedOut)
        harness.drain()

        assertEquals(1, stores.metadata.removeAllCalls)
        harness.coordinator.stop()
    }

    @Test
    fun `stop ends the subscription`() = runTest {
        val session = RecordingSessionPort().apply { restoreResult = SessionState.SignedOut }
        val harness = harness(session = session)

        harness.coordinator.start()
        harness.drain()
        harness.coordinator.stop()

        // After stopping, a published transition reaches nobody. Asserted through the router,
        // which is the only thing the listener writes to.
        session.publish(SessionState.SignedIn(UserIdentity(randomUserID(), "ada@example.com")))
        harness.drain()

        assertEquals(RootDestination.Auth, harness.router.root)
    }

    // MARK: Harness

    private class Harness(
        val coordinator: SessionCoordinator,
        val router: Router,
        val stores: InMemoryStores,
    ) {
        /**
         * Lets every launched coroutine reach its first suspension.
         *
         * A few `yield`s rather than a barrier or an await: the chain is listener → port →
         * coordinator → store, and each hop suspends at least once, so yielding more times than
         * there are hops drains it without depending on a scheduler detail. Anything more
         * precise would be testing the dispatcher rather than the coordinator.
         */
        suspend fun drain() {
            repeat(8) { yield() }
        }
    }

    /**
     * Everything on [TestScope.backgroundScope], so `drain` is a real barrier rather than a
     * sleep.
     *
     * `backgroundScope` rather than `this` because the coordinator's listener and its restore
     * are long-lived: `runTest` waits for its own scope's children, so a listener attached to
     * it would make every test hang until it was cancelled.
     */
    private fun TestScope.harness(
        session: RecordingSessionPort,
        stores: InMemoryStores = InMemoryStores(),
    ): Harness {
        val router = Router()
        val coordinator = SessionCoordinator(
            session = session,
            router = router,
            // The real teardown, over recording stores, rather than a double: the assertion
            // these tests want is that a sign-out *runs* it, and a double would only prove the
            // test agrees with itself. The one thing a `NoResolver` cannot show — that the
            // session scope is actually reset — is asserted in `SessionTeardownTest` against a
            // real container instead of faked into every test here.
            teardown = stores.teardown(resolver = NoResolver(), scope = backgroundScope),
            sessionCache = stores.session,
            dateProvider = FixedDateProvider(),
            scope = backgroundScope,
        )
        return Harness(coordinator, router, stores)
    }
}