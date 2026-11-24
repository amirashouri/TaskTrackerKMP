package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.randomUserID
import com.tasktracker.domain.Epic
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.Patch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The epic paths of the real store: what is written, what is queued, and what a later read
 * sees.
 *
 * The outbox is asserted on the same weight as the row, for the reason
 * [RoomProjectRepositoryTest] gives: an epic saved locally and never queued is an edit the
 * user was told about that no other device will ever learn.
 */
class RoomProjectRepositoryEpicTest {

    private suspend fun Harness.twoProjects(): Pair<Project, Project> =
        repository.createProject("Apollo", "APO", "", Moments.start) to
            repository.createProject("Borealis", "BOR", "", Moments.start)

    private suspend fun Harness.item(project: Project, title: String = "Task"): WorkItem =
        repository.createWorkItem(
            projectID = project.id,
            title = title,
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = Moments.start,
        )

    private suspend fun queuedEpic(harness: Harness, id: String) =
        harness.queued().single { it.entityKind == "epic" && it.entityID == id }

    @Test
    fun aCreatedEpicIsStoredAndQueuedAsTheWholeDocument() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()

        val epic = harness.repository.createEpic(
            projectIDs = listOf(borealis.id, apollo.id),
            title = "Checkout",
            details = "One feature across both apps",
            dueDate = Moments.muchLater,
            now = Moments.start,
        )

        assertEquals(epic, harness.repository.epic(epic.id))
        val queued = queuedEpic(harness, epic.id.stringValue)
        assertEquals("upsert", queued.operation)
        assertEquals(
            epic,
            JSONCoding.json.decodeFromString(Epic.serializer(), queued.payload.decodeToString()),
        )
    }

    @Test
    fun anEpicOwnerSurvivesTheRealStoreAndTheOutbox() = runTest {
        val harness = Harness()
        val apollo = harness.twoProjects().first
        val owner = randomUserID()
        val replacement = randomUserID()

        val created = harness.repository.createEpic(
            projectIDs = listOf(apollo.id),
            title = "Owned",
            details = "",
            dueDate = null,
            ownerID = owner,
            now = Moments.start,
        )
        assertEquals(owner, harness.repository.epic(created.id)?.ownerID)
        val createPayload = JSONCoding.json.decodeFromString(
            Epic.serializer(),
            queuedEpic(harness, created.id.stringValue).payload.decodeToString(),
        )
        assertEquals(owner, createPayload.ownerID)

        val updated = harness.repository.updateEpic(
            id = created.id,
            title = null,
            details = null,
            dueDate = Patch.Unmentioned,
            projectIDs = null,
            ownerID = replacement,
            now = Moments.later,
        )
        assertEquals(replacement, harness.repository.epic(created.id)?.ownerID)
        val updatePayload = JSONCoding.json.decodeFromString(
            Epic.serializer(),
            harness.queued().last {
                it.entityKind == "epic" && it.entityID == created.id.stringValue
            }.payload.decodeToString(),
        )
        assertEquals(replacement, updatePayload.ownerID)
        assertEquals(Moments.later, updated.updatedAt)
    }

    @Test
    fun epicsAreListedMostRecentlyTouchedFirst() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val first = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "First", "", null, Moments.start,
        )
        val second = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Second", "", null, Moments.later,
        )

        assertEquals(listOf(second.id, first.id), harness.repository.epics().map { it.id })
    }

    @Test
    fun aLocalLinkSurvivesAReadBackAndIsQueuedWithTheItem() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )
        val item = harness.item(apollo)

        val linked = harness.repository.setWorkItemEpic(item.id, Patch.Of(epic.id), Moments.later)

        assertEquals(epic.id, linked.epicID)
        assertEquals(epic.id, harness.repository.workItem(item.id)?.epicID)
        val queued = harness.queued().last { it.entityKind == "workItem" && it.entityID == item.id.stringValue }
        assertEquals(
            epic.id,
            harness.decodeWorkItem(queued).epicID,
            "a link that is stored but not queued is a link the server never hears about",
        )
    }

    @Test
    fun linkingAnItemToAnEpicThatDoesNotSpanItsProjectIsRefused() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val cygnus = harness.repository.createProject("Cygnus", "CYG", "", Moments.start)
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )
        val item = harness.item(cygnus)

        assertFailsWith<AppError.Validation> {
            harness.repository.setWorkItemEpic(item.id, Patch.Of(epic.id), Moments.later)
        }

        assertNull(harness.repository.workItem(item.id)?.epicID)
    }

    @Test
    fun aSecondLinkReplacesTheFirstAndTheOldEpicSeesNoSubtasks() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val first = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "First", "", null, Moments.start,
        )
        val second = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Second", "", null, Moments.start,
        )
        val item = harness.item(apollo)

        harness.repository.setWorkItemEpic(item.id, Patch.Of(first.id), Moments.later)
        harness.repository.setWorkItemEpic(item.id, Patch.Of(second.id), Moments.later)

        assertEquals(second.id, harness.repository.workItem(item.id)?.epicID)
        assertTrue(harness.repository.workItemsForEpic(first.id).isEmpty())
        assertEquals(listOf(item.id), harness.repository.workItemsForEpic(second.id).map { it.id })
    }

    @Test
    fun clearingTheLinkQueuesAnItemWithNoEpicKey() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )
        val item = harness.item(apollo)
        harness.repository.setWorkItemEpic(item.id, Patch.Of(epic.id), Moments.later)

        harness.repository.setWorkItemEpic(item.id, Patch.Of(null), Moments.muchLater)

        assertNull(harness.repository.workItem(item.id)?.epicID)
        val queued = harness.queued().last { it.entityKind == "workItem" && it.entityID == item.id.stringValue }
        assertNull(harness.decodeWorkItem(queued).epicID)
    }

    @Test
    fun droppingAProjectUnlinksTheItemsItHeldAndQueuesBothHalves() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val cygnus = harness.repository.createProject("Cygnus", "CYG", "", Moments.start)
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )
        val kept = harness.item(apollo)
        val dropped = harness.item(borealis)
        harness.repository.setWorkItemEpic(kept.id, Patch.Of(epic.id), Moments.later)
        harness.repository.setWorkItemEpic(dropped.id, Patch.Of(epic.id), Moments.later)

        val updated = harness.repository.updateEpic(
            id = epic.id,
            title = null,
            details = null,
            dueDate = Patch.Unmentioned,
            projectIDs = listOf(apollo.id, cygnus.id),
            now = Moments.muchLater,
        )

        assertEquals(listOf(apollo.id, cygnus.id), updated.projectIDs)
        assertEquals(epic.id, harness.repository.workItem(kept.id)?.epicID)
        assertNull(
            harness.repository.workItem(dropped.id)?.epicID,
            "the server refuses a link its epic no longer explains, so it must go with the project",
        )
        assertEquals(listOf(kept.id), harness.repository.workItemsForEpic(epic.id).map { it.id })
        val queuedDropped = harness.queued()
            .last { it.entityKind == "workItem" && it.entityID == dropped.id.stringValue }
        assertNull(harness.decodeWorkItem(queuedDropped).epicID)
    }

    @Test
    fun anEpicEditQueuesOnePayloadRatherThanTheHistoryOfEdits() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )

        harness.repository.updateEpic(epic.id, "Checkout v2", null, Patch.Unmentioned, null, Moments.later)
        harness.repository.updateEpic(epic.id, "Checkout v3", null, Patch.Unmentioned, null, Moments.muchLater)

        val queued = harness.queued().filter { it.entityKind == "epic" && it.entityID == epic.id.stringValue }
        assertEquals(1, queued.size, "a newer snapshot supersedes an unsent one")
        assertEquals(
            "Checkout v3",
            JSONCoding.json.decodeFromString(Epic.serializer(), queued.single().payload.decodeToString()).title,
        )
    }

    @Test
    fun clearingTheDueDateIsAnEditRatherThanAnOmission() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.twoProjects()
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", Moments.muchLater, Moments.start,
        )

        val updated = harness.repository.updateEpic(
            epic.id, null, null, Patch.Of(null), null, Moments.later,
        )

        assertNull(updated.dueDate)
        assertNotNull(harness.repository.epic(epic.id))
        assertNull(harness.repository.epic(epic.id)?.dueDate)
    }
}
