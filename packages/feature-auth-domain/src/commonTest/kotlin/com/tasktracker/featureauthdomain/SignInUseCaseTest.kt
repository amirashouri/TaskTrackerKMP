package com.tasktracker.featureauthdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.randomUserID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignInUseCaseTest {

    @Test
    fun aValidAddressReachesThePortNormalised() = runTest {
        val session = FakeAuthSession()

        SignIn(session)(email = "  Ada@Example.COM ", password = "hunter2")

        assertEquals(1, session.signInCalls.size)
        assertEquals("ada@example.com", session.signInCalls.single().first)
        // The password is passed through byte for byte.
        assertEquals("hunter2", session.signInCalls.single().second)
    }

    @Test
    fun aMalformedAddressNeverReachesThePort() = runTest {
        val session = FakeAuthSession()

        assertFailsWith<AppError.Validation> {
            SignIn(session)(email = "not-an-address", password = "hunter2")
        }

        assertTrue(session.signInCalls.isEmpty())
    }

    @Test
    fun anEmptyPasswordNeverReachesThePort() = runTest {
        val session = FakeAuthSession()

        assertFailsWith<AppError.Validation> {
            SignIn(session)(email = "ada@example.com", password = "")
        }

        assertTrue(session.signInCalls.isEmpty())
    }

    @Test
    fun aPasswordWithTrailingWhitespaceSurvivesIntact() = runTest {
        val session = FakeAuthSession()

        SignIn(session)(email = "ada@example.com", password = "hunter2 ")

        // Trimming a password silently changes the credential, and the user is the only one
        // who can say which is the right one.
        assertEquals("hunter2 ", session.signInCalls.single().second)
    }

    @Test
    fun aFailureFromThePortIsSurfacedNotSwallowed() = runTest {
        val session = FakeAuthSession()
        session.setFailure(AppError.Unauthorized)

        assertFailsWith<AppError.Unauthorized> {
            SignIn(session)(email = "ada@example.com", password = "wrong-password")
        }
    }
}

class SessionUseCasesTest {

    @Test
    fun restoreDelegatesToThePortAndReturnsItsOutcome() = runTest {
        val session = FakeAuthSession()
        val expected = SessionState.SignedIn(
            UserIdentity(id = randomUserID(), email = "ada@example.com"),
        )
        session.setStateToRestore(expected)

        val settled = RestoreSession(session)()

        assertEquals(1, session.restoreCount)
        assertEquals(expected, settled)
    }

    @Test
    fun aRestoreThatFailsSettlesOnSignedOutRatherThanThrowing() = runTest {
        val session = FakeAuthSession()
        session.setFailure(AppError.Transport("no route to host"))

        // A stored refresh token the server no longer honours is the ordinary end of a
        // session that outlived its credentials. Surfacing it would be a launch that cannot
        // complete.
        assertEquals(SessionState.SignedOut, RestoreSession(session)())
    }

    @Test
    fun signOutDelegatesToThePort() = runTest {
        val session = FakeAuthSession()

        SignOut(session)()

        assertEquals(1, session.signOutCount)
    }
}

class UserIdentityTest {

    @Test
    fun aNameIsPreferredForTheGreeting() {
        val named = UserIdentity(
            id = randomUserID(),
            email = "ada@example.com",
            displayName = "Ada Lovelace",
        )

        assertEquals("Ada Lovelace", named.preferredGreeting)
    }

    @Test
    fun withoutANameTheGreetingFallsBackToTheAddressLocalPart() {
        val anonymous = UserIdentity(id = randomUserID(), email = "ada@example.com")

        assertEquals("ada", anonymous.preferredGreeting)
    }

    @Test
    fun anEmptyNameIsTreatedAsNoName() {
        val blank = UserIdentity(id = randomUserID(), email = "ada@example.com", displayName = "")

        assertEquals("ada", blank.preferredGreeting)
    }

    @Test
    fun anAddressWithNoAtSignGreetsWithTheWholeAddress() {
        // Unreachable through the sign-in path, which rejects such an address first — but a
        // cached profile written by a future schema must not render a nameless greeting.
        val malformed = UserIdentity(id = randomUserID(), email = "not-an-address")

        assertEquals("not-an-address", malformed.preferredGreeting)
    }

    @Test
    fun anIdentityIsEqualToOneBuiltFromTheSameParts() {
        val id = randomUserID()

        assertEquals(
            UserIdentity(id = id, email = "ada@example.com", displayName = "Ada"),
            UserIdentity(id = id, email = "ada@example.com", displayName = "Ada"),
        )
    }
}
