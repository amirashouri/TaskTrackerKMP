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
    ): AdminProject

    @Throws(AppError::class, CancellationException::class)
    suspend fun updateProject(project: Project, version: String): AdminProject
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
