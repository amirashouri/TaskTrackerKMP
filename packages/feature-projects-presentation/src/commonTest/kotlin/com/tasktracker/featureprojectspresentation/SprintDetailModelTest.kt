package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * One sprint's screen: what it says about progress, and who may close it.
 *
 * ## The owner rule is the tested part, not the arithmetic
 *
 * The totals are the domain's, so there is nothing here to re-derive and re-implement in a
 * test. What this file is actually about is [SprintDetail.canFinish] — the three states it
 * has to tell apart (open and owned by you, open and owned by somebody else, open and
 * unowned), because a rule with three outcomes is a rule where the *default* is usually
 * written and the exceptions forgotten.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SprintDetailModelTest {

    private val zone: TimeZone = TimeZone.UTC

    private fun detailModel(
        repository: FakeProjectRepository,
        sprintID: com.tasktracker.core.SprintID,
        currentUserName: String? = "Amir",
    ) = SprintDetailModel(
        sprintID = sprintID,
        repository = repository,
        dateProvider = TestDateProvider(),
        currentUserName = currentUserName,
        timeZone = zone,
    )

    @Test
    fun `a sprint that does not exist reads as no detail rather than a blank one`() = runTest {
        val repository = FakeProjectRepository()
        val model = detailModel(repository, com.tasktracker.core.randomSprintID())
        model.observe(eagerScope())
        advanceUntilIdle()

        // `null` and not a `SprintDetail` with empty fields: a screen cannot tell a deleted
        // sprint from an empty one unless the value says so.
        assertNull(model.detail)
    }

    @Test
    fun `the detail's dates are the days the sprint was stored on`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertEquals(LocalDate(2026, Month.MARCH, 2), detail.startDate)
        assertEquals(LocalDate(2026, Month.MARCH, 16), detail.endDate)
    }

    @Test
    fun `the summary is the domain's own value for the sprint's items`() = runTest {
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
                storyPoints = 6,
                sprintID = sprint.id,
            ),
        )
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-2",
                status = WorkItemStatus.InProgress,
                storyPoints = 4,
                sprintID = sprint.id,
            ),
        )
        repository.seed(
            // A different sprint's done task. If the summary were built from the project's
            // whole item list without filtering, this would push the achieved figure from
            // 6 to 21 and the sprint would appear to have beaten a goal of 10 by twice.
            testWorkItem(
                projectID = project.id,
                key = "AP-3",
                status = WorkItemStatus.Done,
                storyPoints = 15,
                sprintID = testSprint(project.id).id,
            ),
        )

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertEquals(10, detail.summary.committedStoryPoints)
        assertEquals(6, detail.summary.achievedStoryPoints)
        // And the model's value is *the same value* the domain computes, not a copy: the
        // summary screen renders this one, so a second implementation could only disagree
        // with it if it existed.
        assertEquals(sprintSummary(sprint, repository.sprintWorkItems(sprint.id)), detail.summary)
    }

    @Test
    fun `a closed task is reported but contributes no points`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, goalStoryPoints = 10)
        repository.seed(sprint)
        repository.seed(
            testWorkItem(
                projectID = project.id,
                key = "AP-1",
                status = WorkItemStatus.Closed,
                storyPoints = 8,
                sprintID = sprint.id,
            ),
        )

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val summary = model.detail!!.summary
        // Under *done* — it left the flow, so it is no longer outstanding work — but worth
        // nothing, because nothing was delivered. See `SprintGoal.achievedStoryPoints`.
        assertEquals(1, summary.done.size)
        assertEquals(8, summary.committedStoryPoints)
        assertEquals(0, summary.achievedStoryPoints)
        assertFalse(summary.didHitGoal!!, "a closed task cannot beat the goal")
    }

    @Test
    fun `the owner can finish their own open sprint`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir")
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = "Amir")
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertTrue(detail.isViewerTheOwner)
        assertTrue(detail.canFinish)
    }

    @Test
    fun `nobody else can finish somebody else's sprint`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir")
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = "Grace")
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertFalse(detail.isViewerTheOwner)
        assertFalse(detail.canFinish)
        // The note the screen shows instead of a button, so the state is legible rather
        // than a control that is mysteriously absent.
        assertTrue(detail.hasOwner)
    }

    @Test
    fun `an unowned sprint can be finished by anyone`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = null)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = "Grace")
        model.observe(eagerScope())
        advanceUntilIdle()

        // `owner == null` means nobody has claimed it, not that it is locked. Refusing here
        // would leave a sprint nobody is ever able to close.
        val detail = model.detail!!
        assertFalse(detail.hasOwner)
        assertFalse(detail.isViewerTheOwner)
        assertTrue(detail.canFinish)
    }

    @Test
    fun `a viewer the app cannot identify is not shown to be the owner`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir")
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = null)
        model.observe(eagerScope())
        advanceUntilIdle()

        // Not the owner: the alternative puts a Finish button on screen for an identity the
        // app has not established, and the store refuses the press anyway.
        assertFalse(model.detail!!.canFinish)
    }

    @Test
    fun `a finished sprint cannot be finished again`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir", isFinished = true)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = "Amir")
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.detail!!.canFinish)
    }

    @Test
    fun `finishing dates the sprint and reports success`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir")
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = "Amir")
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.finish())
        assertEquals("finishSprint", repository.writes.single())

        // The store dates it rather than taking the date from the caller, so the model
        // cannot write "finished" without one.
        assertTrue(repository.sprints(project.id).single().isFinished)
        advanceUntilIdle()
        assertFalse(model.detail!!.canFinish)
    }

    @Test
    fun `a refused finish reports the failure rather than throwing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir")
        repository.seed(sprint)
        repository.failure = AppError.Validation("'Sprint 7' has already been finished.")

        val model = detailModel(repository, sprint.id, currentUserName = "Amir")
        model.observe(eagerScope())
        advanceUntilIdle()
        repository.failure = AppError.Validation("'Sprint 7' has already been finished.")

        assertFalse(model.finish())
        assertEquals("'Sprint 7' has already been finished.", model.errorMessage)
        assertFalse(model.isBusy, "the wait is cleared even on failure")
    }

    @Test
    fun `reopening a sprint puts it back in the open`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, owner = "Amir", isFinished = true)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id, currentUserName = "Amir")
        model.observe(eagerScope())
        advanceUntilIdle()

        assertTrue(model.reopen())
        assertEquals("reopenSprint", repository.writes.single())
        advanceUntilIdle()
        assertTrue(model.detail!!.canFinish)
    }

    @Test
    fun `an open sprint has no summary screen to open`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, isFinished = false)
        repository.seed(sprint)
        repository.seed(
            testWorkItem(projectID = project.id, key = "AP-1", storyPoints = 3, sprintID = sprint.id),
        )

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        // An open sprint still *has* a summary value — the detail screen shows the same two
        // numbers — but there is no retrospective to open.
        assertFalse(model.detail!!.summary.isEmpty)
        assertFalse(model.detail!!.hasSummaryToShow)
    }

    @Test
    fun `a finished sprint holding nothing has no summary to open`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, goalStoryPoints = 10, isFinished = true)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertTrue(detail.summary.isEmpty)
        // It *did* miss its goal — 0 against 10 — and that verdict is separate from whether
        // there is anything to list. A screen that showed the empty message instead of the
        // verdict would be hiding a real answer.
        assertEquals(false, detail.summary.didHitGoal)
        assertFalse(detail.hasSummaryToShow)
    }

    @Test
    fun `a sprint with no goal has no verdict to report`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        // Goal 0, so `SprintGoal.didHitGoal` returns `null` — the third state, and the one a
        // two-way rendering would have to invent an answer for.
        val sprint = testSprint(project.id, goalStoryPoints = 0, isFinished = true)
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

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val summary = model.detail!!.summary
        assertEquals(4, summary.achievedStoryPoints)
        assertNull(summary.didHitGoal, "no goal means no verdict, not a miss")
    }

    // MARK: Membership
    //
    // `TestDateProvider()` ticks at 1_760_000_000 (October 2025) and the fixture sprint's
    // window is March 2026, so the default sprint here is *planned* — the one state in
    // which membership may be edited. Every locking test moves the window deliberately,
    // and a fixture that happened to be active would make its own green depend on the day
    // it ran.

    /** The fixture sprint's window, shifted to straddle [now] so the sprint is active. */
    private fun activeWindow(): Pair<Instant, Instant> =
        Instant.fromEpochSeconds(1_760_000_000 - 86_400) to Instant.fromEpochSeconds(1_760_000_000 + 86_400)

    @Test
    fun `a planned sprint accepts task edits`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertFalse(detail.isActive)
        assertTrue(detail.canEditItems)
    }

    @Test
    fun `an active sprint locks its task membership`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val (start, end) = activeWindow()
        val sprint = testSprint(project.id, startDate = start, endDate = end)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        val detail = model.detail!!
        assertTrue(detail.isActive)
        assertFalse(detail.canEditItems)
        // The sheet does not even open, rather than opening and refusing to confirm: a
        // gate the screen enforced by remembering to disable a button would be one this
        // model could forget for its next caller.
        model.openPicker()
        assertFalse(model.isPickerOpen)
    }

    @Test
    fun `a finished sprint locks its task membership`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id, isFinished = true)
        repository.seed(sprint)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        assertFalse(model.detail!!.canEditItems)
        model.openPicker()
        assertFalse(model.isPickerOpen)
    }

    @Test
    fun `the picker offers only tasks that belong to no sprint and are not archived`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)
        // Free agent — the only one of the four that may be offered.
        repository.seed(testWorkItem(projectID = project.id, key = "AP-1"))
        // In this sprint already: re-offering it would be a no-op dressed as a choice.
        repository.seed(testWorkItem(projectID = project.id, key = "AP-2", sprintID = sprint.id))
        // Archived: closed work is not committed to a new window.
        repository.seed(testWorkItem(projectID = project.id, key = "AP-3", status = WorkItemStatus.Closed))
        // Another sprint's task. Offering it would move it out of a window whose own
        // membership lock is checked only against *that* sprint — a door locked from the
        // other side.
        repository.seed(
            testWorkItem(projectID = project.id, key = "AP-4", sprintID = testSprint(project.id).id),
        )

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        model.openPicker()
        assertEquals(listOf("AP-1"), model.pickableItems.map { it.key.toString() })
    }

    @Test
    fun `ticking a task and confirming moves it in and closes the sheet`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)
        val item = testWorkItem(projectID = project.id, key = "AP-1")
        repository.seed(item)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        model.openPicker()
        assertFalse(model.canAddSelected, "nothing ticked means nothing to write")
        model.togglePickable(item.id)
        assertTrue(model.canAddSelected)

        assertTrue(model.addSelected())
        assertEquals(sprint.id, repository.workItem(item.id)!!.sprintID)
        assertFalse(model.isPickerOpen, "a confirmed add closes the sheet")
        assertTrue(model.selectedPickables.isEmpty())
        assertFalse(model.isBusy, "the wait is cleared on success too")
        // The write went through the port's one membership call, so a future refactor that
        // spelled it as a plain `updateWorkItem` would not pass unnoticed.
        assertTrue("setWorkItemSprint" in repository.writes)

        // The change event reloaded: the added task is now a sprint item and no longer a
        // candidate, which is the same fact read from both sides.
        advanceUntilIdle()
        assertEquals(listOf(item.id), model.detail!!.items.map { it.id })
        assertTrue(model.pickableItems.isEmpty())
    }

    @Test
    fun `unticking the last task makes the sheet unable to confirm`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)
        val item = testWorkItem(projectID = project.id, key = "AP-1")
        repository.seed(item)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()

        model.openPicker()
        model.togglePickable(item.id)
        model.togglePickable(item.id)
        assertFalse(model.canAddSelected)
        assertFalse(model.addSelected(), "an empty selection writes nothing")
        assertTrue(model.isPickerOpen, "nothing was refused, so the sheet stays up")
        assertNull(repository.workItem(item.id)!!.sprintID)
    }

    @Test
    fun `removing a task sends it back to the backlog`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)
        val item = testWorkItem(projectID = project.id, key = "AP-1", sprintID = sprint.id)
        repository.seed(item)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()
        assertEquals(1, model.detail!!.items.size)

        assertTrue(model.removeItem(item.id))

        assertNull(repository.workItem(item.id)!!.sprintID)
        // And the screen sees it: the change event reloaded, so the row is gone from the
        // sprint's own list rather than linger until something else pokes the model.
        advanceUntilIdle()
        assertTrue(model.detail!!.items.isEmpty())
        assertFalse(model.isBusy, "the wait is cleared even on success")
    }

    @Test
    fun `a refused membership write reports the banner and keeps the sheet open`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val sprint = testSprint(project.id)
        repository.seed(sprint)
        val item = testWorkItem(projectID = project.id, key = "AP-1")
        repository.seed(item)

        val model = detailModel(repository, sprint.id)
        model.observe(eagerScope())
        advanceUntilIdle()
        model.openPicker()
        model.togglePickable(item.id)
        repository.failure = AppError.Validation("'Sprint 1' is in progress, so its tasks cannot be changed.")

        assertFalse(model.addSelected())
        // Verbatim: `errorMessage` is `AppError.message`, never a model's own wording.
        assertEquals("'Sprint 1' is in progress, so its tasks cannot be changed.", model.errorMessage)
        assertTrue(model.isPickerOpen, "a refusal keeps the sheet up so the choice survives")
        assertTrue(model.canAddSelected, "and keeps the ticks")
        assertFalse(model.isBusy, "the wait is cleared even on failure")
        assertNull(repository.workItem(item.id)!!.sprintID)
    }
}
