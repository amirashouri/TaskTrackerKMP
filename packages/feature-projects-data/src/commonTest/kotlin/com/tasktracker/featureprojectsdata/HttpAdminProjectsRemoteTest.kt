package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomUserID
import com.tasktracker.featureprojectsdomain.Project
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class HttpAdminProjectsRemoteTest {

    @Test
    fun listProjectsRequestsTheActiveBucketByDefault() {
        var captured: HttpRequest? = null
        val client = FakeClient { request ->
            captured = request
            listOf(
                projectBody(
                    id = "11111111-1111-1111-1111-111111111111",
                    name = "Demo",
                    isArchived = false,
                ),
            )
        }

        val remote = HttpAdminProjectsRemote(client)
        val projects = remote.projects(ProjectAdminStatus.Active)

        assertEquals(1, projects.size)
        assertEquals("Demo", projects.first().name)
        assertEquals(
            HttpRequest(
                method = HttpMethod.Get,
                path = "/v1/admin/projects",
                queryItems = listOf(HttpQueryItem(name = "status", value = "active")),
                requiresAuth = true,
            ),
            captured,
        )
    }

    @Test
    fun listProjectsRequestsTheArchivedBucketWhenAsked() {
        val client = FakeClient { request ->
            assertEquals(
                listOf(HttpQueryItem(name = "status", value = "archived")),
                request.queryItems,
            )
            emptyList()
        }

        val remote = HttpAdminProjectsRemote(client)
        remote.projects(ProjectAdminStatus.Archived)
    }

    @Test
    fun listProjectsDecodesTheProjectShape() {
        val client = FakeClient { _ ->
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
            )
        }

        val remote = HttpAdminProjectsRemote(client)
        val projects = remote.projects(ProjectAdminStatus.Active)

        assertEquals(1, projects.size)
        val project = projects.first()
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", project.id.stringValue)
        assertEquals("TT", project.keyPrefix)
        assertEquals("Alpha", project.name)
        assertEquals("notes", project.details)
        assertEquals(3, project.nextItemNumber)
        assertEquals(false, project.isArchived)
        assertEquals("v1", project.version)
    }

    @Test
    fun projectRequestsTheRightPath() {
        var captured: HttpRequest? = null
        val client = FakeClient { request ->
            captured = request
            null
        }

        val remote = HttpAdminProjectsRemote(client)
        val id = randomProjectID()
        remote.project(id)

        assertEquals(
            HttpRequest(
                method = HttpMethod.Get,
                path = "/v1/admin/projects/${id.stringValue}",
                requiresAuth = true,
            ),
            captured,
        )
    }

    @Test
    fun projectDecodesTheProjectWhenFound() {
        val client = FakeClient { _ ->
            projectBody(
                id = "cccccccccccccccc-cccc-cccc-cccc-cccccccccc",
                name = "Found",
                isArchived = true,
            )
        }

        val remote = HttpAdminProjectsRemote(client)
        val project = remote.project(randomProjectID())

        assertNotNull(project)
        assertEquals("Found", project!!.name)
        assertEquals(true, project.isArchived)
    }

    @Test
    fun projectReturnsNullWhenNotFound() {
        val client = FakeClient { _ ->
            null
        }

        val remote = HttpAdminProjectsRemote(client)
        assertNull(remote.project(randomProjectID()))
    }

    @Test
    fun updateProjectRequestsTheRightPathAndBody() {
        var captured: HttpRequest? = null
        val client = FakeClient { request ->
            captured = request
            projectBody(
                id = "dddddddd-dddd-dddd-dddd-dddddddddd",
                name = "Renamed",
                isArchived = true,
                version = "v2",
            )
        }

        val remote = HttpAdminProjectsRemote(client)
        val project = Project(
            id = randomProjectID(),
            keyPrefix = "RP",
            name = "Original",
            details = "old",
            memberIDs = listOf(randomUserID(), randomUserID()),
            isArchived = false,
            nextItemNumber = 1,
            createdAt = Instant.fromEpochSeconds(1_700_000_000),
            updatedAt = Instant.fromEpochSeconds(1_700_000_001),
        )
        val updated = remote.updateProject(project, version = "v1")

        assertEquals("dddddddd-dddd-dddd-dddd-dddddddddd", updated.id.stringValue)
        assertEquals("Renamed", updated.name)
        assertEquals(true, updated.isArchived)

        val decoded = Json.decodeFromString<JsonObject>(captured!!.body.toString(Charsets.UTF_8))
        assertEquals(project.id.stringValue, decoded["id"]?.jsonPrimitive?.content)
        assertEquals("RP", decoded["keyPrefix"]?.jsonPrimitive?.content)
        assertEquals("Original", decoded["name"]?.jsonPrimitive?.content)
        assertEquals("old", decoded["details"]?.jsonPrimitive?.content)
        assertEquals(false, decoded["isArchived"]?.jsonPrimitive?.boolean)
        assertEquals(1, decoded["nextItemNumber"]?.jsonPrimitive?.int)
        assertEquals(DateCoding.format(project.createdAt), decoded["createdAt"]?.jsonPrimitive?.content)
        assertEquals(DateCoding.format(project.updatedAt), decoded["updatedAt"]?.jsonPrimitive?.content)
        assertEquals("v1", decoded["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun updateProjectDecodesTheStoredVersion() {
        val client = FakeClient { _ ->
            projectBody(
                id = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                name = "Saved",
                isArchived = false,
                version = "stored-version",
            )
        }

        val remote = HttpAdminProjectsRemote(client)
        val updated = remote.updateProject(
            Project(
                id = randomProjectID(),
                keyPrefix = "SE",
                name = "Draft",
                details = "",
                memberIDs = emptyList(),
                isArchived = true,
                nextItemNumber = 1,
                createdAt = Instant.fromEpochSeconds(1_700_000_000),
                updatedAt = Instant.fromEpochSeconds(1_700_000_001),
            ),
            version = "client-version",
        )

        assertEquals("stored-version", updated.version)
        assertEquals(false, updated.isArchived)
    }

    @Serializable
    private data class ProjectResponseBody(
        val id: String,
        val keyPrefix: String,
        val name: String,
        val details: String,
        val memberIds: List<String>,
        val isArchived: Boolean,
        val nextItemNumber: Int,
        @SerialName("createdAt") val createdAt: String,
        @SerialName("updatedAt") val updatedAt: String,
        val version: String,
    )

    private fun projectBody(
        id: String,
        keyPrefix: String = "KP",
        name: String = "Name",
        details: String = "",
        memberIds: List<String> = emptyList(),
        isArchived: Boolean = false,
        nextItemNumber: Int = 1,
        createdAt: String = "2026-01-01T00:00:00Z",
        updatedAt: String = "2026-01-01T00:00:01Z",
        version: String = "",
    ): ProjectResponseBody = ProjectResponseBody(
        id = id,
        keyPrefix = keyPrefix,
        name = name,
        details = details,
        memberIds = memberIds,
        isArchived = isArchived,
        nextItemNumber = nextItemNumber,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
    )

    private class FakeClient(
        private val responder: suspend (HttpRequest) -> Any?,
    ) : HttpClient {
        override suspend fun send(request: HttpRequest): ByteArray {
            val body = responder(request)
            return when (body) {
                null -> ByteArray(0)
                is ByteArray -> body
                else -> body.toString().toByteArray(Charsets.UTF_8)
            }
        }

        override suspend fun <T> send(request: HttpRequest, decoding: kotlinx.serialization.KSerializer<T>): T {
            val body = responder(request)
            return when (body) {
                null -> throw AppError.Decoding("no body")
                is ByteArray -> kotlinx.serialization.json.Json.decodeFromString(decoding, body.toString(Charsets.UTF_8))
                else -> throw AppError.Decoding("unexpected type")
            }
        }

        override suspend fun <T> send(request: HttpRequest, encoding: kotlinx.serialization.KSerializer<T>, body: T) {
            responder(request)
        }
    }
}
