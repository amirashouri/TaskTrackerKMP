package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JWT
import com.tasktracker.core.TestDateProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The clock every fixture starts on, matching the epoch `TestToken` mints against. */
private val NOW: Instant = Instant.fromEpochSeconds(1_760_000_000)

/**
 * The debug remote is the one the app actually runs, so it is the one thing in this package
 * a user can reach by typing their own address. It gets tested like production code.
 */
class MockAuthRemoteTest {

    private fun remote(
        accessLifetime: Long = MockAuthRemote.DEFAULT_ACCESS_LIFETIME_SECONDS,
        clock: TestDateProvider = TestDateProvider(NOW),
    ) = MockAuthRemote(dateProvider = clock, accessLifetimeSeconds = accessLifetime)

    @Test
    fun theReservedPasswordIsRejected() = runTest {
        val failure = assertFailsWith<AppError.Unauthorized> {
            remote().signIn(email = "ada@example.com", password = MockAuthRemote.REJECTED_PASSWORD)
        }

        assertEquals("wrong-password", MockAuthRemote.REJECTED_PASSWORD)
        assertEquals(AppError.Unauthorized, failure)
    }

    @Test
    fun anyOtherPasswordIsAccepted() = runTest {
        val tokens = remote().signIn(email = "ada@example.com", password = "hunter2")

        assertTrue(tokens.accessToken.isNotEmpty())
        assertTrue(tokens.refreshToken.isNotEmpty())
    }

    /**
     * The crash this file exists to prevent: a subject built from too few bytes, which
     * indexed past the end of its own array the first time any real sign-in ran.
     */
    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun theAccountIdIsAWellFormedUUID() = runTest {
        val tokens = remote().signIn(email = "ada@example.com", password = "hunter2")
        val claims = assertNotNull(JWT.parse(tokens.accessToken))

        assertNotNull(Uuid.parse(claims.subject), "\"${claims.subject}\" is not a UUID")
    }

    /**
     * A per-process-seeded id would make every launch a different account, so nothing
     * account-scoped could ever be demonstrated.
     */
    @Test
    fun theSameAddressAlwaysYieldsTheSameAccountId() = runTest {
        val first = remote().signIn(email = "ada@example.com", password = "a")
        val second = remote().signIn(email = "ada@example.com", password = "b")

        assertNotEquals(first.accessToken, second.accessToken, "each sign-in mints a fresh jti")
        assertEquals(
            JWT.parse(first.accessToken)?.subject,
            JWT.parse(second.accessToken)?.subject,
            "the subject must not depend on the sign-in",
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun theWholeIdentifierCarriesEntropyRatherThanRepeatingHalfOfIt() = runTest {
        // A UUID is 16 bytes, and it is tempting to spread one 64-bit hash across all 16 by
        // shifting down "120 to 0". `ULong.shr` masks its shift count to six bits, so those
        // shifts wrap and the last eight bytes come out equal to the first eight — a
        // well-formed, stable, 64-bit id wearing a 128-bit label. Comparing only the whole
        // string cannot see it, because both halves of *each* id are internally consistent.
        suspend fun subjectOf(email: String): Uuid {
            val tokens = remote().signIn(email = email, password = "a")
            return Uuid.parse(assertNotNull(JWT.parse(tokens.accessToken)).subject)
        }

        val ada = subjectOf("ada@example.com").toHexString()
        // A UUID is 16 bytes, so each half is 16 hex characters. Comparing `take(8)` with
        // `drop(8)` would be 4 bytes against 12 and could never fail, which is how this
        // assertion passed against the very bug it was written for.
        assertNotEquals(
            ada.take(16),
            ada.drop(16),
            message = "the id repeats itself: 64 bits, not 128",
        )
    }

    @Test
    fun theAddressIsMatchedCaseInsensitively() = runTest {
        val lower = remote().signIn(email = "ada@example.com", password = "a")
        val upper = remote().signIn(email = "Ada@Example.com", password = "a")

        assertEquals(JWT.parse(lower.accessToken)?.subject, JWT.parse(upper.accessToken)?.subject)
    }

    @Test
    fun differentAddressesYieldDifferentAccounts() = runTest {
        val ada = remote().signIn(email = "ada@example.com", password = "a")
        val grace = remote().signIn(email = "grace@example.com", password = "a")

        assertNotEquals(JWT.parse(ada.accessToken)?.subject, JWT.parse(grace.accessToken)?.subject)
    }

    @Test
    fun theAccessTokenCarriesTheAddressAndAReadableExpiry() = runTest {
        val tokens = remote().signIn(email = "ada@example.com", password = "a")
        val claims = assertNotNull(JWT.parse(tokens.accessToken))

        assertEquals("ada@example.com", claims.email)
        assertEquals("access", claims.tokenType)
        // Readable expiry is what lets the vault decide to refresh instead of guessing, and
        // an opaque fake token would send every request down its fail-closed branch.
        assertNotNull(claims.expiry)
    }

    @Test
    fun theAccessTokenIsShortLivedSoTheRefreshPathIsReachable() = runTest {
        val tokens = remote(accessLifetime = 5).signIn(email = "ada@example.com", password = "a")
        val expiry = assertNotNull(assertNotNull(JWT.parse(tokens.accessToken)).expiry)

        assertTrue(expiry - NOW <= 5.seconds, "expected a 5s lifetime, got ${expiry - NOW}")
    }

    @Test
    fun theRefreshTokenIsLongerLivedThanTheAccessToken() = runTest {
        val tokens = remote().signIn(email = "ada@example.com", password = "a")
        val access = assertNotNull(JWT.parse(tokens.accessToken))
        val refresh = assertNotNull(JWT.parse(tokens.refreshToken))

        assertEquals("refresh", refresh.tokenType)
        assertTrue(refresh.expiry!! > access.expiry!!)
    }

    @Test
    fun theMintedExpiryIsTheClockPlusTheLifetimeRatherThanTheSystemClock() = runTest {
        val clock = TestDateProvider(NOW)
        val tokens = remote(clock = clock).signIn(email = "ada@example.com", password = "a")

        val expiry = assertNotNull(assertNotNull(JWT.parse(tokens.accessToken)).expiry)

        // The original read the system clock inside the minter, which made an exact expiry
        // unassertable. Injected, it is simply true.
        assertEquals(
            NOW + MockAuthRemote.DEFAULT_ACCESS_LIFETIME_SECONDS.seconds,
            expiry,
        )
    }

    @Test
    fun aRefreshReturnsAUsableAccessTokenForTheSameAccount() = runTest {
        val subject = remote()
        val first = subject.signIn(email = "ada@example.com", password = "a")

        val refreshed = subject.refresh(refreshToken = first.refreshToken)
        val before = assertNotNull(JWT.parse(first.accessToken))
        val after = assertNotNull(JWT.parse(refreshed.accessToken))

        assertEquals(before.subject, after.subject)
        assertEquals("ada@example.com", after.email)
    }

    @Test
    fun anUnreadableRefreshTokenIsRejected() = runTest {
        // A real server rejects a token it has already consumed, which is the whole reason
        // the vault collapses concurrent refreshes into one.
        assertFailsWith<AppError.Unauthorized> {
            remote().refresh(refreshToken = "not-a-token")
        }
    }

    @Test
    fun theVaultAcceptsWhatThisRemoteMintsWithoutFailingClosed() = runTest {
        // The end-to-end reason the tokens are real: a vault handed these decides they are
        // still good on the claims alone, rather than taking the "expiry cannot be read"
        // branch on every single request.
        val clock = TestDateProvider(NOW)
        val minted = remote(clock = clock).signIn(email = "ada@example.com", password = "a")
        val vault = TokenVault(
            keychain = inMemoryKeychainStore(),
            remote = remote(clock = clock),
            bus = SessionStateBus(),
            dateProvider = clock,
        )
        vault.store(minted)

        assertEquals(minted.accessToken, vault.validAccessToken(forceRefresh = false))
    }
}
