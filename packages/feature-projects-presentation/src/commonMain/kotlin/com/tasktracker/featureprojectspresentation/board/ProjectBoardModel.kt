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
    private val dateProvider: DateProvider,
    /**
     * Who the signed-in user is, or `null` when no session is known.
     *
     * An exact `UserID` comparison, which is the whole point of 0.2: the previous
     * first-name substring match called two people named "Ada" the same person, and
     * called one person "Ada Lovelace" not-themselves when they had typed "Ada L".
     */
    private val currentUserID: UserID? = null,
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

    /** The active sprint for this board, if any. */
    var activeSprint: Sprint? by mutableStateOf<Sprint?>(null)
        private set

    /**
     * Every item's blockers, resolved to the keys the cards draw.
     *
     * ## Why this is a map rather than a lookup the card performs
     *
     * A card is given its keys, the way it is given its assignee's name: the card knows how to
     * be a card, and resolving ids against the project's item list is the *board's* knowledge.
     * A card that reached into the list itself would need the list passed to it as well, which
     * is the same leak in a different shape.
     *
     * ## Why it is keyed by id rather than read off the projection
     *
     * Because the join is against [rawItems] — the project's **whole** list — and not against
     * [projection]'s filtered items. That distinction is load-bearing: a blocker is very often
     * not on the board being looked at. It can be in another sprint, or assigned to someone
     * else while the My Tasks toggle is on, or filed in a terminal column. Joining against the
     * visible cards would drop those, and the card's row would then claim a task waits on two
     * things when it waits on four — silently, and increasingly as filters narrow. The editor
     * resolves the same relation from the same list, so the two agree by construction.
     *
     * Covers every item in [rawItems], not just the visible ones, so toggling a filter cannot
     * change what a *given* card would resolve if it were still shown.
     */
    var blockedByKeys: Map<WorkItemID, List<WorkItemKey>> by mutableStateOf(emptyMap())
        private set

    /**
     * Whether lane-add is locked because a sprint is in progress.
     *
     * True only when [activeSprint]'s [Sprint.statusAt] is [SprintStatus.InProgress] on the
     * model's clock: a scheduled (future) sprint does not own the board yet, Kanban (null)
     * never does, and a finished/archived one is history. The screen hides the column "+"
     * while this holds; the backlog/sprint pickers stay the way tasks join a running sprint.
     */
    var addsLocked: Boolean by mutableStateOf(false)
        private set

    private var rawItems: List<WorkItem> = emptyList()

    /** Whether to show only tasks assigned to the current user. */
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

        // Resolved from `rawItems` rather than from the projection — see [blockedByKeys].
        blockedByKeys = rawItems.associate { item ->
            item.id to dependencyKeysFor(rawItems, item)
        }

        projection = BoardProjection(
            project = project,
            items = currentFilteredItems(),
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
