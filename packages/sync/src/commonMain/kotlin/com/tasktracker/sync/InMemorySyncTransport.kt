package com.tasktracker.sync

import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A server that lives in the app.
 *
 * **New in the port, with no counterpart in the original**, because there is no backend to
 * talk to: every `/v1/sync/…` path in `HttpSyncTransport` is a contract nobody has
 * implemented. Without this the app cannot run a complete cycle offline, so the board's
 * local edits can be seen travelling through recover → push → pull → apply → cursor in a
 * simulator, and Phase 14 has something to launch.
 *
 * It is a real implementation of [SyncTransport] rather than a test double, and it behaves
 * like the server the original was written against rather than like a stub:
 *
 *  - every push is answered per entry, so verdicts are id-matched exactly as the engine
 *    requires;
 *  - an accepted or conflicted change is appended to that scope's change log, so a *second*
 *    run of the cycle pulls back what the first one pushed — a round trip rather than a
 *    one-way trip;
 *  - cursors are positions in the log, which is enough to make paging and the
 *    advance-only-after-apply rule observable.
 *
 * A `Mutex` rather than an `actor`, for the same reason `:packages:database` does not use
 * one: the state is a couple of lists, every operation is suspend already, and an actor
 * would make the observation properties suspend too.
 */
class InMemorySyncTransport(
    /** Verdicts per entry id, for a test or a demo that needs a specific server answer. */
    private val scripted: Map<String, PushOutcome> = emptyMap(),
    private val conflicts: Boolean = false,
) : SyncTransport {

    private val mutex = Mutex()
    private val logs: MutableMap<SyncScope, MutableList<RemoteChange>> =
        SyncScope.all.associateWith { mutableListOf<RemoteChange>() }.toMutableMap()
    // Observation through flows rather than plain properties: the engine calls this from a
    // coroutine, and a `List` field read from another one is a data race the tests would
    // only catch sometimes.
    private val pushedFlow = MutableStateFlow<List<String>>(emptyList())
    private val batchesFlow = MutableStateFlow<List<List<String>>>(emptyList())

    /** Every id the engine has handed over, oldest first. */
    val pushed: List<String> get() = pushedFlow.value

    /** Every id handed over, grouped by push call — how a test sees batch boundaries. */
    val batches: List<List<String>> get() = batchesFlow.value

    /** Seeds a scope's change log, as if another device had made these changes. */
    suspend fun seed(scope: SyncScope, changes: List<RemoteChange>) = mutex.withLock {
        logs.getValue(scope).addAll(changes)
    }

    /** How many changes a scope's log holds, i.e. what a fresh client would pull. */
    suspend fun logSize(scope: SyncScope): Int = mutex.withLock { logs.getValue(scope).size }

    override suspend fun push(entries: List<OutboxEntry>): List<PushResult> = mutex.withLock {
        val results = entries.map { entry ->
            val outcome = scripted[entry.id] ?: defaultOutcome()
            if (outcome !is PushOutcome.RetryLater && outcome !is PushOutcome.Rejected) {
                // Accepted and conflicted both leave the server holding this change: the
                // first as the winner, the second as someone else's version. Either way it
                // belongs in the log, which is what makes the next pull bring it back down.
                logs.getValue(scopeFor(entry.entity)).add(entry.toRemoteChange())
            }
            PushResult(entry.id, outcome)
        }
        pushedFlow.value = pushedFlow.value + entries.map { it.id }
        batchesFlow.value = batchesFlow.value + listOf(entries.map { it.id })
        results
    }

    override suspend fun pull(scope: SyncScope, cursor: String?, limit: Int): PullPage = mutex.withLock {
        val log = logs.getValue(scope)
        val from = cursor?.toIntOrNull() ?: 0
        if (from >= log.size) {
            // A cursor at the end is not an error: the log grew between the client's last
            // pull and this one only if the client is behind, and a client that is caught up
            // is asked again on every cycle.
            return@withLock PullPage.empty
        }
        val end = minOf(from + maxOf(1, limit), log.size)
        val page = log.subList(from, end).toList()
        return@withLock PullPage(
            changes = page,
            nextCursor = end.toString(),
            hasMore = end < log.size,
        )
    }

    private fun defaultOutcome(): PushOutcome = if (conflicts) {
        PushOutcome.Conflict(serverVersion = "in-memory-1")
    } else {
        PushOutcome.Accepted
    }

    override fun toString(): String = "InMemorySyncTransport(pushed=$pushed)"
}

/**
 * Which stream an entity's changes belong to.
 *
 * The inverse of the rule that makes a scope *not* an entity type: a project rename travels
 * on the `workItems` stream too, so this mapping is only ever about where a change this
 * device *made* is filed in this device's stand-in server. A real server decides the
 * stream; here there is no server to decide.
 */
private fun scopeFor(entity: OutboxEntity): SyncScope = when (entity) {
    OutboxEntity.Project -> SyncScope.Projects
    // Sprints ride the `workItems` stream rather than getting one of their own.
    //
    // A `SyncScope.Sprints` would be the tidier answer and is deliberately not done here.
    // `SyncScope` is a **wire value** — the server names the streams — so adding one is a
    // protocol change, and `SettingsScreen` renders a sync control per scope, so it is
    // also a new user-facing switch. Neither belongs in the phase that adds a table.
    //
    // Filing them here is not a fudge: this doc already records that a project rename
    // travels on the `workItems` stream too, so the scopes were never a strict partition of
    // entities. A sprint exists only to group work items, and every change to one is only
    // interesting next to the cards in it — so `workItems` is the stream a subscriber
    // watching that project is already reading.
    // Comments ride `workItems` for exactly the reason sprints do: a thread exists only
    // under a card, and a subscriber watching the project is already reading that stream.
    // A dedicated stream would be a wire change and a new user-facing sync switch — see
    // the sprint note above; neither belongs in the phase that adds a table.
    OutboxEntity.WorkItem, OutboxEntity.Sprint, OutboxEntity.Comment -> SyncScope.WorkItems
    // Attachments get their own stream on the server, so they do here too. Filing them under
    // `workItems` would make the pull loop read an empty `attachments` log forever, and a
    // second device would never see the file appear. This branch is also what keeps the
    // `when` exhaustive: `OutboxEntity.Attachment` was added to the enum with the outbox
    // work, and a `when` over the enum without it does not compile.
    OutboxEntity.Attachment -> SyncScope.Attachments
}

private fun OutboxEntry.toRemoteChange(): RemoteChange = RemoteChange(
    entity = entity,
    entityID = entityID,
    operation = operation,
    payload = payload,
    version = null,
)
