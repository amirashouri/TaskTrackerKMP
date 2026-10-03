package com.tasktracker.compositionroot

import com.tasktracker.core.TTLogger
import com.tasktracker.database.CachedSession
import com.tasktracker.di.Resolver
import com.tasktracker.network.SessionInvalidationReason
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * What [SessionTeardown] does, against the real collaborators it can be given here.
 *
 * A real container and a real in-memory database, because both of the interesting claims are
 * about *how* it touches them: that the session scope is dropped, and that exactly the two
 * stores are cleared. A pair of recording doubles would make both claims true by construction.
 */
class SessionTeardownTest {

    @Test
    fun `the session scope is reset`() = runTest {
        val resolver = testResolver()
        val stores = InMemoryStores()

        stores.teardown(resolver, backgroundScope).run(SessionInvalidationReason.UserLogout)

        // First, because everything after it depends on it: nothing should be able to resolve a
        // half-torn-down graph, and the only way to know the scope went is to ask the container.
        assertTrue((resolver as ResetCountingResolver).sessionScopeWasReset)
    }

    @Test
    fun `pull metadata is cleared`() = runTest {
        val stores = InMemoryStores().apply { seed() }

        stores.teardown(testResolver(), backgroundScope).run(SessionInvalidationReason.UserLogout)

        // Cursors describe a server-side view of the change log as seen by this session, so a
        // stale one under a new account would replay changes that account already applied.
        assertEquals(1, stores.metadata.removeAllCalls)
        assertEquals(emptySet(), stores.metadata.scopes())
    }

    @Test
    fun `the session cache is cleared`() = runTest {
        val stores = InMemoryStores()
        stores.session.save(cachedSession())

        stores.teardown(testResolver(), backgroundScope).run(SessionInvalidationReason.UserLogout)

        // Cleared before anything else can write a row attributed to this account. A status
        // change made afterwards is recorded with an unknown author, which is the truth.
        assertEquals(1, stores.session.clearCalls)
        assertNull(stores.session.held())
    }

    /*
     * `the outbox is left alone` used to live here. It now lives in [HostedGraphScenarios],
     * because it opens a real `RoomOutboxQueue` and Room's `inMemoryDatabaseBuilder` needs a
     * `Context` on Android. A recording outbox double would have made the assertion true by
     * construction, so the fix was to move the test rather than to weaken it — see the note in
     * that file for the full reasoning.
     */

    @Test
    fun `two teardowns in a row are safe`() = runTest {
        val stores = InMemoryStores().apply { seed() }
        val teardown = stores.teardown(testResolver(), backgroundScope)

        teardown.run(SessionInvalidationReason.Forbidden)
        // The second one is not hypothetical: a redundant sign-out publishes `SignedOut`
        // twice, and the coordinator's guard is the only thing between that and a second run.
        teardown.run(SessionInvalidationReason.Forbidden)

        // Idempotent rather than throwing, because a teardown that threw would surface from
        // inside the session collector and take the app's navigation with it.
        assertEquals(2, stores.metadata.removeAllCalls)
        assertEquals(2, stores.session.clearCalls)
    }

    @Test
    fun `a store that throws does not abort the rest of the teardown`() = runTest {
        val stores = InMemoryStores().apply { seed() }
        val teardown = SessionTeardown(
            resolver = testResolver(),
            // Fails on every call, so a teardown that let the throw escape would never reach the
            // session cache — and a session cache left holding the old identity is the bug this
            // ordering prevents.
            metadata = FailingMetadataStore(),
            sessionCache = stores.session,
            syncCoordinator = silentSyncCoordinator(scope = backgroundScope),
        )

        teardown.run(SessionInvalidationReason.RefreshRejected)

        assertEquals(1, stores.session.clearCalls)
        // The metadata this teardown held is untouched, because the store refused.
        assertEquals(setOf("projects", "workItems"), stores.metadata.scopes())
    }

    /**
     * The reason a session ended is the one thing a teardown records rather than does.
     *
     * Every other observable here is state the teardown *changed* — and the two ways a session
     * ends change exactly the same state, in the same order, for the same duration. A tap on
     * Sign Out and a `403` from the server are indistinguishable in every other effect, so if
     * the reason is not written down anywhere then "the app signed me out" has no answer to
     * give, and the only way to recover it is to guess from whatever the user saw.
     *
     * Asserted through a recording sink rather than a field on the teardown, because the log
     * line *is* the behaviour: it is what a person reads in a bug report. A test that asserted
     * a captured reason property would pass even if the line were never written, which is
     * precisely the failure this exists to prevent.
     *
     * Both reasons are asserted, and in the same teardown, so the assertion is about the
     * distinction rather than about the presence of any text. A line that hard-coded one
     * reason would satisfy a single-reason test and be wrong in production.
     */
    @Test
    fun `the reason the session ended is recorded`() = runTest {
        val lines = mutableListOf<String>()
        val revert = TTLogger.auth.install { _, category, text ->
            lines += "$category: $text"
        }
        val stores = InMemoryStores()
        val teardown = stores.teardown(testResolver(), backgroundScope)

        try {
            teardown.run(SessionInvalidationReason.UserLogout)
            teardown.run(SessionInvalidationReason.Unauthorized)
        } finally {
            // Restored, or every later test in this JVM logs into a list nobody is reading.
            revert()
        }

        assertTrue(
            lines.any { it == "auth: session ended: userLogout" },
            "expected a tapped sign-out to be recorded as userLogout, got $lines",
        )
        assertTrue(
            lines.any { it == "auth: session ended: unauthorized" },
            "expected an expiry to be recorded as unauthorized, got $lines",
        )
    }

    // MARK: Harness

    /**
     * The production graph, wrapped so a session-scope reset is visible.
     *
     * A real container rather than a double: `resetSessionScope` is the container's own
     * bookkeeping, and a double asserting on a call it received would prove nothing about
     * whether the real one does it.
     */
    private fun testResolver(): Resolver {
        val graph = AppGraph.start(testBindings())
        return ResetCountingResolver(graph)
    }

    private class ResetCountingResolver(private val delegate: Resolver) : Resolver {
        var sessionScopeWasReset: Boolean = false
            private set

        override fun resetSessionScope() {
            sessionScopeWasReset = true
            delegate.resetSessionScope()
        }

        override fun <T : Any> resolve(klass: kotlin.reflect.KClass<T>): T = delegate.resolve(klass)
        override fun <T : Any> getOrNull(klass: kotlin.reflect.KClass<T>): T? = delegate.getOrNull(klass)
        override fun <T : Any> forceResolve(klass: kotlin.reflect.KClass<T>): T = delegate.forceResolve(klass)
        override fun start() = delegate.start()
        override val isStarted: Boolean get() = delegate.isStarted
        override fun invalidateInstances() = delegate.invalidateInstances()
        override fun stop() = delegate.stop()
        override fun isRegistered(klass: kotlin.reflect.KClass<*>): Boolean = delegate.isRegistered(klass)
        override val registeredNames: List<String> get() = delegate.registeredNames
    }

    private fun cachedSession() = CachedSession(
        userID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        email = "ada@example.com",
        displayName = "Ada",
        lastSignedInAt = Clock.System.now(),
        serverVersion = 0,
    )

}