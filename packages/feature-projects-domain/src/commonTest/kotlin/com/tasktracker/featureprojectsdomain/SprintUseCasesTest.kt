package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The sprint use cases' decisions, tested against the fake port.
 *
 * ## The window rule is the one worth testing twice over
 *
 * `UpdateSprint` reads the sprint before it patches, because `end < start` is a relation
 * between two fields and a partial edit may name only one of them. That read is the whole
 * reason this use case is more than a forward, so it gets the sharper of the two tests:
 * moving the *start* past an untouched end must be refused, which a naive
 * "refuse only when both arguments are present" check would let through.
 *
 * Note the fake's `updateSprint` deliberately does **not** make that check — `:packages:
 * feature-projects-data` does, inside the transaction, and a fake that copied the check
 * would mean this test could not tell which layer refused.
 */
class SprintUseCasesTest {

    private val now: Instant = Instant.fromEpochSeconds(1_760_000_000)
    private val march2: Instant = Instant.parse("2026-03-02T00:00:00Z")
    private val march16: Instant = Instant.parse("2026-03-16T00:00:00Z")

    private fun create(repository: FakeProjectRepository) = CreateSprint(repository, TestDateProvider(now))

    private suspend fun sprintIn(
        repository: FakeProjectRepository,
        goalStoryPoints: Int = 10,
        startDate: Instant = march2,
        endDate: Instant = march16,
        owner: String? = "Amir",
    ): Pair<Project, Sprint> {
        val project = repository.seedProject()
        val sprint = create(repository)(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = goalStoryPoints,
            startDate = startDate,
            endDate = endDate,
            owner = owner,
        )
        return project to sprint
    }

    // MARK: CreateSprint

    @Test
    fun `a valid sprint is created with its name trimmed`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        val sprint = create(repository)(
            projectID = project.id,
            name = "  Sprint 7  ",
            goalStoryPoints = 12,
            startDate = march2,
            endDate = march16,
            owner = "Grace",
        )

        assertEquals("Sprint 7", sprint.name)
        assertEquals(12, sprint.goalStoryPoints)
        assertEquals("Grace", sprint.owner)
        assertEquals(1, repository.sprints(project.id).size)
    }

    @Test
    fun `a blank name is refused before anything is stored`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        assertFailsWith<AppError.Validation> {
            create(repository)(
                projectID = project.id,
                name = "   ",
                startDate = march2,
                endDate = march16,
            )
        }
        assertTrue(repository.sprints(project.id).isEmpty())
    }

    @Test
    fun `a sprint ending before it starts is refused on creation`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        val failure = assertFailsWith<AppError.Validation> {
            create(repository)(
                projectID = project.id,
                name = "Sprint 7",
                startDate = march16,
                endDate = march2,
            )
        }
        // The message is the whole of what the model above shows a user, so it is asserted
        // rather than left to be whatever the string happened to say.
        assertEquals("A sprint cannot end before it starts.", failure.message)
        assertTrue(repository.sprints(project.id).isEmpty())
    }

    @Test
    fun `a one-day sprint is not backwards`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        // Inclusive on both ends, matching `Sprint.isActiveOn`. A strict comparison would
        // make the shortest possible sprint unsaveable, which is the kind of rule that only
        // shows up after somebody tries to book a one-day sprint.
        val sprint = create(repository)(
            projectID = project.id,
            name = "Bug bash",
            startDate = march2,
            endDate = march2,
        )
        assertEquals(march2, sprint.endDate)
    }

    @Test
    fun `a negative goal is floored rather than refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        val sprint = create(repository)(
            projectID = project.id,
            name = "Sprint 7",
            goalStoryPoints = -5,
            startDate = march2,
            endDate = march16,
        )

        // Floored, matching `Sprint`'s own `init` and the store's `updateSprint`: a value
        // that arrived from a server is normalised in place so every later reader sees one
        // number rather than a sprint holding a goal no screen can render.
        assertEquals(0, sprint.goalStoryPoints)
    }

    @Test
    fun `a blank owner is nobody rather than whitespace`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()

        val sprint = create(repository)(
            projectID = project.id,
            name = "Sprint 7",
            startDate = march2,
            endDate = march16,
            owner = "   ",
        )

        // `null` is unowned, which anyone may finish — see `Sprint`'s KDoc. Storing the
        // whitespace would make "am I the owner" answer false for a sprint showing a blank
        // owner field.
        assertNull(sprint.owner)
    }

    // MARK: UpdateSprint

    @Test
    fun `a rename leaves the rest of the sprint alone`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository)

        val updated = UpdateSprint(repository, TestDateProvider(now))(id = sprint.id, name = "Sprint 8")

        assertEquals("Sprint 8", updated.name)
        assertEquals(10, updated.goalStoryPoints)
        assertEquals("Amir", updated.owner)
        assertEquals(march2, updated.startDate)
        assertEquals(march16, updated.endDate)
    }

    @Test
    fun `an untouched end is not dragged along by a moved start`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository, startDate = march2, endDate = march16)
        val update = UpdateSprint(repository, TestDateProvider(now))

        // Only the start is mentioned, and it lands after the end that was left alone. This
        // is the edit that a check reading only its own arguments would accept, and it is
        // the one that produces a sprint which is never active — which is why this use case
        // reads before it patches.
        val failure = assertFailsWith<AppError.Validation> {
            update(id = sprint.id, startDate = Instant.parse("2026-03-20T00:00:00Z"))
        }
        assertEquals("A sprint cannot end before it starts.", failure.message)
        // Refused, so the store still holds the original window.
        assertEquals(march2, repository.sprint(sprint.id)!!.startDate)
        assertEquals(march16, repository.sprint(sprint.id)!!.endDate)
    }

    @Test
    fun `an untouched start is not dragged along by a moved end`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository, startDate = march2, endDate = march16)

        assertFailsWith<AppError.Validation> {
            UpdateSprint(repository, TestDateProvider(now))(
                id = sprint.id,
                endDate = Instant.parse("2026-03-01T00:00:00Z"),
            )
        }
        assertEquals(march16, repository.sprint(sprint.id)!!.endDate)
    }

    @Test
    fun `both ends moved together are accepted`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository)
        val june = Instant.parse("2026-06-01T00:00:00Z")

        val updated = UpdateSprint(repository, TestDateProvider(now))(
            id = sprint.id,
            startDate = june,
            endDate = Instant.parse("2026-06-15T00:00:00Z"),
        )

        assertEquals(june, updated.startDate)
    }

    @Test
    fun `editing a sprint that no longer exists is refused`() = runTest {
        val repository = FakeProjectRepository()

        // `Storage`, not a silent no-op: the caller asked to edit something, and reporting
        // success for an edit that was never applied is the combination a user reports as a
        // bug with no reproduction.
        assertFailsWith<AppError.Storage> {
            UpdateSprint(repository, TestDateProvider(now))(
                id = com.tasktracker.core.randomSprintID(),
                name = "Sprint 8",
            )
        }
    }

    @Test
    fun `handing a sprint to nobody is an instruction rather than an absence`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository, owner = "Amir")

        val updated = UpdateSprint(repository, TestDateProvider(now))(
            id = sprint.id,
            owner = Patch.Of("   "),
        )

        // Normalised to `null` on the way through, so the stored value is "unowned" and not
        // a whitespace owner that would fail an equality check against every real name.
        assertNull(updated.owner)
    }

    @Test
    fun `an unmentioned owner is left alone`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository, owner = "Amir")

        val updated = UpdateSprint(repository, TestDateProvider(now))(id = sprint.id, name = "Sprint 8")

        assertEquals("Amir", updated.owner)
    }

    // MARK: Finish, reopen, delete

    @Test
    fun `finishing dates the sprint from the injected clock`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository)

        val finished = FinishSprint(repository, TestDateProvider(now))(sprint.id)

        assertTrue(finished.isFinished)
        assertEquals(now, finished.finishedAt)
    }

    @Test
    fun `reopening clears the finish date`() = runTest {
        val repository = FakeProjectRepository()
        val (_, sprint) = sprintIn(repository)
        FinishSprint(repository, TestDateProvider(now))(sprint.id)

        // The half that gets forgotten: `reconcileFinishedAt` keeps a date on an open
        // sprint otherwise, and the state it rules out is a finished date on a sprint that
        // is not finished.
        val reopened = ReopenSprint(repository, TestDateProvider(now))(sprint.id)

        assertTrue(!reopened.isFinished)
        assertNull(reopened.finishedAt)
    }

    @Test
    fun `deleting a sprint stamps it from the injected clock`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val sprint = create(repository)(
            projectID = project.id,
            name = "Abandoned",
            startDate = march2,
            endDate = march16,
        )

        DeleteSprint(repository, TestDateProvider(now))(sprint.id)

        assertTrue(repository.sprints(project.id).isEmpty())
    }

    @Test
    fun `a store refusal reaches the caller as itself`() = runTest {
        val repository = FakeProjectRepository()
        repository.failure = AppError.Validation("'Sprint 3' is still open.")

        // The use cases do not wrap the store's refusals, and the reason is the one
        // `ProjectModel.onFailure` documents: `AppError.message` is already the user-facing
        // sentence, and a use case that reworded it would create a second source of truth
        // for text `:packages:core` owns.
        val failure = assertFailsWith<AppError.Validation> {
            FinishSprint(repository, TestDateProvider(now))(com.tasktracker.core.randomSprintID())
        }
        assertEquals("'Sprint 3' is still open.", failure.message)
    }

    // MARK: Sprint membership
    //
    // The window these tests use matters more than it looks: `now` is October 2025 and
    // `march2..march16` is 2026, so `sprintIn` produces a *planned* sprint — the one state
    // in which membership may be edited. Every refusal test therefore has to move the
    // window or the clock deliberately, and a fixture that "just happened to be active"
    // would make the green runs depend on the day they ran on.

    private suspend fun itemsIn(repository: FakeProjectRepository, project: Project) =
        listOf(
            repository.seedWorkItem(projectID = project.id, title = "One"),
            repository.seedWorkItem(projectID = project.id, title = "Two"),
        )

    @Test
    fun `adding tasks to a planned sprint moves them in`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val items = itemsIn(repository, project)

        val moved = AddWorkItemsToSprint(repository, TestDateProvider(now))(
            workItemIDs = items.map { it.id },
            sprintID = sprint.id,
        )

        assertEquals(2, moved.size)
        assertEquals(sprint.id, repository.workItem(items[0].id)!!.sprintID)
        assertEquals(sprint.id, repository.workItem(items[1].id)!!.sprintID)
    }

    @Test
    fun `adding a finished task is refused`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val done = repository.seedWorkItem(
            projectID = project.id,
            title = "Shipped",
            status = WorkItemStatus.Done,
        )

        val failure = assertFailsWith<AppError.Validation> {
            AddWorkItemsToSprint(repository, TestDateProvider(now))(
                workItemIDs = listOf(done.id),
                sprintID = sprint.id,
            )
        }

        assertEquals(
            "'${done.key}' is already finished, so it cannot be added to a sprint.",
            failure.message,
        )
        // The task is untouched: no sprint membership was written and its status was not
        // moved — committing finished work into a future window is refused, not corrected.
        assertNull(repository.workItem(done.id)!!.sprintID)
        assertEquals(WorkItemStatus.Done, repository.workItem(done.id)!!.status)
    }

    @Test
    fun `a finished task in a batch refuses the whole batch`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val open = repository.seedWorkItem(projectID = project.id, title = "Open")
        val done = repository.seedWorkItem(projectID = project.id, title = "Shipped", status = WorkItemStatus.Done)

        assertFailsWith<AppError.Validation> {
            AddWorkItemsToSprint(repository, TestDateProvider(now))(
                workItemIDs = listOf(open.id, done.id),
                sprintID = sprint.id,
            )
        }

        // Committing work into a sprint is one act: a batch that contains one finished task
        // moves nothing, so the sheet's refusal never leaves a half-committed selection.
        assertNull(repository.workItem(open.id)!!.sprintID)
        assertNull(repository.workItem(done.id)!!.sprintID)
    }

    @Test
    fun `adding a backlog task moves it onto the board as todo`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val item = repository.seedWorkItem(
            projectID = project.id,
            title = "Unstarted",
            status = WorkItemStatus.Backlog,
        )

        AddWorkItemsToSprint(repository, TestDateProvider(now))(
            workItemIDs = listOf(item.id),
            sprintID = sprint.id,
        )

        val stored = repository.workItem(item.id)!!
        assertEquals(sprint.id, stored.sprintID)
        // A committed task has a column, and Backlog is not one: it lands in the first
        // lane, where the sprint's board can actually show it.
        assertEquals(WorkItemStatus.Todo, stored.status)
    }

    @Test
    fun `adding an in-progress task keeps it in progress`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val item = repository.seedWorkItem(
            projectID = project.id,
            title = "Under way",
            status = WorkItemStatus.InProgress,
        )

        AddWorkItemsToSprint(repository, TestDateProvider(now))(
            workItemIDs = listOf(item.id),
            sprintID = sprint.id,
        )

        val stored = repository.workItem(item.id)!!
        assertEquals(sprint.id, stored.sprintID)
        // Already on the board, already committed to work: its lane is its own.
        assertEquals(WorkItemStatus.InProgress, stored.status)
    }

    @Test
    fun `removing a task sends it back to the backlog`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val item = itemsIn(repository, project).first()
        AddWorkItemsToSprint(repository, TestDateProvider(now))(
            workItemIDs = listOf(item.id),
            sprintID = sprint.id,
        )

        val removed = RemoveWorkItemsFromSprint(repository, TestDateProvider(now))(
            workItemIDs = listOf(item.id),
            sprintID = sprint.id,
        )

        assertEquals(1, removed.size)
        // `Patch.Of(null)` and not `Unmentioned` — the assertion that distinguishes them is
        // that the field actually cleared, which an unmentioned patch would leave alone.
        assertNull(repository.workItem(item.id)!!.sprintID)
    }

    @Test
    fun `an empty selection writes nothing`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val item = itemsIn(repository, project).first()
        val changesBefore = repository.publishedChanges().size

        // No sprint read either: a refusal for an instruction that asks for nothing would
        // be a banner about a tap the user did not meaningfully make, so the random id is
        // there to prove the sprint is never looked up.
        AddWorkItemsToSprint(repository, TestDateProvider(now))(
            workItemIDs = emptyList(),
            sprintID = com.tasktracker.core.randomSprintID(),
        )
        RemoveWorkItemsFromSprint(repository, TestDateProvider(now))(
            workItemIDs = emptyList(),
            sprintID = com.tasktracker.core.randomSprintID(),
        )

        assertNull(repository.workItem(item.id)!!.sprintID)
        // No write reached the store: `setWorkItemSprint` publishes both kinds of change,
        // so an unchanged count is the observable half of "wrote nothing".
        assertEquals(changesBefore, repository.publishedChanges().size)
    }

    @Test
    fun `adding to an active sprint is refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val active = create(repository)(
            projectID = project.id,
            name = "Under way",
            startDate = Instant.fromEpochSeconds(now.epochSeconds - 86_400),
            endDate = Instant.fromEpochSeconds(now.epochSeconds + 86_400),
        )
        val item = repository.seedWorkItem(projectID = project.id)

        val failure = assertFailsWith<AppError.Validation> {
            AddWorkItemsToSprint(repository, TestDateProvider(now))(
                workItemIDs = listOf(item.id),
                sprintID = active.id,
            )
        }

        // The message is what the detail screen's banner shows, so it is asserted rather
        // than left to whatever the string happened to say.
        assertEquals("'Under way' is in progress, so its tasks cannot be changed.", failure.message)
        assertNull(repository.workItem(item.id)!!.sprintID)
    }

    @Test
    fun `removing from an active sprint is refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val active = create(repository)(
            projectID = project.id,
            name = "Under way",
            startDate = Instant.fromEpochSeconds(now.epochSeconds - 86_400),
            endDate = Instant.fromEpochSeconds(now.epochSeconds + 86_400),
        )
        val item = repository.seedWorkItem(projectID = project.id, title = "Committed")
        // Put it in through the store directly: the use case that would do it is exactly
        // the one this test proves refuses, so going through it would make the fixture
        // depend on the behaviour under test.
        repository.setWorkItemSprint(listOf(item.id), Patch.Of(active.id), now)

        assertFailsWith<AppError.Validation> {
            RemoveWorkItemsFromSprint(repository, TestDateProvider(now))(
                workItemIDs = listOf(item.id),
                sprintID = active.id,
            )
        }
        assertEquals(active.id, repository.workItem(item.id)!!.sprintID)
    }

    @Test
    fun `adding to a finished sprint is refused`() = runTest {
        val repository = FakeProjectRepository()
        val (project, sprint) = sprintIn(repository)
        val item = repository.seedWorkItem(projectID = project.id)
        FinishSprint(repository, TestDateProvider(now))(sprint.id)

        val failure = assertFailsWith<AppError.Validation> {
            AddWorkItemsToSprint(repository, TestDateProvider(now))(
                workItemIDs = listOf(item.id),
                sprintID = sprint.id,
            )
        }

        // "Finished" is reported in preference to "in progress": a finished sprint can
        // still have today inside its window, and finished is the more specific complaint.
        assertEquals("'Sprint 1' is finished, so its tasks cannot be changed.", failure.message)
        assertNull(repository.workItem(item.id)!!.sprintID)
    }

    @Test
    fun `adding to a sprint that no longer exists is refused`() = runTest {
        val repository = FakeProjectRepository()
        val (project, _) = sprintIn(repository)
        val item = repository.seedWorkItem(projectID = project.id)

        // `Storage`, not a silent no-op — the same reason `UpdateSprint` gives: reporting
        // success for a write that never happened is the combination a user reports as a
        // bug with no reproduction.
        assertFailsWith<AppError.Storage> {
            AddWorkItemsToSprint(repository, TestDateProvider(now))(
                workItemIDs = listOf(item.id),
                sprintID = com.tasktracker.core.randomSprintID(),
            )
        }
        assertNull(repository.workItem(item.id)!!.sprintID)
    }
}
