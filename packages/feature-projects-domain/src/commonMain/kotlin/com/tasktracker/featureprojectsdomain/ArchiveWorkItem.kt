package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Files a task away into Closed status, preserving original resolution date.
 */
class ArchiveWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: WorkItemID): WorkItem {
        val current = repository.workItem(id)
            ?: throw AppError.Validation("That work item no longer exists.")
        if (current.status.isTerminal) {
            throw AppError.Validation(
                "${current.key.rawValue} is already filed away in ${current.status.wireValue}.",
            )
        }
        return repository.moveWorkItem(
            id = id,
            to = WorkItemStatus.Closed,
            rank = null,
            now = dateProvider.now(),
        )
    }
}
