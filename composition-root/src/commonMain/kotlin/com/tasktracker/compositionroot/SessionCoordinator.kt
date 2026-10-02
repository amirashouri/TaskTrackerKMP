package com.tasktracker.compositionroot

import com.tasktracker.core.DateProvider
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.database.CachedSession
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.network.SessionInvalidationReason
import com.tasktracker.routing.Router
import com.tasktracker.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Discards everything that belonged to the signed-in user.
 *
 * ## Its own type rather than a closure on the shell
 *
 * The original builds it as a value it already holds, and that is the reason it is a class
 * here too: [SessionCoordinator] needs it at construction, and a closure capturing the shell
 * would have to be created inside the shell's `init`, before it is fully initialised. The
 * port's alternative — assigning it afterwards — is a two-phase initialisation, and a
 * coordinator can observe one half-built.
 *
 * ## [reason] exists so a tap and an expiry cannot drift
 *
 * Every way a session ends arrives here. A `403` from the server and a tap on Sign Out clear
 * the same state; only the logged reason differs. Routing is deliberately *not* done here:
 * [Router] belongs to [SessionCoordinator], and two owners of `root` is how a sign-out ends
 * up half-applied.
 *
 * ## Outbox rows are kept, and this is the load-bearing decision
 *
 * Every queued row carries the id of the account that queued it, and the engine claims only
 * rows belonging to whoever is signed in ([com.tasktracker.sync.SyncEngine]'s `pushAs`). A
 * different user signing in therefore cannot push this account's work, and the same user
 * signing back in finds their unsynced edits where they left them.
 *
 * Pull metadata is *not* kept: its cursors describe a server-side view of the change log as
 * seen by this session, and a stale cursor under a new account would replay changes that
 * account has already applied.
 */
class SessionTeardown(
    private val resolver: Resolver,
    private val metadata: SyncMetadataStore,
    private val sessionCache: SessionCache,
    private val syncCoordinator: SyncCoordinator,
) {

    suspend fun run(reason: SessionInvalidationReason) {
        // The reason is recorded, and it is the only observable difference between the two ways
        // a session ends. A tap and a `403` clear identical state, so without this line the two
        // are indistinguishable in a bug report — and "the app signed me out" is exactly the
        // question a user asks, with exactly one of two answers. It goes first because it is
        // cheap and because a crash in a teardown below would otherwise lose it.
        //
        // The wire name, not the enum name: this is a log a person reads, and
        // [SessionInvalidationReason] names the member the way the source does, not the way the
        // reason reads.
        TTLogger.auth.info { logMessage { append("session ended: "); append(reason.wireName) } }

        // Session-scoped objects go first, so nothing can resolve a half-torn-down graph.
        resolver.resetSessionScope()

        runCatching { metadata.removeAll() }
        // Before anything else can write a row that would be attributed to this account.
        // A status change made after this point but before the next sign-in is recorded with
        // an unknown author, which is the truth — there is no user to name. It also stamps
        // `null` on any outbox row queued in that window, which no session will claim until
        // someone signs in.
        runCatching { sessionCache.clear() }
        syncCoordinator.stop()
    }
}

/**
 * Turns session state into navigation.
 *
 * ## It lives here, not in the auth feature
 *
 * The split between the sign-in screen and the signed-in shell is a decision only the app can
 * make: the auth feature knows *whether* a session exists and [Router] knows where each half
 * lives, but neither may depend on the other. This is the one place that sees both.
 *
 * And here rather than in `:shared-ui`, which the plan sketched. `:shared-ui` *depends on*
 * this module, so a coordinator here could not be named by [AppGraph] — and the settings
 * module's `endSession` is handed the graph's coordinator, so somebody has to be able to
 * resolve it. Moving the shell's own coordinator up to the composition root costs the shell
 * one `resolve`, which it was making anyway, and removes a layer whose only purpose would
 * have been to hold a type the graph could not see.
 *
 * ## Deliberately not observable state
 *
 * It exposes no Compose state. [Router] is the observable half, and this class's whole job is
 * to keep it correct — a second piece of observable state here would be a second thing a
 * recomposition could read stale.
 *
 * ## [hasLiveSession] is the guard that stops a cold launch destroying work
 *
 * `restore()` on a fresh install ends in [SessionState.SignedOut] because there are no
 * credentials, which is the app's normal initial state and not the end of anything. Tearing
 * down there would clear pull metadata and stop the sync coordinator on *every cold start*,
 * silently discarding unsynced work for a session that never existed.
 */
class SessionCoordinator(
    private val session: AuthSessionPort,
    private val router: Router,
    private val teardown: SessionTeardown,
    private val sessionCache: SessionCache,
    private val dateProvider: DateProvider,
    /**
     * Where the listener and the restore run.
     *
     * Injectable for the same reason `SyncCoordinator` takes one: a test hands in
     * `runTest`'s scope so the restore completes deterministically instead of racing the
     * assertions. The default is app-lifetime, and [stop] cancels the children rather than
     * the scope, so a caller that supplied its own does not get it cancelled from under it.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private var listener: Job? = null

    /**
     * Why the user ended the session, when they did.
     *
     * A user tap and an expiry need different logs, and the session bus does not carry the
     * reason.
     */
    private var pendingReason: SessionInvalidationReason? = null

    /** Whether a session is currently live. See the class note. */
    private var hasLiveSession = false

    /**
     * Subscribes, then adopts whatever session is already on the device.
     *
     * Subscribe first, restore second. The other order leaves a window in which `restore()`
     * publishes [SessionState.SignedIn] to nobody and the app sits on the sign-in screen with
     * a valid session until the next sign-out — and `sessionUpdates` has no replay, so the
     * publication is simply *dropped* rather than buffered.
     *
     * Writing them in that order is not by itself enough, which is what [CoroutineStart.UNDISPATCHED] is for.
     * Both `launch`es below are dispatched, so on a multi-threaded scope — and the default is
     * [Dispatchers.Default] — the restore can be *dequeued* before the collector has run its
     * first instruction, let alone reached `collect`. The window is then real even though the
     * code is in the right order, and it is a window that widens on whatever thread the
     * coordinator happens to be started from. `UNDISPATCHED` runs the collector's first
     * instruction inline, up to the suspension inside `collect`, so by the time it returns the
     * subscription exists and the `Restoring` publication cannot be lost. The shell's test
     * double found this before production did; the same ordering rule is documented on
     * `ScriptedSessionDriver`.
     *
     * Idempotent, because a second call — from a recomposition, or from a graph that was
     * rebuilt — must not leave a second collector writing the same router.
     */
    fun start() {
        if (listener != null) return
        listener = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.sessionUpdates.collect { handle(it) }
        }
        scope.launch {
            // A restore failure is not a sign-out. The port's contract is that a failed restore
            // leaves the previous state alone, so there is nothing to end and nothing to tear
            // down; the collector above has already routed to sign-in.
            runCatching { session.restore() }
        }
    }

    /** Ends the subscription. For a shell being torn down. */
    fun stop() {
        listener?.cancel()
        listener = null
    }

    /**
     * Ends the session at the user's request.
     *
     * Deliberately goes through the port rather than tearing down directly: the port is what
     * forgets the credentials, and this coordinator reacts to the state it publishes. Calling
     * the teardown here would leave the tokens sitting in the keychain.
     */
    suspend fun signOut() {
        pendingReason = SessionInvalidationReason.UserLogout
        session.signOut()
    }

    private suspend fun handle(state: SessionState) {
        when (state) {
            // Nothing to route. The app opens on `Router.root`'s default of `.auth`, which is
            // where a restoring session belongs. The router is told anyway, because "reading
            // the session" and "no session" are the same screen but not the same moment: the
            // app shows its splash for the first and the sign-in form for the second.
            SessionState.Restoring -> router.beginRestoringSession()

            is SessionState.SignedIn -> {
                hasLiveSession = true
                // Cached before the shell is entered, so the first status change anybody makes
                // can be attributed. This is the only place the app learns who is signed in:
                // `SessionState` carries the identity, and every other consumer reads the cache.
                cache(state.identity)
                router.enterMain()
            }

            SessionState.SignedOut -> {
                val userAsked = pendingReason
                pendingReason = null
                val wasLive = hasLiveSession
                hasLiveSession = false

                // Teardown only for a session that actually ended. See the class note.
                if (wasLive || userAsked != null) {
                    teardown.run(reason = userAsked ?: SessionInvalidationReason.Unauthorized)
                }
                router.enterAuth()
            }
        }
    }

    /**
     * Best effort, like every other cache write here.
     *
     * A store that cannot record the user is not a reason to refuse the session; the cost is
     * that history rows are recorded with an unknown author.
     */
    private suspend fun cache(identity: UserIdentity) {
        runCatching {
            sessionCache.save(
                CachedSession(
                    userID = identity.id.stringValue,
                    email = identity.email,
                    displayName = identity.displayName,
                    lastSignedInAt = dateProvider.now(),
                    serverVersion = 0,
                )
            )
        }
    }
}