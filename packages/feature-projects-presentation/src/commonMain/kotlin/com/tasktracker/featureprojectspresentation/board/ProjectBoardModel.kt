package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.SprintStatus
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.featureprojectsdomain.MoveWorkItem
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/**
 * Manages board state, column projections, and card transitions for a single project.
 * Column ordering and empty-lane guarantees derive from domain [BoardProjection].
 */
class ProjectBoardModel(
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    private val currentUserID: UserID? = null,
) : ObservingProjectModel(repository) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)

    /** The board projection, initialized to empty items until first load. */
    var projection: BoardProjection by mutableStateOf(BoardProjection(project = null, items = emptyList()))
        private set

    /** ID of the work item currently performing an async move or archive mutation. */
    var movingItemID: WorkItemID? by mutableStateOf(null)
        private set

    /** The active sprint for this board, if any. */
    var activeSprint: Sprint? by mutableStateOf<Sprint?>(null)
        private set

    /**
     * Map of item blockers resolved to readable keys.
     * Evaluated against [rawItems] (all project tasks) so cross-sprint and filtered blockers remain visible.
     */
    var blockedByKeys: Map<WorkItemID, List<WorkItemKey>> by mutableStateOf(emptyMap())
        private set

    /** True when a sprint is actively InProgress, locking direct board column additions. */
    var addsLocked: Boolean by mutableStateOf(false)
        private set

    private var rawItems: List<WorkItem> = emptyList()

    /** Toggles filtering to show only tasks assigned to the signed-in user. */
    var showOnlyMyTasks: Boolean by mutableStateOf(false)
        private set

    fun toggleShowOnlyMyTasks() {
        showOnlyMyTasks = !showOnlyMyTasks
        updateProjection()
    }

    private fun currentFilteredItems(): List<WorkItem> {
        val sprintFiltered = if (activeSprint != null) {
            rawItems.filter { it.sprintID == activeSprint?.id }
        } else {
            rawItems
        }
        return if (showOnlyMyTasks) {
            val me = currentUserID ?: return emptyList()
            sprintFiltered.filter { it.assigneeID == me }
        } else {
            sprintFiltered
        }
    }

    private fun updateProjection() {
        projection = BoardProjection(
            project = projection.project,
            items = currentFilteredItems(),
        )
    }

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        val project = repository.project(projectID)
        rawItems = repository.workItems(projectID)
        val sprints = repository.sprints(projectID)
        val now = dateProvider.now()
        activeSprint = sprints.firstOrNull { !it.isFinished && it.isActiveOn(now) }
            ?: sprints.firstOrNull { !it.isFinished }
        addsLocked = activeSprint?.statusAt(now) == SprintStatus.InProgress

        blockedByKeys = rawItems.associate { item ->
            item.id to dependencyKeysFor(rawItems, item)
        }

        projection = BoardProjection(
            project = project,
            items = currentFilteredItems(),
        )
    }

    /** Moves a card to [to], appending it to the destination lane's rank order. */
    suspend fun move(itemID: WorkItemID, to: WorkItemStatus): Boolean = acting(itemID) {
        moveWorkItem(id = itemID, to = to)
    }

    val destinationStatuses: List<WorkItemStatus> get() = WorkItemStatus.boardOrder

    /** Archives the card, enforcing terminal state idempotency via [ArchiveWorkItem]. */
    suspend fun fileAway(itemID: WorkItemID): Boolean = acting(itemID) {
        archiveWorkItem(id = itemID)
    }

    private suspend fun acting(itemID: WorkItemID, block: suspend () -> Unit): Boolean {
        movingItemID = itemID
        return try {
            reporting { block() } != null
        } finally {
            movingItemID = null
        }
    }
}