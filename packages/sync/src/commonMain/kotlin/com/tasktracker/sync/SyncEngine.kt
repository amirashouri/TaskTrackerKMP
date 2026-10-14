package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.SystemDateProvider
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxEntry
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.OutboxStatus
import com.tasktracker.database.SyncMetadataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * One push-then-pull cycle, and the retry policy that governs it.
 *
 * The shape of a cycle is fixed and the order is not negotiable:
 *
 *  1. **Recover.** Anything left `inFlight` by a crash is returned to `pending`.
 *  2. **Push.** Drain claimed outbox entries oldest-first, in order.
 *  3. **Pull.** Replay each scope's change log from its cursor into the local store.
 *
 * Push first because the pull is ordered from a server log that only contains what the
 * server has; pushing first means the pull usually reflects the user's own most recent
 * edit, and any conflict the server wins is resolved by a change this cycle is about to
 * fetch rather than one cycle later.
 *
 * ## Not an actor
 *
 * The original is a Swift `actor`, and the reason is worth keeping: a cycle is long
 * stateful work that must not overlap itself, and two concurrent cycles would claim the
 * same entries and double-push them, which is the single worst thing this module could do.
 * Kotlin's [Mutex] gives the same exclusion with the same intent, and unlike an actor it
 * does not make every *read* of the engine a suspension point — `isSyncing` is a property
 * here, where the original needed `await engine.isSyncing`.
 */
class SyncEngine(
    private val outbox: OutboxQueue,
    private val metadata: SyncMetadataStore,
    private val transport: SyncTransport,
    private val sinks: Map<OutboxEntity, SyncApplySink> = emptyMap(),
    private val configuration: SyncConfiguration = SyncConfiguration(),
    private val dateProvider: DateProvider = SystemDateProvider(),
    /**
     * Reads the signed-in user at claim time, so the push claims only that account's queued
     * rows. Injectable rather than a captured value because a session can start or end while
     * the engine is alive; `null` claims everything, which is what a store without auth
     * wiring takes.
     */
    private val pushAs: suspend () -> String? = { null },
) {
    private val logger = TTLogger.sync

    /**
     * Exclusion for a whole cycle, and [isSyncing] is read outside it.
     *
     * Two primitives for one idea, deliberately: the `Mutex` is what makes the check and the
     * claim atomic, and a `MutableStateFlow` is what lets another coroutine *observe* that a
     * cycle is running without blocking on the lock it is waiting to enter. A `var` would
     * have been the third option, and it is not a thread-safe one.
     */
    private val gate = Mutex()
    private val runningFlag = MutableStateFlow(false)

    /** Whether a cycle is in flight. A caller that needs to know should have used
     * [SyncCoordinator], which also enforces single-flight; this is for diagnostics. */
    val isSyncing: Boolean get() = runningFlag.value

    /**
     * Runs one cycle and reports what happened. Never throws.
     *
     * A cycle is triggered by a timer, by app launch, and by connectivity events. None of
     * those callers can act on a thrown error, and a thrown error would lose the counts
     * that are the only useful diagnostic here — how much is queued, how much is dead, did
     * the sign-out happen. So every failure is folded into the report.
     */
    suspend fun run(
        trigger: SyncTrigger = SyncTrigger.Manual,
        onPhase: suspend (SyncPhase) -> Unit = {},
    ): SyncReport {
        if (!gate.tryLock()) {
            // Reported rather than dropped, and that asymmetry is deliberate: the
            // coordinator drops a *trigger* it cannot honour, but a caller that reaches the
            // engine directly and is refused deserves a report explaining why it did
            // nothing.
            return SyncReport(
                trigger = trigger,
                failures = listOf(
                    SyncFailure(
                        stage = SyncStage.Push,
                        message = "A sync cycle is already running; this trigger was ignored.",
                    ),
                ),
            )
        }
        runningFlag.value = true
        try {
            var report = SyncReport(trigger = trigger)
            logger.info { logMessage { append("sync: cycle started ("); append(trigger.wireName); append(")") } }

            report = report.copy(recoveredStranded = recoverStranded(report))

            val pushed = push(report, onPhase)
            report = pushed.report

            val pulled = pull(report, onPhase)
            report = pulled.report

            report = finalise(report, onPhase)

            if (report.isSuccess) {
                logger.info {
                    logMessage {
                        append("sync: cycle finished, pushed ")
                        append(report.pushed)
                        append(", applied ")
                        append(report.applied)
                    }
                }
            } else {
                logger.warning {
                    logMessage {
                        append("sync: cycle finished with ")
                        append(report.failures.size)
                        append(" failure(s)")
                    }
                }
            }
            return report
        } finally {
            runningFlag.value = false
            gate.unlock()
        }
    }

    // MARK: - Recover

    private suspend fun recoverStranded(report: SyncReport): Int = try {
        val count = outbox.recoverStrandedEntries()
        if (count > 0) {
            // Worth a line of its own: a non-zero count here means a previous process died
            // mid-push, so this is a crash-loop symptom, not routine.
            logger.warning { logMessage { append("sync: recovered "); append(count); append(" change(s) stranded in flight") } }
        }
        count
    } catch (error: Exception) {
        report.withFailure(SyncStage.RecoveringStranded, describe(error))
        0
    }

    // MARK: - Push

    private class StageResult(val report: SyncReport)

    private suspend fun push(
        start: SyncReport,
        onPhase: suspend (SyncPhase) -> Unit,
    ): StageResult {
        var report = start
        // Entries already attempted in *this* cycle. The outbox hands back `failed` rows on
        // the next claim, so without this a server that rejects everything with "retry
        // later" would be asked again in a tight loop and the cycle would never end. Once a
        // full batch comes back with nothing new in it, this cycle has run out of work it
        // can make progress on.
        val attemptedInCycle = mutableSetOf<String>()
        // Read once per cycle, not per batch: a sign-out mid-cycle is already handled by the
        // session-ended branch below, and re-reading per batch would let one cycle straddle
        // two accounts.
        val currentUserID = pushAs()

        for (batch in 0 until configuration.pushBatchCeiling) {
            val claimed = try {
                outbox.claim(
                    limit = configuration.pushBatchSize,
                    maxAttempts = configuration.maxAttempts,
                    userID = currentUserID,
                )
            } catch (error: Exception) {
                return StageResult(report.withFailure(SyncStage.Push, describe(error)))
            }

            if (claimed.isEmpty()) return StageResult(report)

            val fresh = claimed.filterNot { attemptedInCycle.contains(it.id) }
            if (fresh.isEmpty()) {
                // `claim` already marked the whole batch in flight and charged an attempt,
                // so these cannot be dropped: that would leave them stranded, invisible to
                // the next claim, until the next launch. `fail` rather than `release`,
                // because by construction every one of them failed earlier in this cycle,
                // and the attempt budget is what eventually stops a hopeless change being
                // retried forever.
                runCatching {
                    outbox.fail(
                        ids = claimed.map { it.id },
                        message = "Deferred: already attempted in this cycle.",
                        maxAttempts = configuration.maxAttempts,
                    )
                }
                logger.info {
                    logMessage { append("sync: every claimed change was already tried this cycle, stopping the push loop") }
                }
                return StageResult(report)
            }
            attemptedInCycle.addAll(fresh.map { it.id })

            onPhase(SyncPhase.Pushing(fresh.size))
            report = report.copy(pushed = report.pushed + fresh.size)

            val settled = try {
                val results = transport.push(fresh)
                settle(fresh, results, report)
            } catch (error: Exception) {
                // The batch is in `inFlight` and the server may or may not have seen it.
                // Returning it to a retryable state is the only safe answer: the change ids
                // are stable, so a server that dedupes drops the duplicate.
                val message = describe(error)
                if ((error as? AppError)?.requiresSignOut == true) {
                    // The session ending is not the change's fault, so the attempt is handed
                    // back. Charging it here would let a single sign-out push a user's
                    // unsaved work over the dead-letter limit — work they never had a chance
                    // to send.
                    report = report.copy(
                        retrying = report.retrying + fresh.size,
                        requiresSignOut = true,
                    ).withFailure(SyncStage.Push, "The session ended mid-push; queued changes were kept.")
                    runCatching { outbox.release(fresh.map { it.id }, message = message) }
                    logger.error {
                        logMessage { append("sync: session ended mid-push, "); append(fresh.size); append(" change(s) kept") }
                    }
                    return StageResult(report)
                }
                // A transport failure is a genuine failed attempt and does count towards the
                // budget, so an unreachable server eventually dead-letters instead of retrying
                // forever.
                report = report.copy(retrying = report.retrying + fresh.size)
                    .withFailure(SyncStage.Push, message)
                return StageResult(failedRequeue(report, fresh, message))
            }

            report = settled.report
            if (settled.mustRetry) {
                // Stop here rather than claiming again. The next claim would hand back these
                // same entries, and pushing a newer change past one the server has not
                // accepted would break the ordering the queue exists to keep.
                logger.info {
                    logMessage { append("sync: stopping the push loop after a retryable failure, the rest waits for the next cycle") }
                }
                return StageResult(report)
            }
        }

        if (attemptedInCycle.size >= configuration.pushBatchSize * configuration.pushBatchCeiling) {
            logger.info {
                logMessage { append("sync: push batch ceiling reached, the rest of the backlog waits for the next cycle") }
            }
        }
        return StageResult(report)
    }

    /** A claimed batch returned to the queue as a **failed** attempt, charging the attempt
     * it already paid at claim time. */
    private suspend fun failedRequeue(
        report: SyncReport,
        entries: List<OutboxEntry>,
        reason: String,
    ): SyncReport = try {
        outbox.fail(entries.map { it.id }, reason, configuration.maxAttempts)
        report
    } catch (error: Exception) {
        report.withFailure(SyncStage.Push, describe(error))
    }

    private class SettleResult(val report: SyncReport, val mustRetry: Boolean)

    /**
     * Applies the server's verdicts to the queue.
     *
     * @return `true` when something must be retried, meaning the push loop should stop
     * rather than claim again.
     */
    private suspend fun settle(
        entries: List<OutboxEntry>,
        results: List<PushResult>,
        start: SyncReport,
    ): SettleResult {
        // Matched on the entry id, never on position. A change the server did not mention
        // has an unknown fate and is treated as "retry" rather than "done" — assuming success
        // would silently drop a change the user was told was saved.
        val verdicts = mutableMapOf<String, PushOutcome>()
        val duplicates = mutableListOf<String>()
        for (result in results) {
            if (verdicts.containsKey(result.entryID)) duplicates.add(result.entryID)
            verdicts[result.entryID] = result.outcome
        }

        val acceptedIDs = mutableListOf<String>()
        val conflictIDs = mutableListOf<String>()
        val retrying = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        var unmentioned = false

        for (entry in entries) {
            when (val outcome = verdicts[entry.id]) {
                null -> {
                    retrying.add(entry.id)
                    unmentioned = true
                }

                PushOutcome.Accepted -> acceptedIDs.add(entry.id)
                // The server's copy won. The entry is resolved, not failed — the next pull
                // brings the winning version down. Leaving it queued would push the same
                // losing change on every future cycle.
                is PushOutcome.Conflict -> conflictIDs.add(entry.id)
                is PushOutcome.Rejected -> {
                    rejected.add(entry.id)
                    reasons.add(outcome.reason)
                }

                is PushOutcome.RetryLater -> {
                    retrying.add(entry.id)
                    reasons.add(outcome.reason)
                }
            }
        }

        if (unmentioned) {
            reasons.add("The server did not report a result for every change; the remainder was queued again.")
        }
        var report = start
        if (duplicates.isNotEmpty()) {
            // A repeated id means the server and the engine can disagree about what a
            // verdict applied to, so nothing is trusted for that change.
            val repeated = duplicates.toSet()
            acceptedIDs.removeAll { repeated.contains(it) }
            conflictIDs.removeAll { repeated.contains(it) }
            rejected.removeAll { repeated.contains(it) }
            retrying.addAll(repeated)
            reasons.add("The server reported a conflicting result more than once for ${repeated.size} change(s).")
            report = report.withFailure(SyncStage.Push, "The server returned contradictory results for a change.")
        }
        val reason = if (reasons.isEmpty()) "" else reasons.joinToString("; ")

        // Conflicts are counted apart from acceptances. Both empty the queue, but a report
        // that said "3 accepted" when the server overwrote one of them would misreport what
        // happened to the user's data.
        val resolved = acceptedIDs + conflictIDs
        report = report.copy(
            accepted = report.accepted + acceptedIDs.size,
            conflicts = report.conflicts + conflictIDs.size,
        )

        if (resolved.isNotEmpty()) {
            report = try {
                outbox.complete(resolved)
                report
            } catch (error: Exception) {
                report.withFailure(SyncStage.Push, describe(error))
            }
        }
        if (rejected.isNotEmpty()) {
            report = report.copy(rejected = report.rejected + rejected.size)
            report = try {
                outbox.reject(rejected, message = reason)
                report
            } catch (error: Exception) {
                report.withFailure(SyncStage.Push, describe(error))
            }
        }
        if (retrying.isNotEmpty()) {
            report = report.copy(retrying = report.retrying + retrying.toSet().size)
            report = try {
                outbox.fail(retrying, message = reason, maxAttempts = configuration.maxAttempts)
                report
            } catch (error: Exception) {
                report.withFailure(SyncStage.Push, describe(error))
            }
        }

        if (report.conflicts > 0) {
            logger.warning {
                logMessage { append("sync: "); append(report.conflicts); append(" change(s) resolved in the server's favour") }
            }
        }
        return SettleResult(report, mustRetry = retrying.isNotEmpty())
    }

    // MARK: - Pull

    private suspend fun pull(
        start: SyncReport,
        onPhase: suspend (SyncPhase) -> Unit,
    ): StageResult {
        var report = start
        for (scope in configuration.scopes) {
            report = pull(scope, report, onPhase)
        }
        return StageResult(report)
    }

    private suspend fun pull(
        scope: SyncScope,
        start: SyncReport,
        onPhase: suspend (SyncPhase) -> Unit,
    ): SyncReport {
        onPhase(SyncPhase.Pulling(scope))
        var report = start

        val stored = try {
            metadata.metadata(scope.wireValue)
        } catch (error: Exception) {
            return report.withFailure(SyncStage.Pull(scope), describe(error))
        }
        var cursor = stored?.cursor
        // Set once a resync has been started, so a server that keeps asking cannot spin this
        // loop until the page ceiling runs out and report a clean sync having applied nothing.
        var resyncing = false

        for (page in 0 until configuration.pullPageCeiling) {
            val page_ = try {
                transport.pull(scope = scope, cursor = cursor, limit = configuration.pullPageSize)
            } catch (error: Exception) {
                // The cursor is only advanced after a page is fully applied, so a failed
                // pull simply re-reads the same page next cycle. Nothing to undo here.
                val signOut = (error as? AppError)?.requiresSignOut == true
                return report.copy(requiresSignOut = report.requiresSignOut || signOut)
                    .withFailure(SyncStage.Pull(scope), describe(error))
            }

            // The cursor names a position the server has pruned away, so this page is a
            // partial view of the gap. Applying it and advancing the cursor would skip the
            // changes that are gone for good and still look like a clean sync. Throw the
            // cursor away and read the surviving log from the start instead — the sinks are
            // idempotent, so re-delivering a change already applied is harmless.
            if (page_.resyncRequired) {
                if (resyncing) {
                    // Asking twice in one cycle means the server is pruning faster than this
                    // client can read. Looping would not converge, and reporting success
                    // would be a lie about data the app never received.
                    return report.withFailure(
                        SyncStage.Pull(scope),
                        "The server asked for a resync again before this scope could catch up.",
                    )
                }
                resyncing = true
                cursor = null
                continue
            }

            if (page_.changes.isEmpty()) return report

            val applied = try {
                for (change in page_.changes) {
                    val sink = sinks[change.entity]
                        ?: throw SyncException.NoSink(change.entity)
                    sink.apply(change)
                }
                page_.changes.size
            } catch (error: Exception) {
                // A missing sink is a wiring bug, and it is deliberately fatal for this
                // scope. The cursor stays put so nothing is skipped, which means a missing
                // sink blocks that stream loudly instead of dropping changes quietly.
                // Fixing the wiring is the correct response; silently advancing past data
                // the app cannot store is not.
                val message = describe(error)
                report = report.withFailure(SyncStage.Pull(scope), message)
                recordFailure(scope, message)
                return report
            }
            report = report.copy(applied = report.applied + applied)

            cursor = page_.nextCursor
            try {
                metadata.markSynced(scope = scope.wireValue, cursor = cursor, at = dateProvider.now())
            } catch (error: Exception) {
                return report.withFailure(SyncStage.Pull(scope), describe(error))
            }

            if (!page_.hasMore) return report
        }
        return report
    }

    // MARK: - Finalise

    private suspend fun finalise(
        start: SyncReport,
        onPhase: suspend (SyncPhase) -> Unit,
    ): SyncReport {
        onPhase(SyncPhase.Finalising)
        var report = start
        try {
            report = report.copy(
                queueDepth = outbox.count(null),
                deadLetterCount = outbox.count(OutboxStatus.Dead),
            )
        } catch (error: Exception) {
            report = report.withFailure(SyncStage.Push, describe(error))
        }

        // One bookkeeping note per scope, so a failed push is visible in the settings screen
        // next to the scope it belongs to and not only in the device log.
        if (!report.isSuccess) {
            val message = report.failures.joinToString("; ") { it.message }
            for (scope in configuration.scopes) {
                recordFailure(scope, message)
            }
        }
        return report
    }

    private suspend fun recordFailure(scope: SyncScope, message: String) {
        // Best effort by design: this is bookkeeping *about* a failure already recorded in
        // the report, so a store that cannot be written to must not add a second failure to
        // the thing being reported.
        runCatching { metadata.recordFailure(scope.wireValue, message) }
    }

    // MARK: - Helpers

    private fun SyncReport.withFailure(stage: SyncStage, message: String): SyncReport =
        copy(failures = failures + SyncFailure(stage, message))

    /**
     * A message safe to show a user and to log.
     *
     * `AppError` already carries user-facing text, so it is preferred; anything else is an
     * unexpected failure from a layer below and gets its own message, or its type name when
     * it has none. Kotlin has no `localizedDescription`, so this is the one place the
     * original's `NSError` fallback had to be re-invented.
     */
    private fun describe(error: Exception): String = when (error) {
        is AppError -> error.message.orEmpty()
        else -> error.message ?: error::class.simpleName ?: "Unexpected failure"
    }
}
