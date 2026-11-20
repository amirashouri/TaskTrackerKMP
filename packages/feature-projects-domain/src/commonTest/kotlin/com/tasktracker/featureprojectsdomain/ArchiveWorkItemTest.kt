package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The archive decision, tested against a fake port.
 *
 * The rules worth pinning: archiving is a move to [WorkItemStatus.Closed] from anything that is
 * not already closed — including `Done`, which is a lane the archive must be able to clear — and
 * it must refuse to fire on a task that is already `Closed`, where there is nothing left to file
 * and a second move would only claim a change that did not happen.
 */
class ArchiveWorkItemTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private suspend fun makeItem(
        repository: FakeProjectRepository,
        status: WorkItemStatus,
    ): WorkItem {
        val project = repository.createProject(
            name = "Apollo",
            keyPrefix = "APOLLO",
            details = "",
            now = now,
        )
        return repository.createWorkItem(
            projectID = project.id,
            title = "Write the spec",
            details = "",
            status = status,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = now,
        )
    }

    @Test
    fun `archiving a live task moves it to Closed`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.InProgress)
        val archive = ArchiveWorkItem(repository, TestDateProvider(now))

        val archived = archive(item.id)

        assertEquals(WorkItemStatus.Closed, archived.status)
        assertEquals(true, archived.status.isTerminal)
        assertEquals(
            now,
            archived.resolvedAt,
            "closing dates the resolution or the archive cannot file it",
        )
    }

    @Test
    fun `archiving records the history row like any other move`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.Todo)
        val later = now + 60.seconds
        val archive = ArchiveWorkItem(repository, TestDateProvider(later))

        archive(item.id)

        // Newest first, so the archive move leads over the creation row.
        val history = repository.statusChanges(item.id)
        assertEquals(2, history.size)
        assertEquals(WorkItemStatus.Todo, history.first().from)
        assertEquals(WorkItemStatus.Closed, history.first().to)
        assertEquals(later, history.first().changedAt)
    }

    @Test
    fun `archiving a done task closes it and keeps its resolution date`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.Done)
        val later = now + 60.seconds
        val archive = ArchiveWorkItem(repository, TestDateProvider(later))

        val archived = archive(item.id)

        assertEquals(WorkItemStatus.Closed, archived.status)
        assertEquals(
            now,
            archived.resolvedAt,
            "closing must not rewrite the date the task was actually finished",
        )
    }

    @Test
    fun `closing a done task records the move in history`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.Done)
        val later = now + 60.seconds
        ArchiveWorkItem(repository, TestDateProvider(later))(item.id)

        val history = repository.statusChanges(item.id)
        assertEquals(WorkItemStatus.Done, history.first().from)
        assertEquals(WorkItemStatus.Closed, history.first().to)
        assertEquals(later, history.first().changedAt)
    }

    @Test
    fun `an already-closed task cannot be re-archived`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.Closed)
        val archive = ArchiveWorkItem(repository, TestDateProvider(now + 60.seconds))

        assertFailsWith<AppError> { archive(item.id) }

        assertEquals(now, assertNotNull(repository.workItem(item.id)).resolvedAt)
    }

    @Test
    fun `the refusal names the item and where it is already filed`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.Closed)
        val archive = ArchiveWorkItem(repository, TestDateProvider(now + 60.seconds))

        val error = assertFailsWith<AppError> { archive(item.id) }

        // The key rather than the id, because the key is what the user is looking at. The
        // status is the wire value rather than "Closed": see the note in `MoveWorkItem` on why
        // this module cannot produce a display title.
        assertEquals("FAKE-1 is already filed away in closed.", error.message)
    }

    @Test
    fun `archiving a task that no longer exists is refused before any write`() = runTest {
        val repository = FakeProjectRepository()
        val archive = ArchiveWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> { archive(randomWorkItemID()) }

        assertEquals(
            0,
            repository.moveCallCount(),
            "the existence check has to happen before the write not after",
        )
    }

    @Test
    fun `unarchiving goes through the ordinary move which clears the resolution date`() = runTest {
        val repository = FakeProjectRepository()
        val item = makeItem(repository, WorkItemStatus.Todo)
        ArchiveWorkItem(repository, TestDateProvider(now))(item.id)

        val reopened = repository.moveWorkItem(
            id = item.id,
            to = WorkItemStatus.InProgress,
            rank = null,
            now = now + 60.seconds,
        )

        assertEquals(WorkItemStatus.InProgress, reopened.status)
        assertEquals(
            null,
            reopened.resolvedAt,
            "a live task must not stay filed in the archive",
        )
    }
}
