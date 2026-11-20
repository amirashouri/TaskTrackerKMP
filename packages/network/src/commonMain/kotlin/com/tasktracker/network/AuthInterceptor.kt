package com.tasktracker.network

import com.tasktracker.core.AppError
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import kotlinx.coroutines.CancellationException

/**
 * The 401/403 policy, in one place.
 *
 * Two rules matter here and both are easy to get subtly wrong:
 *
 * 1. **Never loop.** A `401` triggers at most one refresh and one replay. If the replay
 *    also comes back `401` or `403`, the session ends.
 * 2. **`403` is terminal.** Unlike `401`, a `403` means "this token is understood and
 *    refused" (wrong account, revoked, insufficient scope), so retrying with a fresh
 *    token cannot help.
 * 3. **A refused refresh ends the session wherever it happens.** [adapt] is where a
 *    relaunch with a dead refresh token first fails — there is no response yet, so
 *    [retry] never runs. Treating that as an ordinary error left the token in the
 *    keychain, retried the refresh every cycle and kept showing stale data instead of
 *    the sign-in screen. Transient failures (offline, timeout, 5xx) are not a sign-out.
 *
 * A `struct` in the original; a class holding one immutable reference here. Wrapping it in
 * a coroutine `Mutex` would be wrong — the delegate behind it does the concurrency
 * control, and a lock here would serialise every in-flight request behind one refresh.
 */
class AuthInterceptor(private val delegate: AuthSessionDelegate) : RequestInterceptor {

    override suspend fun adapt(request: HttpRequest): HttpRequest {
        if (!request.requiresAuth) return request
        // Sign-in and refresh must not try to read a session that does not exist yet.
        val token = try {
            delegate.validAccessToken(forceRefresh = false)
        } catch (cancellation: CancellationException) {
            // A cancelled screen must not end the session on its way out; the same guard
            // `retry` keeps, for the same reason.
            throw cancellation
        } catch (error: Throwable) {
            // A refresh the server refused is the session ending, and it has to be handled
            // here as well as in `retry`: a relaunch with a dead refresh token fails before
            // any request is sent, so there is no `401` response to react to. Left
            // unsignalled, the dead token stayed in the keychain, every sync cycle retried
            // the refresh, and the app kept rendering stale data instead of the sign-in
            // screen. Only a refusal ends the session — being offline is not being signed
            // out, and queued work must survive it.
            if ((error as? AppError)?.requiresSignOut == true) {
                TTLogger.auth.error {
                    logMessage {
                        append("network: refresh rejected before the request left -> ending session ")
                        append("(${SessionInvalidationReason.RefreshRejected.wireName})")
                    }
                }
                delegate.invalidateSession(SessionInvalidationReason.RefreshRejected)
            }
            throw error
        }
        return request.setting("Authorization", value = "Bearer $token")
    }

    override suspend fun retry(
        request: HttpRequest,
        response: HttpResponseSummary,
        hasReplayed: Boolean,
    ): InterceptorDecision {
        if (!request.requiresAuth) return InterceptorDecision.Proceed

        if (response.isForbidden) {
            TTLogger.auth.warning {
                logMessage { append("network: 403 -> ending session (${SessionInvalidationReason.Forbidden.wireName})") }
            }
            delegate.invalidateSession(SessionInvalidationReason.Forbidden)
            return InterceptorDecision.Proceed
        }

        if (!response.isUnauthorized) return InterceptorDecision.Proceed

        // The replay budget is already spent. A 401 *after* a successful refresh means the
        // server is rejecting the freshly minted token too, so refreshing again would not
        // help, and leaving the session alive would just 401 every later call.
        if (hasReplayed) {
            TTLogger.auth.error {
                logMessage {
                    append("network: 401 after refresh -> ending session ")
                    append("(${SessionInvalidationReason.Unauthorized.wireName})")
                }
            }
            delegate.invalidateSession(SessionInvalidationReason.Unauthorized)
            return InterceptorDecision.Proceed
        }

        return try {
            // The server says our token is no longer good, so trust it over the locally
            // cached expiry and force a refresh.
            delegate.validAccessToken(forceRefresh = true)
            InterceptorDecision.Retry
        } catch (cancellation: CancellationException) {
            // The original's bare `catch` covers this too, because Swift has no
            // cancellation. Catching it here would be a real bug: a cancelled screen would
            // end the user's session on the way out, so the next launch would be signed out
            // for no reason. Rethrown before the broad catch.
            throw cancellation
        } catch (_: Throwable) {
            TTLogger.auth.error {
                logMessage {
                    append("network: 401 and refresh failed -> ending session ")
                    append("(${SessionInvalidationReason.RefreshRejected.wireName})")
                }
            }
            delegate.invalidateSession(SessionInvalidationReason.RefreshRejected)
            InterceptorDecision.Proceed
        }
    }
}
