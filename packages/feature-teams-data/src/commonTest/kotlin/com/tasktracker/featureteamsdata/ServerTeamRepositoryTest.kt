package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.TeamPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ADA_ID: String = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
private const val GRACE_ID: String = "6b29fc40-ca47-1067-b31d-00dd010662da"
private const val AMIR_ID: String = "0a000000-0000-4000-8000-000000000009"

private val signedInUser = User(
    id = UserID(AMIR_ID),
    email = "amir@example.com",
    displayName = "Amir",
)

/**
 * The server-backed directory's composition behaviour: what it fetches, when it refreshes,
 * and what it does when the server cannot answer.
 *
 * The empty-query listing is the picker's first load, so that is the refresh point; a typed
 * query is deliberately served from the cached list so a four-letter search does not cost
 * four round trips.
 */
class ServerTeamRepositoryTest {

    private fun repository(
        client: HttpClient,
        local: TeamPort = StubLocalTeam(signedInUser),
    ) = ServerTeamRepository(local = local, remote = HttpTeamRemote(client))

    private fun memberJson(id: String, name: String, email: String): String =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("id", id)
                    put("email", email)
                    put("display_name", name)
                    put("created_at", 1_600_000_000_000L)
                },
            )
        }.toString()

    private fun memberList(vararg rows: Pair<String, Pair<String, String>>): String =
        buildJsonArray {
            rows.forEach { (id, nameAndEmail) ->
                add(
                    buildJsonObject {
                        put("id", id)
                        put("email", nameAndEmail.second)
                        put("display_name", nameAndEmail.first)
                        put("created_at", 1_600_000_000_000L)
                    },
                )
            }
        }.toString()

    @Test
    fun aBlankQueryReturnsTheServerDirectorySortedByGreeting() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            memberList(
                GRACE_ID to ("Grace Hopper" to "grace@example.com"),
                ADA_ID to ("Ada Lovelace" to "ada@example.com"),
            ),
        )

        val users = repository(client).searchMembers("")

        assertEquals(
            listOf("Ada Lovelace", "Grace Hopper"),
            users.map { it.preferredGreeting },
            "the server's newest-first order must not decide the dropdown's order",
        )
    }

    @Test
    fun aTypedQueryFiltersTheCachedDirectoryWithoutAnotherRequest() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            memberList(
                ADA_ID to ("Ada Lovelace" to "ada@example.com"),
                GRACE_ID to ("Grace Hopper" to "grace@example.com"),
            ),
        )
        val repo = repository(client)

        repo.searchMembers("")
        val matches = repo.searchMembers("gRaCe")

        assertEquals(listOf("Grace Hopper"), matches.map { it.preferredGreeting })
        assertEquals(1, client.requests.size, "typing must reuse the initial listing")
    }

    @Test
    fun selectedIdsResolveThroughTheServerDirectory() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberJson(GRACE_ID, "Grace Hopper", "grace@example.com"))
        val repo = repository(client)

        val resolved = repo.users(listOf(UserID(GRACE_ID)))

        assertEquals(listOf(UserID(GRACE_ID)), resolved.map { it.id })
        assertEquals("Grace Hopper", resolved.single().preferredGreeting)
    }

    @Test
    fun aBlankQueryRefreshesTheDirectory() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberJson(ADA_ID, "Ada Lovelace", "ada@example.com"))
        client.respondWith(
            memberList(
                ADA_ID to ("Ada Lovelace" to "ada@example.com"),
                GRACE_ID to ("Grace Hopper" to "grace@example.com"),
            ),
        )
        val repo = repository(client)

        repo.searchMembers("")
        val refreshed = repo.searchMembers("")

        assertEquals(2, refreshed.size, "opening the picker again must see a member added since")
        assertEquals(2, client.requests.size)
    }

    @Test
    fun anUnreachableServerFallsBackToTheSessionAccount() = runTest {
        val repo = repository(FailingHttpClient())

        assertEquals(signedInUser, repo.user(signedInUser.id))
        assertEquals(
            listOf("Amir"),
            repo.searchMembers("").map { it.preferredGreeting },
            "the picker must stay usable offline, naming the one account this device knows",
        )
        assertTrue(repo.users(listOf(UserID(GRACE_ID))).isEmpty())
    }
}

/** The session-backed half, reduced to the one account these tests need. */
private class StubLocalTeam(private val signedIn: User) : TeamPort {

    override suspend fun user(id: UserID): User? = signedIn.takeIf { it.id == id }

    override suspend fun users(ids: List<UserID>): List<User> =
        ids.mapNotNull { id -> signedIn.takeIf { it.id == id } }

    override suspend fun searchMembers(query: String): List<User> =
        searchDirectory(listOf(signedIn), query)
}

/** A transport that never reaches a server, for the fallback path. */
private class FailingHttpClient : HttpClient {

    private val failure = AppError.Transport("connection refused")

    override suspend fun send(request: HttpRequest): ByteArray = throw failure

    override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T = throw failure

    override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
        throw failure
    }
}
