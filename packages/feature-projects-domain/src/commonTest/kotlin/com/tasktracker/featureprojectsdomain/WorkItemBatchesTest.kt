package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.ProjectID
import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The batch actions, tested against the fake port.
 *
 * A batch is a loop over the per-item commands, so the rules worth pinning are the ones a naive
 * loop gets wrong: it must keep going after an item fails, it must not land different items in
 * different lanes when the catalog is read once, and an item another client already finished is
 * skipped rather than failing the set.
 */
class WorkItemBatchesTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private suspend fun projectWithReviewLane(
        repository: FakeProjectRepository,
    ): Pair<FakeProjectRepository, Project> {
        val project = repository.createProject(
            name = "Apollo",
            keyPrefix = "APOLLO",
            details = "",
            now = now,
        )
        val catalogued = repository.updateProject(
            id = project.id,
            name = null,
            details = null,
            now = now,
            statuses = listOf(
                StatusDefinition(id = "todo-1", name = "To Do", category = WorkItemStatus.Todo, order = 0),
                StatusDefinition(id = "review-1", name = "In Review", category = WorkItemStatus.InReview, order = 1),
            ),
        )
        return repository to catalogued
    }

    private suspend fun addItem(
        repository: FakeProjectRepository,
        projectID: ProjectID,
        status: WorkItemStatus = WorkItemStatus.Backlog,
    ): WorkItem = repository.createWorkItem(
        projectID = projectID,
        title = "Write the spec",
        details = "",
        status = status,
        priority = Priority.Medium,
        assigneeID = null,
        dueDate = null,
        now = now,
    )

    // MARK: Put on board

    @Test
    fun `boarding a set moves every item into the one chosen lane`() = runTest {
        val (repository, project) = projectWithReviewLane(FakeProjectRepository())
        val first = addItem(repository, project.id)
        val second = addItem(repository, project.id)
        val board = PutWorkItemsOnBoard(repository, TestDateProvider(now + 60.seconds))

        val outcome = board(project.id, listOf(first.id, second.id), statusKey = "review-1")

        assertEquals(listOf(first.id, second.id), outcome.applied)
        assertEquals(0, outcome.skippedCount)
        assertEquals(0, outcome.failureCount)
        for (id in listOf(first.id, second.id)) {
            val moved = assertNotNull(repository.workItem(id))
            assertEquals(WorkItemStatus.InReview, moved.status, "the lane's category, not Todo")
            assertEquals("review-1", moved.statusKey, "the lane id has to travel with the move")
        }
    }

    @Test
    fun `boarding a set with no catalog lands every item in Todo without a key`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val first = addItem(repository, project.id)
        val second = addItem(repository, project.id)
        val board = PutWorkItemsOnBoard(repository, TestDateProvider(now))

        val outcome = board(project.id, listOf(first.id, second.id))

        assertEquals(2, outcome.appliedCount)
        for (id in listOf(first.id, second.id)) {
            val moved = assertNotNull(repository.workItem(id))
            assertEquals(WorkItemStatus.Todo, moved.status)
            assertNull(moved.statusKey)
        }
    }

    @Test
    fun `one missing item fails alone and the rest still board`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val live = addItem(repository, project.id)
        val gone = addItem(repository, project.id)
        repository.deleteWorkItem(gone.id)
        val board = PutWorkItemsOnBoard(repository, TestDateProvider(now))

        val outcome = board(project.id, listOf(gone.id, live.id))

        assertEquals(listOf(live.id), outcome.applied)
        assertEquals(listOf(gone.id), outcome.retryable)
        assertEquals(WorkItemStatus.Todo, assertNotNull(repository.workItem(live.id)).status)
    }

    @Test
    fun `boarding a set writes one history row per item`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = addItem(repository, project.id)
        val later = now + 60.seconds
        val board = PutWorkItemsOnBoard(repository, TestDateProvider(later))

        board(project.id, listOf(item.id))

        // Newest first: the boarding move leads over the creation row.
        val history = repository.statusChanges(item.id)
        assertEquals(2, history.size)
        assertEquals(WorkItemStatus.Backlog, history.first().from)
        assertEquals(WorkItemStatus.Todo, history.first().to)
        assertEquals(later, history.first().changedAt)
    }

    @Test
    fun `a duplicate id is acted on once`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = addItem(repository, project.id)
        val board = PutWorkItemsOnBoard(repository, TestDateProvider(now))

        val outcome = board(project.id, listOf(item.id, item.id))

        assertEquals(listOf(item.id), outcome.applied)
        assertEquals(1, repository.moveCallCount())
    }

    // MARK: Archive

    @Test
    fun `archiving a set closes every item with its own resolution date`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val first = addItem(repository, project.id, WorkItemStatus.Todo)
        val second = addItem(repository, project.id, WorkItemStatus.InProgress)
        val later = now + 60.seconds
        val archive = ArchiveWorkItems(repository, TestDateProvider(later))

        val outcome = archive(listOf(first.id, second.id))

        assertEquals(listOf(first.id, second.id), outcome.applied)
        for (id in listOf(first.id, second.id)) {
            val closed = assertNotNull(repository.workItem(id))
            assertEquals(WorkItemStatus.Closed, closed.status)
            assertTrue(closed.status.isTerminal)
            assertEquals(later, closed.resolvedAt)
        }
    }

    @Test
    fun `a done item is closed rather than skipped`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val done = addItem(repository, project.id, WorkItemStatus.Done)
        val finishedResolution = assertNotNull(repository.workItem(done.id)).resolvedAt
        val archive = ArchiveWorkItems(repository, TestDateProvider(now + 60.seconds))

        val outcome = archive(listOf(done.id))

        assertEquals(listOf(done.id), outcome.applied)
        assertEquals(0, outcome.skippedCount)
        val closed = assertNotNull(repository.workItem(done.id))
        assertEquals(WorkItemStatus.Closed, closed.status)
        assertEquals(finishedResolution, closed.resolvedAt, "the done date is the resolution")
    }

    @Test
    fun `an item filed away elsewhere is skipped and keeps its resolution date`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val finished = addItem(repository, project.id, WorkItemStatus.Closed)
        val live = addItem(repository, project.id, WorkItemStatus.Todo)
        val finishedResolution = assertNotNull(repository.workItem(finished.id)).resolvedAt
        val archive = ArchiveWorkItems(repository, TestDateProvider(now + 60.seconds))

        val outcome = archive(listOf(finished.id, live.id))

        assertEquals(listOf(finished.id), outcome.skipped)
        assertEquals(listOf(live.id), outcome.applied)
        assertEquals(0, outcome.failureCount, "an already-finished task is not a failure to retry")
        assertEquals(
            finishedResolution,
            assertNotNull(repository.workItem(finished.id)).resolvedAt,
            "the skipped item must not have its resolution rewritten",
        )
    }

    @Test
    fun `one missing item fails alone and the rest still archive`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val live = addItem(repository, project.id, WorkItemStatus.Todo)
        val gone = addItem(repository, project.id, WorkItemStatus.Todo)
        repository.deleteWorkItem(gone.id)
        val archive = ArchiveWorkItems(repository, TestDateProvider(now))

        val outcome = archive(listOf(live.id, gone.id))

        assertEquals(listOf(live.id), outcome.applied)
        assertEquals(listOf(gone.id), outcome.retryable)
        assertEquals(WorkItemStatus.Closed, assertNotNull(repository.workItem(live.id)).status)
    }

    @Test
    fun `an empty batch attempts nothing`() = runTest {
        val repository = FakeProjectRepository()
        val archive = ArchiveWorkItems(repository, TestDateProvider(now))

        assertEquals(0, archive(emptyList()).attemptedCount)
        assertEquals(0, repository.moveCallCount())
    }
}
