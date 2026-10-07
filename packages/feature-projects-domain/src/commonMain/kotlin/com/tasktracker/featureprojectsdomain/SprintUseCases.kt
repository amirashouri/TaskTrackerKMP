package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemRules
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * The decisions a view makes about a sprint before anything reaches the store.
 *
 * ## Why sprint writes need a use case layer at all
 *
 * `ProjectRepositoryPort` grew nine sprint members in 23.3 and nothing above it grew a
 * caller, so for one phase the sprint writes were reachable only by a view model holding
 * the port directly. That is the shape every other write in this feature was moved *away*
 * from: [ProjectUseCases] exists because a decision a view makes — trim a name, refuse an
 * illegal one, fold a status change into the re-rank it implies — is a decision that
 * belongs somewhere reviewable, and a view model is not somewhere.
 *
 * ## What is validated here, and what is not
 *
 * The split is deliberate and it is *not* "the same checks twice":
 *
 * - **Here**: normalisation, and the checks that depend on more than one argument. A
 *   window that ends before it starts is a relation between two fields, and neither
 *   field's own rule can see it.
 * - **In the store**: anything that depends on state the caller does not hold — chiefly
 *   the one-open-sprint rule, which only the transaction can see. See
 *   `ProjectRepositoryPort.createSprint`.
 *
 * A check that appears in both layers is not duplication, because the two layers are
 * asked different questions: this one is "is this edit well-formed", and the store's is
 * "may this project hold it". The reason [ProjectUseCases] restates it anyway is the
 * reason `CreateProject` does: a rejected edit should never reach a store and leave a
 * queued change behind for a sprint that was never created.
 *
 * `now` comes from the injected [DateProvider] on every write, for the same reason as
 * everywhere else in this module — a rule that reads the wall clock is a rule whose tests
 * can only be run on a particular day.
 *
 * ## `@Throws` lists two classes, and that is not padding
 *
 * Every declaration here is `suspend`, and Kotlin/Native **rejects** `@Throws` on a
 * suspend function unless `CancellationException` is among the listed classes. A JVM-only
 * run compiles the identical annotation without complaint, so this reaches `./gradlew
 * check` rather than the module you were working in.
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
        startDate: Instant,
        endDate: Instant,
        owner: String? = null,
    ): Sprint {
        val name = ProjectRules.normaliseName(name)
        // `normaliseAssignee`, not a sprint-specific rule, and the reason is in `Sprint`'s
        // own KDoc: an owner that filtered or displayed differently from an assignee would
        // be two concepts wearing the same word, and "am I the owner" would then be
        // answered inconsistently in two places.
        val owner = WorkItemRules.normaliseAssignee(owner)
        requireWindow(startDate = startDate, endDate = endDate)
        return repository.createSprint(
            projectID = projectID,
            name = name,
            // Floored rather than refused, matching `Sprint`'s own `init` and
            // `updateSprint`: a negative goal is normalised in place so every later reader
            // sees one number rather than a sprint holding a goal no screen can render.
            goalStoryPoints = maxOf(0, goalStoryPoints),
            startDate = startDate,
            endDate = endDate,
            owner = owner,
            now = dateProvider.now(),
        )
    }
}

/**
 * Applies a partial edit to a sprint.
 *
 * ## The window is checked against the *merged* sprint, not against the arguments
 *
 * `end < start` is a relation between two fields, and a partial edit may name only one of
 * them. Refusing only when both are present would therefore accept exactly the edit that
 * is easiest to make by accident: moving the start past the end while leaving the end
 * alone. So this reads the sprint, applies the patch to a copy, and checks *that* — the
 * store's own `updateSprint` makes the same check inside the transaction, where it is the
 * authority, and this one exists so a refused edit does not reach it.
 *
 * The read costs one call and is why this use case is not a three-line forward. It is
 * still cheaper than the alternative: a write that fails inside a transaction after
 * opening it has already touched the outbox.
 *
 * A missing sprint is refused with [AppError.Storage] rather than being silently
 * ignored — the caller asked to edit something, and reporting success for an edit that
 * was never applied is the combination a user reports as a bug with no reproduction.
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
        startDate: Instant? = null,
        endDate: Instant? = null,
        owner: Patch<String?> = Patch.Unmentioned,
    ): Sprint {
        val existing = repository.sprint(id)
            ?: throw AppError.Storage("That sprint no longer exists.")

        requireWindow(
            startDate = startDate ?: existing.startDate,
            endDate = endDate ?: existing.endDate,
        )

        return repository.updateSprint(
            id = id,
            name = name,
            goalStoryPoints = goalStoryPoints,
            startDate = startDate,
            endDate = endDate,
            owner = if (owner is Patch.Of) {
                Patch.Of(WorkItemRules.normaliseAssignee(owner.value))
            } else {
                Patch.Unmentioned
            },
            now = dateProvider.now(),
        )
    }
}

/**
 * Closes a sprint.
 *
 * Thin on purpose: the date, the already-finished refusal and the outbox row all belong
 * to the store, which is the only place that can see them. See
 * `ProjectRepositoryPort.finishSprint`.
 */
class FinishSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.finishSprint(id = id, now = dateProvider.now())
}

/**
 * Reopens a sprint.
 *
 * Also thin, and the one-open rule behind it is likewise the store's: this caller cannot
 * see whether another sprint has been opened in the meantime.
 */
class ReopenSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID): Sprint =
        repository.reopenSprint(id = id, now = dateProvider.now())
}

/**
 * Deletes a sprint, returning its tasks to the backlog.
 *
 * Separate from [CreateSprint] on purpose — see `ProjectRepositoryPort.deleteSprint` for
 * why an abandoned sprint's tasks are kept and its history is not.
 */
class DeleteSprint(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: SprintID) =
        repository.deleteSprint(id = id, now = dateProvider.now())
}

/**
 * Puts work items into a sprint.
 *
 * ## The membership lock is here, not only on the button
 *
 * A sprint's membership may be edited **only while the sprint is neither active nor
 * finished** — a planned sprint is still being assembled, and an active or finished one is
 * a commitment whose committed set is what the summary will report. The detail screen
 * hides the affordance for exactly the same rule (see `SprintDetail.canEditItems`), but a
 * hidden button is a UI state, not a guarantee: the port is reachable from any model, and
 * `setWorkItemSprint` itself makes no check. So the rule is stated once here, where a
 * refusal arrives as the banner sentence the user can read, rather than as a row that
 * silently did not move.
 *
 * "Active" is [Sprint.isActiveOn] — today inside the window, inclusive — plus
 * [Sprint.isFinished], which [Sprint.isActiveOn] deliberately does not consider. The two
 * are checked in that order because a finished sprint can still have today inside its
 * window, and "finished" is the more specific of the two complaints.
 *
 * An empty list short-circuits before the sprint is even read: there is nothing to write,
 * and a refusal for an instruction that asks for nothing would be a banner about a tap the
 * user did not meaningfully make.
 *
 * Two more rules live here, both because a write that changes the board is this use case's
 * job rather than the sheet's:
 *
 * - **A finished task cannot be committed.** A sprint commits work it is going to do, and a
 *   `Done` or `Closed` item is finished work - delivered, or filed away. Committing either
 *   to a *future* window is a contradiction the refusal names out loud. The sheet also
 *   hides this class of task, but a hidden row is UI state, not a guarantee: see the
 *   membership lock above for why the rule is stated once here too.
 * - **A backlog task lands on the board.** Bringing a task that was sitting untriaged in
 *   the backlog into a sprint *is* the sweep that commits it, and a committed task has a
 *   column - `Backlog` is not one, so a committed Backlog task would be present in the
 *   sprint and invisible on its board. It is moved to `Todo`, the first lane. On-board
 *   statuses (Todo, In Progress, In Review) keep themselves; they were already committed
 *   to work.
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
        requireMembershipEditable(sprint, dateProvider.now())
        val now = dateProvider.now()
        // Read every item before writing any of them: committing work into a sprint is one
        // act, so a batch that contains one finished task refuses the whole batch. Partial
        // writes would leave the sheet's banner right and the store wrong.
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
        // `commits` holds the statuses as read above, so the sweep is over the read snapshot
        // even after a move mutates a stored row (the fake's live objects) below it.
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
 * Sends work items from a sprint back to the backlog.
 *
 * The same lock as [AddWorkItemsToSprint], for the same reason read the other way: a task
 * pulled out of an active sprint changes its committed set mid-flight, which is the number
 * the progress bar and the eventual summary are built from. Both directions are one rule —
 * membership is frozen once the sprint starts — and [requireMembershipEditable] is where
 * that rule is written down.
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
        requireMembershipEditable(sprint, dateProvider.now())
        return repository.setWorkItemSprint(
            workItemIDs = workItemIDs,
            // `Of(null)` rather than `Unmentioned`: sending a task back to the backlog is an
            // instruction, and "do not care about membership" is a different one. See
            // `Patch`.
            sprintID = Patch.Of(null),
            now = dateProvider.now(),
        )
    }
}

/**
 * The membership lock, named once so both directions cannot drift apart.
 *
 * [AppError.Validation], so the sentence reaches the user through the banner every model
 * already renders — `AppError.message` is the user-facing text and a use case that reworded
 * it would create a second owner for it.
 */
private fun requireMembershipEditable(sprint: Sprint, now: Instant) {
    if (sprint.isFinished) {
        throw AppError.Validation("'${sprint.name}' is finished, so its tasks cannot be changed.")
    }
    if (sprint.isActiveOn(now)) {
        throw AppError.Validation("'${sprint.name}' is in progress, so its tasks cannot be changed.")
    }
}

/**
 * The one window rule, named once.
 *
 * A **backwards** window is survivable — `Sprint`'s own KDoc stores one rather than
 * throwing, because a value that arrived from a server has to be representable — but
 * *editing into* it is refused. A sprint that ends before it starts is never active, so it
 * silently books nothing against its goal for its whole lifetime, and the summary at the
 * end of it reports a window that reads as a typo with nothing to say so.
 *
 * Inclusive on both ends, matching `Sprint.isActiveOn`: a one-day sprint has
 * `startDate == endDate` and is not backwards.
 */
private fun requireWindow(startDate: Instant, endDate: Instant) {
    if (endDate < startDate) {
        throw AppError.Validation("A sprint cannot end before it starts.")
    }
}
