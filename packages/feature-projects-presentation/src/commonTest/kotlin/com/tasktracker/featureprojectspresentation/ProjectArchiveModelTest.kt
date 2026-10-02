package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The archive: resolved work, grouped by calendar month.
 *
 * The month *grouping* belongs to the domain and is tested there. What matters here is
 * that the model injects its zone rather than letting the grouping read the device's, and
 * that the resulting sections are ordered the way a reader wants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectArchiveModelTest {

    private fun archiveOf(repository: FakeProjectRepository, projectID: com.tasktracker.core.ProjectID, zone: TimeZone = TimeZone.UTC) =
        ProjectArchiveModel(projectID, repository, TestDateProvider(), timeZone = zone)

    @Test
    fun `the archive holds resolved work and says so when there is none`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)

        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.projection.isEmpty)
        assertEquals(0, model.projection.itemCount)
        // Vacuously true, and deliberately so: an archive that has not loaded yet must not
        // read as "some of these can be changed".
        assertTrue(model.allItemsAreFiledAway)
    }

    @Test
    fun `nothing untriaged means everything is filed away`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(
            testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done, resolvedAt = MARCH),
        )

        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.allItemsAreFiledAway)
    }

    @Test
    fun `a backlog item means not everything is filed away`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(
            testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done, resolvedAt = MARCH),
        )
        repository.seed(testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Backlog))

        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.allItemsAreFiledAway)
    }

    /** An archive is read backwards, from now towards the past. */
    @Test
    fun `months are newest first`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done, resolvedAt = JANUARY))
        repository.seed(testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Done, resolvedAt = MARCH))
        repository.seed(testWorkItem(project.id, key = "AP-3", status = WorkItemStatus.Closed, resolvedAt = FEBRUARY))

        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(
            listOf("2026-03", "2026-02", "2026-01"),
            model.projection.months.map { it.monthStart.toString().take(7) },
        )
    }

    @Test
    fun `work from the same month shares a section`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(
            testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done, resolvedAt = MARCH),
        )
        repository.seed(
            testWorkItem(project.id, key = "AP-2", status = WorkItemStatus.Done, resolvedAt = MARCH + 1.hours),
        )

        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(1, model.projection.months.size)
        assertEquals(2, model.projection.months.single().count)
    }

    /**
     * The zone is injected, so a section boundary is a decision rather than a side effect.
     *
     * Two timestamps an hour apart either share a month or straddle two months depending on
     * the device's zone - 00:30 UTC on the 1st is still February in Auckland. A test that
     * could not name its zone would be a test that failed on a developer's laptop and
     * passed in CI, which is the worst of both.
     */
    @Test
    fun `the month boundary follows the injected zone`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        // 2026-02-28T12:00Z is 2026-02-28 in UTC and 2026-03-01T01:00 in Auckland (UTC+13
        // under NZDT), so the same instant is in two different months.
        val edge = Instant.parse("2026-02-28T12:00:00Z")
        repository.seed(testWorkItem(project.id, key = "AP-1", status = WorkItemStatus.Done, resolvedAt = edge))

        val utc = archiveOf(repository, project.id, TimeZone.UTC)
        utc.observe(eagerScope())
        val auckland = archiveOf(repository, project.id, TimeZone.of("Pacific/Auckland"))
        auckland.observe(eagerScope())
        advanceUntilIdle()

        assertEquals("2026-02-01T00:00:00Z", utc.projection.months.single().monthStart.toString())
        assertEquals("2026-02-28T11:00:00Z", auckland.projection.months.single().monthStart.toString())
    }

    @Test
    fun `the archive ignores another project's changes`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, randomProjectID())))
        assertTrue(model.isRelevant(ProjectChange(ProjectChange.Kind.WorkItems, project.id)))
    }

    @Test
    fun `the archive carries the project so the header can name it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(name = "Apollo")
        repository.seed(project)

        val model = archiveOf(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals("Apollo", model.projection.project?.name)
    }

    private companion object {
        val JANUARY: Instant = Instant.parse("2026-01-15T12:00:00Z")
        val FEBRUARY: Instant = Instant.parse("2026-02-15T12:00:00Z")
        val MARCH: Instant = Instant.parse("2026-03-15T12:00:00Z")
    }
}
