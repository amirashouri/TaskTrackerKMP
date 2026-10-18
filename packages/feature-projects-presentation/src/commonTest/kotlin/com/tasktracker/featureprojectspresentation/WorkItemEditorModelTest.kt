package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.AttachFile
import com.tasktracker.featureprojectsdomain.PickedFile
import com.tasktracker.featureprojectsdomain.WorkItemSaveOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The work item create/edit form.
 *
 * The test that earns its keep here is
 * [the key the project issues is read back rather than composed locally] - a fake that
 * numbered from 1 every time would make a naive implementation pass.
 *
 * ## No commas in a test name
 *
 * This class is `commonTest`, so it compiles for iOS too, and the Kotlin/Native backend
 * rejects a comma in a function name outright: `Name contains illegal characters: ","`. It
 * rejects the whole module, not just the one test, so a single comma here makes every iOS
 * test in this module uncompilable while `jvmTest` stays green — which is exactly how one got
 * in. Use "and", or split the sentence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkItemEditorModelTest {

    /** The identity the editor tests assign work to. */
    private val samUserID = UserID("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee")

    // MARK: Creating

    @Test
    fun `a new item starts untriaged and unsaveable`() = runTest {
        val model = newItem()

        assertTrue(model.isCreating)
        assertEquals(WorkItemStatus.Backlog, model.status)
        assertEquals(Priority.Medium, model.priority)
        assertEquals("", model.title)
        assertFalse(model.canSave)
    }

    @Test
    fun `a title makes a new item saveable`() = runTest {
        val model = newItem()
        model.onTitleChanged("Write the spec")
        assertTrue(model.canSave)
    }

    @Test
    fun `whitespace is not a title`() = runTest {
        val model = newItem()
        model.onTitleChanged("   ")
        assertFalse(model.canSave)
    }

    /**
     * The key the project issues is read back rather than composed locally.
     *
     * A key is `prefix-number`, and the number depends on what the project has already
     * handed out - so a new item in a project that is on its tenth task is `AP-11`, not
     * `AP-1`. The project is seeded at `nextItemNumber = 4` and the fake is told to issue
     * rank 7, so an implementation that computed the key as `prefix-1`, or that used the
     * project's counter rather than the one it was handed, would fail here.
     */
    @Test
    fun `the key the project issues is read back rather than composed locally`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject(keyPrefix = "AP", nextItemNumber = 4)
        repository.seed(project)
        repository.nextItemRank = 7

        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Write the spec")
        assertNotNull(model.save())

        assertEquals("AP-7", model.issuedKey?.toString())
        assertEquals("AP-7", repository.workItems(project.id).single().key.toString())
        // The project's counter moved past what it issued, so the next item cannot collide.
        assertEquals(8, repository.project(project.id)!!.nextItemNumber)
    }

    @Test
    fun `a created item reports the new id`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Write the spec")

        val outcome = model.save()

        val created = assertIs<WorkItemSaveOutcome.Created>(outcome)
        assertEquals(created.workItemID, model.savedWorkItemID)
    }

    /**
     * The add-task button's whole reason for carrying `initialSprintID` in the route: the
     * board shows only the active sprint's items, so a create that drops the sprint id saves
     * successfully into the store and is still invisible — the exact bug this seam exists
     * to prevent. The estimate rides the same write because the editor routes both at once.
     */
    @Test
    fun `a create seeded with the sprint id and estimate routes both into the saved item`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        val sprint = testSprint(projectID = project.id)
        repository.seed(project)
        repository.seed(sprint)

        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider(), null, sprint.id)
        model.onTitleChanged("Board lane task")
        model.onStoryPointsChanged("5")

        assertNotNull(model.save())

        val stored = repository.workItems(project.id).single()
        assertEquals(sprint.id, stored.sprintID, "a create from a sprint board must join that sprint")
        assertEquals(5, stored.storyPoints, "the estimate must not be dropped on the floor")
    }

    // MARK: Editing

    @Test
    fun `an existing item starts with its fields and no changes`() = runTest {
        val item = testWorkItem(
            projectID = testProject().id,
            title = "Ship it",
            details = "Notes",
            status = WorkItemStatus.InProgress,
            priority = Priority.High,
            assigneeID = samUserID,
        )
        val model = editing(item)

        assertFalse(model.isCreating)
        assertEquals("Ship it", model.title)
        assertEquals("Notes", model.details)
        assertEquals(WorkItemStatus.InProgress, model.status)
        assertEquals(Priority.High, model.priority)
        assertEquals(samUserID, model.assigneeID)
        assertEquals(item.key, model.issuedKey)
        assertFalse(model.hasChanges)
    }

    @Test
    fun `a field edit is a change`() = runTest {
        val model = editing(testWorkItem(projectID = testProject().id, title = "Ship it"))

        model.onTitleChanged("Ship it properly")

        assertTrue(model.hasChanges)
    }

    @Test
    fun `an unchanged field is not a change`() = runTest {
        val model = editing(testWorkItem(projectID = testProject().id, title = "Ship it", details = "Notes"))

        model.onTitleChanged("Ship it")
        model.onDetailsChanged("Notes")

        assertFalse(model.hasChanges)
    }

    /**
     * A status change and a field change are reported differently, because the view says so.
     *
     * A form that only edited a typo does not want a "moved to In Progress" confirmation,
     * and one that dragged a card into a new column does. `SaveWorkItem` decides which, and
     * this checks the model passes the answer through rather than flattening it.
     */
    @Test
    fun `a status change is reported as a move and a field edit is not`() = runTest {
        val project = testProject()
        val item = testWorkItem(projectID = project.id, status = WorkItemStatus.Todo)

        val moved = FakeProjectRepository().apply { seed(item) }
        val moving = editing(item, moved)
        moving.onStatusChanged(WorkItemStatus.InProgress)
        val movedOutcome = moving.save()
        assertIs<WorkItemSaveOutcome.Updated>(movedOutcome)
        assertTrue(movedOutcome.moved, "the card changed column, so the view should say so")

        val edited = FakeProjectRepository().apply { seed(item) }
        val editing_ = editing(item, edited)
        editing_.onTitleChanged("A better title")
        val editedOutcome = editing_.save()
        assertIs<WorkItemSaveOutcome.Updated>(editedOutcome)
        assertFalse(editedOutcome.moved, "a typo fix is not a move")
    }

    @Test
    fun `a status change is itself a change`() = runTest {
        val item = testWorkItem(projectID = testProject().id, status = WorkItemStatus.Todo)
        val model = editing(item)

        model.onStatusChanged(WorkItemStatus.InProgress)

        assertTrue(model.hasChanges)
    }

    // MARK: Terminal statuses

    /** Closed is reached by filing away, not by choosing it. */
    @Test
    fun `closed is not offered as a status to choose`() = runTest {
        val model = newItem()

        assertFalse(WorkItemStatus.Closed in model.selectableStatuses)
        assertTrue(WorkItemStatus.Backlog in model.selectableStatuses)
        assertTrue(WorkItemStatus.Done in model.selectableStatuses)
    }

    @Test
    fun `an item that has been filed away offers no statuses`() = runTest {
        val item = testWorkItem(projectID = testProject().id, status = WorkItemStatus.Closed)
        val model = editing(item)

        assertTrue(model.isFiledAway)
        assertEquals(emptyList(), model.selectableStatuses)
    }

    @Test
    fun `choosing a status that is already filed away is ignored`() = runTest {
        val item = testWorkItem(projectID = testProject().id, status = WorkItemStatus.Closed)
        val model = editing(item)

        model.onStatusChanged(WorkItemStatus.Closed)

        assertEquals(WorkItemStatus.Closed, model.status)
        assertFalse(model.hasChanges)
    }

    // MARK: File away

    @Test
    fun `filing away archives the item and marks it filed`() = runTest {
        val repository = FakeProjectRepository()
        val item = testWorkItem(projectID = testProject().id, status = WorkItemStatus.Todo)
        repository.seed(item)
        val model = editing(item, repository)

        assertTrue(model.fileAway())

        assertTrue(repository.writes.any { it == "moveWorkItem:Closed" })
        assertTrue(model.isFiledAway)
    }

    @Test
    fun `filing away an unsaved new item does nothing`() = runTest {
        val model = newItem()

        assertFalse(model.fileAway(), "there is no id to archive yet")
    }

    /**
     * Filing away something already filed away is a no-op, not a mistake to report.
     *
     * The form knows the item is closed, so it does not offer the action and a second press
     * is not something the user can get wrong. The backlog has no such knowledge - it will
     * accept any id - so it lets `ArchiveWorkItem` refuse and surfaces that wording. The
     * difference is what the surface knows, not a difference in policy.
     */
    @Test
    fun `filing away an item that is already filed away does nothing quietly`() = runTest {
        val repository = FakeProjectRepository()
        val item = testWorkItem(projectID = testProject().id, status = WorkItemStatus.Closed)
        repository.seed(item)
        val model = editing(item, repository)

        assertFalse(model.fileAway())
        assertNull(model.errorMessage, "a no-op is not a failure to report")
        assertEquals(emptyList(), repository.writes)
    }

    // MARK: Clearing fields

    /**
     * Clearing a field has to reach the store as a *clear*, not as an ignored write.
     *
     * `updateWorkItem` takes `Patch<UserID?>`, precisely because a nullable field cannot
     * otherwise tell "leave this alone" from "set this to nothing". A screen that sent the
     * assignee as `null` on save would silently keep the old assignee.
     */
    @Test
    fun `clearing the assignee reaches the store as a clear`() = runTest {
        val repository = FakeProjectRepository()
        val item = testWorkItem(projectID = testProject().id, assigneeID = samUserID)
        repository.seed(item)
        val model = editing(item, repository)

        model.onAssigneeChanged(null)
        assertNotNull(model.save())

        assertEquals(null, repository.workItem(item.id)?.assigneeID)
    }

    @Test
    fun `clearing the due date reaches the store as a clear`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(projectID = project.id, dueDate = Instant.parse("2026-04-01T00:00:00Z"))
        repository.seed(item)
        val model = editing(item, repository)

        model.onDueDateChanged(null)
        assertNotNull(model.save())

        assertNull(repository.workItem(item.id)?.dueDate)
    }

    @Test
    fun `setting a due date reaches the store`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        repository.seed(testWorkItem(projectID = project.id))
        val model = editing(repository.workItems(project.id).single(), repository)
        val due = Instant.parse("2026-05-05T00:00:00Z")

        model.onDueDateChanged(due)
        assertNotNull(model.save())

        assertEquals(due, repository.workItems(project.id).single().dueDate)
    }

    // MARK: Failure

    @Test
    fun `a failed save reports the failure and changes nothing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Write the spec")

        repository.failure = AppError.Offline

        assertNull(model.save())
        assertEquals(AppError.Offline.message, model.errorMessage)
        assertEquals(0, repository.writes.count { it == "createWorkItem" })
    }

    @Test
    fun `a failed save does not claim a key`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Write the spec")
        repository.failure = AppError.Offline

        model.save()

        // A key displayed for an item that was never created is a promise the store cannot
        // keep - the number may well have been taken by the retry.
        assertNull(model.issuedKey)
    }

    @Test
    fun `dismissing the error clears it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Write the spec")
        repository.failure = AppError.Offline
        model.save()
        assertNotNull(model.errorMessage)

        model.dismissError()
        assertNull(model.errorMessage)
    }

    // MARK: Dependencies

    /**
     * The blockers chosen in the picker are the ones the store ends up holding.
     *
     * This is the whole feature on the model side: a draft that collected ids and never
     * forwarded them would leave every pill a local-only lie, and this is the assertion that
     * would catch it.
     */
    @Test
    fun `selected blockers reach the store on save`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val blocker = testWorkItem(projectID = project.id, key = "AP-1", title = "Design the API")
        val item = testWorkItem(projectID = project.id, key = "AP-2", title = "Build it")
        repository.seed(blocker)
        repository.seed(item)
        val model = editing(item, repository)

        model.addBlockedBy(listOf(blocker.id))
        assertNotNull(model.save())

        assertEquals(listOf(blocker.id), repository.workItem(item.id)?.blockedByIDs)
    }

    /** A pill is added twice in the picker, and the list holds one pill. */
    @Test
    fun `a blocker selected twice is stored once`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val blocker = testWorkItem(projectID = project.id, key = "AP-1")
        val item = testWorkItem(projectID = project.id, key = "AP-2")
        repository.seed(blocker)
        repository.seed(item)
        val model = editing(item, repository)

        model.addBlockedBy(listOf(blocker.id))
        model.addBlockedBy(listOf(blocker.id))

        assertEquals(listOf(blocker.id), model.blockedByIDs)
        assertNotNull(model.save())
        assertEquals(1, repository.workItem(item.id)?.blockedByIDs?.size)
    }

    /** The picker excludes the open task, but the model screens it again in case it is not. */
    @Test
    fun `an item is never its own blocker`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(projectID = project.id, key = "AP-1")
        repository.seed(item)
        val model = editing(item, repository)

        model.addBlockedBy(listOf(item.id))

        assertEquals(emptyList(), model.blockedByIDs)
    }

    /** Removing the last pill saves an empty list rather than leaving the old one behind. */
    @Test
    fun `removing a blocker reaches the store as an empty list`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val blocker = testWorkItem(projectID = project.id, key = "AP-1")
        val item = testWorkItem(
            projectID = project.id,
            key = "AP-2",
            blockedByIDs = listOf(blocker.id),
        )
        repository.seed(blocker)
        repository.seed(item)
        val model = editing(item, repository)

        model.removeBlockedBy(blocker.id)
        assertNotNull(model.save())

        assertEquals(emptyList(), repository.workItem(item.id)?.blockedByIDs)
    }

    /**
     * A blocker edit is a change, so an untouched form is not.
     *
     * [WorkItemEditorModel.hasChanges] reads its `Draft`, and a field left out of that record
     * would be invisible to it — a user could add a blocker and cancel out with no warning.
     */
    @Test
    fun `a blocker edit counts as a change`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val blocker = testWorkItem(projectID = project.id, key = "AP-1")
        val item = testWorkItem(projectID = project.id, key = "AP-2")
        repository.seed(blocker)
        repository.seed(item)
        val model = editing(item, repository)
        assertFalse(model.hasChanges)

        model.addBlockedBy(listOf(blocker.id))

        assertTrue(model.hasChanges)
    }

    // MARK: Recurrence

    /**
     * A repeat edit is a change, so an untouched form is not.
     *
     * [WorkItemEditorModel.hasChanges] reads its `Draft`, and a field left out of that
     * record would be invisible to it — a user could set a repeat rule and cancel out
     * with no warning, and the rule would never be saved.
     */
    @Test
    fun `a repeat rule counts as a change`() = runTest {
        val model = editing(testWorkItem(projectID = testProject().id, title = "Ship it"))
        assertFalse(model.hasChanges)

        model.onRecurrenceChanged(Recurrence.Daily())

        assertTrue(model.hasChanges)
    }

    @Test
    fun `a chosen rule is saved with a new item`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Water the plants")
        model.onRecurrenceChanged(Recurrence.Daily())

        assertNotNull(model.save())

        assertEquals(
            Recurrence.Daily(),
            repository.workItems(project.id).single().recurrence,
            "the create path must carry the rule, not just the update path",
        )
    }

    @Test
    fun `clearing a rule reaches the store as a clear rather than a silence`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(
            projectID = project.id,
            title = "Water the plants",
            recurrence = Recurrence.Weekly(5),
        )
        repository.seed(item)
        val model = editing(item, repository)

        model.onRecurrenceChanged(null)
        assertNotNull(model.save())

        assertEquals(
            null,
            repository.workItem(item.id)?.recurrence,
            "the editor is the truth: clearing a repeat must clear the stored rule, not leave it",
        )
    }

    /**
     * Finishing a recurring task advances the series by a new card.
     *
     * The next instance is dated at the rule's next occurrence, carries the same rule,
     * and lands in Backlog; the finished card keeps its own status and due date, because
     * the series advances by a row rather than by rewriting one.
     */
    @Test
    fun `saving a recurring item into Done spawns the next instance`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(
            projectID = project.id,
            title = "Water the plants",
            status = WorkItemStatus.Todo,
            dueDate = Instant.parse("2026-01-16T12:00:00Z"),
            recurrence = Recurrence.Weekly(5),
        )
        repository.seed(item)
        val model = editing(item, repository)

        model.onStatusChanged(WorkItemStatus.Done)
        assertNotNull(model.save())

        val items = repository.workItems(project.id)
        assertEquals(2, items.size, "finishing a recurring task must create the next instance")
        val next = items.first { it.id != item.id }
        assertEquals(
            Instant.parse("2026-01-23T12:00:00Z"),
            next.dueDate,
            "weekly(Friday) from Fri 16 Jan noon should be Fri 23 Jan noon",
        )
        assertEquals(Recurrence.Weekly(5), next.recurrence, "the rule travels to the next instance")
        assertEquals(WorkItemStatus.Backlog, next.status)
        assertEquals(
            WorkItemStatus.Done,
            repository.workItem(item.id)?.status,
            "the finished instance stays finished with its own history",
        )
    }

    /**
     * Expansion is a transition, not a state.
     *
     * The original is the snapshot the form opened with, so re-editing a task that was
     * already Done — a typo in the title — must not spawn another instance per save.
     */
    @Test
    fun `re-editing an item that was already Done does not expand it again`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(
            projectID = project.id,
            title = "Water the plants",
            status = WorkItemStatus.Done,
            dueDate = Instant.parse("2026-01-16T12:00:00Z"),
            recurrence = Recurrence.Weekly(5),
        )
        repository.seed(item)
        val model = editing(item, repository)

        model.onTitleChanged("Water the plants properly")
        assertNotNull(model.save())

        assertEquals(
            1,
            repository.workItems(project.id).size,
            "an edit to an already-finished recurring task must not spawn another instance",
        )
    }

    @Test
    fun `saving a recurring item not into Done does not expand it`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(
            projectID = project.id,
            title = "Water the plants",
            status = WorkItemStatus.Todo,
            dueDate = Instant.parse("2026-01-16T12:00:00Z"),
            recurrence = Recurrence.Weekly(5),
        )
        repository.seed(item)
        val model = editing(item, repository)

        model.onStatusChanged(WorkItemStatus.InProgress)
        assertNotNull(model.save())

        assertEquals(
            1,
            repository.workItems(project.id).size,
            "moving a recurring card between open lanes must not advance the series",
        )
    }

    @Test
    fun `finishing a one-off does not spawn anything`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(
            projectID = project.id,
            title = "Ship the one thing",
            status = WorkItemStatus.Todo,
            dueDate = Instant.parse("2026-01-16T12:00:00Z"),
        )
        repository.seed(item)
        val model = editing(item, repository)

        model.onStatusChanged(WorkItemStatus.Done)
        assertNotNull(model.save())

        assertEquals(1, repository.workItems(project.id).size)
    }

    @Test
    fun `a create saved straight into Done expands exactly once`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val model = WorkItemEditorModel(project.id, null, repository, TestDateProvider())
        model.onTitleChanged("Daily standup")
        model.onRecurrenceChanged(Recurrence.Daily())
        model.onDueDateChanged(Instant.parse("2026-01-16T12:00:00Z"))
        model.onStatusChanged(WorkItemStatus.Done)

        assertNotNull(model.save())

        assertEquals(
            2,
            repository.workItems(project.id).size,
            "a brand-new recurring task saved straight into Done expands once, not never",
        )
    }

    /**
     * A failed expansion keeps the editor open on the card that was just saved.
     *
     * The save itself succeeded, so dismissing would leave a red banner nobody can read
     * and a series that silently did not advance. The create is failed while the update
     * is left alone — a sticky [FakeProjectRepository.failure] would take the save down
     * with it and test the wrong thing.
     */
    @Test
    fun `an expansion failure keeps the editor open with the banner readable`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(
            projectID = project.id,
            title = "Water the plants",
            status = WorkItemStatus.Todo,
            dueDate = Instant.parse("2026-01-16T12:00:00Z"),
            recurrence = Recurrence.Weekly(5),
        )
        repository.seed(item)
        repository.createFailure = AppError.Storage("the outbox is full")
        val model = editing(item, repository)

        model.onStatusChanged(WorkItemStatus.Done)
        val outcome = model.save()

        assertNull(outcome, "a failed expansion must keep the editor open")
        assertEquals(
            AppError.Storage("the outbox is full").message,
            model.errorMessage,
            "the reason the series did not advance must be on the banner, verbatim",
        )
        assertEquals(
            1,
            repository.workItems(project.id).size,
            "the save itself landed; only the next instance is missing",
        )
    }

    // MARK: Attachments

    /**
     * The attach flow's happy path: the picker's file lands in the store and the model's
     * list grows by exactly the row the write returned. Asserting on the store as well
     * as the list is what separates "the view was told" from "it is saved".
     */
    @Test
    fun `attaching a file appends the stored attachment`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        val model = editing(item, repository)

        model.attachFile(
            PickedFile(fileName = "report.txt", mimeType = "text/plain", bytes = byteArrayOf(1, 2)),
        )

        assertEquals(1, model.attachments.size)
        assertEquals("report.txt", model.attachments.single().fileName)
        assertEquals(1, repository.attachments(item.id).size)
        assertEquals(model.attachments.single(), repository.attachments(item.id).single())
        assertNull(model.errorMessage, "a successful attach leaves the banner clear")
    }

    @Test
    fun `a blank-name refusal lands in the error banner and stores nothing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        val model = editing(item, repository)

        model.attachFile(
            PickedFile(fileName = "   ", mimeType = "text/plain", bytes = byteArrayOf(1)),
        )

        assertTrue(model.attachments.isEmpty(), "a refusal must not grow the list")
        assertTrue(
            repository.attachments(item.id).isEmpty(),
            "a refusal must not reach the store",
        )
        assertNotNull(
            model.errorMessage,
            "a refusal the user watched must be on the banner, not swallowed",
        )
    }

    @Test
    fun `an empty-file refusal lands in the banner and stores nothing`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        val model = editing(item, repository)

        model.attachFile(
            PickedFile(fileName = "empty.txt", mimeType = "text/plain", bytes = byteArrayOf()),
        )

        assertTrue(model.attachments.isEmpty())
        assertTrue(repository.attachments(item.id).isEmpty())
        assertNotNull(model.errorMessage)
    }

    /**
     * The TooLarge banner names the measured size and the limit, because a "too large"
     * with no numbers is a message the user cannot act on, and a size whose text the
     * scenario asserts on byte-exactly is a measurement that fails when the cap moves.
     */
    @Test
    fun `a too-large refusal names the measured size and the limit on the banner`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        val model = editing(item, repository)
        val overCap = ByteArray((AttachFile.MAX_ATTACHMENT_BYTES + 1L).toInt())

        model.attachFile(
            PickedFile(fileName = "huge.bin", mimeType = "application/octet-stream", bytes = overCap),
        )

        assertTrue(model.attachments.isEmpty())
        assertTrue(repository.attachments(item.id).isEmpty())
        assertTrue(
            model.errorMessage?.contains("25.0 MB") == true,
            "the banner must name the limit, in the same words the list shows a size in",
        )
    }

    @Test
    fun `refreshing loads the attachments the store holds`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        repository.seed(
            Attachment(
                id = randomAttachmentID(),
                workItemID = item.id,
                fileName = "crash-report.txt",
                mimeType = "text/plain",
                sizeBytes = 42L,
                blobPath = "crash-report.blob",
                createdAt = Instant.fromEpochSeconds(1_700_000_000),
            ),
        )
        val model = editing(item, repository)

        model.refreshAttachments()

        assertEquals(1, model.attachments.size)
        assertEquals("crash-report.txt", model.attachments.single().fileName)
    }

    /**
     * The remove flow drops the row only when the store agreed. A model that dropped
     * first and the store that failed afterwards is the desync this ordering avoids.
     */
    @Test
    fun `removing an attachment drops it from the model and the store`() = runTest {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        val model = editing(item, repository)
        model.attachFile(
            PickedFile(fileName = "report.txt", mimeType = "text/plain", bytes = byteArrayOf(1)),
        )
        val stored = model.attachments.single()

        model.removeAttachment(stored.id)

        assertTrue(model.attachments.isEmpty())
        assertTrue(repository.attachments(item.id).isEmpty())
    }

    /**
     * A create has no id yet, and a file cannot be filed against a work item that does
     * not exist, so every attachment call on a create model is a no-op rather than a
     * crash or a stray write.
     */
    @Test
    fun `attachment calls on a create model are no-ops`() = runTest {
        val repository = FakeProjectRepository()
        val model = newItem(repository)

        model.attachFile(
            PickedFile(fileName = "report.txt", mimeType = "text/plain", bytes = byteArrayOf(1)),
        )
        model.refreshAttachments()
        model.removeAttachment(randomAttachmentID())

        assertTrue(model.attachments.isEmpty())
        assertTrue(repository.writes.isEmpty(), "a create must not write attachments")
        assertNull(model.errorMessage)
    }

    // MARK: Sync state

    /**
     * A graph with no file API is a supported build — the offline demo — and its rows must
     * not pretend to be queued. There is no server to queue for, so "waiting" there would be
     * a spinner that can never resolve, and "synced" would be a lie about a local file.
     */
    @Test
    fun `a file on a build with no server stays on this device`() = runTest {
        val (repository, item) = seededItem()
        repository.seed(pendingAttachment(item))
        val model = editing(item, repository)

        model.refreshAttachments()

        assertEquals(
            AttachmentSyncState.LocalOnly,
            model.syncStateOf(model.attachments.single()),
        )
    }

    @Test
    fun `a successful upload turns the row synced`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1, 2, 3))
        val transport = FakeAttachmentBlobTransport()
        val model = editing(item, repository, transport)

        model.refreshAttachments()

        assertEquals(listOf(attachment.id), transport.uploads)
        val row = model.attachments.single()
        assertEquals(AttachmentSyncState.Synced, model.syncStateOf(row))
        assertNotNull(row.blobRef, "a synced row must carry the server's key")
        assertNull(model.errorMessage, "a successful upload leaves the banner clear")
    }

    /**
     * The pair that decides the feature's honesty: a refusal is a verdict and a transport
     * failure is weather. They must be treated differently, and both must be *observable* —
     * one on the banner and the row, the other only in the retry count.
     */
    @Test
    fun `a refusal is remembered and explained and not retried`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val refusal = AppError.Validation("That file is larger than the 25 MB limit.")
        val transport = FakeAttachmentBlobTransport(failure = refusal)
        val model = editing(item, repository, transport)

        model.refreshAttachments()

        assertEquals(
            AttachmentSyncState.Failed,
            model.syncStateOf(model.attachments.single()),
            "a refused file must stop claiming it is on its way",
        )
        assertEquals(refusal.message, model.errorMessage, "a refusal must say why")

        model.refreshAttachments()

        assertEquals(1, transport.attempts, "a refusal is a verdict, not a delay")
    }

    @Test
    fun `a transient failure stays quiet and is retried`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val transport = FakeAttachmentBlobTransport(failure = AppError.Transport("offline"))
        val model = editing(item, repository, transport)

        model.refreshAttachments()

        assertEquals(
            AttachmentSyncState.Waiting,
            model.syncStateOf(model.attachments.single()),
            "a file still on its way is waiting, not failed",
        )
        assertNull(
            model.errorMessage,
            "a transient failure must not paint a banner over an attach the user watched succeed",
        )

        model.refreshAttachments()

        assertEquals(2, transport.attempts, "weather is retried")
    }

    // MARK: Opening

    /**
     * The press itself: the bytes this device holds, under the name and type the row carries.
     *
     * All three are asserted because all three decide what happens next — the name is the
     * file the receiving app shows, the type picks which app that is, and the bytes are the
     * file. A test that only compared bytes would pass while the platform opened the wrong
     * kind of thing.
     */
    @Test
    fun `opening a file hands the platform its bytes under the row's name and type`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1, 2, 3))
        val opener = FakeAttachmentOpener()
        val model = editing(item, repository, opener = opener)

        model.refreshAttachments()
        model.openAttachment(attachment.id)

        val open = opener.opens.single()
        assertEquals("report.txt", open.fileName)
        assertEquals("text/plain", open.mimeType)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(open.bytes))
        assertNull(model.errorMessage, "a successful open says nothing")
        assertFalse(model.isOpening(attachment.id), "the flag must come back down")
    }

    /**
     * A file another member attached has no bytes here, so the open has to fetch them — and
     * this is the one path where a press legitimately waits on the network.
     */
    @Test
    fun `opening a file this device does not hold fetches it first`() = runTest {
        val (repository, item) = seededItem()
        val remote = pendingAttachment(item).copy(blobRef = "server-key", blobPath = "")
        repository.seed(remote)
        val transport = FakeAttachmentBlobTransport(downloadBytes = byteArrayOf(9, 9))
        val opener = FakeAttachmentOpener()
        val model = editing(item, repository, transport, opener = opener)

        model.refreshAttachments()
        model.openAttachment(remote.id)

        assertTrue(
            byteArrayOf(9, 9).contentEquals(opener.opens.single().bytes),
            "the opened bytes must be the downloaded ones",
        )
    }

    /**
     * The silent case: the file is here as a row but its bytes are neither on this device nor
     * on the server yet. Nothing to open is not a failure, and the row's own caption already
     * says which state it is in.
     */
    @Test
    fun `opening a file with nothing to open says nothing`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item).copy(blobPath = "")
        repository.seed(attachment)
        val transport = FakeAttachmentBlobTransport()
        val opener = FakeAttachmentOpener()
        val model = editing(item, repository, transport, opener = opener)

        model.refreshAttachments()
        model.openAttachment(attachment.id)

        assertTrue(opener.opens.isEmpty(), "there were no bytes to hand over")
        assertNull(model.errorMessage, "an unavailable file is not an error")
    }

    @Test
    fun `a graph with no opener cannot open anything`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val model = editing(item, repository)

        model.refreshAttachments()
        model.openAttachment(attachment.id)

        assertNull(model.errorMessage, "a missing seam is not a failure of the press")
    }

    /**
     * A platform that cannot open the type is a real failure of a press the user made, so it
     * must reach the banner — silence there is indistinguishable from a broken row.
     */
    @Test
    fun `an open the platform refuses is reported`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val refusal = AppError.Validation("No app on this device can open report.txt.")
        val opener = FakeAttachmentOpener(failure = refusal)
        val model = editing(item, repository, opener = opener)

        model.refreshAttachments()
        model.openAttachment(attachment.id)

        assertEquals(refusal.message, model.errorMessage)
        assertEquals(1, opener.opens.size, "the attempt happened; the platform refused it")
        assertFalse(model.isOpening(attachment.id), "a failure must not leave the row opening")
    }

    /**
     * The re-entrancy guard, observed from inside the open it guards: a second press while
     * the first is still fetching must join it rather than start a second download.
     *
     * The hook runs inside the fake's `open`, which is after `openingIDs` was set and before
     * it is cleared — the only window in which the guard is doing anything.
     */
    @Test
    fun `a second press while the first is opening is ignored`() = runTest {
        val (repository, item) = seededItem()
        val attachment = pendingAttachment(item)
        repository.seed(attachment)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val opener = FakeAttachmentOpener()
        val model = editing(item, repository, opener = opener)
        var flagDuringOpen: Boolean? = null
        opener.onOpen = {
            flagDuringOpen = model.isOpening(attachment.id)
            model.openAttachment(attachment.id)
        }

        model.refreshAttachments()
        model.openAttachment(attachment.id)

        assertEquals(true, flagDuringOpen, "the row must read as opening while it opens")
        assertEquals(1, opener.opens.size, "a second press must not open the file twice")
    }

    // MARK: Helpers

    private fun newItem(repository: FakeProjectRepository = FakeProjectRepository()) =
        WorkItemEditorModel(testProject().id, null, repository, TestDateProvider())

    private fun editing(
        item: com.tasktracker.domain.WorkItem,
        repository: FakeProjectRepository = FakeProjectRepository(),
        transport: FakeAttachmentBlobTransport? = null,
        opener: FakeAttachmentOpener? = null,
    ) = WorkItemEditorModel(
        item.projectID,
        item,
        repository,
        TestDateProvider(),
        blobTransport = transport,
        opener = opener,
    )

    private suspend fun seededItem(): Pair<FakeProjectRepository, com.tasktracker.domain.WorkItem> {
        val repository = FakeProjectRepository()
        val project = testProject()
        repository.seed(project)
        val item = testWorkItem(project.id)
        repository.seed(item)
        return repository to item
    }

    /** A row as a real attach leaves it: scoped to its project, with no server key yet. */
    private fun pendingAttachment(item: com.tasktracker.domain.WorkItem) = Attachment(
        id = randomAttachmentID(),
        workItemID = item.id,
        projectID = item.projectID,
        fileName = "report.txt",
        mimeType = "text/plain",
        sizeBytes = 3L,
        blobPath = "report.blob",
        createdAt = Instant.fromEpochSeconds(1_700_000_000),
    )
}
