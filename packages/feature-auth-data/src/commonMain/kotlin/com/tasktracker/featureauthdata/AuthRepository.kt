package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JWT
import com.tasktracker.core.parseUserID
import com.tasktracker.core.randomUserID
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The session, as one object.
 *
 * Sits between the vault (which owns secrets) and the app (which decides what else a
 * sign-out should tear down). Its job is the ordering that makes a session trustworthy:
 *
 * - **Sign in** retains the tokens *before* publishing `signedIn`, so a session is never
 *   announced that cannot be replayed after a relaunch.
 * - **A failed sign-in touches nothing.** The previous state and the existing tokens are
 *   left exactly as they were, so a mistyped password cannot sign a working user out.
 * - **Sign out** clears the credentials, then publishes `signedOut`. The other order would
 *   let an observer rebuild something the wipe had just removed.
 *
 * The identity comes from the access token's own claims rather than from a stored copy of
 * the profile. A token's `sub` is the server's account id, so deriving it is both simpler
 * and safer than a second copy that can disagree with the credential it describes. The
 * cached profile is kept only as a fallback, for the shell's benefit when a token has
 * expired past readability but the session is still good.
 *
 * ## Why this is a class and not an `actor`
 *
 * The original was a Swift actor. The ordering guarantees above do not need one: every
 * mutation here is a short sequence of local calls plus a publish, and the only state this
 * type holds is the bus and the two collaborators. Serialising them would mean holding a
 * lock across a network sign-in, which is precisely the thing the vault is careful not to
 * do. Two concurrent sign-ins is the one real race, and it resolves the way a user expects:
 * the later publish wins, and both have retained their own tokens by then.
 */
class AuthRepository(
    private val vault: TokenVault,
    private val remote: AuthRemotePort,
    private val defaults: UserDefaultsStore,
    private val bus: SessionStateBus,
) : AuthSessionPort {

    // MARK: - AuthSessionPort

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signIn(email: String, password: String): UserIdentity {
        val tokens = remote.signIn(email = email, password = password)
        // Retained before announced. If the keychain write throws, the caller sees a failure
        // and the state is untouched — the inverse order would publish a session that no
        // longer exists after a relaunch.
        vault.store(tokens)
        // A server that issues a token with no readable `sub` is malformed, but it is not a
        // reason to refuse the sign-in: the user authenticated, and the id is only used for
        // display and for scoping rows that do not exist yet.
        val identity = identityFrom(tokens.accessToken, fallbackEmail = email)
            ?: UserIdentity(id = randomUserID(), email = email)
        cache(identity)
        bus.send(SessionState.SignedIn(identity))
        return identity
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun restore(): SessionState {
        bus.send(SessionState.Restoring)

        if (!vault.hasStoredSession()) {
            bus.send(SessionState.SignedOut)
            return SessionState.SignedOut
        }

        // Prefer the token's own claims: they are the authority on who is signed in. A
        // cached profile is only a hint, and is used when the stored token has expired past
        // the point where it can still be read.
        val stored = vault.storedAccessToken()
        if (stored != null) {
            val identity = identityFrom(stored)
            if (identity != null) {
                cache(identity)
                bus.send(SessionState.SignedIn(identity))
                return SessionState.SignedIn(identity)
            }
        }

        defaults.value<UserIdentity>(DefaultsKey.CachedProfile)?.let { cached ->
            bus.send(SessionState.SignedIn(cached))
            return SessionState.SignedIn(cached)
        }

        // A refresh token with no readable access token and no cached profile is a
        // half-session. Signing out is the honest answer: the alternative is to claim a
        // signed-in user the app cannot name.
        signOut()
        return SessionState.SignedOut
    }

    /**
     * Revokes the token family on the server, then forgets the session here.
     *
     * ## The revoke is bounded, and its failure is no longer swallowed
     *
     * Both halves of that change are one decision. The revoke is the only part of a sign-out
     * that touches the network, and it is the part the user is waiting on: the default request
     * timeout is 30 seconds and the endpoint is idempotent, so the retry policy makes three
     * attempts — a blackholed host leaves the tap looking ignored for around ninety seconds,
     * with the screen still on Settings and nothing on it saying why. A deadline turns that
     * into a prompt answer.
     *
     * Not swallowing the failure is what lets the caller say *which* answer it got, and it is
     * why the local wipe below is no longer unconditional. The failure propagates before it,
     * so a session that could not be ended cleanly is still intact and the user chooses what
     * happens next. The previous behaviour — wiping anyway — accepted a possibly-live refresh
     * token on the server without ever telling them, which is the one thing a sign-out exists
     * to prevent.
     */
    @Throws(AppError::class, CancellationException::class)
    override suspend fun signOut() {
        val refreshToken = vault.storedRefreshToken()
        if (refreshToken != null) {
            revoke(refreshToken)
        }
        forget()
    }

    /**
     * Forgets the session here without asking the server anything.
     *
     * The way out a user reaches for after [signOut] has told them the revocation could not be
     * confirmed. Nothing about it can fail for want of a network, so it is also the path a
     * device with no backend has always effectively taken — see [AuthSessionPort.signOutLocally].
     */
    @Throws(AppError::class, CancellationException::class)
    override suspend fun signOutLocally() {
        forget()
    }

    /**
     * Asks the server to revoke the family, failing if it cannot be reached in time.
     *
     * `withTimeout` signals the deadline with [TimeoutCancellationException], which *is* a
     * [CancellationException] — so it is translated rather than left to propagate. A caller
     * that saw a bare cancellation would treat a slow server as a cancelled screen, and the
     * whole point of the deadline is that it is a result.
     */
    private suspend fun revoke(refreshToken: String) {
        try {
            withTimeout(SIGN_OUT_REVOKE_TIMEOUT) { remote.signOut(refreshToken) }
        } catch (_: TimeoutCancellationException) {
            throw AppError.Timeout
        }
    }

    /**
     * The local half, shared by both ways of ending a session.
     *
     * Clears the credentials and *then* publishes, for the reason the class note gives: the
     * other order would let an observer rebuild something this wipe had just removed.
     */
    private suspend fun forget() {
        vault.clear()
        defaults.remove(DefaultsKey.CachedProfile)
        defaults.remove(DefaultsKey.CachedUserEmail)
        bus.send(SessionState.SignedOut)
    }

    override val sessionUpdates: Flow<SessionState> get() = bus.states()

    // MARK: - Internals

    /**
     * Reads the identity out of a token. Returns `null` when the token has no readable
     * claims, which the caller treats as "ask the cache instead" rather than "guess".
     */
    private fun identityFrom(accessToken: String, fallbackEmail: String? = null): UserIdentity? {
        val claims = JWT.parse(accessToken) ?: return null
        if (claims.subject.isEmpty()) return null
        val identifier = parseUserID(claims.subject) ?: return null
        return UserIdentity(
            id = identifier,
            email = claims.email ?: fallbackEmail ?: "",
            displayName = null,
        )
    }

    private suspend fun cache(identity: UserIdentity) {
        defaults.set(identity, DefaultsKey.CachedProfile)
        // Also persist the email alone so the settings service can seed [UserDetails.email]
        // without needing to know about [UserIdentity] or the auth module at all.
        defaults.set(identity.email, DefaultsKey.CachedUserEmail)
    }
}

/**
 * How long a sign-out waits for the server to revoke the refresh token family.
 *
 * Short on purpose: this is the one network call in the app the user is watching. The cost of
 * abandoning it is bounded, because the token expires on its own — which is the same outcome
 * the unbounded version settled for whenever the request failed, except that here the user is
 * told and gets to choose.
 */
internal val SIGN_OUT_REVOKE_TIMEOUT: Duration = 5.seconds