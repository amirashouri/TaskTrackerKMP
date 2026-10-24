package com.tasktracker.sharedui

import com.tasktracker.core.AttachmentID
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.CommentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TagID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.core.randomCommentID
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Recurrence
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

    // Sprints are *read* for real since 23.6, unlike every other unimplemented member in this
    // file. The Sprints tab calls `sprints(projectID)` on every appearance, so leaving it as
    // `unused(...)` would mean the tab cannot be rendered by any shell test at all — and a
    // destination nothing can reach is a destination nothing checks.
    //
    // Membership is *written* for real too, since the sprint detail gained its add/remove
    // tasks: `setWorkItemSprint` is the write behind both verbs, and a fake that threw there
    // would make every shell scenario about task membership fail for the wrong reason. The
    // other sprint writes (`createSprint`, `finishSprint`, …) stay `unused` because no shell
    // scenario drives those forms yet, and still fail loudly when one tries.
    private val storedSprints = mutableListOf<Sprint>()

    // Attachments are needed by the WorkItemEditorDestination in NavigationHost, which loads
    // them when the editor opens. A fake that threw would make every editor scenario fail on
    // read.
    private val storedAttachments = mutableListOf<Attachment>()

    /** Seeds a sprint directly, the way [seed] does for a project. */
    fun seedSprint(sprint: Sprint) {
        storedSprints += sprint.copy()
    }

    /** Every write the shell's code path made, so a test can assert a verb reached the store. */
    val writes = mutableListOf<String>()

    /**
     * Every `sprintWorkItems` read the shell's code path made, so a scenario can assert the
     * sprint detail was *not* reloaded. The read is the reload's observable signature: the
     * detail model re-reads it on every `observe`, so a count that does not move across an
     * editor open/close proves the destination was never torn down. `shared-ui`'s scenarios
     * assert this deliberately, because a rebuild-flicker is a composition defect, not a
     * pixel one.
     */
    val sprintWorkItemReads = mutableListOf<SprintID>()

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

    /**
     * The sprint detail's membership write: add tasks to a sprint, or send them back.
     *
     * Mirrors `RoomProjectRepository` and the presentation module's fake: an empty list or
     * an unmentioned patch is a caller that changes nothing, so there is no write and no
     * change to announce — collapsing `Unmentioned` to `null` would clear membership that
     * every real store leaves alone, and a test asserting "sent back" against it would pass
     * against a behaviour no store has. The publish is load-bearing for the UI: the detail
     * model does not re-read after a write, it reloads when the change bus tells it to, so a
     * fake that skipped it would leave the screen stale while `writes` still recorded success.
     */
    override suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem> {
        if (workItemIDs.isEmpty() || sprintID is Patch.Unmentioned) return emptyList()
        val target = (sprintID as Patch.Of).value
        return writing(
            kind = ProjectChange.Kind.WorkItems,
            projectID = { storedItems.firstOrNull { it.id in workItemIDs }?.projectID },
        ) {
            storedItems.filter { it.id in workItemIDs }.forEach {
                it.sprintID = target
                it.updatedAt = now
            }
            writes += "setWorkItemSprint"
            storedItems.filter { it.id in workItemIDs }.map { it.copy() }
        }
    }

    // Reachable from the shell: the Settings retry affordance. A shell fake holds no outbox,
    // so there is never a dead row to revive — zero is the honest count here, not a stub.
    override suspend fun retryStuckChanges(now: Instant): Int = 0

    // MARK: Not reachable from the shell

    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> =
        unused("statusChanges")

    // Reachable from the shell: opening the work-item editor loads the thread, so an
    // `unused(...)` here would make every editor scenario fail on read. Empty — a shell
    // test seeds through `addComment` if it needs content.
    override suspend fun comments(workItemID: WorkItemID): List<Comment> = emptyList()

    override suspend fun addComment(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID?,
        now: Instant,
    ): Comment = locked {
        writes += "addComment"
        Comment(
            id = randomCommentID(),
            workItemID = workItemID,
            authorID = authorID,
            body = body,
            createdAt = now,
            parentID = parentID,
        )
    }

    // MARK: Attachments

    // Reachable from the shell: opening the work-item editor loads the attachments, so an
    // `unused(...)` here would make every editor scenario fail on read. Empty — a shell
    // test seeds through `seed(attachment: Attachment)` if it needs content.
    override suspend fun attachments(workItemID: WorkItemID): List<Attachment> = locked {
        storedAttachments.filter { it.workItemID == workItemID }.map { it.copy() }
    }

    override suspend fun addAttachment(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        now: Instant,
    ): Attachment = locked {
        writes += "addAttachment"
        val attachment = Attachment(
            id = randomAttachmentID(),
            workItemID = workItemID,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            blobPath = "",
            createdAt = now,
        )
        storedAttachments += attachment
        attachment.copy()
    }

    override suspend fun deleteAttachment(id: AttachmentID) = locked {
        val index = storedAttachments.indexOfFirst { it.id == id }
        if (index >= 0) {
            storedAttachments.removeAt(index)
            writes += "deleteAttachment"
        }
    }

    override suspend fun sprints(projectID: ProjectID): List<Sprint> = locked {
        // Open first, then by start date — the order the real port documents as part of its
        // contract, so a shell test does not depend on insertion order by accident.
        storedSprints.filter { it.projectID == projectID }
            .sortedWith(compareBy({ it.isFinished }, { it.startDate }))
            .map { it.copy() }
    }

    override suspend fun sprint(id: SprintID): Sprint? = locked {
        storedSprints.firstOrNull { it.id == id }?.copy()
    }

    override suspend fun sprintWorkItems(id: SprintID): List<WorkItem> = locked {
        sprintWorkItemReads += id
        storedItems.filter { it.sprintID == id }.sortedBy { it.rank }.map { it.copy() }
    }

    override suspend fun createSprint(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int,
        goal: String?,
        startDate: Instant,
        endDate: Instant,
        ownerID: UserID?,
        now: Instant,
    ): Sprint = unused("createSprint")

    override suspend fun updateSprint(
        id: SprintID,
        name: String?,
        goalStoryPoints: Int?,
        goal: Patch<String?>,
        startDate: Instant?,
        endDate: Instant?,
        ownerID: Patch<UserID?>,
        now: Instant,
    ): Sprint = unused("updateSprint")

    override suspend fun finishSprint(id: SprintID, now: Instant): Sprint = unused("finishSprint")

    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint = unused("reopenSprint")

    // Present only to satisfy the port: no shell test archives a sprint, so these follow the
    // same `unused` contract as `createSprint`/`finishSprint` above. They were missing, which
    // left this class failing to compile before any dependency work landed.
    override suspend fun archiveSprint(id: SprintID, now: Instant): Sprint = unused("archiveSprint")

    override suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint =
        unused("unarchiveSprint")

    override suspend fun deleteSprint(id: SprintID, now: Instant): Unit = unused("deleteSprint")

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
        assigneeID: UserID?,
        dueDate: Instant?,
        storyPoints: Int?,
        sprintID: SprintID?,
        now: Instant,
        blockedByIDs: List<WorkItemID>,
        recurrence: Recurrence?,
        tagIDs: List<TagID>,
    ): WorkItem = unused("createWorkItem")

    override suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assigneeID: Patch<UserID?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
        blockedByIDs: Patch<List<WorkItemID>>,
        recurrence: Patch<Recurrence?>,
        tagIDs: Patch<List<TagID>>,
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
    isArchived: Boolean = false,
): Project = Project(
    id = id,
    keyPrefix = keyPrefix,
    name = name,
    details = "",
    isArchived = isArchived,
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
    sprintID: SprintID? = null,
): WorkItem = WorkItem(
    projectID = projectID,
    key = ProjectRules.workItemKey(keyPrefix, number),
    title = title,
    details = "",
    status = status,
    priority = Priority.Medium,
    assigneeID = null,
    dueDate = null,
    rank = 0,
    sprintID = sprintID,
    createdAt = Instant.fromEpochSeconds(1_760_000_000),
    updatedAt = Instant.fromEpochSeconds(1_760_000_000),
    resolvedAt = null,
)

/**
 * The identity shell fixtures assign ownership and assignment to.
 *
 * Fixed rather than random: a scenario that seeds a sprint and later reads it back has to
 * compare the same id twice.
 */
val shellTestUserID = UserID("99999999-9999-4999-8999-999999999999")

/**
 * A sprint fixture, on a fixed clock so nothing in a shell test depends on `now`.
 *
 * The window is a fortnight in March 2026 at UTC midnight, matching the presentation
 * module's `testSprint`, so a date assertion written against one reads the same against the
 * other. The goal is non-zero because the list row's progress is `null` without one, and a
 * fixture that could only exercise the no-goal case would leave the common path untested.
 *
 * [startDate] and [endDate] are parameters because the shell's clock is the fixed
 * `TestDateProvider()` (October 2025), so "planned", "active" and "finished" are window
 * decisions: the default window is *ahead* of that clock and therefore editable, and a
 * scenario wanting the locked state passes a window the clock falls inside. [isFinished]
 * locks it regardless of the window, as the real store's flag does.
 */
fun shellTestSprint(
    projectID: ProjectID,
    name: String = "Sprint 7",
    goalStoryPoints: Int = 12,
    ownerID: UserID? = shellTestUserID,
    isFinished: Boolean = false,
    id: SprintID = SprintID("33333333-3333-3333-3333-333333333333"),
    startDate: Instant = Instant.parse("2026-03-02T00:00:00Z"),
    endDate: Instant = Instant.parse("2026-03-16T00:00:00Z"),
): Sprint = Sprint(
    id = id,
    projectID = projectID,
    name = name,
    goalStoryPoints = goalStoryPoints,
    startDate = startDate,
    endDate = endDate,
    ownerID = ownerID,
    isFinished = isFinished,
    // Presence, not the instant: `Sprint.reconcileFinishedAt`'s invariant is that the two
    // agree — non-null exactly when finished — and no shell assertion reads the date, so
    // deriving it from the window keeps the invariant under a custom window for free.
    finishedAt = if (isFinished) endDate else null,
    createdAt = Instant.fromEpochSeconds(1_760_000_000),
    updatedAt = Instant.fromEpochSeconds(1_760_000_000),
)
