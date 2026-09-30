package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Files a task away: the guarded move into [WorkItemStatus.Closed], the archive-only status.
 *
 * "Archive" is deliberately not a field on the entity — it is a status. `Closed` means
 * *finished with, not delivered*: the decision was taken and the item left the flow, which
 * is exactly what "archived" asks for. Modelling it as a move keeps the whole existing
 * machinery honest for free — the history records who closed it and when, `resolvedAt` is
 * dated so the archive can file it by month, and the outbox queues the change like any
 * other edit.
 *
 * The guard is the decision this use case owns: [WorkItemStatus.Done] and
 * [WorkItemStatus.Closed] are already terminal, and re-archiving an archived task would
 * rewrite its `resolvedAt` — erasing *when it actually finished* and replacing it with
 * the moment somebody tapped the button again. Reopening must go through
 * [MoveWorkItem] instead, which the board and backlog menus already offer.
 *
 * The two classes on `@Throws` are the module-wide convention for a suspend declaration,
 * explained in full on [CreateProject].
 */
class ArchiveWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: WorkItemID): WorkItem {
        // The existence check ran on purpose: a stale screen — or a second device having
        // deleted the task since — now produces "no longer exists" rather than a bare
        // write failure from deep in the store.
        val current = repository.workItem(id)
            ?: throw AppError.Validation("That work item no longer exists.")
        if (current.status.isTerminal) {
            // The key rather than the id, because the key is what the user is looking at.
            // The status is its [WorkItemStatus.wireValue] rather than a display title, for
            // the reason given in [MoveWorkItem]: a user-facing name cannot live behind a
            // module that may not reach `:packages:design-system`, and the wire value is
            // the same string the database column and the deep link already use.
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
