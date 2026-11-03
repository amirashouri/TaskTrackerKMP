package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AgentRunID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AgentRunTest {

    private fun run(st: AgentRunStatus, cancelRequested: Boolean = false) = AgentRun(
        id = AgentRunID(RUN_ID),
        workItemID = WorkItemID(ITEM_ID),
        projectID = ProjectID(PROJECT_ID),
        status = st,
        requestedBy = UserID(USER_ID),
        runnerID = null,
        cancelRequested = cancelRequested,
        createdAt = Instant.parse("2026-11-03T12:00:00Z"),
        startedAt = null,
        finishedAt = null,
        summary = "",
        error = "",
    )

    @Test
    fun everyServerStatusParses() {
        val expected = listOf(
            "queued" to AgentRunStatus.Queued,
            "claimed" to AgentRunStatus.Claimed,
            "running" to AgentRunStatus.Running,
            "succeeded" to AgentRunStatus.Succeeded,
            "failed" to AgentRunStatus.Failed,
            "cancelled" to AgentRunStatus.Cancelled,
        )
        for ((wire, status) in expected) {
            assertEquals(status, AgentRunStatus.fromWireValue(wire))
            assertEquals(wire, status.wireValue)
        }
    }

    @Test
    fun anUnknownStatusIsRejectedRatherThanGuessed() {
        assertNull(AgentRunStatus.fromWireValue("teleported"))
    }

    @Test
    fun onlyLiveRunsArePolled() {
        assertTrue(AgentRunStatus.Queued.isLive)
        assertTrue(AgentRunStatus.Claimed.isLive)
        assertTrue(AgentRunStatus.Running.isLive)
        assertFalse(AgentRunStatus.Succeeded.isLive)
        assertFalse(AgentRunStatus.Failed.isLive)
        assertFalse(AgentRunStatus.Cancelled.isLive)
        assertTrue(AgentRunStatus.Succeeded.isTerminal)
    }

    @Test
    fun cancelIsOfferedOnceAndOnlyWhileLive() {
        assertTrue(run(AgentRunStatus.Queued).canCancel)
        assertTrue(run(AgentRunStatus.Running).canCancel)

        // Already asked: the runner observes the flag at its next heartbeat, so a
        // second request would do nothing.
        assertFalse(run(AgentRunStatus.Running, cancelRequested = true).canCancel)

        assertFalse(run(AgentRunStatus.Succeeded).canCancel)
        assertFalse(run(AgentRunStatus.Failed).canCancel)
        assertFalse(run(AgentRunStatus.Cancelled).canCancel)
    }

    private companion object {
        const val RUN_ID = "0195c3a4-0000-7000-8000-000000000001"
        const val ITEM_ID = "0195c3a4-0000-7000-8000-000000000002"
        const val PROJECT_ID = "0195c3a4-0000-7000-8000-000000000003"
        const val USER_ID = "0195c3a4-0000-7000-8000-000000000004"
    }
}
