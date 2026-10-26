package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
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
 *
 * ## The lock tests assert what refused, and then what still went out
 *
 * `windowLocked` is one boolean, and a test that only read it would pass on a model that
 * *reported* the lock and ignored it. So the locked scenarios call every frozen setter,
 * assert the draft did not move, then save and assert the stored sprint did not move
 * either — while the fields that are supposed to stay editable are changed and asserted as
 * written. Both halves in one test, because a model that froze everything (including the
 * description) would pass the first half alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SprintEditorModelTest {

    private val zone: TimeZone = TimeZone.UTC

    /** The identity the editor tests pick as owner, distinct from [testUserID]. */
    private val graceUserID = UserID("77777777-7777-4777-8777-777777777777")

    private fun editorModel(
        repository: com.tasktracker.featureprojectsdomain.ProjectRepositoryPort,
        projectID: com.tasktracker.core.ProjectID,
        existing: com.tasktracker.domain.Sprint? = null,
        projectSprints: List<com.tasktracker.domain.Sprint> = emptyList(),
    ) = SprintEditorModel(
        projectID = projectID,
        existing = existing,
        projectSprints = projectSprints,
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

        model.onGoalStoryPointsChanged("  ")
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
        model.onGoalStoryPointsChanged("0")
        assertEquals(0, model.goalStoryPoints)    }

    @Test
    fun `a negative or half-typed goal is dropped rather than stored`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)

        model.onGoalStoryPointsChanged("-3")
        assertNull(model.goalStoryPoints, "negative")
        model.onGoalStoryPointsChanged("1x")
        assertNull(model.goalStoryPoints, "not a number")
        model.onGoalStoryPointsChanged("12")
        assertEquals(12, model.goalStoryPoints)
    }

    @Test
    fun `creating a sprint stores it in the zone the model was given`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("  Sprint 7  ")
        model.onGoalStoryPointsChanged("12")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))
        model.onOwnerChanged(graceUserID)

        assertTrue(model.save())

        val sprint = repository.sprints(project.id).single()
        // Trimmed, because the use case normalises on the way in — an editor that stored the
        // raw draft would put `"  Sprint 7  "` in a list row's title.
        assertEquals("Sprint 7", sprint.name)
        assertEquals(12, sprint.goalStoryPoints)
        assertEquals(graceUserID, sprint.ownerID)
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
        val sprint = testSprint(project.id, ownerID = testUserID)
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        assertTrue(model.hasChanges.not())

        model.onOwnerChanged(null)
        assertTrue(model.hasChanges)
        assertTrue(model.save())

        // The whole reason `Patch` exists: an unassigned field means "set it to nothing", and
        // a plain nullable argument would have read as "leave it alone" and silently kept
        // the previous owner.
        assertNull(repository.sprints(project.id).single().ownerID)
    }

    @Test
    fun `an unchanged edit is not a change`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id, name = "Sprint 3", goalStoryPoints = 7, ownerID = graceUserID)

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
            ownerID = graceUserID,
        )
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        model.onNameChanged("Sprint 4")
        assertTrue(model.save())

        val updated = repository.sprints(project.id).single()
        assertEquals("Sprint 4", updated.name)
        assertEquals(7, updated.goalStoryPoints)
        assertEquals(graceUserID, updated.ownerID)
        assertEquals(sprint.startDate, updated.startDate)
        assertEquals(sprint.endDate, updated.endDate)
        assertEquals("updateSprint", repository.writes.single())
    }

    @Test
    fun `a window over an unfinished sibling is blocked and named`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        // The default fixture window is 2–16 March, exactly the draft's below.
        val sibling = testSprint(project.id, name = "Sprint 3")
        val model = editorModel(repository, project.id, projectSprints = listOf(sibling))
        model.onNameChanged("Sprint 4")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

        assertEquals(
            "Sprint 3",
            model.blockedBySprintName,
            "the sibling's name, so the message can say which sprint to finish",
        )
        assertFalse(model.canSave, "a blocked window is not saveable")
    }

    @Test
    fun `a finished sibling never blocks`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val finished = testSprint(project.id, name = "Sprint 2", isFinished = true)
        val model = editorModel(repository, project.id, projectSprints = listOf(finished))
        model.onNameChanged("Sprint 4")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

        assertNull(model.blockedBySprintName, "history is not a timetable")
        assertTrue(model.canSave)
    }

    @Test
    fun `the sprint being edited is not its own blocker`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val existing = testSprint(project.id, name = "Sprint 1")
        val model = editorModel(
            repository,
            project.id,
            existing = existing,
            projectSprints = listOf(existing),
        )

        // The draft starts on the existing window, so an overlap that counted the sprint
        // against itself would block every edit that did not move the dates.
        model.onNameChanged("Sprint 1")
        assertNull(model.blockedBySprintName)

        model.onGoalStoryPointsChanged("12")
        assertTrue(model.canSave, "renaming one's own sprint is not a conflict")
    }

    @Test
    fun `moving the window clear of a sibling unblocks the save`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sibling = testSprint(project.id, name = "Sprint 3")
        val model = editorModel(repository, project.id, projectSprints = listOf(sibling))
        model.onNameChanged("Sprint 4")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))
        assertEquals("Sprint 3", model.blockedBySprintName)

        // 17 March, not 16: the 16th is the sibling's *end*, and `isActiveOn` is inclusive,
        // so a sprint starting on it would share that day's first instant. The next whole
        // day is the first window that does not overlap.
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 17))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 30))

        assertNull(model.blockedBySprintName)
        assertTrue(model.canSave)
    }

    @Test
    fun `a backwards window is not also reported as an overlap`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sibling = testSprint(project.id, name = "Sprint 3")
        val model = editorModel(repository, project.id, projectSprints = listOf(sibling))
        model.onNameChanged("Sprint 4")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 16))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 2))

        // One complaint per draft: `endIsBeforeStart` covers this shape, and reporting both
        // would put two errors under one field.
        assertTrue(model.endIsBeforeStart!!)
        assertNull(model.blockedBySprintName)
        assertFalse(model.canSave)
    }

    @Test
    fun `a refused save reports the failure instead of throwing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("Sprint 4")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))

        // The store's own overlap refusal, which `canSave` cannot see and only a race or
        // another device produces — the sibling set this editor was handed is a snapshot.
        // It arrives as the banner, not a crash.
        repository.failure = AppError.Validation("'Sprint 3' overlaps this window.")

        assertFalse(model.save())
        assertEquals("'Sprint 3' overlaps this window.", model.errorMessage)
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

    // MARK: The goal in prose

    @Test
    fun `creating stores the prose goal trimmed`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val model = editorModel(repository, project.id)
        model.onNameChanged("Sprint 1")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 16))
        model.onGoalChanged("  Ship the migration  ")

        assertTrue(model.save())

        // Trimmed like the name: the use case normalises on the way in, and a sentence
        // stored with the draft's whitespace would show in the detail card exactly as typed.
        assertEquals("Ship the migration", repository.sprints(project.id).single().goal)
    }

    @Test
    fun `clearing the prose goal stores no goal`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id, goal = "Ship the migration")
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        assertTrue(model.hasChanges.not())

        model.onGoalChanged("   ")
        // An emptied field is the user clearing it, not leaving it — the same resolution
        // the owner field makes, for the same reason: the draft started as the original's
        // value, so a blank here can only mean it was emptied.
        assertTrue(model.hasChanges)
        assertTrue(model.save())

        assertNull(repository.sprints(project.id).single().goal)
    }

    @Test
    fun `an untouched prose goal is left alone rather than written over`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id, goal = "Set at creation")
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        // Another device rewrites the sentence after this editor opened. The draft
        // snapshotted the original, so from here the field looks untouched — and a save
        // that sent `Patch.Of(null)` instead of `Patch.Unmentioned` would erase that write
        // as a side effect of renaming.
        sprint.goal = "Set from another device"

        model.onNameChanged("Sprint 1 (revised)")
        assertTrue(model.save())

        val stored = repository.sprints(project.id).single()
        assertEquals("Sprint 1 (revised)", stored.name)
        assertEquals("Set from another device", stored.goal)
    }

    // MARK: The window lock

    @Test
    fun `a locked sprint freezes the cap and the window but not what it is about`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id, goal = "Ship the migration", isLocked = true)
        repository.seed(sprint)

        val model = editorModel(repository, project.id, existing = sprint)
        assertTrue(model.windowLocked)

        model.onGoalStoryPointsChanged("99")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 9))
        model.onEndDateChanged(LocalDate(2026, Month.MARCH, 10))
        assertEquals(10, model.goalStoryPoints, "the cap is what the lock protects")
        assertEquals(LocalDate(2026, Month.MARCH, 2), model.startDate)
        assertEquals(LocalDate(2026, Month.MARCH, 16), model.endDate)

        // The parts that describe rather than commit still take the edit...
        model.onNameChanged("Sprint 1 (revised)")
        model.onGoalChanged("Ship the migration, quietly")
        model.onOwnerChanged(graceUserID)
        assertTrue(model.hasChanges, "a locked window is not an uneditable sprint")
        assertTrue(model.save())

        val stored = repository.sprints(project.id).single()
        assertEquals("Sprint 1 (revised)", stored.name)
        assertEquals("Ship the migration, quietly", stored.goal)
        assertEquals(graceUserID, stored.ownerID)
        // ...and nothing frozen moved in the write either: the setters refusing is the
        // model's half of the guarantee, and this is that the save is the other half.
        assertEquals(10, stored.goalStoryPoints)
        assertEquals(sprint.startDate, stored.startDate)
        assertEquals(sprint.endDate, stored.endDate)
    }

    @Test
    // No comma in this name: the iOS source set is compiled by Kotlin/Native, which rejects
    // a backticked name containing one ("Name contains illegal characters") and fails the
    // whole test compilation, so the suite cannot run at all.
    fun `the lock comes from the stored sprint and not from the calendar`() {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(project.id)

        val unlocked = editorModel(repository, project.id, existing = sprint)
        assertFalse(unlocked.windowLocked, "an unlocked sprint accepts window edits")

        val locked = editorModel(
            repository,
            project.id,
            existing = sprint.copy(isLocked = true),
        )
        assertTrue(locked.windowLocked, "the owner's lock is what freezes the window")

        // A finished sprint is history whether or not anyone locked it, so the old
        // start-date rule's answer for it is kept rather than handed back with the lock.
        val finished = editorModel(
            repository,
            project.id,
            existing = testSprint(project.id, isFinished = true),
        )
        assertTrue(finished.windowLocked, "a closed sprint's window stays frozen")
    }

    @Test
    fun `creating is never locked`() {
        val repository = FakeProjectRepository()
        val project = testProject()

        val model = editorModel(repository, project.id)
        assertFalse(
            model.windowLocked,
            "a sprint nobody has written yet has no lock to honour",
        )

        model.onGoalStoryPointsChanged("99")
        model.onStartDateChanged(LocalDate(2026, Month.MARCH, 2))
        assertEquals(99, model.goalStoryPoints)
        assertEquals(LocalDate(2026, Month.MARCH, 2), model.startDate)
    }
}
