package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The sprint editor: what it will save, and what it sends.
 *
 * ## The assertions are about the draft and about the wire, not about the form
 *
 * A model test that only checked `canSave` would pass on an editor that saved nothing, and
 * one that only checked the store would pass on an editor that let the user save nonsense.
 * So most of these read `FakeProjectRepository.writes` — which records which port method
 * was reached — rather than only the resulting state, and one reads the *arguments*, which
 * is the only way to see a field dropped on the way to the port.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SprintEditorModelTest {

    private val zone: TimeZone = TimeZone.UTC

    private fun editorModel(
        repository: com.tasktracker.featureprojectsdomain.ProjectRepositoryPort,
        projectID: com.tasktracker.core.ProjectID,
        existing: com.tasktracker.domain.Sprint? = null,
    ) = SprintEditorModel(
        projectID = projectID,
        existing = existing,
        repository = repository,
        dateProvider = TestDateProvider(),
        timeZone = zone,
    )

    @Test
    fun `a new sprint needs a name and both dates`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)

        assertFalse(model.canSave)

        model.onNameChanged("Sprint 1")
        assertFalse(model.canSave, "still no window")

        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        assertFalse(model.canSave, "still no end")

        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))
        assertTrue(model.canSave)
    }

    @Test
    fun `a one-day sprint is not backwards`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("Sprint 1")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 2))

        // Inclusive on both ends, matching `Sprint.isActiveOn`. A strict comparison here
        // would make the shortest possible sprint unsaveable.
        assertFalse(model.endIsBeforeStart!!)
        assertTrue(model.canSave)

        assertTrue(model.save())
    }

    @Test
    fun `an end before the start blocks the save before anything is written`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("Sprint 1")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 16))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 2))

        assertTrue(model.endIsBeforeStart!!)
        assertFalse(model.canSave)
        assertFalse(model.save())
        // The point of checking `canSave` in the model rather than in the store: a refused
        // edit that reaches the store has already opened a transaction.
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a half-chosen window is not reported as backwards`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)

        // `null`, not `false`: an error under a field the user has not filled in yet is a
        // complaint about the form rather than about the draft.
        assertNull(model.endIsBeforeStart)
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        assertNull(model.endIsBeforeStart)
    }

    @Test
    fun `a blank goal is no goal rather than zero`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("Sprint 1")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

        model.onGoalChanged("  ")
        assertNull(model.goalStoryPoints)

        assertTrue(model.save())
        val sprint = repository.sprints(project.id).single()
        // The store floors rather than holds a null, so a goal of nothing is stored as 0 —
        // which is exactly why the *draft* has to distinguish them.
        assertEquals(0, sprint.goalStoryPoints)
    }

    @Test
    fun `a goal of zero is kept as zero`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)

        // `0` means somebody said zero, which is a real answer. Blank means nobody said.
        model.onGoalChanged("0")
        assertEquals(0, model.goalStoryPoints)    }

    @Test
    fun `a negative or half-typed goal is dropped rather than stored`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)

        model.onGoalChanged("-3")
        assertNull(model.goalStoryPoints, "negative")
        model.onGoalChanged("1x")
        assertNull(model.goalStoryPoints, "not a number")
        model.onGoalChanged("12")
        assertEquals(12, model.goalStoryPoints)
    }

    @Test
    fun `creating a sprint stores it in the zone the model was given`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("  Sprint 7  ")
        model.onGoalChanged("12")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))
        model.onOwnerChanged("  Grace  ")

        assertTrue(model.save())

        val sprint = repository.sprints(project.id).single()
        // Trimmed, because the use case normalises on the way in — an editor that stored the
        // raw draft would put `"  Sprint 7  "` in a list row's title.
        assertEquals("Sprint 7", sprint.name)
        assertEquals(12, sprint.goalStoryPoints)
        assertEquals("Grace", sprint.owner)
        // The first moment of the chosen day, which is what the user chose. See
        // `sprintLocalDate`'s KDoc for why the end is not rounded up to the day's end.
        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), sprint.startDate)
        assertEquals(Instant.parse("2026-03-16T00:00:00Z"), sprint.endDate)
        assertEquals("createSprint", repository.writes.single())
    }

    @Test
    fun `clearing the owner hands the sprint to nobody`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id, owner = "Amir")
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        assertTrue(model.hasChanges.not())

        model.onOwnerChanged("")
        assertTrue(model.hasChanges)
        assertTrue(model.save())

        // The whole reason `Patch` exists: an empty field means "set it to nothing", and a
        // plain nullable argument would have read as "leave it alone" and silently kept
        // "Amir".
        assertNull(repository.sprints(project.id).single().owner)
    }

    @Test
    fun `an unchanged edit is not a change`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id, name = "Sprint 3", goalStoryPoints = 7, owner = "Grace")

        val model = editorModel(repository, project.id, existing = sprint)
        assertFalse(model.hasChanges)
        assertFalse(model.canSave, "an edit with nothing to save cannot be saved")

        // Re-picking the same day must not count, even though the stored instant was not at
        // midnight in a general case: the draft and the original are compared as LocalDates.
        model.onStartDateChanged(sprintLocalDate(sprint.startDate, zone))
        assertFalse(model.hasChanges)
    }

    @Test
    fun `editing one field leaves the others alone`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(
            projectID = project.id,
            name = "Sprint 3",
            goalStoryPoints = 7,
            startDate = Instant.parse("2026-03-02T00:00:00Z"),
            endDate = Instant.parse("2026-03-16T00:00:00Z"),
            owner = "Grace",
        )
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        model.onNameChanged("Sprint 4")
        assertTrue(model.save())

        val updated = repository.sprints(project.id).single()
        assertEquals("Sprint 4", updated.name)
        assertEquals(7, updated.goalStoryPoints)
        assertEquals("Grace", updated.owner)
        assertEquals(sprint.startDate, updated.startDate)
        assertEquals(sprint.endDate, updated.endDate)
        assertEquals("updateSprint", repository.writes.single())
    }

    @Test
    fun `a refused save reports the failure instead of throwing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("Sprint 4")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

        // The store's own one-open-sprint refusal, which `canSave` cannot see and only a
        // race or another device produces. It arrives as the banner, not a crash.
        repository.failure = AppError.Validation("'Sprint 3' is still open.")

        assertFalse(model.save())
        assertEquals("'Sprint 3' is still open.", model.errorMessage)
        assertTrue(repository.sprints(project.id).isEmpty())
    }

    @Test
    fun `a failure that is not an AppError is reported as unknown rather than thrown`() =
        runTest {
            val repository = ThrowingProjectRepository()
            val project = testProject()
            val model = editorModel(repository, project.id)
            model.onNameChanged("Sprint 4")
            model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
            model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

            assertFalse(model.save())
            assertNotNull(model.errorMessage)
            assertTrue(
                model.errorMessage!!.contains("IllegalStateException"),
                "the type is named so the failure is diagnosable from the banner, got: " +
                    model.errorMessage,
            )
        }
}
