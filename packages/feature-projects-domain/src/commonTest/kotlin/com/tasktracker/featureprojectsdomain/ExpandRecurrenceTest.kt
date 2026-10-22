package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.randomTagID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The expansion decision: one card stays finished, one new card carries the series.
 *
 * What is worth testing here is the *refusal* paths and the anchor — the data layer
 * queues the outbox row for whatever this returns, so a wrong next date is a wrong
 * row on the server, and "ended series" returning a card would be a card nobody asked
 * for.
 */
class ExpandRecurrenceTest {

    // Friday 2026-01-16 noon UTC.
    private val anchor = Instant.parse("2026-01-16T12:00:00Z")
    private val now = Instant.parse("2026-01-17T09:00:00Z")
    private val utc = TimeZone.UTC
    private val adaID = UserID("33333333-3333-4333-8333-333333333333")

    private suspend fun harness(): Triple<FakeProjectRepository, ExpandRecurrence, com.tasktracker.core.ProjectID> {
        val repository = FakeProjectRepository()
        // The fake refuses an item under a project that does not exist — correctly —
        // so the project has to be seeded before any item can be.
        val projectID = repository.seedProject().id
        return Triple(repository, ExpandRecurrence(repository, TestDateProvider(now)), projectID)
    }

    private suspend fun FakeProjectRepository.seedItem(
        projectID: com.tasktracker.core.ProjectID,
        recurrence: Recurrence?,
        dueDate: Instant?,
        title: String = "Water the plants",
        status: WorkItemStatus = WorkItemStatus.Done,
    ) = createWorkItem(
        projectID = projectID,
        title = title,
        details = "From the office window",
        status = status,
        priority = Priority.Medium,
        assigneeID = adaID,
        dueDate = dueDate,
        storyPoints = 3,
        recurrence = recurrence,
        now = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun aWeeklySeriesCreatesTheNextFridayLeavingTheFinishedOneAlone() = runTest {
        val (repository, expand, projectID) = harness()
        val weekly = Recurrence.Weekly(DayOfWeek.FRIDAY.isoDayNumber)
        val finished = repository.seedItem(projectID, weekly, dueDate = anchor)

        val next = assertNotNull(expand(finished.id, utc))

        assertEquals(
            Instant.parse("2026-01-23T12:00:00Z"),
            next.dueDate,
            "weekly(Friday) from Fri 16 Jan noon should be Fri 23 Jan noon",
        )
        assertEquals(finished.title, next.title)
        assertEquals(finished.priority, next.priority)
        assertEquals(finished.assigneeID, next.assigneeID)
        assertEquals(finished.storyPoints, next.storyPoints)
        assertEquals(weekly, next.recurrence, "the series rule travels to the next instance")
        assertEquals(
            WorkItemStatus.Backlog,
            next.status,
            "the next instance is a fresh backlog card, not a copy of the finished status",
        )
        assertNull(next.sprintID, "the next instance must not silently refill a sprint")
        assertNotEquals(finished.id, next.id, "the next instance is a new card, not the old one")
        assertEquals(
            finished.id,
            repository.workItem(finished.id)?.id,
            "the finished instance must still exist after expansion",
        )
        assertEquals(
            WorkItemStatus.Done,
            repository.workItem(finished.id)?.status,
            "the finished instance must keep its status",
        )
        assertEquals(
            anchor,
            repository.workItem(finished.id)?.dueDate,
            "the finished instance's due date must not slide",
        )
    }

    @Test
    fun theNextInstanceKeepsTheSeriesTags() = runTest {
        // A tag is a property of the work being repeated, not of one occurrence. Dropping it
        // would make every weekly chore silently lose its labels after the first run.
        val (repository, expand, projectID) = harness()
        val finished = repository.seedItem(projectID, Recurrence.Daily(), dueDate = anchor)
        finished.tagIDs = listOf(randomTagID(), randomTagID())

        val next = assertNotNull(expand(finished.id, utc))

        assertEquals(finished.tagIDs, next.tagIDs)
    }

    @Test
    fun theNextInstanceGetsAFreshKeyRatherThanTheFinishedOnes() = runTest {
        val (repository, expand, projectID) = harness()
        val finished = repository.seedItem(projectID, Recurrence.Daily(), dueDate = anchor)

        val next = assertNotNull(expand(finished.id, utc))

        assertNotEquals(
            finished.key,
            next.key,
            "the next instance must carry a new key; both were ${finished.key.rawValue}",
        )
    }

    @Test
    fun anItemWithNoRuleExpandsToNothing() = runTest {
        val (repository, expand, projectID) = harness()
        val oneOff = repository.seedItem(projectID, recurrence = null, dueDate = anchor)

        assertNull(expand(oneOff.id, utc), "a one-off has no next instance")
        assertEquals(1, repository.itemCount(), "expanding a one-off must not create a row")
    }

    @Test
    fun anItemWithNoDueDateExpandsToNothingRatherThanGuessingASchedule() = runTest {
        val (repository, expand, projectID) = harness()
        val undated = repository.seedItem(projectID, Recurrence.Daily(), dueDate = null)

        assertNull(expand(undated.id, utc), "without an anchor date there is nothing to step from")
        assertEquals(1, repository.itemCount())
    }

    @Test
    fun aSeriesPastItsUntilExpandsToNothing() = runTest {
        val (repository, expand, projectID) = harness()
        val ending = Instant.parse("2026-01-20T12:00:00Z")
        val last = repository.seedItem(
            projectID,
            Recurrence.Daily(until = ending),
            dueDate = ending,
        )

        assertNull(
            expand(last.id, utc),
            "the occurrence after `until` must end the series, not create a card",
        )
        assertEquals(1, repository.itemCount())
    }

    @Test
    fun anOccurrenceLandingExactlyOnUntilStillExpands() = runTest {
        val (repository, expand, projectID) = harness()
        val ending = Instant.parse("2026-01-23T12:00:00Z")
        val item = repository.seedItem(
            projectID,
            Recurrence.Weekly(DayOfWeek.FRIDAY.isoDayNumber, until = ending),
            dueDate = anchor,
        )

        val next = assertNotNull(expand(item.id, utc), "until is inclusive; this occurrence is on it")
        assertEquals(ending, next.dueDate)
    }

    @Test
    fun expandingAWorkItemThatVanishedIsAValidationErrorNotACrash() = runTest {
        val (repository, expand, _) = harness()

        assertFailsWith<AppError> { expand(com.tasktracker.core.randomWorkItemID(), utc) }
    }
}
