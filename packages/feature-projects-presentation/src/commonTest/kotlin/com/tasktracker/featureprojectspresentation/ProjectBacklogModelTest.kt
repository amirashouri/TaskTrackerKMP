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
     * A done task can be filed away, and it was already in the archive.
     *
     * Filing a `Done` task now closes it — `Done` is a lane and `Closed` is the archive — so the
     * press changes the store instead of being refused. The projection assertions hold both
     * halves: the task is history before and after, and one task is still counted once.
     */
    @Test
    fun `a done task can be filed away and is already in the archive`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.fileAway(item.id))
        advanceUntilIdle()

        assertEquals(WorkItemStatus.Closed, repository.workItem(item.id)!!.status)
        assertNull(model.errorMessage)
        assertEquals(listOf("AP-1"), model.projection.archived.map { it.key.toString() })
        assertEquals(1, model.projection.totalItemCount)
    }

    // MARK: Select mode and batches

    @Test
    fun `starting selection keeps the mode even with nothing ticked yet`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.startSelecting()

        assertTrue(model.isSelecting)
        assertTrue(model.selection.isEmpty)
    }

    @Test
    fun `a long press seeds the selection and taps toggle in order`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val first = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        val second = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Backlog)
        repository.seed(first)
        repository.seed(second)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.startSelecting(seed = second.id)
        model.toggleSelected(first.id)

        assertEquals(listOf(second.id, first.id), model.selection.ids)
        model.toggleSelected(second.id)
        assertEquals(listOf(first.id), model.selection.ids)
        assertTrue(model.isSelecting, "deselecting one row is not leaving the mode")
    }

    @Test
    fun `boarding a selection moves every ticked item into the chosen lane`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(
            statuses = listOf(
                StatusDefinition(id = "todo-1", name = "To Do", category = WorkItemStatus.Todo, order = 0),
                StatusDefinition(id = "review-1", name = "In Review", category = WorkItemStatus.InReview, order = 1),
            ),
        )
        repository.seed(project)
        val first = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        val second = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Backlog)
        val untouched = testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.Backlog)
        repository.seed(first)
        repository.seed(second)
        repository.seed(untouched)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.startSelecting(seed = first.id)
        model.toggleSelected(second.id)
        val outcome = assertNotNull(model.putSelectionOnBoard("review-1"))
        advanceUntilIdle()

        assertEquals(2, outcome.appliedCount)
        assertEquals(0, outcome.failureCount)
        assertEquals(WorkItemStatus.InReview, repository.workItem(first.id)!!.status)
        assertEquals("review-1", repository.workItem(second.id)!!.statusKey)
        assertEquals(
            WorkItemStatus.Backlog,
            repository.workItem(untouched.id)!!.status,
            "an unticked row must not move",
        )
        assertTrue(model.selection.isEmpty)
        assertFalse(model.isSelecting, "a finished batch with nothing left to retry leaves the mode")
    }

    @Test
    fun `a failed item stays ticked and reports its message while the rest move`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val live = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        val gone = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Backlog)
        repository.seed(live)
        repository.seed(gone)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()
        model.startSelecting(seed = live.id)
        model.toggleSelected(gone.id)

        // Deleted on another client between the selection and the command.
        repository.deleteWorkItem(gone.id)
        advanceUntilIdle()
        val outcome = assertNotNull(model.putSelectionOnBoard(null))
        advanceUntilIdle()

        assertEquals(1, outcome.appliedCount)
        assertEquals(listOf(gone.id), outcome.retryable)
        assertEquals(listOf(gone.id), model.selection.ids)
        assertTrue(model.isSelecting)
        assertNotNull(model.errorMessage)
        assertEquals(WorkItemStatus.Todo, repository.workItem(live.id)!!.status)
    }

    @Test
    fun `archiving a selection closes every ticked item`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val first = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        val second = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Todo)
        val untouched = testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.Backlog)
        repository.seed(first)
        repository.seed(second)
        repository.seed(untouched)
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.startSelecting(seed = first.id)
        model.toggleSelected(second.id)
        val outcome = assertNotNull(model.archiveSelection())
        advanceUntilIdle()

        assertEquals(listOf(first.id, second.id), outcome.applied)
        assertEquals(WorkItemStatus.Closed, repository.workItem(first.id)!!.status)
        assertEquals(WorkItemStatus.Closed, repository.workItem(second.id)!!.status)
        assertEquals(WorkItemStatus.Backlog, repository.workItem(untouched.id)!!.status)
        assertTrue(model.selection.isEmpty)
    }

    @Test
    fun `an item finished elsewhere is skipped without failing the batch`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val live = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        // Closed is the only terminal status Archive skips now; a Done card is closed on purpose.
        val finished = testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Closed)
        repository.seed(live)
        repository.seed(finished)
        val resolutionBefore = repository.workItem(finished.id)!!.resolvedAt
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.startSelecting(seed = finished.id)
        model.toggleSelected(live.id)
        val outcome = assertNotNull(model.archiveSelection())
        advanceUntilIdle()

        assertEquals(listOf(finished.id), outcome.skipped)
        assertEquals(listOf(live.id), outcome.applied)
        assertEquals(0, outcome.failureCount)
        assertEquals(resolutionBefore, repository.workItem(finished.id)!!.resolvedAt)
        assertTrue(model.selection.isEmpty, "a skipped row is not retryable")
    }

    @Test
    fun `a batch with nothing selected does nothing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog))
        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.startSelecting()
        assertNull(model.putSelectionOnBoard(null))
        assertNull(model.archiveSelection())

        assertTrue(repository.writes.isEmpty(), "no selection means no per-item write")
    }

    // MARK: Link to epic

    /**
     * The picker offers exactly the epics a link would be accepted into: active, and spanning
     * this project. An archived epic and an epic in another project are both refused by the rule,
     * so showing either would be an action that always fails.
     */
    @Test
    fun `link choices are active epics that span this project`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val otherProject = testProject(name = "Borealis")
        val spanning = testEpic(listOf(project.id, otherProject.id), title = "Ship it")
        val archived = testEpic(listOf(project.id), title = "Old story", isArchived = true)
        val foreign = testEpic(listOf(otherProject.id), title = "Elsewhere")
        repository.seed(project)
        repository.seed(otherProject)
        repository.seed(spanning)
        repository.seed(archived)
        repository.seed(foreign)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        model.loadLinkableEpics()

        assertEquals(listOf(spanning.id), model.linkableEpics.map { it.id })
        assertFalse(model.isLoadingEpics)
    }

    /** Linking writes the item's one epic field, so the row can hide its action on reload. */
    @Test
    fun `linking a backlog item records the epic on the item`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val epic = testEpic(listOf(project.id), title = "Ship it")
        val item = testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Backlog)
        repository.seed(project)
        repository.seed(epic)
        repository.seed(item)

        val model = ProjectBacklogModel(project.id, repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.linkToEpic(item.id, epic.id))
        advanceUntilIdle()

        assertEquals(epic.id, repository.workItem(item.id)?.epicID)
        assertEquals(epic.id, model.projection.backlog.single().epicID)
        assertNull(model.busyItemID)
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
