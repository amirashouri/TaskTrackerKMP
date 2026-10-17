package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Project
import kotlin.coroutines.cancellation.CancellationException

/**
 * One project as the admin API hands it back: the entity, plus the compare-and-swap token
 * the next save has to carry.
 *
 * The version is deliberately not a field on [Project]. It is not project state — the sync
 * side keeps the same token in the store (`serverVersion`) rather than on the entity, and
 * an entity carrying its own wire revision would have to be written on every read. Pairing
 * the two here is what lets an edit send back the version it was seeded from, so the server
 * answers `409` instead of silently overwriting whoever saved first.
 */
data class AdminProject(
    val project: Project,
    /** The `version` the server returned with this row; echoed back on the next edit. */
    val version: String,
)

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
    suspend fun projects(status: ProjectAdminStatus): List<AdminProject>

    /**
     * One project, by id.
     *
     * The edit screen is seeded from this: tapping a row in the admin list opens the editor
     * with the project this returns. `null` means the server no longer has that project, and
     * is deliberately not the same answer as a failed call — the screen shows "gone" for one
     * and a retry for the other.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun project(id: ProjectID): AdminProject?

    /**
     * Applies an edit to a project the way the sync path does: the row and its change-feed
     * entry are written in one transaction, so the project's members pull the edit exactly
     * as they would one their own client pushed.
     *
     * [project] is the whole project as the client holds it. `id` and `createdAt` are taken
     * from the stored row; [version] is the compare-and-swap the caller read the project
     * against — the token from the [AdminProject] it was seeded with, or the one the previous
     * save returned. An empty [version] is an unconditional overwrite, which is what the
     * server does with a push that carries none.
     *
     * Returns the stored project, carrying the new version the caller saves against next
     * time.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun updateProject(project: Project, version: String): AdminProject
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
