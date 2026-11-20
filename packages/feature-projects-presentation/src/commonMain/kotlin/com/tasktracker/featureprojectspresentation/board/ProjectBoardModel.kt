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
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.ArchiveWorkItems
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.featureprojectsdomain.MoveWorkItem
import com.tasktracker.featureprojectsdomain.MoveWorkItems
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.WorkItemBatchOutcome

/**
 * Manages board state, column projections, and card transitions for a single project.
 * Column ordering and empty-lane guarantees derive from domain [BoardProjection].
 *
 * ## Selection is lane-scoped
 *
 * [selectionLane] is the one column the selection belongs to, and [selection] never spans lanes:
 * the batch verbs read differently per lane (archiving a `Done` card closes it; archiving a live
 * card files it away), so a set spanning both would be one button with two meanings. The lane is
 * also why the set is pruned whenever the projection changes — a card that left the lane, or was
 * hidden by the My Tasks filter, must not stay ticked where the user cannot see it.
 *
 * The board has no long-press entry: long-press already picks a card up for drag, and a stationary
 * hold would lift a ghost card under the finger that meant to select. Selection starts from the
 * column header, which is also where Select all lives.
 */
class ProjectBoardModel(
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    private val currentUserID: UserID? = null,
) : ObservingProjectModel(repository) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)
    private val moveWorkItems = MoveWorkItems(repository, dateProvider)
    private val archiveWorkItems = ArchiveWorkItems(repository, dateProvider)

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
        pruneSelection()
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
            boardStatuses = projection.project?.boardStatuses.orEmpty(),
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
            boardStatuses = project?.boardStatuses.orEmpty(),
        )
        pruneSelection()
    }

    /** Moves a card to [toStatusKey], appending it to the destination lane's rank order. */
    suspend fun move(itemID: WorkItemID, toStatusKey: String): Boolean {
        val definition = projection.project?.statuses?.firstOrNull { it.id == toStatusKey }
        val to = definition?.category ?: WorkItemStatus.fromWireValue(toStatusKey) ?: return false
        return acting(itemID) {
            moveWorkItem(id = itemID, to = to, statusKey = toStatusKey)
        }
    }

    val destinationStatuses: List<StatusDefinition>
        get() = projection.project?.boardStatuses.orEmpty()

    /**
     * Archives the card through [ArchiveWorkItem]: a live card is filed away, a `Done` card is
     * closed, and an already-`Closed` card is refused.
     */
    suspend fun fileAway(itemID: WorkItemID): Boolean = acting(itemID) {
        archiveWorkItem(id = itemID)
    }

    // MARK: Selection

    /** True while one lane's cards are being ticked. */
    var isSelecting: Boolean by mutableStateOf(false)
        private set

    /** The lane the selection belongs to, or null when nothing is selected. */
    var selectionLane: String? by mutableStateOf<String?>(null)
        private set

    /** The ticked cards of [selectionLane], in the order they were ticked. */
    var selection: WorkItemSelection by mutableStateOf(WorkItemSelection())
        private set

    /** True while a batch move or archive runs; the action bar disables itself on it. */
    var isBatchRunning: Boolean by mutableStateOf(false)
        private set

    /**
     * Enters select mode on [laneID], optionally ticking [seed].
     *
     * Switching lanes starts a fresh set: keeping the old ticks would make "one lane's selection"
     * a lie the moment the user opened another column.
     */
    fun startSelecting(laneID: String, seed: WorkItemID? = null) {
        if (selectionLane != laneID) selection = WorkItemSelection()
        selectionLane = laneID
        isSelecting = true
        if (seed != null) selection = selection + seed
    }

    /**
     * Ticks or unticks one card. A tap in another lane is ignored rather than moving the mode:
     * lanes are separate selections, and the header's Clear is the way to change which one.
     */
    fun toggleSelected(laneID: String, itemID: WorkItemID) {
        if (!isSelecting || selectionLane == null) {
            startSelecting(laneID)
        } else if (selectionLane != laneID) {
            return
        }
        selection = selection.toggle(itemID)
    }

    /**
     * Selects [itemIDs] in [laneID], filtered to what that lane is actually projecting.
     *
     * [itemIDs] is what the screen is drawing — it knows the search query — and the projection is
     * what the sprint and My Tasks filters left. Intersecting the two is what keeps Select all from
     * ticking a card the user cannot see either way.
     */
    fun selectAll(laneID: String, itemIDs: List<WorkItemID>) {
        val projected = laneItemIDs(laneID).toSet()
        val chosen = itemIDs.distinct().filter { it in projected }
        selectionLane = laneID
        isSelecting = true
        selection = WorkItemSelection(chosen)
    }

    fun clearSelection() {
        selection = WorkItemSelection()
        selectionLane = null
        isSelecting = false
    }

    /** Moves every ticked card to [toStatusKey], one command per card, in selection order. */
    suspend fun moveSelection(toStatusKey: String): WorkItemBatchOutcome? {
        val definition = projection.project?.statuses?.firstOrNull { it.id == toStatusKey }
        val to = definition?.category ?: WorkItemStatus.fromWireValue(toStatusKey) ?: return null
        return batching {
            moveWorkItems(ids = selection.ids, to = to, statusKey = toStatusKey)
        }
    }

    /** Archives every ticked card: closing a `Done` lane, filing away a live one. */
    suspend fun archiveSelection(): WorkItemBatchOutcome? = batching {
        archiveWorkItems(selection.ids)
    }

    private suspend fun batching(block: suspend () -> WorkItemBatchOutcome): WorkItemBatchOutcome? {
        if (selection.isEmpty || isBatchRunning) return null
        isBatchRunning = true
        return try {
            val outcome = reporting { block() } ?: return null
            selection = selection.retaining(outcome.retryable)
            if (selection.isEmpty) clearSelection()
            outcome.firstFailure?.let { onFailure(it.error) }
            outcome
        } finally {
            isBatchRunning = false
        }
    }

    private fun laneItemIDs(laneID: String): List<WorkItemID> =
        projection.columns.firstOrNull { it.status.id == laneID }
            ?.items
            ?.map { it.id }
            .orEmpty()

    /**
     * Drops ticks the projection no longer draws.
     *
     * A card that moved to another lane, was hidden by My Tasks, or whose lane disappeared from the
     * catalog must not stay ticked where the user cannot account for it. A lane that no longer
     * exists ends the mode entirely; an emptied one leaves an empty checklist, which is a state
     * the header already answers with its Clear control.
     */
    private fun pruneSelection() {
        // A batch's own writes publish changes; pruning mid-batch would drop a failed card from
        // the retry set before the outcome that names it has even been read.
        if (isBatchRunning) return
        val lane = selectionLane ?: return
        if (projection.columns.none { it.status.id == lane }) {
            clearSelection()
            return
        }
        selection = selection.retaining(laneItemIDs(lane))
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