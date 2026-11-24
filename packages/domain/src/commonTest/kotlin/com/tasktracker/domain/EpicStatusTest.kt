package com.tasktracker.domain

import com.tasktracker.core.EpicID
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The epic status truth table, and the one wire rule the epic introduces on an existing
 * payload: a work item's `epicID` is absent when unset and survives a round trip when set.
 */
class EpicStatusTest {

    private val projectID = ProjectID("11111111-1111-1111-1111-111111111111")
    private val otherProjectID = ProjectID("22222222-2222-2222-2222-222222222222")
    private val epicID = EpicID("33333333-3333-3333-3333-333333333333")
    private val createdAt = Instant.parse("2026-11-24T10:00:00Z")

    private fun item(status: WorkItemStatus, key: String = "TT-1", project: ProjectID = projectID) =
        WorkItem(
            projectID = project,
            key = WorkItemKey(key),
            title = "Task",
            status = status,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    // ------------------------------------------------------------------
    // Derivation
    // ------------------------------------------------------------------

    @Test
    fun noSubtasksIsBacklogNotDone() {
        assertEquals(
            EpicStatus.Backlog,
            deriveEpicStatus(emptyList()),
            "an epic with nothing visible is not finished; a device may simply see no subtasks",
        )
    }

    @Test
    fun everySubtaskTerminalIsDone() {
        assertEquals(
            EpicStatus.Done,
            deriveEpicStatus(listOf(item(WorkItemStatus.Done), item(WorkItemStatus.Done, "TT-2"))),
        )
    }

    @Test
    fun closedCountsAsTerminalAlongsideDone() {
        assertEquals(
            EpicStatus.Done,
            deriveEpicStatus(listOf(item(WorkItemStatus.Done), item(WorkItemStatus.Closed, "TT-2"))),
            "a closed item is finished with, one way or another, so it must not keep the epic open",
        )
    }

    @Test
    fun anyInProgressOutranksTodoAndDone() {
        assertEquals(
            EpicStatus.InProgress,
            deriveEpicStatus(
                listOf(
                    item(WorkItemStatus.Done),
                    item(WorkItemStatus.Todo, "TT-2"),
                    item(WorkItemStatus.InProgress, "TT-3"),
                ),
            ),
        )
    }

    @Test
    fun inReviewIsInProgress() {
        assertEquals(
            EpicStatus.InProgress,
            deriveEpicStatus(listOf(item(WorkItemStatus.InReview), item(WorkItemStatus.Todo, "TT-2"))),
            "review is work still moving; treating it as done is the mistake this pins",
        )
    }

    @Test
    fun anyTodoIsTodoWhenNothingHasBegun() {
        assertEquals(
            EpicStatus.Todo,
            deriveEpicStatus(listOf(item(WorkItemStatus.Backlog), item(WorkItemStatus.Todo, "TT-2"))),
        )
    }

    @Test
    fun aFinishedSubtaskMakesTheEpicInProgressWhileAnotherIsTodo() {
        assertEquals(
            EpicStatus.InProgress,
            deriveEpicStatus(listOf(item(WorkItemStatus.Done), item(WorkItemStatus.Todo, "TT-2"))),
            "one finished item is progress even when another has not started; only *all* terminal is Done",
        )
    }

    @Test
    fun backlogOnlyIsBacklog() {
        assertEquals(EpicStatus.Backlog, deriveEpicStatus(listOf(item(WorkItemStatus.Backlog))))
    }

    @Test
    fun finishedSubtasksKeepTheEpicInProgressWithABacklogItem() {
        assertEquals(
            EpicStatus.InProgress,
            deriveEpicStatus(
                listOf(
                    item(WorkItemStatus.Done),
                    item(WorkItemStatus.Closed, "TT-2"),
                    item(WorkItemStatus.Backlog, "TT-3"),
                ),
            ),
            "work has finished, so the epic has begun even if one item is still in backlog",
        )
    }

    @Test
    fun subtasksFromDifferentProjectsAreCountedTogether() {
        assertEquals(
            EpicStatus.InProgress,
            deriveEpicStatus(
                listOf(
                    item(WorkItemStatus.Done, "FE-1", projectID),
                    item(WorkItemStatus.InProgress, "BE-1", otherProjectID),
                ),
            ),
            "an epic spans projects; its status must read the union of the subtasks it can see",
        )
    }

    // ------------------------------------------------------------------
    // Wire
    // ------------------------------------------------------------------

    @Test
    fun anItemWithoutAnEpicGainsNoEpicKey() {
        val encoded = JSONCoding.json.encodeToString(WorkItem.serializer(), item(WorkItemStatus.Todo))
        assertFalse(
            "\"epicID\"" in encoded,
            "an unset epic must be omitted, not sent as null: the server's payload has no key",
        )
    }

    @Test
    fun anItemsEpicIdSurvivesARoundTrip() {
        val linked = item(WorkItemStatus.Todo).copy(epicID = epicID)
        val encoded = JSONCoding.json.encodeToString(WorkItem.serializer(), linked)
        assertTrue("\"epicID\":\"$epicID\"" in encoded, "the key must be the server's epicID, got $encoded")

        val decoded = JSONCoding.json.decodeFromString(WorkItem.serializer(), encoded)
        assertEquals(epicID, decoded.epicID)
    }

    @Test
    fun aPayloadWithoutAnEpicIdDecodesToNull() {
        // The shape a row written before the field existed, or a server that predates it,
        // hands back: the key is simply absent.
        val payload = """
            {"id":"44444444-4444-4444-4444-444444444444",
             "projectID":"$projectID","key":"TT-1","title":"Task",
             "createdAt":"2026-11-24T10:00:00Z","updatedAt":"2026-11-24T10:00:00Z"}
        """.trimIndent()
        val decoded = JSONCoding.json.decodeFromString(WorkItem.serializer(), payload)
        assertNull(decoded.epicID)
    }

    @Test
    fun anEpicEncodesTheServersKeySet() {
        val epic = Epic(
            id = epicID,
            projectIDs = listOf(projectID, otherProjectID),
            title = "Checkout",
            details = "One feature across both apps",
            isArchived = true,
            createdAt = createdAt,
            updatedAt = createdAt,
            version = "v1",
        )
        val encoded = JSONCoding.json.encodeToString(Epic.serializer(), epic)

        for (key in listOf(
            "\"id\"", "\"projectIds\"", "\"title\"", "\"details\"",
            "\"isArchived\"", "\"createdAt\"", "\"updatedAt\"", "\"version\"",
        )) {
            assertTrue(key in encoded, "the epic payload lost $key: $encoded")
        }
        assertFalse(
            "\"dueDate\"" in encoded,
            "an unset due date is omitted, matching the server's omitempty: $encoded",
        )
        assertFalse(
            "\"projectIDs\"" in encoded,
            "the wire spelling is projectIds, not the Kotlin property name: $encoded",
        )
    }

    @Test
    fun anEpicDecodesTheServersProjectIdsSpelling() {
        val payload = """
            {"id":"$epicID","projectIds":["$projectID","$otherProjectID"],
             "title":"Checkout","details":"","isArchived":false,
             "createdAt":"2026-11-24T10:00:00Z","updatedAt":"2026-11-24T10:00:00Z","version":"v1"}
        """.trimIndent()
        val decoded = JSONCoding.json.decodeFromString(Epic.serializer(), payload)
        assertEquals(listOf(projectID, otherProjectID), decoded.projectIDs)
    }

    @Test
    fun aRepeatedProjectIdIsStoredOnceInOrder() {
        val epic = Epic(
            projectIDs = listOf(otherProjectID, projectID, otherProjectID),
            title = "Checkout",
            createdAt = createdAt,
            updatedAt = createdAt,
        )
        assertEquals(listOf(otherProjectID, projectID), epic.projectIDs)
    }
}
