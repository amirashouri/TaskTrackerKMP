package com.tasktracker.featureauthdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Who is signed in.
 *
 * ## Why a bare ID was not enough
 *
 * The original tracked a `UserID` and a `Bool`, and rebuilt a display name from the
 * email every time one was needed. That put a string-mangling rule in the view layer,
 * where two screens could each grow their own slightly different version of it, and
 * the greeting on the lock screen stopped matching the greeting on the board.
 *
 * [preferredGreeting] is that rule, stated once, next to the only data it is derived
 * from. It is a computed property rather than a stored field so the two cannot drift:
 * nothing can construct a [UserIdentity] whose greeting disagrees with its parts.
 *
 * ## Why this is `@Serializable`
 *
 * The account's display name is a convenience the server owns but the device is allowed
 * to cache, because a sign-out request has to render "signing out as ..." on a device that
 * may be offline and therefore unable to ask. The data layer stores this exact type in
 * user defaults, so the wire form is a stable contract rather than a class layout. It
 * carries a UUID, and because `UserID` is a value class over `Uuid` kotlinx.serialization
 * treats it transparently — the identifier is a bare JSON string, matching what the server
 * already sends for a user id in every other payload.
 */
@Serializable
data class UserIdentity(
    val id: UserID,
    val email: String,
    val displayName: String? = null,
) {

    /**
     * What to call this person.
     *
     * A nonblank [displayName] wins; otherwise the part of the email before the `@`; and
     * failing that, the whole address — which then displays as-is rather than as an empty
     * string.
     *
     * `substringBefore` rather than a `split` on the delimiter is deliberate: on an address
     * with no `@` it returns the whole string, and on one with several it takes the text up
     * to the first, where `split`'s first element could be empty. The sign-in path cannot
     * produce such an address — [com.tasktracker.domain.AuthRules] rejects it first — but a
     * cached profile written by a future schema, or by an older app version, must not be
     * able to render a nameless greeting.
     */
    val preferredGreeting: String
        get() = when {
            !displayName.isNullOrEmpty() -> displayName
            else -> email.substringBefore('@')
        }
}

/**
 * The three states a session can be in, and the second and third are why this is a
 * [sealed interface] rather than a nullable [UserIdentity].
 *
 * [Restoring] is the state that a nullable model cannot express and that an app with a
 * keychain-backed session has to express. On launch the vault has tokens in it but has not
 * yet asked anyone whether they still work, and "signed out" would be a lie during those
 * few hundred milliseconds — a screen bound to it would render the sign-in prompt, and the
 * user would be offered to sign in again while a valid session is about to come back.
 * [Restoring] is the honest answer, and it is what lets the launch path show a spinner
 * instead of a flash of the wrong screen.
 *
 * A sealed interface rather than an enum with a payload field, because an enum cannot
 * carry a `UserIdentity` and the alternatives — parallel `isSignedIn` and `currentUser`
 * properties — are precisely the two-sources-of-truth bug the shape exists to prevent. A
 * `when` over the three cases is exhaustive at compile time, so a new state cannot be
 * added without every reader dealing with it.
 */
sealed interface SessionState {

    /** Nobody is signed in. The app's entry point. */
    data object SignedOut : SessionState

    /** A session may exist and is being checked. The launch state. Never a resting state. */
    data object Restoring : SessionState

    /** [identity] is signed in and the tokens behind it are known good. */
    data class SignedIn(val identity: UserIdentity) : SessionState
}

/** The convenience test every caller writes first, once, as an extension. */
val SessionState.isSignedIn: Boolean get() = this is SessionState.SignedIn

/** The signed-in account, or `null` in either other state. */
val SessionState.identityOrNull: UserIdentity? get() = (this as? SessionState.SignedIn)?.identity

/**
 * A pair of bearer credentials.
 *
 * Deliberately opaque: an access token and a refresh token are strings to everything above
 * this layer, and naming their contents here would invite a caller to parse one. The one
 * thing a caller may reasonably want is the refresh token, because a failed refresh is a
 * signed-out session, and [refreshToken] is there for the vault rather than for the UI.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * The network half of sign-in.
 *
 * A port rather than a concrete HTTP client, and this is the seam that lets the whole app run
 * with no server: `MockAuthRemote` implements it, and the tests implement it with a scripted
 * queue of outcomes. Without it, every test of session handling would need a live HTTP server
 * or a fake interceptor buried in the network module's internals.
 *
 * Every method is `suspend` and `@Throws(AppError, CancellationException)`, for the reason
 * spelled out on the use cases: Kotlin/Native rejects `@Throws` on a suspend function
 * unless cancellation is among the listed classes, and naming it states the real contract
 * — a cancelled sign-in is not a failed sign-in.
 */
interface AuthRemotePort {

    /**
     * Exchanges credentials for a credential pair.
     *
     * Note what this does *not* return: no identity. A server may identify the account only
     * inside the token's `sub` claim, and a copy of the profile in the response body is a
     * second source of truth that can disagree with the credential it describes. The
     * repository reads the identity out of the token it was handed instead, so the two
     * cannot drift.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun signIn(email: String, password: String): AuthTokens

    /**
     * Exchanges a refresh token for a fresh pair.
     *
     * The returned refresh token is not always the one passed in — a server that rotates
     * refresh tokens invalidates the one it just consumed — so the caller must adopt both
     * halves of [AuthTokens] rather than only the access token.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun refresh(refreshToken: String): AuthTokens

    /**
     * Asks the server to revoke a refresh token's family, ending the session server-side.
     *
     * The client clears its local state regardless of whether this call succeeds — the
     * device is the authority on whether the user is signed in on this device — but
     * calling the server prevents a stolen refresh token from being used after sign-out.
     * A failure (e.g. the server is unreachable) is acceptable: the token will expire on
     * its own, and the local wipe is immediate.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun signOut(refreshToken: String) {
        // Default: no-op. HttpAuthRemote overrides to call the server.
    }
}

/**
 * The session as the presentation layer sees it.
 *
 * Everything the sign-in screen needs and nothing it does not: no tokens, no keychain, no
 * HTTP. Signing out is here rather than left to the app because "forget the session" is a
 * rule the auth feature owns — the app decides what else to tear down.
 *
 * The domain declares this rather than the data layer, so that a screen's dependency is
 * expressed in terms it can reason about. The alternative — depending on `AuthRepository`
 * and having that class also hold a [Flow] — makes the mutation methods and the observation
 * method look equally available, and invites a screen to observe a state it has just
 * overwritten.
 */
interface AuthSessionPort {

    /**
     * Authenticates and, on success, retains the resulting session.
     *
     * A failure leaves the previous state untouched, so a failed attempt can never sign a
     * working user out.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun signIn(email: String, password: String): UserIdentity

    /**
     * Adopts a session already on the device, or leaves the state signed out.
     *
     * Returns the state it settled on, and publishes it on [sessionUpdates] too. Returning it
     * is the one deliberate departure from the original's `async` void, and it buys something
     * concrete: the caller that asked can act on the outcome without having had to subscribe
     * first, which is the only way a launch path can branch on "restored" versus "show the
     * sign-in screen". A void here would make the subscription mandatory and turn a
     * sequencing bug into a silent one.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun restore(): SessionState

    /**
     * Discards every retained credential and publishes [SessionState.SignedOut].
     *
     * Idempotent when already signed out.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun signOut()

    /**
     * State changes, hottest-last. A fresh stream per subscriber, dropping while nobody is
     * listening, and completing when the session layer is torn down.
     *
     * No replay: a subscriber that arrives late calls [restore] first, and a send with
     * nothing listening is dropped rather than buffered — see `SessionStateBus` for why
     * buffering would break the "subscribe, then restore" order that the launch path
     * depends on.
     */
    val sessionUpdates: Flow<SessionState>
}
