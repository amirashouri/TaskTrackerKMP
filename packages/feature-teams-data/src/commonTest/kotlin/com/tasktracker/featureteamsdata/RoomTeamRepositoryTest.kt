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
    fun aBlankQueryReturnsTheWholeDirectoryIncludingTheSessionUser() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        session.signIn(displayName = "Amir")

        val everyone = repo.searchMembers("")

        // Seven people: the six in the fake directory plus the signed-in user. The order is
        // alphabetical by greeting across the merge, so the session user sits among the
        // fake people rather than being pinned to a row — and the message names the count
        // because "empty list" and "session user only" are the two ways this breaks when
        // the merge is wrong.
        assertEquals(
            listOf(
                "Ada Lovelace",
                "Alan Turing",
                "Amir",
                "Barbara Liskov",
                "Grace Hopper",
                "Katherine Johnson",
                "Radia Perlman",
            ),
            everyone.map { it.preferredGreeting },
            "expected the session user plus the six fake people, sorted by name",
        )
    }

    @Test
    fun theQueryFiltersByNameCaseInsensitively() = runTest {
        val repo = RoomTeamRepository(sessionCache = StubSessionCache())

        val matches = repo.searchMembers("gRaCe")

        assertEquals(
            listOf("Grace Hopper"),
            matches.map { it.preferredGreeting },
            "expected exactly Grace, matched regardless of the query's case",
        )
    }

    @Test
    fun theQueryFiltersByEmailAsWellAsName() = runTest {
        val session = StubSessionCache()
        val repo = RoomTeamRepository(sessionCache = session)
        session.signIn(displayName = "Amir")

        val byEmail = repo.searchMembers("katherine@")
        val everyoneAtExample = repo.searchMembers("@example.com")

        assertEquals(listOf("Katherine Johnson"), byEmail.map { it.preferredGreeting })
        // All six fake people use @example.com; the session user does too, so the email
        // filter must not silently drop the one real person in the list.
        assertEquals(7, everyoneAtExample.size, "expected the email query to match everyone")
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
