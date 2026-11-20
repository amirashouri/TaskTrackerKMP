package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * One item in a batch that could not be acted on, and why.
 *
 * The id rather than the item: the caller has the projection that knows the key, and a batch
 * that failed half-way through should still report on an item it could not even read.
 */
data class WorkItemBatchFailure(
    val id: WorkItemID,
    val error: AppError,
)

/**
 * What a batch did, item by item.
 *
 * A batch deliberately does not abort on the first failure: the point of selecting ten items is
 * that eight of them can be dealt with even when two cannot. So the outcome splits three ways,
 * and the caller decides what to tell the user:
 *
 * - [applied] — the action happened; each one is its own per-item write with its own outbox row.
 * - [skipped] — the action was already unnecessary, i.e. the item had become terminal on another
 *   client between the selection and the command. Not an error, and not retryable.
 * - [failures] — the action was refused; [firstFailure] is what the caller reports, because a
 *   sentence per failure would be a wall of text for a set the user is already looking at.
 *
 * [applied] and [skipped] keep the input order, so a report can name the first item that moved.
 */
data class WorkItemBatchOutcome(
    val applied: List<WorkItemID> = emptyList(),
    val skipped: List<WorkItemID> = emptyList(),
    val failures: List<WorkItemBatchFailure> = emptyList(),
) {
    val appliedCount: Int get() = applied.size

    val skippedCount: Int get() = skipped.size

    val failureCount: Int get() = failures.size

    val firstFailure: WorkItemBatchFailure? get() = failures.firstOrNull()

    /** The count this batch was asked to act on, however each item turned out. */
    val attemptedCount: Int get() = applied.size + skipped.size + failures.size

    /** Ids that must stay selected because acting on them again could still work. */
    val retryable: List<WorkItemID> get() = failures.map { it.id }

    val isEmpty: Boolean get() = attemptedCount == 0
}

/**
 * Boards every item in [ids] into one column.
 *
 * The lane is resolved once, from the project's catalog, with the same rule as the single-item
 * path: an unknown or absent [statusKey] falls back to `Todo` with no key, which is the
 * pre-catalog behaviour. Resolving it once rather than per item is the point — a batch must not
 * half-land in one lane and half in another because the catalog changed mid-loop.
 *
 * Each item still goes through [MoveWorkItem], so each one writes its own status history row and
 * its own outbox change. There is no server-side batch and this class must not grow one.
 */
class PutWorkItemsOnBoard(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    private val moveWorkItem = MoveWorkItem(repository, dateProvider)

    @Throws(CancellationException::class)
    suspend operator fun invoke(
        projectID: ProjectID,
        ids: List<WorkItemID>,
        statusKey: String? = null,
    ): WorkItemBatchOutcome {
        val definition = statusKey?.let { key ->
            repository.project(projectID)?.statuses?.firstOrNull { it.id == key }
        }
        val to = definition?.category ?: WorkItemStatus.Todo
        var outcome = WorkItemBatchOutcome()
        for (id in ids.distinct()) {
            try {
                moveWorkItem(id = id, to = to, statusKey = definition?.id)
                outcome = outcome.copy(applied = outcome.applied + id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: AppError) {
                outcome = outcome.copy(failures = outcome.failures + WorkItemBatchFailure(id, failure))
            }
        }
        return outcome
    }
}

/**
 * Files every item in [ids] away through [ArchiveWorkItem].
 *
 * An item that is already terminal — a task another client finished between the selection and
 * this command — is [WorkItemBatchOutcome.skipped] rather than a failure: there is nothing left
 * to archive, and re-archiving would rewrite `resolvedAt`, the date the task actually finished.
 * The check happens before the archive call because [ArchiveWorkItem]'s refusal is a message, not
 * a distinguishable type, and string-matching it here would make the skip depend on the wording.
 *
 * A missing item is still a failure, not a skip: it may have been deleted, and that is worth
 * saying.
 */
class ArchiveWorkItems(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)

    @Throws(CancellationException::class)
    suspend operator fun invoke(ids: List<WorkItemID>): WorkItemBatchOutcome {
        var outcome = WorkItemBatchOutcome()
        for (id in ids.distinct()) {
            val current = repository.workItem(id)
            if (current == null) {
                outcome = outcome.copy(
                    failures = outcome.failures +
                        WorkItemBatchFailure(id, AppError.Validation("That work item no longer exists.")),
                )
                continue
            }
            if (current.status.isTerminal) {
                outcome = outcome.copy(skipped = outcome.skipped + id)
                continue
            }
            try {
                archiveWorkItem(id)
                outcome = outcome.copy(applied = outcome.applied + id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: AppError) {
                outcome = outcome.copy(failures = outcome.failures + WorkItemBatchFailure(id, failure))
            }
        }
        return outcome
    }
}
