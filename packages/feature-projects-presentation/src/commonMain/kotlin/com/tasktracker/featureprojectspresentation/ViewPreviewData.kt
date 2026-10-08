package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
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
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Fixtures for previews, and a [ProjectRepositoryPort] small enough to stand in for a store.
 *
 * ## Why this lives in `commonMain`
 *
 * The same reason `AuthPreviewData` does: the KMP convention gives this module no `debug`
 * source set, and a fixture in `commonTest` is not reachable from a `commonMain` preview.
 * It is also what lets a Compose UI test - which runs in `commonTest` - reuse the same
 * shapes the previews show, so a screen can be written against the fixtures before its real
 * model exists.
 *
 * ## Why this is not the test fake
 *
 * `FakeProjectRepository` in `commonTest` records calls, can fail a chosen one, and asserts
 * nothing about ordering. This one does the opposite: it is *read-mostly*, cheap, and
 * deterministic, because a preview should be able to ask for a board with one card in one
 * column without arranging a scenario. A preview that has to be set up like a test stops
 * being used for layout.
 *
 * The one behaviour it does keep from the real port is the one that took an afternoon to
 * find: **every read returns a copy.** `BoardProjection` and its siblings are `data class`
 * values over *mutable* entities, and a model holds one in `mutableStateOf`, whose
 * `structuralEqualityPolicy` discards a write whose new value compares equal to the old.
 * With a shared instance, mutating a status changes the entity *inside* the projection
 * already on screen, the new projection compares equal, the write is dropped, and the screen
 * silently keeps stale data. `RoomProjectRepository` maps each row to a fresh entity per
 * read; this does the same, and the test fake does too.
 */
object ViewPreviewData {

    /**
     * A fixed instant, so a preview does not change with the wall clock.
     *
     * A preview of the archive grouped by month that shows a different month each time it is
     * opened cannot be compared against a screenshot, which is most of what a preview is
     * for.
     */
    val instant: Instant = Instant.parse("2026-03-15T12:00:00Z")

    private val zone: TimeZone = TimeZone.UTC

    /**
     * The zone a preview's sprint dates are read in.
     *
     * Public because a preview that builds a [SprintListRow] or a [SprintDetail] by hand has
     * to convert the same instants the models convert, and reading them in a different zone
     * would make a preview disagree with the screen it is previewing. `sprintLocalDate`
     * takes the zone as a parameter for exactly this reason.
     */
    val previewZone: TimeZone = zone

    /** The board a first launch starts with, matching the store's own seed. */
    val seededProject: Project = Project(
        id = projectID(1),
        keyPrefix = "TT",
        name = "Task Tracker",
        details = "Everything that has to happen before this app is finished.",
        nextItemNumber = 5,
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

    /**
     * One task per board column, plus one for the backlog and one for the archive.
     *
     * Every column gets a card on purpose. A preview with two cards in one column and empty
     * columns everywhere else cannot show a column header's real height, and the widest
     * thing a board draws is a card in the column with the longest status name.
     */
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
                assignee = "Amir",
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
                assignee = "Amir",
                // Past due against `instant`, and *not* finished with - which is the only
                // combination the overdue chip is for, and the reason the fixture needs one.
                dueDate = Instant.parse("2026-03-10T00:00:00Z"),
            ),
        )
        add(
            item(
                "TT-9",
                seededProject.id,
                "Publish the release notes",
                status = WorkItemStatus.Todo,
                // After `instant`, so the "not late yet" half of the overdue rule has a
                // fixture of its own rather than relying on a task with no date at all.
                dueDate = Instant.parse("2026-04-01T00:00:00Z"),
            ),
        )
        add(item("GE-1", secondProject.id, "Rename the colour tokens", status = WorkItemStatus.Todo))
    }

    // MARK: Sprints

    /**
     * Two sprints: one open and mid-flight, one finished and over its goal.
     *
     * Preview fixtures only — **not** the store's first-launch sample data, which 23.8
     * forbids extending while `SeedingTest` has three documented failures: adding rows to
     * the fixture those tests assert against makes the baseline worse and their failures
     * harder to read.
     *
     * Both readings are here on purpose. The open sprint is short of its goal and holds an
     * unestimated task, so a preview shows a bar below the end *and* `TTStoryPoints`'
     * absent reading. The finished one is over its goal, so a preview shows the overshoot
     * that [SprintListRow.progressFraction] deliberately does not clamp away, and a
     * summary reporting a hit.
     */
    val openSprint: Sprint = Sprint(
        id = sprintID(1),
        projectID = seededProject.id,
        name = "Sprint 7",
        goalStoryPoints = 12,
        startDate = Instant.parse("2026-03-02T00:00:00Z"),
        endDate = Instant.parse("2026-03-16T00:00:00Z"),
        owner = "Amir",
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
        owner = "Amir",
        isFinished = true,
        // A date, because `finishedAt != null` exactly when `isFinished` is what
        // `Sprint.reconcileFinishedAt` exists to hold and a preview that broke it would be
        // showing a state the store cannot hold.
        finishedAt = Instant.parse("2026-02-20T16:30:00Z"),
        createdAt = instant,
        updatedAt = instant,
    )

    val sprints: List<Sprint> = listOf(openSprint, finishedSprint)

    /**
     * The task list, with sprint membership and estimates on it.
     *
     * Built by copying [workItems] rather than by changing it: the board previews assert on
     * [workItems] having items in every column and nothing in a sprint, and a fixture that
     * moved a card into a sprint would take the board's empty-column with it. Every item
     * below the seeded project that is not already terminal is pulled into [openSprint],
     * which is what makes the open sprint's bar read as partly filled.
     */
    fun sprintWorkItems(
        sprint: Sprint = openSprint,
        points: Map<String, Int?> = mapOf(
            "TT-2" to 5,
            "TT-3" to 3,
            "TT-4" to 8,
            // Unestimated on purpose: the reading `TTStoryPoints` exists to distinguish
            // from a real zero, and a preview that never shows it cannot show that.
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

    /** A store holding the fixtures, for a preview that needs a real model behind it. */
    fun repository(
        projects: List<Project> = listOf(seededProject, secondProject),
        items: List<WorkItem> = workItems,
        sprints: List<Sprint> = emptyList(),
    ): ProjectRepositoryPort = PreviewRepository(projects, items, sprints)

    // MARK: Projections

    /** A board with content in every column. */
    fun boardProjection(project: Project = seededProject): BoardProjection = BoardProjection(
        project = project,
        items = workItems.filter { it.projectID == project.id && it.status.appearsOnBoard },
    )

    /** A board with nothing in it, so an empty state can be previewed. */
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

    /** Rows for the projects list, newest activity first. */
    fun summaries(): List<ProjectSummary> = listOf(
        ProjectSummary(seededProject, 7),
        ProjectSummary(secondProject, 1),
    )

    /** Everything available in a project switcher. */
    fun pickable(): List<PickableProject> = listOf(seededProject, secondProject).map {
        PickableProject(id = it.id, name = it.name, keyPrefix = it.keyPrefix)
    }

    // MARK: Builders

    @Suppress("LongParameterList")
    private fun item(
        key: String,
        projectID: ProjectID,
        title: String,
        details: String = "",
        status: WorkItemStatus = WorkItemStatus.Backlog,
        priority: Priority = Priority.Medium,
        assignee: String? = null,
        dueDate: Instant? = null,
        rank: Int = 0,
    ): WorkItem {
        // The status changes the key prefix issued, so a preview never shows TT-0.
        val number = key.substringAfter('-').toIntOrNull() ?: 1
        return WorkItem(
            id = workItemID(key),
            projectID = projectID,
            key = WorkItemKey(key),
            title = title,
            details = details,
            status = status,
            priority = priority,
            assignee = assignee,
            dueDate = dueDate,
            rank = rank,
            createdAt = instant,
            updatedAt = instant,
            resolvedAt = if (status.isTerminal) instant else null,
        ).also { check(number > 0) }
    }
}

/** A read-mostly [ProjectRepositoryPort] over a fixed set of fixtures. */
private class PreviewRepository(
    projects: List<Project>,
    items: List<WorkItem>,
    sprints: List<Sprint> = emptyList(),
) : ProjectRepositoryPort {

    private val storedProjects = projects.map { it.copy() }.toMutableList()
    private val storedItems = items.map { it.copy() }.toMutableList()
    // Seeded only when a preview asks for sprints; the default stays empty so the previews
    // that predate this feature are unaffected. It is real storage rather than an
    // `emptyList()` return because a preview that is handed a sprint — or that creates
    // one — should behave, and `setWorkItemSprint` has to be able to write here for the
    // same reason.
    private val storedSprints = sprints.map { it.copy() }.toMutableList()
    private val storedStatusChanges = mutableListOf<StatusChange>()
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

    override suspend fun seedSampleDataIfNeeded(): Boolean = false

    // MARK: Sprints

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
        owner: String?,
        now: Instant,
    ): Sprint {
        // The overlap rule holds here too, and for the same reason the store keeps it: a
        // preview that could put two unfinished sprints on the same day would show a state
        // the app cannot produce, hiding the very thing the Sprints tab exists to reveal.
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
            owner = owner,
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
        owner: Patch<String?>,
        now: Instant,
    ): Sprint = storedSprints.first { it.id == id }.also {
        if (name != null) it.name = name
        if (goalStoryPoints != null) it.goalStoryPoints = goalStoryPoints
        if (goal is Patch.Of) it.goal = goal.value
        if (startDate != null) it.startDate = startDate
        if (endDate != null) it.endDate = endDate
        if (owner is Patch.Of) it.owner = owner.value
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
        storedItems.removeAll { it.projectID == id }
        broadcaster.send(ProjectChange(ProjectChange.Kind.Projects, id))
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
            assignee = assignee,
            dueDate = dueDate,
            storyPoints = storyPoints,
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
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
    ): WorkItem = storedItems.first { it.id == id }.also {
        if (title != null) it.title = title
        if (details != null) it.details = details
        if (priority != null) it.priority = priority
        if (assignee is Patch.Of) it.assignee = assignee.value
        if (dueDate is Patch.Of) it.dueDate = dueDate.value
        if (storyPoints is Patch.Of) it.storyPoints = storyPoints.value
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
        broadcaster.send(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID))
    }

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

/** Distinct from every other id above it, so a sprint can never collide with a task. */
private fun sprintID(n: Int): SprintID = SprintID("00000000-0000-4000-8002-" + n.toString().padStart(12, '0'))
