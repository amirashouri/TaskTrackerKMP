package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.ArchiveWorkItems
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.featureprojectsdomain.MoveWorkItem
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.PutWorkItemsOnBoard
import com.tasktracker.featureprojectsdomain.WorkItemBatchOutcome

/**
 * Manages backlog and archived items for a project.
 * All archive operations route through [ArchiveWorkItem] to preserve original `resolvedAt` timestamps.
 *
 * ## Select mode
 *
 * [isSelecting] is the mode; [selection] is what is ticked. They are separate because the mode
 * has to survive an empty selection — tapping **Select** shows the checkboxes before the first
 * row is ticked, and deselecting the last row is a step towards another, not a way out. Mode
 * exits on [clearSelection], on leaving the destination, and after a batch that leaves nothing
 * retryable. Single-row commands keep using [busyItemID]; a batch uses [isBatchRunning] so the
 * action bar can disable itself without borrowing every row's spinner.
 *
 * Batch entry points return the [WorkItemBatchOutcome] the domain produced, and apply its result
 * to the selection: applied and skipped items leave it, failed items stay ticked so the user can
 * retry exactly those. A failed item's message goes through the ordinary [errorMessage] banner —
 * the failed rows staying ticked is what carries the count.
 */
class ProjectBacklogModel(
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ObservingProjectModel(repository) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)
    private val putWorkItemsOnBoard = PutWorkItemsOnBoard(repository, dateProvider)
    private val archiveWorkItems = ArchiveWorkItems(repository, dateProvider)

    /**
     * Backlog (`Backlog`) and archived (terminal: `Done` and `Closed`) sections.
     * The backlog section is strictly disjoint from the other two; the archive deliberately
     * overlaps the board, because a finished task is both a `Done` lane and history.
     */
    var projection: BacklogSectionedProjection by mutableStateOf(
        BacklogSectionedProjection(project = null, items = emptyList()),
    )
        private set

    /** Item ID currently running an async mutation, or null if idle. */
    var busyItemID: WorkItemID? by mutableStateOf(null)
        private set

    /** True while the backlog is in multi-select mode. */
    var isSelecting: Boolean by mutableStateOf(false)
        private set

    /** The ticked items, in the order they were ticked. */
    var selection: WorkItemSelection by mutableStateOf(WorkItemSelection())
        private set

    /** True while a batch command is running; the action bar disables itself on it. */
    var isBatchRunning: Boolean by mutableStateOf(false)
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
     * Boards a backlog item into the column named by [statusKey].
     *
     * [statusKey] is a `StatusDefinition.id` from the project's catalog. The move lands in that
     * lane (its `category` is the coarse status the scheduling rules read). When [statusKey] is
     * null — a project with no catalog, or the legacy path — the item goes to `Todo` with no
     * lane key, which is exactly the pre-catalog behaviour. The screen is responsible for picking
     * a [statusKey] that the project actually contains; the use case only resolves what it is given,
     * and ignores an unknown key by falling back to `Todo`.
     *
     * Delegates to [PutWorkItemsOnBoard] with a one-item set so the single-row and batch paths
     * cannot resolve the lane differently.
     */
    suspend fun putOnBoard(itemID: WorkItemID, statusKey: String? = null): Boolean = acting(itemID) {
        val outcome = putWorkItemsOnBoard(projectID, listOf(itemID), statusKey)
        outcome.firstFailure?.let { throw it.error }
    }

    // MARK: Selection

    /** Enters select mode, optionally ticking [seed] (the row that was long-pressed). */
    fun startSelecting(seed: WorkItemID? = null) {
        isSelecting = true
        if (seed != null) selection = selection + seed
    }

    /** Ticks or unticks one row, entering select mode if a tap got here first. */
    fun toggleSelected(itemID: WorkItemID) {
        isSelecting = true
        selection = selection.toggle(itemID)
    }

    /** Leaves select mode and drops every tick. */
    fun clearSelection() {
        selection = WorkItemSelection()
        isSelecting = false
    }

    /**
     * Boards every ticked item into one column, resolved once for the whole set.
     *
     * Returns the domain's outcome, or null when the batch could not run at all (no selection,
     * another batch running) or the port failed before the loop. Applied and skipped items leave
     * the selection; failed items stay ticked and the first failure's message reaches the banner.
     */
    suspend fun putSelectionOnBoard(statusKey: String? = null): WorkItemBatchOutcome? = batching {
        putWorkItemsOnBoard(projectID, selection.ids, statusKey)
    }

    /** Archives every ticked item, skipping any that became terminal elsewhere. */
    suspend fun archiveSelection(): WorkItemBatchOutcome? = batching {
        archiveWorkItems(selection.ids)
    }

    private suspend fun batching(block: suspend () -> WorkItemBatchOutcome): WorkItemBatchOutcome? {
        if (selection.isEmpty || isBatchRunning) return null
        isBatchRunning = true
        return try {
            val outcome = reporting { block() } ?: return null
            selection = selection.retaining(outcome.retryable)
            if (selection.isEmpty) isSelecting = false
            outcome.firstFailure?.let { onFailure(it.error) }
            outcome
        } finally {
            isBatchRunning = false
        }
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