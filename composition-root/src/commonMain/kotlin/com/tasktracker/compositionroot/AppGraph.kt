package com.tasktracker.compositionroot

import com.tasktracker.core.DateProvider
import com.tasktracker.core.SystemDateProvider
import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.RoomOutboxQueue
import com.tasktracker.database.LocalStoreEraser
import com.tasktracker.database.RoomLocalStoreEraser
import com.tasktracker.database.RoomSessionCache
import com.tasktracker.database.RoomSyncMetadataStore
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.di.KoinResolver
import com.tasktracker.di.ResolverBuilder
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdata.AuthRepository
import com.tasktracker.featureauthdata.HttpAuthRemote
import com.tasktracker.featureauthdata.MockAuthRemote
import com.tasktracker.featureauthdata.SessionStateBus
import com.tasktracker.featureauthdata.TokenVault
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureprojectsdata.AdminProjectsRepository
import com.tasktracker.featureprojectsdata.HttpAdminProjectsRemote
import com.tasktracker.featureprojectsdata.LocalDataSyncSink
import com.tasktracker.featureprojectsdata.ProjectChangeBus
import com.tasktracker.featureprojectsdata.RoomProjectRepository
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.AttachmentBlobTransport
import com.tasktracker.featureprojectsdomain.AttachmentOpener
import com.tasktracker.featureprojectsdomain.BackfillUnqueuedAttachments
import com.tasktracker.featureprojectsdomain.FilePicker
import com.tasktracker.featureprojectsdomain.createAttachmentOpener
import com.tasktracker.featureprojectsdomain.createFilePicker
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.SweepPendingAttachments
import com.tasktracker.featureprojectsdomain.UploadAttachment
import com.tasktracker.featuresettingsdata.LiveSettingsService
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featureteamsdata.HttpMemberAdminRemote
import com.tasktracker.featureteamsdata.HttpTeamRemote
import com.tasktracker.featureteamsdata.MemberAdminRepository
import com.tasktracker.featureteamsdata.RoomTeamRepository
import com.tasktracker.featureteamsdata.ServerTeamRepository
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.keychain.KeychainAccess
import com.tasktracker.keychain.KeychainStore
import com.tasktracker.keychain.systemKeychain
import com.tasktracker.network.AuthInterceptor
import com.tasktracker.network.HttpClient
import com.tasktracker.network.KtorHttpClient
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.network.RequestInterceptor
import com.tasktracker.routing.Router
import com.tasktracker.sync.HttpSyncTransport
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
     *
     * ## [apiBaseUrl] is the one switch between a real backend and the offline demo
     *
     * A named URL binds the real `HttpAuthRemote` and `HttpSyncTransport`; `null`, the
     * default, binds the in-process `MockAuthRemote` / `InMemorySyncTransport` pair. The
     * default is the demo because a caller that names no backend has not got one, and a graph
     * that guessed at an address could only hang the auth gate on a socket nobody is listening
     * to. All three shells now name one: [platformApiBaseUrl] reads each platform's own
     * configuration surface — an environment variable, an `Info.plist` key, a manifest
     * meta-data — so Desktop, iOS and Android all reach the Go server.
     *
     * ## The `null` default is what the tests rely on, and no shell reaches it
     *
     * Every entry point passes a URL from [platformApiBaseUrl], so this default is no longer
     * the path a launched app takes. The callers that still depend on it are the tests:
     * `testBindings` defaults `apiBaseUrl` to `null` because that is the demo pair the rest of
     * the suite wants, and `HostedGraphScenarios.theSampleSeedOnlyRunsWithoutABackend` exercises
     * both sides of the switch. A default changed to a non-null value would bind those graphs
     * to a real backend with the assertions still green.
     *
     * `startServices`'s default is the harder half. The *public* start cannot gate it: exercising
     * the default starts the loops, and `startLoops` resolves the store, so an assertion that
     * went through it would create the developer's real database. A test *can* reach the default
     * through the internal `start(platforms)` with an in-memory store, but that leaves three
     * loops running over a store the test then has to stop, to prove a `true` that production is
     * the only caller of — a scheduler owned by a wiring test, bought for no assertion about
     * wiring. Production takes the default, and it is what all three entry points — both shells
     * and Desktop — get.
     *
     * ## [startServices] is the same switch [PlatformBindings] already carries
     *
     * It was added for the Desktop entry point's own wiring test, which has to build the graph
     * from the *production* entry (real base URL, real bindings) and look at which auth remote
     * it bound — and `DesktopGraph` is a `by lazy` holder behind a private name, so a test
     * cannot reach that line any other way. Starting the loops on the way there would resolve
     * the store and open the developer's real database, which is a side effect an assertion
     * about *which client was built* must not have.
     *
     * The graph's own tests are the callers that pass `false` most often, for the same reason:
     * `testBindings(startServices = false)` describes the graph without leaving three loops for
     * each test to remember to stop. Production is the caller that passes nothing, and `true`
     * is what it gets.
     */
    fun start(
        host: Any? = null,
        apiBaseUrl: String? = null,
        startServices: Boolean = true,
    ): Resolver = start(
        PlatformBindings(host = host, apiBaseUrl = apiBaseUrl, startServices = startServices),
    )

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
            registerProjects(platforms)
            registerTeams(platforms)
            registerAuth(platforms)
            registerSync(platforms)
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

        // The platform's file opener, resolved once for the same reason as the picker above:
        // the hand-off needs a platform host (a `Context` for Android's FileProvider), and the
        // presentation layer has nothing to hand a factory. It reaches the editor through
        // [com.tasktracker.featureprojectspresentation.project.ProjectEnvironment.opener].
        //
        // Registered unconditionally, unlike the blob transport: opening a file this device
        // attached is a cache read, so it works with no server. Tying it to the API base URL
        // would make a demo build's own attachments unopenable.
        app<AttachmentOpener> { createAttachmentOpener(platforms.host) }
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

        // Bound here rather than inside the session registration, because it is a property of
        // the store and not of a session: the eraser holds the database and nothing else.
        app<LocalStoreEraser> { resolver ->
            RoomLocalStoreEraser(resolver.resolve(TaskTrackerDatabase::class))
        }

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

        // ## The remote follows the base URL, and that switch is a recorded decision
        //
        // With no URL the graph has no server to talk to, so `MockAuthRemote` mints genuine
        // signed-format JWTs in-process — the vault's expiry, forced-refresh and restore paths
        // all run for real while nothing is listening on a socket. That is the offline demo a
        // test runs by leaving the URL `null`; no shipping shell names no URL any more.
        //
        // With a URL the real `HttpAuthRemote` is bound instead, which is every shell now that
        // all three pass [platformApiBaseUrl]'s value. This is the switch `MockAuthRemote`'s own
        // KDoc placed here, and the release rule it named still holds: a build with a backend is
        // handed `HttpAuthRemote`, never the mock.
        //
        // The client has **no interceptors**, deliberately. A login or refresh must not carry
        // an `Authorization` header, and routing a refresh through the authenticated client
        // would recurse: the 401 answering a refresh would trigger another refresh, which would
        // 401 again until the session was torn down as "expired". It is built here rather than
        // registered because `AppGraphTest` pins "no HTTP client registered"; the app scope
        // means the factory runs once, so one engine is created for the process either way.
        app<AuthRemotePort> { resolver ->
            platforms.apiBaseUrl?.let { url ->
                HttpAuthRemote(platforms.httpClient(url, emptyList()))
            } ?: MockAuthRemote(dateProvider = resolver.resolve(DateProvider::class))
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
     *
     * ## The demo board is written only while offline
     *
     * The port is wrapped in [OfflineOnlySampleSeed] whenever `platforms.apiBaseUrl` names a
     * backend, and the seeding call then answers `false`. That is a correctness rule rather than
     * a convenience: the repository queues every seeded row through the outbox, so with a
     * backend bound the sample board would not stay local scenery — the first sync would push it
     * to the server, for an account whose owner never asked to populate it. With no URL bound —
     * the demo every entry point that names no API root still runs — the real repository is
     * bound unchanged and seeds exactly as before.
     */
    private fun ResolverBuilder.registerProjects(platforms: PlatformBindings) {
        app<ProjectRepositoryPort> { resolver ->
            val repository = RoomProjectRepository(
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
            // The demo board is an offline fixture, so it is bound only while nothing is
            // pointing at a server. See the section above for why that is correctness rather
            // than convenience, and [OfflineOnlySampleSeed] for the decorator itself.
            if (platforms.apiBaseUrl == null) repository else OfflineOnlySampleSeed(repository)
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

        // The admin half of the projects feature: the live server view of every project, and
        // the edit path that reaches the change feed.
        //
        // Registered only when a backend is bound, and with a client built from
        // `platforms.httpClient` rather than `resolve(HttpClient::class)`: there is no
        // `HttpClient` registration in this graph — `registerAuth` and `registerSync` each build
        // their own — so a `resolve` would find nothing, throw inside the factory, and be read by
        // the shell's `getOrNull` as "no port". The member-admin pair made that mistake once and
        // is wired the same way now.
        //
        // The bearer token is the point of the interceptor list: an admin API call is an
        // authenticated one, and a client without `AuthInterceptor` would reach the server
        // anonymous. A demo graph has no projects API to administer, so it binds no port and
        // the shell renders the placeholder — the `null` the destination documents.
        platforms.apiBaseUrl?.let { url ->
            app<AdminProjectsPort> { resolver ->
                AdminProjectsRepository(
                    remote = HttpAdminProjectsRemote(
                        client = platforms.httpClient(
                            url,
                            listOf(AuthInterceptor(resolver.resolve(TokenVault::class))),
                        ),
                    ),
                )
            }
        }
    }

    /**
     * The user directory: [TeamPort] resolves a UserID into a User profile.
     *
     * The session cache answers for the signed-in account without a request, so the directory
     * always has at least one honest name. With a backend bound, [ServerTeamRepository] wraps
     * that local half and replaces search/resolution with the server's live account list —
     * the member picker was showing a fixed fixture until this wiring existed, which meant a
     * newly created project could not name the colleagues the admin had just added.
     *
     * Registered after persistence and projects — both depend only on [SessionCache], which
     * persistence owns, and projects reads it too.
     */
    private fun ResolverBuilder.registerTeams(platforms: PlatformBindings) {
        app<TeamPort> { resolver ->
            val local = RoomTeamRepository(
                sessionCache = resolver.resolve(SessionCache::class),
            )
            platforms.apiBaseUrl?.let { url ->
                ServerTeamRepository(
                    local = local,
                    remote = HttpTeamRemote(
                        client = platforms.httpClient(
                            url,
                            listOf(AuthInterceptor(resolver.resolve(TokenVault::class))),
                        ),
                    ),
                )
            } ?: local
        }
        // The admin half of the directory, and the same two rules the projects admin pair
        // records: bind it only when a backend is configured, and build the client from
        // `platforms.httpClient` rather than `resolve(HttpClient::class)`. This graph has no
        // `HttpClient` registration — `registerAuth` and `registerSync` each build their own —
        // so the old `resolve` threw `Unregistered` *inside* the factory, and the shell's
        // `getOrNull` read that as "no port". The members screen therefore sat on its
        // placeholder against a live server, with nothing red to say the wiring was dead.
        platforms.apiBaseUrl?.let { url ->
            app<HttpMemberAdminRemote> { resolver ->
                HttpMemberAdminRemote(
                    client = platforms.httpClient(
                        url,
                        listOf(AuthInterceptor(resolver.resolve(TokenVault::class))),
                    ),
                )
            }
            app<MemberAdminPort> { resolver ->
                MemberAdminRepository(
                    remote = resolver.resolve(HttpMemberAdminRemote::class),
                    team = resolver.resolve(TeamPort::class),
                )
            }
        }
    }

    /**
     * The transport, the engine, and the gate that decides when a cycle runs.
     *
     * ## The transport follows the same switch as the auth remote
     *
     * With no base URL, [InMemorySyncTransport] is bound: it is a real
     * [com.tasktracker.sync.SyncTransport] — it pushes, appends to a change log and serves it
     * back — so an offline demo still round-trips a change through the engine and the outbox
     * without a server, and a board hears about a write it did not make.
     *
     * With a URL, [com.tasktracker.sync.HttpSyncTransport] talks to the `/v1/sync` routes
     * instead. It is built over the **authenticated** client, which every sync call needs: the
     * server rejects an unauthorised push or pull, and a `401` from one is what drives
     * `AuthInterceptor`'s refresh-then-replay. Reusing the remote's plain client here would send
     * every request without a token. The interceptor needs the vault, so this is constructed
     * after [registerAuth] — the registration list is the dependency order.
     */
    private fun ResolverBuilder.registerSync(platforms: PlatformBindings) {
        app<SyncTransport> { resolver ->
            platforms.apiBaseUrl?.let { url ->
                HttpSyncTransport(
                    platforms.httpClient(
                        url,
                        listOf(AuthInterceptor(resolver.resolve(TokenVault::class))),
                    ),
                )
            } ?: InMemorySyncTransport()
        }

        // Attachment bytes are server-only. Binding this offline would wrap the in-memory
        // transport, whose blob methods deliberately throw, and turn "attach a file" into a
        // background upload that always fails. A demo graph therefore leaves the port
        // unbound and the attachment stays local — exactly the behaviour before this feature.
        if (platforms.apiBaseUrl != null) {
            app<AttachmentBlobTransport> { resolver ->
                SyncBackedAttachmentBlobTransport(resolver.resolve(SyncTransport::class))
            }

            // The sweep that drains the attachments a closed editor left behind, and the
            // `UploadAttachment` it runs. Registered under the same switch as the transport
            // above, and for the same reason: with no file API there is nowhere to send
            // bytes, so a registered sweep would be a loop over rows it can only conclude
            // nothing about. The coordinator asks for it with `getOrNull`, so a demo graph
            // simply gets no post-cycle hook.
            app<SweepPendingAttachments> { resolver ->
                val repository = resolver.resolve(ProjectRepositoryPort::class)
                SweepPendingAttachments(
                    repository = repository,
                    upload = UploadAttachment(
                        repository = repository,
                        transport = resolver.resolve(AttachmentBlobTransport::class),
                        dateProvider = resolver.resolve(DateProvider::class),
                    ),
                )
            }

            // The upgrade path, bounded to one run per install. Under the same switch for a
            // sharper reason: a demo graph has no server to announce anything to, and the
            // flag it would set is a promise made on behalf of a store that will never see
            // the change land. Leaving it unbound here means a demo graph keeps every
            // attachment local and honest rather than marking an upgrade done that did not
            // happen.
            app<BackfillUnqueuedAttachments> { resolver ->
                BackfillUnqueuedAttachments(
                    repository = resolver.resolve(ProjectRepositoryPort::class),
                    dateProvider = resolver.resolve(DateProvider::class),
                )
            }
            app<AttachmentUpgradeBackfill> { resolver ->
                AttachmentUpgradeBackfill(
                    // The use case behind a function rather than the class, so the guard is
                    // testable without a store — see its own KDoc.
                    backfill = { resolver.resolve(BackfillUnqueuedAttachments::class)() },
                    defaults = resolver.resolve(UserDefaultsStore::class),
                )
            }
        }

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
                    // Attachment metadata arrives on its own `attachments` stream and must
                    // reach the same sink; without this entry a pulled attachment would hit
                    // `NoSink`, and the pull for that scope would stop on its first change —
                    // so a member would never see an attachment another member added.
                    OutboxEntity.Attachment to sink,
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
        //
        // The post-cycle hook is the sweep, and only when a backend is configured —
        // `getOrNull` rather than `resolve` because the demo graph deliberately has no
        // `AttachmentBlobTransport` and so no `SweepPendingAttachments` either. Two triggers
        // want a cycle, and two things want to run after one; the second is bound here
        // rather than inside the coordinator because `:packages:sync` must not know what a
        // project is.
        app<SyncCoordinator> { resolver ->
            // Resolved once, here, rather than inside the hook: a hook that resolved on every
            // cycle would be a second resolution path per cycle, would resolve from whichever
            // thread the cycle finished on, and would throw once the container is stopped —
            // after `AppGraph.stop`, when a sweep is still finishing its last upload.
            val sweep = resolver.getOrNull(SweepPendingAttachments::class)
            val upgrade = resolver.getOrNull(AttachmentUpgradeBackfill::class)
            SyncCoordinator(
                engine = resolver.resolve(SyncEngine::class),
                monitor = resolver.resolve(NetworkMonitor::class),
                dateProvider = resolver.resolve(DateProvider::class),
                // An `if` rather than `?.let { … } ?: { }`, because a lambda literal nested
                // inside `let`'s result is inferred as a plain lambda and then rejected as a
                // suspend function type. Both branches are lambdas over the same parameter,
                // so the shape is uniform and the branch is the only difference.
                //
                // Order is not what makes the upgrade work — both halves converge on the
                // cycle after the backfill either way — but it is what keeps a failing
                // backfill from costing the sweep its cycle. The sweep is the recurring
                // work and the backfill is a one-time catch-up, so the recurring one goes
                // first: a store that throws while queueing the pre-sync rows still uploads
                // everything that was already queued.
                //
                // Neither waits. An upload that reaches the server before its own metadata
                // row is answered `404`, which the sweep records as "not yet on server" and
                // retries on the next cycle.
                onCycleSucceeded = if (sweep == null) {
                    { }
                } else {
                    {
                        sweep()
                        upgrade?.runIfNeeded()
                    }
                },
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
                defaults = resolver.resolve(UserDefaultsStore::class),
                eraser = resolver.resolve(LocalStoreEraser::class),
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
     * ## Two closures, because there are two ways to end a session
     *
     * `endSession` asks the server first and can fail; `forceEndSession` asks nothing and
     * erases what the session left behind. They are separate parameters rather than one
     * closure taking a flag, so the settings service cannot accidentally pass the wrong one —
     * the two are not variants of a call, they are different calls with different outcomes,
     * and only the screen knows which one the user chose.
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
                onEndSession = { resolver.resolve(SessionCoordinator::class).signOut() },
                onForceEndSession = { resolver.resolve(SessionCoordinator::class).forceSignOut() },
                // The one place both sides are visible. The store that can rebuild a refused
                // payload is the projects repository, which `:packages:feature-settings-data`
                // is not allowed to name — so the call is handed over as a closure and the
                // service stays the seam rather than learning what a project is.
                onRetryStuckChanges = {
                    resolver.resolve(ProjectRepositoryPort::class).retryStuckChanges(
                        // The same app-scoped clock the repository was built with, rather than
                        // a second one, so a repaired project's `updatedAt` agrees with every
                        // other timestamp this write touches.
                        now = resolver.resolve(DateProvider::class).now(),
                    )
                },
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
 * The platform backends, the store location, and the API root: the values that differ between
 * a real process and a test host.
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
     * The API root the graph talks to, or `null` for the offline demo with no backend.
     *
     * The one value that decides whether [AppGraph]'s auth and sync registrations bind the real
     * HTTP pair or the in-process demo pair. `null` is the default so a call site that names no
     * backend gets the demo; the three shells all pass [platformApiBaseUrl]'s value, so only a
     * test leaves this `null`.
     *
     * A default rather than a required argument for the same reason [database] is: the default
     * is what production gets, and the call sites that pass anything are the platform entry
     * points and the tests.
     */
    val apiBaseUrl: String? = null,
    /**
     * Builds the HTTP client a bound backend is reached through, given its root and the
     * interceptors it must carry.
     *
     * A seam for the same reason [keychain] is one, and it is here to make one property
     * assertable rather than obvious: the sync client carries a bearer-token interceptor and
     * the auth client carries none. Both halves are silent when they are wrong — an
     * unauthenticated sync is a 401 on every push, and a login routed through the
     * authenticated client recurses the moment the server answers 401 — and neither is
     * observable from outside a built client, so a graph test cannot see them without a
     * factory it can watch. It also keeps a graph test from constructing a real platform
     * engine just to ask which of two clients was bound.
     *
     * The default is [defaultHttpClient], and both halves of that are gated: `AppGraphTest`
     * drives a request through a client built by [defaultHttpClient], and it constructs
     * [PlatformBindings] with this argument left to its default and sends through the factory
     * it finds, so a default that dropped the interceptors is caught.
     *
     * ## What the seam does not reach, stated rather than implied
     *
     * [AppGraph.start] builds its [PlatformBindings] with *named* arguments, so naming
     * `httpClient` there would replace this default — a wiring that shipped an unauthenticated
     * sync client — and no test would see it. The test above constructs its own
     * [PlatformBindings] instead of calling `AppGraph.start`, because `start`'s own list leaves
     * the two platform backends and the store to the caller; and a graph built by `start`
     * reports only the *type* of client that got bound. Seeing the interceptors a client
     * carries means asking [KtorHttpClient], which keeps them private, so the only observe is a
     * request — and a request is the socket these tests exist to avoid. The test's argument list
     * is therefore deliberately this constructor's, not `AppGraph.start`'s, and this link is
     * recorded as a residual rather than gated.
     */
    val httpClient: (String, List<RequestInterceptor>) -> HttpClient = defaultHttpClient,
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
     * Production is `true` and no entry point passes anything else: the two platform entry
     * points and Desktop all call [AppGraph.start], which starts them. The Desktop entry
     * point's wiring assertion was the first caller that needed `false` — it has to build a real
     * graph through the production entry and may not open the developer's store to do it — and
     * the graph's own tests are the others, since `testBindings` passes `false` by default. See
     * [AppGraph.start]'s `startServices` note. It is also here for tests that exercise a
     * *registration* and would
     * otherwise have to race the session coordinator's own `restore` against the restore they
     * are trying to observe: two concurrent restores of the same vault is a race that has
     * nothing to do with what those tests are about.
     *
     * Turning loops off is not turning off wiring — every registration, scope and factory is
     * still the production one, so a graph built this way is describable and resolvable in
     * exactly the ways a running one is.
     */
    val startServices: Boolean = true,
) {

    companion object {

        /**
         * The client every real backend call goes through: [KtorHttpClient] over the platform
         * engine, with exactly the interceptors its caller named and no others.
         *
         * A named value rather than the literal duplicated at [PlatformBindings.httpClient]'s
         * declaration and again in `testBindings`: a test that substituted a factory would
         * otherwise silently diverge from what production builds the day either copy changed,
         * and the whole point of the seam is that the substituted shape and the real one are
         * the same code path up to the engine.
         *
         * What the tests hold is that the interceptors reach [KtorHttpClient], and that the
         * *given* interceptor runs. Two things they do not hold, and cannot without a socket:
         *
         * - `baseUrl`. The test interceptor refuses before an address is built and the client's
         *   root is private, so a factory that hard-coded the root would stay green. The root is
         *   pinned a level up instead — at `desktopApiBaseUrl`, whose value
         *   `DesktopEntryPointJvmTest` asserts literally, and whose pass-through into the graph
         *   is pinned by type. That pass-through is the link nothing observes: a wrong root here
         *   is a wrong port, not a failing test.
         * - an *extra* interceptor. The one the test hands over throws first, so a factory that
         *   appended another would never reach it, and "and no others" above is the half that
         *   is asserted by convention rather than by an assertion.
         */
        val defaultHttpClient: (String, List<RequestInterceptor>) -> HttpClient =
            { baseUrl, interceptors ->
                KtorHttpClient(baseUrl = baseUrl, interceptors = interceptors)
            }
    }
}
