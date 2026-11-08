package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AgentRunID
import com.tasktracker.core.AppError
import com.tasktracker.core.WorkItemID
import com.tasktracker.featureprojectsdomain.AgentRun
import com.tasktracker.featureprojectsdomain.AgentRunEvent
import com.tasktracker.featureprojectsdomain.AgentRunPage
import com.tasktracker.featureprojectsdomain.AgentRunsPort
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The agent state of one work item: the latest run, its event log, and the polling
 * that keeps both current.
 *
 * Polling exists only while this model is being read. The screen starts [follow]
 * in a `LaunchedEffect` keyed on the model and leaves it when the sheet closes, so
 * a background run is not chased from a screen nobody is looking at — reopening the
 * item reads the terminal state from [refresh] instead.
 *
 * Nothing here writes to the store or the outbox: a run is server-owned, and a
 * dispatch is a request, not a queued intent. That is what makes an offline
 * dispatch fail honestly instead of promising work that would never be sent.
 */
@OptIn(ExperimentalUuidApi::class)
class AgentRunModel(
    private val workItemID: WorkItemID,
    private val port: AgentRunsPort,
    private val pollInterval: Duration = LIVE_POLL_INTERVAL,
    private val retryInterval: Duration = RETRY_POLL_INTERVAL,
    private val failureThreshold: Int = FAILURE_THRESHOLD,
) : ProjectModel() {

    var run by mutableStateOf<AgentRun?>(null)
        private set

    /** Progress lines in seq order, appended as pages arrive. */
    var events by mutableStateOf<List<AgentRunEvent>>(emptyList())
        private set

    var isDispatching by mutableStateOf(false)
        private set

    var isCancelling by mutableStateOf(false)
        private set

    /** The server no longer holds the run, which is not the same thing as a failure. */
    var isUnavailable by mutableStateOf(false)
        private set

    /** True while the full-log sheet is walking the event cursor. */
    var isLoadingLog by mutableStateOf(false)
        private set

    private var afterSeq: Long = 0
    private var consecutiveFailures: Int = 0

    /**
     * The key of the dispatch the user is trying to land, kept across a failure.
     *
     * A retry that minted a fresh key would be a second intent, which is exactly the
     * duplicate run the server's idempotency guard exists to prevent. Clearing it on
     * success is what makes the next deliberate send a new run.
     */
    private var pendingDispatchKey: String? = null

    val isLive: Boolean get() = run?.isLive == true

    val canCancel: Boolean get() = run?.canCancel == true && !isCancelling

    val latestEvent: AgentRunEvent? get() = events.lastOrNull()

    /**
     * Loads the item's latest run and its log.
     *
     * Called when the editor opens: a run that finished while the app was closed is
     * ordinary, and this is where its result is recovered.
     */
    suspend fun refresh() {
        val latest = reporting { port.runs(workItemID) }?.firstOrNull()
        if (latest == null) {
            clear()
            return
        }
        applyPage(AgentRunPage(run = latest, events = emptyList(), hasMore = false))
        if (latest.status.isTerminal && latest.id == run?.id) {
            // A finished run still has a log worth showing, and it never changes.
            readDetail(latest.id)
        }
    }

    /**
     * Reads every remaining event page for the current run, for the full-log sheet.
     *
     * The live section only keeps the tail; this fills in the earlier lines without
     * changing the polling cursor's meaning. The loop stops when the server says there
     * is no more, when a page returns no progress (so a bad cursor cannot spin), or at
     * [MAX_LOG_PAGES] as a final bound.
     */
    suspend fun loadFullLog() {
        val current = run ?: return
        if (isLoadingLog) return
        isLoadingLog = true
        try {
            var page = reporting { port.run(current.id, afterSeq) } ?: return
            applyPage(page)
            var pages = 1
            while (page.hasMore && pages < MAX_LOG_PAGES) {
                val before = events.size
                page = reporting { port.run(current.id, afterSeq) } ?: return
                applyPage(page)
                pages++
                if (page.events.isEmpty() && events.size == before) break
            }
        } finally {
            isLoadingLog = false
        }
    }

    /** Queues this item for an agent. A live run already covering it is reported, not duplicated. */
    suspend fun dispatch() {
        if (isDispatching || isLive) return
        isDispatching = true
        dismissError()
        isUnavailable = false
        val key = pendingDispatchKey ?: Uuid.random().toString().also { pendingDispatchKey = it }
        try {
            val dispatched = port.dispatch(workItemID, key)
            pendingDispatchKey = null
            clear()
            applyPage(AgentRunPage(dispatched, emptyList(), hasMore = false))
        } catch (conflict: AppError.Conflict) {
            // The server's partial index refused a second live run. It is a state of
            // the item the user can see and wait out, not a mystery failure.
            onFailure(AppError.Validation("An agent run is already in progress for this task."))
        } catch (failure: AppError) {
            onFailure(failure)
        } finally {
            isDispatching = false
        }
    }

    /** Asks the runner to stop. The status only becomes cancelled when the server says so. */
    suspend fun cancel() {
        val current = run ?: return
        if (isCancelling || !current.canCancel) return
        isCancelling = true
        try {
            val updated = port.cancel(current.id)
            run = updated
        } catch (failure: AppError) {
            onFailure(failure)
        } finally {
            isCancelling = false
        }
    }

    /**
     * Polls until the run reaches a terminal state, the caller's scope is cancelled,
     * or the server stops holding the run.
     *
     * The delay grows to [retryInterval] after [failureThreshold] consecutive
     * failures: a laptop that went to sleep should not be asked every two seconds,
     * and a run the server still has is not lost because three polls timed out.
     */
    suspend fun follow() {
        while (true) {
            val current = run ?: return
            if (current.status.isTerminal || isUnavailable) return

            delay(if (consecutiveFailures >= failureThreshold) retryInterval else pollInterval)

            // The run may have finished while the delay ran — a dispatch response can
            // already be terminal, and a cancel can land in between.
            val latest = run ?: return
            if (latest.status.isTerminal || isUnavailable) return

            pollOnce(latest.id)
        }
    }

    private suspend fun pollOnce(runID: AgentRunID) {
        try {
            val page = port.run(runID, afterSeq)
            consecutiveFailures = 0
            applyPage(page)
        } catch (_: AppError.NotFound) {
            isUnavailable = true
        } catch (failure: AppError) {
            consecutiveFailures += 1
            if (consecutiveFailures >= failureThreshold) {
                onFailure(failure)
            }
        }
    }

    private suspend fun readDetail(runID: AgentRunID) {
        try {
            applyPage(port.run(runID, 0))
        } catch (_: AppError.NotFound) {
            isUnavailable = true
        } catch (failure: AppError) {
            onFailure(failure)
        }
    }

    private fun applyPage(page: AgentRunPage) {
        if (page.run.id != run?.id) {
            // A different run means a different log; carrying the old seq would drop
            // this run's first events.
            events = emptyList()
            afterSeq = 0
        }
        run = page.run
        if (page.events.isNotEmpty()) {
            val known = events.mapTo(mutableSetOf()) { it.seq }
            events = (events + page.events.filterNot { it.seq in known }).sortedBy { it.seq }
            afterSeq = maxOf(afterSeq, page.events.maxOf { it.seq })
        }
        if (page.hasMore) {
            // The page was capped; the next poll continues from the cursor. It still
            // waits one interval, because a burst is not a reason to spin.
            consecutiveFailures = 0
        }
    }

    private fun clear() {
        run = null
        events = emptyList()
        afterSeq = 0
        consecutiveFailures = 0
        isUnavailable = false
    }

    companion object {
        /** Two seconds: the sheet is open and the user is watching. */
        val LIVE_POLL_INTERVAL: Duration = 2.seconds

        /** After three failures, poll at the slower pace rather than giving up. */
        val RETRY_POLL_INTERVAL: Duration = 8.seconds

        const val FAILURE_THRESHOLD: Int = 3

        /** A hard stop for a server that keeps claiming there is another page. */
        const val MAX_LOG_PAGES: Int = 20
    }
}
