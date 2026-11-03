package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AgentRunID
import com.tasktracker.core.AppError
import com.tasktracker.core.WorkItemID
import kotlin.coroutines.cancellation.CancellationException

/**
 * The agent API as presentation sees it: dispatch, read, poll, cancel.
 *
 * Deliberately narrow. There is no local store behind it and no runner pairing:
 * the CLI owns the machine credential, and a run cannot progress offline, so the
 * port is a thin read/write surface over the server with no cache to invalidate.
 */
interface AgentRunsPort {

    /**
     * Asks the server to queue this item for whichever machine is paired.
     *
     * Throws [AppError.Conflict] when a live run already exists — the server's
     * partial index refuses the second dispatch, and the port does not pretend
     * otherwise.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun dispatch(workItemID: WorkItemID): AgentRun

    /** Recent runs for one item, newest first. What reopens an item onto a finished run. */
    @Throws(AppError::class, CancellationException::class)
    suspend fun runs(workItemID: WorkItemID, limit: Int = DEFAULT_LIMIT): List<AgentRun>

    /**
     * The run plus every event after [afterSeq] in one round trip.
     *
     * [AppError.NotFound] means the server no longer holds the run; the caller
     * renders that as "no longer available" rather than retrying.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun run(runID: AgentRunID, afterSeq: Long = 0): AgentRunPage

    /**
     * Asks the runner to stop. Cooperative: the run keeps its status until the
     * runner observes `cancelRequested` on its next heartbeat and finishes.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun cancel(runID: AgentRunID): AgentRun

    companion object {
        /** The server's own default page size. */
        const val DEFAULT_LIMIT: Int = 20
    }
}
