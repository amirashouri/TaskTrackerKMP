package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.BacklogProjection
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.featureprojectsdomain.MoveWorkItem
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/**
 * One project's backlog: the work not yet put on a board, and the two ways it leaves.
 *
 * ## Why the backlog is a flat list
 *
 * [BacklogProjection] dropped the original's two sections, and the reason is the bug that
 * motivated the change: the second section held in-flight work borrowed from the board's
 * columns, so the backlog listed cards the board was already showing. With only
 * [WorkItemStatus.Backlog] items here, every one of them is somewhere the board is not, so
 * there is nothing left to divide.
 *
 * That is why this model offers "put on board" as a first-class action. In the original it
 * was one of several moves between sections; here it is the *only* way out of the backlog
 * and forward, so the model treats it as the primary action rather than as a status
 * dropdown's first entry.
 *
 * ## Filing away, from two surfaces
 *
 * [fileAway] is the operation the archive round-trip test cares about, and it is reachable
 * from here *and* from a board card menu. Both go through [ArchiveWorkItem], so the
 * already-terminal guard - which refuses rather than rewriting `resolvedAt` and erasing
 * when the task actually finished - is enforced in one place rather than twice.
 */
class ProjectBacklogModel(
    /** The project whose backlog this is. */
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ObservingProjectModel(repository) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)

    /**
     * The backlog, or an empty projection until the first read.
     *
     * Two sections — backlog and archived — because the backlog tab now answers "what still
     * needs doing" and "what was filed away from here" without leaving the tab. The backlog
     * section is `WorkItemStatus.Backlog` only and the archived section is `WorkItemStatus.Closed`
     * only, so the two stay disjoint by construction and neither re-lists a card the board is
     * already showing.
     *
     * A `data class` value over the same input the rest of the app uses, so a section switch
     * that does not change the underlying items does not recompute the partitions — only the
     * selected section changes, which is one of the cheap recompositions this screen is built
     * to make.
     */
    var projection: BacklogSectionedProjection by mutableStateOf(
        BacklogSectionedProjection(project = null, items = emptyList()),
    )
        private set

    /** The row currently being acted on, so it can show the wait. `null` when idle. */
    var busyItemID: WorkItemID? by mutableStateOf(null)
        private set

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        projection = BacklogSectionedProjection(
            project = repository.project(projectID),
            items = repository.workItems(projectID),
        )
    }

    /**
     * Moves a backlogged task onto the board.
     *
     * [WorkItemStatus.Todo] rather than [WorkItemStatus.InProgress]: "put on the board" is
     * a triage action, and starting the work is a separate decision the user makes on the
     * board. Sending it straight to in-progress would have the backlog quietly book time
     * against somebody.
     */
    suspend fun putOnBoard(itemID: WorkItemID): Boolean = acting(itemID) {
        moveWorkItem(id = itemID, to = WorkItemStatus.Todo)
    }

    /**
     * Files a task away into the archive, and reports whether it did.
     *
     * Fails with the use case's own wording if the task is already filed away, rather than
     * silently succeeding - see [ArchiveWorkItem] for why that guard exists.
     */
    suspend fun fileAway(itemID: WorkItemID): Boolean = acting(itemID) {
        archiveWorkItem(id = itemID)
    }

    /**
     * Reopens a filed-away task: moves it from [WorkItemStatus.Closed] back to
     * [WorkItemStatus.Backlog], clearing its resolution date so it does not stay archived
     * forever. Uses [MoveWorkItem] rather than [ArchiveWorkItem] because reopening is the
     * inverse of filing away and must clear [WorkItem.resolvedAt].
     */
    suspend fun unarchive(itemID: WorkItemID): Boolean = acting(itemID) {
        moveWorkItem(id = itemID, to = WorkItemStatus.Backlog)
    }

    private suspend fun acting(itemID: WorkItemID, block: suspend () -> Unit): Boolean {
        busyItemID = itemID
        return try {
            reporting { block() } != null
        } finally {
            busyItemID = null
        }
    }
}
