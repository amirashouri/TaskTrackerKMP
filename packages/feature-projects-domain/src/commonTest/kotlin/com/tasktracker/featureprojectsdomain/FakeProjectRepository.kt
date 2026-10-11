package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomSprintID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintBlockingOverlap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * An in-memory stand-in for the port, so the use cases can be tested without a database.
 *
 * This is the reason the port exists. These tests would otherwise need a real store to
 * check that [CreateProject] refuses a blank name, which is slow, flaky under parallel
 * execution, and says nothing about the use case at all. `:packages:feature-projects-data`
 * covers the real repository; this covers the decisions.
 *
 * ## Why [Mutex] and not `@Synchronized`
 *
 * The original used an `NSLock` to make an `@unchecked Sendable` class safe. `@Synchronized`
 * does not exist in Kotlin common code — it is a JVM intrinsic — so a multiplatform fake has
 * to reach for `kotlinx.coroutines.sync.Mutex`, which costs one thing: `withLock` suspends,
 * so the test-control surface below is suspend too. That is the right trade for a class whose
 * whole job is to run inside `runTest`, and the alternative — a `ReentrantLock` behind
 * `expect`/`actual` pairs — buys synchronous test helpers at the cost of a real-source-set
 * file that has to be maintained for one fixture.
 *
 * ## The one place the original faked it
 *
 * `changes()` hand-rolled a dictionary of `AsyncStream.Continuation`s and its own comment
 * admitted the leak: no `onTermination` cleanup, "so the fake gains a data race it does not
 * need", and a subscriber table left to grow for the lifetime of the object. This delegates
 * to [Broadcaster], which unregisters a subscriber when its collecting coroutine is
 * cancelled — the property the original had to document its way around, and the reason
 * [Broadcaster] exists in `:packages:core` rather than a `MutableSharedFlow` per call site.
 */
class FakeProjectRepository : ProjectRepositoryPort {

    private val lock = Mutex()
    private val broadcaster = Broadcaster<ProjectChange>()

    private val storedProjects = mutableListOf<Project>()
    private val storedItems = mutableListOf<WorkItem>()
    private val storedStatusChanges = mutableListOf<StatusChange>()
    private val storedSprints = mutableListOf<Sprint>()
    private val recordedChanges = mutableListOf<ProjectChange>()

    private var itemCounter = 0
    private var moveCalls = 0

    /** Set to make the next call throw, for testing that a use case surfaces a failure. */
    var failure: AppError? = null

    // MARK: Reads

    suspend fun projectCount(): Int = lock.withLock { storedProjects.size }

    suspend fun itemCount(): Int = lock.withLock { storedItems.size }

    /** Every [ProjectChange] published so far, in order. */
    suspend fun publishedChanges(): List<ProjectChange> = lock.withLock { recordedChanges.toList() }

    suspend fun moveCallCount(): Int = lock.withLock { moveCalls }

    override suspend fun projects(includeArchived: Boolean): List<Project> {
        throwIfFailing()
        return lock.withLock { storedProjects.filter { includeArchived || !it.isArchived } }
    }

    override suspend fun project(id: ProjectID): Project? {
        throwIfFailing()
        return lock.withLock { storedProjects.firstOrNull { it.id == id } }
    }

    override suspend fun workItems(projectID: ProjectID): List<WorkItem> {
        throwIfFailing()
        return lock.withLock { storedItems.filter { it.projectID == projectID } }
    }

    override suspend fun workItem(id: WorkItemID): WorkItem? {
        throwIfFailing()
        return lock.withLock { storedItems.firstOrNull { it.id == id } }
    }

    override suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int> {
        throwIfFailing()
        return lock.withLock {
            storedItems.filter { it.projectID == projectID }
                .groupingBy { it.status }
                .eachCount()
        }
    }

    override suspend fun isEmpty(): Boolean {
        throwIfFailing()
        return lock.withLock { storedProjects.isEmpty() }
    }

    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> {
        throwIfFailing()
        return lock.withLock {
            // Newest first, with insertion order as the tiebreak. Two changes can share an
            // instant — a card created and triaged in the same clock tick, or a seed row
            // and the move that immediately follows it — and a stable sort on the timestamp
            // alone hands those back in insertion order, which is the *oldest* order. So
            // "newest first" is only true as a total order if the tie is broken, and the
            // real store has to break it the same way; see the note on the port.
            storedStatusChanges.withIndex()
                .filter { it.value.workItemID == workItemID }
                .sortedWith(
                    compareByDescending<IndexedValue<StatusChange>> { it.value.changedAt }
                        .thenByDescending { it.index },
                )
                .map { it.value }
        }
    }

    // MARK: Sprints
    //
    // Not exercised by any use-case test yet - the sprint use cases are a later phase - but
    // implemented rather than stubbed, because a fake that threw here would be a landmine
    // for the first test written against it, and a fake that silently did nothing would be
    // a worse one. The rules below are the store's, deliberately: a fake that diverges
    // from `:packages:feature-projects-data` is how the two would drift apart unnoticed.

    override suspend fun sprints(projectID: ProjectID): List<Sprint> {
        throwIfFailing()
        return lock.withLock {
            storedSprints.filter { it.projectID == projectID }
                .sortedWith(compareBy({ it.isFinished }, { it.startDate }))
        }
    }

    override suspend fun sprint(id: SprintID): Sprint? {
        throwIfFailing()
        return lock.withLock { storedSprints.firstOrNull { it.id == id } }
    }

    override suspend fun sprintWorkItems(id: SprintID): List<WorkItem> {
        throwIfFailing()
        return lock.withLock {
            storedItems.filter { it.sprintID == id }.sortedBy { it.rank }
        }
    }

    override suspend fun createSprint(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int,
        goal: String?,
        startDate: Instant,
        endDate: Instant,
        owner: String?,
        now: Instant,
    ): Sprint {
        throwIfFailing()
        return lock.withLock {
            sprintBlockingOverlap(
                startDate,
                endDate,
                storedSprints.filter { it.projectID == projectID },
            )?.let {
                throw AppError.Validation("'${it.name}' overlaps this window.")
            }
            val sprint = Sprint(
                id = randomSprintID(),
                projectID = projectID,
                name = ProjectRules.normaliseName(name),
                goalStoryPoints = goalStoryPoints,
                goal = goal,
                startDate = startDate,
                endDate = endDate,
                owner = owner,
                createdAt = now,
                updatedAt = now,
            )
            storedSprints += sprint
            publish(ProjectChange(ProjectChange.Kind.Sprints, projectID))
            publish(ProjectChange(ProjectChange.Kind.WorkItems, projectID))
            sprint
        }
    }

    override suspend fun updateSprint(
        id: SprintID,
        name: String?,
        goalStoryPoints: Int?,
        goal: Patch<String?>,
        startDate: Instant?,
        endDate: Instant?,
        owner: Patch<String?>,
        now: Instant,
    ): Sprint {
        throwIfFailing()
        return lock.withLock {
            val sprint = storedSprints.firstOrNull { it.id == id }
                ?: throw AppError.Validation("No such sprint.")
            if (name != null) sprint.name = name
            if (goalStoryPoints != null) sprint.goalStoryPoints = goalStoryPoints
            if (goal is Patch.Of) sprint.goal = goal.value
            if (startDate != null) sprint.startDate = startDate
            if (endDate != null) sprint.endDate = endDate
            if (owner is Patch.Of) sprint.owner = owner.value
            sprint.updatedAt = now
            publish(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
            sprint
        }
    }

    override suspend fun finishSprint(id: SprintID, now: Instant): Sprint {
        throwIfFailing()
        return lock.withLock {
            val sprint = storedSprints.firstOrNull { it.id == id }
                ?: throw AppError.Validation("No such sprint.")
            if (sprint.isFinished) throw AppError.Validation("'${sprint.name}' has already been finished.")
            sprint.isFinished = true
            sprint.updatedAt = now
            sprint.reconcileFinishedAt(now)
            publish(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
            sprint
        }
    }

    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint {
        throwIfFailing()
        return lock.withLock {
            val sprint = storedSprints.firstOrNull { it.id == id }
                ?: throw AppError.Validation("No such sprint.")
            sprintBlockingOverlap(
                sprint.startDate,
                sprint.endDate,
                storedSprints.filter { it.projectID == sprint.projectID && it.id != sprint.id },
            )?.let { throw AppError.Validation("'${it.name}' overlaps, so '${sprint.name}' cannot be reopened.") }
            sprint.isFinished = false
            sprint.updatedAt = now
            sprint.reconcileFinishedAt(now)
            publish(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
            sprint
        }
    }

    override suspend fun archiveSprint(id: SprintID, now: Instant): Sprint {
        throwIfFailing()
        return lock.withLock {
            val sprint = storedSprints.firstOrNull { it.id == id }
                ?: throw AppError.Validation("No such sprint.")
            if (!sprint.isFinished) throw AppError.Validation("'${sprint.name}' is not finished yet.")
            sprint.isArchived = true
            sprint.updatedAt = now
            publish(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
            sprint
        }
    }

    override suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint {
        throwIfFailing()
        return lock.withLock {
            val sprint = storedSprints.firstOrNull { it.id == id }
                ?: throw AppError.Validation("No such sprint.")
            sprint.isArchived = false
            sprint.updatedAt = now
            publish(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
            sprint
        }
    }

    override suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem> {
        throwIfFailing()
        // Mirrors `RoomProjectRepository`: `Unmentioned` is a caller that does not care, so
        // it changes nothing and returns empty — it is *not* "send them back". Collapsing
        // it to `null` here would clear the field the real store leaves alone, and a
        // domain test asserting `Patch.Of(null)` would then pass against a behaviour no
        // store has. See `sendingItemsBackToTheBacklogIsDistinctFromNotMentioningTheirSprint`.
        if (workItemIDs.isEmpty() || sprintID is Patch.Unmentioned) return emptyList()
        return lock.withLock {
            val target = (sprintID as Patch.Of).value
            val moved = workItemIDs.map { wanted ->
                storedItems.firstOrNull { it.id == wanted }
                    ?: throw AppError.Validation("No such work item.")
            }
            moved.forEach {
                it.sprintID = target
                it.updatedAt = now
            }
            // Both kinds, because a sprint's membership is both: the sprint's own item list
            // and every board the cards moved off.
            val projectID = moved.firstOrNull()?.projectID
            if (projectID != null) {
                publish(ProjectChange(ProjectChange.Kind.Sprints, projectID))
                publish(ProjectChange(ProjectChange.Kind.WorkItems, projectID))
            }
            moved.toList()
        }
    }

    override suspend fun deleteSprint(id: SprintID, now: Instant) {
        throwIfFailing()
        lock.withLock {
            val sprint = storedSprints.firstOrNull { it.id == id }
                ?: throw AppError.Validation("No such sprint.")
            storedSprints.remove(sprint)
            storedItems.filter { it.sprintID == id }.forEach {
                it.sprintID = null
                it.updatedAt = now
            }
            publish(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
            publish(ProjectChange(ProjectChange.Kind.WorkItems, sprint.projectID))
        }
    }

    override suspend fun seedSampleDataIfNeeded(): Boolean {
        throwIfFailing()
        return lock.withLock {
            if (storedProjects.isNotEmpty()) return@withLock false
            val epoch = Instant.fromEpochSeconds(0)
            storedProjects += Project(
                keyPrefix = "SEED",
                name = "Seed",
                details = "",
                createdAt = epoch,
                updatedAt = epoch,
            )
            true
        }
    }

    // MARK: Writes

    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID>,
    ): Project {
        throwIfFailing()
        return lock.withLock {
            val project = Project(
                keyPrefix = keyPrefix,
                name = name,
                details = details,
                memberIDs = memberIDs,
                createdAt = now,
                updatedAt = now,
            )
            storedProjects += project
            publish(ProjectChange(ProjectChange.Kind.Projects, project.id))
            project
        }
    }

    override suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>?,
    ): Project {
        throwIfFailing()
        return lock.withLock {
            val index = storedProjects.indexOfFirst { it.id == id }
                .also { requireFound(it, "No such project.") }
            val project = storedProjects[index]
            if (name != null) project.name = name
            if (details != null) project.details = details
            if (memberIDs != null) project.memberIDs = memberIDs
            project.updatedAt = now
            publish(ProjectChange(ProjectChange.Kind.Projects, id))
            project
        }
    }

    override suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project {
        throwIfFailing()
        return lock.withLock {
            val index = storedProjects.indexOfFirst { it.id == id }
                .also { requireFound(it, "No such project.") }
            val project = storedProjects[index]
            project.isArchived = isArchived
            project.updatedAt = now
            publish(ProjectChange(ProjectChange.Kind.Projects, id))
            project
        }
    }

    override suspend fun deleteProject(id: ProjectID) {
        throwIfFailing()
        lock.withLock {
            if (storedProjects.none { it.id == id }) throw AppError.Validation("No such project.")
            storedProjects.removeAll { it.id == id }
            storedItems.removeAll { it.projectID == id }
            storedStatusChanges.removeAll { it.projectID == id }
            publish(ProjectChange(ProjectChange.Kind.Projects, id))
        }
    }

    override suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        priority: Priority,
        assignee: String?,
        dueDate: Instant?,
        storyPoints: Int?,
        sprintID: SprintID?,
        now: Instant,
        blockedByIDs: List<WorkItemID>,
        recurrence: Recurrence?,
    ): WorkItem {
        throwIfFailing()
        return lock.withLock {
            if (storedProjects.none { it.id == projectID }) {
                throw AppError.Validation("No such project.")
            }
            itemCounter += 1
            val item = WorkItem(
                projectID = projectID,
                key = WorkItemKey("FAKE-$itemCounter"),
                title = title,
                details = details,
                status = status,
                priority = priority,
                assignee = assignee,
                dueDate = dueDate,
                recurrence = recurrence,
                storyPoints = storyPoints,
                blockedByIDs = blockedByIDs,
                sprintID = sprintID,
                rank = itemCounter,
                createdAt = now,
                updatedAt = now,
            )
            // The real repository maintains the `resolvedAt` invariant on create, and the
            // fake must match it — otherwise a domain test written against the fake quietly
            // asserts a contract the store does not honour, and the archive is the one
            // place that difference shows up.
            item.reconcileResolutionAt(now)
            storedItems += item
            record(
                StatusChange(
                    workItemID = item.id,
                    projectID = projectID,
                    from = null,
                    to = status,
                    changedAt = now,
                    changedBy = null,
                ),
            )
            publish(ProjectChange(ProjectChange.Kind.WorkItems, projectID))
            item
        }
    }

    override suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
        blockedByIDs: Patch<List<WorkItemID>>,
        recurrence: Patch<Recurrence?>,
    ): WorkItem {
        throwIfFailing()
        return lock.withLock {
            val index = storedItems.indexOfFirst { it.id == id }
                .also { requireFound(it, "No such work item.") }
            val item = storedItems[index]
            if (title != null) item.title = title
            if (details != null) item.details = details
            if (priority != null) item.priority = priority
            // The two `Patch`es are the whole reason this type exists: an `Unmentioned`
            // leaves the field alone and an `Of(null)` clears it, and a plain `String?`
            // could not tell those apart.
            when (assignee) {
                is Patch.Unmentioned -> Unit
                is Patch.Of -> item.assignee = assignee.value
            }
            when (dueDate) {
                is Patch.Unmentioned -> Unit
                is Patch.Of -> item.dueDate = dueDate.value
            }
            when (storyPoints) {
                is Patch.Unmentioned -> Unit
                is Patch.Of -> item.storyPoints = storyPoints.value
            }
            when (blockedByIDs) {
                is Patch.Unmentioned -> Unit
                is Patch.Of -> item.blockedByIDs = blockedByIDs.value
            }
            when (recurrence) {
                is Patch.Unmentioned -> Unit
                is Patch.Of -> item.recurrence = recurrence.value
            }
            item.updatedAt = now
            publish(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID))
            item
        }
    }

    override suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem {
        throwIfFailing()
        return lock.withLock {
            moveCalls += 1
            val index = storedItems.indexOfFirst { it.id == id }
                .also { requireFound(it, "No such work item.") }
            val item = storedItems[index]
            val previous = item.status
            item.status = to
            if (rank != null) item.rank = rank
            item.updatedAt = now
            item.reconcileResolutionAt(now)
            if (previous != to) {
                record(
                    StatusChange(
                        workItemID = id,
                        projectID = item.projectID,
                        from = previous,
                        to = to,
                        changedAt = now,
                        changedBy = null,
                    ),
                )
            }
            publish(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID))
            item
        }
    }

    override suspend fun deleteWorkItem(id: WorkItemID) {
        throwIfFailing()
        lock.withLock {
            val index = storedItems.indexOfFirst { it.id == id }
                .also { requireFound(it, "No such work item.") }
            val owner = storedItems[index].projectID
            storedItems.removeAt(index)
            storedStatusChanges.removeAll { it.workItemID == id }
            publish(ProjectChange(ProjectChange.Kind.WorkItems, owner))
        }
    }

    // MARK: Invalidation

    override fun changes(): Flow<ProjectChange> = broadcaster.stream()

    /**
     * Always called from inside `withLock`, so the record list and the subscriber set are
     * mutated together and a reader can never see one updated and the other not.
     */
    private fun publish(change: ProjectChange) {
        recordedChanges += change
        broadcaster.send(change)
    }

    /**
     * Always called from inside `withLock`, and never announces anything: a history row is
     * written in the same transaction as the move that caused it, so the move has already
     * invalidated the screens that care.
     */
    private fun record(change: StatusChange) {
        storedStatusChanges += change
    }

    // MARK: Test control

    /**
     * Seeds a project directly, so a test can set up a board without going through the use
     * case it is meant to be exercising.
     */
    suspend fun seedProject(name: String = "Apollo", keyPrefix: String = "APOLLO"): Project =
        lock.withLock {
            val epoch = Instant.fromEpochSeconds(0)
            val project = Project(
                keyPrefix = keyPrefix,
                name = name,
                details = "",
                createdAt = epoch,
                updatedAt = epoch,
            )
            storedProjects += project
            project
        }

    /**
     * Seeds a work item directly, with the epoch as its timestamp.
     *
     * Deliberately *not* publishing a change: this is fixture setup, and a collector
     * asserting on a board would otherwise see events for rows it never asked for.
     */
    suspend fun seedWorkItem(
        projectID: ProjectID,
        title: String = "Task",
        status: WorkItemStatus = WorkItemStatus.Todo,
        storyPoints: Int? = null,
    ): WorkItem = lock.withLock {
        itemCounter += 1
        val epoch = Instant.fromEpochSeconds(0)
        val item = WorkItem(
            projectID = projectID,
            key = WorkItemKey("FAKE-$itemCounter"),
            title = title,
            status = status,
            storyPoints = storyPoints,
            rank = itemCounter,
            createdAt = epoch,
            updatedAt = epoch,
        )
        storedItems += item
        item
    }

    private fun throwIfFailing() {
        failure?.let { throw it }
    }

    /**
     * Turns `indexOfFirst`'s "not found" sentinel into a throw.
     *
     * The original wrote `firstIndex(where:)`, which returns an `Optional` and so could be
     * unwrapped with a `guard let`. Kotlin's [kotlin.collections.List.indexOfFirst] returns
     * `-1`, and `-1` is neither `null` nor out of bounds — so the obvious `?: throw` here
     * compiles, reads as though it works, and does nothing at all. A missing row then
     * reached `storedProjects[-1]`, and a test for "editing a deleted project is refused"
     * failed with an `IndexOutOfBoundsException` rather than the `AppError` the use case is
     * supposed to raise.
     */
    private fun requireFound(index: Int, message: String) {
        if (index < 0) throw AppError.Validation(message)
    }
}
