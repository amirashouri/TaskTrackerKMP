package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.CommentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TagID
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.core.randomCommentID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomWorkItemID
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
import com.tasktracker.featureprojectsdomain.BacklogProjection
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.featureteamsdomain.User
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Deterministic preview fixtures and an in-memory [ProjectRepositoryPort] implementation.
 * All repository reads return defensive copies to ensure Compose [mutableStateOf] triggers updates.
 */
object ViewPreviewData {

    val instant: Instant = Instant.parse("2026-03-15T12:00:00Z")

    val previewUserID: UserID = UserID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    val previewGraceID: UserID = UserID("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    val previewUnnamedUserID: UserID = UserID("cccccccc-cccc-4ccc-8ccc-cccccccccccc")

    val previewMemberIDs: List<UserID> = listOf(previewUserID, previewGraceID, previewUnnamedUserID)

    val previewUserNames: Map<UserID, String> = mapOf(
        previewUserID to "Amir",
        previewGraceID to "Grace",
    )

    val previewSearchMembers: suspend (String) -> List<User> = { query ->
        val normalised = query.trim().lowercase()
        previewUserNames
            .filter { (id, name) ->
                normalised.isEmpty() ||
                        name.lowercase().contains(normalised) ||
                        id.stringValue.contains(normalised)
            }
            .map { (id, name) -> User(id = id, email = "${name.lowercase()}@example.com", displayName = name) }
            .sortedBy { it.preferredGreeting.lowercase() }
    }

    private val zone: TimeZone = TimeZone.UTC

    val previewZone: TimeZone = zone

    val seededProject: Project = Project(
        id = projectID(1),
        keyPrefix = "TT",
        name = "Task Tracker",
        details = "Everything that has to happen before this app is finished.",
        nextItemNumber = 5,
        memberIDs = previewMemberIDs,
        createdAt = instant,
        updatedAt = instant,
    )

    val secondProject: Project = Project(
        id = projectID(2),
        keyPrefix = "GE",
        name = "Gemini",
        details = "A second project, so a switcher has something to switch between.",
        nextItemNumber = 2,
        createdAt = instant,
        updatedAt = instant,
    )

    val archivedProject: Project = Project(
        id = projectID(3),
        keyPrefix = "VG",
        name = "Vega",
        details = "Filed away, with history behind it.",
        isArchived = true,
        nextItemNumber = 1,
        createdAt = instant,
        updatedAt = instant,
    )

    val workItems: List<WorkItem> = buildList {
        add(item("TT-1", seededProject.id, "Fix the offline queue", status = WorkItemStatus.Backlog))
        add(item("TT-2", seededProject.id, "Write the migration", status = WorkItemStatus.Todo))
        add(
            item(
                "TT-3",
                seededProject.id,
                "Crash on launch when the vault is empty",
                details = "Reported on a cold install with no keychain entry.",
                status = WorkItemStatus.InProgress,
                priority = Priority.Critical,
                assigneeID = previewUserID,
            ),
        )
        add(
            item(
                "TT-4",
                seededProject.id,
                "Reduce the glass effect cost",
                details = "The blur is repainting on every frame of a drag.",
                status = WorkItemStatus.InReview,
                priority = Priority.High,
            ),
        )
        add(
            item(
                "TT-5",
                seededProject.id,
                "Delete the unused migration",
                status = WorkItemStatus.Done,
                priority = Priority.Lowest,
            ),
        )
        add(
            item(
                "TT-6",
                seededProject.id,
                "Archive the old sync experiments",
                details = "Superseded by the queue in core.",
                status = WorkItemStatus.Closed,
                priority = Priority.Low,
            ),
        )
        add(
            item(
                "TT-7",
                seededProject.id,
                "Ship the 1.0 board",
                status = WorkItemStatus.Done,
                dueDate = Instant.parse("2026-02-28T00:00:00Z"),
            ),
        )
        add(
            item(
                "TT-8",
                seededProject.id,
                "Answer the flaky sync test",
                details = "Fails once in forty runs, always on the iOS simulator.",
                status = WorkItemStatus.InProgress,
                priority = Priority.High,
                assigneeID = previewUserID,
                dueDate = Instant.parse("2026-03-10T00:00:00Z"),
            ),
        )
        add(
            item(
                "TT-9",
                seededProject.id,
                "Publish the release notes",
                status = WorkItemStatus.Todo,
                dueDate = Instant.parse("2026-04-01T00:00:00Z"),
            ),
        )
        add(item("GE-1", secondProject.id, "Rename the colour tokens", status = WorkItemStatus.Todo))
    }

    val previewComments: List<Comment> = listOf(
        Comment(
            id = CommentID("cccccccc-cccc-4ccc-8ccc-cccccccc0001"),
            workItemID = workItems.first().id,
            authorID = previewGraceID,
            body = "The queue drops the second retry when the tab loses focus — seen twice on iOS.",
            createdAt = instant,
        ),
        Comment(
            id = CommentID("cccccccc-cccc-4ccc-8ccc-cccccccc0002"),
            workItemID = workItems.first().id,
            authorID = previewUserID,
            body = "Reproduced. It is the cancellation path, not the retry counter.",
            createdAt = instant + 3.minutes,
            parentID = CommentID("cccccccc-cccc-4ccc-8ccc-cccccccc0001"),
        ),
        Comment(
            id = CommentID("cccccccc-cccc-4ccc-8ccc-cccccccc0003"),
            workItemID = workItems.first().id,
            authorID = previewUserID,
            body = "Filed a follow-up; this card can stay in backlog until the fix lands.",
            createdAt = instant + 7.minutes,
        ),
        Comment(
            id = CommentID("cccccccc-cccc-4ccc-8ccc-cccccccc0004"),
            workItemID = workItems.first().id,
            authorID = previewGraceID,
            body = "Agreed — and the fix should land before the release notes do.",
            createdAt = instant + 9.minutes,
            parentID = CommentID("cccccccc-cccc-4ccc-8ccc-cccccccc0002"),
        ),
    )

    val previewAttachments: List<Attachment> = listOf(
        Attachment(
            id = AttachmentID("dddddddd-dddd-4ddd-8ddd-dddddddd0001"),
            workItemID = workItems.first().id,
            fileName = "crash-report.txt",
            mimeType = "text/plain",
            sizeBytes = 2_412L,
            blobPath = "dddddddd-dddd-4ddd-8ddd-dddddddd0001.blob",
            createdAt = instant,
        ),
        Attachment(
            id = AttachmentID("dddddddd-dddd-4ddd-8ddd-dddddddd0002"),
            workItemID = workItems.first().id,
            fileName = "screenshot-locked.png",
            mimeType = "image/png",
            sizeBytes = 184_320L,
            blobPath = "dddddddd-dddd-4ddd-8ddd-dddddddd0002.blob",
            createdAt = instant + 4.minutes,
        ),
        Attachment(
            id = AttachmentID("dddddddd-dddd-4ddd-8ddd-dddddddd0003"),
            workItemID = workItems.first().id,
            fileName = "sync-qa-report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 7_340_032L,
            blobPath = "dddddddd-dddd-4ddd-8ddd-dddddddd0003.blob",
            createdAt = instant + 6.minutes,
        ),
    )

    val previewCommentAuthorNames: Map<UserID, String> = previewUserNames

    val openSprint: Sprint = Sprint(
        id = sprintID(1),
        projectID = seededProject.id,
        name = "Sprint 7",
        goalStoryPoints = 12,
        startDate = Instant.parse("2026-03-02T00:00:00Z"),
        endDate = Instant.parse("2026-03-16T00:00:00Z"),
        ownerID = previewUserID,
        isFinished = false,
        finishedAt = null,
        createdAt = instant,
        updatedAt = instant,
    )

    val finishedSprint: Sprint = Sprint(
        id = sprintID(2),
        projectID = seededProject.id,
        name = "Sprint 6",
        goalStoryPoints = 8,
        startDate = Instant.parse("2026-02-09T00:00:00Z"),
        endDate = Instant.parse("2026-02-20T00:00:00Z"),
        ownerID = previewUserID,
        isFinished = true,
        finishedAt = Instant.parse("2026-02-20T16:30:00Z"),
        createdAt = instant,
        updatedAt = instant,
    )

    val sprints: List<Sprint> = listOf(openSprint, finishedSprint)

    fun sprintWorkItems(
        sprint: Sprint = openSprint,
        points: Map<String, Int?> = mapOf(
            "TT-2" to 5,
            "TT-3" to 3,
            "TT-4" to 8,
            "TT-8" to null,
            "TT-9" to 2,
        ),
    ): List<WorkItem> = workItems
        .filter { it.projectID == sprint.projectID && !it.status.isTerminal }
        .map { item ->
            item.copy(
                sprintID = sprint.id,
                storyPoints = points[item.key.toString()],
            )
        }

    fun repository(
        projects: List<Project> = listOf(seededProject, secondProject),
        items: List<WorkItem> = workItems,
        sprints: List<Sprint> = emptyList(),
    ): ProjectRepositoryPort = PreviewRepository(projects, items, sprints)

    fun boardProjection(project: Project = seededProject): BoardProjection = BoardProjection(
        project = project,
        items = workItems.filter { it.projectID == project.id && it.status.appearsOnBoard },
    )

    fun emptyBoardProjection(project: Project = seededProject): BoardProjection =
        BoardProjection(project = project, items = emptyList())

    /** A backlog with two untriaged tasks and one already filed away. */
    fun backlogProjection(project: Project = seededProject): BacklogProjection = BacklogProjection(
        project = project,
        items = workItems.filter { it.projectID == project.id },
    )

    /** The same work, split into the backlog tab's two sections. */
    fun backlogSectionedProjection(project: Project = seededProject): BacklogSectionedProjection =
        BacklogSectionedProjection(
            project = project,
            items = workItems.filter { it.projectID == project.id },
        )

    fun summaries(): List<ProjectSummary> = listOf(
        ProjectSummary(seededProject, 7),
        ProjectSummary(secondProject, 1),
    )

    fun pickable(): List<PickableProject> = listOf(seededProject, secondProject).map {
        PickableProject(id = it.id, name = it.name, keyPrefix = it.keyPrefix)
    }

    @Suppress("LongParameterList")
    private fun item(
        key: String,
        projectID: ProjectID,
        title: String,
        details: String = "",
        status: WorkItemStatus = WorkItemStatus.Backlog,
        priority: Priority = Priority.Medium,
        assigneeID: UserID? = null,
        dueDate: Instant? = null,
        rank: Int = 0,
    ): WorkItem {
        val number = key.substringAfter('-').toIntOrNull() ?: 1
        return WorkItem(
            id = workItemID(key),
            projectID = projectID,
            key = WorkItemKey(key),
            title = title,
            details = details,
            status = status,
            priority = priority,
            assigneeID = assigneeID,
            dueDate = dueDate,
            rank = rank,
            createdAt = instant,
            updatedAt = instant,
            resolvedAt = if (status.isTerminal) instant else null,
        ).also { check(number > 0) }
    }
}

private class PreviewRepository(
    projects: List<Project>,
    items: List<WorkItem>,
    sprints: List<Sprint> = emptyList(),
) : ProjectRepositoryPort {

    private val storedProjects = projects.map { it.copy() }.toMutableList()
    private val storedItems = items.map { it.copy() }.toMutableList()
    private val storedSprints = sprints.map { it.copy() }.toMutableList()
    private val storedStatusChanges = mutableListOf<StatusChange>()
    private val storedComments = mutableListOf<Comment>()
    private val broadcaster = Broadcaster<ProjectChange>()

    override suspend fun projects(includeArchived: Boolean): List<Project> =
        storedProjects.filter { includeArchived || !it.isArchived }.map { it.copy() }

    override suspend fun project(id: ProjectID): Project? =
        storedProjects.firstOrNull { it.id == id }?.copy()

    override suspend fun workItems(projectID: ProjectID): List<WorkItem> =
        storedItems.filter { it.projectID == projectID }.map { it.copy() }

    override suspend fun workItem(id: WorkItemID): WorkItem? =
        storedItems.firstOrNull { it.id == id }?.copy()

    override suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int> =
        storedItems.filter { it.projectID == projectID }
            .groupingBy { it.status }
            .eachCount()

    override suspend fun isEmpty(): Boolean = storedProjects.isEmpty()

    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> =
        storedStatusChanges.filter { it.workItemID == workItemID }

    override suspend fun comments(workItemID: WorkItemID): List<Comment> =
        storedComments.filter { it.workItemID == workItemID }
            .sortedWith(compareBy({ it.createdAt }, { it.id.stringValue }))

    override suspend fun addComment(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID?,
        now: Instant,
    ): Comment {
        val comment = Comment(
            id = randomCommentID(),
            workItemID = workItemID,
            authorID = authorID,
            body = body,
            createdAt = now,
            parentID = parentID,
        )
        storedComments += comment
        return comment
    }


    // MARK: Attachments

    private val storedAttachments = mutableListOf<Attachment>()

    override suspend fun attachments(workItemID: WorkItemID): List<Attachment> =
        storedAttachments.filter { it.workItemID == workItemID }.map { it.copy() }

    override suspend fun addAttachment(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        now: Instant,
    ): Attachment {
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
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems))
        return attachment
    }

    override suspend fun deleteAttachment(id: AttachmentID) {
        val index = storedAttachments.indexOfFirst { it.id == id } ?: return
        storedAttachments.removeAt(index)
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems))
    }

    override suspend fun seedSampleDataIfNeeded(): Boolean = false

    override suspend fun sprints(projectID: ProjectID): List<Sprint> = storedSprints
        .filter { it.projectID == projectID }
        .sortedWith(compareBy({ it.isFinished }, { it.startDate }))

    override suspend fun sprint(id: SprintID): Sprint? =
        storedSprints.firstOrNull { it.id == id }?.copy()

    override suspend fun sprintWorkItems(id: SprintID): List<WorkItem> = storedItems
        .filter { it.sprintID == id }
        .sortedBy { it.rank }

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
        com.tasktracker.domain.sprintBlockingOverlap(
            startDate,
            endDate,
            storedSprints.filter { it.projectID == projectID },
        )?.let { throw com.tasktracker.core.AppError.Validation("'${it.name}' overlaps this window.") }
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
        broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, projectID))
        return sprint.copy()
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
    ): Sprint = storedSprints.first { it.id == id }.also {
        if (name != null) it.name = name
        if (goalStoryPoints != null) it.goalStoryPoints = goalStoryPoints
        if (goal is Patch.Of) it.goal = goal.value
        if (startDate != null) it.startDate = startDate
        if (endDate != null) it.endDate = endDate
        if (ownerID is Patch.Of) it.ownerID = ownerID.value
        it.updatedAt = now
    }.also {
        broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, it.projectID))
    }.copy()

    override suspend fun finishSprint(id: SprintID, now: Instant): Sprint =
        storedSprints.first { it.id == id }.also {
            it.isFinished = true
            it.updatedAt = now
            it.reconcileFinishedAt(now)
        }.also {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, it.projectID))
        }.copy()

    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint =
        storedSprints.first { it.id == id }.also {
            it.isFinished = false
            it.updatedAt = now
            it.reconcileFinishedAt(now)
        }.also {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, it.projectID))
        }.copy()

    override suspend fun archiveSprint(id: SprintID, now: Instant): Sprint =
        storedSprints.first { it.id == id }.also {
            it.isArchived = true
            it.updatedAt = now
        }.also {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, it.projectID))
        }.copy()

    override suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint =
        storedSprints.first { it.id == id }.also {
            it.isArchived = false
            it.updatedAt = now
        }.also {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, it.projectID))
        }.copy()

    override suspend fun setSprintLocked(id: SprintID, isLocked: Boolean, now: Instant): Sprint =
        storedSprints.first { it.id == id }.also {
            it.isLocked = isLocked
            it.updatedAt = now
        }.also {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, it.projectID))
        }.copy()

    override suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem> = workItemIDs.map { id ->
        storedItems.first { it.id == id }
    }.also { moved ->
        val target = (sprintID as? Patch.Of)?.value
        val projectID = moved.firstOrNull()?.projectID
        moved.forEach {
            it.sprintID = target
            it.updatedAt = now
        }
        if (projectID != null) {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, projectID))
            broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, projectID))
        }
    }.map { it.copy() }

    override suspend fun deleteSprint(id: SprintID, now: Instant) {
        val sprint = storedSprints.first { it.id == id }
        storedSprints.removeAll { it.id == id }
        storedItems.filter { it.sprintID == id }.forEach {
            it.sprintID = null
            it.updatedAt = now
        }
        broadcaster.send(ProjectChange(ProjectChange.Kind.Sprints, sprint.projectID))
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, sprint.projectID))
    }

    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID>,
    ): Project = Project(
        keyPrefix = keyPrefix,
        name = name,
        details = details,
        memberIDs = memberIDs,
        createdAt = now,
        updatedAt = now,
    ).also {
        storedProjects += it
        broadcaster.send(ProjectChange(ProjectChange.Kind.Projects, it.id))
    }.copy()

    override suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>?,
    ): Project = storedProjects.first { it.id == id }.also {
        if (name != null) it.name = name
        if (details != null) it.details = details
        if (memberIDs != null) it.memberIDs = memberIDs
        it.updatedAt = now
    }.also {
        broadcaster.send(ProjectChange(ProjectChange.Kind.Projects, id))
    }.copy()

    override suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project =
        storedProjects.first { it.id == id }.also {
            it.isArchived = isArchived
            it.updatedAt = now
        }.also {
            broadcaster.send(ProjectChange(ProjectChange.Kind.Projects, id))
        }.copy()

    override suspend fun deleteProject(id: ProjectID) {
        storedProjects.removeAll { it.id == id }
        val itemIDs = storedItems.filter { it.projectID == id }.map { it.id }
        storedItems.removeAll { it.projectID == id }
        storedComments.removeAll { it.workItemID in itemIDs }
        broadcaster.send(ProjectChange(ProjectChange.Kind.Projects, id))
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
        tagIDs: List<TagID>,
    ): WorkItem {
        val project = storedProjects.first { it.id == projectID }
        val item = WorkItem(
            id = randomWorkItemID(),
            projectID = projectID,
            key = ProjectRules.workItemKey(project.keyPrefix, project.nextItemNumber),
            title = title,
            details = details,
            status = status,
            priority = priority,
            assigneeID = assigneeID,
            dueDate = dueDate,
            recurrence = recurrence,
            storyPoints = storyPoints,
            blockedByIDs = blockedByIDs,
            tagIDs = tagIDs,
            sprintID = sprintID,
            createdAt = now,
            updatedAt = now,
        )
        project.nextItemNumber++
        storedItems += item
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, projectID))
        return item.copy()
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
        tagIDs: Patch<List<TagID>>,
    ): WorkItem = storedItems.first { it.id == id }.also {
        if (title != null) it.title = title
        if (details != null) it.details = details
        if (priority != null) it.priority = priority
        if (assigneeID is Patch.Of) it.assigneeID = assigneeID.value
        if (dueDate is Patch.Of) it.dueDate = dueDate.value
        if (storyPoints is Patch.Of) it.storyPoints = storyPoints.value
        if (blockedByIDs is Patch.Of) it.blockedByIDs = blockedByIDs.value
        if (recurrence is Patch.Of) it.recurrence = recurrence.value
        if (tagIDs is Patch.Of) it.tagIDs = tagIDs.value
        it.updatedAt = now
    }.also {
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, it.projectID))
    }.copy()

    override suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem = storedItems.first { it.id == id }.also { item ->
        val from = item.status
        item.status = to
        item.updatedAt = now
        if (rank != null) item.rank = rank
        if (to.isTerminal && item.resolvedAt == null) item.resolvedAt = now
        storedStatusChanges += StatusChange(
            workItemID = item.id,
            projectID = item.projectID,
            from = from,
            to = to,
            changedAt = now,
            changedBy = null,
        )
    }.also {
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, it.projectID))
    }.copy()

    override suspend fun deleteWorkItem(id: WorkItemID) {
        val item = storedItems.first { it.id == id }
        storedItems.removeAll { it.id == id }
        storedComments.removeAll { it.workItemID == id }
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID))
    }

    override suspend fun retryStuckChanges(now: Instant): Int = 0

    override fun changes(): Flow<ProjectChange> = broadcaster.stream()
}

/** A clock pinned to the preview instant, so a preview does not move with the wall clock. */
fun previewDateProvider(): TestDateProvider = TestDateProvider(ViewPreviewData.instant)

/** Provides a fixture store to previews and UI tests. */
@Composable
fun previewProjectEnvironment(content: @Composable () -> Unit) {
    val repository = ViewPreviewData.repository()
    val dateProvider = previewDateProvider()
    ProvideProjectEnvironment(
        environment = ProjectEnvironment(
            repository = repository,
            dateProvider = dateProvider,
        ),
        content = content,
    )
}

/**
 * A fixed id, so a preview is the same shape every time it is opened.
 *
 * `ProjectID` and `WorkItemID` are value classes over `Uuid`, so a readable string like
 * `"apollo"` is not an option. `String.format` is not either - it does not exist in common
 * code - hence `padStart`.
 */
private fun deterministicID(n: Int): String =
    "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

private fun projectID(n: Int): ProjectID = ProjectID(deterministicID(n))

private fun workItemID(key: String): WorkItemID =
    WorkItemID("00000000-0000-4000-8001-" + key.substringAfter('-').padStart(12, '0'))

private fun sprintID(n: Int): SprintID = SprintID("00000000-0000-4000-8002-" + n.toString().padStart(12, '0'))