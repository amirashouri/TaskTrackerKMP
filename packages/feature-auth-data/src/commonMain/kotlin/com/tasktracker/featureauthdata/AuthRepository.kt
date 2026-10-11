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
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

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

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signOut() {
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