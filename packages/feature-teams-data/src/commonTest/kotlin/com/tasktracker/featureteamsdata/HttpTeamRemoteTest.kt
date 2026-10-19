package com.tasktracker.featureteamsdata

import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real directory remote's wire shape.
 *
 * The properties worth pinning are invisible in a response: the path, the `q` parameter, and
 * `requiresAuth` — the route is open to any signed-in member but rejects an anonymous call.
 * The decode half covers the DTO's `@SerialName` list, because `JSONCoding` leaves
 * `ignoreUnknownKeys` off and a missing `created_at` declaration would fail the whole list.
 */
class HttpTeamRemoteTest {

    private fun remote(client: RecordingHttpClient) = HttpTeamRemote(client)

    private fun memberObject(
        id: String = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
        email: String = "ada@example.com",
        displayName: String? = "Ada",
        position: String? = "Lead",
        avatarId: String? = null,
        status: String? = "active",
        role: String? = "admin",
        lastSeen: Long? = 1_700_000_000_000L,
        createdAt: Long = 1_600_000_000_000L,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("email", email)
        if (displayName != null) put("display_name", displayName)
        if (position != null) put("position", position)
        if (avatarId != null) put("avatar_id", avatarId)
        if (status != null) put("status", status)
        if (role != null) put("role", role)
        if (lastSeen != null) put("last_seen", lastSeen)
        put("created_at", createdAt)
    }

    private fun memberArray(entries: List<JsonObject>): String =
        buildJsonArray { entries.forEach { add(it) } }.toString()

    @Test
    fun aBlankQueryAsksTheDirectoryForEverything() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberArray(listOf(memberObject())))

        remote(client).searchMembers("")

        val request = assertNotNull(client.requests.firstOrNull())
        assertEquals("/v1/users/search", request.path)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("", request.queryItems.single { it.name == "q" }.value)
        assertTrue(request.requiresAuth, "the directory route rejects an anonymous call")
    }

    @Test
    fun aQueryTravelsAsTheQParameter() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberArray(emptyList()))

        remote(client).searchMembers("grace")

        assertEquals(
            "grace",
            assertNotNull(client.requests.first()).queryItems.single { it.name == "q" }.value,
        )
    }

    @Test
    fun theDirectoryDecodesIntoDomainRows() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            memberArray(
                listOf(
                    memberObject(status = "active", role = "admin"),
                    memberObject(
                        id = "6b29fc40-ca47-1067-b31d-00dd010662da",
                        email = "grace@example.com",
                        displayName = null,
                        position = null,
                        status = null,
                        role = null,
                        lastSeen = null,
                    ),
                ),
            ),
        )

        val users = remote(client).searchMembers("")

        assertEquals(2, users.size)
        assertEquals("Ada", users[0].preferredGreeting)
        assertEquals(UserStatus.Active, users[0].status)
        assertEquals(MemberRole.Admin, users[0].role)
        // Absent optional fields read as their defaults rather than failing the decode.
        assertEquals("grace", users[1].preferredGreeting)
        assertEquals(UserStatus.Active, users[1].status)
        assertEquals(MemberRole.Member, users[1].role)
    }
}
