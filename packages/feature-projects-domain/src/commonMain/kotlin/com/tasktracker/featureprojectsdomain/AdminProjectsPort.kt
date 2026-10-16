package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import kotlin.coroutines.cancellation.CancellationException

/**
 * The project administration surface: every project on the server, in the active or
 * archived bucket, and edits applied through the change feed.
 *
 * [ProjectRepositoryPort] is the offline-first seam the rest of the app reads and edits
 * through — the board, the backlog, the picker. This is the *admin* seam: it can see and
 * edit a project the caller is not a member of, which is the whole point of an admin
 * section, and it is online-only by design.
 *
 * It is a separate interface rather than new methods on [ProjectRepositoryPort] for the same
 * reason [MemberAdminPort] is: an ordinary project screen must not be able to reach an admin
 * action, so the surface a screen holds names which one it is.
 *
 * Every method is `suspend` and `@Throws(AppError, CancellationException)`. Kotlin/Native
 * rejects `@Throws` on a suspend function unless cancellation is among the listed classes,
 * and naming it states the real contract — a cancelled call is not a failed one.
 */
interface AdminProjectsPort {

    /**
     * The server's projects in one bucket.
     *
     * [status] is the bucket the screen's segmented control switches between. Anything the
     * server does not serve is refused here rather than forwarded as a typo that would return
     * the wrong set.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun projects(status: ProjectAdminStatus): List<Project>

    /**
     * One project, by id.
     *
     * The edit screen is seeded from this: tapping a row in the admin list opens the editor
     * with the project this returns.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun project(id: ProjectID): Project?

    /**
     * Applies an edit to a project the way the sync path does: the row and its change-feed
     * entry are written in one transaction, so the project's members pull the edit exactly
     * as they would one their own client pushed.
     *
     * [project] is the whole project as the client holds it. `id` and `createdAt` are taken
     * from the stored row; `version` is the compare-and-swap the caller read the project
     * against, and omitting it is last-write-wins, exactly as in a push.
     *
     * Returns the stored project, carrying the new `version` the caller saves against next
     * time.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun updateProject(project: Project): Project
}

/**
 * The buckets the admin projects screen's segmented control switches between.
 *
 * They name the archived flag — a project's own state — not the "deleted" bucket the member
 * admin list uses, where the row is gone. [Project.isArchived] is the source of truth both
 * sides agree on.
 */
enum class ProjectAdminStatus(val wireValue: String) {
    Active("active"),
    Archived("archived");

    companion object {
        /** Maps the admin projects API's `status` query parameter; anything unrecognised is refused. */
        fun fromWireValue(value: String?): ProjectAdminStatus =
            when (value) {
                Active.wireValue -> Active
                Archived.wireValue -> Archived
                else -> throw AppError.Validation(
                    "The admin projects API lists the active or the archived bucket; " +
                        "\"$value\" is not one of them.",
                )
            }
    }
}
