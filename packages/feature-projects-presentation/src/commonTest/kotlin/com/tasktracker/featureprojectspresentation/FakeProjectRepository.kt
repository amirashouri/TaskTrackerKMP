package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.CommentID
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.core.randomCommentID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintBlockingOverlap
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
    private val storedComments = mutableListOf<Comment>()
    private val storedAttachments = mutableListOf<Attachment>()

    /**
     * The attachment bytes this device holds, keyed by id.
     *
     * Separate from [storedAttachments] because the real store separates them too: the row
     * is metadata and the bytes are a file on disk, addressed by `blobPath`. A fake that
     * kept the bytes in the row would let a test pass on a shape the repository cannot
     * produce — and the upload path reads them through [attachmentBytes] precisely so the
     * two can be missing independently.
     */
    private val blobBytes = mutableMapOf<AttachmentID, ByteArray>()
    private val storedSprints = mutableListOf<Sprint>()

    /** Written, so a test can assert which use case ran and with what. */
    val writes = mutableListOf<String>()

    /** Every `ProjectChange` that has gone out, whether from a write or a manual [emit]. */
    val emittedChanges = mutableListOf<ProjectChange>()

    /** Throw this from the next call of the named operation. */
    var failure: AppError? = null

    /**
     * Throw this from every `createWorkItem`, leaving updates alone.
     *
     * The editor's save is an update whose *expansion* is a create, so a sticky [failure]
     * would take the save down with it; this knob fails only the create, which is how a
     * test can hold "the save landed, the next instance did not".
     */
    var createFailure: AppError? = null

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
        goal: String?,
        startDate: Instant,
        endDate: Instant,
        ownerID: UserID?,
        now: Instant,
    ): Sprint {
        return sprintWrite({ projectID }) {
            failIfSet()
        sprintBlockingOverlap(
            startDate,
            endDate,
            storedSprints.filter { it.projectID == projectID },
        )?.let { throw AppError.Validation("'${it.name}' overlaps this window.") }
        val sprint = Sprint(
            id = randomSprintID(),
            projectID = projectID,
            name = ProjectRules.normaliseName(name),
            goalStoryPoints = goalStoryPoints,
            goal = goal,
            startDate = startDate,
            endDate = endDate,
            ownerID = ownerID,
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
        goal: Patch<String?>,
        startDate: Instant?,
        endDate: Instant?,
        ownerID: Patch<UserID?>,
        now: Instant,
    ): Sprint = sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
        failIfSet()
        val existing = storedSprints.first { it.id == id }
        if (name != null) existing.name = name
        if (goalStoryPoints != null) existing.goalStoryPoints = goalStoryPoints
        if (goal is Patch.Of) existing.goal = goal.value
        if (startDate != null) existing.startDate = startDate
        if (endDate != null) existing.endDate = endDate
        if (ownerID is Patch.Of) existing.ownerID = ownerID.value
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

    override suspend fun archiveSprint(id: SprintID, now: Instant): Sprint = sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
            failIfSet()
            val existing = storedSprints.first { it.id == id }
            if (!existing.isFinished) throw AppError.Validation("'${existing.name}' is not finished yet.")
            existing.isArchived = true
            existing.updatedAt = now
            writes += "archiveSprint"
            existing.copy()
        }

    override suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint = sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
            failIfSet()
            val existing = storedSprints.first { it.id == id }
            if (!existing.isArchived) throw AppError.Validation("'${existing.name}' is not archived.")
            existing.isArchived = false
            existing.updatedAt = now
            writes += "unarchiveSprint"
            existing.copy()
        }

    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint =
        sprintWrite({ storedSprints.first { it.id == id }.projectID }) {
            failIfSet()
            val existing = storedSprints.first { it.id == id }
            sprintBlockingOverlap(
                existing.startDate,
                existing.endDate,
                storedSprints.filter { it.projectID == existing.projectID && it.id != existing.id },
            )?.let { throw AppError.Validation("'${it.name}' overlaps, so '${existing.name}' cannot be reopened.") }
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
    ): List<WorkItem> = sprintWrite({ storedItems.firstOrNull { it.id in workItemIDs }?.projectID }) {
        failIfSet()
        // Mirrors `RoomProjectRepository`: `Unmentioned` is a caller that does not care, so
        // it writes nothing. Collapsing it to `null` would clear the field the real store
        // leaves alone, and a test asserting "sent back" against `Patch.Unmentioned` would
        // then pass against a behaviour no store has.
        if (workItemIDs.isEmpty() || sprintID is Patch.Unmentioned) return@sprintWrite emptyList()
        val target = (sprintID as Patch.Of).value
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

    override suspend fun comments(workItemID: WorkItemID): List<Comment> = locked {
        // Oldest first; the id tiebreak is this fake's stand-in for the real store's
        // insertion-order tiebreak — deterministic, which is what a fake owes a test.
        storedComments.filter { it.workItemID == workItemID }
            .sortedWith(
                compareBy<Comment> { it.createdAt }
                    .thenBy { it.id.stringValue },
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
            val itemIDs = storedItems.filter { it.projectID == id }.map { it.id }
            storedProjects.removeAll { it.id == id }
            storedItems.removeAll { it.projectID == id }
            storedComments.removeAll { it.workItemID in itemIDs }
            writes += "deleteProject"
        }
    }

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
    ): WorkItem = writing({ ProjectChange(ProjectChange.Kind.WorkItems, projectID) }) {
        failIfSet()
        createFailure?.let { throw it }
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
            assigneeID = assigneeID,
            dueDate = dueDate,
            recurrence = recurrence,
            storyPoints = storyPoints,
            blockedByIDs = blockedByIDs,
            sprintID = sprintID,
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
        assigneeID: Patch<UserID?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
        blockedByIDs: Patch<List<WorkItemID>>,
        recurrence: Patch<Recurrence?>,
    ): WorkItem = writing({
        ProjectChange(ProjectChange.Kind.WorkItems, storedItems.first { it.id == id }.projectID)
    }) {
        failIfSet()
        val existing = storedItems.first { it.id == id }
        if (title != null) existing.title = title
        if (details != null) existing.details = details
        if (priority != null) existing.priority = priority
        when (assigneeID) {
            is Patch.Of -> existing.assigneeID = assigneeID.value
            Patch.Unmentioned -> Unit
        }
        when (dueDate) {
            is Patch.Of -> existing.dueDate = dueDate.value
            Patch.Unmentioned -> Unit
        }
        when (storyPoints) {
            is Patch.Of -> existing.storyPoints = storyPoints.value
            Patch.Unmentioned -> Unit
        }
        when (blockedByIDs) {
            is Patch.Of -> existing.blockedByIDs = blockedByIDs.value
            Patch.Unmentioned -> Unit
        }
        when (recurrence) {
            is Patch.Of -> existing.recurrence = recurrence.value
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
            storedComments.removeAll { it.workItemID == id }
            storedAttachments.removeAll { it.workItemID == id }
            writes += "deleteWorkItem"
        }
    }

    override suspend fun addComment(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID?,
        now: Instant,
    ): Comment {
        var projectID: ProjectID? = null
        return writing({ ProjectChange(ProjectChange.Kind.WorkItems, projectID!!) }) {
            failIfSet()
            val existing = storedItems.first { it.id == workItemID }
            projectID = existing.projectID
            val comment = Comment(
                id = randomCommentID(),
                workItemID = workItemID,
                authorID = authorID,
                body = body,
                createdAt = now,
                parentID = parentID,
            )
            storedComments += comment
            writes += "addComment"
            comment.copy()
        }
    }

    override suspend fun attachments(workItemID: WorkItemID): List<Attachment> = locked {
        storedAttachments.filter { it.workItemID == workItemID }
            .sortedWith(compareBy({ it.createdAt }, { it.id.stringValue }))
            .map { it.copy() }
    }

    override suspend fun addAttachment(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        now: Instant,
    ): Attachment {
        var projectID: ProjectID? = null
        return writing({ ProjectChange(ProjectChange.Kind.WorkItems, projectID!!) }) {
            failIfSet()
            val existing = storedItems.first { it.id == workItemID }
            projectID = existing.projectID
            val attachment = Attachment(
                id = randomAttachmentID(),
                workItemID = workItemID,
                // Set, because the real store sets it: the file API scopes every request by
                // project, and an upload with no project is one the use case refuses. A fake
                // that left it null would make every upload look already-synced.
                projectID = existing.projectID,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
                blobPath = "",
                createdAt = now,
            )
            storedAttachments += attachment
            blobBytes[attachment.id] = bytes
            writes += "addAttachment"
            attachment.copy()
        }
    }

    override suspend fun deleteAttachment(id: AttachmentID) {
        failIfSet()
        // The not-found case is the port's documented no-op, handled before the write
        // rather than inside it: `writing` evaluates its change lambda after the block,
        // and a row that was never there has no project to name.
        var pending: ProjectChange? = null
        lock.withLock {
            val index = storedAttachments.indexOfFirst { it.id == id }
            if (index < 0) return
            val owner = storedAttachments[index].workItemID
            storedAttachments.removeAt(index)
            blobBytes.remove(id)
            val projectID = storedItems.firstOrNull { it.id == owner }?.projectID
            writes += "deleteAttachment"
            pending = ProjectChange(ProjectChange.Kind.WorkItems, projectID)
        }
        pending?.let { publish(it) }
    }

    override suspend fun attachment(id: AttachmentID): Attachment? = locked {
        storedAttachments.firstOrNull { it.id == id }?.copy()
    }

    override suspend fun attachmentBytes(id: AttachmentID): ByteArray? = locked { blobBytes[id] }

    override suspend fun cacheAttachmentBytes(id: AttachmentID, bytes: ByteArray): Attachment? {
        var cached: Attachment? = null
        var pending: ProjectChange? = null
        lock.withLock {
            val index = storedAttachments.indexOfFirst { it.id == id }
            if (index < 0) return null
            val row = storedAttachments[index]
            blobBytes[id] = bytes
            val updated = row.copy(
                blobPath = "cache/${id.stringValue}",
                sizeBytes = bytes.size.toLong(),
            )
            storedAttachments[index] = updated
            writes += "cacheAttachmentBytes"
            cached = updated.copy()
            pending = ProjectChange(
                ProjectChange.Kind.WorkItems,
                storedItems.firstOrNull { it.id == row.workItemID }?.projectID,
            )
        }
        pending?.let { publish(it) }
        return cached
    }

    override suspend fun markAttachmentUploaded(id: AttachmentID, blobRef: String, uploadedAt: Instant) {
        var pending: ProjectChange? = null
        lock.withLock {
            val index = storedAttachments.indexOfFirst { it.id == id }
            if (index < 0) return
            val row = storedAttachments[index]
            storedAttachments[index] = row.copy(blobRef = blobRef, uploadedAt = uploadedAt)
            writes += "markAttachmentUploaded"
            pending = ProjectChange(
                ProjectChange.Kind.WorkItems,
                storedItems.firstOrNull { it.id == row.workItemID }?.projectID,
            )
        }
        pending?.let { publish(it) }
    }

    override fun changes(): Flow<ProjectChange> = broadcaster.stream()

    // A fake holds no outbox, so no change can ever be dead in it: there is nothing to
    // revive. Returning zero keeps the retry affordance a no-op rather than faking a sync
    // that cannot happen — the same answer `ViewPreviewData.PreviewRepository` gives, and
    // the reason this double does not grow an outbox to satisfy the port.
    override suspend fun retryStuckChanges(now: Instant): Int = 0

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

    /** Adds an attachment directly, without recording a write or a change. */
    suspend fun seed(attachment: Attachment) = locked { storedAttachments += attachment }

    /**
     * Puts bytes behind an attachment id, as if the file were already on this device.
     *
     * Needed because [seed] writes metadata only, and the upload path deliberately reads the
     * bytes through their own call: a row with no bytes is `NoLocalBytes`, not a failure.
     */
    suspend fun seedBytes(id: AttachmentID, bytes: ByteArray) = locked { blobBytes[id] = bytes }

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

/**
 * The identity presentation fixtures assign ownership and assignment to.
 *
 * Fixed rather than [com.tasktracker.core.randomUserID]: a scenario that asserts "my tasks
 * shows the card" has to compare the same id twice, and a fixture that moved between runs
 * would make the second half an assertion about nothing.
 */
val testUserID = UserID("99999999-9999-4999-8999-999999999999")

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
    assigneeID: UserID? = null,
    dueDate: Instant? = null,
    rank: Int = 0,
    resolvedAt: Instant? = null,
    id: WorkItemID = randomWorkItemID(),
    storyPoints: Int? = null,
    sprintID: SprintID? = null,
    blockedByIDs: List<WorkItemID> = emptyList(),
    recurrence: Recurrence? = null,
): WorkItem = WorkItem(
    id = id,
    projectID = projectID,
    key = WorkItemKey(key),
    title = title,
    details = details,
    status = status,
    priority = priority,
    assigneeID = assigneeID,
    dueDate = dueDate,
    storyPoints = storyPoints,
    sprintID = sprintID,
    blockedByIDs = blockedByIDs,
    recurrence = recurrence,
    rank = rank,
    createdAt = FIXED,
    updatedAt = FIXED,
    resolvedAt = resolvedAt,
)

/**
 * A sprint fixture, on a fixed clock.
 *
 * The defaults are a **two-week window in March 2026**, chosen so that the window crosses
 * no month boundary and `LocalDate(2026, MARCH, 2)` is what [sprintLocalDate] has to
 * produce in UTC. A sprint whose end lands in a different month would make a date
 * conversion test pass or fail on which month it happened to pick, which is not what any
 * of these tests are about.
 */
@Suppress("LongParameterList")
fun testSprint(
    projectID: ProjectID,
    name: String = "Sprint 1",
    goalStoryPoints: Int = 10,
    goal: String? = null,
    startDate: Instant = Instant.parse("2026-03-02T00:00:00Z"),
    endDate: Instant = Instant.parse("2026-03-16T00:00:00Z"),
    ownerID: UserID? = testUserID,
    isFinished: Boolean = false,
    isArchived: Boolean = false,
    finishedAt: Instant? = null,
    id: SprintID = randomSprintID(),
): Sprint = Sprint(
    id = id,
    projectID = projectID,
    name = name,
    goalStoryPoints = goalStoryPoints,
    goal = goal,
    startDate = startDate,
    endDate = endDate,
    ownerID = ownerID,        isFinished = isFinished,
            isArchived = isArchived,
            // Kept in step with `isFinished` by hand, because a fixture that broke the entity's
            // own invariant would make every test using it assert against a state the store cannot
            // hold — which is a green test and a useless one.
            finishedAt = finishedAt ?: if (isFinished) Instant.parse("2026-03-16T12:00:00Z") else null,
    createdAt = FIXED,
    updatedAt = FIXED,
)

private val FIXED: Instant = Instant.fromEpochSeconds(1_760_000_000)
