package com.tasktracker.sharedui

import com.tasktracker.core.AppError
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.DateProvider
import com.tasktracker.core.TestDateProvider
import com.tasktracker.di.DiException
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlin.reflect.KClass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [Resolver] backed by a map, for testing the shell without a container.
 *
 * The shell resolves a session, a router and the two projects collaborators, and this binds
 * all four. It exists rather than reaching for `AppGraph` because a shell test that builds
 * the production graph would fail for reasons that have nothing to do with the shell: a
 * change to an auth registration would break a test about which screen the app shows, and the
 * failure would point at the wrong file — and, on the Android host, a production graph
 * cannot even be built, since the projects store needs a real `Context`.
 *
 * Every other method throws. A test that accidentally resolves something else should fail
 * loudly at the call, not silently get `null` and pass a weaker assertion than intended.
 */
class FakeResolver(private val bindings: Map<KClass<*>, Any>) : Resolver {

    override fun <T : Any> resolve(klass: KClass<T>): T {
        val binding = bindings[klass]
            ?: throw DiException.Unregistered("no binding for ${klass.simpleName}")
        @Suppress("UNCHECKED_CAST")
        return binding as T
    }

    override fun <T : Any> getOrNull(klass: KClass<T>): T? = bindings[klass] as? T

    override fun <T : Any> forceResolve(klass: KClass<T>): T = resolve(klass)

    override fun start() = Unit

    override val isStarted: Boolean = true

    override fun resetSessionScope() = Unit

    override fun invalidateInstances() = Unit

    override fun stop() = Unit

    override fun isRegistered(klass: KClass<*>): Boolean = bindings.containsKey(klass)

    override val registeredNames: List<String> = bindings.keys.mapNotNull { it.simpleName }.sorted()

    /**
     * Starts the session→router driver and returns it, for a test to stop.
     *
     * A method rather than something [forSession] does on construction because the driver is
     * the graph's responsibility, not a property of the bindings — and starting it eagerly in
     * the factory would mean a test that builds a resolver to inspect its `registeredNames`
     * had also started a session restore it never asked for.
     */
    fun startSessionDriver(): ScriptedSessionDriver {
        val driver = ScriptedSessionDriver(resolve(AuthSessionPort::class), resolve(Router::class))
        driver.start()
        return driver
    }

    companion object {
        /**
         * A graph with everything the signed-in area needs.
         *
         * The router and the projects repository are bound here rather than left to each
         * scenario, because "the shell resolves a router and a project repository" is a
         * property of the shell, not of any one test: a scenario that had to remember to bind
         * them would be a scenario that could forget, and the failure would be a crash rather
         * than an assertion.
         *
         * The router is a *real* [Router]. Navigation state is pure Kotlin with no platform
         * dependency, and a stub router would make every routing assertion here vacuous — the
         * shell would be tested against an object that agrees with whatever it is told.
         *
         * The repository is passed in so a test that needs a specific board can seed one.
         * The clock is the real [TestDateProvider] from `:packages:core`'s `commonMain`, so
         * nothing a test creates is stamped with wall-clock time.
         */
        fun forSession(
            session: AuthSessionPort,
            router: Router = Router(),
            projects: ProjectRepositoryPort = FakeProjectRepository(),
            settings: SettingsPort? = null,
            preferences: UserDefaultsStore = UserDefaultsStore(InMemoryPreferences()),
        ): FakeResolver = FakeResolver(
            mapOf(
                AuthSessionPort::class to session,
                Router::class to router,
                ProjectRepositoryPort::class to projects,
                DateProvider::class to TestDateProvider(),
                // The shell resolves this to build `ShellPreferences`, so it is bound here
                // rather than per-scenario: a test that had to remember it is a test that can
                // forget it, and the failure would be a `DiException` rather than an assertion.
                UserDefaultsStore::class to preferences,
            ) + (settings?.let { mapOf(SettingsPort::class to it) } ?: emptyMap()),
        )

        /**
         * A graph with a session and nothing else — the shape an app built without projects
         * has, and the one case where the shell is *supposed* to show a message rather than a
         * screen.
         *
         * The preferences store is bound even here, because the shell resolves it
         * unconditionally: a graph without projects is a real build, not a half-wired one, and
         * a crash in that build would be a worse answer than the sentence it is supposed to
         * show.
         */
        fun withoutProjects(
            session: AuthSessionPort,
            router: Router = Router(),
            preferences: UserDefaultsStore = UserDefaultsStore(InMemoryPreferences()),
        ): FakeResolver = FakeResolver(
            mapOf(
                AuthSessionPort::class to session,
                Router::class to router,
                UserDefaultsStore::class to preferences,
            ),
        )
    }
}

/**
 * A scripted [AuthSessionPort], with the sign-in state machine the real repository has and
 * only the parts the shell exercises.
 *
 * `sessionUpdates` is a state flow rather than a bare `emptyFlow()` because the sign-out
 * path's correctness depends on the shell reacting to the port publishing `SignedOut`; a
 * port that published nothing would make a shell that ignored the bus look correct.
 */
class ScriptedSession(
    initial: SessionState = SessionState.SignedOut,
    private val restored: SessionState = initial,
    private val onSignIn: suspend (email: String, password: String) -> SessionState = { _, _ ->
        SessionState.SignedOut
    },
    /**
     * Held by [restore] until completed, so a test can observe the window *before* the
     * session settles.
     *
     * Needed because `waitForIdle()` drains the shell's `LaunchedEffect`, so an ungated
     * restore has always finished by the time an assertion runs. The launch path's
     * in-flight behaviour is therefore untestable without this, and it is exactly where the
     * sign-in-flash bug would live.
     */
    private val restoreGate: CompletableDeferred<Unit>? = null,
) : AuthSessionPort {

    /**
     * The real [Broadcaster], not a `StateFlow`.
     *
     * This is fidelity, not tidiness. `sessionUpdates` has no replay and drops a `send` when
     * nothing is collecting, and a `StateFlow` replays its current value to every new
     * collector — so a replaying double cannot fail the way the real port fails. It hid a
     * real bug: the shell subscribed with a plain `launch`, so `restore` published before the
     * subscription existed, both sends were dropped, and the app rendered a permanently blank
     * screen. Against this double, that test fails as it should.
     */
    private val states = Broadcaster<SessionState>()
    private val gate = Mutex()

    /** Every sign-in the shell triggered, so a test can assert the shell passed them on. */
    val signInAttempts: MutableList<Pair<String, String>> = mutableListOf()

    /**
     * When set, [signOut] fails with it instead of ending the session.
     *
     * The server-facing half of a sign-out is the only part that can fail, and this is how a
     * shell test reaches the popup that failure exists to raise.
     */
    var signOutFailure: AppError? = null

    /** How many times the device-only sign-out was taken. */
    var signOutLocallyCalls: Int = 0
        private set

    /**
     * What the port last published, for asserting an effect rather than an emission.
     *
     * Tracked separately because the real bus keeps no such field — a broadcast is fire and
     * forget. The shell cannot read this, and neither can any test that only observes the
     * screen.
     */
    var currentState: SessionState = initial
        private set

    private fun publish(state: SessionState) {
        currentState = state
        states.send(state)
    }

    override val sessionUpdates: Flow<SessionState> = states.stream()

    override suspend fun signIn(email: String, password: String): UserIdentity {
        gate.withLock { signInAttempts += email to password }
        val next = onSignIn(email, password)
        publish(next)
        return (next as? SessionState.SignedIn)?.identity
            ?: throw IllegalStateException("scripted sign-in did not produce a signed-in state")
    }

    override suspend fun restore(): SessionState {
        // The real `AuthRepository` publishes `Restoring` before it reads storage, and this
        // ordering is load-bearing: a shell that treats `Restoring` as signed-out flashes the
        // sign-in form at a user who is already signed in.
        publish(SessionState.Restoring)
        restoreGate?.await()
        publish(restored)
        return restored
    }

    override suspend fun signOut() {
        // Settable, so a shell test can drive the one path the settings screen has a popup
        // for: a sign-out the server could not confirm. Left null, this is the happy path and
        // the popup must not appear.
        signOutFailure?.let { throw it }
        publish(SessionState.SignedOut)
    }

    /**
     * The device-only sign-out, counted rather than published separately.
     *
     * It publishes the same state as [signOut], because that is the contract: the difference
     * between the two is what they ask the server, and a double that ended in a different state
     * would let a shell that confused them pass.
     */
    override suspend fun signOutLocally() {
        signOutLocallyCalls++
        publish(SessionState.SignedOut)
    }
}

/**
 * A [SettingsPort] for the shell's scenarios, and *not* the feature's own `FakeSettingsPort`.
 *
 * A `commonTest` source set is compiled per module and is on no other module's classpath, so
 * the feature's double cannot be reached from here and cannot be made reachable. This is
 * therefore a second implementation of the same port for the same reason `FakeProjectRepository`
 * is a second implementation of its own: the shell needs the port, the feature's copy is
 * invisible, and a shell test that could not supply one would leave the Settings tab untested.
 *
 * Both update flows are `emptyFlow()`, which completes immediately — a collector of one runs to
 * completion and stops rather than hanging a test on a subscription nothing will fulfil.
 */
class FakeShellSettingsPort(
    private val connectivityValue: ConnectivitySummary = ConnectivitySummary.Online,
    private val canSyncNowValue: Boolean = true,
    private val outcomeValue: SyncOutcome = SyncOutcome.Started,
) : SettingsPort {

    private val forcedOfflineValues = mutableListOf<Boolean>()
    private val syncNowValues = mutableListOf<SyncOutcome>()

    var sessionEndCount: Int = 0
        private set

    var sessionForceEndCount: Int = 0
        private set

    val forcedOfflineHistory: List<Boolean> get() = forcedOfflineValues.toList()

    val syncNowHistory: List<SyncOutcome> get() = syncNowValues.toList()

    override suspend fun connectivity(): ConnectivitySummary = connectivityValue

    override fun connectivityUpdates(): Flow<ConnectivitySummary> = emptyFlow()

    override suspend fun setForcedOffline(forced: Boolean) {
        forcedOfflineValues += forced
    }

    override suspend fun syncActivity(): SyncActivity = SyncActivity()

    override fun syncUpdates(): Flow<SyncActivity> = emptyFlow()

    override suspend fun canSyncNow(): Boolean = canSyncNowValue

    override suspend fun syncNow(): SyncOutcome {
        syncNowValues += outcomeValue
        return outcomeValue
    }

    override suspend fun endSession() {
        sessionEndCount++
    }

    /**
     * Counted separately from [sessionEndCount], and the separation is the assertion that
     * matters here: only this one erases the user's data, so a shell that wired the popup's
     * confirm button to the wrong port call would still sign the user out and only this count
     * would show it.
     */
    override suspend fun forceEndSession() {
        sessionForceEndCount++
    }

    override fun appVersion(): AppVersion = AppVersion(marketing = "1.0", build = "1")

    override suspend fun userDetails(): UserDetails = UserDetails()

    override fun userDetailsUpdates(): Flow<UserDetails> = emptyFlow()

    override suspend fun setUserDetails(details: UserDetails) = Unit
}

/** A signed-in state for tests, with a recognisable address. */
fun signedIn(email: String = "signed-in@example.com"): SessionState.SignedIn =
    SessionState.SignedIn(
        UserIdentity(
            id = com.tasktracker.core.randomUserID(),
            email = email,
        ),
    )

/**
 * Drives the [Router] from a session, standing in for the graph's `SessionCoordinator`.
 *
 * ## Why the shell needs one at all
 *
 * The shell used to subscribe to the session and route it itself. It no longer does — that is
 * the phase's whole change, and the routing now lives in
 * `com.tasktracker.compositionroot.SessionCoordinator`, which `AppGraph` starts. So a shell
 * test that binds only a session and a router would leave the router on its auth default and
 * every signed-in scenario would fail for a reason that has nothing to do with the shell.
 *
 * ## Why it is a double and not the real coordinator
 *
 * The real one needs a `Resolver`, a `SyncMetadataStore`, a `SessionCache` and a
 * `SyncCoordinator` — a graph, a database and a sync engine — none of which this module can
 * build: the Android host's store needs a `Context`, and a `commonTest` source set is on no
 * other module's classpath so the fakes cannot be shared either.
 *
 * The routing *rules* are therefore not covered here, and are not meant to be:
 * `SessionCoordinatorTest` in `:composition-root` asserts them against the real type. What
 * these scenarios are for is the level below that — given a router that has been routed, does
 * the shell draw the right screen — and a driver that routes by the same three rules is enough
 * to reach it. The one rule copied verbatim is `Restoring`, because the splash gate's
 * correctness depends on the router saying "still restoring" rather than the driver guessing.
 */
class ScriptedSessionDriver(
    private val session: AuthSessionPort,
    private val router: Router,
) {
    private var subscription: Job? = null

    /**
     * Subscribes, then restores — on [Dispatchers.Unconfined], and that is the whole trick.
     *
     * `Unconfined` runs both coroutines to their first *real* suspension on the calling thread,
     * so the subscription is attached and the restore has been routed before [start] returns.
     * Every scenario then begins against a router that has already been told the answer, which
     * is what makes `waitForIdle()` sufficient — with any dispatched scope there would be a
     * window where it returned before the session had been read, and the failure would be a
     * missing node rather than anything about the shell.
     *
     * It is also why the scope is *not* the test's. A driver parented to the test's job would
     * never complete — it collects for as long as the session lives — and `runTest` waits for
     * the test body's children, so every scenario would time out at 60s. Detached, it lives
     * exactly as long as the process, holding one session and one router that no other test
     * can see. [stop] exists for a test that wants it gone.
     */
    fun start() {
        subscription = CoroutineScope(Dispatchers.Unconfined).launch {
            session.sessionUpdates.collect { state ->
                when (state) {
                    SessionState.Restoring -> router.beginRestoringSession()
                    is SessionState.SignedIn -> router.enterMain()
                    SessionState.SignedOut -> router.enterAuth()
                }
            }
        }
        CoroutineScope(Dispatchers.Unconfined).launch { runCatching { session.restore() } }
    }

    fun stop() {
        subscription?.cancel()
        subscription = null
    }
}
