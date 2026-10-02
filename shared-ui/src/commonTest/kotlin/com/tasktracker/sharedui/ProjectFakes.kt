package com.tasktracker.sharedui

import com.tasktracker.core.Broadcaster
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
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
 * A project store for the shell tests, covering only what the shell's code path touches.
 *
 * ## Why this is a third implementation of the same port
 *
 * `:packages:feature-projects-domain` and `:packages:feature-projects-presentation` each have
 * one, and neither can be used here: a `commonTest` source set is compiled per module and is
 * on no other module's classpath, so there is no mechanism in this build for sharing one. A
 * real `RoomProjectRepository` is not an option either — on the Android host the store's
 * builder needs a `Context`, which is exactly why `:packages:database` and
 * `:packages:feature-projects-data` both compile their android target and disable its host
 * tests.
 *
 * So this is small *on purpose* rather than a third full copy: it implements the reads and
 * writes the projects list, the board and the new-project sheet actually make, and every
 * other member fails with a message naming itself. A shell test that reaches one has asked
 * for something the shell has no business doing, and a silent default would have let that
 * pass.
 *
 * ## Reads hand back copies
 *
 * The same rule the presentation module's fake follows, for the same reason: `Project` and
 * `WorkItem` are `var`-bearing data classes, and a model that stored a shared instance in
 * `mutableStateOf` would have its state write dropped as structurally equal after a
 * mutation — a stale screen with no error. Copying on read is what the real port does.
 *
 * ## The change bus is a real [Broadcaster]
 *
 * Not a `StateFlow`: the port's contract is a *cold* flow with no replay, and
 * `ObservingProjectModel` depends on that — it reads the store itself on subscribe rather
 * than trusting an emission. A replaying double would make the observe/stop tests pass for
 * the wrong reason.
 */
class FakeProjectRepository : ProjectRepositoryPort {

    private val lock = Mutex()
    private val broadcaster = Broadcaster<ProjectChange>()

    private val storedProjects = mutableListOf<Project>()
    private val storedItems = mutableListOf<WorkItem>()

    /** Every write the shell's code path made, so a test can assert a verb reached the store. */
    val writes = mutableListOf<String>()

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

    /** Derived from the stored items, so it cannot drift from what the screens are drawing. */
    override suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int> = locked {
        storedItems.filter { it.projectID == projectID }.groupingBy { it.status }.eachCount()
    }

    override suspend fun isEmpty(): Boolean = locked { storedProjects.isEmpty() }

    // MARK: Writes the shell drives

    /**
     * Seeds one project, so the list is not empty on a first frame.
     *
     * The real store seeds a sample board on first launch, and `ProjectsListModel` calls this
     * on its first read — so a fake that inserted nothing would show a shell whose dashboard
     * is permanently the empty state, and the "open a project" scenario would have nothing to
     * open. It is a no-op once anything exists, exactly as the port documents.
     */
    override suspend fun seedSampleDataIfNeeded(): Boolean {
        var created: Project? = null
        val seeded = locked {
            if (storedProjects.isNotEmpty()) return@locked false
            val project = sampleProject()
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
    ): Project = writing(ProjectChange.Kind.Projects) {
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
        project.copy()
    }

    override suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project =
        writing(ProjectChange.Kind.Projects) {
            val existing = storedProjects.first { it.id == id }
            existing.isArchived = isArchived
            existing.updatedAt = now
            writes += "setArchived"
            existing.copy()
        }

    override suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem = writing(
        kind = ProjectChange.Kind.WorkItems,
        projectID = { storedItems.first { it.id == id }.projectID },
    ) {
        val existing = storedItems.first { it.id == id }
        existing.status = to
        existing.updatedAt = now
        if (rank != null) existing.rank = rank
        if (to.isTerminal && existing.resolvedAt == null) existing.resolvedAt = now
        writes += "moveWorkItem:$to"
        existing.copy()
    }

    // MARK: Not reachable from the shell

    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> =
        unused("statusChanges")

    override suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>?,
    ): Project = unused("updateProject")

    override suspend fun deleteProject(id: ProjectID): Unit = unused("deleteProject")

    override suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        priority: Priority,
        assignee: String?,
        dueDate: Instant?,
        now: Instant,
    ): WorkItem = unused("createWorkItem")

    override suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        now: Instant,
    ): WorkItem = unused("updateWorkItem")

    override suspend fun deleteWorkItem(id: WorkItemID): Unit = unused("deleteWorkItem")

    override fun changes(): Flow<ProjectChange> = broadcaster.stream()

    // MARK: Test control

    /** Adds a project directly, without recording a write or announcing a change. */
    suspend fun seed(project: Project) = locked { storedProjects += project }

    /** Adds a work item directly, without recording a write. */
    suspend fun seed(item: WorkItem) = locked { storedItems += item }

    /** How many collectors the bus currently has. Zero after the shell leaves the screen. */
    val subscriberCount: Int get() = broadcaster.subscriberCount

    // MARK: Internals

    private suspend fun <T> locked(block: () -> T): T = lock.withLock { block() }

    /**
     * Runs a write under [lock], then announces it *after* releasing it.
     *
     * Emitting inside the critical section deadlocks the observer: every model here re-reads
     * the store on a change, and a non-reentrant [Mutex] would have the publishing thread
     * waiting on the read it just woke.
     */
    /**
     * Runs a write under [lock], then announces it *after* releasing it.
     *
     * Emitting inside the critical section deadlocks the observer: every model here re-reads
     * the store on a change, and a non-reentrant [Mutex] would have the publishing thread
     * waiting on the read it just woke.
     *
     * [projectID] is a lambda rather than a value because the item a write touches is only
     * known inside the critical section, and getting it wrong is a test that passes for the
     * wrong reason: an observer filters a `WorkItems` change on the project id, so announcing
     * someone else's id leaves a board stale while the assertion — which reads the fake's
     * `writes`, not the screen — still passes. Left `null` for project-wide changes, which is
     * the honest "everything changed" and matches what a real `Projects` change carries.
     */
    private suspend fun <T> writing(
        kind: ProjectChange.Kind,
        projectID: (() -> ProjectID?)? = null,
        block: () -> T,
    ): T {
        var pending: ProjectChange? = null
        val result = lock.withLock {
            val value = block()
            pending = ProjectChange(kind = kind, projectID = projectID?.invoke())
            value
        }
        pending?.let { publish(it) }
        return result
    }

    private suspend fun publish(change: ProjectChange) {
        broadcaster.send(change)
    }

    private fun <T> unused(name: String): T =
        error("FakeProjectRepository.$name is not implemented: the shell has no path that calls it.")

    private fun sampleProject(): Project = Project(
        keyPrefix = "TT",
        name = "Task Tracker",
        createdAt = FIXED,
        updatedAt = FIXED,
    )

    private companion object {
        val FIXED: Instant = Instant.fromEpochSeconds(1_760_000_000)
    }
}

/** A project fixture on a fixed clock, so no shell assertion depends on `now`. */
fun shellTestProject(
    name: String = "Apollo",
    keyPrefix: String = "AP",
    id: ProjectID = randomProjectID(),
    updatedAt: Instant = Instant.fromEpochSeconds(1_760_000_000),
): Project = Project(
    id = id,
    keyPrefix = keyPrefix,
    name = name,
    details = "",
    isArchived = false,
    nextItemNumber = 1,
    createdAt = updatedAt,
    updatedAt = updatedAt,
)

/** A work item fixture on a fixed clock, with a key built from its project's prefix. */
fun shellTestWorkItem(
    projectID: ProjectID,
    keyPrefix: String = "AP",
    number: Int = 1,
    title: String = "A task",
    status: WorkItemStatus = WorkItemStatus.Todo,
): WorkItem = WorkItem(
    projectID = projectID,
    key = ProjectRules.workItemKey(keyPrefix, number),
    title = title,
    details = "",
    status = status,
    priority = Priority.Medium,
    assignee = null,
    dueDate = null,
    rank = 0,
    createdAt = Instant.fromEpochSeconds(1_760_000_000),
    updatedAt = Instant.fromEpochSeconds(1_760_000_000),
    resolvedAt = null,
)
