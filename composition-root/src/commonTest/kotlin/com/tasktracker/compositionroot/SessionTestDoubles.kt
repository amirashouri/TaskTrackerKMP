package com.tasktracker.compositionroot

import com.tasktracker.core.DateProvider
import com.tasktracker.database.CachedSession
import com.tasktracker.database.OutboxEntry
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.OutboxStatus
import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SyncMetadata
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.di.DiException
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.network.Connectivity
import com.tasktracker.network.ConnectivityWatcher
import com.tasktracker.network.NetworkMonitor
import com.tasktracker.sync.InMemorySyncTransport
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncEngine
import com.tasktracker.sync.SyncSchedule
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A session port that publishes on demand rather than running its own bus.
 *
 * A `MutableSharedFlow` with no replay, because these tests *drive* transitions: a
 * coordinator's interesting inputs are a restore, then a sign-in or a sign-out, and a bus
 * that only publishes when asked is the only way to ask in between two assertions. The
 * no-replay property is kept for a reason beyond convenience — it is the property that makes
 * `subscribe, then restore` load-bearing, so a double that replayed would quietly remove the
 * very failure these tests exist to catch. What it cannot model is a *late* subscriber
 * missing an earlier publication, which is why [emitBeforeSubscribe] exists as an explicit
 * opt-in rather than as an accident of construction.
 */
internal class RecordingSessionPort : AuthSessionPort {

    /** What [restore] settles on. */
    var restoreResult: SessionState = SessionState.SignedOut

    /** Makes [restore] throw, standing in for a keychain read that failed. */
    var failRestore: Boolean = false

    var restoreCalls: Int = 0
        private set
    var signOutCalls: Int = 0
        private set

    private val states = MutableSharedFlow<SessionState>(extraBufferCapacity = 8)

    /** Published with nothing collecting, to reproduce a publication nothing was ready for. */
    fun emitBeforeSubscribe(state: SessionState) {
        states.tryEmit(state)
    }

    fun publish(state: SessionState) {
        states.tryEmit(state)
    }

    override suspend fun signIn(email: String, password: String): UserIdentity =
        throw UnsupportedOperationException("the session coordinator never signs in; the port does")

    override suspend fun restore(): SessionState {
        restoreCalls += 1
        if (failRestore) throw IllegalStateException("restore failed")
        // Published as well as returned, because the real port's contract says so: it adopts
        // the session on the device *and* announces it. A double that only returned the state
        // would make every restore test pass for the wrong reason — the coordinator would look
        // right while nothing had actually reached its listener.
        publish(restoreResult)
        return restoreResult
    }

    override suspend fun signOut() {
        signOutCalls += 1
        // A real port publishes here, and the coordinator's whole design is that it reacts
        // rather than tears down. Publishing is what makes the sign-out test meaningful.
        publish(SessionState.SignedOut)
    }

    override val sessionUpdates: Flow<SessionState> get() = states
}

/**
 * A metadata store that refuses every call.
 *
 * Exists to prove the teardown's ordering survives a store that throws: the session cache
 * must still be cleared, because a cache left holding the old identity is exactly what the
 * clear is for.
 */
internal class FailingMetadataStore : SyncMetadataStore {
    override suspend fun metadata(scope: String): SyncMetadata? = throw IllegalStateException("store unavailable")
    override suspend fun record(metadata: SyncMetadata): Unit = throw IllegalStateException("store unavailable")
    override suspend fun markSynced(scope: String, cursor: String?, at: Instant): Unit =
        throw IllegalStateException("store unavailable")

    override suspend fun markPushed(scope: String, at: Instant): Unit = throw IllegalStateException("store unavailable")
    override suspend fun recordFailure(scope: String, message: String): Unit = throw IllegalStateException("store unavailable")
    override suspend fun removeAll(): Unit = throw IllegalStateException("store unavailable")
}

/**
 * A session cache in a field.
 *
 * Recording rather than refusing, so a test can assert *what was written* — that the
 * coordinator cached the identity the session published, and with which author id.
 */
internal class RecordingSessionCache : SessionCache {

    /**
     * The last thing written, which [clear] deliberately does not erase.
     *
     * A record of writes rather than of present contents: the coordinator test wants to know
     * *what* it cached, and the teardown test wants to know the cache no longer holds it —
     * which is [current], not this.
     */
    var saved: CachedSession? = null
        private set
    var clearCalls: Int = 0
        private set

    private var stored: CachedSession? = null

    /** The cache's present contents, which is what "cleared" is about. */
    suspend fun held(): CachedSession? = current()

    override suspend fun current(): CachedSession? = stored

    override suspend fun save(session: CachedSession) {
        stored = session
        saved = session
    }

    override suspend fun clear() {
        clearCalls += 1
        stored = null
    }
}

/**
 * Metadata in a map, so a teardown's effect is observable.
 *
 * The store's interface is six methods because a real store has six jobs; the teardown only
 * calls [removeAll], and a double that implemented all six with a shared mutable map is more
 * useful than one that throws on the five a teardown must never call — a throwing double
 * would turn "teardown cleared too much" into a test failure for the wrong reason.
 */
internal class RecordingMetadataStore : SyncMetadataStore {

    private val rows = mutableMapOf<String, SyncMetadata>()

    var removeAllCalls: Int = 0
        private set

    fun put(scope: String, cursor: String?) {
        rows[scope] = blank(scope = scope, cursor = cursor)
    }

    fun scopes(): Set<String> = rows.keys.toSet()

    /** A non-suspending read, so a test can build an expected map without a coroutine. */
    fun metadataBlocking(scope: String): SyncMetadata? = rows[scope]

    /** A row with nothing but the two fields a pull position consists of. */
    private fun blank(scope: String, cursor: String? = null) = SyncMetadata(
        scope = scope,
        cursor = cursor,
        lastSyncedAt = null,
        lastPushedAt = null,
        serverVersion = 0,
        lastError = null,
    )

    override suspend fun metadata(scope: String): SyncMetadata? = rows[scope]

    override suspend fun record(metadata: SyncMetadata) {
        rows[metadata.scope] = metadata
    }

    override suspend fun markSynced(scope: String, cursor: String?, at: Instant) {
        // Rebuilt rather than `copy`ed: `SyncMetadata` is a class with hand-written equality,
        // not a data class, so `copy` does not exist. Only [SyncMetadata.cursor] matters here.
        rows[scope] = blank(scope = scope, cursor = cursor)
    }

    override suspend fun markPushed(scope: String, at: Instant) = Unit

    override suspend fun recordFailure(scope: String, message: String) = Unit

    override suspend fun removeAll() {
        removeAllCalls += 1
        rows.clear()
    }
}

/** A fixed instant, so a cached session's timestamp is an assertion rather than a clock read. */
internal class FixedDateProvider(private val instant: Instant = Clock.System.now()) :
    DateProvider {
    override suspend fun now(): Instant = instant
}

/** Unused here, but named so the import block does not carry a resolver nobody resolves through. */
private typealias UnusedResolver = Resolver
/**
 * A connectivity watcher this test drives.
 *
 * The counterpart to [RecordingSessionPort]: the monitor publishes from a callback, so a test
 * decides when connectivity "happens" rather than waiting for a platform. [emit] before
 * [NetworkMonitor.start] is deliberate — a status that arrives with nothing attached is
 * dropped, which is the behaviour [start] exists to prevent.
 */
internal class FakeWatcher : ConnectivityWatcher {

    private var onStatus: ((Connectivity) -> Unit)? = null
    private var running = false

    fun emit(status: Connectivity) {
        onStatus?.invoke(status)
    }

    override fun attach(onStatus: (Connectivity) -> Unit) {
        this.onStatus = onStatus
    }

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }
}

/**
 * A store whose writes a teardown can be seen to have made.
 *
 * Shared by the coordinator and teardown tests because both need to answer the same
 * question from opposite ends: the coordinator test asks "was anything written when the
 * session went live", and the teardown test asks "was everything removed when it ended".
 */
internal class InMemoryStores {

    val metadata = RecordingMetadataStore()
    val session = RecordingSessionCache()

    /** Puts the caches in a state that looks like a signed-in app mid-cycle. */
    fun seed() {
        metadata.put(scope = "projects", cursor = "page-7")
        metadata.put(scope = "workItems", cursor = "page-9")
    }

    fun cursors(): Map<String, String?> =
        metadata.scopes().associateWith { scope -> metadata.metadataBlocking(scope)?.cursor }

    /** The real [SessionTeardown] over this harness's stores, for the coordinator tests. */
    fun teardown(resolver: Resolver, scope: kotlinx.coroutines.CoroutineScope): SessionTeardown =
        SessionTeardown(
            resolver = resolver,
            metadata = metadata,
            sessionCache = session,
            syncCoordinator = silentSyncCoordinator(scope = scope),
        )
}

/**
 * A resolver that answers nothing, for a teardown whose only container call is
 * `resetSessionScope`.
 *
 * A real container is used by `SessionTeardownTest`; this exists because the coordinator
 * tests are about routing, and standing up a container to watch it being reset would be a
 * second subject in every one of them.
 */
internal class NoResolver(
    var resetCalls: Int = 0,
) : Resolver {

    override val isStarted: Boolean get() = true
    override val registeredNames: List<String> get() = emptyList()

    override fun <T : Any> resolve(klass: kotlin.reflect.KClass<T>): T =
        throw DiException.Unregistered(klass.simpleName ?: "<anonymous>")

    override fun <T : Any> getOrNull(klass: kotlin.reflect.KClass<T>): T? = null

    override fun <T : Any> forceResolve(klass: kotlin.reflect.KClass<T>): T =
        throw DiException.Unregistered(klass.simpleName ?: "<anonymous>")

    override fun resetSessionScope() {
        resetCalls += 1
    }

    override fun invalidateInstances() = Unit
    override fun stop() = Unit
    override fun isRegistered(klass: kotlin.reflect.KClass<*>): Boolean = false
    override fun start() = Unit
}

/**
 * An outbox with nothing in it and no way to get anything.
 *
 * Every method is a real implementation of "nothing", rather than a throw, because the
 * teardown's own invariant is that the outbox is *not* touched — so a double that threw
 * would make the "kept" assertion pass for the wrong reason if a future change reached it.
 */
internal class EmptyOutbox : OutboxQueue {
    override suspend fun claim(limit: Int, maxAttempts: Int, userID: String?): List<OutboxEntry> =
        emptyList()

    override suspend fun complete(ids: List<String>) = Unit
    override suspend fun fail(ids: List<String>, message: String, maxAttempts: Int) = Unit
    override suspend fun recoverStrandedEntries(): Int = 0
    override suspend fun release(ids: List<String>, message: String) = Unit
    override suspend fun entries(): List<OutboxEntry> = emptyList()
    override suspend fun count(status: OutboxStatus?): Int = 0
    override suspend fun removeAll() = Unit
    override suspend fun enqueue(entry: NewOutboxEntry) = Unit
}

/**
 * A real sync coordinator with nothing to sync.
 *
 * A real coordinator over a real engine, an empty outbox and the in-memory transport, rather
 * than a subclass or a stand-in: `stop` is the collaborator teardown calls, and only the real
 * one can show that calling it on a coordinator that never ran is safe — which it must be,
 * because a teardown can follow a cold launch where nothing started.
 *
 * [SyncSchedule.manual] so nothing fires on its own; the coordinator is never started in these
 * tests, it is only ever stopped.
 */
internal fun silentSyncCoordinator(scope: CoroutineScope): SyncCoordinator = SyncCoordinator(
    engine = SyncEngine(
        outbox = EmptyOutbox(),
        metadata = RecordingMetadataStore(),
        transport = InMemorySyncTransport(),
    ),
    monitor = NetworkMonitor(FakeWatcher()),
    schedule = SyncSchedule.manual,
    scope = scope,
)
