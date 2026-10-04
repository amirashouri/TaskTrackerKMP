package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.featureprojectsdomain.MoveWorkItem
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/**
 * One project's board: its columns, and the ability to move a card between them.
 *
 * The columns come from [BoardProjection], which derives them in the domain layer rather
 * than here. That is the rule this model exists to not break: a board that grouped its own
 * copy would eventually disagree with `WorkItemGrouping`, and the board would reorder
 * itself whenever only one of the two changed. It also means empty columns are
 * guaranteed - `boardColumns` returns one entry per status in `boardOrder` whether or not
 * anything is in it, so a column the user has never used still has somewhere to drop a
 * card and does not have to appear out of nowhere.
 *
 * ## Scoped to one project, and says so
 *
 * This model watches a single project, so [isRelevant] is where the filtering lives: a
 * change scoped to a different project is ignored. An *unscoped* change is honoured, since
 * `null` is what a create reports and a project that did not exist a moment ago cannot be
 * excluded by matching its id against a model that has not loaded it yet.
 */
class ProjectBoardModel(
    /** The project whose board this is. */
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    private val currentUserName: String? = null,
) : ObservingProjectModel(repository) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)

    /**
     * The board, or an empty projection until the first read.
     *
     * Empty rather than null so a screen can render a skeleton without a null check on
     * every field; [BoardProjection.isEmpty]-style checks decide what to show, and
     * `project == null` is how a screen tells "still loading" from "this project is gone".
     */
    var projection: BoardProjection by mutableStateOf(BoardProjection(project = null, items = emptyList()))
        private set

    /** The card currently being moved, so its row can show the wait. `null` when idle. */
    var movingItemID: WorkItemID? by mutableStateOf(null)
        private set

    private var rawItems: List<WorkItem> = emptyList()

    /** Whether to show only tasks assigned to the current user. */
    var showOnlyMyTasks: Boolean by mutableStateOf(false)
        private set

    fun toggleShowOnlyMyTasks() {
        showOnlyMyTasks = !showOnlyMyTasks
        updateProjection()
    }

    private fun assigneeMatchesCurrentUser(assignee: String?): Boolean {
        val current = currentUserName?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val target = assignee?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val currentFirst = assigneeFirstName(current)
        val targetFirst = assigneeFirstName(target)
        return currentFirst.isNotEmpty() && targetFirst.isNotEmpty() &&
            currentFirst.equals(targetFirst, ignoreCase = true)
    }

    private fun updateProjection() {
        val filtered = if (showOnlyMyTasks) {
            rawItems.filter { assigneeMatchesCurrentUser(it.assignee) }
        } else {
            rawItems
        }
        projection = BoardProjection(
            project = projection.project,
            items = filtered,
        )
    }

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        val project = repository.project(projectID)
        rawItems = repository.workItems(projectID)
        val filtered = if (showOnlyMyTasks) {
            rawItems.filter { assigneeMatchesCurrentUser(it.assignee) }
        } else {
            rawItems
        }
        projection = BoardProjection(
            project = project,
            items = filtered,
        )
    }

    /**
     * Moves a card to [to], and reports whether it moved.
     *
     * The rank is left alone deliberately: `MoveWorkItem` appends to the destination
     * column, which is what a drag from another column should do. Preserving the old rank
     * would drop a card dragged from the middle of one lane into an arbitrary slot in the
     * next, which is the failure `ProjectRepositoryPort.moveWorkItem` documents.
     */
    suspend fun move(itemID: WorkItemID, to: WorkItemStatus): Boolean = acting(itemID) {
        moveWorkItem(id = itemID, to = to)
    }

    /** The columns a card may be dragged to: the board's own, in board order. */
    val destinationStatuses: List<WorkItemStatus> get() = WorkItemStatus.boardOrder

    /**
     * Files a card away into the archive, and reports whether it did.
     *
     * Present because [ProjectBoardScreen] has always drawn a "File Away" button on every
     * non-terminal card while this model had no verb for it: a host that wired the button to
     * nothing would have shipped a live-looking control that does nothing, and a host that
     * reached for [com.tasktracker.domain.WorkItemStatus.Closed] itself would have
     * duplicated [ArchiveWorkItem]'s guard — the one that refuses to archive something
     * already archived rather than reporting a no-op as a success.
     *
     * Shares [movingItemID] with [move] rather than keeping a second busy field, because the
     * screen reads one id to decide which card shows the wait, and a card that was being
     * moved and a card that was being archived are the same state from the card's point of
     * view: something is happening to this card and not to that one.
     */
    suspend fun fileAway(itemID: WorkItemID): Boolean = acting(itemID) {
        archiveWorkItem(id = itemID)
    }

    /**
     * Reports a write's outcome as a boolean, with the card's busy flag held for the write.
     *
     * The `try`/`finally` rather than the `reporting` alone: the flag has to come down even
     * when the write threw, and a card left permanently busy is a card whose buttons are
     * dead for the rest of the session.
     */
    private suspend fun acting(itemID: WorkItemID, block: suspend () -> Unit): Boolean {
        movingItemID = itemID
        return try {
            reporting { block() } != null
        } finally {
            movingItemID = null
        }
    }
}
