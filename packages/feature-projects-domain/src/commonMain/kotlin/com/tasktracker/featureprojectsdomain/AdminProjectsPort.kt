package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Project
import kotlin.coroutines.cancellation.CancellationException

/**
 * Project as returned by admin API, along with compare-and-swap version token.
 */
data class AdminProject(
    val project: Project,
    val version: String,
)

/**
 * Project administration port for online-only cross-member operations.
 */
interface AdminProjectsPort {

    @Throws(AppError::class, CancellationException::class)
    suspend fun projects(status: ProjectAdminStatus): List<AdminProject>

    @Throws(AppError::class, CancellationException::class)
    suspend fun project(id: ProjectID): AdminProject?

    @Throws(AppError::class, CancellationException::class)
    suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        memberIDs: List<UserID>,
        /** Optional; omitted entirely when the admin leaves the field empty. */
        repoUrl: String? = null,
    ): AdminProject

    @Throws(AppError::class, CancellationException::class)
    suspend fun updateProject(project: Project, version: String): AdminProject

    /**
     * How many of the project's work items are not finished (done or closed).
     *
     * The admin list needs this before it can offer the right action: a project with open
     * work is archived rather than deleted, exactly as the member list treats one. It is a
     * separate read because the count is not part of the project object — adding a key there
     * would be a decoding failure on every installed client.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun openItemCount(id: ProjectID): Int

    /**
     * Removes the project and everything under it.
     *
     * Admin-authorized rather than membership-authorized, because the admin may not be a
     * member of the project they are administering. Members learn about it on their next
     * pull, which delivers the delete so their store retires the project instead of
     * keeping a row the server no longer has.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun deleteProject(id: ProjectID)
}

/**
 * Buckets for admin projects list.
 */
enum class ProjectAdminStatus(val wireValue: String) {
    Active("active"),
    Archived("archived");

    companion object {
        fun fromWireValue(value: String?): ProjectAdminStatus =
            when (value) {
                Active.wireValue -> Active
                Archived.wireValue -> Archived
                else -> throw AppError.Validation("Invalid project admin status: \"$value\".")
            }
    }
}
