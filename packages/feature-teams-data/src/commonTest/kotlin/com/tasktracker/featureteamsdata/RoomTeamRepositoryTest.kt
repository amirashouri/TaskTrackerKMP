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
}
