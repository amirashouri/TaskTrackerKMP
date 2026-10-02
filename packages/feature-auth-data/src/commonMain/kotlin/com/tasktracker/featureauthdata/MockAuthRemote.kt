package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.JWT
import com.tasktracker.core.randomUserID
import com.tasktracker.featureauthdomain.AuthRemotePort
import com.tasktracker.featureauthdomain.AuthTokens
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A sign-in backend that does not need one.
 *
 * The composition root's base URL points at a `.invalid` host, so the real `HttpAuthRemote`
 * can never succeed and the app would be permanently stuck behind the auth gate. This stands
 * in for it so the whole flow — sign in, retain, refresh, restore, sign out — is exercisable
 * on a simulator with no server anywhere.
 *
 * It mints **genuine** unsigned JWTs rather than returning the string `"token-1"`. The vault
 * decides whether a token is still good by reading its `exp` claim, so a fake returning an
 * opaque string would take the "expiry cannot be read, fail closed" branch on every request
 * and quietly exercise none of the interesting logic. These carry a real `exp`, so the safety
 * margin, the forced refresh and the restore path are all tested for real.
 *
 * ## On being in `commonMain`
 *
 * The release rule is "a debug build must never be handed this instead of
 * [HttpAuthRemote]", and that rule is enforced at the *composition root*, which chooses
 * between the two — not by this file's location. Nothing in the app constructs a remote
 * except that root, so the class sitting in `commonMain` cannot by itself put an unsigned
 * token on the wire.
 *
 * It is here rather than in a debug-only source set for two reasons. Its `commonTest`
 * exercises it on all three targets, so it has to be in a source set `commonTest` can see;
 * and the KMP hierarchy in this build applies the default template, with no debug/release
 * split to hang a `debugMain` on. Adding that split to keep one class out of a release
 * binary is new build machinery in every module, for a problem a single `if` in the root
 * already solves.
 *
 * The one thing a reader must not do is reach for this type directly. When the root gains its
 * build-mode switch, this becomes unreachable from a release root the same day — noted in
 * `PLAN.md` under the phase that wires the root, so the decision is not left implicit here.
 */
class MockAuthRemote(
    private val dateProvider: DateProvider,
    private val accessLifetimeSeconds: Long = DEFAULT_ACCESS_LIFETIME_SECONDS,
    private val refreshLifetimeSeconds: Long = DEFAULT_REFRESH_LIFETIME_SECONDS,
    /**
     * How a token is produced. A seam rather than a hard call so a test can mint something
     * the vault will reject, which is the only way to reach its fail-closed branch from this
     * side. `suspend` because the production minter is, and a narrower type here would force
     * every caller to wrap it.
     */
    private val mint: suspend (
        subject: String,
        email: String,
        type: String,
        lifetimeSeconds: Long,
        issuedAt: Instant,
    ) -> String = ::mintToken,
) : AuthRemotePort {

    @Throws(AppError::class, CancellationException::class)
    override suspend fun signIn(email: String, password: String): AuthTokens {
        if (password == REJECTED_PASSWORD) throw AppError.Unauthorized
        return pairFor(subject = subjectFor(email), email = email)
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun refresh(refreshToken: String): AuthTokens {
        // A real server rejects a refresh token it has already consumed, which is the whole
        // reason the vault collapses concurrent refreshes into one. Rejecting an unreadable
        // token here models the same failure without needing per-token bookkeeping.
        val claims = JWT.parse(refreshToken)
        if (claims == null || claims.subject.isEmpty()) throw AppError.Unauthorized
        return pairFor(subject = claims.subject, email = claims.email ?: "")
    }

    private suspend fun pairFor(subject: String, email: String): AuthTokens {
        val now = dateProvider.now()
        return AuthTokens(
            accessToken = mint(subject, email, TOKEN_TYPE_ACCESS, accessLifetimeSeconds, now),
            refreshToken = mint(subject, email, TOKEN_TYPE_REFRESH, refreshLifetimeSeconds, now),
        )
    }

    companion object {

        /**
         * Rejects a sign-in whose password is this, so the error path is reachable without
         * needing a server to misbehave.
         */
        const val REJECTED_PASSWORD: String = "wrong-password"

        private const val TOKEN_TYPE_ACCESS = "access"
        private const val TOKEN_TYPE_REFRESH = "refresh"

        /** An hour — long enough that the expiry margin is not what a test is measuring. */
        const val DEFAULT_ACCESS_LIFETIME_SECONDS: Long = 3_600

        /** Thirty days, matching what a real deployment would consider a session. */
        const val DEFAULT_REFRESH_LIFETIME_SECONDS: Long = 60L * 60L * 24L * 30L

        /**
         * A stable account id for an address, so the same email always signs in as the same
         * user across launches. Otherwise every sign-in would look like a different person
         * and nothing account-scoped could be demonstrated.
         *
         * FNV-1a rather than a hash of the string, which is seeded per process and would hand
         * out a different id on every launch. Formatted as a real UUID so [UserID] accepts it.
         */
        @OptIn(ExperimentalUuidApi::class)
        fun subjectFor(email: String): String {
            val normalised = email.lowercase().encodeToByteArray()
            val high = fnv1a(normalised)
            // Two rounds rather than one word spread over sixteen bytes. `ULong.shr` masks its
            // shift count to six bits, so the tempting `(15 - index) * 8` does not walk
            // 120 down to 0 — it wraps, and indices 0..7 alias onto 56..0, producing a UUID
            // whose last eight bytes repeat its first eight. That is well-formed and stable,
            // so every obvious test passes, and it quietly halves the identifier to 64 bits.
            val low = fnv1a(high.toString().encodeToByteArray())
            val bytes = ByteArray(16) { index ->
                val word = if (index < 8) high else low
                (word shr ((index and 7) * 8)).toByte()
            }
            // Parsed rather than built from `Uuid(bytes)`, so this file depends only on the
            // identifier helpers `Identifiers.kt` already proves on every target.
            return Uuid.parse(bytes.joinToString("") { byte ->
                val value = byte.toUByte().toInt()
                HEX[value shr 4].toString() + HEX[value and 0x0F]
            }).toString()
        }

        /**
         * FNV-1a, 64-bit. The standard offset basis and prime, so the mapping is a fixed
         * function of the input rather than anything process-scoped.
         */
        private fun fnv1a(bytes: ByteArray): ULong {
            var hash = 0xcbf2_9ce484222325uL
            for (byte in bytes) {
                hash = hash xor (byte.toUByte().toULong())
                hash *= 0x100000001b3uL
            }
            return hash
        }

        /**
         * Builds a real, unsigned JWT carrying the claims the vault reads.
         *
         * `issuedAt` is a parameter rather than a call to the system clock, so a test can
         * assert an exact expiry instead of "roughly an hour from whenever this ran".
         */
        suspend fun mintToken(
            subject: String,
            email: String,
            type: String,
            lifetimeSeconds: Long,
            issuedAt: Instant,
        ): String {
            val header = segment(
                buildJsonObject {
                    put("alg", "none")
                    put("typ", "JWT")
                },
            )
            val payload = segment(
                buildJsonObject {
                    put("sub", subject)
                    put("email", email)
                    put("typ", type)
                    put("iat", issuedAt.epochSeconds)
                    put("exp", issuedAt.epochSeconds + lifetimeSeconds)
                    put("jti", randomTokenID())
                },
            )
            // A trailing empty signature segment keeps the token three-part, which is what
            // `JWT.parse` insists on before it will read the claims.
            return "$header.$payload."
        }

        private const val HEX = "0123456789abcdef"

        private fun segment(value: JsonObject): String =
            JWT.encodeBase64URL(value.toString().encodeToByteArray())
    }
}

/** A fresh id per minted token, so two tokens for the same account are distinguishable. */
private fun randomTokenID(): String = randomUserID().stringValue
