package com.tasktracker.compositionroot

import com.tasktracker.core.DateProvider
import com.tasktracker.core.SystemDateProvider
import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.RoomOutboxQueue
import com.tasktracker.database.RoomSessionCache
import com.tasktracker.database.RoomSyncMetadataStore
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.di.KoinResolver
import com.tasktracker.di.ResolverBuilder
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdata.AuthRepository
import com.tasktracker.featureauthdata.MockAuthRemote
import com.tasktracker.featureauthdata.SessionStateBus
import com.tasktracker.featureauthdata.TokenVault
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureprojectsdata.LocalDataSyncSink
import com.tasktracker.featureprojectsdata.ProjectChangeBus
import com.tasktracker.featureprojectsdata.RoomProjectRepository
import com.tasktracker.featureprojectsdomain.FilePicker
import com.tasktracker.featureprojectsdomain.createFilePicker
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featuresettingsdata.LiveSettingsService
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featureteamsdata.RoomTeamRepository
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.keychain.KeychainAccess
import com.tasktracker.keychain.KeychainStore
import com.tasktracker.keychain.systemKeychain
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.routing.Router
import com.tasktracker.sync.InMemorySyncTransport
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncEngine
import com.tasktracker.sync.SyncTransport
import com.tasktracker.userdefaults.PreferencesBackend
import com.tasktracker.userdefaults.UserDefaultsStore
import com.tasktracker.userdefaults.platformPreferences

/**
 * The one module allowed to see every concrete type, and therefore the only place the
 * object graph is described. This is the Kotlin counterpart of the original's
 * `App/CompositionRoot.swift`.
 *
 * Registrations are added here, in one place, in dependency order. Nothing else may
 * construct a concrete dependency: a presentation module asks its own domain module for a
 * port, and the binding from a port to an implementation exists only inside this file.
 * That is what keeps the SwiftPM target boundaries enforceable under Gradle — a
 * presentation module does not merely avoid importing a data module, it has no edge to it
 * to import from.
 */
object AppGraph {

    /**
     * Builds the container from every registration and then starts it.
     *
     * Registration and start are separate calls on purpose. A container that resolved
     * during registration could not detect a dependency cycle, because the first factory
     * call would land before the graph was fully described. Splitting them means every
     * resolve is a real edge in a finished description.
     *
     * ## [host] is the platform host object, and Android cannot omit it
     *
     * `systemKeychain()` and `platformPreferences()` both need a `Context` on Android to
     * reach the Keystore and `SharedPreferences`, and `DatabaseConfiguration.applicationDefault`
     * needs one to find app-private storage. Typed as `Any?` because this is a common
     * function and cannot name `Context`; each platform's `actual` casts and throws a
     * message naming what it wanted. Pass the **application** context — the graph outlives
     * any activity, and the backends take `.applicationContext` internally anyway.
     *
     * Pass `null` on JVM and iOS, where the backends need nothing. Android passing `null`
     * is not a default case: it throws at start-up, deliberately, because the alternative is
     * a keychain that fails later on the first sign-in with a message about the user rather
     * than about the wiring.
     */
    fun start(host: Any? = null): Resolver = start(PlatformBindings(host))

    /**
     * Stops the graph's looping services and releases the container.
     *
     * ## Exists because [start] now starts things
     *
     * A graph whose three services are running is a resource, and a resource needs an owner to
     * shut it down. The original did this in `deinit`; Kotlin has no equivalent for an object
     * the platform layer merely holds, so the call moves to whoever holds the [Resolver] —
     * which is the same place the original's composition root was.
     *
     * Every service is stopped before the container is released, because [SessionCoordinator]
     * resolves [Router] and [SyncCoordinator] resolves the monitor, and neither can be
     * resolved from a stopped container.
     *
     * Idempotent in effect rather than in contract: a second call resolves nothing, because
     * the services are already stopped and the container already released.
     */
    fun stop(resolver: Resolver) {
        resolver.getOrNull(SessionCoordinator::class)?.stop()
        resolver.getOrNull(SyncCoordinator::class)?.stop()
        resolver.getOrNull(NetworkMonitor::class)?.stop()
        resolver.stop()
    }

    /**
     * The same graph with the two platform backends supplied.
     *
     * ## Why this seam exists, and why it is this narrow
     *
     * Until the auth graph was registered, [start] built nothing, so it was testable
     * everywhere. Registering it made every composition-root test reach `systemKeychain()`
     * and `platformPreferences()`, and on the Android host both fail against the stub
     * `android.jar` — so the tests broke for a reason that has nothing to do with the graph
     * they are checking. `:packages:keychain` has no `androidHostTest` source set at all,
     * which is the same limitation from the other side: nothing has ever constructed a real
     * keychain off a device.
     *
     * The alternative was to weaken the tests to inspect registration names without
     * resolving anything, which would have stopped testing the graph at exactly the moment
     * the graph started doing something. A seam is the honest trade: two injected values,
     * both `internal`, both defaulting to the real platform backend so no production call
     * site can pass anything else.
     */
    internal fun start(platforms: PlatformBindings): Resolver =
        KoinResolver.build {
            registerPlatform(platforms)
            registerPersistence(platforms)
            registerProjects()
            registerTeams()
            registerAuth(platforms)
            registerSync()
            registerSession()
            registerSettings(platforms)
        }.also {
            it.start()
            // Starting is a separate step from building, and it is conditional for the same
            // reason the rest of this file has a seam: a test that wants to describe the graph
            // must not inherit three loops it has to remember to stop.
            if (platforms.startServices) startLoops(it)
        }

    /**
     * Routing, connectivity, and nothing that has collaborators of its own.
     *
     * The first module because [Router] is asked for by the shell at launch and
     * [NetworkMonitor] by both the sync coordinator and the settings service, and neither
     * resolves anything.
     */
    private fun ResolverBuilder.registerPlatform(platforms: PlatformBindings) {
        registerRouter()

        // `forHost` rather than the primary constructor: on Android the watcher needs a
        // `Context` to reach `ConnectivityManager`, and the no-argument constructor throws
        // there. Passing the host everywhere means one registration works on all three
        // platforms rather than three registrations that differ only in an ignored argument.
        //
        // Not started here — see [startLoops].
        app<NetworkMonitor> { NetworkMonitor.forHost(platforms.host) }

        // The platform's file picker. Where it is registered matters: the graph outlives
        // every activity, and an activity-result registration belongs to an Activity, so the
        // picker is built once per graph from the platform host, and it flows to the
        // presentation layer through [com.tasktracker.featureprojectspresentation.project
        // .ProjectEnvironment.filePicker]. Composing the picker in the presentation layer
        // would either force a `LocalContext` (which Compose Multiplatform 1.12.1 does not
        // provide outside androidMain) or push the host object through every caller. One
        // resolved instance, flowing through the environment, is the only shape where a
        // test can substitute a fake without touching the screen tree.
        app<FilePicker> { createFilePicker(platforms.host) }
    }

    /**
     * The store, and the collaborators more than one feature shares.
     *
     * ## Each store resolves the database rather than taking it
     *
     * They are separate classes over one database on purpose. Each owns its own
     * `ModelContext`, which is the only way a `ModelContext` can be used safely, so they
     * cannot be one object; and they all resolve the same app-scoped database, so they
     * cannot be three databases either.
     *
     * [ProjectChangeBus] is registered here rather than with projects because two modules
     * need it — the repository writes to it and the sync sink broadcasts on it. One
     * registration is what makes that sharing structural instead of a convention two
     * factories have to remember: two buses would mean a pulled change landing somewhere no
     * open screen was listening, and the board stayed stale until the user navigated.
     */
    private fun ResolverBuilder.registerPersistence(platforms: PlatformBindings) {
        // App-scoped and resolved lazily: opening the store touches the disk — or, on
        // Android, throws for want of a `Context` — so an eager `open` here would make
        // every test of this graph need a real store before it could assert anything about
        // the graph.
        app<TaskTrackerDatabase> { TaskTrackerDatabase.open(platforms.database) }

        app<ProjectChangeBus> { ProjectChangeBus() }

        app<SessionCache> { resolver -> RoomSessionCache(resolver.resolve(TaskTrackerDatabase::class)) }
        app<OutboxQueue> { resolver -> RoomOutboxQueue(resolver.resolve(TaskTrackerDatabase::class)) }
        app<SyncMetadataStore> { resolver ->
            RoomSyncMetadataStore(resolver.resolve(TaskTrackerDatabase::class))
        }
    }

    /**
     * Everything needed to sign in.
     *
     * Read the order top to bottom: each registration resolves types registered above it,
     * so the list *is* the dependency order and there is no separate ordering to maintain.
     * The DI container detects a cycle, but a file that lists a dependency before its
     * dependency is merely confusing rather than wrong, and the confusion costs a reader
     * more than the cycle ever would.
     */
    private fun ResolverBuilder.registerAuth(platforms: PlatformBindings) {
        // `app`, not `transient`: a fresh clock per resolution would give the vault and the
        // remote different notions of "now" within one sign-in, which is exactly the bug a
        // token-expiry margin is meant to prevent.
        app<DateProvider> { SystemDateProvider() }

        // The broadcaster every session mutation publishes through. App-scoped because the
        // bus outlives any single session — a sign-out has to publish `SignedOut` on the
        // same instance a sign-in published `SignedIn` on, or a late subscriber would miss
        // the transition it exists to observe.
        app<SessionStateBus> { SessionStateBus() }

        // ## The remote is a mock, and that is a recorded decision rather than an oversight
        //
        // There is no server. The real `HttpAuthRemote` needs an `HttpClient` pointed at a
        // base URL, and this project's base URL is a `.invalid` host by design, so a real
        // remote could only ever fail — the app would be permanently stuck behind the auth
        // gate with a network error and no way forward. `MockAuthRemote` mints genuine
        // signed-format JWTs in-process, so the vault's expiry, forced-refresh and restore
        // paths all run for real while nothing has to be listening on a socket.
        //
        // What this is *not* is a build-mode switch that already exists. `MockAuthRemote`'s
        // own KDoc puts the switch in this file, and it lands here: the release rule is
        // "a release build must never be handed this instead of `HttpAuthRemote`", and the
        // enforcement point is this `if`, not the class's source set. Until there is a base
        // URL to configure there is nothing to switch between, so a switch would be a
        // constant with a misleading name. See `docs/PORT-LOG.md`, Phase 13.
        app<AuthRemotePort> { resolver ->
            MockAuthRemote(dateProvider = resolver.resolve(DateProvider::class))
        }

        // The platform keychain and preferences backends, resolved rather than constructed
        // at a call site so the graph stays the only place a concrete backend is named.
        app<KeychainAccess> { platforms.keychain }
        app<PreferencesBackend> { platforms.preferences }

        app<KeychainStore> { resolver -> KeychainStore(resolver.resolve(KeychainAccess::class)) }

        // App-scoped, and the `app` scope's own KDoc names the token vault as the case it
        // exists for: it owns the two mutexes that collapse concurrent refreshes, and a
        // session-scoped vault would be rebuilt mid-refresh on sign-out.
        app<TokenVault> { resolver ->
            TokenVault(
                keychain = resolver.resolve(KeychainStore::class),
                remote = resolver.resolve(AuthRemotePort::class),
                bus = resolver.resolve(SessionStateBus::class),
                dateProvider = resolver.resolve(DateProvider::class),
            )
        }

        app<UserDefaultsStore> { resolver ->
            UserDefaultsStore(backend = resolver.resolve(PreferencesBackend::class))
        }

        /**
         * The port the sign-in screen asks for.
         *
         * App-scoped rather than session-scoped, which is the decision here worth stating.
         * The session scope is dropped by `resetSessionScope()` on sign-out, and the shell
         * holds this reference from launch — it calls `restore()` before any session exists.
         * A session-scoped binding would be invalidated underneath it, leaving the shell
         * signing in through a repository the container no longer considers registered.
         */
        app<AuthSessionPort> { resolver ->
            AuthRepository(
                vault = resolver.resolve(TokenVault::class),
                remote = resolver.resolve(AuthRemotePort::class),
                defaults = resolver.resolve(UserDefaultsStore::class),
                bus = resolver.resolve(SessionStateBus::class),
            )
        }
    }

    /**
     * The projects store, and the port the projects feature asks for.
     *
     * ## The database is app-scoped and resolved lazily, and both matter
     *
     * App-scoped because the repository holds the change bus, and two repositories would
     * mean two buses: a write through one would publish a change the other never hears, and
     * a board observing the other would sit stale while the store in front of it changed.
     *
     * Lazily *because* it is app-scoped, and that is the subtle part. An eager `open` at
     * registration would touch the disk — or, on Android, throw for want of a `Context` —
     * during graph construction, so every test of the graph would need a real store before
     * it could assert anything about the graph. A factory defers all of that to the first
     * `resolve`, which is the only point at which somebody has actually asked for projects.
     *
     * `sessionCache` is left out, deliberately and for a stated reason: it is what credits a
     * status change to an author, and absent is a legitimate state rather than a stub — every
     * change is then recorded with an unknown author. Wiring it is Phase 13's session-coordinator
     * work, and a graph that invented a user id to fill it in would write false history.
     */
    private fun ResolverBuilder.registerProjects() {
        app<ProjectRepositoryPort> { resolver ->
            RoomProjectRepository(
                database = resolver.resolve(TaskTrackerDatabase::class),
                changeBus = resolver.resolve(ProjectChangeBus::class),
                // Who to credit a status change to. Wired now that persistence registers a
                // `SessionCache`; while it was absent every change was recorded with an
                // unknown author, which is honest but wrong now that the store exists.
                sessionCache = resolver.resolve(SessionCache::class),
                // The same app-scoped clock the vault uses, rather than a second one. A
                // repository with its own `SystemDateProvider` would be correct in isolation
                // and inconsistent with the token times, which is the class of drift a
                // single app-scoped registration exists to prevent.
                dateProvider = resolver.resolve(DateProvider::class),
            )
        }

        // The one sink that knows how to write a project or a work item into the local
        // store. Both entities route to the same sink deliberately: it applies each change to
        // its own table and shares one rule — never touch the outbox, because a pull that
        // queued a change would turn into a push that became a pull.
        app<LocalDataSyncSink> { resolver ->
            LocalDataSyncSink(
                database = resolver.resolve(TaskTrackerDatabase::class),
                changeBus = resolver.resolve(ProjectChangeBus::class),
            )
        }
    }

    /**
     * The user directory: [TeamPort] resolves a UserID into a User profile.
     *
     * Backed by the session cache, which SessionCoordinator seeds on sign-in, so the directory
     * can always answer for the signed-in account. Registered after persistence and projects —
     * both depend only on [SessionCache], which persistence owns, and projects reads it too.
     */
    private fun ResolverBuilder.registerTeams() {
        app<TeamPort> { resolver ->
            RoomTeamRepository(
                sessionCache = resolver.resolve(SessionCache::class),
            )
        }
    }

    /**
     * The transport, the engine, and the gate that decides when a cycle runs.
     *
     * ## [SyncTransport] is in-memory, and that is the same decision as the mock auth remote
     *
     * There is no backend: the base URL is a `.invalid` host by design, so a real transport
     * could only ever fail and the app would sit permanently on a sync error. The original
     * needed a server that does not exist yet too, and Phase 14 has the same problem.
     *
     * [InMemorySyncTransport] is a real [com.tasktracker.sync.SyncTransport] — it pushes,
     * appends to a change log, and serves it back — not a stub. So a cycle runs end to end
     * and a pulled change really does wake a board, which is what makes the local edits
     * observable at all before a server exists.
     */
    private fun ResolverBuilder.registerSync() {
        app<SyncTransport> { InMemorySyncTransport() }

        app<SyncEngine> { resolver ->
            val sink = resolver.resolve(LocalDataSyncSink::class)
            val sessionCache = resolver.resolve(SessionCache::class)
            SyncEngine(
                outbox = resolver.resolve(OutboxQueue::class),
                metadata = resolver.resolve(SyncMetadataStore::class),
                transport = resolver.resolve(SyncTransport::class),
                sinks = mapOf(
                    OutboxEntity.Project to sink,
                    OutboxEntity.WorkItem to sink,
                ),
                // Claims only the signed-in account's queued rows, read at cycle time from the
                // same cache the repository stamps rows with. The two must agree, or a stamped
                // row would never be claimed again — which is why this is the session cache
                // and not the session port.
                pushAs = { sessionCache.current()?.userID },
            )
        }

        // Decides *when* to sync, which is a different problem from knowing how: a timer, an
        // app lifecycle and the connectivity monitor can all fire at once, and without one
        // gate here each would start a cycle.
        app<SyncCoordinator> { resolver ->
            SyncCoordinator(
                engine = resolver.resolve(SyncEngine::class),
                monitor = resolver.resolve(NetworkMonitor::class),
                dateProvider = resolver.resolve(DateProvider::class),
            )
        }
    }

    /**
     * What ends a session, and who notices.
     *
     * ## The teardown holds the container rather than a narrower reset
     *
     * It calls [Resolver.resetSessionScope], and that is the one method here that is not a
     * store operation — it is the container's own bookkeeping. A narrower collaborator
     * ("something that drops the session scope") would be a wrapper around one method that
     * the container already has, and would need its own seam for testing.
     *
     * ## App-scoped, and the reason is [SessionTeardown]'s own note
     *
     * A sign-out drops the session scope, and a session-scoped coordinator would be
     * invalidated underneath the shell that holds it — the same reason the auth port is
     * app-scoped. Rebuilding it per session would also mean the shell's `remember` held an
     * object the container had forgotten.
     */
    private fun ResolverBuilder.registerSession() {
        app<SessionTeardown> { resolver ->
            SessionTeardown(
                resolver = resolver,
                metadata = resolver.resolve(SyncMetadataStore::class),
                sessionCache = resolver.resolve(SessionCache::class),
                syncCoordinator = resolver.resolve(SyncCoordinator::class),
            )
        }

        app<SessionCoordinator> { resolver ->
            SessionCoordinator(
                session = resolver.resolve(AuthSessionPort::class),
                router = resolver.resolve(Router::class),
                teardown = resolver.resolve(SessionTeardown::class),
                sessionCache = resolver.resolve(SessionCache::class),
                dateProvider = resolver.resolve(DateProvider::class),
            )
        }
    }

    /**
     * The settings port, and the version it reports.
     *
     * ## `endSession` is handed the graph's coordinator, not a way to build one
     *
     * A settings package gets to ask for a session to be ended, and the app keeps the
     * decision about what ending it means. Resolving inside the closure rather than in the
     * factory matters: resolving at registration would put the coordinator on the settings
     * service's construction path, so a graph where settings were resolved before auth would
     * build a coordinator over a session port that does not exist yet.
     *
     * ## [AppVersion] is a value, not a factory
     *
     * It cannot change while the app runs, so it is read once here and the About row reads
     * the same answer every time. Registered even though nothing resolves it eagerly —
     * `SettingsPort` does, inside its constructor.
     */
    private fun ResolverBuilder.registerSettings(platforms: PlatformBindings) {
        app<AppVersion> { platformAppVersion(platforms.host) }

        app<SettingsPort> { resolver ->
            LiveSettingsService(
                monitor = resolver.resolve(NetworkMonitor::class),
                coordinator = resolver.resolve(SyncCoordinator::class),
                version = resolver.resolve(AppVersion::class),
                endSession = { resolver.resolve(SessionCoordinator::class).signOut() },
                defaults = resolver.resolve(UserDefaultsStore::class),
            )
        }
    }

    /**
     * Starts the three things that own a loop.
     *
     * ## Explicitly not a side effect of a factory
     *
     * Resolution is a lookup that may happen many times, and a factory that started a loop
     * would start another one the moment its instance was invalidated — so "built" and
     * "running" are kept as two separate facts, which is most of the reason for having a
     * lazy graph at all. Every test that builds the graph therefore gets containers with
     * nothing running, and nothing to stop.
     *
     * ## The monitor first, in one call rather than two
     *
     * The original starts both inside a single task so the monitor is listening before the
     * first cycle reads its value: a coordinator started first sees [com.tasktracker.network.Connectivity.Unknown]
     * and skips itself. Here the monitor is `start`ed synchronously — it attaches and starts
     * a watcher — so the ordering is structural rather than a matter of task scheduling.
     *
     * ## The coordinator subscribes after both
     *
     * Its restore call is asynchronous, so a `SignedIn` published by it cannot arrive before
     * the listener is attached; [SessionCoordinator.start] attaches before it restores for
     * exactly that reason.
     */
    private fun startLoops(resolver: Resolver) {
        resolver.resolve(NetworkMonitor::class).start()
        resolver.resolve(SyncCoordinator::class).start()
        resolver.resolve(SessionCoordinator::class).start()
    }

    /**
     * Navigation state, as a resolution rather than something the shell constructs.
     *
     * The router holds the active project and the per-tab stacks, and it has to survive
     * recomposition, sign-out and the shell being torn down and rebuilt. A `remember`ed
     * `Router` would be a *new* router on every re-creation of the caller, silently
     * discarding whatever the user had navigated to; app-scoped here is what makes "the
     * navigation state" a single thing for the life of the process, exactly as the session
     * port is.
     */
    private fun ResolverBuilder.registerRouter() {
        app<Router> { Router() }
    }
}

/**
 * The three things that differ between a device and a test host.
 *
 * Every other registration is pure wiring — classes with no platform state — so this is the
 * entire surface a test has to substitute. `internal` because it is not part of the
 * composition root's contract: a *feature* that wanted a fake keychain would have to reach
 * for it, and the only caller that ever passes one is the test below.
 */
internal class PlatformBindings(
    val host: Any? = null,
    val keychain: KeychainAccess = systemKeychain(host),
    val preferences: PreferencesBackend = platformPreferences(host),
    /**
     * Where the store is, as a configuration rather than as a host.
     *
     * The one binding here that is a value instead of a substitute, and it needs the same
     * reasoning as the two above. `DatabaseConfiguration.applicationDefault(host)` derives
     * app-private storage from a `Context` on Android and throws without one, so a default
     * argument evaluated here would throw during *construction* — which is during every test
     * of this graph, including the ones that only want to know whether a port is registered.
     *
     * Defaulting to `applicationDefault(host)` rather than to the in-memory configuration is
     * the point. A default is what production gets, and production must never be able to
     * reach an in-memory store by omission. The only callers that pass anything are tests,
     * and they pass [DatabaseConfiguration.inMemory], which carries no usable path at all.
     *
     * Note this is a *configuration*, not an opened store, and the distinction is what makes
     * the graph buildable on a target that cannot open one. See [testPlatformHost] for why the
     * Android test host still cannot.
     */
    val database: DatabaseConfiguration = DatabaseConfiguration.applicationDefault(host),
    /**
     * Whether to start the three looping services after building the container.
     *
     * Production is `true` and there is no code path that says otherwise — the two platform
     * entry points call [AppGraph.start], which starts them. It is here for tests that
     * exercise a *registration* and would otherwise have to race the session coordinator's
     * own `restore` against the restore they are trying to observe: two concurrent restores of
     * the same vault is a race that has nothing to do with what those tests are about.
     *
     * Turning loops off is not turning off wiring — every registration, scope and factory is
     * still the production one, so a graph built this way is describable and resolvable in
     * exactly the ways a running one is.
     */
    val startServices: Boolean = true,
)
