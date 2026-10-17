package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.ProjectID
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import kotlin.time.Instant

/**
 * A fake admin-projects port for host tests that need one, modelled on the member-admin test
 * doubles: it records the calls a test can assert on and hands back whatever the test seeded.
 *
 * It lives in `commonTest`, not next to the screen it exercises. A double in `commonMain` ships
 * in the app, and the one public composable per file rule reads the screen's file as the
 * screen — a recording fake with a `companion object` fixture is a second, unrelated reason for
 * that file to change.
 *
 * Reads answer with [AdminProject], because that is what the port hands a screen: the row and
 * the compare-and-swap token it was read against. A test that seeds a version can assert the
 * edit sent it back, which is the property the admin editor exists to guarantee.
 */
class RecordingAdminProjectsPort(
    private val byStatus: Map<ProjectAdminStatus, List<AdminProject>> = emptyMap(),
    private val projectByID: Map<ProjectID, AdminProject> = emptyMap(),
    private val updateResult: ((Project, String) -> AdminProject)? = null,
) : AdminProjectsPort {

    val projectsCalls = mutableListOf<ProjectAdminStatus>()
    val projectCalls = mutableListOf<ProjectID>()
    val updateCalls = mutableListOf<Pair<Project, String>>()

    override suspend fun projects(status: ProjectAdminStatus): List<AdminProject> {
        projectsCalls += status
        return byStatus[status] ?: emptyList()
    }

    override suspend fun project(id: ProjectID): AdminProject? {
        projectCalls += id
        return projectByID[id]
    }

    override suspend fun updateProject(project: Project, version: String): AdminProject {
        updateCalls += project to version
        return updateResult?.invoke(project, version) ?: AdminProject(project, version)
    }

    companion object {
        /** The version every seeded row carries, so a test names it once. */
        const val SEEDED_VERSION: String = "seeded-v1"

        fun singleActiveProject(
            name: String = "Demo",
            isArchived: Boolean = false,
        ): RecordingAdminProjectsPort {
            val id = randomProjectID()
            val project = Project(
                id = id,
                keyPrefix = "DM",
                name = name,
                details = "",
                memberIDs = listOf(com.tasktracker.core.randomUserID()),
                isArchived = isArchived,
                nextItemNumber = 1,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
            val adminProject = AdminProject(project = project, version = SEEDED_VERSION)
            return RecordingAdminProjectsPort(
                byStatus = mapOf(ProjectAdminStatus.Active to listOf(adminProject)),
                projectByID = mapOf(id to adminProject),
            )
        }

        private val createdAt = Instant.fromEpochSeconds(1_700_000_000)
    }
}
