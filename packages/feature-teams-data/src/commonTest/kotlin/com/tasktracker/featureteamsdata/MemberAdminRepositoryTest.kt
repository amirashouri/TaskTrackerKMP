package com.tasktracker.featureteamsdata

import com.tasktracker.core.UserID
import com.tasktracker.core.randomUserID
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The composition `MemberAdminPort` forces.
 *
 * The port extends `TeamPort`, so the interesting question is not whether each method returns
 * something but *which side it returns from* — a read that quietly round-tripped to the server,
 * or a delete that quietly went to the local directory, is the defect the split exists to
 * prevent, and either would still pass a test that only checked the return value.
 */
class MemberAdminRepositoryTest {

    private fun aMember(
        id: UserID = randomUserID(),
        email: String = "ada@example.com",
        status: UserStatus = UserStatus.Active,
        role: MemberRole = MemberRole.Member,
    ): User = User(id = id, email = email, displayName = "Ada", status = status, role = role)

    @Test
    fun readsResolveThroughTheLocalDirectoryAndNeverTheNetwork() = runTest {
        val member = aMember()
        val team = StubTeamPort(known = listOf(member))
        val client = RecordingHttpClient()
        val repo = MemberAdminRepository(HttpMemberAdminRemote(client), team)

        assertEquals(member, repo.user(member.id))
        assertEquals(listOf(member), repo.users(listOf(member.id)))
        assertEquals(listOf(member), repo.searchMembers("ada"))

        assertEquals(1, team.userCalls)
        assertEquals(1, team.usersCalls)
        assertEquals(1, team.searchCalls)
        assertEquals("ada", team.seenQuery)
        assertTrue(client.requests.isEmpty(), "a local read must not reach the server")
    }

    @Test
    fun theMemberListComesFromTheServer() = runTest {
        val client = RecordingHttpClient()
        client.respondWith("[]")
        val team = StubTeamPort(known = listOf(aMember()))
        val repo = MemberAdminRepository(HttpMemberAdminRemote(client), team)

        assertEquals(emptyList(), repo.members(UserStatus.Deleted))

        val request = assertNotNull(client.requests.singleOrNull())
        assertEquals("/v1/admin/users", request.path)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("deleted", request.queryItems.single { it.name == "status" }.value)
    }

    @Test
    fun creatingGoesToTheServer() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            """{"id":"${randomUserID()}","email":"ada@example.com","role":"admin","created_at":1}""",
        )
        val repo = MemberAdminRepository(HttpMemberAdminRemote(client), StubTeamPort())

        val created = repo.createMember(
            email = "ada@example.com",
            password = "hunter2",
            role = MemberRole.Admin,
            position = "Lead",
        )

        assertEquals("ada@example.com", created.email)
        assertEquals(MemberRole.Admin, created.role)
        assertEquals(HttpMethod.Post, assertNotNull(client.requests.singleOrNull()).method)
    }

    @Test
    fun deletingGoesToTheServerByPath() = runTest {
        val member = aMember()
        val client = RecordingHttpClient()
        val repo = MemberAdminRepository(HttpMemberAdminRemote(client), StubTeamPort(known = listOf(member)))

        repo.softDeleteMember(member.id)

        val request = assertNotNull(client.requests.singleOrNull())
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/v1/admin/users/${member.id.stringValue}", request.path)
    }

    @Test
    fun reactivatingGoesToTheServerAndReturnsTheUpdatedMember() = runTest {
        val member = aMember(status = UserStatus.Active)
        val client = RecordingHttpClient()
        client.respondWith(
            """{"id":"${member.id.stringValue}","email":"ada@example.com","status":"active","created_at":1}""",
        )
        val repo = MemberAdminRepository(HttpMemberAdminRemote(client), StubTeamPort(known = listOf(member)))

        val reactivated = repo.reactivateMember(member.id)

        assertEquals(UserStatus.Active, reactivated.status)
        assertEquals(HttpMethod.Patch, assertNotNull(client.requests.singleOrNull()).method)
    }
}

/**
 * A `TeamPort` that counts what it was asked for.
 *
 * The counts are the point: they are what lets the tests above prove a read *stayed* local rather
 * than merely returning the right value, which the HTTP client's own empty request list can only
 * confirm from its side.
 */
private class StubTeamPort(private val known: List<User> = emptyList()) : TeamPort {

    var userCalls: Int = 0
        private set
    var usersCalls: Int = 0
        private set
    var searchCalls: Int = 0
        private set
    var seenQuery: String? = null
        private set

    override suspend fun user(id: UserID): User? {
        userCalls += 1
        return known.firstOrNull { it.id == id }
    }

    override suspend fun users(ids: List<UserID>): List<User> {
        usersCalls += 1
        return known.filter { it.id in ids }
    }

    override suspend fun searchMembers(query: String): List<User> {
        searchCalls += 1
        seenQuery = query
        return known
    }
}
