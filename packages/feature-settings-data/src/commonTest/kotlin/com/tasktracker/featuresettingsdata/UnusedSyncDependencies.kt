package com.tasktracker.featuresettingsdata

import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.OutboxEntry
import com.tasktracker.database.OutboxQueue
import com.tasktracker.database.OutboxStatus
import com.tasktracker.database.SyncMetadata
import com.tasktracker.database.SyncMetadataStore
import com.tasktracker.sync.SyncConfiguration
import com.tasktracker.sync.SyncCoordinator
import com.tasktracker.sync.SyncEngine
import com.tasktracker.sync.SyncSchedule
import com.tasktracker.sync.SyncTransport
import com.tasktracker.sync.InMemorySyncTransport

/**
 * The minimum a [SyncCoordinator] needs to exist, for tests that never ask it to sync.
 *
 * Every body below throws. That is not laziness — it is the assertion: the connectivity tests
 * in this module construct a coordinator only because [LiveSettingsService]'s constructor takes
 * one, and a body that ran would mean the test had reached the sync half of the service, which
 * those tests deliberately do not cover. A `TODO()` returning a plausible empty value would be
 * worse, because it would let a test pass while quietly exercising something it claims not to.
 *
 * Deliberately not copied from `:packages:sync`'s own `InMemoryStores`: those implement real
 * behaviour for the engine's benefit, and duplicating the behaviour here would be a second
 * outbox to keep honest. This file is the difference between "a collaborator that exists" and
 * "a collaborator that works".
 */
private object UnusedOutbox : OutboxQueue {
    private fun unused(): Nothing = throw AssertionError("this test must not reach the sync engine")

    override suspend fun claim(limit: Int, maxAttempts: Int, userID: String?): List<OutboxEntry> = unused()

    override suspend fun complete(ids: List<String>) = unused()

    override suspend fun fail(ids: List<String>, message: String, maxAttempts: Int) = unused()

    override suspend fun recoverStrandedEntries(): Int = unused()

    override suspend fun release(ids: List<String>, message: String) = unused()

    override suspend fun entries(): List<OutboxEntry> = unused()

    override suspend fun count(status: OutboxStatus?): Int = unused()

    override suspend fun removeAll() = unused()

    override suspend fun enqueue(entry: NewOutboxEntry) = unused()
}

private object UnusedMetadata : SyncMetadataStore {
    private fun unused(): Nothing = throw AssertionError("this test must not reach the sync engine")

    override suspend fun metadata(scope: String): SyncMetadata? = unused()

    override suspend fun record(metadata: SyncMetadata) = unused()

    override suspend fun markSynced(scope: String, cursor: String?, at: kotlin.time.Instant) = unused()

    override suspend fun markPushed(scope: String, at: kotlin.time.Instant) = unused()

    override suspend fun recordFailure(scope: String, message: String) = unused()

    override suspend fun removeAll() = unused()
}

/**
 * A coordinator that can answer `currentStatus`, `isSyncing` and `canSyncNow` — the three
 * things [LiveSettingsService] reads without ever running a cycle — and would refuse to sync.
 *
 * `SyncSchedule.manual` so no periodic loop is even constructed, and the real
 * [InMemorySyncTransport] rather than a stub because it is already in `:packages:sync`'s main
 * source set and a transport is the one collaborator with no meaningful empty state.
 */
internal fun unusedSyncCoordinator(): SyncCoordinator = SyncCoordinator(
    engine = SyncEngine(
        outbox = UnusedOutbox,
        metadata = UnusedMetadata,
        transport = object : SyncTransport by InMemorySyncTransport() {},
        configuration = SyncConfiguration(),
    ),
    monitor = com.tasktracker.network.NetworkMonitor(
        watcher = object : com.tasktracker.network.ConnectivityWatcher {
            override fun attach(onStatus: (com.tasktracker.network.Connectivity) -> Unit) = Unit

            override fun start() = Unit

            override fun stop() = Unit
        },
    ),
    schedule = SyncSchedule.manual,
)