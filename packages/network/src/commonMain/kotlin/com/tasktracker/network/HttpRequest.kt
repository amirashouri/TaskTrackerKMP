package com.tasktracker.network

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The HTTP verbs this client speaks. */
enum class HttpMethod(val wireName: String) {
    Get("GET"),
    Post("POST"),
    Put("PUT"),
    Patch("PATCH"),
    Delete("DELETE"),
    ;

    /**
     * Whether re-sending the request is safe by definition.
     *
     * [Post] is excluded on purpose: the outbox `push` endpoint is a POST, and blindly
     * replaying it after a transport error could apply the same change twice. A request
     * that genuinely is safe to replay opts in via [HttpRequest.isIdempotent].
     */
    val isIdempotentByDefault: Boolean
        get() = when (this) {
            Get, Put, Delete -> true
            Post, Patch -> false
        }
}

/**
 * One query parameter.
 *
 * A type rather than a `Pair` because a query item is compared in tests and printed in
 * failure messages, and `Pair("cursor", "c7")` says less than
 * `HttpQueryItem(name = "cursor", value = "c7")` at either. The value is non-null: every
 * query the API takes has one, and a nullable value would have to become `?flag` on the
 * wire — which nothing here can ask for and no caller can test, because Ktor's parameter
 * builder has no way to emit it.
 */
data class HttpQueryItem(val name: String, val value: String)

/**
 * A transport-agnostic description of one HTTP call.
 *
 * An immutable value type, so it is safe to hand between the interceptor chain and the
 * client without a lock — the same property the original got from a Swift `struct` of
 * `let`s.
 *
 * ## Not a data class
 *
 * [body] is a `ByteArray`, and Kotlin's generated `equals` compares arrays by
 * *identity*. Two requests built separately with the same JSON would be unequal, so a
 * test asserting on a request, or a cache keyed by one, would fail for reasons that
 * have nothing to do with the network. Hence hand-written [equals], [hashCode] and
 * [copy] — the same trap `:packages:database` hit with outbox payloads.
 */
class HttpRequest(
    val method: HttpMethod,
    /** Path relative to the client's base URL, e.g. `/v1/projects`. */
    val path: String,
    val queryItems: List<HttpQueryItem> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    /** `false` only for sign-in and token refresh, which must work before a session exists. */
    val requiresAuth: Boolean = true,
    val timeout: Duration = DEFAULT_TIMEOUT,
    /**
     * Overrides [HttpMethod.isIdempotentByDefault] for a non-idempotent verb that is
     * nonetheless safe to replay (e.g. `POST /auth/login`).
     */
    val isIdempotent: Boolean? = null,
) {

    /** [isIdempotent] when the endpoint said so, the verb's default otherwise. */
    val effectiveIdempotency: Boolean get() = isIdempotent ?: method.isIdempotentByDefault

    /** Case-insensitive header lookup — HTTP header names are not case-sensitive. */
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /**
     * A copy with one header set, replacing any existing spelling of that name.
     *
     * Replacing rather than adding is the point: the auth interceptor runs again on a
     * replay, and a request that carried `Authorization: Bearer stale` would otherwise
     * leave both values on the wire for the server to pick between.
     */
    fun setting(name: String, value: String): HttpRequest = copy(
        headers = headers.filterKeys { !it.equals(name, ignoreCase = true) } + (name to value),
    )

    fun copy(
        method: HttpMethod = this.method,
        path: String = this.path,
        queryItems: List<HttpQueryItem> = this.queryItems,
        headers: Map<String, String> = this.headers,
        body: ByteArray? = this.body,
        requiresAuth: Boolean = this.requiresAuth,
        timeout: Duration = this.timeout,
        isIdempotent: Boolean? = this.isIdempotent,
    ): HttpRequest = HttpRequest(
        method, path, queryItems, headers, body, requiresAuth, timeout, isIdempotent,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpRequest) return false
        return method == other.method &&
            path == other.path &&
            queryItems == other.queryItems &&
            headers == other.headers &&
            body.contentEqualsNullable(other.body) &&
            requiresAuth == other.requiresAuth &&
            timeout == other.timeout &&
            isIdempotent == other.isIdempotent
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + queryItems.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + requiresAuth.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + (isIdempotent?.hashCode() ?: 0)
        return result
    }

    /**
     * The body is rendered as its length, not its bytes.
     *
     * A request can be logged on a retry, and this value ends up in a log line. A JSON
     * body here would be a payload — possibly a password, since sign-in is a POST —
     * printed where nobody is looking for redaction.
     */
    override fun toString(): String =
        "HttpRequest(method=$method, path=$path, queryItems=$queryItems, " +
            "headers=${headers.keys}, body=${body?.size ?: 0} bytes, " +
            "requiresAuth=$requiresAuth, timeout=$timeout, isIdempotent=$isIdempotent)"

    companion object {
        val DEFAULT_TIMEOUT: Duration = 30.seconds
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> this.contentEquals(other)
}
