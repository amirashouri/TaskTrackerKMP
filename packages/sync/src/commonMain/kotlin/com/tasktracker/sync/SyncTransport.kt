package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxEntry
import com.tasktracker.database.OutboxOperation

/**
 * What the server did with one queued change.
 *
 * The distinction between these four is the whole retry policy, so it is modelled
 * explicitly instead of being collapsed into a success flag. In particular [Conflict] is
 * a *success* for the queue: the server's copy won, the local change is resolved, and the
 * next pull brings the server's version down. Treating it as a failure would retry a
 * change the server has already decided, forever.
 */
sealed interface PushOutcome {
    /** Applied. */
    data object Accepted : PushOutcome

    /** The server's version is newer and has won. Resolved, not retryable. */
    data class Conflict(val serverVersion: String?) : PushOutcome

    /**
     * Permanently invalid. Retrying cannot help, so this dead-letters immediately instead
     * of burning the remaining attempts on a guaranteed failure.
     */
    data class Rejected(val reason: String) : PushOutcome

    /** A transport failure or 5xx. The entry goes back for another attempt. */
    data class RetryLater(val reason: String) : PushOutcome
}

/**
 * The server's verdict on one change, tied to the change it refers to.
 *
 * The entry id travels with the outcome rather than being implied by position. A transport
 * that returns a bare `List<PushOutcome>` forces the engine to assume the server answered
 * in request order, and an out-of-order or partial reply then attaches "accepted" to the
 * wrong change — which loses a user's edit with no error anywhere. Matching on an explicit
 * id fails loudly instead.
 */
data class PushResult(val entryID: String, val outcome: PushOutcome) {
    companion object {
        fun accepted(entryID: String): PushResult = PushResult(entryID, PushOutcome.Accepted)
    }
}

/**
 * One change the server has that this device may not.
 *
 * **Not a data class**: [payload] is a `ByteArray`, and generated equality compares arrays
 * by identity, so two changes carrying the same JSON would compare unequal and a test
 * asserting on an applied page would be testing nothing. The third array-identity trap in
 * the port, after `HttpRequest` and `OutboxEntry`.
 */
class RemoteChange(
    val entity: OutboxEntity,
    val entityID: String,
    val operation: OutboxOperation,
    /** The entity's new state, or the bare id for a delete. Opaque here: this module has no
     * business knowing the shape of a `WorkItem`, and the sink that applies it does. */
    val payload: ByteArray,
    val version: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteChange) return false
        return entity == other.entity &&
            entityID == other.entityID &&
            operation == other.operation &&
            payload.contentEquals(other.payload) &&
            version == other.version
    }

    override fun hashCode(): Int {
        var result = entity.hashCode()
        result = 31 * result + entityID.hashCode()
        result = 31 * result + operation.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (version?.hashCode() ?: 0)
        return result
    }

    /** Deliberately omits [payload]: it is opaque here and can hold a whole work item. */
    override fun toString(): String =
        "RemoteChange(entity=$entity, entityID=$entityID, operation=$operation, " +
            "version=$version, payloadSize=${payload.size})"
}

/** One page of a pull. */
data class PullPage(
    val changes: List<RemoteChange>,
    /** Opaque resume token. Only the server knows how to order its log, so the engine
     * stores and returns it without interpreting it. */
    val nextCursor: String?,
    val hasMore: Boolean,
    /**
     * The server has dropped changes this cursor had not yet read, so [changes] is a partial
     * view of the gap rather than a contiguous run. The cursor is no longer a position in a
     * log that still exists, and applying this page would advance past changes that are gone
     * for good — so the engine must start over from the beginning of what survives.
     *
     * Defaults to `false` because the server omits the field when there is nothing to
     * resync, and because a server old enough not to know it is also saying the same thing.
     */
    val resyncRequired: Boolean = false,
) {
    companion object {
        val empty: PullPage = PullPage(changes = emptyList(), nextCursor = null, hasMore = false)
    }
}

/**
 * Talks to the server on the engine's behalf.
 *
 * An interface rather than a concrete HTTP client, so the engine's logic — ordering, retry
 * classification, cursor handling, crash recovery — is testable without a server. This is
 * the same reasoning as `HttpClient` sitting in front of the transport in
 * `:packages:network`.
 */
interface SyncTransport {
    /**
     * Applies queued changes **in the order given**.
     *
     * Order is a hard requirement, not a preference: the outbox deliberately claims
     * oldest-first because the server applies a stream sequentially, and "update task 7"
     * arriving before "create task 7" is rejected as an unknown id.
     *
     * @return one result per change, in any order. A change the server does not mention has
     * an unknown fate and will be retried, never assumed to have landed.
     */
    suspend fun push(entries: List<OutboxEntry>): List<PushResult>

    suspend fun pull(scope: SyncScope, cursor: String?, limit: Int): PullPage

    /**
     * Uploads the raw bytes for an attachment.
     *
     * The metadata row is expected to already exist on the server — it travels the change
     * feed like any other entity — so this is the file content only. The body is the raw
     * bytes, **not** a multipart envelope: the server reads the request body directly and
     * caps it at its own limit, answering `413` past it.
     *
     * The default refuses rather than no-ops, because a transport with no server behind it
     * (the in-memory demo) has nowhere to put bytes. A caller that reaches this default has
     * wired an upload against a store that cannot honour it, and silently "succeeding" would
     * leave the attachment permanently un-synced with nothing to show for it.
     */
    suspend fun uploadAttachmentBlob(
        projectID: String,
        workItemID: String,
        attachmentID: String,
        bytes: ByteArray,
    ) {
        throw AppError.Transport("This build has no server to store attachment bytes.")
    }

    /**
     * Downloads the raw bytes for an attachment.
     *
     * Throws [AppError.NotFound] when the server has no blob for it — an attachment whose
     * metadata arrived before its bytes were uploaded is a legitimate state, not a bug, and
     * the caller retries later rather than reporting the attachment as missing.
     */
    suspend fun downloadAttachmentBlob(
        projectID: String,
        workItemID: String,
        attachmentID: String,
    ): ByteArray {
        throw AppError.Transport("This build has no server to fetch attachment bytes from.")
    }
}

/**
 * Applies a server change to the local store.
 *
 * Implemented by whichever feature owns the entity, which is why this is an interface:
 * `:packages:sync` routes opaque payloads to the feature that can decode them and never
 * imports that feature itself.
 */
interface SyncApplySink {
    /**
     * Applies one change, server-wins. Must be idempotent, because a cursor can only be
     * advanced after the page is applied, so an interrupted cycle re-delivers the same
     * page on the next run.
     */
    suspend fun apply(change: RemoteChange)
}

/** A failure this module raises, as opposed to one the network layer raised. */
sealed class SyncException(message: String) : Exception(message) {
    /**
     * A change arrived for an entity no sink claims. A wiring bug, not a data problem,
     * and deliberately fatal for the scope it arrives on — see [SyncEngine.pull].
     */
    class NoSink(val entity: OutboxEntity) :
        SyncException("No local store is registered to apply incoming ${entity.wireValue} changes.")
}
