package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The fake, and the parts of the port contract only it is asked to keep.
 *
 * The use-case suites *assume* the fake behaves like a store, so those assumptions are
 * worth asserting directly — a fake that quietly disagreed with
 * `:packages:feature-projects-data` would leave every use-case test green while the real
 * repository did the opposite thing. The three properties that would do the most damage if
 * they drifted are here: history that is newest-first, a `resolvedAt` invariant that holds
 * on create, and an invalidation channel that is typed rather than "something, somewhere".
 */
class FakeProjectRepositoryTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private suspend fun FakeProjectRepository.addItem(
        projectID: ProjectID,
        status: WorkItemStatus = WorkItemStatus.Todo,
        title: String = "Land it",
    ) = createWorkItem(
        projectID = projectID,
        title = title,
        details = "",
        status = status,
        priority = Priority.Medium,
        assignee = null,
        dueDate = null,
        now = now,
    )

    // MARK: Reads

    @Test
    fun `the default listing leaves archived projects out`() = runTest {
        val repository = FakeProjectRepository()
        val live = repository.seedProject(name = "Apollo", keyPrefix = "APOLLO")
        val archived = repository.seedProject(name = "Gemini", keyPrefix = "GEMINI")
        repository.setArchived(id = archived.id, isArchived = true, now = now)

        assertEquals(listOf(live.id), repository.projects().map { it.id })
        assertEquals(
            setOf(live.id, archived.id),
            repository.projects(includeArchived = true).map { it.id }.toSet(),
        )
    }

    @Test
    fun `an empty store knows it is empty and a seeded one does not`() = runTest {
        val repository = FakeProjectRepository()
        assertTrue(repository.isEmpty())

        repository.seedProject()
        assertFalse(repository.isEmpty())
    }

    @Test
    fun `counts are grouped by status and confined to one project`() = runTest {
        val repository = FakeProjectRepository()
        val apollo = repository.seedProject(keyPrefix = "APOLLO")
        val gemini = repository.seedProject(name = "Gemini", keyPrefix = "GEMINI")
        repository.seedWorkItem(projectID = apollo.id, status = WorkItemStatus.Todo)
        repository.seedWorkItem(projectID = apollo.id, status = WorkItemStatus.Todo)
        repository.seedWorkItem(projectID = apollo.id, status = WorkItemStatus.Done)
        repository.seedWorkItem(projectID = gemini.id, status = WorkItemStatus.Backlog)

        assertEquals(
            mapOf(WorkItemStatus.Todo to 2, WorkItemStatus.Done to 1),
            repository.counts(apollo.id),
        )
        assertEquals(mapOf(WorkItemStatus.Backlog to 1), repository.counts(gemini.id))
    }

    @Test
    fun `reading a row that is not there gives null rather than an error`() = runTest {
        val repository = FakeProjectRepository()

        // Absent is a normal answer on the read path: a screen opened against a project
        // deleted on another device has to render something, not throw.
        assertNull(repository.project(randomProjectID()))
        assertNull(repository.workItem(randomWorkItemID()))
        assertTrue(repository.statusChanges(randomWorkItemID()).isEmpty())
    }

    @Test
    fun `deleting a project takes its own items with it and leaves other projects alone`() = runTest {
        val repository = FakeProjectRepository()
        val apollo = repository.seedProject(name = "Apollo", keyPrefix = "APOLLO")
        val gemini = repository.seedProject(name = "Gemini", keyPrefix = "GEMINI")
        val doomed = repository.addItem(projectID = apollo.id, title = "Doomed")
        val kept = repository.addItem(projectID = gemini.id, title = "Kept")
        repository.moveWorkItem(
            id = doomed.id,
            to = WorkItemStatus.InProgress,
            rank = null,
            now = now,
        )
        assertEquals(2, repository.statusChanges(doomed.id).size)

        repository.deleteProject(apollo.id)

        // The cascade is the whole reason `RemoveProject` refuses to delete a project with
        // open work: it takes the items, and in the real store it takes a queued delete for
        // every one of them as well.
        assertNull(repository.workItem(doomed.id))
        assertTrue(repository.statusChanges(doomed.id).isEmpty())
        assertEquals(kept.id, repository.workItem(kept.id)?.id)
        assertEquals(1, repository.workItems(gemini.id).size)
    }

    // MARK: Status history

    @Test
    fun `history comes back newest first`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.addItem(projectID = project.id, status = WorkItemStatus.Backlog)
        repository.moveWorkItem(id = item.id, to = WorkItemStatus.InProgress, rank = null, now = now)
        repository.moveWorkItem(
            id = item.id,
            to = WorkItemStatus.Done,
            rank = null,
            now = now + 60.seconds,
        )

        val history = repository.statusChanges(item.id)

        // Newest first, because that is the order the editor renders it in. Asking every
        // caller to remember to reverse a chronological list is how a history ends up
        // reading backwards in one screen and forwards in another.
        assertEquals(
            listOf(WorkItemStatus.Done, WorkItemStatus.InProgress, WorkItemStatus.Backlog),
            history.map { it.to },
        )
        assertEquals(now + 60.seconds, history.first().changedAt)
    }

    @Test
    fun `two changes in the same instant are still newest first`() = runTest {
        // A card created and triaged inside one clock tick is an ordinary thing to do, and
        // a sort on the timestamp alone hands those two back in insertion order — the
        // oldest order. The tiebreak is what makes "newest first" a total order rather than
        // a property of whichever sort the implementation happened to reach for.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.addItem(projectID = project.id, status = WorkItemStatus.Backlog)

        repository.moveWorkItem(id = item.id, to = WorkItemStatus.InProgress, rank = null, now = now)
        repository.moveWorkItem(id = item.id, to = WorkItemStatus.Todo, rank = null, now = now)

        assertEquals(
            listOf(WorkItemStatus.Todo, WorkItemStatus.InProgress, WorkItemStatus.Backlog),
            repository.statusChanges(item.id).map { it.to },
        )
    }

    @Test
    fun `a creation is recorded as a history row with no previous status`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.addItem(projectID = project.id, status = WorkItemStatus.Todo)

        val history = repository.statusChanges(item.id)

        // "Created here" and "moved here from the backlog" are different facts, and a
        // history that cannot tell them apart cannot explain a new card.
        assertEquals(1, history.size)
        assertNull(history.single().from)
        assertTrue(history.single().isCreation)
    }

    @Test
    fun `a move that changes no status records no history row`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.addItem(projectID = project.id, status = WorkItemStatus.Todo)

        repository.moveWorkItem(
            id = item.id,
            to = WorkItemStatus.Todo,
            rank = null,
            now = now + 60.seconds,
        )

        // A record of `todo -> todo` would make the history claim something that did not
        // happen, and `SaveWorkItem` is careful never to send one.
        assertEquals(1, repository.statusChanges(item.id).size)
    }

    @Test
    fun `a history row never announces anything of its own`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.addItem(projectID = project.id, status = WorkItemStatus.Todo)
        val afterCreate = repository.publishedChanges().size

        repository.moveWorkItem(
            id = item.id,
            to = WorkItemStatus.InProgress,
            rank = null,
            now = now + 60.seconds,
        )

        // The move wrote a row and a history row together; only the move announces, because
        // the row is written in the same transaction and the screens that care have
        // already been invalidated by the time it exists.
        assertEquals(2, repository.statusChanges(item.id).size)
        assertEquals(1, repository.publishedChanges().size - afterCreate)
    }

    @Test
    fun `a created terminal item is dated so the archive can file it`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        val item = repository.addItem(projectID = project.id, status = WorkItemStatus.Closed)

        // `resolvedAt` non-null exactly when the status is terminal, on the create path as
        // well as the move path. A fake that skipped it would let a domain test assert a
        // contract the store does not honour, and the archive is the one place the
        // difference shows up.
        assertEquals(now, item.resolvedAt)
        assertEquals(now, repository.workItem(item.id)?.resolvedAt)
    }

    // MARK: Seeding

    @Test
    fun `seeding is idempotent and does nothing once a project exists`() = runTest {
        val repository = FakeProjectRepository()

        assertTrue(repository.seedSampleDataIfNeeded())
        assertEquals(1, repository.projectCount())

        assertFalse(repository.seedSampleDataIfNeeded())
        assertEquals(1, repository.projectCount())
    }

    @Test
    fun `a real project is never joined by the sample board`() = runTest {
        val repository = FakeProjectRepository()
        repository.seedProject(name = "Apollo")

        repository.seedSampleDataIfNeeded()

        assertEquals(
            listOf("Apollo"),
            repository.projects(includeArchived = true).map { it.name },
        )
    }

    // MARK: Invalidation

    @Test
    fun `every write announces a typed change scoped to what moved`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)

        repository.updateProject(id = project.id, name = "Artemis", details = null, now = now)
        repository.moveWorkItem(id = item.id, to = WorkItemStatus.Done, rank = null, now = now)

        // A unit payload would say only "something, somewhere", which forces every view
        // model to re-read on every write in the feature and makes the contract untestable:
        // no test could assert that a project-only change left the board alone.
        assertEquals(
            listOf(
                ProjectChange(ProjectChange.Kind.Projects, project.id),
                ProjectChange(ProjectChange.Kind.WorkItems, project.id),
            ),
            repository.publishedChanges(),
        )
    }

    @Test
    fun `a change published before anyone listened is not replayed`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.updateProject(id = project.id, name = "Artemis", details = null, now = now)
        val seen = mutableListOf<ProjectChange>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes().collect { seen += it }
        }
        repository.updateProject(id = project.id, name = "Gemini", details = null, now = now)
        advanceUntilIdle()

        // The screen's first act is to read the current state, so a replay would only ever
        // tell it about something it has already accounted for.
        assertEquals(listOf(ProjectChange(ProjectChange.Kind.Projects, project.id)), seen)
    }

    @Test
    fun `every subscriber receives every change`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val board = mutableListOf<ProjectChange>()
        val list = mutableListOf<ProjectChange>()

        // Unconfined so both collectors are already subscribed by the time the write lands,
        // rather than the test asserting on launch order. One shared cursor would hand the
        // event to whichever collector got there first and leave the other with a board
        // that never learns about an edit.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes().collect { board += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.changes().collect { list += it }
        }

        repository.updateProject(id = project.id, name = "Artemis", details = null, now = now)
        advanceUntilIdle()

        val expected = listOf(ProjectChange(ProjectChange.Kind.Projects, project.id))
        assertEquals(expected, board)
        assertEquals(expected, list)
    }

    // MARK: Missing rows

    @Test
    fun `writing to a row that is not there is refused rather than indexing past the end`() = runTest {
        val repository = FakeProjectRepository()
        val absent = randomWorkItemID()

        // `indexOfFirst` returns `-1` for a miss, which is neither `null` nor out of
        // bounds — so the obvious `?: throw` compiles, reads as though it works, and does
        // nothing at all, and the write lands on `storedItems[-1]`. A test for "editing a
        // deleted item is refused" then fails with an IndexOutOfBounds instead of the
        // AppError the port is supposed to raise.
        assertFailsWith<AppError> {
            repository.updateWorkItem(
                id = absent,
                title = "Renamed",
                details = null,
                priority = null,
                assignee = Patch.Unmentioned,
                dueDate = Patch.Unmentioned,
                storyPoints = Patch.Unmentioned,
                now = now,
            )
        }
        assertFailsWith<AppError> { repository.deleteWorkItem(absent) }
        assertFailsWith<AppError> { repository.deleteProject(randomProjectID()) }
        assertFailsWith<AppError> { repository.setArchived(randomProjectID(), true, now) }
    }
}
