package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.Project
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * The project administration surface: every project on the server, in one of two buckets,
 * with edits applied through the change feed.
 *
 * This is the seam that satisfies [AdminProjectsPort] by holding one remote and answering the
 * bucket reads and the single-project read through it. The edit goes to the remote directly;
 * there is no local store involved, because the admin list is an online view of the live server
 * and an edit the server never saw is not an edit.
 *
 * ## The admin calls are online-only, by design
 *
 * Nothing here queues an outbox row or caches the server's answer. The project list is an
 * administrative view of every project on the server, so a failed write is surfaced to the admin
 * rather than replayed later: an archive the server never saw is not an archive, and an offline
 * queue that claimed otherwise would show a project as archived while the server still held it
 * active for the rest of the team.
 *
 * Every override re-states `@Throws(AppError, CancellationException)` even though the interface
 * already carries it. The annotation is not inherited by an override, and Kotlin/Native rejects
 * `@Throws` on a suspend function unless cancellation is among the listed classes — so naming it
 * per method is what keeps the platform export honest, the same way `MemberAdminRepository` does
 * it.
 */
class AdminProjectsRepository(
    private val remote: HttpAdminProjectsRemote,
) : AdminProjectsPort {

    @Throws(AppError::class, CancellationException::class)
    override suspend fun projects(status: ProjectAdminStatus): List<Project> =
        remote.projects(status)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun project(id: ProjectID): Project? =
        remote.project(id)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun updateProject(project: Project): Project {
        // The edit screen is seeded with the project it is editing, which is where `version`
        // comes from. A project this build does not carry a version for is one it did not read
        // from the server, and sending it back without one is last-write-wins on purpose — the
        // server treats an absent version as unconditional overwrite, exactly as a push does.
        val version = project.version.takeIf { it.isNotBlank() } ?: ""
        return remote.updateProject(project, version)
    }
}
