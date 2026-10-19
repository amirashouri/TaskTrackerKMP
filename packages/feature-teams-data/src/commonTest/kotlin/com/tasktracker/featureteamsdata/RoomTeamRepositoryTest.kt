package com.tasktracker.featureteamsdata

import com.tasktracker.core.randomUserID
import com.tasktracker.featureteamsdomain.UserStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RoomTeamRepositoryTest {

    @Test
    fun resolvesTheSignedInUserFromTheSessionCache() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        val id = session.signIn()

        val user = repo.user(id)

        // The ID match is exact (UserID value equality), not a name compare — the defect the
        // directory exists to avoid is resolving the wrong person because two share a name.
        assertEquals(id, user?.id)
        assertEquals("amir@example.com", user?.email)
        assertEquals("Amir", user?.displayName)
        assertEquals(UserStatus.Active, user?.status)
        // lastSeen is the closest signal the cache carries: the last sign-in instant.
        assertEquals(Moments.start, user?.lastSeen)
        assertEquals("Amir", user?.preferredGreeting)
    }

    @Test
    fun returnsNullForAnIdThatIsNotTheCurrentSession() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        session.signIn()
        val stranger = randomUserID()

        assertNull(repo.user(stranger))
    }

    @Test
    fun returnsNullWhenNobodyIsSignedIn() = runTest {
        val repo = RoomTeamRepository(sessionCache = StubSessionCache())

        assertNull(repo.user(randomUserID()))
        assertEquals(emptyList(), repo.users(listOf(randomUserID())))
    }

    @Test
    fun usersReturnsOnlyTheIdsItCanResolve() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        val current = session.signIn(displayName = "Mina")
        val stranger = randomUserID()

        val resolved = repo.users(listOf(stranger, current))

        assertEquals(1, resolved.size)
        assertEquals(current, resolved.single().id)
        assertEquals("Mina", resolved.single().preferredGreeting)
    }

    @Test
    fun aBlankQueryReturnsTheSessionUserOnly() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        session.signIn(displayName = "Amir")

        val everyone = repo.searchMembers("")

        // The offline directory is exactly one person: nobody else is a real account this
        // device knows. A fixture leaking back in here is the defect this assertion names.
        assertEquals(
            listOf("Amir"),
            everyone.map { it.preferredGreeting },
            "expected only the signed-in user without a backend directory",
        )
    }

    @Test
    fun aBlankQueryIsEmptyWhenNobodyIsSignedIn() = runTest {
        val repo = RoomTeamRepository(sessionCache = StubSessionCache())

        assertEquals(emptyList(), repo.searchMembers(""))
    }

    @Test
    fun theQueryFiltersTheSessionUserByNameCaseInsensitively() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        session.signIn(displayName = "Grace Hopper")

        val matches = repo.searchMembers("gRaCe")

        assertEquals(
            listOf("Grace Hopper"),
            matches.map { it.preferredGreeting },
            "expected the session user, matched regardless of the query's case",
        )
        assertEquals(emptyList(), repo.searchMembers("ada"), "nobody else is in the offline directory")
    }

    @Test
    fun theQueryFiltersTheSessionUserByEmailAsWellAsName() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        session.signIn(email = "katherine@example.com", displayName = "Katherine Johnson")

        val byEmail = repo.searchMembers("katherine@")

        assertEquals(listOf("Katherine Johnson"), byEmail.map { it.preferredGreeting })
        assertEquals(emptyList(), repo.searchMembers("@elsewhere"), "a non-matching address filters out")
    }

    @Test
    fun theSessionUserIsSearchableByTheirOwnName() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        val id = session.signIn(displayName = "Amir")

        val matches = repo.searchMembers("amir")

        assertEquals(
            listOf(id),
            matches.map { it.id },
            "expected the signed-in user to be findable by name like anyone else",
        )
    }
}
