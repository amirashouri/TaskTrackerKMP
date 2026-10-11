package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The use cases' decisions, tested against a fake port rather than a real store.
 *
 * What is worth testing here is *when a write is refused*, because that is the part the
 * data layer cannot check for you: the store will happily accept a blank name or a
 * duplicate key, and the rule exists precisely to stop it reaching that far.
 */
class ProjectUseCasesTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    // MARK: CreateProject

    @Test
    fun `a valid project is created and stored`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateProject(repository, TestDateProvider(now))

        val project = create(name = "  Apollo  ", keyPrefix = " apollo ", details = "Moon shots")

        assertEquals("Apollo", project.name)
        assertEquals("APOLLO", project.keyPrefix)
        assertEquals(1, repository.projectCount())
    }

    @Test
    fun `create project keeps selected member identifiers`() = runTest {
        val repository = FakeProjectRepository()
        val member = UserID("10000000-0000-4000-8000-000000000001")
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "AP",
            memberIDs = listOf(member),
        )

        assertEquals(listOf(member), project.memberIDs)
        assertEquals(listOf(member), repository.project(project.id)?.memberIDs)
    }

    @Test
    fun `a blank name is refused before anything is stored`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(name = "   ", keyPrefix = "APOLLO") }

        assertEquals(
            0,
            repository.projectCount(),
            "a rejected create must not leave a row behind",
        )
    }

    @Test
    fun `a key prefix that is not a usable identifier is refused`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(name = "Apollo", keyPrefix = "!") }

        assertEquals(0, repository.projectCount())
    }

    @Test
    fun `a store failure reaches the caller instead of being swallowed`() = runTest {
        val repository = FakeProjectRepository()
        repository.failure = AppError.Storage("disk is on fire")
        val create = CreateProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(name = "Apollo", keyPrefix = "APOLLO") }
    }

    // MARK: UpdateProject

    @Test
    fun `a rename is normalised and stored`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
            details = "Moon shots",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))

        val updated = update(id = project.id, name = "  Artemis  ")

        assertEquals("Artemis", updated.name)
        assertEquals(
            "Moon shots",
            updated.details,
            "an edit that does not mention the notes must not touch them",
        )
        val stored = assertNotNull(repository.project(project.id))
        assertEquals("Artemis", stored.name)
    }

    @Test
    fun `rewriting the notes leaves the name alone`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))

        val updated = update(id = project.id, details = "Lunar landings")

        assertEquals("Apollo", updated.name)
        assertEquals("Lunar landings", updated.details)
    }

    @Test
    fun `a blank rename is refused before the store is touched`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))

        assertFailsWith<AppError> { update(id = project.id, name = "   ") }

        val stored = assertNotNull(repository.project(project.id))
        assertEquals(
            "Apollo",
            stored.name,
            "a rejected edit must not leave a half-renamed row behind",
        )
    }

    @Test
    fun `a no-op edit reads the project rather than writing it`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))
        val changesBefore = repository.publishedChanges().size

        val unchanged = update(id = project.id)

        assertEquals(
            project.updatedAt,
            unchanged.updatedAt,
            "nothing changed so nothing was written",
        )
        assertEquals("Apollo", unchanged.name)
        assertEquals(
            changesBefore,
            repository.publishedChanges().size,
            "a no-op must not announce a change that did not happen",
        )
    }

    @Test
    fun `renaming a project that has been deleted is refused`() = runTest {
        val repository = FakeProjectRepository()
        val project = CreateProject(repository, TestDateProvider(now))(
            name = "Apollo",
            keyPrefix = "APOLLO",
        )
        val update = UpdateProject(repository, TestDateProvider(now + 60.seconds))
        // Deleting through the shared fake, on the same public path the UI would take.
        repository.deleteProject(project.id)

        assertFailsWith<AppError> { update(id = project.id, name = "Artemis") }
    }

    // MARK: CreateWorkItem

    @Test
    fun `a work item is created in its project`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val create = CreateWorkItem(repository, TestDateProvider(now))

        val item = create(projectID = project.id, title = "  Land it  ")

        assertEquals("Land it", item.title)
        assertEquals(project.id, item.projectID)
        assertEquals(1, repository.itemCount())
    }

    @Test
    fun `a blank title is refused and nothing is stored`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val create = CreateWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> { create(projectID = project.id, title = "   ") }

        assertEquals(0, repository.itemCount())
    }

    @Test
    fun `an item in a project that does not exist is refused`() = runTest {
        val repository = FakeProjectRepository()
        val create = CreateWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> {
            create(projectID = randomProjectID(), title = "Orphan")
        }

        assertEquals(0, repository.itemCount())
    }

    // MARK: MoveWorkItem

    @Test
    fun `a move reaches the store with the new status`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val move = MoveWorkItem(repository, TestDateProvider(now))

        val moved = move(id = item.id, to = WorkItemStatus.InProgress)

        assertEquals(WorkItemStatus.InProgress, moved.status)
        assertEquals(1, repository.moveCallCount())
    }

    @Test
    fun `moving an item that no longer exists is refused and no write is attempted`() = runTest {
        val repository = FakeProjectRepository()
        val move = MoveWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> { move(id = randomWorkItemID(), to = WorkItemStatus.Done) }

        assertEquals(
            0,
            repository.moveCallCount(),
            "the existence check has to happen before the write not after",
        )
    }

    @Test
    fun `the current status is read before moving so a transition rule can use it`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.InReview)
        val move = MoveWorkItem(repository, TestDateProvider(now))

        // Moves backwards. Permissive today, because no transition policy has been written
        // down — but the test pins the behaviour so that adding one is a deliberate change
        // to a failing test, rather than a silent difference nobody notices.
        val moved = move(id = item.id, to = WorkItemStatus.Todo)

        assertEquals(WorkItemStatus.Todo, moved.status)
    }

    // MARK: SaveWorkItem

    @Test
    fun `saving without an id creates and does not attempt a move`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val save = SaveWorkItem(repository, TestDateProvider(now))

        val outcome = save(projectID = project.id, title = "  New thing  ", status = WorkItemStatus.Todo)

        val created = assertIs<WorkItemSaveOutcome.Created>(outcome)
        assertEquals(1, repository.itemCount())
        assertEquals("New thing", repository.workItem(created.workItemID)?.title)
        assertEquals(
            0,
            repository.moveCallCount(),
            "a new item has no previous column to leave",
        )
    }

    @Test
    fun `a create from a sprint board joins that sprint and carries its estimate`() = runTest {
        // The board shows only the active sprint's items, so the sprint id handed at creation
        // is the whole difference between a task that appears when the sheet closes and one
        // that saves into the store and never shows up. Deliberately asserting both fields off
        // the one write: the regression to watch is the editor routing `initialSprintID` as
        // `null` while the user types points into the estimate field.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val sprint = repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 20,
            startDate = now,
            endDate = now + 7.days,
            ownerID = null,
            now = now,
        )
        val save = SaveWorkItem(repository, TestDateProvider(now))

        save(
            projectID = project.id,
            title = "Wired through the sprint filter",
            status = WorkItemStatus.Todo,
            storyPoints = 5,
            initialSprintID = sprint.id,
        )

        val stored = repository.workItems(project.id).single()
        assertEquals(sprint.id, stored.sprintID, "the task must land in the sprint the add came from")
        assertEquals(5, stored.storyPoints, "the estimate must not be dropped on the floor")
    }

    @Test
    fun `an unestimated create still joins the sprint and stays unestimated`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val sprint = repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 20,
            startDate = now,
            endDate = now + 7.days,
            ownerID = null,
            now = now,
        )
        val save = SaveWorkItem(repository, TestDateProvider(now))

        save(
            projectID = project.id,
            title = "No estimate yet",
            status = WorkItemStatus.Todo,
            initialSprintID = sprint.id,
        )

        val stored = repository.workItems(project.id).single()
        assertEquals(sprint.id, stored.sprintID)
        assertNull(stored.storyPoints, "absent input must not become zero")
    }

    @Test
    fun `editing fields without touching the status does not move the item`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val save = SaveWorkItem(repository, TestDateProvider(now))

        val outcome = save(
            id = item.id,
            projectID = project.id,
            title = "Renamed",
            status = WorkItemStatus.Todo,
        )

        assertEquals(WorkItemSaveOutcome.Updated(item.id, moved = false), outcome)
        assertEquals(
            0,
            repository.moveCallCount(),
            "an unchanged status must not re-rank anything",
        )
    }

    @Test
    fun `changing the status also re-ranks which a field update alone would not do`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val save = SaveWorkItem(repository, TestDateProvider(now))

        val outcome = save(
            id = item.id,
            projectID = project.id,
            title = "Land the capsule",
            status = WorkItemStatus.Done,
        )

        assertEquals(WorkItemSaveOutcome.Updated(item.id, moved = true), outcome)
        assertEquals(1, repository.moveCallCount())
        assertEquals(WorkItemStatus.Done, repository.workItem(item.id)?.status)
    }

    @Test
    fun `a blank title is refused on save and nothing is written`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val item = repository.seedWorkItem(projectID = project.id)
        val save = SaveWorkItem(repository, TestDateProvider(now))

        assertFailsWith<AppError> {
            save(id = item.id, projectID = project.id, title = "  ", status = WorkItemStatus.Todo)
        }

        assertEquals(0, repository.moveCallCount())
    }

    @Test
    fun `saving with a cleared assignee clears it rather than leaving it alone`() = runTest {
        // The case a plain nullable cannot express on its own. `SaveWorkItem` always sends
        // a value — a `null` assigneeID *is* an instruction to unassign — so a port that read
        // "not mentioned" from it would keep the old assignee and report the edit as saved.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val adaID = UserID("33333333-3333-4333-8333-333333333333")
        val item = repository.createWorkItem(
            projectID = project.id,
            title = "Land the capsule",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = adaID,
            dueDate = null,
            now = now,
        )
        val save = SaveWorkItem(repository, TestDateProvider(now + 60.seconds))

        save(
            id = item.id,
            projectID = project.id,
            title = "Land the capsule",
            status = WorkItemStatus.Todo,
            assigneeID = null,
        )

        assertEquals(null, repository.workItem(item.id)?.assigneeID)
    }

    @Test
    fun `a patch that was not mentioned leaves the field exactly as it was`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        val due = now + 86_400.seconds
        val adaID = UserID("33333333-3333-4333-8333-333333333333")
        val item = repository.createWorkItem(
            projectID = project.id,
            title = "Land the capsule",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = adaID,
            dueDate = due,
            now = now,
        )

        repository.updateWorkItem(
            id = item.id,
            title = "Renamed",
            details = null,
            priority = null,
            assigneeID = Patch.Unmentioned,
            dueDate = Patch.Unmentioned,
            storyPoints = Patch.Unmentioned,
            now = now + 60.seconds,
        )

        val stored = assertNotNull(repository.workItem(item.id))
        assertEquals(adaID, stored.assigneeID)
        assertEquals(due, stored.dueDate)
    }

    // MARK: RemoveProject

    @Test
    fun `a project with unfinished work is archived rather than deleted`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val remove = RemoveProject(repository, TestDateProvider(now))

        val outcome = remove(project.id)

        assertEquals(ProjectRemovalOutcome.Archived, outcome)
        assertEquals(1, repository.projectCount(), "archiving destroys nothing")
        assertTrue(assertNotNull(repository.project(project.id)).isArchived)
    }

    @Test
    fun `a closed item is a decision and keeps its project out of the delete path`() = runTest {
        // `isComplete` and not `isTerminal`. A closed item is finished with, but it is also
        // a record of a decision that was taken, so a project holding one is archived.
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Closed)
        val remove = RemoveProject(repository, TestDateProvider(now))

        val outcome = remove(project.id)

        assertEquals(ProjectRemovalOutcome.Archived, outcome)
        assertEquals(1, repository.projectCount())
    }

    @Test
    fun `a project whose work is all delivered is deleted with its items`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Done)
        val remove = RemoveProject(repository, TestDateProvider(now))

        val outcome = remove(project.id)

        assertEquals(ProjectRemovalOutcome.Deleted, outcome)
        assertEquals(0, repository.projectCount())
        assertEquals(0, repository.itemCount(), "the cascade takes the items with it")
    }

    @Test
    fun `removing an already-archived project does nothing at all`() = runTest {
        val repository = FakeProjectRepository()
        val project = repository.seedProject()
        repository.seedWorkItem(projectID = project.id, status = WorkItemStatus.Todo)
        val clock = TestDateProvider(now)
        RemoveProject(repository, clock)(project.id)
        val changesBefore = repository.publishedChanges().size

        val outcome = RemoveProject(repository, clock)(project.id)

        assertEquals(ProjectRemovalOutcome.AlreadyArchived, outcome)
        assertEquals(
            changesBefore,
            repository.publishedChanges().size,
            "a second removal must not publish a change that did not happen",
        )
    }

    @Test
    fun `removing a project that no longer exists is refused`() = runTest {
        val repository = FakeProjectRepository()
        val remove = RemoveProject(repository, TestDateProvider(now))

        assertFailsWith<AppError> { remove(randomProjectID()) }
    }
}
