package com.tasktracker.core

/**
 * The single error type that crosses module boundaries.
 *
 * Domain layers throw and catch this, so no Ktor exception, no `SerializationException`
 * and no SQLite error ever leaks upward past the data layer. The alternative — letting
 * each layer define its own failures — is how a presentation module ends up with a
 * `when` over six unrelated exception types and a catch-all that renders "something
 * went wrong".
 *
 * ## Why it extends [Exception]
 *
 * The original is a thrown Swift `Error`, and the use sites read as `try { … } catch`.
 * A sealed interface returned as a value would be more idiomatic in isolation, but it
 * changes every signature in the port and, more importantly, it changes the failure
 * mode: a suspend function that returns a value has to be checked by every caller,
 * whereas a thrown exception is caught once at the boundary. That boundary is the whole
 * point of this type — `:packages:sync`'s engine catches it to build a `SyncReport`,
 * `:packages:network` throws it, and `:packages:feature-*-presentation` renders it
 * without ever naming a Ktor or database type.
 *
 * ## The text lives here, not in string resources
 *
 * The original's user-facing strings are hardcoded literals, and they stay that way here
 * for a structural reason: `:packages:core` is allowed no project dependencies at all
 * (`allowedLibraryTable` in `tasktracker.architecture.gradle.kts` gives it the empty
 * set), so it cannot reach `composeResources`, which `:packages:design-system` owns
 * exclusively. Routing these through `Res.string` would mean either giving core a
 * dependency on the design system — inverting the layering to serve a string — or
 * duplicating the text. One copy, in the place that defines the error, is the honest
 * option. The `error_*` entries were therefore removed from `strings.xml`.
 *
 * The visible text is the [Exception] message, so a presentation module renders
 * `error.message` and never re-words a failure: the same error must not read
 * differently in two places.
 *
 * The detail-carrying cases name their property `detail`, not `message`. `Throwable`
 * already owns `message` as the *visible* text, and a `val message` on a subclass
 * would shadow it — so `Server(500, "boom").message` would return the server's
 * fragment while the banner showed "Server error (500). boom". Two values behind one
 * name is exactly the confusion this type exists to remove.
 */
sealed class AppError(message: String) : Exception(message) {

    /** The device has no usable network path. Local writes are queued and sync later. */
    data object Offline : AppError("You appear to be offline.")

    /** A transport-level failure. Retryable. */
    data class Transport(val detail: String) :
        AppError("Could not reach the server. $detail")

    /** The request did not complete within its deadline. Retryable. */
    data object Timeout : AppError("The server took too long to respond.")

    /** 401 — no valid credentials. Triggers a refresh attempt, then a sign-out. */
    data object Unauthorized : AppError("Your session has expired. Please sign in again.")

    /** 403 — the session is definitively rejected. Triggers a sign-out. */
    data object Forbidden : AppError("You do not have access to this resource.")

    /** 5xx. */
    data class Server(val statusCode: Int, val detail: String) :
        AppError("Server error ($statusCode). $detail")

    /**
     * 404 — the server does not have the resource.
     *
     * Its own case rather than [Unknown] because the two mean opposite things to a caller: an
     * absent row is an *answer*, not a failure, and the admin projects screen reads it as one by
     * turning it into `null`. Folded into [Unknown], that read could not tell it from a `500`,
     * and a server failure rendered as a project that had been deleted.
     *
     * Not retryable: the same request finds the same absent row.
     */
    data class NotFound(val detail: String) : AppError(detail)

    /** The payload did not match the expected contract. */
    data class Decoding(val detail: String) :
        AppError("The server sent something unexpected. $detail")

    /**
     * The server rejected a write because its copy is newer. The server-wins policy
     * means the local row has already been replaced by the time this is thrown, so this
     * is reported rather than treated as a lost edit.
     */
    data class Conflict(val entity: String) :
        AppError("This $entity was changed on the server. The server version has been applied.")

    /** Input rejected before it left the device. */
    data class Validation(val detail: String) : AppError(detail)

    /** The caller cancelled the work. */
    data object Cancelled : AppError("The operation was cancelled.")

    /**
     * Local persistence failed — the store is corrupt, full, or the row is invalid.
     *
     * Never a sign-out trigger and never silently swallowed: a write the user was told
     * succeeded but did not land is a correctness bug, not a network hiccup.
     */
    data class Storage(val detail: String) :
        AppError("Could not save your changes. $detail")

    data class Unknown(val detail: String) : AppError(detail)
    /**
     * Whether retrying the same request could plausibly succeed later.
     *
     * A total `when` with no `else`, deliberately. Adding a case above and forgetting
     * this is a compile error rather than a silently-defaulted `false`, which is the
     * whole reason the property is written out longhand instead of being a set
     * membership test.
     */
    val isRetryable: Boolean
        get() = when (this) {
            is Transport, is Timeout, is Offline, is Server -> true
            is Unauthorized, is Forbidden, is NotFound, is Decoding, is Conflict,
            is Validation, is Cancelled, is Storage, is Unknown,
            -> false
        }

    /**
     * Whether this error must terminate the user's session.
     *
     * A 403 ends the session outright rather than attempting a refresh: a fresh token
     * cannot grant access that the current one was refused, so retrying would spend a
     * round trip to learn the same answer. `AuthInterceptor` in `:packages:network`
     * depends on this being the only two cases.
     */
    val requiresSignOut: Boolean
        get() = when (this) {
            is Unauthorized, is Forbidden -> true
            is Offline, is Transport, is Timeout, is Server, is NotFound, is Decoding,
            is Conflict, is Validation, is Cancelled, is Storage, is Unknown,
            -> false
        }

    /**
     * Whether the device could not reach the server at all, as opposed to the server
     * having answered unusably. The distinction decides whether the Settings "Force
     * Offline" switch explains itself and whether a queued edit is presented as safe.
     */
    val isConnectivityFailure: Boolean
        get() = when (this) {
            is Offline, is Transport, is Timeout -> true
            is Unauthorized, is Forbidden, is Server, is NotFound, is Decoding, is Conflict,
            is Validation, is Cancelled, is Storage, is Unknown,
            -> false
        }

    /**
     * A compact form for logs, not for users.
     *
     * `Exception.toString()` cannot be used for this: it renders the class name and the
     * visible text, so a log line would carry the user-facing sentence rather than
     * `transport(…)`. Two shapes for the same value is the point — a log line is
     * grepped, an error banner is read.
     */
    val logText: String
        get() = when (this) {
            is Offline -> "offline"
            is Transport -> "transport($detail)"
            is Timeout -> "timeout"
            is Unauthorized -> "unauthorized"
            is Forbidden -> "forbidden"
            is Server -> "server($statusCode, $detail)"
            is NotFound -> "notfound($detail)"
            is Decoding -> "decoding($detail)"
            is Conflict -> "conflict($entity)"
            is Validation -> "validation($detail)"
            is Cancelled -> "cancelled"
            is Storage -> "storage($detail)"
            is Unknown -> "unknown($detail)"
        }
}
