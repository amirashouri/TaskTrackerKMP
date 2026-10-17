package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.randomProjectID
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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
        client.respondWithFailure(AppError.Unknown("Not found (404)."))

        assertNull(HttpAdminProjectsRemote(client).project(randomProjectID()))
    }

    /** Every failure that is not absence still reaches the caller. */
    @Test
    fun projectPropagatesFailuresThatAreNotAbsence() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Server(503, "maintenance"))

        assertFailsWith<AppError.Server> {
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
