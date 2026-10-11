package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The board: the grouping the view draws, and the move that puts an item in a column.
 *
 * The grouping itself belongs to `:packages:feature-projects-domain` and is tested there.
 * What is tested here is the model's own half - that it reloads on the right changes, and
 * that a move reports honestly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectBoardModelTest {

    @Test
    fun `the board shows the project and its items grouped into columns`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog))
        repository.seed(testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.InProgress))
        repository.seed(testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.Done))

        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals("Apollo", model.projection.project?.name)
        // Two, not three: the Backlog item is not on the board. `appearsOnBoard` excludes
        // Backlog because it is untriaged - it belongs in the backlog, and showing it here
        // as well is the duplication PLAN 8.14 records. So the board is the *other* half of
        // the split, and this asserts the boundary from the board's side.
        assertEquals(2, model.projection.itemCount)
        assertEquals(
            listOf(WorkItemStatus.Todo, WorkItemStatus.InProgress, WorkItemStatus.InReview, WorkItemStatus.Done),
            model.projection.columns.map { it.status },
        )
    }

    /** An empty column is still a column - the view draws a drop target for it. */
    @Test
    fun `empty columns are present`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.projection.columns.isNotEmpty())
        assertTrue(model.projection.columns.all { it.count == 0 })
        assertEquals(0, model.projection.itemCount)
    }

    @Test
    fun `the board ignores another project's work item changes`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        val other = randomProjectID()
        assertFalse(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, other)))
        assertTrue(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, project.id)))
    }

    /**
     * A project-wide change still matters.
     *
     * Renaming the project is a `Projects` change, and the board's header shows that name.
     * Filtering on the id here would leave the header showing a name the user just changed
     * until they navigated away and back.
     */
    @Test
    fun `a project change refreshes the board header`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo")
        repository.seed(project)
        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()
        assertEquals("Apollo", model.projection.project?.name)

        repository.createProject("Other", "OT", "", repositoryClock())
        repository.updateProject(project.id, "Apollo 2", null, repositoryClock())
        advanceUntilIdle()

        assertEquals("Apollo 2", model.projection.project?.name)
    }

    // MARK: Moving

    /**
     * A move reaches the store, and the card ends up in the column it was dropped into.
     *
     * The write and the screen are the two halves of a drag, and a move that updated only
     * the board's own state would look correct right up until the app was closed.
     */
    @Test
    fun `moving a card reaches the store and puts it in the new column`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo)
        repository.seed(item)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.move(item.id, WorkItemStatus.InProgress))
        advanceUntilIdle()

        assertTrue(repository.writes.contains("moveWorkItem:InProgress"))
        assertNull(model.movingItemID)
        assertEquals(
            WorkItemStatus.InProgress,
            model.projection.items.single { it.id == item.id }.status,
        )
    }

    @Test
    fun `a failed move reports the failure and leaves the item where it was`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo)
        repository.seed(item)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        repository.failure = AppError.Offline
        assertFalse(model.move(item.id, WorkItemStatus.Done))
        advanceUntilIdle()

        assertEquals(AppError.Offline.message, model.errorMessage)
        // The board still holds the card, and still shows the card where it was, so a
        // failed drag does not leave the user's mental model out of step with the screen.
        assertEquals(WorkItemStatus.Todo, model.projection.items.single { it.id == item.id }.status)
    }

    // MARK: Filing away

    /**
     * A board card's "File Away" has a verb behind it.
     *
     * The screen has always drawn the button — Phase 12 wired it to a per-item action slot
     * and tagged it — while this model had no `fileAway` at all. No model test could catch
     * that, because a model cannot be asked whether a *button* has a caller; only wiring the
     * board into a host surfaced it, which is the argument for doing that.
     */
    @Test
    fun `filing a card away closes it and clears the in-flight id`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo)
        repository.seed(item)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(
            model.fileAway(item.id),
            "expected the archive to succeed, but the model reported: ${model.errorMessage}",
        )
        advanceUntilIdle()

        // `ArchiveWorkItem` reaches the store as a move to `Closed` — it validates first and
        // then delegates to the same write, which is the point of having one verb for it.
        assertTrue(
            repository.writes.contains("moveWorkItem:Closed"),
            "writes were ${repository.writes}",
        )
        assertNull(model.movingItemID)
        // The card leaves the *board*, not the project: `items` is every card the project
        // holds, and the board draws filed-away ones separately. So the assertion is on the
        // columns, which is what "no longer on the board" actually means here.
        assertEquals(
            WorkItemStatus.Closed,
            model.projection.items.single { it.id == item.id }.status,
        )
        assertTrue(
            model.projection.columns.none { column -> column.items.any { it.id == item.id } },
            "a filed-away card still has a column: ${model.projection.columns}",
        )
    }

    /**
     * A failed archive leaves the card exactly where it was, and does not leave the flag up.
     *
     * The flag half matters more than it looks: it is what the card's buttons check, so a
     * flag left set by a throw would kill that card's verbs for the rest of the session.
     */
    @Test
    fun `a failed file away reports the failure and leaves the card on the board`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo)
        repository.seed(item)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        repository.failure = AppError.Offline
        assertFalse(model.fileAway(item.id))
        advanceUntilIdle()

        assertEquals(AppError.Offline.message, model.errorMessage)
        assertNull(model.movingItemID)
        assertEquals(WorkItemStatus.Todo, model.projection.items.single { it.id == item.id }.status)
    }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo)
        repository.seed(item)
        val model = ProjectBoardModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        repository.failure = AppError.Offline
        model.move(item.id, WorkItemStatus.Done)
        assertNotNull(model.errorMessage)

        model.dismissError()
        assertNull(model.errorMessage)
    }

    @Test
    fun `toggling showOnlyMyTasks filters items by assignee presence`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", assignee = "Amir"))
        repository.seed(testWorkItem(project.id, key = "AP-2", assignee = null))

        val model = ProjectBoardModel(project.id, repository, TestDateProvider(), currentUserName = "Amir")
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(2, model.projection.itemCount)

        model.toggleShowOnlyMyTasks()
        assertTrue(model.showOnlyMyTasks)
        assertEquals(1, model.projection.itemCount)
        assertEquals("Amir", model.projection.items.single().assignee)
    }

    @Test
    fun `the board filters work items by active sprint when an active sprint exists`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val now = TestDateProvider().now()
        val sprint = repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 10,
            startDate = now - 1.days,
            endDate = now + 1.days,
            owner = null,
            now = now,
        )
        val itemInSprint = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo, sprintID = sprint.id)
        val itemOutSprint = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Todo, sprintID = null)
        repository.seed(itemInSprint)
        repository.seed(itemOutSprint)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider(now))
        model.observe(eagerScope())
        advanceUntilIdle()

        assertNotNull(model.activeSprint)
        assertEquals(sprint.id, model.activeSprint?.id)
        assertEquals(1, model.projection.itemCount)
        assertEquals("AP-1", model.projection.items.single().key.rawValue)
    }

    @Test
    fun `adds are locked while a sprint is in progress`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val now = TestDateProvider().now()
        repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 10,
            startDate = now - 1.days,
            endDate = now + 1.days,
            owner = null,
            now = now,
        )

        val model = ProjectBoardModel(project.id, repository, TestDateProvider(now))
        model.observe(eagerScope())
        advanceUntilIdle()

        assertNotNull(model.activeSprint, "an in-window sprint is the board's active sprint")
        assertTrue(model.addsLocked, "an InProgress sprint locks lane-add on the board")
    }

    @Test
    fun `adds stay open for a scheduled sprint and for kanban`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val now = TestDateProvider().now()
        // Scheduled: the window has not opened, so the board is not owned by the sprint yet.
        repository.createSprint(
            projectID = project.id,
            name = "Sprint future",
            goalStoryPoints = 10,
            startDate = now + 5.days,
            endDate = now + 12.days,
            owner = null,
            now = now,
        )

        val model = ProjectBoardModel(project.id, repository, TestDateProvider(now))
        model.observe(eagerScope())
        advanceUntilIdle()

        assertNotNull(model.activeSprint)
        assertFalse(model.addsLocked, "a scheduled sprint must keep lane-add open")
    }

    @Test
    fun `adds stay open when no sprint exists`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val now = TestDateProvider().now()

        val model = ProjectBoardModel(project.id, repository, TestDateProvider(now))
        model.observe(eagerScope())
        advanceUntilIdle()

        assertNull(model.activeSprint)
        assertFalse(model.addsLocked, "kanban is always open to new tasks")
    }

    @Test
    fun `the board falls back to all project work items when no active sprint exists`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val now = TestDateProvider().now()
        val item1 = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo, sprintID = null)
        val item2 = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Todo, sprintID = null)
        repository.seed(item1)
        repository.seed(item2)

        val model = ProjectBoardModel(project.id, repository, TestDateProvider(now))
        model.observe(eagerScope())
        advanceUntilIdle()

        assertNull(model.activeSprint)
        assertEquals(2, model.projection.itemCount)
    }

    /**
     * The invariant 24.3 exists to pin: a lane-add on a sprintless board must survive the
     * round trip. The board host seeds the editor with `initialSprintID = activeSprint?.id`
     * (NavigationHost) or the same value handed to `onAddTask` (ProjectsHost) — both null
     * here — and the lane's status. If either is dropped on the way into `SaveWorkItem`, the
     * task saves into the store but never shows up: an identical bug to the sprint-board one
     * §19 documents, just on the far side of the sprint filter. So the test composes the two
     * models exactly as the hosts do, and asserts the item keeps both create-time facts and
     * lands on the board in the column belonging to the lane it was added from.
     */
    @Test
    fun `a task added from a sprintless board keeps its lane status and shows up on the board`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val now = TestDateProvider().now()

        val board = ProjectBoardModel(project.id, repository, TestDateProvider(now))
        board.observe(eagerScope())
        advanceUntilIdle()
        assertNull(board.activeSprint)

        val editor = WorkItemEditorModel(
            projectID = project.id,
            existing = null,
            repository = repository,
            dateProvider = TestDateProvider(now),
            initialStatus = WorkItemStatus.Todo,
            initialSprintID = board.activeSprint?.id,
        )
        editor.onTitleChanged("Sprintless board task")
        assertNotNull(editor.save())
        advanceUntilIdle()

        val stored = repository.workItems(project.id).single()
        assertEquals(
            WorkItemStatus.Todo,
            stored.status,
            "a lane-add must keep the lane status it was created with",
        )
        assertNull(
            stored.sprintID,
            "with no active sprint the add must join no sprint",
        )

        val column = board.projection.columns.single { it.status == stored.status }
        assertTrue(
            column.items.any { it.id == stored.id },
            "the new task must appear on the board in the lane it was added from; columns were ${board.projection.columns}",
        )
    }
}

private fun repositoryClock(): kotlin.time.Instant = kotlin.time.Instant.fromEpochSeconds(1_760_000_000)
