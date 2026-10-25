package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomTagID
import com.tasktracker.core.randomUserID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.projects
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

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
    fun aCreatedProjectNamesItsCreatorAsAMemberEvenWhenNoneWerePicked() = runTest {
        // The server authorizes a project it does not already have against the payload's own
        // member list, because a project that does not exist has no membership rows to check.
        // So a project created without its creator in that list is not merely unauthorized —
        // it is permanently unsyncable: the server answers `Rejected`, and the queue
        // dead-letters the change instead of retrying it.
        val harness = Harness()
        val userID = harness.session.signIn("10000000-0000-4000-8000-0000000000aa")

        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertEquals(listOf(UserID(userID)), project.memberIDs)
        // The payload is what the server actually reads, so this is the assertion that
        // matters: a stored member list that never reached the wire would fix nothing.
        assertEquals(listOf(UserID(userID)), harness.queuedProject().memberIDs)
    }

    @Test
    fun aCreatedProjectKeepsTheMembersItWasGivenAndAppendsTheCreatorOnce() = runTest {
        val harness = Harness()
        val userID = harness.session.signIn("10000000-0000-4000-8000-0000000000bb")
        val ada = UserID("10000000-0000-4000-8000-000000000001")
        val grace = UserID("10000000-0000-4000-8000-000000000003")

        val project = harness.repository.createProject(
            "Apollo", "APO", "", Moments.start, memberIDs = listOf(ada, grace),
        )

        // Appended, not prepended: the order the user arranged their members in is theirs.
        assertEquals(listOf(ada, grace, UserID(userID)), project.memberIDs)

        // And ticking yourself in the picker is not a second membership.
        val alsoMine = harness.repository.createProject(
            "Borealis", "BOR", "", Moments.later, memberIDs = listOf(ada, UserID(userID)),
        )

        assertEquals(listOf(ada, UserID(userID)), alsoMine.memberIDs)
    }

    @Test
    fun aProjectCreatedWithoutASessionAddsNoCreatorToItsMembers() = runTest {
        // There is nobody to name, and an unauthenticated store cannot push, so there is no
        // payload for the rule to protect. A placeholder would be a fabricated member.
        val harness = Harness()

        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertEquals(emptyList(), project.memberIDs)
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
            assigneeID = null,
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
                assigneeID = null,
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

        // Kinds rather than a count, because the count moved and the move is the point:
        // deleting a project now takes its sprints with it, so a Sprints list has to wake
        // up too or it shows a sprint for a project that no longer exists. This test caught
        // the change on its own — it asserted `2` and got `3`.
        assertEquals(
            listOf(
                ProjectChange.Kind.Projects,
                ProjectChange.Kind.WorkItems,
                ProjectChange.Kind.Sprints,
            ),
            seen.map { it.kind },
        )
        assertTrue(seen.all { it.projectID == project.id })
    }

    // MARK: Creating work items

    @Test
    fun aNewWorkItemIsIssuedTheProjectsNextKey() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val first = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        val second = harness.repository.createWorkItem(
            project.id, "Two", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
            assigneeID = adaUserID,
            dueDate = Moments.day,
            now = Moments.start,
        )

        assertEquals("Write it", item.title)
        assertEquals("thoroughly", item.details)
        assertEquals(adaUserID, item.assigneeID)
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
            project.id, "Done on arrival", "", WorkItemStatus.Done, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "Older", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )

        val fresh = harness.repository.createWorkItem(
            project.id, "Newer", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.later,
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
                randomProjectID(), "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
            )
        }
    }

    @Test
    fun creatingAWorkItemWithABlankTitleWritesNothing() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        assertFailsWith<AppError> {
            harness.repository.createWorkItem(
                project.id, "  ", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
            )
        }

        // The counter must not have moved either: a rejected edit that still advanced the
        // counter would skip a key number for no reason.
        assertEquals(1, harness.repository.project(project.id)?.nextItemNumber)
        assertEquals(1, harness.queueDepth())
    }

    // MARK: Editing work items

    @Test
    fun aCreatedWorkItemKeepsItsTagsInOrderAndCapsThemAtFive() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val ids = List(7) { randomTagID() }

        val item = harness.repository.createWorkItem(
            projectID = project.id,
            title = "Tagged",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            tagIDs = ids + ids.first(),
            now = Moments.start,
        )

        assertEquals(ids.take(5), item.tagIDs, "the store enforces the cap as well as the use case")
        assertEquals(ids.take(5), harness.repository.workItem(item.id)?.tagIDs)
    }

    @Test
    fun anUnmentionedTagPatchLeavesTheSelectionAlone() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val ids = listOf(randomTagID(), randomTagID())
        val item = harness.repository.createWorkItem(
            projectID = project.id,
            title = "Tagged",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            tagIDs = ids,
            now = Moments.start,
        )

        val renamed = harness.repository.updateWorkItem(
            id = item.id,
            title = "Renamed",
            details = null,
            priority = null,
            assigneeID = Patch.Unmentioned,
            dueDate = Patch.Unmentioned,
            storyPoints = Patch.Unmentioned,
            now = Moments.later,
        )

        assertEquals(ids, renamed.tagIDs)
    }

    @Test
    fun anEmptyTagPatchClearsTheSelection() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            projectID = project.id,
            title = "Tagged",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            tagIDs = listOf(randomTagID()),
            now = Moments.start,
        )

        val cleared = harness.repository.updateWorkItem(
            id = item.id,
            title = null,
            details = null,
            priority = null,
            assigneeID = Patch.Unmentioned,
            dueDate = Patch.Unmentioned,
            storyPoints = Patch.Unmentioned,
            tagIDs = Patch.Of(emptyList()),
            now = Moments.later,
        )

        assertEquals(emptyList(), cleared.tagIDs)
        assertEquals(emptyList(), harness.repository.workItem(item.id)?.tagIDs)
    }

    @Test
    fun anUnmentionedFieldIsLeftAloneRatherThanCleared() = runTest {
        // The failure this whole `Patch` type exists to prevent: a caller clearing an
        // assignee passes `null`, a plain nullable reads it as "no instruction", and the
        // field silently keeps its old value. The edit reports as saved and the value is
        // wrong.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, adaUserID, Moments.day, null, null, Moments.start,
        )

        val untouched = harness.repository.updateWorkItem(
            id = item.id,
            title = "Renamed",
            details = null,
            priority = null,
            assigneeID = Patch.Unmentioned,
            dueDate = Patch.Unmentioned,
            storyPoints = Patch.Unmentioned,
            now = Moments.later,
        )

        assertEquals("Renamed", untouched.title)
        assertEquals(adaUserID, untouched.assigneeID)
        assertEquals(Moments.day, untouched.dueDate)
    }

    @Test
    fun aPatchOfNullIsAnInstructionToClear() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, adaUserID, Moments.day, null, null, Moments.start,
        )

        val cleared = harness.repository.updateWorkItem(
            id = item.id,
            title = null,
            details = null,
            priority = null,
            assigneeID = Patch.Of(null),
            dueDate = Patch.Of(null),
            storyPoints = Patch.Of(null),
            now = Moments.later,
        )

        assertNull(cleared.assigneeID)
        assertNull(cleared.dueDate)
    }

    @Test
    fun editingAWorkItemChangesItsPriority() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )

        val updated = harness.repository.updateWorkItem(
            item.id, null, null, Priority.Critical, Patch.Unmentioned, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
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
            project.id, "Shipped", "", WorkItemStatus.Done, Priority.Medium, null, null, null, null, Moments.start,
        )

        val edited = harness.repository.updateWorkItem(
            item.id, "Shipped, with a note", null, null, Patch.Unmentioned, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
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
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        val before = harness.repository.statusChanges(item.id).size

        harness.repository.updateWorkItem(
            item.id, "Renamed", null, null, Patch.Unmentioned, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
        )

        assertEquals(before, harness.repository.statusChanges(item.id).size)
    }

    @Test
    fun editingAWorkItemThatIsNotThereFails() = runTest {
        val harness = Harness()
        val id = parseWorkItemID("22222222-2222-4222-8222-222222222222")!!

        assertFailsWith<AppError.Storage> {
            harness.repository.updateWorkItem(
                id, null, null, null, Patch.Unmentioned, Patch.Unmentioned, Patch.Unmentioned, Moments.start,
            )
        }
    }

    @Test
    fun editingAWorkItemAnnouncesTheProjectItBelongsToRatherThanTheItems() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.updateWorkItem(
            item.id, "Renamed", null, null, Patch.Unmentioned, Patch.Unmentioned, Patch.Unmentioned, Moments.later,
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
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "One", "", WorkItemStatus.Done, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "First", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        val second = harness.repository.createWorkItem(
            project.id, "Second", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )

        val moved = harness.repository.moveWorkItem(item.id, WorkItemStatus.InProgress, rank = 42, now = Moments.later)

        assertEquals(42, moved.rank)
    }

    @Test
    fun deletingAWorkItemRemovesItAndItsHistoryAndQueuesADelete() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "Low", "", WorkItemStatus.Todo, Priority.Lowest, null, null, null, null, Moments.start,
        )
        val high = harness.repository.createWorkItem(
            project.id, "High", "", WorkItemStatus.Todo, Priority.Critical, null, null, null, null, Moments.start,
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
            first.id, "Mine", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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
                project.id, "Todo $it", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
            )
        }
        harness.repository.createWorkItem(
            project.id, "Done", "", WorkItemStatus.Done, Priority.Medium, null, null, null, null, Moments.start,
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
            project.id, "Good", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
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

/**
 * Sprints, through the repository.
 *
 * Separate from [RoomProjectRepositoryTest] rather than in it, because the file it would
 * otherwise live in has a doc comment that describes its scope in terms of projects and
 * work items, and appending a third aggregate to a class whose documentation does not
 * mention it is how a file stops being readable.
 *
 * ## The three things worth stating before the tests
 *
 * 1. **Every write is asserted on the outbox as well as the return value**, for the reason
 *    [RoomProjectRepositoryTest] gives. A sprint saved locally and never pushed is the same
 *    failure it is.
 * 2. **The overlap rule is a refusal, not a coalescing policy.** Creating a sprint whose
 *    window shares an instant with an unfinished one throws rather than quietly closing the
 *    other, because "silently finished my sprint" is not a thing anybody asked for. A
 *    finished sprint never blocks: its window is history, not a timetable.
 * 3. **Reopening and editing are refused on the same terms**, and are the only places that
 *    can see the sibling windows, which is therefore the only place the rule can hold.
 *    Reopening a finished sprint can put its window back next to a live one; editing can
 *    move one sprint's window onto another's. Each excludes the sprint's own row.
 */
class RoomSprintRepositoryTest {

    private suspend fun aProjectWithASprint(): Pair<Harness, ProjectID> {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        harness.repository.createSprint(
            projectID = project.id,
            name = "Sprint 1",
            goalStoryPoints = 21,
            startDate = Moments.start,
            endDate = Moments.day,
            ownerID = adaUserID,
            now = Moments.start,
        )
        return harness to project.id
    }

    @Test
    fun aCreatedSprintIsStoredWithEveryFieldAndQueued() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val sprint = harness.repository.createSprint(
            projectID = project.id,
            name = "  Sprint 1  ",
            goalStoryPoints = 21,
            startDate = Moments.start,
            endDate = Moments.day,
            ownerID = adaUserID,
            now = Moments.start,
        )

        // Trimmed by the same rule a project name is, so "  Sprint 1  " cannot become a
        // sprint whose name is two spaces wider than it looks.
        assertEquals("Sprint 1", sprint.name)
        assertEquals(21, sprint.goalStoryPoints)
        assertEquals(adaUserID, sprint.ownerID)
        assertEquals(false, sprint.isFinished)

        val stored = assertNotNull(harness.repository.sprint(sprint.id))
        assertEquals(sprint, stored)
        assertEquals(listOf(sprint), harness.repository.sprints(project.id))

        // And queued: an edit saved locally and never pushed is the failure the outbox
        // exists to prevent. Filtered to sprints, because `createProject` above queued a row
        // of its own and counting the whole queue here would assert on that instead.
        val queued = harness.queued().filter { it.entityKind == "sprint" }
        assertEquals(1, queued.size)
        assertEquals(sprint.id.stringValue, queued.first().entityID)
        assertEquals(sprint, harness.decodeSprint(queued.first()))
    }

    @Test
    fun aSprintWhoseWindowOverlapsAnOpenOneIsRefusedAndTheBlockerIsNamed() = runTest {
        // `aProjectWithASprint` opens a sprint over [Moments.start, Moments.day].
        val (harness, projectID) = aProjectWithASprint()

        val failure = assertFailsWith<AppError.Validation> {
            harness.repository.createSprint(
                projectID = projectID,
                name = "Sprint 2",
                goalStoryPoints = 13,
                // Starts *on* the first sprint's last day. `isActiveOn` is inclusive of both
                // ends, so the two are both active at that instant — this is the boundary
                // the rule refuses, not the staggered case below it.
                startDate = Moments.day,
                endDate = Moments.day + 10.days,
                ownerID = null,
                now = Moments.later,
            )
        }

        // The message names the sprint standing in the way, so the caller can offer to
        // finish it rather than showing a dead end.
        assertTrue(failure.message.orEmpty().contains("Sprint 1"))
        assertEquals(1, harness.repository.sprints(projectID).size)
    }

    @Test
    fun aSecondOpenSprintIsAllowedWhenItsWindowStartsTheDayAfterTheFirstEnds() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val first = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )

        // The day after the first ends. Both stay open: the rule is about the window, not
        // about how many sprints are live, so a project that plans ahead can hold two.
        val second = harness.repository.createSprint(
            project.id, "Sprint 2", 13, null, Moments.day + 1.days, Moments.day + 10.days, null, Moments.later,
        )

        assertEquals(false, second.isFinished)
        assertEquals(false, assertNotNull(harness.repository.sprint(first.id)).isFinished)
        // Unfinished first, then by start date — the read contract the Sprints tab depends on.
        assertEquals(
            listOf(first.id, second.id),
            harness.repository.sprints(project.id).map { it.id },
        )
    }

    @Test
    fun aFinishedSprintDoesNotBlockAWindowSetOverItsDates() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val first = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        harness.repository.finishSprint(first.id, Moments.later)

        // Exactly the finished sprint's dates. Allowed: a finished window is history, not a
        // timetable, and refusing here would make the calendar unfillable once enough
        // sprints have run.
        val second = harness.repository.createSprint(
            project.id, "Sprint 2", 13, null, Moments.start, Moments.day, null, Moments.later,
        )

        assertEquals(false, second.isFinished)
        assertEquals(2, harness.repository.sprints(project.id).size)
    }

    @Test
    fun finishingASprintDatesItAndRefusesToFinishItTwice() = runTest {
        val (harness, projectID) = aProjectWithASprint()
        val sprint = harness.repository.sprints(projectID).single()

        val finished = harness.repository.finishSprint(sprint.id, Moments.later)

        assertEquals(true, finished.isFinished)
        assertEquals(Moments.later, finished.finishedAt)

        // A second finish would silently move the date a reader is being told the sprint
        // ended on — and the summary is computed from the items at the moment it is read.
        assertFailsWith<AppError.Validation> {
            harness.repository.finishSprint(sprint.id, Moments.muchLater)
        }
        assertEquals(
            Moments.later,
            assertNotNull(harness.repository.sprint(sprint.id)).finishedAt,
        )
    }

    @Test
    fun reopeningIsRefusedWhenItWouldOverlapAnOpenSprint() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val first = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        harness.repository.finishSprint(first.id, Moments.later)
        // Legal against the finished first: the second starts the day the first ends, which
        // still *shares* that day's first instant with it — but the first is history, so it
        // is not consulted.
        val second = harness.repository.createSprint(
            project.id, "Sprint 2", 13, null, Moments.day, Moments.day + 10.days, null, Moments.later,
        )

        // Refused: reopening would put the first's window back on the calendar sharing the
        // second's start instant — the exact state createSprint exists to prevent.
        assertFailsWith<AppError.Validation> {
            harness.repository.reopenSprint(first.id, Moments.later)
        }
        assertTrue(assertNotNull(harness.repository.sprint(first.id)).isFinished)

        harness.repository.finishSprint(second.id, Moments.later)
        val reopened = harness.repository.reopenSprint(first.id, Moments.muchLater)

        assertEquals(false, reopened.isFinished)
        assertNull(reopened.finishedAt)
    }

    @Test
    fun movingItemsIntoASprintQueuesEachOneAndLeavesTheirRankAlone() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val sprint = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        val first = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        val second = harness.repository.createWorkItem(
            project.id, "Two", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        val depthBefore = harness.queueDepth()

        val changed = harness.repository.setWorkItemSprint(
            workItemIDs = listOf(first.id, second.id),
            sprintID = Patch.Of(sprint.id),
            now = Moments.later,
        )

        assertEquals(setOf(sprint.id), changed.map { it.sprintID }.toSet())
        // Rank is the item's position in its status column and a sprint is not a column.
        assertEquals(listOf(first.rank, second.rank), changed.map { it.rank })

        // Each item is an edit, and each edit has to be queued — a bulk UPDATE produces no
        // payload for the queue, which is why this goes through the mapping.
        assertEquals(depthBefore + 2, harness.queueDepth())
        assertEquals(
            setOf(sprint.id),
            changed.map { item -> harness.repository.sprintWorkItems(sprint.id).first { it.id == item.id }.sprintID }.toSet(),
        )
    }

    @Test
    fun sendingItemsBackToTheBacklogIsDistinctFromNotMentioningTheirSprint() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val sprint = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        harness.repository.setWorkItemSprint(listOf(item.id), Patch.Of(sprint.id), Moments.start)

        // "Not mentioned" is a caller that does not care — it must change nothing, and it
        // must not read as "send it back". This is the collision a plain `SprintID?` would
        // have made silently.
        assertEquals(
            emptyList(),
            harness.repository.setWorkItemSprint(
                listOf(item.id), Patch.Unmentioned, Moments.muchLater,
            ),
        )
        assertEquals(
            sprint.id,
            assertNotNull(harness.repository.workItem(item.id)).sprintID,
        )

        val released = harness.repository.setWorkItemSprint(
            listOf(item.id), Patch.Of(null), Moments.muchLater,
        )
        assertEquals(listOf(null), released.map { it.sprintID })
    }

    @Test
    fun deletingASprintReturnsItsItemsToTheBacklogRatherThanOrphaningThem() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val sprint = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
        harness.repository.setWorkItemSprint(listOf(item.id), Patch.Of(sprint.id), Moments.start)

        harness.repository.deleteSprint(sprint.id, Moments.later)

        assertNull(harness.repository.sprint(sprint.id))
        // A card still claiming to be in a sprint is a card that reappears there on the next
        // pull, so the release is part of the delete rather than a separate chore.
        assertNull(assertNotNull(harness.repository.workItem(item.id)).sprintID)
        assertTrue(harness.queued().any { it.entityKind == "sprint" })
    }

    @Test
    fun lockingASprintPersistsAnnouncesAndQueuesIt() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val sprint = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        val depthBefore = harness.queueDepth()

        val locked = harness.repository.setSprintLocked(sprint.id, true, Moments.later)

        assertTrue(locked.isLocked)
        assertTrue(assertNotNull(harness.repository.sprint(sprint.id)).isLocked)
        // The lock has to reach the other devices, so it is an ordinary queued sprint write.
        assertEquals(depthBefore + 1, harness.queueDepth())

        val unlocked = harness.repository.setSprintLocked(sprint.id, false, Moments.muchLater)

        assertFalse(unlocked.isLocked)
        assertFalse(assertNotNull(harness.repository.sprint(sprint.id)).isLocked)
    }

    @Test
    fun editingALockedSprintsWindowIsRefusedButItsNameIsNot() = runTest {
        val (harness, projectID) = aProjectWithASprint()
        val sprint = harness.repository.sprints(projectID).single()
        harness.repository.setSprintLocked(sprint.id, true, Moments.later)

        assertFailsWith<AppError.Validation> {
            harness.repository.updateSprint(
                id = sprint.id,
                name = null,
                goalStoryPoints = null,
                startDate = Moments.muchLater,
                endDate = null,
                ownerID = Patch.Unmentioned,
                now = Moments.later,
            )
        }
        assertEquals(sprint.startDate, assertNotNull(harness.repository.sprint(sprint.id)).startDate)

        // The description is outside the lock, so a rename goes through without unlocking.
        val renamed = harness.repository.updateSprint(
            id = sprint.id,
            name = "Sprint 1 (renamed)",
            goalStoryPoints = null,
            startDate = null,
            endDate = null,
            ownerID = Patch.Unmentioned,
            now = Moments.later,
        )
        assertEquals("Sprint 1 (renamed)", renamed.name)
    }

    @Test
    fun deletingAProjectTakesItsSprintsWithItAndQueuesThem() = runTest {
        val (harness, projectID) = aProjectWithASprint()
        val depthBefore = harness.queueDepth()

        harness.repository.deleteProject(projectID)

        assertEquals(emptyList(), harness.repository.sprints(projectID))
        // Queueing only the project would leave the server holding a sprint for a project
        // nothing local references — the mirror of the bug `deleteProject`'s own doc warns
        // about for work items, one level up.
        assertTrue(harness.queued().drop(depthBefore).any { it.entityKind == "sprint" })
    }

    @Test
    fun aSprintWriteAnnouncesSprintsAndWorkItems() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )

        // Both kinds, every time: a subscriber hearing only the sprints half keeps showing
        // the old membership, and one hearing only the items half never learns the sprint
        // exists.
        assertEquals(
            listOf(ProjectChange.Kind.Sprints, ProjectChange.Kind.WorkItems),
            seen.map { it.kind },
        )
        assertTrue(seen.all { it.projectID == project.id })
    }

    @Test
    fun editingASprintIntoABackwardsWindowIsRefusedRatherThanStored() = runTest {
        val (harness, projectID) = aProjectWithASprint()
        val sprint = harness.repository.sprints(projectID).single()

        assertFailsWith<AppError.Validation> {
            harness.repository.updateSprint(
                id = sprint.id,
                name = null,
                goalStoryPoints = null,
                startDate = null,
                endDate = Moments.start.minus(1.days),
                ownerID = Patch.Unmentioned,
                now = Moments.later,
            )
        }

        // A backwards window is survivable if it arrived from a server; an *edit* that
        // silently produces one is how a user ends up with a sprint that is never active.
        assertEquals(sprint.endDate, assertNotNull(harness.repository.sprint(sprint.id)).endDate)
    }

    @Test
    fun editingASprintIntoAnOpenSiblingsWindowIsRefused() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val first = harness.repository.createSprint(
            project.id, "Sprint 1", 21, null, Moments.start, Moments.day, null, Moments.start,
        )
        val second = harness.repository.createSprint(
            project.id, "Sprint 2", 13, null, Moments.day + 1.days, Moments.day + 10.days, null, Moments.later,
        )

        val failure = assertFailsWith<AppError.Validation> {
            harness.repository.updateSprint(
                id = second.id,
                name = null,
                goalStoryPoints = null,
                // Moved onto the first's window — the edit the form's gate cannot see on
                // another device, and the reason the store re-checks.
                startDate = Moments.start,
                endDate = null,
                ownerID = Patch.Unmentioned,
                now = Moments.muchLater,
            )
        }
        assertTrue(failure.message.orEmpty().contains("Sprint 1"))
        // The refusal left the row where it was rather than half-applying the move.
        assertEquals(Moments.day + 1.days, assertNotNull(harness.repository.sprint(second.id)).startDate)
    }

    @Test
    fun editingASprintWithoutMovingItsWindowDoesNotFindItsOwnOverlap() = runTest {
        val (harness, projectID) = aProjectWithASprint()
        val sprint = harness.repository.sprints(projectID).single()

        // The window is unchanged and it overlaps *itself*; the guard excludes the sprint's
        // own row, so a rename is not refused for colliding with the thing being renamed.
        val renamed = harness.repository.updateSprint(
            id = sprint.id,
            name = "Sprint 1 (revised)",
            goalStoryPoints = null,
            startDate = null,
            endDate = null,
            ownerID = Patch.Unmentioned,
            now = Moments.later,
        )

        assertEquals("Sprint 1 (revised)", renamed.name)
    }

    @Test
    fun aNegativeGoalIsFlooredRatherThanStored() = runTest {
        val (harness, projectID) = aProjectWithASprint()
        val sprint = harness.repository.sprints(projectID).single()

        val updated = harness.repository.updateSprint(
            id = sprint.id,
            name = null,
            goalStoryPoints = -8,
            startDate = null,
            endDate = null,
            ownerID = Patch.Unmentioned,
            now = Moments.later,
        )

        assertEquals(0, updated.goalStoryPoints)
    }

    // MARK: Comments

    @Test
    fun anAddedCommentIsStoredWithItsBodyAndParent() = runTest {
        val harness = Harness()
        val item = anItem(harness)
        val author = randomUserID()

        // Whitespace is the *use case's* rule to strip; the store holds what it is given.
        // A trim here would erase the very difference the pair of tests exists to pin.
        val root = harness.repository.addComment(item.id, author, "  As typed  ", null, Moments.start)
        val reply = harness.repository.addComment(item.id, author, "Answer", root.id, Moments.later)

        val thread = harness.repository.comments(item.id)
        assertEquals(listOf(root.id, reply.id), thread.map { it.id })
        assertEquals("  As typed  ", thread[0].body, "the store stores the body as given")
        assertEquals(root.id, thread[1].parentID)
        assertEquals(author, thread[0].authorID)
    }

    @Test
    fun anAddedCommentQueuesOneCommentRowCarryingTheCommentItself() = runTest {
        val harness = Harness()
        val item = anItem(harness)
        val author = randomUserID()

        val comment = harness.repository.addComment(item.id, author, "Queued", null, Moments.start)

        // Filtered rather than compared against the whole queue: the project and item this
        // test set up are queued too, and the comment rows are the subject.
        val commentRows = harness.queued().filter { it.entityKind == "comment" }
        assertEquals(1, commentRows.size)
        assertEquals("upsert", commentRows[0].operation)
        assertEquals(comment.id.stringValue, commentRows[0].entityID)
        val payload = JSONCoding.json.decodeFromString(Comment.serializer(), commentRows[0].payload.decodeToString())
        assertEquals(comment, payload, "the payload is the domain value, not the record")
    }

    @Test
    fun addingACommentAnnouncesTheWorkItemsOfItsProject() = runTest {
        val harness = Harness()
        val item = anItem(harness)
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.repository.addComment(item.id, randomUserID(), "Announced", null, Moments.start)

        assertEquals(
            listOf(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID)),
            seen,
            "an open editor re-reads the thread only if the announcement names its project",
        )
    }

    @Test
    fun commentsComeBackOldestFirstWithTheTieBrokenByInsertion() = runTest {
        val harness = Harness()
        val item = anItem(harness)
        val author = randomUserID()

        val first = harness.repository.addComment(item.id, author, "First", null, Moments.start)
        val second = harness.repository.addComment(item.id, author, "Second", null, Moments.start)
        val third = harness.repository.addComment(item.id, author, "Third", null, Moments.later)

        assertEquals(
            listOf(first.id, second.id, third.id),
            harness.repository.comments(item.id).map { it.id },
            "two comments in one clock tick must not be able to swap places between reads",
        )
    }

    @Test
    fun addingACommentToAWorkItemThatIsGoneIsRefusedWithoutAQueueRow() = runTest {
        // No foreign key enforces this — the package rule rules those out — so the read
        // inside the transaction is the only thing standing between a dangling thread and
        // a row nothing would ever clean up.
        val harness = Harness()
        val commentRowsBefore = harness.queued().count { it.entityKind == "comment" }

        assertFailsWith<AppError> {
            harness.repository.addComment(randomWorkItemID(), randomUserID(), "Orphan", null, Moments.start)
        }

        assertEquals(
            commentRowsBefore,
            harness.queued().count { it.entityKind == "comment" },
            "nothing exists to attach the thread to, so nothing may be queued",
        )
    }

    @Test
    fun deletingAWorkItemTakesItsCommentsWithoutQueueingARowForEach() = runTest {
        val harness = Harness()
        val item = anItem(harness)
        harness.repository.addComment(item.id, randomUserID(), "Doomed", null, Moments.start)

        harness.repository.deleteWorkItem(item.id)

        assertEquals(emptyList(), harness.repository.comments(item.id))
        assertEquals(
            0,
            harness.queued().count { it.entityKind == "comment" && it.operation == "delete" },
            "the item's own delete row is all the server needs; a row per comment would be a second, redundant answer",
        )
    }

    @Test
    fun deletingAProjectTakesTheCommentsOfItsItems() = runTest {
        val harness = Harness()
        val item = anItem(harness)
        harness.repository.addComment(item.id, randomUserID(), "Goes too", null, Moments.start)

        harness.repository.deleteProject(item.projectID)

        assertEquals(emptyList(), harness.repository.comments(item.id))
    }

    /** A project with one item — the arrangement every comment test in this file needs. */
    private suspend fun anItem(harness: Harness): WorkItem {
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        return harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, null, null, Moments.start,
        )
    }
}
