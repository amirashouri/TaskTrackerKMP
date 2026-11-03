package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AgentRunID
import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import kotlin.time.Instant

/**
 * One delegated run of a work item.
 *
 * Server-owned and polled, not merged: there is no local row and no outbox entry,
 * so this value is only ever what the last read returned. [runnerID] and the two
 * later timestamps are nullable because a run is dispatched before any machine has
 * claimed it.
 */
data class AgentRun(
    val id: AgentRunID,
    val workItemID: WorkItemID,
    val projectID: ProjectID,
    val status: AgentRunStatus,
    val requestedBy: UserID,
    val runnerID: AgentRunnerID?,
    val cancelRequested: Boolean,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val summary: String,
    val error: String,
) {
    /** A run the user still has to wait for: queued, claimed or executing. */
    val isLive: Boolean get() = status.isLive

    /**
     * Cancel is offered only while the run is live and not already cancelling.
     *
     * A second request would not cancel anything sooner — the runner observes the
     * flag at its next heartbeat — so the button is not offered twice.
     */
    val canCancel: Boolean get() = isLive && !cancelRequested
}

/**
 * One progress line, appended in server sequence order.
 *
 * [kind] is an open set (`status`, `log`, `tool`, `result`) rather than an enum:
 * the server accepts any non-empty kind, and a client that failed to decode a new
 * one would lose the whole page rather than one row. [payload] is opaque JSON and
 * is never parsed here.
 */
data class AgentRunEvent(
    val seq: Long,
    val kind: String,
    val message: String,
    val payload: String,
    val createdAt: Instant,
)

/** One poll's worth of a run: the row, the events after the cursor, and whether more are waiting. */
data class AgentRunPage(
    val run: AgentRun,
    val events: List<AgentRunEvent>,
    val hasMore: Boolean,
)

/** The server's closed status vocabulary. */
enum class AgentRunStatus(val wireValue: String) {
    Queued("queued"),
    Claimed("claimed"),
    Running("running"),
    Succeeded("succeeded"),
    Failed("failed"),
    Cancelled("cancelled"),
    ;

    /** Still in flight; polling continues. */
    val isLive: Boolean get() = this == Queued || this == Claimed || this == Running

    /** Finished, one way or another; polling stops. */
    val isTerminal: Boolean get() = !isLive

    companion object {
        /**
         * The status a wire value names, or `null` when it is not in the vocabulary.
         *
         * Returning `null` rather than throwing keeps the rejection at the data
         * layer, which turns it into `AppError.Decoding`: an unknown status is a
         * contract failure the UI cannot reason about, not a run in an odd state.
         */
        fun fromWireValue(value: String): AgentRunStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}
