package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.database.OutboxEntry
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer

/**
 * A server the test scripts.
 *
 * Observation goes through [MutableStateFlow]s rather than plain `var`s: the engine calls
 * this from a coroutine, and the interesting tests read the recorded calls *while* a cycle
 * is paused inside `push`, so a list field read across that boundary is a race the suite
 * would only catch sometimes.
 */
class StubSyncTransport : SyncTransport {

    /** How the stub answers a push. */
    sealed interface PushBehaviour {
        data object Accept : PushBehaviour

        /** One verdict for every change. */
        data class Verdict(val outcome: PushOutcome) : PushBehaviour

        /** A verdict per change id; anything unlisted is accepted. */
        data class PerEntry(val byID: Map<String, PushOutcome>) : PushBehaviour

        /** Answer with nothing at all, as a server that lost the request would. */
        data object Silent : PushBehaviour

        data class Fail(val error: AppError) : PushBehaviour
    }

    private val mutex = Mutex()
    private var behaviour: PushBehaviour = PushBehaviour.Accept
    private val pages = mutableMapOf<SyncScope, MutableList<PullPage>>()
    private var pullError: AppError? = null
    private val batchesFlow = MutableStateFlow<List<List<String>>>(emptyList())
    private val pullsFlow = MutableStateFlow<List<PullRequest>>(emptyList())
    private val pausedFlow = MutableStateFlow(false)
    // `null` is an open gate; a deferred is a closed one that [release] completes. A
    // `MutableStateFlow` rather than a plain flag so a coroutine can *wait* on the transition
    // without polling the flag.
    private val gate = MutableStateFlow<CompletableDeferred<Unit>?>(null)

    /** One pull the engine asked for, kept whole because the cursor is the point. */
    data class PullRequest(val scope: SyncScope, val cursor: String?, val limit: Int)

    val pushCallCount: Int get() = batchesFlow.value.size
    val pushedIDs: List<String> get() = batchesFlow.value.flatten()
    val batches: List<List<String>> get() = batchesFlow.value
    val pullRequests: List<PullRequest> get() = pullsFlow.value

    val isPaused: Boolean get() = pausedFlow.value

    // MARK: Scripting

    suspend fun setPushBehaviour(behaviour: PushBehaviour) = mutex.withLock { this.behaviour = behaviour }

    suspend fun setPages(pages: List<PullPage>, scope: SyncScope) = mutex.withLock {
        this.pages[scope] = pages.toMutableList()
    }

    suspend fun setPullError(error: AppError?) = mutex.withLock { pullError = error }

    // MARK: SyncTransport

    override suspend fun push(entries: List<OutboxEntry>): List<PushResult> {
        batchesFlow.value = batchesFlow.value + listOf(entries.map { it.id })
        val closed = gate.value
        if (closed != null) {
            pausedFlow.value = true
            closed.await()
            pausedFlow.value = false
        }
        return when (val current = currentBehaviour()) {
            PushBehaviour.Accept -> entries.map { PushResult.accepted(it.id) }
            is PushBehaviour.Verdict -> entries.map { PushResult(it.id, current.outcome) }
            is PushBehaviour.PerEntry -> entries.map {
                PushResult(it.id, current.byID[it.id] ?: PushOutcome.Accepted)
            }

            PushBehaviour.Silent -> emptyList()
            is PushBehaviour.Fail -> throw current.error
        }
    }

    override suspend fun pull(scope: SyncScope, cursor: String?, limit: Int): PullPage {
        pullsFlow.value = pullsFlow.value + PullRequest(scope, cursor, limit)
        pullError()?.let { throw it }
        val remaining = remainingPages(scope)
        if (remaining.isEmpty()) return PullPage.empty
        return mutex.withLock { pages.getValue(scope).removeAt(0) }
    }

    // MARK: Single-flight gate

    /** Blocks inside `push` until [release] is called, so a test can observe the window in
     * which a cycle is genuinely in flight. */
    fun pauseOnPush() {
        gate.value = CompletableDeferred()
    }

    /** Suspends until something calls [release]. */
    suspend fun waitUntilPaused() {
        while (!pausedFlow.value) kotlinx.coroutines.yield()
    }

    fun release() {
        gate.value?.complete(Unit)
        gate.value = null
    }

    private suspend fun currentBehaviour(): PushBehaviour = mutex.withLock { behaviour }

    private suspend fun pullError(): AppError? = mutex.withLock { pullError }

    private suspend fun remainingPages(scope: SyncScope): List<PullPage> =
        mutex.withLock { pages[scope]?.toList() ?: emptyList() }

}

/** Stands in for a feature's local store. */
class RecordingSink(private val failure: AppError? = null) : SyncApplySink {
    private val appliedFlow = MutableStateFlow<List<RemoteChange>>(emptyList())

    val applied: List<RemoteChange> get() = appliedFlow.value
    val appliedIDs: List<String> get() = appliedFlow.value.map { it.entityID }

    override suspend fun apply(change: RemoteChange) {
        failure?.let { throw it }
        appliedFlow.value = appliedFlow.value + change
    }
}

/**
 * Captures the request a transport built and replays a canned body.
 *
 * Mirrors `HttpSyncTransportTests`' `StubHTTPClient` in the original, including decoding
 * with the shared `JSONCoding` so a transport bug is not mistaken for an encoding
 * difference.
 */
class StubHttpClient(
    private var stubbedBody: ByteArray = ByteArray(0),
    private var error: AppError? = null,
) : HttpClient {
    private val requestsFlow = MutableStateFlow<List<HttpRequest>>(emptyList())

    val requests: List<HttpRequest> get() = requestsFlow.value

    fun setResponse(body: ByteArray) {
        stubbedBody = body
    }

    fun setError(error: AppError?) {
        this.error = error
    }

    override suspend fun send(request: HttpRequest): ByteArray {
        requestsFlow.value = requestsFlow.value + request
        error?.let { throw it }
        return stubbedBody
    }

    override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T {
        val data = send(request)
        return try {
            // The shared configuration, not a bare `Json`: the real client decodes with it,
            // and a stub that was stricter or laxer would turn a transport bug into a
            // decoding "success".
            JSONCoding.json.decodeFromString(decoding, data.decodeToString())
        } catch (failure: Exception) {
            throw AppError.Decoding(failure.message ?: "Could not decode the response.")
        }
    }

    override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
        requestsFlow.value = requestsFlow.value + request
        error?.let { throw it }
    }
}
