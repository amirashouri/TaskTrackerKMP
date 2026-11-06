package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.StatusDefinition
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
 * The backlog tab: the untriaged work, the work filed away from it, and the two ways work
 * leaves the first.
 *
 * Both actions here are one-way, so the tests are about the *refusals* as much as the
 * successes - each of them refuses on an item that has already been dealt with.
 *
 * The tab now splits into two sections - backlog and archived - but the model beneath it has
 * not changed its verbs: "put on board" still moves a backlogged task, and "file away" still
 * moves it to `Closed`. What changed is that boarding now takes the target column's
 * `StatusDefinition.id`: with no catalog it lands in `Todo` as before, and with a custom catalog
 * it lands in the named lane. The screen decides which lane (and remembers it), so the model's
 * job here is only to place the item correctly given that key. The sections are a view-layer
 * split, and the only model behaviour they add is that filing a backlogged task moves it from the
 * backlog section into the archived section of the same tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectBacklogModelTest {

    // MARK: What is in the backlog

    /**
     * Only untriaged work is listed.
     *
     * The old backlog also showed work borrowed from the board's in-flight columns, and
     * that is the bug PLAN 8.14 records: a card appeared in the backlog while the board
     * was already showing it, so triaging it did nothing visible and the user triaged
     * twice. Anything that is not [WorkItemStatus.Backlog] is somebody else's.
     */
    @Test
    fun `only untriaged work is listed`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog))
        repository.seed(testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Todo))
        repository.seed(testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.InProgress))
        repository.seed(testWorkItem(project.id, key = "AP-4", status = WorkItemStatus.Done))
        repository.seed(testWorkItem(project.id, key = "AP-5", status = WorkItemStatus.Closed))

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(listOf("AP-1"), model.projection.backlog.map { it.key.toString() })
        assertEquals(1, model.projection.backlogItemCount)
        assertFalse(model.projection.backlogIsEmpty)
    }

    @Test
    fun `an empty backlog says so`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.projection.backlogIsEmpty)
        assertTrue(model.projection.archivedIsEmpty)
        assertEquals(0, model.projection.totalItemCount)
    }

    @Test
    fun `the backlog ignores another project's changes`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, randomProjectID())))
        assertTrue(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, project.id)))
    }

    // MARK: Put on board

    @Test
    fun `putting work on board sends it to Todo and clears the in-flight id`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.putOnBoard(item.id, null))
        advanceUntilIdle()

        assertTrue(repository.writes.contains("moveWorkItem:Todo"))
        assertNull(model.busyItemID)
        // And it has left the backlog section, so this tab is not showing work the board now owns.
        assertTrue(model.projection.backlogIsEmpty)
    }

    /**
     * With a catalog, boarding to a named column lands the item in that lane, not in `Todo`.
     *
     * This is the behaviour the new "Put on board" prompt relies on: the screen picks a column,
     * hands its `StatusDefinition.id` here, and the item must move to that lane's category *with*
     * that key set — otherwise the board would file it under the wrong column or drop the key and
     * render it uncategorised.
     */
    @Test
    fun `putting work on board to a chosen column uses that column's key and category`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(
            statuses = listOf(
                StatusDefinition(id = "todo-1", name = "To Do", category = WorkItemStatus.Todo, order = 0),
                StatusDefinition(id = "review-1", name = "In Review", category = WorkItemStatus.InReview, order = 1),
            ),
        )
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.putOnBoard(item.id, "review-1"))
        advanceUntilIdle()

        val moved = repository.workItem(item.id)!!
        assertEquals(WorkItemStatus.InReview, moved.status)
        assertEquals("review-1", moved.statusKey)
        assertTrue(model.projection.backlogIsEmpty)
    }

    /**
     * A key the project no longer contains is treated as "no choice", not as a crash.
     *
     * The screen only persists keys the catalog still has, but defence in depth here means a stale
     * stored default (e.g. a column deleted elsewhere) cannot drive the move into a phantom lane —
     * it falls back to the legacy `Todo` column the same as a project with no catalog at all.
     */
    @Test
    fun `putting work on board with an unknown column key falls back to Todo`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(
            statuses = listOf(
                StatusDefinition(id = "todo-1", name = "To Do", category = WorkItemStatus.Todo, order = 0),
            ),
        )
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.putOnBoard(item.id, "does-not-exist"))
        advanceUntilIdle()

        val moved = repository.workItem(item.id)!!
        assertEquals(WorkItemStatus.Todo, moved.status)
        assertNull(moved.statusKey)
    }

    @Test
    fun `a failed put on board reports the failure and leaves the item listed`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        repository.failure = AppError.Offline
        assertFalse(model.putOnBoard(item.id, null))
        advanceUntilIdle()

        assertEquals(AppError.Offline.message, model.errorMessage)
        assertEquals(1, model.projection.backlogItemCount)
        assertNull(model.busyItemID)
    }

    // MARK: File away

    @Test
    fun `filing away archives the item and it leaves the backlog`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.fileAway(item.id))
        advanceUntilIdle()

        assertTrue(repository.writes.any { it == "moveWorkItem:Closed" })
        assertTrue(model.projection.backlogIsEmpty)
    }

    /**
     * A project with nothing left in it archives to the same place a board with history
     * does. This is the round trip: file away, then read the archive.
     */
    @Test
    fun `filed-away work is visible in the archive`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)

        val backlog = ProjectBacklogModel(project.id, repository, TestDateProvider())
        backlog.observe(eagerScope())
        advanceUntilIdle()
        backlog.fileAway(item.id)
        advanceUntilIdle()

        val archive = ProjectArchiveModel(
            project.id,
            repository,
            TestDateProvider(),
            timeZone = kotlinx.datetime.TimeZone.UTC,
        )
        archive.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(listOf("AP-1"), archive.projection.months.flatMap { it.items }.map { it.key.toString() })
        assertTrue(
            archive.allItemsAreFiledAway,
            "the backlog's only task is the one just filed away, so nothing is left " +
                "in the flow",
        )
    }

    /**
     * Filing away something already filed away is refused rather than silently accepted.
     *
     * `ArchiveWorkItem` treats this as a mistake in the request rather than an
     * idempotent success, so the model surfaces its wording instead of reporting a change
     * that did not happen.
     */
    @Test
    fun `filing away work that is already filed away is refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Closed)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.fileAway(item.id))
        assertNotNull(model.errorMessage)
    }

    /**
     * A done task is refused a second filing *and* is already in the archive.
     *
     * The reported bug in one place: "file away" on a `Done` task is refused because `Done` is
     * terminal, and that refusal is only honest if the archive section is showing the task. An
     * archive filtered on `Closed` refused the press and drew nothing, so the task looked lost
     * from both sections at once. Asserted on the projection the screen draws rather than on the
     * store, because that is the surface that was wrong.
     */
    @Test
    fun `a done task is refused a second filing and is already in the archive`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.fileAway(item.id))
        assertNotNull(model.errorMessage)
        assertEquals(listOf("AP-1"), model.projection.archived.map { it.key.toString() })
        // The done lane still holds it: one task, read in two sections, counted once.
        assertEquals(1, model.projection.totalItemCount)
    }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(item)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        repository.failure = AppError.Offline
        model.putOnBoard(item.id, null)
        assertNotNull(model.errorMessage)

        model.dismissError()
        assertNull(model.errorMessage)
    }
}
