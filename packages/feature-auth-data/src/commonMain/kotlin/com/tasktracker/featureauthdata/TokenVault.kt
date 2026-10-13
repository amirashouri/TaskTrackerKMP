package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.JWT
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthTokens
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.keychain.KeychainError
import com.tasktracker.keychain.KeychainKey
import com.tasktracker.keychain.KeychainStore
import com.tasktracker.keychain.set
import com.tasktracker.keychain.value
import com.tasktracker.network.AuthSessionDelegate
import com.tasktracker.network.SessionInvalidationReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the two tokens, and every decision about whether they are still usable.
 *
 * This is the type `:packages:network`'s `AuthInterceptor` has been waiting for: it conforms
 * to [AuthSessionDelegate], which is why requests can finally carry a bearer token.
 *
 * The vault holds tokens and nothing else. It does not know who the user is — that lives in
 * the repository, in user defaults rather than the keychain, which is the split
 * `DefaultsKey.CachedProfile` was named for. Keeping secrets in one place and profile data
 * in another is what lets the profile be read before the store is opened, and lets the
 * keychain be wiped wholesale on sign-out without losing the display name.
 *
 * ## The two locks, and why not one
 *
 * The original was a Swift actor, which meant every method was mutually exclusive — but
 * because `await` releases an actor's executor, its `Task.detached` refresh did *not* hold
 * the actor, and the explicit `refreshInFlight` was what stopped eight concurrent misses
 * becoming eight refreshes. A naive port to a single `Mutex` would instead hold it across the
 * network call. That is correct, and wrong in a way that shows up in the app: a tap on Sign
 * Out would block for the length of a slow refresh, on the one path where the user is waiting
 * for a response to a gesture.
 *
 * So the two concerns get a lock each:
 *
 * - [state] guards the cached tokens and [revision] — held only for reads and writes, and
 *   never across the network.
 * - [refresh] serialises the refresh round-trip itself, which is what collapses concurrent
 *   misses: the first caller in does the work, the rest find a changed [revision] and adopt
 *   its result.
 *
 * A sign-out therefore proceeds while a refresh is in flight, and the refresh discards its
 * own result on the way back (see [refreshedTokens]) rather than resurrecting a session the
 * user just ended. That is the behaviour the original got from cancelling the detached task;
 * what differs is that the in-flight HTTP call is not itself cancelled, so a refresh already
 * on the wire finishes and is thrown away.
 *
 * **Lock order is [refresh] then [state], never the reverse, and never nested.** No public
 * method takes both. That is what keeps [state] free for a sign-out while a refresh holds
 * [refresh], and it is why the code below reads the token fields directly under [state]
 * instead of calling [currentAccessToken] from inside it — a `Mutex` is not reentrant, and
 * a nested `withLock` on the same one would deadlock rather than fail.
 */
class TokenVault(
    private val keychain: KeychainStore,
    private val remote: AuthRemotePort,
    private val bus: SessionStateBus,
    private val dateProvider: DateProvider,
) : AuthSessionDelegate {

    private val state = Mutex()
    private val refresh = Mutex()

    /**
     * A fast path for the in-process case, not the source of truth. The keychain is.
     *
     * These caches start empty on every launch, so any read that trusted them alone would
     * conclude there is no session and refresh against a token it never loaded. Every read
     * goes through [currentAccessToken] / [currentRefreshToken], which fall back to the
     * keychain and only then repopulate these.
     */
    private var accessToken: String? = null
    private var refreshToken: String? = null

    /**
     * Bumped by every event that changes the answer to "what token is current": a
     * successful refresh, a fresh sign-in, and a wipe.
     *
     * This is the KMP stand-in for the original's `refreshInFlight: Task?`, and it carries
     * two responsibilities the task did. It tells a caller queued behind a refresh that the
     * work it was about to do is already done, and it tells a refresh that returned late
     * that whatever it fetched belongs to a session that no longer exists.
     */
    private var revision: Long = 0

    // MARK: - AuthSessionDelegate
    //
    // No `@Throws` on the two overrides, and that is a compiler rule rather than an
    // omission: Kotlin/Native rejects an override whose `@Throws` filter differs from the one
    // on the interface member, and `AuthSessionDelegate` declares none. A JVM-only build
    // compiles the identical annotation without complaint, so this is exactly the kind of
    // mistake that reaches `./gradlew check` rather than the module you were working in.

    override suspend fun validAccessToken(forceRefresh: Boolean): String {
        if (!forceRefresh) {
            currentAccessToken()?.let { token ->
                if (!isExpiringSoon(token)) return token
            }
        }
        return refreshedTokens().accessToken
    }

    /**
     * Called by `AuthInterceptor` when the session is definitively unusable. Must not throw
     * and must not attempt a refresh — the server has already said no.
     */
    override suspend fun invalidateSession(reason: SessionInvalidationReason) {
        TTLogger.auth.warning {
            logMessage {
                append("auth: invalidating session (")
                append(reason.wireName)
                append(")")
            }
        }
        clear()
        bus.send(SessionState.SignedOut)
    }

    // MARK: - Session lifecycle

    /**
     * Retains a freshly issued pair. Called after a successful sign-in, before the session is
     * reported as live, so a session is never announced that cannot be replayed.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun store(tokens: AuthTokens) {
        state.withLock {
            // The refresh token goes in first, then the access token. The reverse order leaves
            // a half-written pair behind if the second write throws — an access token with no
            // refresh token to renew it, which restores as a signed-in user who cannot make a
            // single request. A refresh token with no access token is merely inert.
            persist(tokens)
            accessToken = tokens.accessToken
            refreshToken = tokens.refreshToken
            // Bumped, so a refresh that was already on the wire for the *previous* session
            // is discarded when it lands rather than overwriting these.
            revision += 1
        }
    }

    /**
     * Whether a refresh token is on the device. Read from the keychain rather than from
     * memory, so a relaunch — where nothing has been loaded yet — answers honestly.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun hasStoredSession(): Boolean = !currentRefreshToken().isNullOrEmpty()

    /**
     * The access token already on the device, without refreshing or validating it.
     *
     * Read so a relaunch can rebuild the signed-in identity from the token that is actually
     * in play. Deriving it from the token rather than from a cached profile is what keeps a
     * stale cache from resurrecting an account the user has since dropped: a cache can
     * disagree with the keychain, and the keychain is the authority.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun storedAccessToken(): String? = currentAccessToken()

    /**
     * The refresh token already on the device, without refreshing or validating it.
     *
     * Read so sign-out can pass it to the server, which revokes the token family and
     * prevents the session from being resumed by anyone who gains access to the device
     * after sign-out.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun storedRefreshToken(): String? = currentRefreshToken()?.takeIf { it.isNotEmpty() }

    /**
     * Forgets everything. Does not publish: callers decide whether this is a sign-out or an
     * incidental clear, and a wipe triggered by a `403` is not the same event as a tap.
     */
    suspend fun clear() {
        state.withLock {
            accessToken = null
            refreshToken = null
            revision += 1
            try {
                keychain.removeAll()
            } catch (_: KeychainError) {
                // A failure to wipe is not reported: there is nothing to report it *to*. The
                // tokens are already unreachable through this object, which is the only thing
                // this method promises, and the original swallowed it for the same reason.
                TTLogger.auth.error {
                    logMessage {
                        append("auth: keychain wipe failed; the session is unreachable anyway")
                    }
                }
            }
        }
    }

    // MARK: - Refresh

    /**
     * Collapses every concurrent miss into a single refresh round-trip.
     *
     * The first caller in takes [refresh] and does the work. The rest queue behind it, then
     * find [revision] has moved since they looked and adopt the token the winner stored. One
     * request, and no caller left holding a token the server has already invalidated.
     */
    @Throws(AppError::class, CancellationException::class)
    private suspend fun refreshedTokens(): AuthTokens {
        val revisionWhenCalled = state.withLock { revision }

        // Written as one expression rather than with `return@withLock`, and that is not a
        // style preference: Kotlin/Native miscompiles a labelled return out of `Mutex.withLock`
        // when the lambda's result is not `Unit`, handing the caller back a `Unit` that fails
        // to cast to `AuthTokens` at runtime. A JVM build passes. So the branches yield a value
        // and the lambda ends on an expression.
        return refresh.withLock {
            val revisionOnEntry = state.withLock { revision }
            if (revisionOnEntry != revisionWhenCalled) {
                // A refresh completed while this caller was queueing, or a sign-in landed.
                // Either way the answer already exists, and re-refreshing would spend a
                // refresh token the server has probably just invalidated.
                adoptedTokens()
            } else {
                val stored = currentRefreshToken()?.takeIf { it.isNotEmpty() }
                    ?: throw AppError.Unauthorized

                val tokens = remote.refresh(stored)

                state.withLock {
                    // The check and the write share one critical section on purpose. Separated,
                    // a sign-out landing between them would be undone by the write below, which
                    // is the exact resurrection this is meant to prevent.
                    if (revision != revisionOnEntry) {
                        // Discard. Whatever is on the device now belongs to a newer decision —
                        // and if that decision was a wipe, there is no session left to adopt.
                        adoptedTokens()
                    } else {
                        persist(tokens)
                        accessToken = tokens.accessToken
                        refreshToken = tokens.refreshToken
                        revision += 1
                        tokens
                    }
                }
            }
        } ?: throw AppError.Unauthorized
    }

    /**
     * The cached pair, or `null` if either half is missing. Callers must already hold
     * [state] — it reads the fields directly rather than going through
     * [currentAccessToken], which would nest a `withLock` on a non-reentrant mutex.
     *
     * Reading the cache rather than the keychain is sufficient here because every path that
     * reaches this either has already read a token through the caching accessors on this
     * launch, or has just wiped them.
     */
    private fun adoptedTokens(): AuthTokens? {
        val access = accessToken ?: return null
        val refresh = refreshToken ?: return null
        return AuthTokens(accessToken = access, refreshToken = refresh)
    }

    /**
     * Writes both halves to the keychain. Callers must hold [state].
     *
     * A keychain failure becomes [AppError.Storage] rather than escaping as
     * [KeychainError]: the port this class satisfies is declared in terms of [AppError] and
     * nothing above the data layer may name a keychain type.
     */
    private suspend fun persist(tokens: AuthTokens) {
        try {
            keychain.set(tokens.refreshToken, KeychainKey.RefreshToken)
            keychain.set(tokens.accessToken, KeychainKey.AccessToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (keychainFailure: KeychainError) {
            throw AppError.Storage("Could not retain the session: ${keychainFailure.reason}")
        }
    }

    /**
     * The memory cache, falling back to the keychain the first time a launch needs it.
     *
     * Populating the cache on first read is what keeps the interceptor's per-request lookup
     * off the keychain: one read per launch, then a memory hit.
     *
     * A keychain read failure is `null` rather than a throw. The distinction is deliberate
     * and asymmetric with [persist]: reading a token is a fast path with a slow path behind
     * it, and a keystore that cannot be read right now is indistinguishable, from here, from
     * a keystore with nothing in it — both mean "there is no usable session". Failing to
     * *write* one is a correctness failure with nowhere to go.
     */
    private suspend fun currentAccessToken(): String? = cached(KeychainKey.AccessToken)

    /** See [currentAccessToken] — the same cache-then-keychain shape for the refresh token. */
    private suspend fun currentRefreshToken(): String? = cached(KeychainKey.RefreshToken)

    private suspend fun cached(key: KeychainKey): String? {
        val observed = state.withLock { revision to fieldOf(key) }
        if (observed.second != null) return observed.second

        // Read outside the lock, deliberately: a keychain round-trip is a platform call, and
        // holding [state] across it would make a sign-out wait on it.
        val stored = try {
            keychain.value<String>(key)
        } catch (_: KeychainError) {
            null
        }

        return state.withLock {
            // [revision] is what makes writing the cache back safe. An earlier version of this
            // claimed a concurrent write could only be overwritten by an identical value, which
            // is true of [store] and false of [clear]: a wipe landing while the read above was
            // in flight would null the fields and `removeAll()`, and the value that read then
            // brings back would repopulate the cache with a session the user just ended. It
            // would be handed straight back out by the interceptor, so the sign-out would
            // appear to work and then quietly not take.
            //
            // So if anything moved the session on, the cache is the newer truth and the value
            // the keychain returned is dropped — `null` after a wipe, the new token after a
            // sign-in.
            if (revision != observed.first) {
                fieldOf(key)
            } else {
                setField(key, stored)
                stored
            }
        }
    }

    /**
     * The in-memory half of [key]. Callers must hold [state] — a nested `withLock` on a
     * non-reentrant mutex would deadlock rather than fail.
     */
    private fun fieldOf(key: KeychainKey): String? = when (key) {
        KeychainKey.AccessToken -> accessToken
        KeychainKey.RefreshToken -> refreshToken
    }

    /** [fieldOf]'s counterpart. Callers must hold [state]. */
    private fun setField(key: KeychainKey, value: String?) {
        when (key) {
            KeychainKey.AccessToken -> accessToken = value
            KeychainKey.RefreshToken -> refreshToken = value
        }
    }

    /**
     * Fails closed: a token whose expiry cannot be read is treated as expired. Refreshing
     * unnecessarily costs one request; trusting an unreadable token costs a `401`, and on a
     * `403` it costs the session.
     */
    private suspend fun isExpiringSoon(token: String): Boolean {
        val expiry = JWT.expiry(token) ?: return true
        return expiry - dateProvider.now() <= EXPIRY_MARGIN
    }

    companion object {
        /**
         * How far ahead of the real expiry a token is treated as already expired.
         *
         * Without a margin, a token fetched with one second left is handed to a request that
         * then takes four seconds to cross a slow network, and the user sees a `401` for a
         * session that was valid when they tapped. A minute of early refresh costs at most
         * one extra refresh and removes that whole class of failure.
         */
        val EXPIRY_MARGIN: Duration = 60.seconds
    }
}
