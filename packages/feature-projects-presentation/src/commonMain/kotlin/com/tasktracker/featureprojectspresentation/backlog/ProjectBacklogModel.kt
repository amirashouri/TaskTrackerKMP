package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.featureprojectsdomain.MoveWorkItem
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/**
 * Manages backlog and archived items for a project.
 * All archive operations route through [ArchiveWorkItem] to preserve original `resolvedAt` timestamps.
 */
class ProjectBacklogModel(
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ObservingProjectModel(repository) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)

    /**
     * Backlog (`Backlog`) and archived (`Closed`) sections.
     * Strictly disjoint by status; neither contains in-flight cards present on the board.
     */
    var projection: BacklogSectionedProjection by mutableStateOf(
        BacklogSectionedProjection(project = null, items = emptyList()),
    )
        private set

    /** Item ID currently running an async mutation, or null if idle. */
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

    /** Moves task to Todo on the board (triage action; work begins separately as InProgress). */
    suspend fun putOnBoard(itemID: WorkItemID): Boolean = acting(itemID) {
        moveWorkItem(id = itemID, to = WorkItemStatus.Todo)
    }

    /** Archives the task. Fails if the item is already terminal. */
    suspend fun fileAway(itemID: WorkItemID): Boolean = acting(itemID) {
        archiveWorkItem(id = itemID)
    }

    /** Reopens an archived task to Backlog, clearing its `resolvedAt` date. */
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