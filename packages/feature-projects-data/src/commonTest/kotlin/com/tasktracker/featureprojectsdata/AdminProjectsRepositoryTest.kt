package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.randomProjectID
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The repository's half of the admin projects contract: which bucket it asks for, and that the
 * compare-and-swap version survives the round trip in both directions.
 *
 * Driven through the real remote over a recorded transport rather than through a hand-written
 * fake of [HttpAdminProjectsRemote]. The remote is a final class with no seam above it, and a
 * fake standing in for it would be a second copy of the same expectations — the mirror trap
 * `cmd/server/contract_test.go` warns about on the server side. The transport is the boundary
 * that actually moves bytes, so that is where the double belongs.
 */
class AdminProjectsRepositoryTest {

    @Test
    fun projectsAsksForTheBucketItWasGiven() = runTest {
        val client = RecordingHttpClient()
        client.respondWith("[${projectBody(name = "Active")}]")

        val found = repositoryOver(client).projects(ProjectAdminStatus.Active)

        assertEquals(listOf("Active"), found.map { it.project.name })
        assertEquals("active", client.requests.single().queryItems.single().value)
    }

    @Test
    fun projectsRefusesABucketTheServerDoesNotServe() = runTest {
        assertFailsWith<AppError.Validation> {
            repositoryOver(RecordingHttpClient()).projects(ProjectAdminStatus.fromWireValue("deleted"))
        }
    }

    @Test
    fun projectReturnsTheRowAndTheVersionItWasReadAgainst() = runTest {
        val id = randomProjectID()
        val client = RecordingHttpClient()
        client.respondWith(projectBody(id = id.stringValue, name = "Solo", version = "v3"))

        val found = repositoryOver(client).project(id)

        assertEquals(AdminProject(project = found!!.project, version = "v3"), found)
        assertEquals("Solo", found.project.name)
        assertEquals("/v1/admin/projects/${id.stringValue}", client.requests.single().path)
    }

    @Test
    fun projectAnswersNullWhenTheServerHasNone() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Unknown("Not found (404)."))

        assertNull(repositoryOver(client).project(randomProjectID()))
    }

    @Test
    fun updateProjectForwardsTheVersionTheCallerReadAndReturnsTheStoredOne() = runTest {
        val id = randomProjectID()
        val client = RecordingHttpClient()
        client.respondWith(projectBody(id = id.stringValue, name = "Edit", version = "v4"))

        val stored = repositoryOver(client)
            .updateProject(aProject(id = id, name = "Edit"), version = "v3")

        val body = Json.decodeFromString<JsonObject>(
            client.requests.single().body!!.decodeToString(),
        )
        assertEquals("v3", body["version"]?.jsonPrimitive?.content)
        assertEquals("Edit", body["name"]?.jsonPrimitive?.content)
        assertEquals("v4", stored.version)
    }

    @Test
    fun updateProjectWithABlankVersionIsAnUnconditionalOverwrite() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Edit"))

        repositoryOver(client).updateProject(aProject(name = "Edit"), version = "")

        val body = Json.decodeFromString<JsonObject>(
            client.requests.single().body!!.decodeToString(),
        )
        assertEquals("", body["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun updateProjectPropagatesAConflict() = runTest {
        val client = RecordingHttpClient()
        client.respondWithFailure(AppError.Conflict(entity = "project"))

        assertFailsWith<AppError.Conflict> {
            repositoryOver(client).updateProject(aProject(), version = "stale")
        }
    }

    private fun repositoryOver(client: RecordingHttpClient): AdminProjectsRepository =
        AdminProjectsRepository(HttpAdminProjectsRemote(client))

    private fun projectBody(
        id: String = "11111111-1111-1111-1111-111111111111",
        name: String,
        version: String = "",
    ): String =
        """{"id":"$id","keyPrefix":"KP","name":"$name","details":"","memberIds":[],""" +
            """"isArchived":false,"nextItemNumber":1,""" +
            """"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:01Z","version":"$version"}"""
}
