package com.tasktracker.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The physical network path, as far as the platform will admit to knowing.
 *
 * The four cases are the original's, and the fourth is not a placeholder — [Unknown] is
 * the state before the first callback arrives, and the sync engine treats it as "not a
 * recovery event" rather than as offline, so a cold start does not bounce the UI through
 * an offline banner it has not earned yet.
 */
enum class Connectivity {
    Satisfied,
    Unsatisfied,

    /** A network exists but the user has not joined it — captive portal, Wi-Fi with no route. */
    RequiresConnection,
    Unknown,
    ;

    /**
     * Whether the platform has said the path *cannot* be used, as opposed to not having
     * said yet.
     *
     * [Unknown] is deliberately **not** unusable. A monitor has not reported at all on a
     * cold start, and a client that failed fast on it would park every request on every
     * launch, before the platform has had a chance to answer.
     *
     * Whether a *sync cycle* is worth starting is a different question with a different
     * answer, so it is not answered here. The original wrote that policy as an internal
     * extension on this type inside `SyncService`, with a comment saying a view should ask
     * `SyncCoordinator.canSyncNow` instead so the rule is written down once; the port
     * keeps it there, in `:packages:sync`.
     */
    val isKnownUnusable: Boolean get() = this == Unsatisfied || this == RequiresConnection
}

/** The minimum [KtorHttpClient] needs to know whether to try at all. */
interface ConnectivitySource {
    val current: Connectivity
}

/**
 * The platform half of connectivity.
 *
 * Deliberately just `start`/`stop` plus a callback: path classification, de-duplication,
 * the override and the stream all live in common code where they can be tested, and each
 * platform contributes only the thing it alone can know. An `expect class NetworkMonitor`
 * — the shape the plan sketched — would have put the policy on the untestable side of the
 * seam and left three near-identical bodies to drift apart.
 */
interface ConnectivityWatcher {
    /**
     * Registers the single observer. Called by [NetworkMonitor.start] rather than by the
     * factory, because a factory that takes a callback cannot be the default argument of a
     * constructor that needs to pass its own method reference.
     */
    fun attach(onStatus: (Connectivity) -> Unit)

    fun start()

    fun stop()
}

/**
 * Publishes [Connectivity] as a stream of changes.
 *
 * @param watcher the platform watcher. Injected rather than constructed so a test can drive
 *   statuses without one. The secondary constructors pick the real one for this platform.
 */
class NetworkMonitor(private val watcher: ConnectivityWatcher) : ConnectivitySource {

    /** Uses the platform watcher. Throws on Android, which needs a `Context` — see [forHost]. */
    constructor() : this(createConnectivityWatcher(null))

    /** The last status the platform reported, ignoring [setOverride]. */
    private val platform = MutableStateFlow(Connectivity.Unknown)

    /** Settings' "Force Offline", or `null` for the real path. */
    private val override = MutableStateFlow<Connectivity?>(null)

    /** What [current] and [updates] report. Written only by [publish], so it cannot skew. */
    private val effective = MutableStateFlow(Connectivity.Unknown)

    private var running = false

    override val current: Connectivity get() = effective.value

    /**
     * A fresh stream per caller, replaying the current value immediately.
     *
     * `MutableStateFlow` rather than core's `Broadcaster`, and the difference is the
     * point. Connectivity is a *level*, not a stream of events: a subscriber that was
     * descheduled across an offline→online flip wants the newest value and no backlog, so
     * conflating is correct and replay-on-subscribe is what lets a freshly opened screen
     * render "Offline" without waiting for the next change. `Broadcaster` does the
     * opposite on both counts, for a caller that needs it — the session-invalidation
     * stream, where every event matters and a late subscriber must see nothing.
     *
     * The same choice makes de-duplication structural: a repeated status cannot re-emit,
     * because a `StateFlow` suppresses a value equal to its current one. The original
     * needed an explicit `guard next != status` for that.
     */
    fun updates(): Flow<Connectivity> = effective.asStateFlow()

    /** Starts observing. Idempotent, because a second `start` must not double-report. */
    fun start() {
        if (running) return
        running = true
        watcher.attach(::handle)
        watcher.start()
    }

    fun stop() {
        if (!running) return
        running = false
        watcher.stop()
    }

    /**
     * Forces the reported connectivity regardless of the real path.
     *
     * This is what makes the Settings "Force Offline" switch honest: a simulator's network
     * is always up, so without an override the offline banner would be a lie the user can
     * see through. Passing `null` hands the decision back to the platform.
     */
    fun setOverride(forced: Connectivity?) {
        override.value = forced
        publish()
    }

    private fun handle(next: Connectivity) {
        if (platform.value == next) return
        platform.value = next
        publish()
    }

    private fun publish() {
        effective.value = override.value ?: platform.value
    }

    companion object {
        /**
         * The Android constructor, because `ConnectivityManager` needs a `Context` and
         * Kotlin has no way to have a default argument that is only required on one
         * platform. [host] is ignored on JVM and iOS.
         */
        fun forHost(host: Any?): NetworkMonitor = NetworkMonitor(createConnectivityWatcher(host))

        /**
         * A monitor driven by a caller-supplied [ConnectivityWatcher].
         *
         * Public, and this is the second reason the primary constructor is not `internal`:
         * hiding it while [ConnectivityWatcher] is public would mean a consumer could see the
         * seam and never supply one. `:packages:sync` needs exactly that — its coordinator
         * tests must not depend on the machine having a network, and cannot use [forHost]
         * because on Android that demands a `Context`.
         */
        fun forWatcher(watcher: ConnectivityWatcher): NetworkMonitor = NetworkMonitor(watcher)
    }
}

/**
 * The platform's connectivity watcher.
 *
 * @param host Android's application `Context`, ignored elsewhere. A missing context is
 *   reported by name, the same way `:packages:database` reports a missing one: a monitor
 *   that cannot watch is otherwise indistinguishable from one that is watching and
 *   reporting [Connectivity.Unknown] forever.
 */
expect fun createConnectivityWatcher(host: Any?): ConnectivityWatcher
