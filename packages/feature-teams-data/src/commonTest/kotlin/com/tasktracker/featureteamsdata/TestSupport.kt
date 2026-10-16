package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.UserID
import com.tasktracker.core.randomUserID
import com.tasktracker.database.CachedSession
import com.tasktracker.database.SessionCache
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpRequest
import kotlinx.serialization.KSerializer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

internal class StubSessionCache(
    private var session: CachedSession? = null,
) : SessionCache {

    override suspend fun current(): CachedSession? = session

    override suspend fun save(session: CachedSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }

    /** Signs in a user and returns the id, so a test can assert on it. */
    fun signIn(
        userID: UserID = randomUserID(),
        email: String = "amir@example.com",
        displayName: String? = "Amir",
    ): UserID {
        session = CachedSession(
            userID = userID.stringValue,
            email = email,
            displayName = displayName,
            lastSignedInAt = Moments.start,
            serverVersion = 1,
        )
        return userID
    }
}

internal object Moments {
    val start: Instant = Instant.fromEpochSeconds(1_760_000_000)
}

/**
 * Captures the requests a data-layer remote builds and replays scripted responses.
 *
 * The same double `feature-auth-data`'s `StubHttpClient` is, and here for the same reason: the
 * app points the real client at a live host, so without a stub the one type that builds the
 * admin API's requests would have no coverage at all. Requests are kept rather than merely
 * answered so a test can assert the wire shape — method, path, the `status` query, and
 * `requiresAuth`, which is invisible in the response and decides whether a bearer token is
 * attached in the first place.
 *
 * Replies are a queue consumed in call order, with the last one repeating — the shape
 * `ScriptedAuthRemote` uses — so one test can drive several calls whose response bodies differ
 * (a list is an array, a create is an object). A call with nothing scripted gets an empty body,
 * which is what a `204` delete answers anyway.
 *
 * The decode path mirrors the real client's: an unknown key is an [AppError.Decoding] because
 * `JSONCoding` leaves `ignoreUnknownKeys` off, which is the behaviour a DTO's `@SerialName`
 * list exists to satisfy.
 */
internal class RecordingHttpClient : HttpClient {

    val requests: MutableList<HttpRequest> = mutableListOf()

    private val scripted: MutableList<ByteArray> = mutableListOf()

    /** Enqueues the next reply, consumed in call order. */
    fun respondWith(body: ByteArray) {
        scripted += body
    }

    fun respondWith(json: String) {
        respondWith(json.encodeToByteArray())
    }

    private fun nextBody(): ByteArray =
        if (scripted.size > 1) scripted.removeFirst() else scripted.firstOrNull() ?: ByteArray(0)

    override suspend fun send(request: HttpRequest): ByteArray {
        requests += request
        return nextBody()
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
