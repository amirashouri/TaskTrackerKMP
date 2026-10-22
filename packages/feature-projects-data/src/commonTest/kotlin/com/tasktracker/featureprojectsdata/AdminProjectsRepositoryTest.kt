package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.parseTagID
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.network.HttpMethod
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
        client.respondWithFailure(AppError.NotFound("Not found (404)."))

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

    /**
     * A row the admin surface read is also the local row, so member screens see it at once.
     *
     * The catalog is the reason: a work item editor reads the local project and nothing else,
     * so an admin whose device kept the server's answer to itself offered an empty tag section
     * for tags that existed.
     */
    @Test
    fun projectWritesTheRowItReadIntoTheLocalStore() = runTest {
        val id = randomProjectID()
        val catalog = aTagCatalog()
        val client = RecordingHttpClient()
        client.respondWith(
            projectBody(id = id.stringValue, name = "Solo", version = "v3", tags = catalog),
        )
        val store = RecordingProjectStore()

        repositoryOver(client, store).project(id)

        assertEquals(listOf(catalog), store.stored.map { it.tags })
    }

    @Test
    fun updateProjectWritesTheStoredRowIntoTheLocalStore() = runTest {
        val id = randomProjectID()
        val catalog = aTagCatalog()
        val client = RecordingHttpClient()
        client.respondWith(
            projectBody(id = id.stringValue, name = "Edit", version = "v4", tags = catalog),
        )
        val store = RecordingProjectStore()

        repositoryOver(client, store).updateProject(aProject(id = id, name = "Edit"), version = "v3")

        assertEquals(listOf(catalog), store.stored.map { it.tags })
        assertEquals("Edit", store.stored.single().name)
    }

    /**
     * The server has already accepted the write, so a local store that refuses the row is a
     * delay, not a failed save: the pull applies the same row later. Reporting a failure here
     * would send the admin to retry a save that already happened, against a stale version.
     */
    @Test
    fun aLocalStoreFailureDoesNotTurnAConfirmedSaveIntoAFailure() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Edit"))
        val store = RecordingProjectStore(failure = IllegalStateException("the row would not write"))

        val saved = repositoryOver(client, store).updateProject(aProject(name = "Edit"), version = "v3")

        assertEquals("Edit", saved.project.name)
    }

    @Test
    fun createProjectReachesTheWireAndReturnsTheStoredRow() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(projectBody(name = "Gemini", version = "v1"))

        val stored = repositoryOver(client)
            .createProject("Gemini", "GE", "Two milestones.", emptyList())

        val request = client.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/admin/projects", request.path)
        val body = Json.decodeFromString<JsonObject>(request.body!!.decodeToString())
        assertEquals("Gemini", body["name"]?.jsonPrimitive?.content)
        assertEquals("GE", body["keyPrefix"]?.jsonPrimitive?.content)
        assertEquals("Two milestones.", body["details"]?.jsonPrimitive?.content)
        // The version the server returned is what the pane's next operation would save against;
        // a create that dropped it would leave the section editing a row it cannot compare-and-swap.
        assertEquals("v1", stored.version)
        assertEquals("Gemini", stored.project.name)
    }

    private fun repositoryOver(
        client: RecordingHttpClient,
        localStore: ConfirmedProjectStore? = null,
    ): AdminProjectsRepository = AdminProjectsRepository(HttpAdminProjectsRemote(client), localStore)

    /** One tag, with every field unlike its default, as the catalog a recorder compares. */
    private fun aTagCatalog(): List<ProjectTag> = listOf(
        ProjectTag(
            id = parseTagID("77777777-7777-4777-8777-777777777777")!!,
            name = "Bug",
            color = TagColor.Red,
            icon = TagIcon.Bug,
        ),
    )

    private fun projectBody(
        id: String = "11111111-1111-1111-1111-111111111111",
        name: String,
        version: String = "",
        tags: List<ProjectTag> = emptyList(),
    ): String {
        val encodedTags = tags.joinToString(",") { tag ->
            """{"id":"${tag.id.stringValue}","name":"${tag.name}",""" +
                """"color":"${tag.color.wireValue}","icon":"${tag.icon.wireValue}"}"""
        }
        return """{"id":"$id","keyPrefix":"KP","name":"$name","details":"",""" +
            """"tags":[$encodedTags],"memberIds":[],""" +
            """"isArchived":false,"nextItemNumber":1,""" +
            """"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:01Z","version":"$version"}"""
    }

    /** The seam the repository projects server-confirmed rows through. */
    private class RecordingProjectStore(
        private val failure: Exception? = null,
    ) : ConfirmedProjectStore {
        val stored = mutableListOf<Project>()

        override suspend fun store(project: Project) {
            failure?.let { throw it }
            stored += project
        }
    }
}
