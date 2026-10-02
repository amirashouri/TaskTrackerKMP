package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.featureauthdomain.AuthTokens
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.keychain.KeychainError
import com.tasktracker.keychain.KeychainKey
import com.tasktracker.keychain.KeychainStore
import com.tasktracker.keychain.value
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.network.SessionInvalidationReason
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A vault over an in-memory keychain and a scripted remote, with a clock the test controls
 * so expiry can be exercised without sleeping.
 */
private class VaultSubject(
    val remote: ScriptedAuthRemote = ScriptedAuthRemote(),
    val access: InMemoryKeychain = InMemoryKeychain(),
    val clock: TestDateProvider = TestDateProvider(),
) {
    val store: KeychainStore = KeychainStore(access)
    val bus: SessionStateBus = SessionStateBus()
    val vault: TokenVault = TokenVault(store, remote, bus, clock)
}

/** The clock every subject starts on, and the epoch `TestToken` mints against. */
private val NOW: Instant = Instant.fromEpochSeconds(1_760_000_000)

class TokenVaultTest {

    @Test
    fun aStoredTokenIsReturnedWithoutARefresh() = runTest {
        val subject = VaultSubject()
        val token = TestToken.make(expiresIn = 3_600)
        subject.vault.store(AuthTokens(token, "r1"))

        assertEquals(token, subject.vault.validAccessToken(forceRefresh = false))
        assertEquals(0, subject.remote.refreshCount)
    }

    @Test
    fun aTokenInsideTheSafetyMarginIsRefreshedBeforeItIsHandedOut() = runTest {
        val subject = VaultSubject()
        // The clock sits 30s before expiry: inside the 60s margin, so the token must be
        // replaced even though `exp` has not passed yet.
        subject.clock.set(NOW + 3_600.seconds - 30.seconds)
        val expiring = TestToken.make(expiresIn = 3_600)
        val fresh = TestToken.make(expiresIn = 7_200)
        subject.remote.setRefreshResults(listOf(Result.success(AuthTokens(fresh, "r2"))))
        subject.vault.store(AuthTokens(expiring, "r1"))

        assertEquals(fresh, subject.vault.validAccessToken(forceRefresh = false))
        assertEquals(1, subject.remote.refreshCount)
    }

    @Test
    fun aTokenJustOutsideTheSafetyMarginIsHandedOutAsIs() = runTest {
        val subject = VaultSubject()
        // The mirror of the test above, and the one that would fail if the margin were off by
        // a rounding error in the generous direction.
        subject.clock.set(NOW + 3_600.seconds - 90.seconds)
        val token = TestToken.make(expiresIn = 3_600)
        subject.vault.store(AuthTokens(token, "r1"))

        assertEquals(token, subject.vault.validAccessToken(forceRefresh = false))
        assertEquals(0, subject.remote.refreshCount)
    }

    @Test
    fun aTokenWithNoReadableExpiryIsTreatedAsExpired() = runTest {
        val subject = VaultSubject()
        val fresh = TestToken.make(expiresIn = 3_600)
        subject.remote.setRefreshResults(listOf(Result.success(AuthTokens(fresh, "r2"))))

        // Not a JWT at all. Trusting it would send garbage to the server and, on a 403, cost
        // the session, so the vault refreshes instead.
        subject.vault.store(AuthTokens("not-a-jwt", "r1"))

        assertEquals(fresh, subject.vault.validAccessToken(forceRefresh = false))
        assertEquals(1, subject.remote.refreshCount)
    }

    @Test
    fun a401ForcesARefreshEvenWhenTheCachedTokenLooksValid() = runTest {
        val subject = VaultSubject()
        val cached = TestToken.make(expiresIn = 3_600)
        val rotated = TestToken.make(expiresIn = 7_200)
        subject.remote.setRefreshResults(listOf(Result.success(AuthTokens(rotated, "r2"))))
        subject.vault.store(AuthTokens(cached, "r1"))

        assertEquals(rotated, subject.vault.validAccessToken(forceRefresh = true))
        assertEquals(1, subject.remote.refreshCount)
    }

    @Test
    fun concurrentMissesCollapseIntoASingleRefreshRoundTrip() = runTest {
        val subject = VaultSubject()
        // A long delay holds the refresh open, so every caller's request is genuinely in
        // flight at the same moment rather than accidentally serialised.
        subject.remote.setRefreshDelay(200.milliseconds)
        subject.remote.setRefreshResults(
            listOf(Result.success(AuthTokens(TestToken.make(expiresIn = 3_600), "r2"))),
        )
        subject.vault.store(AuthTokens(TestToken.make(expiresIn = -10), "r1"))

        val tokens = List(8) { async { subject.vault.validAccessToken(forceRefresh = false) } }
            .awaitAll()

        assertEquals(8, tokens.size)
        // The whole point: one refresh, not eight. A real server invalidates the refresh token
        // it just issued, so eight parallel refreshes would leave seven callers holding a
        // rejected token — which is what looks like a random sign-out.
        assertEquals(1, subject.remote.refreshCount)
        assertTrue(tokens.all { it == tokens.first() }, "every caller must get the same token")
    }

    @Test
    fun withNoRefreshTokenStoredAskingForATokenFailsAsUnauthorized() = runTest {
        val subject = VaultSubject()

        assertFailsWith<AppError.Unauthorized> {
            subject.vault.validAccessToken(forceRefresh = false)
        }
        assertEquals(0, subject.remote.refreshCount)
    }

    @Test
    fun aRejectedRefreshIsSurfacedAndLeavesNoHalfWrittenState() = runTest {
        val subject = VaultSubject()
        subject.remote.setRefreshResults(listOf(Result.failure(AppError.Unauthorized)))
        subject.vault.store(AuthTokens(TestToken.make(expiresIn = -10), "r1"))

        assertFailsWith<AppError.Unauthorized> {
            subject.vault.validAccessToken(forceRefresh = false)
        }
        // The previous pair is still what is on disk, so a later attempt can still be made
        // against the same credentials rather than finding a corrupted vault.
        assertEquals("r1", subject.store.value<String>(KeychainKey.RefreshToken))
    }

    @Test
    fun invalidatingASessionWipesTheKeychainAndAnnouncesSignedOut() = runTest {
        val subject = VaultSubject()
        subject.vault.store(AuthTokens(TestToken.make(expiresIn = 3_600), "r1"))
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        subject.vault.invalidateSession(SessionInvalidationReason.Unauthorized)

        assertNull(subject.store.value<String>(KeychainKey.AccessToken))
        assertTrue(!subject.vault.hasStoredSession())
        assertEquals(listOf<SessionState>(SessionState.SignedOut), seen)
        subject.bus.finish()
    }

    @Test
    fun aClearedVaultKeepsTheRefreshTokenOutOfReach() = runTest {
        val subject = VaultSubject()
        subject.vault.store(AuthTokens(TestToken.make(expiresIn = 3_600), "r1"))

        subject.vault.clear()

        assertTrue(!subject.vault.hasStoredSession())
        assertFailsWith<AppError.Unauthorized> {
            subject.vault.validAccessToken(forceRefresh = false)
        }
    }

    @Test
    fun aStoredSessionSurvivesAFreshVaultAsARelaunchWouldFindIt() = runTest {
        val access = InMemoryKeychain()
        val store = KeychainStore(access)
        val clock = TestDateProvider()
        val first = TokenVault(store, ScriptedAuthRemote(), SessionStateBus(), clock)
        val token = TestToken.make(expiresIn = 3_600)
        first.store(AuthTokens(token, "r1"))

        // A second vault over the same keychain, as a relaunch constructs: empty caches, so
        // this is the only thing that can answer honestly.
        val second = TokenVault(store, ScriptedAuthRemote(), SessionStateBus(), clock)

        assertTrue(second.hasStoredSession())
        assertEquals(token, second.validAccessToken(forceRefresh = false))
    }

    @Test
    fun aTokenThatWasReadFromTheKeychainBeforeASignOutIsNotHandedOutAfterIt() = runTest {
        // The window the in-flight-refresh test above does not cover: not a response that
        // arrives late, but a *read* that does. The vault caches keychain values in memory
        // to keep the interceptor off a platform call, and that write-back is the dangerous
        // moment — a sign-out landing just before it would have its own wipe undone by the
        // value the read had already captured.
        val keychain = PausableKeychain()
        val token = TestToken.make(expiresIn = 3_600)
        keychain.seed(AuthTokens(token, "r1"))

        val clock = TestDateProvider()
        clock.set(NOW)
        val bus = SessionStateBus()
        val remote = ScriptedAuthRemote()
        val vault = TokenVault(KeychainStore(keychain), remote, bus, clock)

        // Started eagerly so the request is parked inside the keychain read — on a `null` in
        // the cold cache, which is what a relaunch looks like — before the sign-out below.
        val reading = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { vault.validAccessToken(forceRefresh = false) }
        }
        keychain.awaitRead()

        // A cold cache is the precondition: with a token already in memory there is no
        // keychain read to interleave with, and the test would pass without reaching the code
        // it exists to cover.
        vault.clear()
        assertEquals(emptyList(), keychain.keys(), "the wipe must land before the read returns")

        keychain.releaseRead()

        // A resurrected cache would satisfy this call with the pre-wipe token, so the
        // assertion is about what the caller gets and not about the keychain, which is
        // already empty either way.
        assertFailsWith<AppError.Unauthorized> { reading.await().getOrThrow() }
        assertNull(vault.storedAccessToken())
        bus.finish()
    }

    @Test
    fun aRefreshThatLandsAfterASignOutIsDiscardedRatherThanResurrectingIt() = runTest {
        val subject = VaultSubject()
        subject.remote.setRefreshDelay(200.milliseconds)
        subject.remote.setRefreshResults(
            listOf(Result.success(AuthTokens(TestToken.make(expiresIn = 3_600), "r2"))),
        )
        subject.vault.store(AuthTokens(TestToken.make(expiresIn = -10), "r1"))

        // `supervisorScope` because this coroutine is *expected* to fail, and both of the
        // obvious homes report that failure as a failed test:
        //   - a plain `async` on the test scope: under structured concurrency a failing child
        //     cancels its parent, so `runTest` sees the exception even though the `runCatching`
        //     below consumes it.
        //   - `backgroundScope`: also a supervisor scope, but `runTest` deliberately fails on
        //     an uncaught exception from it, which is right for a collector that throws and
        //     wrong for a request whose rejection *is* the thing under test.
        // A supervisor scope says exactly what is meant: this child may fail, and only it.
        supervisorScope {
            // `UNDISPATCHED` so the request is genuinely on the wire — parked on the scripted
            // remote's latency — before the sign-out. A plain `async` would not start until
            // the scheduler ran it, which under `runTest` is *after* `clear()`, and the test
            // would pass by never making a request at all.
            val inFlight = async(start = CoroutineStart.UNDISPATCHED) {
                subject.vault.validAccessToken(forceRefresh = false)
            }
            assertEquals(1, subject.remote.refreshCount, "the refresh must be in flight by now")

            // The user gives up in the meantime.
            subject.vault.clear()
            val result = runCatching { inFlight.await() }.exceptionOrNull()

            assertTrue(
                result is AppError.Unauthorized,
                "a refresh that returns after a wipe must not hand back a session, got $result",
            )
        }
        assertTrue(!subject.vault.hasStoredSession(), "the wipe must stand")
    }

    @Test
    fun aKeychainFailureOnStoreIsReportedAsStorageRatherThanEscaping() = runTest {
        val subject = VaultSubject()
        subject.access.failWith(KeychainError(status = -25_300, operation = "set", key = "auth"))

        // `KeychainError` must not cross the data layer's boundary: the port this class
        // satisfies is declared in terms of `AppError`, and nothing above may name a
        // keychain type.
        assertFailsWith<AppError.Storage> {
            subject.vault.store(AuthTokens(TestToken.make(expiresIn = 3_600), "r1"))
        }
        subject.access.stopFailing()
    }
}
