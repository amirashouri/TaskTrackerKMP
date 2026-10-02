package com.tasktracker.featureauthdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JWT
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.parseUserID
import com.tasktracker.core.randomUserID
import com.tasktracker.featureauthdomain.AuthTokens
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
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
) = TestToken.make(subject = subject, email = email, expiresIn = expiresIn)

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
}
