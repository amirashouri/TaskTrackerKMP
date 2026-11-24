package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The epic use cases' decisions, tested against the fake port.
 *
 * The two rules the product states are here rather than only in the picker: an epic must name
 * at least one project, and a link to an epic that does not list the item's project is refused
 * by the server on the next push. A picker that offered neither is a convenience, not the rule,
 * so the rule is tested where it is enforced.
 */
class EpicUseCasesTest {

    private val now: Instant = Instant.fromEpochSeconds(1_760_000_000)

    private suspend fun twoProjects(repository: FakeProjectRepository): Pair<Project, Project> =
        repository.seedProject("Apollo", "APO") to repository.seedProject("Borealis", "BOR")

    private fun create(repository: FakeProjectRepository) =
        CreateEpic(repository, TestDateProvider(now))

    private fun update(repository: FakeProjectRepository) =
        UpdateEpic(repository, TestDateProvider(now))

    private fun link(repository: FakeProjectRepository) =
        SetWorkItemEpic(repository, TestDateProvider(now))

    @Test
    fun anEpicMustNameAtLeastOneProject() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, _) = twoProjects(repository)

        val epic = create(repository)(projectIDs = listOf(apollo.id), title = "Checkout")
        assertEquals(listOf(apollo.id), epic.projectIDs)

        val failure = assertFailsWith<AppError.Validation> {
            create(repository)(projectIDs = emptyList(), title = "No scope")
        }

        assertTrue(failure.message.orEmpty().contains("one project"))
        assertEquals(1, repository.epics().size, "a refused create must write nothing")
    }

    @Test
    fun anEpicKeepsTheChosenProjectOrderAndStartsUnarchived() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, borealis) = twoProjects(repository)

        val epic = create(repository)(
            projectIDs = listOf(borealis.id, apollo.id),
            title = "  Checkout  ",
            details = "  One feature  ",
        )

        assertEquals(listOf(borealis.id, apollo.id), epic.projectIDs)
        assertEquals("Checkout", epic.title)
        assertEquals("One feature", epic.details)
        assertTrue(!epic.isArchived)
        assertEquals(epic, repository.epic(epic.id))
    }

    @Test
    fun aRepeatedProjectIsStoredOnceInTheChosenOrder() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, borealis) = twoProjects(repository)

        val epic = create(repository)(
            projectIDs = listOf(borealis.id, apollo.id, borealis.id),
            title = "Checkout",
        )

        assertEquals(listOf(borealis.id, apollo.id), epic.projectIDs)
    }

    @Test
    fun anItemCannotJoinAnEpicThatDoesNotSpanItsProject() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, borealis) = twoProjects(repository)
        val other = repository.seedProject("Cygnus", "CYG")
        val epic = create(repository)(projectIDs = listOf(apollo.id, borealis.id), title = "Checkout")
        val item = repository.createWorkItem(
            projectID = other.id,
            title = "Task",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = now,
        )

        val failure = assertFailsWith<AppError.Validation> {
            link(repository)(item.id, Patch.Of(epic.id))
        }

        assertTrue(failure.message.orEmpty().contains("does not span"))
        assertNull(repository.workItem(item.id)?.epicID, "the refused link must not be stored")
    }

    @Test
    fun anItemBelongsToAtMostOneEpic() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, borealis) = twoProjects(repository)
        val first = create(repository)(projectIDs = listOf(apollo.id, borealis.id), title = "First")
        val second = create(repository)(projectIDs = listOf(apollo.id, borealis.id), title = "Second")
        val item = repository.createWorkItem(
            projectID = apollo.id,
            title = "Task",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = now,
        )

        link(repository)(item.id, Patch.Of(first.id))
        link(repository)(item.id, Patch.Of(second.id))

        // One field, not a set: the second link replaces the first rather than adding a
        // membership, so "the epic's progress" can never double-count the same card.
        assertEquals(second.id, repository.workItem(item.id)?.epicID)
        assertTrue(repository.workItemsForEpic(first.id).isEmpty())
        assertEquals(listOf(item.id), repository.workItemsForEpic(second.id).map { it.id })
    }

    @Test
    fun clearingTheLinkLeavesTheItemWithoutAnEpic() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, borealis) = twoProjects(repository)
        val epic = create(repository)(projectIDs = listOf(apollo.id, borealis.id), title = "Checkout")
        val item = repository.createWorkItem(
            projectID = apollo.id,
            title = "Task",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = now,
        )
        link(repository)(item.id, Patch.Of(epic.id))

        link(repository)(item.id, Patch.Of(null))

        assertNull(repository.workItem(item.id)?.epicID)
    }

    @Test
    fun droppingAProjectFromAnEpicUnlinksTheItemsItHeld() = runTest {
        val repository = FakeProjectRepository()
        val (apollo, borealis) = twoProjects(repository)
        val epic = create(repository)(projectIDs = listOf(apollo.id, borealis.id), title = "Checkout")
        val item = repository.createWorkItem(
            projectID = borealis.id,
            title = "Task",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = now,
        )
        link(repository)(item.id, Patch.Of(epic.id))

        update(repository)(epic.id, projectIDs = listOf(apollo.id, repository.seedProject("Cygnus", "CYG").id))

        assertNull(
            repository.workItem(item.id)?.epicID,
            "the server would refuse this link on the item's next write, so it must not survive",
        )
    }
}
