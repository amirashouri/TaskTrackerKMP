package com.tasktracker.compositionroot

import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.di.DiException
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdata.TokenVault
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureprojectsdata.ProjectChangeBus
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.routing.Router
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.userdefaults.InMemoryPreferences
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The seam the whole port depends on: one describable object graph, reachable from one
 * place, that no other module can bypass.
 */
class AppGraphTest {

    /**
     * The production graph, with in-memory stand-ins for the two platform backends.
     *
     * Everything else is real: the same registrations, the same scopes, the same factories,
     * and the same `AuthRepository` over the same `TokenVault`. Only the keychain and
     * preferences are substituted, because on the Android host those two cannot be
     * constructed at all. See [AppGraph.start] for the full reasoning.
     */
    private fun testGraph(): Resolver = AppGraph.start(testBindings(startServices = false))

    /** The production graph with its loops running, for the tests that are about the loops. */
    private fun runningGraph(): Resolver = AppGraph.start(testBindings(startServices = true))

    @Test
    fun `start yields a started container`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isStarted)
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `an unregistered dependency fails loudly rather than resolving to something`() {
        val resolver = testGraph()

        try {
            assertFailsWith<DiException.Unregistered> {
                resolver.resolve(Resolver::class)
            }
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the auth port resolves`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isRegistered(AuthSessionPort::class))
            assertNotNull(resolver.resolve(AuthSessionPort::class))
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the session port is one instance for the whole process`() {
        val resolver = testGraph()

        try {
            // App-scoped is a load-bearing decision, not a default: the shell holds this
            // reference from launch, across a sign-out. A per-session or per-resolution
            // binding would hand the shell an object the container no longer owns.
            assertSame(resolver.resolve(AuthSessionPort::class), resolver.resolve(AuthSessionPort::class))
            assertSame(resolver.resolve(TokenVault::class), resolver.resolve(TokenVault::class))
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the vault and the session port share one remote`() {
        val resolver = testGraph()

        try {
            // Not observable from outside either object, so asserted structurally: a second
            // remote would mint tokens a vault never sees, and the mismatch would only
            // surface as a refresh failing much later.
            val remote = resolver.resolve(AuthRemotePort::class)
            assertSame(remote, resolver.resolve(AuthRemotePort::class))
        } finally {
            resolver.stop()
        }
    }

    /**
     * The graph registers what has been ported, and nothing else.
     *
     * The positive half is the point: `ProjectRepositoryPort`, `TaskTrackerDatabase`,
     * `ProjectChangeBus` and `Router` are all resolved by name or by class, and a shell
     * that asked for one of them before this phase would have been a crash on launch. It
     * used to be the other way round — this test asserted that *no* `Project` or `Database`
     * registration existed, which is only true while the feature is unwired, and so is a
     * test that has to be inverted the moment the feature lands.
     *
     * `isRegistered` rather than `resolve` for the database, because resolving it opens a
     * Room database and there is no Android `Context` here. A registration check is the
     * honest question on this host; opening the database is asserted on the device.
     *
     * The negative half survives: an HTTP client is still not registered, and a
     * registration that appears without a phase adding it is the failure mode a growing
     * composition root actually has, invisible to the resolve-everything test above.
     */
    @Test
    fun `the graph registers the ported features and still nothing more`() {
        val resolver = testGraph()

        try {
            assertTrue(
                resolver.isRegistered(ProjectRepositoryPort::class),
                "expected the projects repository to be registered",
            )
            assertTrue(
                resolver.isRegistered(TaskTrackerDatabase::class),
                "expected the database to be registered",
            )
            assertTrue(
                resolver.isRegistered(ProjectChangeBus::class),
                "expected the projects change bus to be registered",
            )
            assertTrue(
                resolver.isRegistered(Router::class),
                "expected the router to be registered for the shell",
            )
            assertTrue(
                resolver.isRegistered(SettingsPort::class),
                "expected the settings port to be registered",
            )
            assertFalse(
                resolver.registeredNames.any { it.contains("HTTP") || it.contains("Ktor") },
                "expected no HTTP client in the graph, found ${resolver.registeredNames}",
            )
        } finally {
            resolver.stop()
        }
    }

    /**
     * The router is one instance for the whole process.
     *
     * App-scoped rather than per-resolution, and load-bearing: the shell holds it from
     * launch, across a sign-out, and the sign-out path has to be able to clear its stacks.
     * A fresh router per resolve would hand the shell an object whose state the container
     * does not own, and clearing the stacks on sign-out would clear the wrong one.
     */
    @Test
    fun `the router is one instance for the whole process`() {
        val resolver = testGraph()

        try {
            assertSame(resolver.resolve(Router::class), resolver.resolve(Router::class))
        } finally {
            resolver.stop()
        }
    }

    /**
     * The persistence collaborators are registered, and share one store.
     *
     * They are separate classes over one database on purpose — each owns its own
     * `ModelContext`, the only way one can be used safely — so "registered and separate" is
     * the assertion, and "same instance" is not available for any of them individually. The
     * sharing that *is* structural is the change bus, which two modules need.
     */
    @Test
    fun `the persistence collaborators are registered and the bus is shared`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isRegistered(OutboxQueue::class))
            assertTrue(resolver.isRegistered(SyncMetadataStore::class))
            assertTrue(resolver.isRegistered(SessionCache::class))
            assertSame(resolver.resolve(ProjectChangeBus::class), resolver.resolve(ProjectChangeBus::class))
        } finally {
            resolver.stop()
        }
    }

    /**
     * A graph built with services off resolves everything but runs nothing.
     *
     * The counterpart to the test above, and the reason the seam is safe: turning loops off
     * removes running work, not registrations. Every resolve in [the graph registers the ported
     * features and still nothing more] still holds with services off, which is what makes that
     * seam usable by the rest of the suite.
     */
    @Test
    fun `services off is a wiring switch and not a graph`() {
        val resolver = testGraph()

        try {
            assertTrue(resolver.isStarted)
            assertTrue(resolver.isRegistered(SyncCoordinator::class))
            assertTrue(resolver.isRegistered(NetworkMonitor::class))
            assertTrue(resolver.isRegistered(SessionCoordinator::class))
        } finally {
            resolver.stop()
        }
    }

}

/*
 * Five assertions used to live here and now live in [HostedGraphScenarios], because they resolve
 * `TaskTrackerDatabase` or `NetworkMonitor` and both need a `Context` on Android: Room's
 * `inMemoryDatabaseBuilder` takes one, and `NetworkMonitor.forHost` needs one to reach
 * `ConnectivityManager`. A `Context` comes only from a Robolectric sandbox, which only a class
 * runner annotation creates, and a runner annotation cannot be written in `commonTest`. So the
 * bodies moved to a scenario object and each host has an entry point that can carry the runner:
 * `HostedGraphScenariosTest` on JVM and iOS, and the Robolectric-annotated
 * `HostedGraphAndroidHostTest` on the Android host.
 *
 * They were left in place here until Phase 15 and failed on `testAndroidHostTest` with a
 * `FactoryFailed` for `TaskTrackerDatabase` and for `NetworkMonitor`, which reads as "the graph
 * is broken on Android" and is not. The same assertions pass on JVM and iOS, where neither
 * backend needs a host, so it was always a gap in the test target rather than in the wiring.
 */
