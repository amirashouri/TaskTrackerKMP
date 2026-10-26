package com.tasktracker.compositionroot

import com.tasktracker.core.randomSprintID
import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.RoomOutboxQueue
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.network.SessionInvalidationReason
import com.tasktracker.sync.InMemorySyncTransport
import com.tasktracker.sync.RemoteChange
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncEngine
import com.tasktracker.sync.SyncScope
import com.tasktracker.sync.SyncTransport
import com.tasktracker.sync.SyncTrigger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope

/**
 * The graph assertions that need a real platform host, as scenarios with no test framework in
 * them.
 *
 * ## Why these five are not in `commonTest` with the others
 *
 * They all resolve [TaskTrackerDatabase] or [NetworkMonitor], and on Android both need a
 * `Context` — Room's `inMemoryDatabaseBuilder` takes one, and `NetworkMonitor.forHost` needs one
 * to reach `ConnectivityManager`. A `Context` is reachable only from a Robolectric sandbox, and
 * a sandbox is created by `@RunWith(RobolectricTestRunner::class)` on the *class*, which cannot
 * be written in a common source set. So the assertions live here and each host contributes the
 * entry points, exactly as `:shared-ui`'s `ShellScenarios` does.
 *
 * The alternative was to weaken these to `isRegistered` checks that resolve nothing, which would
 * have stopped testing the graph at precisely the moment it started doing something. The other
 * alternative — leaving them in `commonTest` and accepting five red tests on the Android host —
 * is what this file replaces: `:composition-root` now passes on all three hosts.
 *
 * ## Each value is `suspend TestScope.() -> Unit`
 *
 * A `TestScope` receiver rather than a plain suspend function because one of them needs
 * `backgroundScope` to hand to a sync coordinator, and the receiver is the only way to reach it
 * from inside a scenario. The host entry points all call `runTest { … }`, so the receiver is
 * always satisfied, and keeping every scenario the same shape means a new one cannot pick the
 * wrong signature by accident.
 */
object HostedGraphScenarios {

    /**
     * The session collaborators resolve, and the coordinator is one instance for the process.
     *
     * App-scoped is load-bearing for the coordinator as much as for the auth port: a sign-out
     * drops the session scope, and a session-scoped coordinator would be invalidated underneath
     * the shell holding it.
     */
    val theSessionCollaboratorsResolveAndTheCoordinatorIsAppScoped: suspend TestScope.() -> Unit = {
        val resolver = AppGraph.start(testBindings())
        try {
            assertTrue(resolver.isRegistered(SessionCoordinator::class))
            assertTrue(resolver.isRegistered(SessionTeardown::class))
            assertSame(
                resolver.resolve(SessionCoordinator::class),
                resolver.resolve(SessionCoordinator::class),
            )
            assertSame(
                resolver.resolve(SessionTeardown::class),
                resolver.resolve(SessionTeardown::class),
            )
        } finally {
            resolver.stop()
        }
    }

    /**
     * The three looping services are started by [AppGraph.start], not by their factories.
     *
     * Asserted through the monitor because it is the only one of the three with observable
     * state: [NetworkMonitor.start] is idempotent and a second `start` would attach a second
     * watcher. What is being pinned is that starting the graph starts the loops, so a caller
     * that only ever calls `start` — which is every platform entry point — gets running
     * services. [AppGraph.stop] resolves all three, so a service that was never registered
     * throws rather than passing.
     */
    val startingTheGraphStartsTheLoopingServices: suspend TestScope.() -> Unit = {
        val resolver = AppGraph.start(testBindings(startServices = true))
        try {
            AppGraph.stop(resolver)
        } finally {
            resolver.stop()
        }
    }

    /**
     * The settings port resolves, and is one instance.
     *
     * Resolved rather than registration-checked because this is the one registration whose
     * factory has real work in it: it reads the platform version, which on a host is blank.
     * That is the point of the assertion — a factory that throws on a host with no bundle
     * would turn the About row into a launch failure.
     */
    val theSettingsPortResolvesAndIsOneInstance: suspend TestScope.() -> Unit = {
        val resolver = AppGraph.start(
            testBindings(
                // These bindings have no API root, which is the offline demo. Reaching this
                // factory would mean the settings registration built an authenticated client
                // for a server that was never configured — the fake would hide it, so the
                // factory throws and the scenario fails.
                httpClient = { _, _ ->
                    error("the settings gateway must not build a client without an API root")
                },
            ),
        )
        try {
            val settings = resolver.resolve(SettingsPort::class)
            assertNotNull(settings)
            assertSame(settings, resolver.resolve(SettingsPort::class))

            // With no gateway the commit is local: no client, no request, no failure for a build
            // that never had a backend.
            assertEquals(UserDetails(), settings.commitUserDetails())
        } finally {
            resolver.stop()
        }
    }

    /**
     * Sync resolves as one graph rather than as loose parts.
     *
     * The engine, the coordinator and the monitor are checked by registration as well as by
     * `resolve`, because resolving a coordinator reads the platform's connectivity and building
     * a transport is real work a host has no business doing to assert a wiring fact. What
     * matters here is that the edge exists and that the coordinator is the one app-scoped
     * instance, since two of them would each start their own cycle.
     */
    val theSyncChainIsRegisteredAndSharesOneCoordinatorAndOneMonitor: suspend TestScope.() -> Unit = {
        val resolver = AppGraph.start(testBindings())
        try {
            assertTrue(resolver.isRegistered(SyncCoordinator::class))
            assertTrue(resolver.isRegistered(NetworkMonitor::class))
            assertSame(
                resolver.resolve(SyncCoordinator::class),
                resolver.resolve(SyncCoordinator::class),
            )
            assertSame(
                resolver.resolve(NetworkMonitor::class),
                resolver.resolve(NetworkMonitor::class),
            )
        } finally {
            resolver.stop()
        }
    }

    /**
     * A teardown leaves the outbox alone.
     *
     * The load-bearing decision, and the one that most needs a *real* store behind it: every
     * queued row carries the id of the account that queued it, and the engine claims only the
     * signed-in account's rows — so a different user cannot push this account's work, and the
     * same user signing back in finds their unsynced edits exactly where they left them.
     * Clearing here would discard them.
     *
     * A recording outbox double would make this true by construction; a real `RoomOutboxQueue`
     * over a real database is what makes it an assertion. That is also why it is one of the five
     * that needs a host.
     *
     * ## And why it is guarded separately from the other four
     *
     * This is the only one of the five that opens a store, and the Android host cannot do that
     * for a reason no fix in this module can address: `sqlite-bundled`'s native library is not on
     * its `java.library.path`. [thisHostOpensRoomStores] is asked first, so the assertion runs on
     * the JVM and iOS — where it is a real assertion — and is skipped rather than failed on the
     * Android host. The other four need only a `Context`, which the Robolectric runner now
     * provides, so they run everywhere.
     */
    val theOutboxIsLeftAlone: suspend TestScope.() -> Unit = {
        // An `if` rather than an early `return`: this is a lambda, and a bare `return` inside one
        // is prohibited because there is no function to return from.
        if (!thisHostOpensRoomStores()) {
            println("skipped: this host cannot open a Room store to check the outbox against")
        } else {
            val resolver = AppGraph.start(testBindings())
            val database = TaskTrackerDatabase.open(
                DatabaseConfiguration(name = "", inMemory = true, host = testPlatformHost()),
            )
            try {
                val outbox = RoomOutboxQueue(database)
                outbox.enqueue(sampleEntry())
                val stores = InMemoryStores()

                SessionTeardown(
                    resolver = resolver,
                    metadata = stores.metadata,
                    sessionCache = stores.session,
                    syncCoordinator = silentSyncCoordinator(scope = backgroundScope),
                    defaults = stores.defaults,
                    eraser = stores.eraser,
                ).run(SessionInvalidationReason.UserLogout)

                assertEquals(1, outbox.count(status = null))
            } finally {
                resolver.stop()
            }
        }
    }

    /**
     * The sample board is written only while no backend is bound.
     *
     * Both halves are asserted, because either alone can pass with the wiring still wrong: a
     * test that only checked the bound graph would pass if the gate were a blanket "never seed",
     * which would leave every offline demo empty, and one that only checked the offline graph
     * would pass if the gate were never wired at all.
     *
     * Guarded like [theOutboxIsLeftAlone] rather than merely hosted, and for the same reason:
     * resolving the port opens a Room store, and the Android host has no `sqliteJni` on its
     * `java.library.path`. The scenario asks [thisHostOpensRoomStores] and registers as a pass
     * where it cannot run, rather than failing for a reason that is not the graph's.
     */
    val theSampleSeedOnlyRunsWithoutABackend: suspend TestScope.() -> Unit = {
        if (!thisHostOpensRoomStores()) {
            println("skipped: this host cannot open a Room store to check whether the seed is offline-only")
        } else {
            val offline = AppGraph.start(testBindings())
            try {
                assertTrue(
                    offline.resolve(ProjectRepositoryPort::class).seedSampleDataIfNeeded(),
                    "an offline graph must still seed its demo board, or every entry point that " +
                        "names no API root launches empty",
                )
            } finally {
                offline.stop()
            }

            val online = AppGraph.start(testBindings(apiBaseUrl = "http://127.0.0.1:8080"))
            try {
                assertFalse(
                    online.resolve(ProjectRepositoryPort::class).seedSampleDataIfNeeded(),
                    "a graph with a backend bound must not seed: the repository queues seeded " +
                        "rows through the outbox, so a true here is the demo board on its way to " +
                        "http://127.0.0.1:8080",
                )
            } finally {
                online.stop()
            }
        }
    }

    /**
     * A sprint another device pushed lands in this device's store.
     *
     * The reported defect, end to end: the server now serves sprint rows on the `workItems`
     * stream, and this is the half that applies them. The engine looks an incoming change's
     * entity up in its sink map and throws `NoSink` when it is missing — and `SyncEngine.pull`
     * treats that as fatal for *the whole scope*, leaving the cursor where it was. So a map that
     * omitted `Sprint` did not merely drop sprints: the `workItems` feed stopped on the first
     * one, and every later card was unreadable too.
     *
     * A real store rather than a double, because the assertion is that a row written by a pull
     * is *readable through the repository* — and it is guarded by [thisHostOpensRoomStores] for
     * the reason [theOutboxIsLeftAlone] is: the Android host has no `sqliteJni` on its library
     * path, so it records a skip instead of a failure that is not the graph's.
     */
    val aPulledSprintLandsInTheStore: suspend TestScope.() -> Unit = {
        if (!thisHostOpensRoomStores()) {
            println("skipped: this host cannot open a Room store to pull a sprint into")
        } else {
            val resolver = AppGraph.start(testBindings())
            try {
                val repository = resolver.resolve(ProjectRepositoryPort::class)
                val now = Clock.System.now()
                // The project exists locally and the sprint does not: a sprint this device
                // already held would make the assertion pass with the sink still missing.
                val project = repository.createProject(
                    name = "Synced",
                    keyPrefix = "SYN",
                    details = "",
                    now = now,
                )
                val sprintID = randomSprintID()

                // The document a second device's push would have logged. Written as the wire
                // literal rather than built from a `Sprint`, because this module has no
                // serialization plugin: the shape has to match `Sprint`'s own property names
                // (`projectID`, not the server's `projectId`), and a key the client does not
                // declare is a hard decoding failure under `JSONCoding`'s strict reader.
                val transport = assertIs<InMemorySyncTransport>(resolver.resolve(SyncTransport::class))
                transport.seed(
                    scope = SyncScope.WorkItems,
                    changes = listOf(
                        RemoteChange(
                            entity = OutboxEntity.Sprint,
                            entityID = sprintID.stringValue,
                            operation = OutboxOperation.Upsert,
                            payload = sprintPayload(
                                id = sprintID.stringValue,
                                projectID = project.id.stringValue,
                                now = now,
                            ),
                            version = null,
                        ),
                    ),
                )

                resolver.resolve(SyncEngine::class).run(SyncTrigger.Manual)

                assertNotNull(
                    repository.sprint(sprintID),
                    "a sprint delivered on the workItems stream was not applied. Without a sink " +
                        "for its entity the pull stops on this change and never advances the " +
                        "scope's cursor, so cards after it stop arriving as well",
                )
            } finally {
                resolver.stop()
            }
        }
    }

    /**
     * A sprint as `RoomProjectRepository.enqueueSprint` serializes it.
     *
     * Only the keys without a default are named: `goal`, `ownerID`, `finishedAt` and the three
     * flags are omitted so the payload also proves the client's defaults still decode a document
     * from an older peer.
     */
    private fun sprintPayload(id: String, projectID: String, now: Instant): ByteArray =
        (
            "{" +
                "\"id\":\"$id\"," +
                "\"projectID\":\"$projectID\"," +
                "\"name\":\"Sprint from another device\"," +
                "\"goalStoryPoints\":20," +
                "\"startDate\":\"$now\"," +
                "\"endDate\":\"$now\"," +
                "\"createdAt\":\"$now\"," +
                "\"updatedAt\":\"$now\"" +
                "}"
            ).encodeToByteArray()

    /** A queued row, so "left alone" has something to leave alone. */
    private fun sampleEntry() = NewOutboxEntry(
        id = "change-1",
        entity = OutboxEntity.Project,
        entityID = "project-1",
        operation = OutboxOperation.Upsert,
        payload = "{}".encodeToByteArray(),
        createdAt = Clock.System.now(),
    )
}
