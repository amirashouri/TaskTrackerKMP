package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AgentRunID
import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.featureprojectsdomain.AgentRun
import com.tasktracker.featureprojectsdomain.AgentRunEvent
import com.tasktracker.featureprojectsdomain.AgentRunPage
import com.tasktracker.featureprojectsdomain.AgentRunStatus
import com.tasktracker.featureprojectsdomain.AgentRunsPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRunModelTest {

    @Test
    fun refreshShowsTheLatestRunAndItsLog() = runTest {
        val terminal = run(AgentRunStatus.Succeeded, summary = "Done.")
        val port = FakePort(
            latest = listOf(terminal),
            pages = mutableListOf(page(terminal, event(1, "status", "reading"))),
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)

        model.refresh()

        assertEquals(terminal, model.run)
        assertEquals(1, model.events.size)
        assertFalse(model.isLive)
        assertTrue(model.canCancel.not())
    }

    @Test
    fun refreshOnAnItemWithNoRunsClearsTheSection() = runTest {
        val port = FakePort()
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)

        model.refresh()

        assertNull(model.run)
        assertEquals(emptyList(), model.events)
    }

    @Test
    fun dispatchQueuesTheItemAndShowsTheLiveRun() = runTest {
        val queued = run(AgentRunStatus.Queued)
        val port = FakePort(dispatchResult = { queued })
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)

        model.dispatch()

        assertEquals(queued, model.run)
        assertTrue(model.isLive)
        assertEquals(1, port.dispatchCalls)
    }

    @Test
    fun aRetryAfterAFailedDispatchReusesTheSameIdempotencyKey() = runTest {
        var attempts = 0
        val port = FakePort(
            dispatchResult = {
                attempts += 1
                if (attempts == 1) throw com.tasktracker.core.AppError.Unknown("offline")
                run(AgentRunStatus.Queued)
            },
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)

        model.dispatch()
        model.dispatch()

        assertEquals(2, port.dispatchCalls)
        assertEquals(1, port.keys.distinct().size)
        assertTrue(port.keys.first() != null)
    }

    @Test
    fun aSecondDispatchIsReportedAsAlreadyRunning() = runTest {
        val port = FakePort(dispatchResult = { throw AppError.Conflict("work item") })
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)

        model.dispatch()

        assertEquals("An agent run is already in progress for this task.", model.errorMessage)
        assertNull(model.run)
    }

    @Test
    fun followPollsUntilTheRunIsTerminal() = runTest {
        val queued = run(AgentRunStatus.Queued)
        val succeeded = run(AgentRunStatus.Succeeded, summary = "Done.")
        val port = FakePort(
            dispatchResult = { queued },
            pages = mutableListOf(
                page(queued, event(1, "status", "reading")),
                page(succeeded, event(2, "result", "done")),
            ),
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)
        model.dispatch()

        model.follow()

        assertEquals(AgentRunStatus.Succeeded, model.run?.status)
        assertEquals(listOf(1L, 2L), model.events.map { it.seq })
        assertEquals(2, port.runCalls)
    }

    @Test
    fun loadingTheFullLogFollowsTheCursorUntilTheServerHasNoMore() = runTest {
        val queued = run(AgentRunStatus.Queued)
        val port = FakePort(
            dispatchResult = { queued },
            pages = mutableListOf(
                page(queued, event(1, "log", "first"), hasMore = true),
                page(queued, event(2, "log", "second"), hasMore = true),
                page(queued, event(3, "log", "third"), hasMore = false),
            ),
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)
        model.dispatch()

        model.loadFullLog()

        assertEquals(listOf(1L, 2L, 3L), model.events.map { it.seq })
        assertEquals(3, port.runCalls, "each page follows the cursor the previous one advanced")
        assertFalse(model.isLoadingLog)
    }

    @Test
    fun pollingStopsOnAFailedRunToo() = runTest {
        val queued = run(AgentRunStatus.Queued)
        val failed = run(AgentRunStatus.Failed, error = "model unreachable")
        val port = FakePort(
            dispatchResult = { queued },
            pages = mutableListOf(page(failed, event(1, "log", "model unreachable"), hasMore = false)),
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)
        model.dispatch()

        model.follow()

        assertEquals(AgentRunStatus.Failed, model.run?.status)
        assertEquals(1, port.runCalls)
    }

    @Test
    fun cancelAsksOnceAndShowsTheRequestUntilTheServerCancels() = runTest {
        val queued = run(AgentRunStatus.Queued)
        val cancelling = queued.copy(cancelRequested = true)
        val port = FakePort(
            dispatchResult = { queued },
            cancelResult = { cancelling },
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)
        model.dispatch()

        model.cancel()

        assertTrue(model.run!!.cancelRequested)
        assertFalse(model.canCancel)
        assertEquals(1, port.cancelCalls)
    }

    @Test
    fun aRunTheServerNoLongerHasIsShownAsUnavailable() = runTest {
        val queued = run(AgentRunStatus.Queued)
        val port = FakePort(
            dispatchResult = { queued },
            pages = mutableListOf(),
            runFailure = { AppError.NotFound("agent run not found") },
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)
        model.dispatch()

        model.follow()

        assertTrue(model.isUnavailable)
    }

    private fun page(
        run: AgentRun,
        event: AgentRunEvent,
        hasMore: Boolean = false,
    ) = AgentRunPage(run, listOf(event), hasMore)

    private fun event(seq: Long, kind: String, message: String) = AgentRunEvent(
        seq = seq,
        kind = kind,
        message = message,
        payload = "{}",
        createdAt = Instant.parse("2026-11-03T12:00:0${seq}Z"),
    )

    private fun run(
        status: AgentRunStatus,
        summary: String = "",
        error: String = "",
    ) = AgentRun(
        id = AgentRunID(RUN_ID),
        workItemID = WorkItemID(ITEM_ID),
        projectID = ProjectID(PROJECT_ID),
        status = status,
        requestedBy = UserID(USER_ID),
        runnerID = AgentRunnerID(RUNNER_ID),
        cancelRequested = false,
        createdAt = Instant.parse("2026-11-03T12:00:00Z"),
        startedAt = null,
        finishedAt = null,
        summary = summary,
        error = error,
    )

    private class FakePort(
        var latest: List<AgentRun> = emptyList(),
        val pages: MutableList<AgentRunPage> = mutableListOf(),
        var dispatchResult: () -> AgentRun = { error("dispatch not stubbed") },
        var cancelResult: (AgentRunID) -> AgentRun = { error("cancel not stubbed") },
        var runFailure: (() -> AppError)? = null,
    ) : AgentRunsPort {
        var dispatchCalls = 0
        val keys = mutableListOf<String?>()
        var runCalls = 0
        var cancelCalls = 0

        override suspend fun dispatch(workItemID: WorkItemID, idempotencyKey: String?): AgentRun {
            dispatchCalls += 1
            keys += idempotencyKey
            return dispatchResult()
        }

        override suspend fun runs(workItemID: WorkItemID, limit: Int): List<AgentRun> = latest

        override suspend fun run(runID: AgentRunID, afterSeq: Long): AgentRunPage {
            runCalls += 1
            runFailure?.let { throw it() }
            return pages.removeAt(0)
        }

        override suspend fun cancel(runID: AgentRunID): AgentRun {
            cancelCalls += 1
            return cancelResult(runID)
        }
    }

    private companion object {
        const val RUN_ID = "0195c3a4-0000-7000-8000-000000000001"
        const val ITEM_ID = "0195c3a4-0000-7000-8000-000000000002"
        const val PROJECT_ID = "0195c3a4-0000-7000-8000-000000000003"
        const val USER_ID = "0195c3a4-0000-7000-8000-000000000004"
        const val RUNNER_ID = "0195c3a4-0000-7000-8000-0000000000aa"
    }
}
