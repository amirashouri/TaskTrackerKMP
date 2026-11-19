package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.SprintMember
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Creates a sprint after validating window and inputs.
 */
class CreateSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int = 0,
        goal: String? = null,
        startDate: Instant,
        endDate: Instant,
        ownerID: UserID? = null,
    ): Sprint {
        val name = ProjectRules.normaliseName(name)
        requireWindow(startDate = startDate, endDate = endDate)
        return repository.createSprint(
            projectID = projectID,
            name = name,
            goalStoryPoints = maxOf(0, goalStoryPoints),
            goal = goal?.trim()?.takeIf { it.isNotEmpty() },
            startDate = startDate,
            endDate = endDate,
            ownerID = ownerID,
            now = dateProvider.now(),
        )
    }
}

/**
 * Updates an existing sprint.
 */
class UpdateSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        id: SprintID,
        name: String? = null,
        goalStoryPoints: Int? = null,
        goal: Patch<String?> = Patch.Unmentioned,
        startDate: Instant? = null,
        endDate: Instant? = null,
        ownerID: Patch<UserID?> = Patch.Unmentioned,
    ): Sprint {
        val existing = repository.sprint(id)
            ?: throw AppError.Storage("That sprint no longer exists.")

        if (existing.isLocked &&
            (goalStoryPoints != null || startDate != null || endDate != null)
        ) {
            throw AppError.Validation(
                "'${existing.name}' is locked. Unlock it to change its window or point cap.",
            )
        }

        requireWindow(
            startDate = startDate ?: existing.startDate,
            endDate = endDate ?: existing.endDate,
        )

        return repository.updateSprint(
            id = id,
            name = name,
            goalStoryPoints = goalStoryPoints,
            goal = if (goal is Patch.Of) {
                Patch.Of(goal.value?.trim()?.takeIf { it.isNotEmpty() })
            } else {
                Patch.Unmentioned
            },
            startDate = startDate,
            endDate = endDate,
            ownerID = ownerID,
            now = dateProvider.now(),
        )
    }
}

class FinishSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.finishSprint(id = id, now = dateProvider.now())
}

class ReopenSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.reopenSprint(id = id, now = dateProvider.now())
}

class LockSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.setSprintLocked(id = id, isLocked = true, now = dateProvider.now())
}

class UnlockSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.setSprintLocked(id = id, isLocked = false, now = dateProvider.now())
}

class ArchiveSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.archiveSprint(id = id, now = dateProvider.now())
}

class DeleteSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID) {
        repository.sprint(id)?.let { sprint ->
            if (sprint.isLocked) {
                throw AppError.Validation("'${sprint.name}' is locked. Unlock it before removing it.")
            }
        }
        repository.deleteSprint(id = id, now = dateProvider.now())
    }
}

/**
 * Adds work items to a sprint.
 */
class AddWorkItemsToSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        workItemIDs: List<WorkItemID>,
        sprintID: SprintID,
    ): List<WorkItem> {
        if (workItemIDs.isEmpty()) return emptyList()
        val sprint = repository.sprint(sprintID)
            ?: throw AppError.Storage("That sprint no longer exists.")
        requireMembershipEditable(sprint)
        val now = dateProvider.now()
        val commits = workItemIDs.map { id ->
            val item = repository.workItem(id)
                ?: throw AppError.Validation("That work item no longer exists.")
            if (item.status == WorkItemStatus.Done || item.status == WorkItemStatus.Closed) {
                throw AppError.Validation(
                    "'${item.key}' is already finished, so it cannot be added to a sprint.",
                )
            }
            item
        }
        val cap = sprint.goalStoryPoints
        if (cap > 0) {
            val incoming = commits.filter { it.sprintID != sprintID }.sumOf { it.storyPoints ?: 0 }
            val held = repository.sprintWorkItems(sprintID).sumOf { it.storyPoints ?: 0 }
            val total = held + incoming
            if (total > cap) {
                throw AppError.Validation(
                    "Adding these would put $total pts in '${sprint.name}', over its $cap pt cap.",
                )
            }
        }
        commits.filter { it.status == WorkItemStatus.Backlog }.forEach { item ->
            repository.moveWorkItem(id = item.id, to = WorkItemStatus.Todo, rank = null, now = now)
        }
        return repository.setWorkItemSprint(
            workItemIDs = workItemIDs,
            sprintID = Patch.Of(sprintID),
            now = now,
        )
    }
}

/**
 * Adds a person to a sprint with the capacity they offered for that window.
 *
 * The gate is the same one that protects task membership: a lock or a finished
 * sprint freezes the roster. A duplicate person is a validation error rather
 * than a second row — two capacities for one member would make the summary
 * count the same work twice.
 */
class AddSprintMember(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {
    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        sprintID: SprintID,
        userID: UserID,
        availableStoryPoints: Int,
    ): SprintMember {
        val sprint = repository.sprint(sprintID)
            ?: throw AppError.Storage("That sprint no longer exists.")
        requireMembershipEditable(sprint)
        if (availableStoryPoints < 0) {
            throw AppError.Validation("Available points cannot be negative.")
        }
        val id = SprintMember.idFor(sprintID, userID)
        if (repository.sprintMembers(sprintID).any { it.id == id }) {
            throw AppError.Validation("That member is already in this sprint.")
        }
        return repository.createSprintMember(
            sprintID = sprintID,
            userID = userID,
            availableStoryPoints = availableStoryPoints,
            now = dateProvider.now(),
        )
    }
}

class UpdateSprintMemberCapacity(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {
    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        sprintID: SprintID,
        id: String,
        availableStoryPoints: Int,
    ): SprintMember {
        val sprint = repository.sprint(sprintID)
            ?: throw AppError.Storage("That sprint no longer exists.")
        requireMembershipEditable(sprint)
        if (availableStoryPoints < 0) {
            throw AppError.Validation("Available points cannot be negative.")
        }
        if (repository.sprintMembers(sprintID).none { it.id == id }) {
            throw AppError.Storage("That member no longer exists.")
        }
        return repository.updateSprintMember(
            id = id,
            availableStoryPoints = availableStoryPoints,
            now = dateProvider.now(),
        )
    }
}

class RemoveSprintMember(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {
    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(sprintID: SprintID, id: String, userID: UserID) {
        val sprint = repository.sprint(sprintID)
            ?: throw AppError.Storage("That sprint no longer exists.")
        requireMembershipEditable(sprint)
        if (repository.sprintMembers(sprintID).none { it.id == id }) {
            throw AppError.Storage("That member no longer exists.")
        }
        if (repository.sprintWorkItems(sprintID).any { it.assigneeID == userID }) {
            throw AppError.Validation(
                "That member still has tasks in '${sprint.name}'. Reassign them first.",
            )
        }
        repository.deleteSprintMember(id = id, now = dateProvider.now())
    }
}

/**
 * Removes work items from a sprint.
 */
class RemoveWorkItemsFromSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        workItemIDs: List<WorkItemID>,
        sprintID: SprintID,
    ): List<WorkItem> {
        if (workItemIDs.isEmpty()) return emptyList()
        val sprint = repository.sprint(sprintID)
            ?: throw AppError.Storage("That sprint no longer exists.")
        requireMembershipEditable(sprint)
        return repository.setWorkItemSprint(
            workItemIDs = workItemIDs,
            sprintID = Patch.Of(null),
            now = dateProvider.now(),
        )
    }
}

private fun requireMembershipEditable(sprint: Sprint) {
    if (sprint.isFinished) {
        throw AppError.Validation("'${sprint.name}' is finished, so its tasks cannot be changed.")
    }
    if (sprint.isLocked) {
        throw AppError.Validation("'${sprint.name}' is locked, so its tasks cannot be changed.")
    }
}

private fun requireWindow(startDate: Instant, endDate: Instant) {
    if (endDate < startDate) {
        throw AppError.Validation("A sprint cannot end before it starts.")
    }
}
