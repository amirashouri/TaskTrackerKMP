package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.SprintStatus
import com.tasktracker.domain.SprintSummary
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintBlockingOverlap
import com.tasktracker.domain.sprintSummary
import com.tasktracker.featureprojectsdomain.AddWorkItemsToSprint
import com.tasktracker.featureprojectsdomain.ArchiveSprint
import com.tasktracker.featureprojectsdomain.CreateSprint
import com.tasktracker.featureprojectsdomain.DeleteSprint
import com.tasktracker.featureprojectsdomain.FinishSprint
import com.tasktracker.featureprojectsdomain.LockSprint
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.RemoveWorkItemsFromSprint
import com.tasktracker.featureprojectsdomain.ReopenSprint
import com.tasktracker.featureprojectsdomain.UnlockSprint
import com.tasktracker.featureprojectsdomain.UpdateSprint
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Presentation model for creating and editing a sprint.
 * Edits dates as [LocalDate] and converts to [Instant] on save.
 * Pre-validates date ranges and prevents overlapping sibling windows.
 */
class SprintEditorModel(
    private val projectID: ProjectID,
    private val existing: Sprint?,
    /** Sprints in this project used to validate against overlapping windows. */
    private val projectSprints: List<Sprint> = emptyList(),
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ProjectModel() {

    private val createSprint = CreateSprint(repository, dateProvider)
    private val updateSprint = UpdateSprint(repository, dateProvider)

    private val originalName: String? = existing?.name
    private val originalGoalStoryPoints: Int? = existing?.goalStoryPoints
    private val originalGoal: String? = existing?.goal
    private val originalOwnerID: UserID? = existing?.ownerID
    private val originalStartDate: LocalDate? = existing?.let {
        sprintLocalDate(it.startDate, timeZone)
    }
    private val originalEndDate: LocalDate? = existing?.let {
        sprintLocalDate(it.endDate, timeZone)
    }

    var name: String by mutableStateOf(existing?.name.orEmpty())
        private set

    var goalStoryPoints: Int? by mutableStateOf(existing?.goalStoryPoints)
        private set

    var goal: String by mutableStateOf(existing?.goal.orEmpty())
        private set

    var startDate: LocalDate? by mutableStateOf(originalStartDate)
        private set

    var endDate: LocalDate? by mutableStateOf(originalEndDate)
        private set

    var ownerID: UserID? by mutableStateOf(existing?.ownerID)
        private set

    var savedSprintID: SprintID? by mutableStateOf(existing?.id)
        private set

    val isCreating: Boolean get() = existing == null

    /** True when sprint is locked or finished, freezing date window and story point cap. */
    val windowLocked: Boolean = existing?.let { sprint -> sprint.isLocked || sprint.isFinished } ?: false

    val hasChanges: Boolean
        get() = isCreating || (
                name.trim() != originalName ||
                        goalStoryPoints != originalGoalStoryPoints ||
                        goal.trim() != originalGoal.orEmpty() ||
                        startDate != originalStartDate ||
                        endDate != originalEndDate ||
                        ownerID != originalOwnerID
                )

    /** True if end date occurs strictly before start date. */
    val endIsBeforeStart: Boolean?
        get() {
            val start = startDate ?: return null
            val end = endDate ?: return null
            return end < start
        }

    /** Name of any overlapping unfinished sprint in this project, or null if clear. */
    val blockedBySprintName: String?
        get() {
            val start = startDate ?: return null
            val end = endDate ?: return null
            if (end < start) return null
            return sprintBlockingOverlap(
                sprintInstant(start, timeZone),
                sprintInstant(end, timeZone),
                projectSprints.filter { it.id != existing?.id },
            )?.name
        }

    val canSave: Boolean
        get() = name.isNotBlank() && startDate != null && endDate != null &&
                endIsBeforeStart != true && blockedBySprintName == null && hasChanges

    fun onNameChanged(value: String) {
        name = value
    }

    fun onGoalStoryPointsChanged(value: String) {
        if (windowLocked) return
        val trimmed = value.trim()
        goalStoryPoints = when {
            trimmed.isEmpty() -> null
            else -> trimmed.toIntOrNull()?.takeIf { it >= 0 }
        }
    }

    fun onGoalChanged(value: String) {
        goal = value
    }

    fun onStartDateChanged(value: LocalDate) {
        if (windowLocked) return
        startDate = value
    }

    fun onEndDateChanged(value: LocalDate) {
        if (windowLocked) return
        endDate = value
    }

    fun onOwnerChanged(value: UserID?) {
        ownerID = value
    }

    /** Creates or updates sprint, sending only modified fields via [Patch]. */
    suspend fun save(): Boolean {
        if (!canSave) return false

        val start = startDate
        val end = endDate
        if (start == null || end == null) return false

        val saved = reporting {
            if (isCreating) {
                createSprint(
                    projectID = projectID,
                    name = name,
                    goalStoryPoints = goalStoryPoints ?: 0,
                    goal = goal,
                    startDate = sprintInstant(start, timeZone),
                    endDate = sprintInstant(end, timeZone),
                    ownerID = ownerID,
                )
            } else {
                updateSprint(
                    id = requireNotNull(savedSprintID),
                    name = name.trim().takeIf { it != originalName },
                    goalStoryPoints = goalStoryPoints?.takeIf { it != originalGoalStoryPoints },
                    goal = if (goal.trim() == originalGoal.orEmpty()) {
                        Patch.Unmentioned
                    } else {
                        Patch.Of(goal.trim().takeIf { it.isNotEmpty() })
                    },
                    startDate = start.takeIf { it != originalStartDate }
                        ?.let { sprintInstant(it, timeZone) },
                    endDate = end.takeIf { it != originalEndDate }
                        ?.let { sprintInstant(it, timeZone) },
                    ownerID = if (ownerID == originalOwnerID) {
                        Patch.Unmentioned
                    } else {
                        Patch.Of(ownerID)
                    },
                )
            }
        } ?: return false

        savedSprintID = saved.id
        return true
    }
}

/**
 * Immutable view model representing a sprint, its items, and domain-computed summary statistics.
 */
data class SprintDetail(
    val sprint: Sprint,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val items: List<WorkItem>,
    val summary: SprintSummary,
    val isViewerTheOwner: Boolean,
    val isActive: Boolean,
    val status: SprintStatus,
) {
    /** True if sprint is open and unlocked, allowing task additions and removals. */
    val canEditItems: Boolean
        get() = !sprint.isFinished && !sprint.isLocked

    /** True if viewer owns the sprint or if sprint is unassigned. */
    val canManageLock: Boolean
        get() = !hasOwner || isViewerTheOwner

    /** True if sprint is past or non-active, and viewer is authorized to finish it. */
    val canFinish: Boolean
        get() = !sprint.isFinished && !isActive && (!hasOwner || isViewerTheOwner)

    val hasOwner: Boolean get() = sprint.ownerID != null

    val hasSummaryToShow: Boolean get() = sprint.isFinished && !summary.isEmpty
}

/**
 * Presentation model for the sprint detail screen.
 * Automatically marks sprints finished if their end date has passed, and coordinates task membership.
 */
class SprintDetailModel(
    val sprintID: SprintID,
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    private val currentUserID: UserID? = null,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    private val finishSprint = FinishSprint(repository, dateProvider)
    private val archiveSprint = ArchiveSprint(repository, dateProvider)
    private val reopenSprint = ReopenSprint(repository, dateProvider)
    private val lockSprint = LockSprint(repository, dateProvider)
    private val unlockSprint = UnlockSprint(repository, dateProvider)
    private val deleteSprint = DeleteSprint(repository, dateProvider)
    private val addWorkItemsToSprint = AddWorkItemsToSprint(repository, dateProvider)
    private val removeWorkItemsFromSprint = RemoveWorkItemsFromSprint(repository, dateProvider)

    var detail: SprintDetail? by mutableStateOf(null)
        private set

    var isBusy: Boolean by mutableStateOf(false)
        private set

    var isPickerOpen: Boolean by mutableStateOf(false)
        private set

    /** Available unassigned and non-terminal project items eligible for sprint addition. */
    var pickableItems: List<WorkItem> by mutableStateOf(emptyList())
        private set

    var selectedPickables: Set<WorkItemID> by mutableStateOf(emptySet())
        private set

    val canAddSelected: Boolean
        get() = selectedPickables.isNotEmpty()

    // Listens to all feature changes (sprint updates notify on project ID)
    override fun isRelevant(change: ProjectChange): Boolean = true

    override suspend fun reload() {
        val sprint = repository.sprint(sprintID) ?: run {
            detail = null
            pickableItems = emptyList()
            selectedPickables = emptySet()
            return
        }
        val items = repository.sprintWorkItems(sprintID)
        val now = dateProvider.now()

        // Auto-finish sprint if end date has elapsed
        val effectiveSprint = if (!sprint.isFinished && now > sprint.endDate) {
            finishSprint(sprintID)
        } else {
            sprint
        }
        detail = SprintDetail(
            sprint = effectiveSprint,
            startDate = sprintLocalDate(effectiveSprint.startDate, timeZone),
            endDate = sprintLocalDate(effectiveSprint.endDate, timeZone),
            items = items,
            summary = sprintSummary(effectiveSprint, items),
            isViewerTheOwner = effectiveSprint.ownerID != null &&
                    effectiveSprint.ownerID == currentUserID,
            isActive = effectiveSprint.isActiveOn(now),
            status = effectiveSprint.statusAt(now),
        )
        pickableItems = repository.workItems(sprint.projectID)
            .filter {
                it.sprintID == null &&
                        it.status != WorkItemStatus.Closed &&
                        it.status != WorkItemStatus.Done
            }
        selectedPickables = selectedPickables
            .filter { id -> pickableItems.any { it.id == id } }
            .toSet()
    }

    fun openPicker() {
        if (detail?.canEditItems != true) return
        selectedPickables = emptySet()
        isPickerOpen = true
    }

    fun closePicker() {
        isPickerOpen = false
        selectedPickables = emptySet()
    }

    fun togglePickable(id: WorkItemID) {
        selectedPickables = if (id in selectedPickables) {
            selectedPickables - id
        } else {
            selectedPickables + id
        }
    }

    suspend fun addSelected(): Boolean {
        if (selectedPickables.isEmpty()) return false
        val moved = acting {
            addWorkItemsToSprint(workItemIDs = selectedPickables.toList(), sprintID = sprintID)
        }
        if (moved) closePicker()
        return moved
    }

    suspend fun removeItem(id: WorkItemID): Boolean = acting {
        removeWorkItemsFromSprint(workItemIDs = listOf(id), sprintID = sprintID)
    }

    suspend fun finish(): Boolean = acting { finishSprint(sprintID) }

    suspend fun archive(): Boolean = acting { archiveSprint(sprintID) }

    suspend fun removeSprint(): Boolean = acting { deleteSprint(sprintID) }

    suspend fun reopen(): Boolean = acting { reopenSprint(sprintID) }

    suspend fun lock(): Boolean = acting { lockSprint(sprintID) }

    suspend fun unlock(): Boolean = acting { unlockSprint(sprintID) }

    private suspend fun acting(block: suspend () -> Unit): Boolean {
        isBusy = true
        return try {
            reporting { block() } != null
        } finally {
            isBusy = false
        }
    }
}