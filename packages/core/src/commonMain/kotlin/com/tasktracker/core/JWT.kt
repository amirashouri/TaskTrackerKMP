package com.tasktracker.core

import kotlin.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Minimal JWT inspection.
 *
 * Only the *claims* are read here — no signature verification, because the client
 * never needs to establish trust in a token it did not mint. Verification is the
 * server's job; the client's only concern is "when does this expire" so it can
 * refresh before the user sees a failure.
 */
object JWT {

    data class Claims(
        val subject: String,
        val email: String?,
        val expiry: Instant?,
        val issuedAt: Instant?,
        /** `access` or `refresh`; distinguishes the two tokens in the same vault. */
        val tokenType: String?,
        val tokenID: String?,
    )

    /** Parses a token, or returns `null` for anything structurally invalid. */
    fun parse(token: String): Claims? {
        // Kotlin's `split` keeps empty segments by default, which is what is wanted
        // here: `a..b` must be three segments, not two, so a doubled separator is
        // rejected rather than quietly producing a valid-looking token.
        val segments = token.split('.')
        if (segments.size != 3) return null
        val payload = decodeBase64URL(segments[1]) ?: return null
        val json = parseObject(payload) ?: return null

        return Claims(
            subject = json.stringClaim("sub") ?: "",
            email = json.stringClaim("email"),
            expiry = json.epochClaim("exp"),
            issuedAt = json.epochClaim("iat"),
            tokenType = json.stringClaim("typ"),
            tokenID = json.stringClaim("jti"),
        )
    }

    /**
     * The `exp` claim, or `null` when the token has no expiry.
     *
     * A null here must not be read as "valid forever": the auth repository treats an
     * unreadable expiry as expired, so a malformed token gets refreshed rather than
     * trusted. Defaulting to "never expires" would be the opposite failure and much
     * harder to notice.
     */
    fun expiry(token: String): Instant? = parse(token)?.expiry

    private fun parseObject(bytes: ByteArray): JsonObject? =
        runCatching {
            JSONCoding.json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        }.getOrNull()

    /**
     * `jsonPrimitive` throws on a non-primitive and `content` throws on a `null`
     * literal, so both are wrapped. A claim of the wrong JSON type is a malformed
     * token, which this type reports as `null` rather than propagating a
     * serialization exception out of a function whose whole contract is
     * "returns null when it cannot understand this".
     */
    private fun JsonObject.primitiveOrNull(name: String): JsonPrimitive? =
        runCatching { this[name] as? JsonPrimitive }.getOrNull()

    private fun JsonObject.stringClaim(name: String): String? =
        primitiveOrNull(name)?.takeIf { it !is JsonNull }?.content

    /**
     * Reads an epoch-seconds claim that may arrive as a JSON number or a JSON
     * string, since both appear in the wild. Only integers are accepted: a fractional
     * `exp` is not a thing, and `longOrNull` returning null for `1.5` keeps that from
     * being silently truncated into a plausible-looking instant.
     */
    private fun JsonObject.epochClaim(name: String): Instant? {
        val primitive = primitiveOrNull(name) ?: return null
        val seconds = primitive.longOrNull ?: primitive.content.toLongOrNull() ?: return null
        return Instant.fromEpochSeconds(seconds)
    }

    // MARK: - base64url

    /**
     * Decodes base64url. The `-`/`_` alphabet and the missing `=` padding are both
     * defined by RFC 7515, so this translates rather than "decodes and hopes" —
     * Kotlin's [Base64] rejects the URL-safe alphabet and requires padding by
     * default, and a token that failed to decode here would surface as a confusing
     * sign-out rather than as a parse failure.
     *
     * The padding is restored arithmetically rather than with a lenient decoder
     * config, because a *global* `isPaddingRequired = false` would also stop
     * rejecting a genuinely truncated payload. Note that a length of `4n + 1` is
     * not representable in base64 at all; the computed padding produces a string
     * that still fails to decode, and the `runCatching` turns that into the `null`
     * this function promises.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64URL(string: String): ByteArray? {
        val unpadded = string.replace('-', '+').replace('_', '/')
        val padding = (4 - unpadded.length % 4) % 4
        val padded = unpadded + "=".repeat(padding)
        return runCatching { Base64.decode(padded) }.getOrNull()
    }

    /** Encodes to unpadded base64url. */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeBase64URL(data: ByteArray): String =
        Base64.encode(data).trimEnd('=').replace('+', '-').replace('/', '_')
}
