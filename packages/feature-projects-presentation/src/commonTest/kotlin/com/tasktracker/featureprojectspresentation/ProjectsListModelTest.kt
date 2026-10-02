package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRemovalOutcome
import com.tasktracker.featureprojectsdomain.RemoveProject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The projects list, and above all the one invariant that is not visible in any screenshot:
 * **the confirmation's wording has to agree with the action that follows it.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsListModelTest {

    // MARK: The outstanding count

    /**
     * The count in a row and the outcome of pressing delete come from the same rule.
     *
     * [ProjectRow.removalOutcome] duplicates `RemoveProject`'s decision so the dialog can be
     * worded before the button is pressed. A duplicated rule is a rule that drifts, and the
     * failure is the worst shape this feature has: the dialog says "this will be archived"
     * and the store deletes a project with unfinished work in it. So this asserts the two
     * against the same data rather than trusting the comment on [ProjectRow.outstandingCount].
     */
    @Test
    fun `a closed task counts as outstanding and a done one does not`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done))
        repository.seed(testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Closed))
        repository.seed(testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.Todo))

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        val row = model.rows.single()
        // Three tasks, one of which is Done. `isComplete` is Done alone, so a Closed task
        // still counts - it is finished with, but the decision behind it is a record.
        assertEquals(2, row.outstandingCount)
        assertEquals(ProjectRemovalOutcome.Archived, row.removalOutcome)

        // And the use case, asked independently, reaches the same verdict.
        val outcome = RemoveProject(repository, clock)(project.id)
        assertEquals(ProjectRemovalOutcome.Archived, outcome)
    }

    /** The mirror of the above: nothing outstanding means the dialog may promise a delete. */
    @Test
    fun `a project of only done tasks is reported as deletable and is deleted`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done))
        repository.seed(testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Done))

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(0, model.rows.single().outstandingCount)
        assertEquals(ProjectRemovalOutcome.Deleted, model.rows.single().removalOutcome)
        assertEquals(
            ProjectRemovalOutcome.Deleted,
            RemoveProject(repository, clock)(project.id),
        )
    }

    // MARK: Rows

    @Test
    fun `archived projects are left out`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        repository.seed(testProject(name = "Live"))
        repository.seed(testProject(name = "Filed", isArchived = true))

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(listOf("Live"), model.rows.map { it.summary.name })
    }

    @Test
    fun `rows are newest activity first`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val older = Instant.fromEpochSeconds(1_700_000_000)
        val newer = Instant.fromEpochSeconds(1_760_000_000)
        repository.seed(testProject(name = "Older", updatedAt = older))
        repository.seed(testProject(name = "Newer", updatedAt = newer))

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(listOf("Newer", "Older"), model.rows.map { it.summary.name })
    }

    @Test
    fun `the item count is the sum across every status`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject()
        repository.seed(project)
        repeat(3) { index ->
            repository.seed(
                testWorkItem(project.id, key = "AP-$index", status = WorkItemStatus.Todo),
            )
        }
        repository.seed(testWorkItem(project.id, key = "AP-9", status = WorkItemStatus.Done))

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        val row = model.rows.single()
        assertEquals(4, row.summary.itemCount)
        assertEquals(3, row.outstandingCount)
    }

    // MARK: Removal

    @Test
    fun `archiving instead of deleting is reported with the name and the count`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject(name = "Apollo")
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Todo))

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.remove(project.id))

        val notice = assertIs<RemovalNotice.ArchivedInstead>(model.notice)
        assertEquals("Apollo", notice.projectName)
        assertEquals(1, notice.outstandingCount)
    }

    @Test
    fun `deleting a clear project is reported as deleted`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject(name = "Empty")
        repository.seed(project)

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.remove(project.id))
        assertEquals(RemovalNotice.Deleted("Empty"), model.notice)
    }

    /**
     * An already-archived project changed nothing, and saying it did would be a lie.
     *
     * The list only shows unarchived projects, so this is a second device archiving the
     * project between the read and the press. The return value is `false` precisely so a
     * caller does not navigate on a no-op.
     */
    @Test
    fun `an already archived project reports no change`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject(name = "Filed", isArchived = true)
        repository.seed(project)

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.remove(project.id))
        assertEquals(RemovalNotice.AlreadyArchived("Filed"), model.notice)
    }

    @Test
    fun `a failed removal reports the failure and changes nothing`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject()
        repository.seed(project)

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()

        repository.failure = AppError.Storage("the disk is full")
        assertFalse(model.remove(project.id))

        assertEquals("Could not save your changes. the disk is full", model.errorMessage)
        assertNull(model.notice)
    }

    // MARK: Observation
    //
    // No commas in these names, and not for style. Kotlin/Native mangles a `commonTest`
    // function name into an Objective-C selector, and a comma is not legal in one, so a
    // name with a comma compiles for `jvmTest` and fails the iOS target - which is how the
    // two names below reached `iosSimulatorArm64` before being caught. They read as one
    // clause for the same reason.

    @Test
    fun `a work item change refreshes the list because the counts move`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        val project = testProject()
        repository.seed(project)

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()
        assertEquals(0, model.rows.single().summary.itemCount)

        // A project-scoped work item change, as a write elsewhere would produce.
        repository.emit(ProjectChange(ProjectChange.Kind.WorkItems, project.id))
        runCurrent()

        // The fake has no way to add a task without going through createWorkItem, so the
        // count moves by writing one - and the point is that the model re-read at all.
        repository.createWorkItem(
            projectID = project.id,
            title = "Late arrival",
            details = "",
            status = WorkItemStatus.Todo,
            priority = com.tasktracker.domain.Priority.Medium,
            assignee = null,
            dueDate = null,
            now = clock.now(),
        )
        runCurrent()

        assertEquals(1, model.rows.single().summary.itemCount)
    }

    @Test
    fun `stopping observation unsubscribes so a write cannot wake a screen nobody is on`() =
        runTest {
            val repository = FakeProjectRepository()
            val clock = TestDateProvider()
            repository.seed(testProject())

            val model = ProjectsListModel(repository, clock)
            model.observe(eagerScope())
            advanceUntilIdle()
            assertEquals(1, repository.subscriberCount)

            model.stopObserving()
            runCurrent()

            assertEquals(0, repository.subscriberCount)
        }

    @Test
    fun `observing again replaces the previous subscription rather than adding one`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()
        repository.seed(testProject())

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        model.observe(eagerScope())
        advanceUntilIdle()

        // The trap PLAN 13.13 names: a project switch mid-view building a second model, or
        // re-observing, must not leave the first collection alive.
        assertEquals(1, repository.subscriberCount)
    }

    // MARK: Seeding

    @Test
    fun `sample data is seeded once and not on every reload`() = runTest {
        val repository = FakeProjectRepository()
        val clock = TestDateProvider()

        val model = ProjectsListModel(repository, clock)
        model.observe(eagerScope())
        advanceUntilIdle()
        assertEquals(1, repository.seedCount)
        assertEquals("Task Tracker", model.rows.single().summary.name)

        repository.emit(ProjectChange(ProjectChange.Kind.Projects))
        advanceUntilIdle()
        repository.emit(ProjectChange(ProjectChange.Kind.Projects))
        advanceUntilIdle()

        // The store's own `seedSampleDataIfNeeded` is idempotent, but it opens a
        // transaction to discover that, and this list reloads on every write in the feature.
        assertEquals(1, repository.seedCount)
    }

    @Test
    fun `a failed seed surfaces rather than showing an empty list`() = runTest {
        val repository = FakeProjectRepository()
        repository.seedFailure = AppError.Storage("no room")

        val model = ProjectsListModel(repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals("Could not save your changes. no room", model.errorMessage)
        assertFalse(model.isLoading)
    }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val repository = FakeProjectRepository()
        repository.seedFailure = AppError.Offline

        val model = ProjectsListModel(repository, TestDateProvider())
        model.observe(eagerScope())
        advanceUntilIdle()
        // `AppError.message` verbatim, so this cannot drift from `:packages:core`'s wording
        // - the whole reason the base class does not re-word failures.
        assertEquals(AppError.Offline.message, model.errorMessage)

        model.dismissError()
        assertNull(model.errorMessage)
    }
}
