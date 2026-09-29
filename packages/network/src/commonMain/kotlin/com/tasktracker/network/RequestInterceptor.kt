package com.tasktracker.network

/** What an interceptor wants the client to do with a response it has seen. */
enum class InterceptorDecision {
    /** Return the response as it is. */
    Proceed,

    /** Replay the *original* request. Ignored if the client has already used its replay. */
    Retry,
}

/**
 * Hooks into a request's lifecycle.
 *
 * Interceptors are the mechanism behind the auth flow: [AuthInterceptor] attaches the
 * bearer token, and on a `401`/`403` decides whether the call can be replayed with a fresh
 * token or whether the session must end.
 */
interface RequestInterceptor {

    /**
     * Called immediately before the request leaves, possibly several times for the same
     * logical call — once per replay. Throwing aborts the request.
     */
    suspend fun adapt(request: HttpRequest): HttpRequest = request

    /**
     * Called after a response arrives. [InterceptorDecision.Retry] replays the *original*
     * request; the client will not replay more than once per call.
     *
     * @param hasReplayed `true` when the client has already spent its single replay, so a
     *   [InterceptorDecision.Retry] would be discarded anyway. An interceptor should use
     *   this to react *differently* rather than return a decision that gets thrown away —
     *   [AuthInterceptor] treats a second `401` as proof the session is dead and ends it,
     *   instead of refreshing a third time.
     */
    suspend fun retry(
        request: HttpRequest,
        response: HttpResponseSummary,
        hasReplayed: Boolean,
    ): InterceptorDecision = InterceptorDecision.Proceed
}

/**
 * What ended the session. Every one of these routes to the same place — the router resets
 * to the auth flow — but the value is logged so support can tell a deliberate sign-out
 * from an expiry from a rejected token.
 */
enum class SessionInvalidationReason(val wireName: String) {
    /** The user tapped Sign Out. */
    UserLogout("userLogout"),

    /** The server answered `403 Forbidden`. */
    Forbidden("forbidden"),

    /** The access token expired and the refresh token was rejected. */
    RefreshRejected("refreshRejected"),

    /** The user has no local credentials at all. */
    NoCredentials("noCredentials"),

    /** The server answered `401` and a refresh was not possible. */
    Unauthorized("unauthorized"),
}

/**
 * The contract this package needs from the auth feature.
 *
 * Declared here, in the layer that consumes it, so `:packages:network` never depends on
 * `:packages:feature-auth-data`. The dependency points the other way: `TokenVault` in the
 * auth data module conforms to this, and `:packages:di` hands it to [AuthInterceptor].
 */
interface AuthSessionDelegate {

    /**
     * Returns a usable access token, refreshing first if the current one is expired or
     * within the safety margin. Implementations must be safe to call concurrently and must
     * collapse concurrent misses into a single refresh.
     *
     * @param forceRefresh refresh even if the cached token still looks valid. Set on a
     *   `401`, because the server knows something the client does not.
     */
    suspend fun validAccessToken(forceRefresh: Boolean): String

    /**
     * Called when the session is definitively unusable. Must not throw, and must not itself
     * try to refresh.
     */
    suspend fun invalidateSession(reason: SessionInvalidationReason)
}
