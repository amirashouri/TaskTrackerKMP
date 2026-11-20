package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Files a task away into Closed status, preserving its original resolution date.
 *
 * A `Done` task is accepted and closed, because `Done` is a board lane and `Closed` is the
 * archive: without this a done card had no way out of its lane, and the lane could only grow.
 * Closing is a move like any other, so the history records it, and the resolution date the task
 * earned when it was finished is not rewritten — the fake and the real store both keep an
 * existing `resolvedAt`, which is the date the work actually ended rather than the day someone
 * tidied the board.
 *
 * `Closed` stays refused. Filing an already-closed task away is a mistake in the request, not an
 * idempotent success, and accepting it would rewrite nothing while claiming a change.
 */
class ArchiveWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: WorkItemID): WorkItem {
        val current = repository.workItem(id)
            ?: throw AppError.Validation("That work item no longer exists.")
        if (current.status == WorkItemStatus.Closed) {
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
