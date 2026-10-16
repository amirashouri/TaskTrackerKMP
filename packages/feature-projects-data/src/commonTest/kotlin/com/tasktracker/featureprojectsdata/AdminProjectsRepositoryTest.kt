package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.randomProjectID
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.Project
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class AdminProjectsRepositoryTest {

    @Test
    fun projectsListsTheBucketTheRemoteReturns() {
        val remote = FakeRemote(
            projects = listOf(
                project(id = "11111111-1111-1111-1111-111111111111", name = "Active", isArchived = false),
            ),
            project = null,
            update = null,
        )

        val repo = AdminProjectsRepository(remote)
        assertEquals(
            listOf(project(id = "11111111-1111-1111-1111-111111111111", name = "Active", isArchived = false)),
            repo.projects(ProjectAdminStatus.Active),
        )
        assertEquals("active", remote.projectsStatus)
    }

    @Test
    fun projectsThrowsWhenTheStatusIsNotServed() {
        val repo = AdminProjectsRepository(
            FakeRemote(projects = emptyList(), project = null, update = null),
        )

        assertFailsWith<AppError.Validation> {
            repo.projects(ProjectAdminStatus.fromWireValue("deleted"))
        }
    }

    @Test
    fun projectReturnsTheSeededProject() {
        val id = randomProjectID()
        val remote = FakeRemote(
            projects = emptyList(),
            project = project(id = id.stringValue, name = "Solo", isArchived = false),
            update = null,
        )

        val repo = AdminProjectsRepository(remote)
        assertEquals(
            project(id = id.stringValue, name = "Solo", isArchived = false),
            repo.project(id),
        )
    }

    @Test
    fun projectReturnsNullWhenTheRemoteHasNone() {
        val repo = AdminProjectsRepository(
            FakeRemote(projects = emptyList(), project = null, update = null),
        )

        assertNull(repo.project(randomProjectID()))
    }

    @Test
    fun updateProjectForwardsTheProjectAndReturnsTheStoredVersion() {
        val id = randomProjectID()
        val remote = FakeRemote(
            projects = emptyList(),
            project = null,
            update = { request ->
                assertEquals(id.stringValue, request.id.stringValue)
                assertEquals("Edit", request.name)
                project(id = id.stringValue, name = "Edit", isArchived = false, version = "server-version")
            },
        )

        val repo = AdminProjectsRepository(remote)
        val edited = repo.updateProject(
            Project(
                id = id,
                keyPrefix = "ED",
                name = "Edit",
                details = "",
                memberIDs = emptyList(),
                isArchived = true,
                nextItemNumber = 1,
                createdAt = Instant.fromEpochSeconds(1_700_000_000),
                updatedAt = Instant.fromEpochSeconds(1_700_000_001),
            ),
        )

        assertEquals("Edit", edited.name)
        assertEquals("server-version", edited.version)
    }

    private fun project(
        id: String,
        name: String,
        isArchived: Boolean,
        version: String = "",
    ): Project = Project(
        id = ProjectID(id),
        keyPrefix = "KP",
        name = name,
        details = "",
        memberIDs = emptyList(),
        isArchived = isArchived,
        nextItemNumber = 1,
        createdAt = Instant.fromEpochSeconds(1_700_000_000),
        updatedAt = Instant.fromEpochSeconds(1_700_000_001),
    ).also { it.version = version }

    privateclass FakeRemote(
    private val projects: List<Project>,
    private val project: Project?,
    private val update: ((FakeUpdateRequest) -> Project)?,
) : HttpAdminProjectsRemote {
        var projectsStatus: String = ""

        override suspend fun projects(status: ProjectAdminStatus): List<Project> {
            projectsStatus = status.wireValue
            return projects
        }

        override suspend fun project(id: ProjectID): Project? = project?.copy(id = id)

        override suspend fun updateProject(proposal: Project, version: String): Project {
            val request = FakeUpdateRequest(proposal, version)
            return update?.invoke(request)
                ?: throw AppError.Http(404, "not found")
        }
    }

    private data class FakeUpdateRequest(
        val id: String,
        val keyPrefix: String,
        val name: String,
        val details: String,
        val memberIds: List<String>,
        val isArchived: Boolean,
        val nextItemNumber: Int,
        val createdAt: String,
        val updatedAt: String,
        val version: String,
    )
}
