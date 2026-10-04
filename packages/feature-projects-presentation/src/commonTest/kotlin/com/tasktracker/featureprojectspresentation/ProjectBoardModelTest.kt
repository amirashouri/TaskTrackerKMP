package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
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
}

private fun repositoryClock(): kotlin.time.Instant = kotlin.time.Instant.fromEpochSeconds(1_760_000_000)
