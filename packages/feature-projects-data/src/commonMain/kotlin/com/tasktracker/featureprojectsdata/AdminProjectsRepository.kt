package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Stores a server-confirmed project row locally.
 */
fun interface ConfirmedProjectStore {
    suspend fun store(project: Project)
}

/**
 * Admin projects port implementation backed by HTTP remote and local store projection.
 */
class AdminProjectsRepository(
    private val remote: HttpAdminProjectsRemote,
    private val localStore: ConfirmedProjectStore? = null,
) : AdminProjectsPort {

    @Throws(AppError::class, CancellationException::class)
    override suspend fun projects(status: ProjectAdminStatus): List<AdminProject> =
        remote.projects(status)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun project(id: ProjectID): AdminProject? {
        val found = remote.project(id) ?: return null
        projectLocally(found.project)
        return found
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        memberIDs: List<UserID>,
        repoUrl: String?,
    ): AdminProject = remote.createProject(name, keyPrefix, details, memberIDs, repoUrl)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun updateProject(project: Project, version: String): AdminProject {
        val saved = remote.updateProject(project, version)
        projectLocally(saved.project)
        return saved
    }

    @Throws(AppError::class, CancellationException::class)
    override suspend fun openItemCount(id: ProjectID): Int = remote.openItemCount(id)

    /**
     * Removes the project on the server.
     *
     * Nothing is rewritten locally: this admin's own copy of the project may not exist
     * (they need not be a member), and the delete reaches every member — this device
     * included — as an ordinary pulled change, which is the one path that also removes
     * the work items and sprints that went with it.
     */
    @Throws(AppError::class, CancellationException::class)
    override suspend fun deleteProject(id: ProjectID) = remote.deleteProject(id)

    private suspend fun projectLocally(project: Project) {
        val store = localStore ?: return
        try {
            store.store(project)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }
}
