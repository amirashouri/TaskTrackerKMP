package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.UserID
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.projects
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository, as the port describes it.
 *
 * ## Why the outbox is asserted on every write
 *
 * A repository test that only checks what came back would pass with a repository that never
 * queues anything, and that is the failure the outbox exists to prevent: an edit saved
 * locally, reported as saved, and never pushed. So most of these tests assert on the queue as
 * well as the return value — the row count, the entity it names, and occasionally the payload
 * itself.
 *
 * The same applies to the announcements. A repository that changed nothing observable to a
 * screen is correct by the port's read methods and useless to the app, so the events are
 * asserted with the same weight as the rows.
 *
 * ## Where the instants come from
 *
 * `now` comes from the caller on every write, so [Moments] is the only clock in this file. That
 * is not tidiness: a store that read `Instant.now()` itself would make every assertion about
 * `updatedAt` a statement about the day the suite happened to run.
 */
class RoomProjectRepositoryTest {

    // MARK: Creating projects

    @Test
    fun aCreatedProjectIsStoredWithItsNameAndPrefixNormalised() = runTest {
        val harness = Harness()

        val project = harness.repository.createProject(
            name = "  Task Tracker  ",
            keyPrefix = "  tt  ",
            details = "  notes  ",
            now = Moments.start,
        )

        assertEquals("Task Tracker", project.name)
        assertEquals("TT", project.keyPrefix)
        assertEquals("notes", project.details)
        assertEquals(project, harness.repository.project(project.id))
    }

    @Test
    fun aCreatedProjectStartsItsCounterAtOneAndIsNotArchived() = runTest {
        val harness = Harness()

        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        // A counter below 1 would reissue a key that is already in use, so the floor is on
        // the value rather than trusted to this writer.
        assertEquals(1, project.nextItemNumber)
        assertTrue(!project.isArchived)
    }

    @Test
    fun aCreatedProjectQueuesExactlyOneUpsertCarryingTheProjectItself() = runTest {
        val harness = Harness()

        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val queued = harness.queued()
        assertEquals(1, queued.size)
        assertEquals("project", queued[0].entityKind)
        assertEquals(project.id.stringValue, queued[0].entityID)
        assertEquals("upsert", queued[0].operation)
        // The payload is the *domain value*, not the record: the server must not depend on
        // this module's column names, and adding a column must not be a wire change.
        assertEquals(project, harness.queuedProject())
    }

    @Test
    fun aCreatedProjectIsStampedWithTheSignedInUser() = runTest {
        val harness = Harness()
        val userID = harness.session.signIn()

        harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertEquals(userID, harness.queued()[0].userID)
    }

    @Test
    fun aProjectCreatedWithoutASessionIsQueuedWithNoAuthorRatherThanNotAtAll() = runTest {
        // A missing session is a real state — a cold launch before the session is restored —
        // and it costs the row its author, never the row itself.
        val harness = Harness()

        harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertNull(harness.queued()[0].userID)
    }

    @Test
    fun creatingAProjectAnnouncesTheProjectsOnlyAndScopesItToTheNewProject() = runTest {
        // One kind, not both. The original announced `.projects` *and* `.workItems` from all
        // seven of its write paths, which is the over-broadcast `ProjectChange` exists to
        // avoid: a board woken by a project rename re-reads every work item in it for nothing,
        // and a test cannot tell a board that woke for the right reason from one that woke by
        // accident. Only a create or a delete of a *work item* moves both aggregates.
        val harness = Harness()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertEquals(listOf(ProjectChange(ProjectChange.Kind.Projects, project.id)), seen)
    }

    @Test
    fun aProjectWithABlankNameIsRejectedBeforeAnythingIsWritten() = runTest {
        // Validation runs before the transaction, so a rejected edit cannot leave a queued
        // change for a project that was never created — which the server would reject
        // permanently and the queue would retry until it dead-lettered.
        val harness = Harness()

        assertFailsWith<AppError> {
            harness.repository.createProject("   ", "APO", "", Moments.start)
        }

        assertEquals(0, harness.queueDepth())
        assertTrue(harness.repository.isEmpty())
    }

    // MARK: Reading projects

    @Test
    fun projectsComeBackMostRecentlyTouchedFirst() = runTest {
        val harness = Harness()
        val first = harness.repository.createProject("First", "ONE", "", Moments.start)
        val second = harness.repository.createProject("Second", "TWO", "", Moments.later)

        assertEquals(listOf(second.id, first.id), harness.repository.projects(includeArchived = true).map { it.id })
    }

    @Test
    fun archivedProjectsAreHiddenUnlessAskedFor() = runTest {
        val harness = Harness()
        val kept = harness.repository.createProject("Kept", "ONE", "", Moments.start)
        val archived = harness.repository.createProject("Archived", "TWO", "", Moments.start)

        harness.repository.setArchived(archived.id, isArchived = true, now = Moments.later)

        assertEquals(listOf(kept.id), harness.repository.projects(includeArchived = false).map { it.id })
        assertEquals(2, harness.repository.projects(includeArchived = true).size)
    }

    @Test
    fun theDefaultProjectsOverloadHidesArchivedProjects() = runTest {
        val harness = Harness()
        val archived = harness.repository.createProject("Archived", "TWO", "", Moments.start)
        harness.repository.setArchived(archived.id, isArchived = true, now = Moments.later)

        assertEquals(emptyList(), harness.repository.projects())
    }

    @Test
    fun anUnknownProjectIsNullRatherThanAFailure() = runTest {
        // A read is a question, and "no" is a real answer to it.
        val harness = Harness()

        assertNull(harness.repository.project(randomProjectID()))
    }

    @Test
    fun isEmptyTracksWhetherAnythingHasBeenWritten() = runTest {
        val harness = Harness()

        assertTrue(harness.repository.isEmpty())

        harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertTrue(!harness.repository.isEmpty())
    }

    // MARK: Editing projects

    @Test
    fun anAbsentNameLeavesTheStoredOneAlone() = runTest {
        // `null` means "leave this field alone", never "set it to null" — neither field is
        // nullable, so there is nothing to clear.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "notes", Moments.start)

        val updated = harness.repository.updateProject(project.id, name = null, details = "other", now = Moments.later)

        assertEquals("Apollo", updated.name)
        assertEquals("other", updated.details)
    }

    @Test
    fun anEmptyStringIsHowNotesGetCleared() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "notes", Moments.start)

        val updated = harness.repository.updateProject(project.id, name = null, details = "", now = Moments.later)

        assertEquals("", updated.details)
    }

    @Test
    fun editingAProjectStampsTheNewInstantAndQueuesOneMoreUpsert() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val updated = harness.repository.updateProject(project.id, "Renamed", null, Moments.later)

        assertEquals(Moments.later, updated.updatedAt)
        assertEquals(Moments.start, updated.createdAt)
        assertEquals(2, harness.queueDepth())
        assertEquals(updated, harness.queuedProject(index = 1))
    }

    @Test
    fun projectMembersPersistAcrossCreateAndUpdate() = runTest {
        val harness = Harness()
        val ada = UserID("10000000-0000-4000-8000-000000000001")
        val grace = UserID("10000000-0000-4000-8000-000000000003")
        val project = harness.repository.createProject(
            "Apollo", "APO", "", Moments.start, memberIDs = listOf(ada),
        )
        assertEquals(listOf(ada), harness.repository.project(project.id)?.memberIDs)

        harness.repository.updateProject(
            project.id, name = null, details = null, now = Moments.later, memberIDs = listOf(grace),
        )

        assertEquals(listOf(grace), harness.repository.project(project.id)?.memberIDs)
    }

    @Test
    fun editingAProjectThatIsNotThereFailsRatherThanSilentlyDoingNothing() = runTest {
        val harness = Harness()

        val failure = assertFailsWith<AppError.Storage> {
            harness.repository.updateProject(randomProjectID(), "Renamed", null, Moments.later)
        }

        assertTrue(failure.message.orEmpty().contains("local store"))
    }

    @Test
    fun editingAProjectQueuesNothingWhenItFails() = runTest {
        val harness = Harness()

        runCatching { harness.repository.updateProject(randomProjectID(), "Renamed", null, Moments.later) }

        // A queued upsert for a row that does not exist is a change the server will reject
        // every time it is retried.
        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun archivingAndUnarchivingAreBothOrdinaryEdits() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val archived = harness.repository.setArchived(project.id, isArchived = true, now = Moments.later)
        assertTrue(archived.isArchived)

        val restored = harness.repository.setArchived(project.id, isArchived = false, now = Moments.muchLater)
        assertTrue(!restored.isArchived)
        // An archive is an edit, not a delete: the history and the items are still there, so
        // it still has to reach the other devices.
        assertEquals(3, harness.queueDepth())
    }

    // MARK: Deleting projects

    @Test
    fun deletingAProjectTakesItsWorkItemsAndHistoryWithIt() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            projectID = project.id,
            title = "One",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assignee = null,
            dueDate = null,
            now = Moments.start,
        )

        harness.repository.deleteProject(project.id)

        assertNull(harness.repository.project(project.id))
        assertEquals(emptyList(), harness.repository.workItems(project.id))
        assertEquals(emptyList(), harness.repository.statusChanges(item.id))
    }

    @Test
    fun deletingAProjectQueuesADeleteForItAndForEveryItemUnderIt() = runTest {
        // Queueing only the project would leave the server holding work items that nothing
        // local references and nothing will ever remove.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        repeat(2) { index ->
            harness.repository.createWorkItem(
                projectID = project.id,
                title = "Item $index",
                details = "",
                status = WorkItemStatus.Todo,
                priority = Priority.Medium,
                assignee = null,
                dueDate = null,
                now = Moments.start,
            )
        }

        harness.repository.deleteProject(project.id)

        val deletes = harness.queued().filter { it.operation == "delete" }
        assertEquals(3, deletes.size)
        assertEquals(1, deletes.count { it.entityKind == "project" })
        assertEquals(2, deletes.count { it.entityKind == "workItem" })
    }

    @Test
    fun aQueuedDeleteCarriesTheBareIdAsItsPayload() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        harness.repository.deleteProject(project.id)

        val delete = harness.queued().last { it.operation == "delete" }
        assertEquals(project.id.stringValue, delete.payload.decodeToString())
    }

    @Test
    fun deletingAProjectThatIsNotThereFails() = runTest {
        val harness = Harness()

        assertFailsWith<AppError.Storage> { harness.repository.deleteProject(randomProjectID()) }
    }

    @Test
    fun deletingAProjectAnnouncesItScopedSoAnOpenBoardWakesUp() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.deleteProject(project.id)

        assertEquals(2, seen.size)
        assertTrue(seen.all { it.projectID == project.id })
    }

    // MARK: Creating work items

    @Test
    fun aNewWorkItemIsIssuedTheProjectsNextKey() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val first = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )
        val second = harness.repository.createWorkItem(
            project.id, "Two", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        assertEquals("APO-1", first.key.rawValue)
        assertEquals("APO-2", second.key.rawValue)
    }

    @Test
    fun creatingAWorkItemAdvancesAndQueuesTheProjectsCounter() = runTest {
        // The server has to learn the counter moved, or a third device reissues the same key.
        // Two project rows, not one: an item that exists locally and a counter the server
        // never heard about is a duplicate waiting to happen.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        assertEquals(2, harness.repository.project(project.id)?.nextItemNumber)
        val projects = harness.queued().filter { it.entityKind == "project" }
        assertEquals(2, projects.size)
        // Compared as a set, not a list: both rows were written at this test's fixed instant,
        // so the queue orders them by a random id and a positional assertion here would be an
        // assertion about `Uuid.random()`. One payload says 1 (the create), the other says 2 —
        // and the second is the one a device that only ever received the first would get wrong.
        assertEquals(setOf(1, 2), projects.map { harness.decodeProject(it).nextItemNumber }.toSet())
        assertEquals(1, harness.queued().count { it.entityKind == "workItem" })
    }

    @Test
    fun aCreatedWorkItemIsStoredWithItsTextNormalised() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val item = harness.repository.createWorkItem(
            project.id,
            title = "  Write it  ",
            details = "  thoroughly  ",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assignee = "  Ada  ",
            dueDate = Moments.day,
            now = Moments.start,
        )

        assertEquals("Write it", item.title)
        assertEquals("thoroughly", item.details)
        assertEquals("Ada", item.assignee)
        assertEquals(Moments.day, item.dueDate)
        assertEquals(item, harness.repository.workItem(item.id))
    }

    @Test
    fun aWorkItemCreatedStraightIntoDoneIsDatedImmediately() = runTest {
        // Otherwise the archive holds an item it cannot file under any month, and the seeded
        // sample board's finished column is invisible.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val item = harness.repository.createWorkItem(
            project.id, "Done on arrival", "", WorkItemStatus.Done, Priority.Medium, null, null, Moments.start,
        )

        assertEquals(Moments.start, item.resolvedAt)
        assertEquals(Moments.start, harness.repository.workItem(item.id)?.resolvedAt)
    }

    @Test
    fun aNewWorkItemLandsAtTheTopOfItsColumn() = runTest {
        // The most recent thing is usually the thing somebody just wrote down. A new card at
        // the bottom of a lane is a card nobody finds.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val existing = harness.repository.createWorkItem(
            project.id, "Older", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        val fresh = harness.repository.createWorkItem(
            project.id, "Newer", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.later,
        )

        assertTrue(fresh.rank < existing.rank)
        assertEquals(
            listOf("Newer", "Older"),
            harness.repository.workItems(project.id).map { it.title },
        )
    }

    @Test
    fun creatingAWorkItemUnderAProjectThatIsNotThereFails() = runTest {
        val harness = Harness()

        assertFailsWith<AppError.Storage> {
            harness.repository.createWorkItem(
                randomProjectID(), "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
            )
        }
    }

    @Test
    fun creatingAWorkItemWithABlankTitleWritesNothing() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertFailsWith<AppError> {
            harness.repository.createWorkItem(
                project.id, "  ", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
            )
        }

        // The counter must not have moved either: a rejected edit that still advanced the
        // counter would skip a key number for no reason.
        assertEquals(1, harness.repository.project(project.id)?.nextItemNumber)
        assertEquals(1, harness.queueDepth())
    }

    // MARK: Editing work items

    @Test
    fun anUnmentionedFieldIsLeftAloneRatherThanCleared() = runTest {
        // The failure this whole `Patch` type exists to prevent: a caller clearing an
        // assignee passes `null`, a plain `String?` reads it as "no instruction", and the
        // field silently keeps its old value. The edit reports as saved and the value is
        // wrong.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, "Ada", Moments.day, Moments.start,
        )

        val untouched = harness.repository.updateWorkItem(
            id = item.id,
            title = "Renamed",
            details = null,
            priority = null,
            assignee = Patch.Unmentioned,
            dueDate = Patch.Unmentioned,
            now = Moments.later,
        )

        assertEquals("Renamed", untouched.title)
        assertEquals("Ada", untouched.assignee)
        assertEquals(Moments.day, untouched.dueDate)
    }

    @Test
    fun aPatchOfNullIsAnInstructionToClear() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, "Ada", Moments.day, Moments.start,
        )

        val cleared = harness.repository.updateWorkItem(
            id = item.id,
            title = null,
            details = null,
            priority = null,
            assignee = Patch.Of(null),
            dueDate = Patch.Of(null),
            now = Moments.later,
        )

        assertNull(cleared.assignee)
        assertNull(cleared.dueDate)
    }

    @Test
    fun editingAWorkItemChangesItsPriority() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        val updated = harness.repository.updateWorkItem(
            item.id, null, null, Priority.Critical, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
        )

        assertEquals(Priority.Critical, updated.priority)
        assertEquals(Priority.Critical, harness.repository.workItem(item.id)?.priority)
    }

    @Test
    fun editingTheTitleOfAFinishedTaskDoesNotPullItOutOfTheArchive() = runTest {
        // The trap: this path cannot change a status, so calling `reconcileResolutionAt` "just
        // in case" would clear the date of a non-terminal-looking row and the task would vanish
        // from the archive on the next read.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "Shipped", "", WorkItemStatus.Done, Priority.Medium, null, null, Moments.start,
        )

        val edited = harness.repository.updateWorkItem(
            item.id, "Shipped, with a note", null, null, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
        )

        assertEquals(WorkItemStatus.Done, edited.status)
        assertEquals(Moments.start, edited.resolvedAt)
    }

    @Test
    fun editingAWorkItemRecordsNoHistory() = runTest {
        // A title is not a status change. A history row claiming one would fill the editor's
        // list with transitions that never happened.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )
        val before = harness.repository.statusChanges(item.id).size

        harness.repository.updateWorkItem(
            item.id, "Renamed", null, null, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
        )

        assertEquals(before, harness.repository.statusChanges(item.id).size)
    }

    @Test
    fun editingAWorkItemThatIsNotThereFails() = runTest {
        val harness = Harness()
        val id = parseWorkItemID("22222222-2222-4222-8222-222222222222")!!

        assertFailsWith<AppError.Storage> {
            harness.repository.updateWorkItem(
                id, null, null, null, Patch.Unmentioned, Patch.Unmentioned, Moments.start,
            )
        }
    }

    @Test
    fun editingAWorkItemAnnouncesTheProjectItBelongsToRatherThanTheItems() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.updateWorkItem(
            item.id, "Renamed", null, null, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
        )

        // A work item write is one kind only. A project announcement here would be harmless
        // but wrong, and the kind is what lets a test assert a board woke up for the right
        // reason.
        assertEquals(listOf(ProjectChange(ProjectChange.Kind.WorkItems, project.id)), seen)
    }

    // MARK: Moving work items

    @Test
    fun movingAWorkItemIntoATerminalColumnDatesItsResolution() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        val moved = harness.repository.moveWorkItem(item.id, WorkItemStatus.Done, rank = null, now = Moments.later)

        assertEquals(WorkItemStatus.Done, moved.status)
        assertEquals(Moments.later, moved.resolvedAt)
        assertEquals(Moments.later, harness.repository.workItem(item.id)?.resolvedAt)
    }

    @Test
    fun reopeningAWorkItemClearsItsResolutionSoItLeavesTheArchive() = runTest {
        // A stale `resolvedAt` on a reopened task files it in the archive forever, and nothing
        // about that looks like a bug on the board.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Done, Priority.Medium, null, null, Moments.start,
        )

        val reopened = harness.repository.moveWorkItem(item.id, WorkItemStatus.Todo, rank = null, now = Moments.later)

        assertNull(reopened.resolvedAt)
        assertNull(harness.repository.workItem(item.id)?.resolvedAt)
    }

    @Test
    fun movingAWorkItemWithNoRankLandsAtTheBottomOfTheDestinationColumn() = runTest {
        // The port's contract, and a correction rather than a port: the original put it at the
        // *top*, from the same `minRank` helper it used for a new card — one helper serving two
        // opposite instructions, so every drag reordered the lane around the card that came in.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val first = harness.repository.createWorkItem(
            project.id, "First", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )
        val second = harness.repository.createWorkItem(
            project.id, "Second", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        harness.repository.moveWorkItem(first.id, WorkItemStatus.Done, rank = null, now = Moments.later)
        harness.repository.moveWorkItem(second.id, WorkItemStatus.Done, rank = null, now = Moments.later)

        val done = harness.repository.workItems(project.id).filter { it.status == WorkItemStatus.Done }
        assertEquals(listOf("First", "Second"), done.map { it.title })
    }

    @Test
    fun movingAWorkItemToAnExplicitRankPutsItThere() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        val moved = harness.repository.moveWorkItem(item.id, WorkItemStatus.InProgress, rank = 42, now = Moments.later)

        assertEquals(42, moved.rank)
    }

    @Test
    fun deletingAWorkItemRemovesItAndItsHistoryAndQueuesADelete() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        harness.repository.deleteWorkItem(item.id)

        assertNull(harness.repository.workItem(item.id))
        assertEquals(emptyList(), harness.repository.statusChanges(item.id))
        val delete = harness.queued().last { it.operation == "delete" }
        assertEquals("workItem", delete.entityKind)
        assertEquals(item.id.stringValue, delete.entityID)
    }

    @Test
    fun deletingAWorkItemAnnouncesTheProjectReadBeforeTheRowWent() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.deleteWorkItem(item.id)

        // Read inside the transaction, because afterwards there is nothing left to ask which
        // project this belonged to.
        assertEquals(listOf(ProjectChange(ProjectChange.Kind.WorkItems, project.id)), seen)
    }

    @Test
    fun deletingAWorkItemThatIsNotThereFails() = runTest {
        val harness = Harness()

        assertFailsWith<AppError.Storage> {
            harness.repository.deleteWorkItem(parseWorkItemID("22222222-2222-4222-8222-222222222222")!!)
        }
    }

    // MARK: Reading work items

    @Test
    fun workItemsComeBackInTheBoardsOwnOrder() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val low = harness.repository.createWorkItem(
            project.id, "Low", "", WorkItemStatus.Todo, Priority.Lowest, null, null, Moments.start,
        )
        val high = harness.repository.createWorkItem(
            project.id, "High", "", WorkItemStatus.Todo, Priority.Critical, null, null, Moments.start,
        )

        // Both were created into an empty column, so their ranks differ by one and rank wins.
        // Forcing a tie is what exercises the rest of the key.
        val forced = high.copy(rank = low.rank)
        assertEquals(listOf("High", "Low"), listOf(forced, low).sortedBy { it.orderingKey }.map { it.title })
        assertEquals(
            listOf("High", "Low"),
            harness.repository.workItems(project.id).map { it.title },
        )
    }

    @Test
    fun workItemsAreScopedToTheirProject() = runTest {
        val harness = Harness()
        val first = harness.repository.createProject("First", "ONE", "", Moments.start)
        val second = harness.repository.createProject("Second", "TWO", "", Moments.start)
        harness.repository.createWorkItem(
            first.id, "Mine", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        assertEquals(1, harness.repository.workItems(first.id).size)
        assertEquals(0, harness.repository.workItems(second.id).size)
    }

    @Test
    fun countsAreGroupedByStatusInOnePass() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        repeat(2) {
            harness.repository.createWorkItem(
                project.id, "Todo $it", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
            )
        }
        harness.repository.createWorkItem(
            project.id, "Done", "", WorkItemStatus.Done, Priority.Medium, null, null, Moments.start,
        )

        val counts = harness.repository.counts(project.id)

        assertEquals(2, counts[WorkItemStatus.Todo])
        assertEquals(1, counts[WorkItemStatus.Done])
        // A status with nothing in it is absent rather than zero, so "no items" and "no such
        // column" cannot be confused by a caller that sums the map.
        assertNull(counts[WorkItemStatus.Backlog])
    }

    @Test
    fun countsForAProjectWithNoItemsAreEmpty() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertEquals(emptyMap(), harness.repository.counts(project.id))
    }

    @Test
    fun theChangesStreamIsTheBusItself() = runTest {
        val harness = Harness()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.repository.changes(), seen)

        harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertEquals(1, seen.size)
    }

    @Test
    fun aStoredRowWithAMalformedProjectIsInvisibleRatherThanFatal() = runTest {
        // A hand-written row, as a migration or a bad sync would leave. One unreadable row must
        // not empty a board.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val good = harness.repository.createWorkItem(
            project.id, "Good", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )
        harness.database.workItemDao().insert(
            aWorkItem(id = parseWorkItemID("33333333-3333-4333-8333-333333333333")!!).toRecord()
                .copy(projectID = "not-a-uuid"),
        )

        val items = harness.repository.workItems(project.id)

        assertEquals(listOf(good.id), items.map { it.id })
    }

    @Test
    fun aProjectRowWithAMalformedIdIsSkippedWhenListingProjects() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        harness.database.projectDao().insert(aProject().toRecord().copy(id = "not-a-uuid"))

        assertEquals(listOf(project.id), harness.repository.projects(includeArchived = true).map { it.id })
        assertNotNull(parseProjectID(project.id.stringValue))
    }
}
