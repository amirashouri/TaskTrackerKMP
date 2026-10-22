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
 * Writes a project row the server has already stored into the local store.
 *
 * The seam exists because the admin surface and the member surfaces read different things:
 * the admin reads the server, while every member screen reads the local row a pull fills.
 * Projecting the rows the admin surface read back into that store — through the same writer a
 * pull uses, and with no outbox row — is what lets a catalog an administrator just created be
 * selectable on the device that created it, instead of waiting for the next sync cycle.
 *
 * Implemented by [LocalDataSyncSink], the one class allowed to write a pulled row.
 */
fun interface ConfirmedProjectStore {
    suspend fun store(project: Project)
}

/**
 * The project administration surface: every project on the server, in one of two buckets,
 * with edits applied through the change feed.
 *
 * This is the seam that satisfies [AdminProjectsPort] by holding one remote and answering the
 * bucket reads and the single-project read through it. The edit goes to the remote directly;
 * there is no local store involved, because the admin list is an online view of the live server
 * and an edit the server never saw is not an edit. The create is the same trade, for a sharper
 * reason: the offline-first create pushes a new project into the caller's member list, and an
 * admin creating one for other people is not on it.
 *
 * ## The admin calls are online-only, by design
 *
 * Nothing here queues an outbox row. The project list is an administrative view of every
 * project on the server, so a failed write is surfaced to the admin rather than replayed later:
 * an archive the server never saw is not an archive, and an offline queue that claimed
 * otherwise would show a project as archived while the server still held it active for the
 * rest of the team.
 *
 * ## Rows the server confirmed are projected locally
 *
 * A row this class reads or writes is one the server already holds, so it is reported to
 * [localStore] as well. The member-facing screens read that local row and nothing else, which
 * is why the alternative — waiting for the change feed — left an administrator looking at a
 * tag catalog that did not include the tags they had just created. The projection is a write
 * of a server-confirmed fact, never a queued intent, so the outbox stays untouched.
 *
 * Every override re-states `@Throws(AppError, CancellationException)` even though the interface
 * already carries it. The annotation is not inherited by an override, and Kotlin/Native rejects
 * `@Throws` on a suspend function unless cancellation is among the listed classes — so naming it
 * per method is what keeps the platform export honest, the same way `MemberAdminRepository` does
 * it.
 */
class AdminProjectsRepository(
    private val remote: HttpAdminProjectsRemote,
    /**
     * Where a server-confirmed row is projected so member surfaces can read it.
     *
     * `null` in a graph with no local store to write — the unit tests that drive this class
     * over a recorded transport — and then the next pull is the only path into the store.
     */
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
    ): AdminProject = remote.createProject(name, keyPrefix, details, memberIDs)

    @Throws(AppError::class, CancellationException::class)
    override suspend fun updateProject(project: Project, version: String): AdminProject {
        // The version is forwarded verbatim rather than defaulted away: a blank one is the
        // caller saying "overwrite whatever is there", which is exactly what the server does
        // with a push that carries none.
        val saved = remote.updateProject(project, version)
        projectLocally(saved.project)
        return saved
    }

    /**
     * Writes a row the server already holds into the local store.
     *
     * Best effort on purpose: the server has accepted the read or the write, so a local
     * failure must not be reported as one. The next pull applies the same row, which makes a
     * refusal here a delay rather than a loss.
     */
    private suspend fun projectLocally(project: Project) {
        val store = localStore ?: return
        try {
            store.store(project)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The change feed is the backstop; there is nothing useful to tell the caller,
            // whose save the server has already accepted.
        }
    }
}
