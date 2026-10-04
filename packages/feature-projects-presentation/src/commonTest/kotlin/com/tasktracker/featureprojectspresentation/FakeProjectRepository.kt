package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

/**
 * An in-memory [ProjectRepositoryPort] for the model tests.
 *
 * ## Why there is a second one
 *
 * `:packages:feature-projects-domain` has its own fake, and reusing it is not an option:
 * a `commonTest` source set is compiled per module and is not on any other module's
 * classpath, so it cannot be depended upon. This is a second implementation of the same
 * seam, written for a different job rather than copied for convenience.
 *
 * The difference is scope. The domain fake exists to check the *use cases* - the decisions
 * - so it counts calls, can fail a chosen call, and records what was written. This one
 * exists to check the *models*, which read much more often than they write, so the emphasis
 * is the other way round: a fake the tests can seed with a specific shape (two projects, a
 * done task, a closed task) and then push a `ProjectChange` at by hand, which is how the
 * observation logic gets tested without waiting for a write to land.
 *
 * ## The parts that have to be right
 *
 * - **[createWorkItem] issues a real key.** It takes the prefix from the project and the
 *   number from `nextItemNumber`, then increments. `WorkItemEditorModel` reads the key back
 *   from the store rather than composing one, precisely because the number depends on what
 *   the project has already handed out - so a fake that numbered from 1 every time would
 *   make that test pass for the wrong reason.
 * - **[counts] is derived from the stored items** rather than kept alongside them, so it
 *   cannot drift from the data the models are asserting on.
 * - **[deleteProject] cascades.** `RemoveProject` only deletes when nothing is
 *   outstanding, so a non-cascading fake would hide a real bug where an archived-then-deleted
 *   project left its work items behind.
 * - **[changes] goes through [Broadcaster]**, which unregisters a cancelled collector - the
 *   property the models' `observe`/`stopObserving` depend on.
 *
 * Uses [Mutex] rather than `@Synchronized`, which is a JVM intrinsic and does not exist in
 * Kotlin common code. The cost is that the control surface below is `suspend`.
 *
 * ## Every read hands back a copy, and that is load-bearing
 *
 * The real port maps a database row to a fresh entity on every read, so a caller holding a
 * [WorkItem] cannot see a later write reach through it. This fake originally kept and
 * returned the *same* mutable instances, which is friendlier to write and quietly wrong.
 *
 * It cost an afternoon of tests that all looked like unrelated model bugs - a backlog that
 * did not empty, a board that kept a moved card, an archive with a stale month. Every model
 * stores its projection in `mutableStateOf`, whose default `structuralEqualityPolicy` drops
 * a write whose new value `equals` the old one. A projection is a `data class` over
 * entities, so with shared instances a status change mutated the entity *inside* the
 * already-stored projection: the new projection compared equal to the old, the state write
 * was silently discarded, and the screen kept showing the object it had before. Nothing
 * threw, and `refresh()` could not fix it.
 *
 * Copying on read restores the property the real port already has, and with it the
 * notification. The alternative - making every model use `neverEqualPolicy` - would have
 * papered over an unfaithful double and left the real repository as the only thing that had
 * to be trusted.
 */
class FakeProjectRepository : ProjectRepositoryPort {

    private val lock = Mutex()
    private val broadcaster = Broadcaster<ProjectChange>()

    private val storedProjects = mutableListOf<Project>()
    private val storedItems = mutableListOf<WorkItem>()
    private val storedStatusChanges = mutableListOf<StatusChange>()
    private val storedSprints = mutableListOf<Sprint>()

    /** Written, so a test can assert which use case ran and with what. */
    val writes = mutableListOf<String>()

    /** Every `ProjectChange` that has gone out, whether from a write or a manual [emit]. */
    val emittedChanges = mutableListOf<ProjectChange>()

    /** Throw this from the next call of the named operation. */
    var failure: AppError? = null

    /** Throw this instead of returning empty, from `seedSampleDataIfNeeded`. */
    var seedFailure: AppError? = null

    /** How many times `seedSampleDataIfNeeded` actually inserted. */
    var seedCount: Int = 0
        private set

    /** Set to a count to make the next `createWorkItem` return that id. */
    var nextItemRank: Int? = null

    // MARK: Reads

    override suspend fun projects(includeArchived: Boolean): List<Project> = locked {
        storedProjects.filter { includeArchived || !it.isArchived }.map { it.copy() }
    }

    override suspend fun project(id: ProjectID): Project? = locked {
        storedProjects.firstOrNull { it.id == id }?.copy()
    }

    override suspend fun workItems(projectID: ProjectID): List<WorkItem> = locked {
        storedItems.filter { it.projectID == projectID }.map { it.copy() }
    }

    override suspend fun workItem(id: WorkItemID): WorkItem? = locked {
        storedItems.firstOrNull { it.id == id }?.copy()
    }

    override suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int> = locked {
        storedItems.filter { it.projectID == projectID }
            .groupingBy { it.status }
            .eachCount()
    }

    override suspend fun isEmpty(): Boolean = locked { storedProjects.isEmpty() }

    // MARK: Sprints
    //
    // Real storage rather than stubs: this fake's whole value is that it cannot drift from
    // the port, and a stubbed sprint path would drift the moment a model called it.

    override suspend fun sprints(projectID: ProjectID): List<Sprint> = locked {
        storedSprints.filter { it.projectID == projectID }
            .sortedWith(compareBy({ it.isFinished }, { it.startDate }))
            .map { it.copy() }
    }

    override suspend fun sprint(id: SprintID): Sprint? = locked {
        storedSprints.firstOrNull { it.id == id }?.copy()
    }

    override suspend fun sprintWorkItems(id: SprintID): List<WorkItem> = locked {
        storedItems.filter { it.sprintID == id }.sortedBy { it.rank }.map { it.copy() }
    }

    override suspend fun createSprint(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int,
        startDate: Instant,
        endDate: Instant,
        owner: String?,
        now: Instant,
    ): Sprint {
        return sprintWrite({ projectID }) {
            failIfSet()
        storedSprints.firstOrNull { it.projectID == projectID && !it.isFinished }?.let {
            throw AppError.Validation("'${it.name}' is still open.")
        }
        val sprint = Sprint(
            id = randomSprintID(),
            projectID = projectID,
            name = ProjectRules.normaliseName(name),
            goalStoryPoints = goalStoryPoints,
            startDate = startDate,
            endDate = endDate,
            owner = owner,
            createdAt = now,
            updatedAt = now,
        )
        storedSprints += sprint
            writes += "createSprint"
            sprint.copy()
        }
    }

    override suspend fun updateSprint(
        id: SprintID,
        name: String?,
        goalStoryPoints: Int?,
        startDate: Instant?,
        endDate: Instant?,
        owner: Patch<String?>,
        now: Instant,
    ): Sprint = sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
        failIfSet()
        val existing = storedSprints.first { it.id == id }
        if (name != null) existing.name = name
        if (goalStoryPoints != null) existing.goalStoryPoints = goalStoryPoints
        if (startDate != null) existing.startDate = startDate
        if (endDate != null) existing.endDate = endDate
        if (owner is Patch.Of) existing.owner = owner.value
        existing.updatedAt = now
        writes += "updateSprint"
        existing.copy()
    }

    override suspend fun finishSprint(id: SprintID, now: Instant): Sprint =
        sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
            failIfSet()
            val existing = storedSprints.first { it.id == id }
            if (existing.isFinished) throw AppError.Validation("'${existing.name}' has already been finished.")
            existing.isFinished = true
            existing.updatedAt = now
            existing.reconcileFinishedAt(now)
            writes += "finishSprint"
            existing.copy()
        }

    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint =
        sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
            failIfSet()
            val existing = storedSprints.first { it.id == id }
            storedSprints.firstOrNull { it.projectID == existing.projectID && !it.isFinished }
                ?.takeIf { it.id != existing.id }
                ?.let { throw AppError.Validation("'${it.name}' is open, so '${existing.name}' cannot be reopened.") }
            existing.isFinished = false
            existing.updatedAt = now
            existing.reconcileFinishedAt(now)
            writes += "reopenSprint"
            existing.copy()
        }

    override suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem> = sprintWrite({ storedItems.first { it.id in workItemIDs }.projectID }) {
        failIfSet()
        val target = (sprintID as? Patch.Of)?.value
        storedItems.filter { it.id in workItemIDs }.forEach {
            it.sprintID = target
            it.updatedAt = now
        }
        writes += "setWorkItemSprint"
        storedItems.filter { it.id in workItemIDs }.map { it.copy() }
    }

    override suspend fun deleteSprint(id: SprintID, now: Instant) {
        sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
            failIfSet()
            val existing = storedSprints.first { it.id == id }
            storedSprints.remove(existing)
            storedItems.filter { it.sprintID == id }.forEach {
                it.sprintID = null
                it.updatedAt = now
            }
            writes += "deleteSprint"
        }
    }

    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> = locked {
        // Newest first, with the later-recorded change winning a tie: a card created and
        // triaged inside one clock tick produces two rows with the same `changedAt`, and a
        // sort on the timestamp alone would hand those back in insertion order - which is
        // the oldest order.
        storedStatusChanges.filter { it.workItemID == workItemID }
            .sortedWith(
                compareByDescending<StatusChange> { it.changedAt }
                    .thenByDescending { it.id.stringValue },
            )
    }

    // MARK: Writes

    override suspend fun seedSampleDataIfNeeded(): Boolean {
        var created: Project? = null
        val seeded = locked {
            seedFailure?.let { throw it }
            if (storedProjects.isNotEmpty()) return@locked false
            seedCount++
            val project = Project(
                keyPrefix = "TT",
                name = "Task Tracker",
                createdAt = FIXED_INSTANT,
                updatedAt = FIXED_INSTANT,
            )
            storedProjects += project
            created = project
            true
        }
        created?.let { publish(ProjectChange(ProjectChange.Kind.Projects, it.id)) }
        return seeded
    }

    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID>,
    ): Project {
        var projectID: ProjectID? = null
        return writing({ ProjectChange(ProjectChange.Kind.Projects, projectID!!) }) {
        failIfSet()
        val project = Project(
            keyPrefix = keyPrefix,
            name = name,
            details = details,
            memberIDs = memberIDs,
            createdAt = now,
            updatedAt = now,
        )
        storedProjects += project
        writes += "createProject"
        // The id is not known until the entity exists, so the change names it afterwards.
        projectID = project.id
        project.copy()
        }
    }

    override suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>?,
    ): Project = writing({ ProjectChange(ProjectChange.Kind.Projects, id) }) {
        failIfSet()
        val existing = storedProjects.first { it.id == id }
        if (name != null) existing.name = name
        if (details != null) existing.details = details
        if (memberIDs != null) existing.memberIDs = memberIDs
        existing.updatedAt = now
        writes += "updateProject"
        existing.copy()
    }

    override suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project =
        writing({ ProjectChange(ProjectChange.Kind.Projects, id) }) {
            failIfSet()
            val existing = storedProjects.first { it.id == id }
            existing.isArchived = isArchived
            existing.updatedAt = now
            writes += "setArchived"
            existing.copy()
        }

    override suspend fun deleteProject(id: ProjectID) {
        writing({ ProjectChange(ProjectChange.Kind.Projects, id) }) {
            failIfSet()
            storedProjects.removeAll { it.id == id }
            storedItems.removeAll { it.projectID == id }
            writes += "deleteProject"
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
        now: Instant,
    ): WorkItem = writing({ ProjectChange(ProjectChange.Kind.WorkItems, projectID) }) {
        failIfSet()
        val project = storedProjects.first { it.id == projectID }
        val number = nextItemRank ?: project.nextItemNumber
        nextItemRank = null
        val item = WorkItem(
            id = randomWorkItemID(),
            projectID = projectID,
            key = ProjectRules.workItemKey(project.keyPrefix, number),
            title = title,
            details = details,
            status = status,
            priority = priority,
            assignee = assignee,
            dueDate = dueDate,
            createdAt = now,
            updatedAt = now,
        )
        if (project.nextItemNumber <= number) project.nextItemNumber = number + 1
        storedItems += item
        writes += "createWorkItem"
        recordStatusChange(item, from = null, to = status, at = now)
        item.copy()
    }

    override suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        now: Instant,
    ): WorkItem = writing({
        ProjectChange(ProjectChange.Kind.WorkItems, storedItems.first { it.id == id }.projectID)
    }) {
        failIfSet()
        val existing = storedItems.first { it.id == id }
        if (title != null) existing.title = title
        if (details != null) existing.details = details
        if (priority != null) existing.priority = priority
        when (assignee) {
            is Patch.Of -> existing.assignee = assignee.value
            Patch.Unmentioned -> Unit
        }
        when (dueDate) {
            is Patch.Of -> existing.dueDate = dueDate.value
            Patch.Unmentioned -> Unit
        }
        existing.updatedAt = now
        writes += "updateWorkItem"
        existing.copy()
    }

    override suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem = writing({
        ProjectChange(ProjectChange.Kind.WorkItems, storedItems.first { it.id == id }.projectID)
    }) {
        failIfSet()
        val existing = storedItems.first { it.id == id }
        val from = existing.status
        existing.status = to
        existing.updatedAt = now
        if (rank != null) existing.rank = rank
        if (to.isTerminal && existing.resolvedAt == null) existing.resolvedAt = now
        writes += "moveWorkItem:$to"
        recordStatusChange(existing, from = from, to = to, at = now)
        existing.copy()
    }

    override suspend fun deleteWorkItem(id: WorkItemID) {
        var projectID: ProjectID? = null
        writing({ ProjectChange(ProjectChange.Kind.WorkItems, projectID!!) }) {
            failIfSet()
            val existing = storedItems.first { it.id == id }
            projectID = existing.projectID
            storedItems.remove(existing)
            writes += "deleteWorkItem"
        }
    }

    override fun changes(): Flow<ProjectChange> = broadcaster.stream()

    // MARK: Test control

    /**
     * Pushes a change as though a write had happened elsewhere.
     *
     * The observation tests need a change they did not cause - a second device, or a sync
     * landing - and making the fake produce one by going through a real write would make
     * the test assert on its own side effect rather than on the model's reaction.
     */
    suspend fun emit(change: ProjectChange) = publish(change)

    /** Closes every subscriber, so a collector observes completion. */
    fun finish() = broadcaster.finish()

    /** The projects currently held, for assertions that need the store rather than a model. */
    suspend fun storedProjects(): List<Project> = locked { storedProjects.map { it.copy() } }

    /** Adds a project directly, without recording a write. */
    suspend fun seed(project: Project) = locked { storedProjects += project }

    /** Adds a work item directly, without recording a write or a status change. */
    suspend fun seed(item: WorkItem) = locked { storedItems += item }

    /** Adds a sprint directly, without recording a write. */
    suspend fun seed(sprint: Sprint) = locked { storedSprints += sprint }

    /** How many subscribers the broadcaster currently has. Proves observation stopped. */
    val subscriberCount: Int get() = broadcaster.subscriberCount

    // MARK: Internals

    private suspend fun <T> locked(block: () -> T): T = lock.withLock { block() }

    /**
     * Runs a write under [lock], then publishes its change *after* releasing it.
     *
     * ## Why the send is outside the lock
     *
     * Emitting from inside the critical section deadlocks the observer. A subscriber that
     * reacts by reading - which is exactly what every model here does, since each one
     * re-reads the store on a change - calls back into this fake, and [lock] is a
     * `Mutex`, which is not reentrant. So the same logical call stack would hold the lock
     * and then wait for it: `withLock` suspends, the reload never completes, and the test
     * sees stale state with no error anywhere.
     *
     * That showed up as three unrelated-looking failures - a backlog that did not empty, a
     * board that kept a moved card - and none of them said "deadlock". Publishing after the
     * critical section is also the more faithful shape for a real store, where a change
     * becomes visible when the transaction commits rather than while it is still open.
     */
    private suspend fun <T> writing(change: () -> ProjectChange, block: () -> T): T {
        var pending: ProjectChange? = null
        val result = lock.withLock {
            val value = block()
            // Built inside the critical section, published outside it. A create mints its
            // id here and a delete can no longer find its item, so the change cannot
            // always be described before the write.
            pending = change()
            value
        }
        pending?.let { publish(it) }
        return result
    }

    /**
     * A sprint write, which moves *both* a sprint and the membership of its cards.
     *
     * Two kinds, published after the lock for the reason [writing] documents. The project is
     * read inside the block because the row a write touches is only known there.
     */
    private suspend fun <T> sprintWrite(projectID: () -> ProjectID?, block: () -> T): T {
        var owner: ProjectID? = null
        var result: T
        lock.withLock {
            result = block()
            owner = projectID()
        }
        owner?.let {
            publish(ProjectChange(ProjectChange.Kind.Sprints, it))
            publish(ProjectChange(ProjectChange.Kind.WorkItems, it))
        }
        return result
    }

    private suspend fun publish(change: ProjectChange) {
        emittedChanges += change
        broadcaster.send(change)
    }

    private fun failIfSet() {
        failure?.let { throw it }
    }

    private fun recordStatusChange(item: WorkItem, from: WorkItemStatus?, to: WorkItemStatus, at: Instant) {
        storedStatusChanges += StatusChange(
            id = randomStatusChangeID(),
            workItemID = item.id,
            projectID = item.projectID,
            from = from,
            to = to,
            changedAt = at,
            changedBy = null,
        )
    }

    private companion object {
        val FIXED_INSTANT: Instant = Instant.fromEpochSeconds(1_760_000_000)
    }
}

/** A project fixture, on a fixed clock so nothing in a test depends on `now`. */
fun testProject(
    name: String = "Apollo",
    keyPrefix: String = "AP",
    details: String = "",
    isArchived: Boolean = false,
    updatedAt: Instant = Instant.fromEpochSeconds(1_760_000_000),
    nextItemNumber: Int = 1,
    id: ProjectID = randomProjectID(),
): Project = Project(
    id = id,
    keyPrefix = keyPrefix,
    name = name,
    details = details,
    isArchived = isArchived,
    nextItemNumber = nextItemNumber,
    createdAt = updatedAt,
    updatedAt = updatedAt,
)

/** A work item fixture, on a fixed clock. */
@Suppress("LongParameterList")
fun testWorkItem(
    projectID: ProjectID,
    key: String = "AP-1",
    title: String = "A task",
    details: String = "",
    status: WorkItemStatus = WorkItemStatus.Todo,
    priority: Priority = Priority.Medium,
    assignee: String? = null,
    dueDate: Instant? = null,
    rank: Int = 0,
    resolvedAt: Instant? = null,
    id: WorkItemID = randomWorkItemID(),
): WorkItem = WorkItem(
    id = id,
    projectID = projectID,
    key = WorkItemKey(key),
    title = title,
    details = details,
    status = status,
    priority = priority,
    assignee = assignee,
    dueDate = dueDate,
    rank = rank,
    createdAt = FIXED,
    updatedAt = FIXED,
    resolvedAt = resolvedAt,
)

private val FIXED: Instant = Instant.fromEpochSeconds(1_760_000_000)
