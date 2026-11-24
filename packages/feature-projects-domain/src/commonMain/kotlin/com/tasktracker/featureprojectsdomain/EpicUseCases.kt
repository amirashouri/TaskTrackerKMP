package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.EpicID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Epic
import com.tasktracker.domain.EpicRules
import com.tasktracker.domain.WorkItem
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Creates an epic spanning one or more projects.
 *
 * The minimum is enforced here rather than by the picker: the picker is one caller, and a
 * store reachable from a preview or a test must not be able to write a container that the
 * product would never have offered.
 */
class CreateEpic(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        projectIDs: List<ProjectID>,
        title: String,
        details: String = "",
        dueDate: Instant? = null,
    ): Epic = repository.createEpic(
        projectIDs = EpicRules.normaliseProjectIDs(projectIDs),
        title = EpicRules.normaliseTitle(title),
        details = EpicRules.normaliseDetails(details),
        dueDate = dueDate,
        now = dateProvider.now(),
    )
}

/**
 * Edits an epic's authored fields.
 *
 * [projectIDs] is a plain optional rather than a [Patch], because an epic cannot span no
 * projects: "not mentioned" and "cleared" are the same absence of an instruction. The due
 * date is a [Patch] for the opposite reason — clearing it is a real edit.
 */
class UpdateEpic(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        id: EpicID,
        title: String? = null,
        details: String? = null,
        dueDate: Patch<Instant?> = Patch.Unmentioned,
        projectIDs: List<ProjectID>? = null,
    ): Epic {
        val normalisedTitle = title?.let { EpicRules.normaliseTitle(it) }
        val normalisedDetails = details?.let { EpicRules.normaliseDetails(it) }
        val normalisedProjects = projectIDs?.let { EpicRules.normaliseProjectIDs(it) }

        if (normalisedTitle == null &&
            normalisedDetails == null &&
            dueDate is Patch.Unmentioned &&
            normalisedProjects == null
        ) {
            return repository.epic(id)
                ?: throw AppError.Storage("That epic no longer exists.")
        }

        return repository.updateEpic(
            id = id,
            title = normalisedTitle,
            details = normalisedDetails,
            dueDate = dueDate,
            projectIDs = normalisedProjects,
            now = dateProvider.now(),
        )
    }
}

/**
 * Archives an epic, or restores it.
 *
 * [archiveSubtasks] is the choice the archive dialog offers: put only the story away, or
 * put its work away with it. A restore always carries the same flag, because the items the
 * epic filed are exactly the ones it should hand back.
 */
class SetEpicArchived(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        id: EpicID,
        isArchived: Boolean,
        archiveSubtasks: Boolean,
    ): Epic = repository.setEpicArchived(
        id = id,
        isArchived = isArchived,
        archiveSubtasks = archiveSubtasks,
        now = dateProvider.now(),
    )
}

/**
 * Links one work item to an epic, or removes the link.
 *
 * The two rules the product states are checked here, with copy a screen can show:
 * an item belongs to at most one epic (a single field makes a second one impossible), and
 * that epic must span the item's project. The store repeats the project check because a
 * queued link can outlive the editor that made it.
 */
class SetWorkItemEpic(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        workItemID: WorkItemID,
        epicID: Patch<EpicID?>,
    ): WorkItem {
        if (epicID is Patch.Unmentioned) {
            return repository.workItem(workItemID)
                ?: throw AppError.Storage("That work item no longer exists.")
        }

        val item = repository.workItem(workItemID)
            ?: throw AppError.Storage("That work item no longer exists.")

        if (epicID is Patch.Of && epicID.value != null) {
            val target = repository.epic(epicID.value)
                ?: throw AppError.Validation("That epic no longer exists.")
            if (item.projectID !in target.projectIDs) {
                throw AppError.Validation(
                    "That epic does not span this item's project.",
                )
            }
        }

        return repository.setWorkItemEpic(
            workItemID = workItemID,
            epicID = epicID,
            now = dateProvider.now(),
        )
    }
}
