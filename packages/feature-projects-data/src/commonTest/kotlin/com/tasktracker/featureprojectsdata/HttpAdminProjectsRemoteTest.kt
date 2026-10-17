package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomUserID
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The admin projects remote's wire contract, against a recorded transport.
 *
 * The `status` query value is asserted by *value* in both directions — `active` for the default
 * bucket, `archived` for the other — because that is what a swapped or hard-coded bucket gets
 * wrong silently: the server answers `200` with the wrong set, and a screen showing archived
 * work under "Active" is a bug no decode assertion can see.
 */
class HttpAdminProjectsRemoteTest {

    @Test
    fun listProjectsRequestsTheActiveBucket() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(listOf(projectBody(name = "Demo")).json())

        val projects = HttpAdminProjectsRemote(client).projects(ProjectAdminStatus.Active)

        assertEquals(1, projects.size)
        assertEquals("Demo", projects.single().project.name)
        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/admin/projects", request.path)
        assertEquals(listOf(HttpQueryItem(name = "status", value = "active")), request.queryItems)
        assertEquals(true, request.requiresAuth)
    }

    @Test
    fun listProjectsRequestsTheArchivedBucketWhenAsked() = runTest {
        val client = RecordingHttpClient()
        client.respondWith("[]")

        HttpAdminProjectsRemote(client).projects(ProjectAdminStatus.Archived)

        assertEquals(
            listOf(HttpQueryItem(name = "status", value = "archived")),
            client.requests.single().queryItems,
        )
    }

    @Test
    fun listProjectsDecodesTheProjectAndItsVersion() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            listOf(
                projectBody(
                    id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    keyPrefix = "TT",
                    name = "Alpha",
                    details = "notes",
                    memberIds = listOf("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                    isArchived = false,
                    nextItemNumber = 3,
                    createdAt = "2026-09-26T20:13:32Z",
                    updatedAt = "2026-09-26T20:14:00Z",
                    version = "v1",
                ),
            ).json(),
        )

        val found = HttpAdminProjectsRemote(client).projects(ProjectAdminStatus.Active).single()

        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", found.project.id.stringValue)
        assertEquals("TT", found.project.keyPrefix)
        assertEquals("Alpha", found.project.name)
        assertEquals("notes", found.project.details)
        assertEquals(3, found.project.nextItemNumber)
        assertEquals(false, found.project.isArchived)
        assertEquals("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", found.project.memberIDs.single().stringValue)
        // Round-tripped through the shared formatter rather than compared to a hand-computed
        // epoch: the point is that the ISO-8601 the server sent survived, not that 2026 is
        // some particular number of seconds.
        assertEquals("2026-09-26T20:13:32Z", DateCoding.format(found.project.createdAt))
        assertEquals("v1", found.version)
    }

    /**
     * An added key is a decoding failure while `ignoreUnknownKeys` is off — the client-first
     * rule the parent repo's AGENTS.md states, exercised on this DTO rather than assumed.
     */
    @Test
    fun anUnknownResponseKeyIsADecodingFailure() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Demo").withUnknownKey())

        assertFailsWith<AppError.Decoding> {
            HttpAdminProjectsRemote(client).projects(ProjectAdminStatus.Active)
        }
    }

    @Test
    fun projectRequestsTheRightPath() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Found"))
        val id = randomProjectID()

        HttpAdminProjectsRemote(client).project(id)

        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/admin/projects/${id.stringValue}", request.path)
        assertEquals(true, request.requiresAuth)
    }

    @Test
    fun projectDecodesTheRowWithItsVersion() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            projectBody(
                id = "cccccccc-cccc-cccc-cccc-cccccccccccc",
                name = "Found",
                isArchived = true,
                version = "v7",
            ),
        )

        val found = HttpAdminProjectsRemote(client).project(randomProjectID())

        assertNotNull(found)
        assertEquals("Found", found.project.name)
        assertEquals(true, found.project.isArchived)
        assertEquals("v7", found.version)
    }

    /**
     * A `404` is absence, not a failure: the screen shows "this project is gone" for it and a
     * retry for anything else, so the two must not collapse into one another.
     */
    @Test
    fun projectAnswersNullWhenTheServerDoesNotHaveIt() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.NotFound("Not found (404)."))

        assertNull(HttpAdminProjectsRemote(client).project(randomProjectID()))
    }

    /**
     * Every failure that is not absence still reaches the caller.
     *
     * The case is [AppError.Unknown] deliberately: a `404` used to arrive as exactly that, so a
     * catch that widened back to `Unknown` would swallow this too — the two tests fail together
     * rather than this one passing for the wrong reason.
     */
    @Test
    fun projectPropagatesFailuresThatAreNotAbsence() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Unknown("Unexpected status 405. "))

        assertFailsWith<AppError.Unknown> {
            HttpAdminProjectsRemote(client).project(randomProjectID())
        }
    }

    @Test
    fun updateProjectSendsTheWholeProjectAndTheVersionItRead() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Renamed", isArchived = true, version = "v2"))

        val id = randomProjectID()
        val project = aProject(id = id, name = "Original", details = "old", isArchived = false)
        val updated = HttpAdminProjectsRemote(client).updateProject(project, version = "v1")

        val request = client.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/v1/admin/projects/${id.stringValue}", request.path)
        assertEquals(true, request.requiresAuth)

        val body = Json.decodeFromString<JsonObject>(request.body!!.decodeToString())
        assertEquals(id.stringValue, body["id"]?.jsonPrimitive?.content)
        assertEquals(project.keyPrefix, body["keyPrefix"]?.jsonPrimitive?.content)
        assertEquals("Original", body["name"]?.jsonPrimitive?.content)
        assertEquals("old", body["details"]?.jsonPrimitive?.content)
        assertEquals(false, body["isArchived"]?.jsonPrimitive?.boolean)
        assertEquals(project.nextItemNumber, body["nextItemNumber"]?.jsonPrimitive?.int)
        assertEquals(DateCoding.format(project.createdAt), body["createdAt"]?.jsonPrimitive?.content)
        assertEquals(DateCoding.format(project.updatedAt), body["updatedAt"]?.jsonPrimitive?.content)
        assertEquals("v1", body["version"]?.jsonPrimitive?.content)

        // The reply's version is the one the caller saves against next time.
        assertEquals("v2", updated.version)
        assertEquals("Renamed", updated.project.name)
    }

    /**
     * A `409` is the whole point of sending the version back: the server refuses a save made
     * against a version that has moved on, and the caller has to see that as a conflict rather
     * than as a save.
     */
    @Test
    fun updateProjectPropagatesAConflict() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Conflict(entity = "project"))

        assertFailsWith<AppError.Conflict> {
            HttpAdminProjectsRemote(client)
                .updateProject(aProject(), version = "stale")
        }
    }

    @Test
    fun createProjectPostsTheFieldsAndAnIdItMinted() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Gemini", keyPrefix = "GE", version = "v1"))
        val memberID = randomUserID()

        val created = HttpAdminProjectsRemote(client)
            .createProject("Gemini", "GE", "Two milestones.", listOf(memberID))

        val request = client.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/admin/projects", request.path)
        assertEquals(true, request.requiresAuth)
        // Opted into replay, and the id in the body is what makes that safe: an attempt replayed
        // after a dropped response is refused for an id the server already holds rather than
        // creating a second project. Nothing else in this body is the server's to decide.
        assertEquals(
            true,
            request.effectiveIdempotency,
            "the create is not replayable, so a response lost after the row landed reads as a " +
                "create that failed",
        )

        val body = Json.decodeFromString<JsonObject>(request.body!!.decodeToString())
        assertNotNull(
            body["id"]?.jsonPrimitive?.content,
            "the client minted no id, so a `409` for an id it holds cannot be read back as this " +
                "call's own project",
        )
        assertEquals("Gemini", body["name"]?.jsonPrimitive?.content)
        assertEquals("GE", body["keyPrefix"]?.jsonPrimitive?.content)
        assertEquals("Two milestones.", body["details"]?.jsonPrimitive?.content)
        assertEquals(
            listOf(memberID.stringValue),
            body["memberIds"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            setOf("id", "name", "keyPrefix", "details", "memberIds"),
            body.keys,
            "the create body carries a field the server owns, which the server then has to " +
                "ignore or be overwritten by",
        )

        assertEquals("Gemini", created.project.name)
        assertEquals("v1", created.version)
    }

    /**
     * A create whose reply was lost is read back rather than reported as a failure.
     *
     * The server refuses a create for an id it already holds — replacing the row would rewind a
     * key counter the project has already spent — so a `409` on this call's own freshly minted id
     * can only be the row an earlier attempt of this call made. The re-read asks for exactly the
     * id the create sent, which is the whole reason the client mints one.
     */
    @Test
    fun createProjectReadsAConflictBackAsTheProjectThatWonIt() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Conflict(entity = "project"))
        client.respondWith(projectBody(name = "Gemini", version = "v3"))

        val created = HttpAdminProjectsRemote(client)
            .createProject("Gemini", "GE", "", emptyList())

        val post = client.requests.first()
        val reread = client.requests.last()
        val sentID = Json.decodeFromString<JsonObject>(post.body!!.decodeToString())["id"]!!
            .jsonPrimitive.content
        assertEquals(HttpMethod.Get, reread.method)
        assertEquals(
            "/v1/admin/projects/$sentID",
            reread.path,
            "the re-read asked for an id other than the one the create sent, so a lost reply " +
                "would surface as a failed create whose obvious answer is to create it again",
        )
        assertEquals("Gemini", created.project.name)
        assertEquals("v3", created.version)
    }

    /**
     * The conflict is only recoverable while the row is there: a `404` behind it means the project
     * was removed between the two calls, and there is nothing to hand back.
     */
    @Test
    fun createProjectPropagatesAConflictForAnIdTheServerDoesNotHave() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Conflict(entity = "project"))
        client.respondWithFailure(AppError.NotFound("Not found (404)."))

        assertFailsWith<AppError.Conflict> {
            HttpAdminProjectsRemote(client).createProject("Gemini", "GE", "", emptyList())
        }
    }

    /** One project payload, with every field named so a test states only what it is about. */
    private fun projectBody(
        id: String = "11111111-1111-1111-1111-111111111111",
        keyPrefix: String = "KP",
        name: String = "Name",
        details: String = "",
        memberIds: List<String> = emptyList(),
        isArchived: Boolean = false,
        nextItemNumber: Int = 1,
        createdAt: String = "2026-01-01T00:00:00Z",
        updatedAt: String = "2026-01-01T00:00:01Z",
        version: String = "",
    ): String = buildString {
        append("""{"id":"$id","keyPrefix":"$keyPrefix","name":"$name","details":"$details",""")
        append(""""memberIds":[${memberIds.joinToString(",") { "\"$it\"" }}],""")
        append(""""isArchived":$isArchived,"nextItemNumber":$nextItemNumber,""")
        append(""""createdAt":"$createdAt","updatedAt":"$updatedAt","version":"$version"}""")
    }

    private fun List<String>.json(): String = "[${joinToString(",")}]"

    /** The same payload with a key no client version declares. */
    private fun String.withUnknownKey(): String = dropLast(1) + ""","serverAdded":"surprise"}"""
}
