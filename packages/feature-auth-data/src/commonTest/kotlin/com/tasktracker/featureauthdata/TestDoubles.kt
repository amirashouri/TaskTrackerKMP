package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JWT
import com.tasktracker.core.JSONCoding
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthTokens
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.keychain.KeychainAccess
import com.tasktracker.keychain.KeychainKey
import com.tasktracker.keychain.KeychainStore
import com.tasktracker.keychain.set
import kotlinx.coroutines.CompletableDeferred
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A scripted remote, so vault and repository tests can say exactly which token comes back
 * and how many times a refresh was asked for — which is the only way to prove the vault
 * collapses concurrent refreshes instead of merely appearing to.
 *
 * A class with a [Mutex] rather than an `actor`, for the reason `TokenVault` is written the
 * way it is: the interesting assertions are about *counts*, and those have to be readable
 * without a suspending accessor on the hot path.
 */
class ScriptedAuthRemote : AuthRemotePort {

    private val mutex = Mutex()
    private val signInScript: MutableList<Result<AuthTokens>> = mutableListOf()
    private val refreshScript: MutableList<Result<AuthTokens>> = mutableListOf()
    private var latency: Duration = Duration.ZERO

    var signInCount: Int = 0
        private set
    var refreshCount: Int = 0
        private set
    val seenRefreshTokens: MutableList<String> = mutableListOf()

    // MARK: Scripting

    /** Returned by each successive `signIn`, in order. */
    suspend fun setSignInResults(results: List<Result<AuthTokens>>) {
        mutex.withLock {
            signInScript.clear()
            signInScript += results
        }
    }

    /** Returned by each successive `refresh`, in order. */
    suspend fun setRefreshResults(results: List<Result<AuthTokens>>) {
        mutex.withLock {
            refreshScript.clear()
            refreshScript += results
        }
    }

    /**
     * Artificial latency, used to hold a refresh open long enough that concurrent callers
     * genuinely overlap instead of accidentally serialising.
     */
    suspend fun setRefreshDelay(delay: Duration) {
        mutex.withLock { latency = delay }
    }

    // MARK: AuthRemotePort

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signIn(email: String, password: String): AuthTokens {
        val result = mutex.withLock {
            signInCount += 1
            signInScript.removeFirstOrNull()
        } ?: throw AppError.Unknown("no scripted sign-in result")
        return result.getOrElse { throw it }
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun refresh(refreshToken: String): AuthTokens {
        // The latency is taken from under the lock rather than read from a field afterwards,
        // so a test that changes the delay between two refreshes cannot have the second read
        // the first one's value.
        val (result, wait) = mutex.withLock {
            refreshCount += 1
            seenRefreshTokens += refreshToken
            refreshScript.removeFirstOrNull() to latency
        }
        if (wait > Duration.ZERO) delay(wait)
        return (result ?: throw AppError.Unauthorized).getOrElse { throw it }
    }
}

/**
 * Builds a real, unsigned JWT carrying the claims the vault reads.
 *
 * The vault decides whether a token is good by parsing it, so a test handing it `"t1"` would
 * exercise only the fail-closed branch and quietly prove nothing about expiry or refresh.
 * These are structurally valid three-part tokens, signature and all.
 */
@OptIn(ExperimentalUuidApi::class)
object TestToken {

    const val DEFAULT_SUBJECT: String = "3F2504E0-4F89-41D3-9A0C-0305E82C3301"
    const val DEFAULT_EMAIL: String = "ada@example.com"

    /**
     * [issuedAt] is explicit rather than read from the system clock, so a test that pairs
     * this with a [TestDateProvider] can assert an exact expiry rather than a range.
     */
    fun make(
        subject: String = DEFAULT_SUBJECT,
        email: String = DEFAULT_EMAIL,
        type: String = "access",
        expiresIn: Long,
        issuedAt: Instant = Instant.fromEpochSeconds(1_760_000_000),
    ): String {
        fun segment(value: JsonObject): String =
            JWT.encodeBase64URL(value.toString().encodeToByteArray())

        val header = segment(
            buildJsonObject {
                put("alg", "none")
                put("typ", "JWT")
            },
        )
        val payload = segment(
            buildJsonObject {
                put("sub", subject)
                put("email", email)
                put("typ", type)
                put("iat", issuedAt.epochSeconds)
                put("exp", issuedAt.epochSeconds + expiresIn)
            },
        )
        // A trailing empty signature segment keeps the token three-part, which is what
        // `JWT.parse` insists on before it will read the claims.
        return "$header.$payload."
    }
}

/**
 * Reads a stream under a deadline, so a missed event fails a test instead of hanging it.
 *
 * The deadline is not optional. An unbounded collect over a stream that has gone quiet never
 * returns, and a test suite that hangs tells you nothing about which assertion was wrong.
 *
 * ## Why every helper here *starts* the collector
 *
 * `Broadcaster` registers a subscriber on collection rather than on stream creation, and
 * `SessionStateBus` deliberately drops a send with nobody listening. So the useful shape is
 * not "read what was published" but "start listening, then publish, then read" — and a
 * helper that took a `Flow` and returned its contents could only ever be used in the one
 * order that never works. These start collecting eagerly and hand back a `Deferred`.
 */
private val DEADLINE: Duration = 5.seconds

/**
 * Begins collecting [flow] now, on an unconfined dispatcher so the subscription is registered
 * before this returns.
 *
 * Await the result with [awaitFirst] rather than `.await()`, so a stream that never produces
 * yields `null` instead of failing the test with a `TimeoutCancellationException` whose
 * message points at the helper rather than at the assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> TestScope.awaitingFirst(flow: Flow<T>): Deferred<T> =
    backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { flow.first() }

/** The first element, or `null` if none arrives before the deadline. */
internal suspend fun <T> Deferred<T>.awaitFirst(deadline: Duration = DEADLINE): T? =
    withTimeoutOrNull(deadline) { await() }

/**
 * Captures the requests `HttpAuthRemote` builds and replays a canned token envelope.
 *
 * This is the only thing standing between the real remote and zero coverage: it has no test
 * of its own anywhere, because the app points it at a `.invalid` host, so without a stub the
 * one type that performs a real sign-in is the one type that is never exercised.
 *
 * The requests are kept rather than just answered so a test can assert on the wire shape —
 * path, method, and above all `requiresAuth` and `isIdempotent`, which are the two answers
 * that are invisible in the response and decide whether sign-in can work at all.
 */
class StubHttpClient(
    private var responseBody: ByteArray = ByteArray(0),
) : HttpClient {

    private val mutex = Mutex()
    val requests: MutableList<HttpRequest> = mutableListOf()

    suspend fun setResponse(body: ByteArray) {
        mutex.withLock { responseBody = body }
    }

    override suspend fun send(request: HttpRequest): ByteArray {
        val body = mutex.withLock {
            requests += request
            responseBody
        }
        return body
    }

    override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T {
        val body = send(request)
        return try {
            JSONCoding.json.decodeFromString(decoding, body.decodeToString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw AppError.Decoding(error.message ?: error::class.simpleName ?: "unreadable body")
        }
    }

    override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
        send(request)
    }
}

/** A keychain store over an in-memory access, for tests that want to inspect the raw bytes. */
fun inMemoryKeychainStore(access: InMemoryKeychain = InMemoryKeychain()): KeychainStore =
    KeychainStore(access)

/**
 * A keychain access a test can hold open *inside* a read, so a sign-out can be interleaved
 * with a keychain round-trip.
 *
 * The value is captured before the pause and returned after it, which is the whole point: it
 * reproduces a caller that read the token, got descheduled, and came back to find the store
 * wiped underneath it. Returning the *current* value after the pause would report `null` and
 * hide the bug.
 *
 * One read only. Every later read passes straight through, so a test resumes the flow it was
 * blocking rather than parking again on the next cache miss.
 */
class PausableKeychain(
    private val inner: InMemoryKeychain = InMemoryKeychain(),
) : KeychainAccess {

    private val reached = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()

    /** Seeds through a real [KeychainStore], so the encoding cannot drift from production. */
    suspend fun seed(tokens: AuthTokens) {
        KeychainStore(inner).apply {
            set(tokens.refreshToken, KeychainKey.RefreshToken)
            set(tokens.accessToken, KeychainKey.AccessToken)
        }
    }

    /** Suspends until a read has parked inside the access. */
    suspend fun awaitRead() = reached.await()

    /** Lets the parked read return the value it captured. */
    fun releaseRead() = released.complete(Unit)

    /** The keys still on the device, for asserting that a wipe really happened. */
    suspend fun keys(): List<String> = inner.keys()

    override suspend fun data(key: String): ByteArray? {
        val data = inner.data(key)
        if (reached.complete(Unit)) released.await()
        return data
    }

    override suspend fun set(data: ByteArray, key: String) = inner.set(data, key)

    override suspend fun remove(key: String) = inner.remove(key)

    override suspend fun removeAll() = inner.removeAll()
}
