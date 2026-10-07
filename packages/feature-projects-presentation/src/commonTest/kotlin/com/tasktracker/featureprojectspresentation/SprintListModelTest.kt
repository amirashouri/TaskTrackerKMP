package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintSummary
import com.tasktracker.featureprojectsdomain.Patch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The sprint list: what a row says.
 *
 * The list no longer answers "may I create one" — with overlapping windows as the rule, a
 * second sprint is always allowed and only its window can be refused. That question now
 * lives on [SprintEditorModel], which is where the blocker is named.
 *
 * ## The zone is pinned, so a date assertion is about the conversion
 *
 * Every model here takes a [TimeZone] rather than reading the system's. That is not only
 * test hygiene — an instant at 23:30 UTC on the last day of a month is the *next* month in
 * most of Asia, so a test that used the machine's zone would be asserting on where the
 * build ran. `TimeZone.UTC` throughout, which makes the expected [LocalDate]s readable off
 * the fixture's own instants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SprintListModelTest {

    private val zone: TimeZone = TimeZone.UTC

    private companion object {
        /** A fixed instant, so nothing here depends on a clock. */
        val FIXED_NOW: Instant = Instant.fromEpochSeconds(1_760_000_000)
    }

    private fun listModel(
        repository: FakeProjectRepository,
        projectID: com.tasktracker.core.ProjectID,
    ) = SprintListModel(projectID, repository, timeZone = zone)

    @Test
    fun `a project with no sprints says so once it has read`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.rows.isEmpty())
        assertEquals(true, model.isEmpty)
    }

    @Test
    fun `an unread list is loading rather than empty`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)

        val model = listModel(repository, project.id)

        // Before `observe`. A `Boolean` would read `false` here and a screen would flash an
        // empty state over a populated list on every cold start.
        assertNull(model.isEmpty)
    }

    @Test
    fun `a row's dates are the days the sprint was stored on`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val row = model.rows.single()
        assertEquals(LocalDate(2026, Month.MARCH, 2), row.startDate)
        assertEquals(LocalDate(2026, Month.MARCH, 16), row.endDate)
    }

    @Test
    fun `a row counts only its own sprint's points`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val first = testSprint(project.id, name = "Sprint 1", goalStoryPoints = 10)
        val second = testSprint(project.id, name = "Sprint 2", goalStoryPoints = 4)
        repository.seed(first)
        repository.seed(second)
        // A `Done` item in the first sprint contributes its points to *that* sprint. If the
        // row summed the whole project, this row would read 9 of 10 having achieved nothing.
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-1",
                status = WorkItemStatus.Done,
                storyPoints = 8,
                sprintID = first.id,
            ),
        )
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-2",
                status = WorkItemStatus.Done,
                storyPoints = 3,
                sprintID = second.id,
            ),
        )

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val rows = model.rows.associateBy { it.sprint.id }
        assertEquals(8, rows.getValue(first.id).achievedStoryPoints)
        assertEquals(8, rows.getValue(first.id).committedStoryPoints)
        assertEquals(3, rows.getValue(second.id).achievedStoryPoints)
    }

    @Test
    fun `an unestimated task is committed to but not achieved until it is done`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, goalStoryPoints = 5)
        repository.seed(sprint)
        repository.seed(
            testWorkItem(projectID = project.id, key = "AP-1", storyPoints = 5, sprintID = sprint.id),
        )
        repository.seed(
            // `null` is unestimated, not zero: it must not move either sum.
            testWorkItem(projectID = project.id, key = "AP-2", storyPoints = null, sprintID = sprint.id),
        )

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val row = model.rows.single()
        assertEquals(5, row.committedStoryPoints)
        assertEquals(0, row.achievedStoryPoints)
    }

    @Test
    fun `a row's progress is the fraction achieved against the goal`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, goalStoryPoints = 4)
        repository.seed(sprint)
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-1",
                status = WorkItemStatus.Done,
                storyPoints = 3,
                sprintID = sprint.id,
            ),
        )

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertEquals(0.75f, model.rows.single().progressFraction)
    }

    @Test
    fun `a sprint that beat its goal keeps the overshoot rather than clamping it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, goalStoryPoints = 2)
        repository.seed(sprint)
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-1",
                status = WorkItemStatus.Done,
                storyPoints = 5,
                sprintID = sprint.id,
            ),
        )

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        // 2.5, not 1.0. A bar pinned at the end would throw away the most interesting thing
        // about a sprint that beat its target.
        assertEquals(2.5f, model.rows.single().progressFraction)
    }

    @Test
    fun `a sprint with no goal has no progress rather than none of it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testSprint(project.id, goalStoryPoints = 0))

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val row = model.rows.single()
        assertFalse(row.hasGoal)
        // `null`, not `0f`: a sprint nobody gave a target has not made no progress.
        assertNull(row.progressFraction)
    }

    @Test
    fun `an unfinished sprint is still a row the list shows`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testSprint(project.id, isFinished = false))
        repository.seed(testSprint(project.id, isFinished = true))

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        // Both are rows: the rule is about a *window* now, so an open sprint neither hides
        // itself nor stops another one being created — see `SprintListModel`'s KDoc.
        assertEquals(2, model.rows.size)
        assertTrue(model.rows.any { !it.sprint.isFinished })
        assertTrue(model.rows.any { it.sprint.isFinished })
    }

    @Test
    fun `a write anywhere in the project re-reads the list`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()
        assertTrue(model.rows.isEmpty())

        repository.seed(testSprint(project.id))
        // The fake publishes a change after a write, and this list is subscribed to it. A
        // sprint write is what announces `Kind.Sprints` for the *project's* id, so this is
        // the exact signal a tightened `isRelevant` would start dropping.
        repository.finishSprint(id = repository.sprints(project.id).single().id, now = FIXED_NOW)
        advanceUntilIdle()

        // A sprint write announces the *project's* id, so the subscription cannot match on
        // the sprint's own id — see `SprintListModel.isRelevant`. If that filter were ever
        // tightened to the sprint's id, this is the assertion that would catch it.
        assertEquals(1, model.rows.size)
    }

    @Test
    fun `a row's dates are read in the model's zone and not in UTC's`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        // 20:00 UTC on the 16th is already the 17th eleven hours east. This instant is the
        // whole reason the zone is a model parameter rather than a cast: a sprint stored as
        // an instant begins on a different day for a user who flies, and every other test in
        // this file uses UTC, so without this one nothing would notice a conversion that
        // silently ignored the zone it was handed.
        val eastEleven: TimeZone = FixedOffsetTimeZone(UtcOffset(hours = 11))
        repository.seed(
            testSprint(
                projectID = project.id,
                startDate = Instant.parse("2026-03-02T20:00:00Z"),
                endDate = Instant.parse("2026-03-16T20:00:00Z"),
            ),
        )

        val model = SprintListModel(project.id, repository, timeZone = eastEleven)
        model.observe(eagerScope())
        advanceUntilIdle()

        val row = model.rows.single()
        assertEquals(
            LocalDate(2026, Month.MARCH, 3),
            row.startDate,
            "20:00 UTC on the 2nd is the 3rd eleven hours east",
        )
        assertEquals(
            LocalDate(2026, Month.MARCH, 17),
            row.endDate,
            "20:00 UTC on the 16th is the 17th eleven hours east",
        )
        // And the same sprint, read in UTC, is the day before. Stated as an equality so the
        // test fails loudly if the zone ever stops mattering.
        val utcModel = SprintListModel(project.id, repository, timeZone = TimeZone.UTC)
        utcModel.observe(eagerScope())
        advanceUntilIdle()
        assertEquals(LocalDate(2026, Month.MARCH, 16), utcModel.rows.single().endDate)
    }

    @Test
    fun `a chosen day is stored as the start of that day in the model's zone`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val eastEleven: TimeZone = FixedOffsetTimeZone(UtcOffset(hours = 11))
        val model = SprintEditorModel(
            projectID = project.id,
            existing = null,
            repository = repository,
            dateProvider = TestDateProvider(),
            timeZone = eastEleven,
        )
        model.onNameChanged("Sprint 1")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

        assertTrue(model.save())

        // 2 March at midnight, eleven hours east — which is 1 March at 13:00 UTC. Storing
        // the UTC midnight instead would make a user who picks the 2nd read the 1st back,
        // and that is the failure the zone parameter is here to prevent.
        assertEquals(
            Instant.parse("2026-03-01T13:00:00Z"),
            repository.sprints(project.id).single().startDate,
        )
    }

    @Test
    fun `the list's totals agree with the domain's own summary`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, goalStoryPoints = 10)
        repository.seed(sprint)
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-1",
                status = WorkItemStatus.Done,
                storyPoints = 4,
                sprintID = sprint.id,
            ),
        )
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-2",
                status = WorkItemStatus.InProgress,
                storyPoints = 6,
                sprintID = sprint.id,
            ),
        )

        val model = listModel(repository, project.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        // The list row and the sprint's own summary screen must never report different
        // progress for the same items, and the only thing holding them together is that both
        // read the domain's summariser rather than summing for themselves.
        val expected = sprintSummary(sprint, repository.workItems(project.id))
        val row = model.rows.single()
        assertEquals(expected.committedStoryPoints, row.committedStoryPoints)
        assertEquals(expected.achievedStoryPoints, row.achievedStoryPoints)
    }
}
