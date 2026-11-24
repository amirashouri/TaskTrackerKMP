package com.tasktracker.featureprojectsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.randomUserID
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.domain.Epic
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.sync.RemoteChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Applying pulled epics, and the one thing a pull may never do: queue.
 *
 * A sink that queued what it applied would turn every pull into a push, and a server echoing
 * the change would leave the two sides pushing at each other for as long as the app is open.
 * So the queue is asserted to stay exactly as it was after every pull below.
 */
class LocalDataSyncSinkEpicTest {

    private fun upsert(entity: OutboxEntity, id: String, payload: ByteArray): RemoteChange =
        RemoteChange(
            entity = entity,
            entityID = id,
            operation = OutboxOperation.Upsert,
            payload = payload,
            version = null,
        )

    private fun delete(entity: OutboxEntity, id: String): RemoteChange =
        RemoteChange(
            entity = entity,
            entityID = id,
            operation = OutboxOperation.Delete,
            payload = id.encodeToByteArray(),
            version = null,
        )

    private fun encoded(epic: Epic): ByteArray =
        JSONCoding.json.encodeToString(Epic.serializer(), epic).encodeToByteArray()

    private fun encoded(item: WorkItem): ByteArray =
        JSONCoding.json.encodeToString(WorkItem.serializer(), item).encodeToByteArray()

    @Test
    fun aPulledEpicIsStoredAndAnnouncesItsOwnKindWithoutQueueing() = runTest {
        val harness = Harness()
        val epic = Epic(
            projectIDs = listOf(harness.repository.createProject("Apollo", "APO", "", Moments.start).id),
            title = "Checkout",
            createdAt = Moments.start,
            updatedAt = Moments.later,
        )
        val changes = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), changes)
        val depthBefore = harness.queueDepth()

        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(epic)))

        assertEquals(epic, harness.repository.epic(epic.id))
        assertTrue(
            changes.any { it.kind == ProjectChange.Kind.Epics },
            "the Epic segment has to hear about a pulled epic or it shows the pre-pull list",
        )
        assertEquals(depthBefore, harness.queueDepth(), "a pull must never write to the outbox")
    }

    @Test
    fun aPulledEpicEditOverwritesTheStoredDocument() = runTest {
        val harness = Harness()
        val epic = Epic(
            projectIDs = listOf(harness.repository.createProject("Apollo", "APO", "", Moments.start).id),
            title = "Checkout",
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )
        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(epic)))
        val edited = epic.copy(title = "Checkout v2", updatedAt = Moments.later)

        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(edited)))

        assertEquals("Checkout v2", harness.repository.epic(epic.id)?.title)
        assertEquals(1, harness.repository.epics().size, "an upsert is not an insert")
    }

    @Test
    fun aPulledEpicCarriesItsOwner() = runTest {
        val harness = Harness()
        val owner = randomUserID()
        val epic = Epic(
            projectIDs = listOf(harness.repository.createProject("Apollo", "APO", "", Moments.start).id),
            title = "Owned checkout",
            ownerID = owner,
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )
        val payload = encoded(epic).decodeToString()
        assertTrue(payload.contains("\"ownerId\""), "the client must declare the ownerId key")

        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(epic)))

        assertEquals(owner, harness.repository.epic(epic.id)?.ownerID)

        val replacement = randomUserID()
        harness.sink.apply(
            upsert(
                OutboxEntity.Epic,
                epic.id.stringValue,
                encoded(epic.copy(ownerID = replacement, updatedAt = Moments.later)),
            ),
        )
        assertEquals(replacement, harness.repository.epic(epic.id)?.ownerID)
    }

    @Test
    fun reapplyingTheSameEpicLeavesOneRow() = runTest {
        val harness = Harness()
        val epic = Epic(
            projectIDs = listOf(harness.repository.createProject("Apollo", "APO", "", Moments.start).id),
            title = "Checkout",
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )

        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(epic)))
        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(epic)))

        assertEquals(listOf(epic), harness.repository.epics())
    }

    @Test
    fun aPulledWorkItemCarriesTheLinkItWasGiven() = runTest {
        val harness = Harness()
        val epic = Epic(
            projectIDs = listOf(harness.repository.createProject("Apollo", "APO", "", Moments.start).id),
            title = "Checkout",
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )
        harness.sink.apply(upsert(OutboxEntity.Epic, epic.id.stringValue, encoded(epic)))
        val item = aWorkItem().also { it.epicID = epic.id }

        harness.sink.apply(upsert(OutboxEntity.WorkItem, item.id.stringValue, encoded(item)))

        assertEquals(epic.id, harness.repository.workItem(item.id)?.epicID)
    }

    @Test
    fun aPulledEpicDeleteDetachesItsItemsAndLeavesThemAlive() = runTest {
        val harness = Harness()
        val (apollo, borealis) = harness.repository.createProject("Apollo", "APO", "", Moments.start) to
            harness.repository.createProject("Borealis", "BOR", "", Moments.start)
        val epic = harness.repository.createEpic(
            listOf(apollo.id, borealis.id), "Checkout", "", null, Moments.start,
        )
        val item = harness.repository.createWorkItem(
            projectID = apollo.id,
            title = "Task",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = Moments.start,
        )
        harness.repository.setWorkItemEpic(item.id, Patch.Of(epic.id), Moments.later)
        val depthBefore = harness.queueDepth()

        harness.sink.apply(delete(OutboxEntity.Epic, epic.id.stringValue))

        assertNull(harness.repository.epic(epic.id))
        val survivor = assertNotNull(harness.repository.workItem(item.id))
        assertNull(
            survivor.epicID,
            "mirroring the server's ON DELETE SET NULL: the item survives its epic",
        )
        assertEquals(depthBefore, harness.queueDepth(), "detaching is part of applying the pull")
    }
}
