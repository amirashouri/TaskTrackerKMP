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
 *
 * A `struct` in the original; a class holding one immutable reference here. Wrapping it in
 * a coroutine `Mutex` would be wrong — the delegate behind it does the concurrency
 * control, and a lock here would serialise every in-flight request behind one refresh.
 */
class AuthInterceptor(private val delegate: AuthSessionDelegate) : RequestInterceptor {

    override suspend fun adapt(request: HttpRequest): HttpRequest {
        if (!request.requiresAuth) return request
        // Sign-in and refresh must not try to read a session that does not exist yet.
        val token = delegate.validAccessToken(forceRefresh = false)
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
