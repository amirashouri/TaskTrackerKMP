package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JWT
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.parseUserID
import com.tasktracker.core.randomUserID
import com.tasktracker.featureauthdomain.AuthTokens
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.keychain.KeychainStore
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A repository over an in-memory keychain, a scripted remote and its own bus. */
private class RepositorySubject {
    val remote = ScriptedAuthRemote()
    val access = InMemoryKeychain()
    val store = KeychainStore(access)
    val bus = SessionStateBus()
    val defaults = UserDefaultsStore(InMemoryPreferences(), namespace = "com.tasktracker.tests")
    val clock = TestDateProvider()
    val vault = TokenVault(store, remote, bus, clock)
    val repository = AuthRepository(vault, remote, defaults, bus)
}

/** A token whose `sub` is a real, parseable account id. */
private fun token(
    subject: String = TestToken.DEFAULT_SUBJECT,
    email: String = TestToken.DEFAULT_EMAIL,
    expiresIn: Long = 3_600,
    role: String? = null,
) = TestToken.make(subject = subject, email = email, expiresIn = expiresIn, role = role)

class AuthRepositoryTest {

    @Test
    fun aSuccessfulSignInRetainsTheTokensAndAnnouncesTheIdentity() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        val identity = subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        assertEquals(TestToken.DEFAULT_SUBJECT.lowercase(), identity.id.stringValue)
        assertEquals("ada@example.com", identity.email)
        // Retained before announced: a session that cannot be replayed after a relaunch must
        // never be the one the UI is told about.
        assertTrue(subject.vault.hasStoredSession())
        assertEquals(listOf<SessionState>(SessionState.SignedIn(identity)), seen)
        subject.bus.finish()
    }

    @Test
    fun aRejectedSignInChangesNothingAndStaysSignedOut() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.failure(AppError.Unauthorized)))
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        assertFailsWith<AppError.Unauthorized> {
            subject.repository.signIn(email = "ada@example.com", password = "wrong-password")
        }

        assertTrue(!subject.vault.hasStoredSession())
        // A mistyped password must not be able to sign a working user out, and it must not
        // announce anything at all.
        assertTrue(seen.isEmpty(), "a failed sign-in published $seen")
        subject.bus.finish()
    }

    @Test
    fun restoringWithNothingOnTheDeviceLandsOnSignedOut() = runTest {
        val subject = RepositorySubject()
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        val settled = subject.repository.restore()

        assertEquals(SessionState.SignedOut, settled)
        assertEquals(
            listOf<SessionState>(SessionState.Restoring, SessionState.SignedOut),
            seen,
        )
        subject.bus.finish()
    }

    @Test
    fun restoringRebuildsTheIdentityFromTheStoredTokenNotFromACache() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        // A cache that disagrees with the credential it describes: the keychain wins, or a
        // stale profile could resurrect an account the user has since dropped.
        subject.defaults.set(
            UserIdentity(id = randomUserID(), email = "someone.else@example.com"),
            DefaultsKey.CachedProfile,
        )
        val secondBus = SessionStateBus()
        val second = AuthRepository(subject.vault, subject.remote, subject.defaults, secondBus)
        val seen = mutableListOf<SessionState>()
        collecting(secondBus.states(), seen)

        val settled = second.restore()

        val identity = assertIs<SessionState.SignedIn>(settled).identity
        assertEquals("ada@example.com", identity.email)
        assertEquals(TestToken.DEFAULT_SUBJECT.lowercase(), identity.id.stringValue)
        subject.bus.finish()
        secondBus.finish()
    }

    @Test
    fun restoringFallsBackToTheCachedProfileWhenTheTokenCannotBeRead() = runTest {
        val subject = RepositorySubject()
        val cached = UserIdentity(id = randomUserID(), email = "ada@example.com", displayName = "Ada")
        subject.defaults.set(cached, DefaultsKey.CachedProfile)
        // A refresh token with an access token that is no longer parseable — the exact case
        // the cached profile exists for.
        subject.vault.store(AuthTokens("not-a-jwt", "r1"))

        val settled = subject.repository.restore()

        assertEquals(SessionState.SignedIn(cached), settled)
    }

    @Test
    fun aHalfSessionWithNeitherTokenNorCacheSignsOutRatherThanClaimingAUser() = runTest {
        val subject = RepositorySubject()
        subject.vault.store(AuthTokens("not-a-jwt", "r1"))

        val settled = subject.repository.restore()

        // Claiming a signed-in user the app cannot name is worse than admitting it has none.
        assertEquals(SessionState.SignedOut, settled)
        assertTrue(!subject.vault.hasStoredSession())
    }

    @Test
    fun signOutWipesTheCredentialsAndTheCachedProfile() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        assertTrue(subject.defaults.hasValue(DefaultsKey.CachedProfile))

        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)
        subject.repository.signOut()

        assertTrue(!subject.vault.hasStoredSession())
        assertNull(subject.defaults.value<UserIdentity>(DefaultsKey.CachedProfile))
        assertEquals(listOf<SessionState>(SessionState.SignedOut), seen)
        subject.bus.finish()
    }

    @Test
    fun aRevocationThatReachesTheServerRetiresTheStoredFamilyExactlyOnce() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        subject.repository.signOut()

        // The *stored refresh* token, not the access token: revoking the wrong family would
        // leave the one that can mint new access tokens alive on the server while every
        // count-based assertion still passed.
        assertEquals(listOf("r1"), subject.remote.seenSignOutTokens)
        assertEquals(1, subject.remote.signOutCount)
        assertEquals(listOf<SessionState>(SessionState.SignedOut), seen)
        subject.bus.finish()
    }

    @Test
    fun aSignOutTheServerNeverAnswersGivesUpAndLeavesTheSessionIntact() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        // Silence rather than refusal: a host that accepts the connection and never answers.
        // Just past the deadline, so the test pins *that* constant rather than merely proving
        // some deadline exists.
        subject.remote.setSignOutDelay(SIGN_OUT_REVOKE_TIMEOUT + 1.seconds)
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        assertFailsWith<AppError.Timeout> { subject.repository.signOut() }

        // The ordering invariant, and the reason the popup can exist at all: the failure
        // propagates *before* the wipe, so there is still a session for the user to force.
        // Wiping first would sign them out silently and never offer the choice.
        assertTrue(subject.vault.hasStoredSession())
        assertTrue(subject.defaults.hasValue(DefaultsKey.CachedProfile))
        assertTrue(seen.isEmpty(), "a blocked sign-out published $seen")
        subject.bus.finish()
    }

    @Test
    fun aRefusedRevocationIsReportedRatherThanSwallowed() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        subject.remote.setSignOutResults(listOf(Result.failure(AppError.Unauthorized)))

        assertFailsWith<AppError.Unauthorized> { subject.repository.signOut() }

        // A refusal and a timeout are the same decision from here — the caller is told, and
        // the session is left standing. What matters is that the reason survives the trip
        // instead of being flattened into a generic failure.
        assertTrue(subject.vault.hasStoredSession())
        assertEquals(1, subject.remote.signOutCount)
        subject.bus.finish()
    }

    @Test
    fun aLocalSignOutWipesTheDeviceWithoutAskingTheServer() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        val seen = mutableListOf<SessionState>()
        collecting(subject.bus.states(), seen)

        subject.repository.signOutLocally()

        assertTrue(!subject.vault.hasStoredSession())
        assertNull(subject.defaults.value<UserIdentity>(DefaultsKey.CachedProfile))
        assertEquals(listOf<SessionState>(SessionState.SignedOut), seen)
        // Not one request, even with a server that would have hung: a device with no backend
        // reaches this path on the first tap, and it must not spend the deadline finding out
        // there is nothing to reach.
        assertEquals(0, subject.remote.signOutCount)
        subject.bus.finish()
    }

    @Test
    fun signingOutWithNothingStoredDoesNotCallTheServer() = runTest {
        val subject = RepositorySubject()

        subject.repository.signOut()

        // Nothing to revoke, so nothing to wait for — and no deadline burned on a device that
        // has never been signed in.
        assertEquals(0, subject.remote.signOutCount)
        assertTrue(!subject.vault.hasStoredSession())
    }

    @Test
    fun eachSubscriberGetsItsOwnStreamRatherThanSharingOne() = runTest {
        val subject = RepositorySubject()
        val first = mutableListOf<SessionState>()
        val second = mutableListOf<SessionState>()
        collecting(subject.bus.states(), first)
        collecting(subject.bus.states(), second)
        assertEquals(2, subject.bus.subscriberCount)

        subject.repository.signOut()

        // A single-consumer channel handed to two listeners would starve the second, so the
        // port promises a fresh stream each time.
        assertEquals(listOf<SessionState>(SessionState.SignedOut), first)
        assertEquals(listOf<SessionState>(SessionState.SignedOut), second)
        subject.bus.finish()
    }

    @Test
    fun thePortsOwnStreamDeliversWhatTheRepositoryPublishes() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))

        // Read through the *port* rather than the bus, because that is what a screen sees.
        // The collector has to be live *before* the sign-in, since the bus drops a send with
        // nobody listening.
        val updates = subject.repository.sessionUpdates
        val firstElement = awaitingFirst(updates)
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        val published = firstElement.awaitFirst()
        assertIs<SessionState.SignedIn>(published, "expected a signed-in state, got $published")
        subject.bus.finish()
    }

    @Test
    fun theIdentityComesFromTheTokenRatherThanTheResponseEnvelope() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))

        val identity = subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        // A server may identify the account only inside the token; a copy of the profile in
        // the response is a second source of truth that can disagree with the credential.
        // The claim is compared through the identifier rather than as a string: `sub` is
        // written upper-case by the fixture, and a canonical `Uuid` stringifies lower-case.
        assertEquals(
            parseUserID(JWT.parse(token())!!.subject)!!.stringValue,
            identity.id.stringValue,
        )
    }

    @Test
    fun anAdminTokenYieldsAnAdminIdentity() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(
            listOf(Result.success(AuthTokens(token(role = "admin"), "r1"))),
        )

        val identity = subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        // The gate on the admin settings screen reads this. Dropping the claim would hide the
        // screen from the very user the server would let through it.
        assertEquals(UserRole.Admin, identity.role)
    }

    @Test
    fun aTokenWithNoRoleClaimIsAMemberRatherThanAnAdmin() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(listOf(Result.success(AuthTokens(token(), "r1"))))

        val identity = subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        // Fail closed: a token that names no role must not be read as the most privileged one.
        assertEquals(UserRole.Member, identity.role)
    }

    @Test
    fun aRoleTheClientDoesNotKnowIsAlsoJustAMember() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(
            listOf(Result.success(AuthTokens(token(role = "superuser"), "r1"))),
        )

        val identity = subject.repository.signIn(email = "ada@example.com", password = "hunter2")

        assertEquals(UserRole.Member, identity.role)
    }

    @Test
    fun restoringCarriesTheRoleOutOfTheStoredToken() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(
            listOf(Result.success(AuthTokens(token(role = "admin"), "r1"))),
        )
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        // A fresh repository over the same vault is the relaunch: the screen is gated before
        // this makes any network call, so the role has to come back with the session.
        val secondBus = SessionStateBus()
        val second = AuthRepository(subject.vault, subject.remote, subject.defaults, secondBus)

        val settled = second.restore()

        val identity = assertIs<SessionState.SignedIn>(settled).identity
        assertEquals(UserRole.Admin, identity.role)
        subject.bus.finish()
        secondBus.finish()
    }

    @Test
    fun theCachedProfileKeepsTheRoleWhenTheTokenCanNoLongerBeRead() = runTest {
        val subject = RepositorySubject()
        subject.remote.setSignInResults(
            listOf(Result.success(AuthTokens(token(role = "admin"), "r1"))),
        )
        subject.repository.signIn(email = "ada@example.com", password = "hunter2")
        // The access token has expired past readability while the session is still good — the
        // one case the cached profile exists for. The role has to survive in it, or an admin
        // would lose the screen on a refresh that outlives the token they signed in with.
        subject.vault.store(AuthTokens("not-a-jwt", "r1"))

        val settled = subject.repository.restore()

        val identity = assertIs<SessionState.SignedIn>(settled).identity
        assertEquals(UserRole.Admin, identity.role)
    }
}
