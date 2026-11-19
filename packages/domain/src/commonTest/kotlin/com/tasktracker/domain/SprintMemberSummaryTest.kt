package com.tasktracker.domain

import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomUserID
import com.tasktracker.core.randomWorkItemID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val now = Instant.parse("2026-11-19T10:00:00Z")
private val projectID: ProjectID = randomProjectID()
private val sprintID: SprintID = randomSprintID()
private val user1: UserID = randomUserID()
private val user2: UserID = randomUserID()
private val itemA: WorkItemID = randomWorkItemID()
private val itemB: WorkItemID = randomWorkItemID()
private val itemC: WorkItemID = randomWorkItemID()

private fun member(userID: UserID, points: Int) = SprintMember(
    id = SprintMember.idFor(sprintID, userID),
    projectID = projectID,
    sprintID = sprintID,
    userID = userID,
    availableStoryPoints = points,
    createdAt = now,
    updatedAt = now,
)

private fun item(id: WorkItemID, assignee: UserID?, status: WorkItemStatus, points: Int?) = WorkItem(
    id = id,
    projectID = projectID,
    key = WorkItemKey("TT-${id.stringValue.take(8)}"),
    title = id.stringValue,
    status = status,
    assigneeID = assignee,
    storyPoints = points,
    sprintID = sprintID,
    createdAt = now,
    updatedAt = now,
)

class SprintMemberSummaryTest {

    @Test
    fun `done items earn points and closed items do not`() {
        val participation = sprintParticipation(
            members = listOf(member(user1, 10)),
            items = listOf(
                item(itemA, user1, WorkItemStatus.Done, 5),
                item(itemB, user1, WorkItemStatus.Closed, 3),
                item(itemC, user1, WorkItemStatus.InProgress, null),
            ),
        )
        val row = participation.members.single()
        assertEquals(5, row.deliveredStoryPoints)
        assertEquals(listOf(itemA), row.finished.map { it.id })
        assertEquals(listOf(itemB), row.closed.map { it.id })
        assertEquals(1, row.remainingCount)
        assertEquals(0.5f, row.progressFraction)
    }

    @Test
    fun `unassigned and unrostered work is reported separately`() {
        val participation = sprintParticipation(
            members = listOf(member(user1, 8)),
            items = listOf(
                item(itemA, user1, WorkItemStatus.Done, 3),
                item(itemB, null, WorkItemStatus.Done, 2),
                item(itemC, user2, WorkItemStatus.Done, 4),
            ),
        )
        assertEquals(2, participation.unassigned.size)
        assertEquals(6, participation.unassignedStoryPoints)
        assertEquals(3, participation.members.single().deliveredStoryPoints)
    }

    @Test
    fun `zero capacity has no progress fraction`() {
        val row = sprintParticipation(
            members = listOf(member(user1, 0)),
            items = listOf(item(itemA, user1, WorkItemStatus.Done, 5)),
        ).members.single()
        assertEquals(5, row.deliveredStoryPoints)
        assertNull(row.progressFraction)
    }

    @Test
    fun `negative capacity is floored at the domain boundary`() {
        assertEquals(0, member(user1, -4).availableStoryPoints)
    }

    @Test
    fun `duplicate member ids collapse to one row`() {
        val participation = sprintParticipation(
            members = listOf(member(user1, 8), member(user1, 3)),
            items = emptyList(),
        )
        assertEquals(1, participation.members.size)
    }
}
