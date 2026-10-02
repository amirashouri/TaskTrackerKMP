package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthTokens
import com.tasktracker.network.Endpoint
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.jsonBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * The wire shapes for signing in.
 *
 * Private to the data layer: nothing outside it should care that the server happens to
 * answer with an `access_token` / `refresh_token` envelope rather than camelCase. That
 * translation is the remote's whole job, and it is also a reason these types are not the
 * domain's [AuthTokens] — a type that crosses a module boundary because it is convenient
 * rather than because the two layers agree on it is a type that will drift.
 */
@Serializable
private data class SignInRequestBody(
    val email: String,
    val password: String,
)

@Serializable
private data class RefreshRequestBody(
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * The two calls this remote makes, as [Endpoint] values.
 *
 * Declarative rather than built inline at each call site so the URL shapes, the
 * `requiresAuth` answer, and the replay decision are all visible in one place per call — and
 * so a reviewer can see that *both* auth calls opt into replay. That is the one property a
 * hand-built [HttpRequest] hides: `POST` is not replayable by default, so `isIdempotent = true`
 * has to be stated or the sign-in silently stops retrying a dropped connection.
 */
private class SignInEndpoint(private val payload: SignInRequestBody) : Endpoint {
    override val path: String = "/v1/auth/login"
    override val method: HttpMethod = HttpMethod.Post
    override val body: ByteArray? = jsonBody(payload)
    override val requiresAuth: Boolean = false

    /**
     * A retried login cannot half-apply. See [RefreshEndpoint.isIdempotent] for the argument
     * the pair shares.
     */
    override val isIdempotent: Boolean? = true
}

private class RefreshEndpoint(private val payload: RefreshRequestBody) : Endpoint {
    override val path: String = "/v1/auth/refresh"
    override val method: HttpMethod = HttpMethod.Post
    override val body: ByteArray? = jsonBody(payload)
    override val requiresAuth: Boolean = false

    /**
     * This one is the case the default would get wrong: a refresh is a `POST`, so the
     * safe-to-replay answer has to be given here or a dropped connection reads as a failed
     * sign-in.
     *
     * Replaying a refresh is safe because it either returns the same rotated pair or a fresh
     * one — never two live sessions, since the server invalidates the token it just consumed.
     */
    override val isIdempotent: Boolean? = true
}

/**
 * The token envelope the server answers with.
 *
 * [userID], [email] and [displayName] are optional and are decoded but then dropped: a
 * server may identify the account only inside the token's `sub` claim, and a second copy of
 * the profile in the response is a second source of truth that can disagree with the
 * credential. `AuthRepository` derives the identity from the token it was handed instead, so
 * the two cannot drift.
 */
@Serializable
private data class AuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user_id") val userID: String? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
) {
    fun tokens(): AuthTokens = AuthTokens(accessToken = accessToken, refreshToken = refreshToken)
}

/**
 * The real sign-in and refresh calls.
 *
 * Built on the *unauthenticated* client, and that is load-bearing rather than incidental:
 *
 * 1. A login request must not carry an `Authorization` header, and a refresh must not either
 *    — the server is being asked for a replacement precisely because the old token is not
 *    good enough.
 * 2. More importantly, routing these through the authenticated client would recurse. That
 *    client carries `AuthInterceptor`, so a `401` answering a refresh would trigger another
 *    refresh, which would `401` again, until the client's single replay ran out and the
 *    session was torn down — a sign-in that reports "session expired".
 *
 * @param client a client with **no interceptors**. The composition root builds a second,
 *   plain client for exactly this.
 */
class HttpAuthRemote(private val client: HttpClient) : AuthRemotePort {

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signIn(email: String, password: String): AuthTokens =
        client.send(
            SignInEndpoint(SignInRequestBody(email = email, password = password)).request,
            decoding = AuthTokenResponse.serializer(),
        ).tokens()

    @Throws(AppError::class, CancellationException::class)
    override suspend fun refresh(refreshToken: String): AuthTokens =
        client.send(
            RefreshEndpoint(RefreshRequestBody(refreshToken = refreshToken)).request,
            decoding = AuthTokenResponse.serializer(),
        ).tokens()
}
