package com.tasktracker.database

import kotlin.time.Instant

/** What a queued change does to the server. */
enum class OutboxOperation(val wireValue: String) {
    Upsert("upsert"),
    Delete("delete"),
    ;

    companion object {
        /** A value written by a build that knew about a state this one does not. */
        fun parse(raw: String): OutboxOperation? = entries.firstOrNull { it.wireValue == raw }
    }
}

/**
 * Which kind of record a queued change refers to.
 *
 * A string rather than a Kotlin type so the queue can outlive a refactor of the feature
 * that produced it, and so `SyncService` can route a payload without importing the
 * feature that owns the entity.
 */
enum class OutboxEntity(val wireValue: String) {
    Project("project"),
    WorkItem("workItem"),
    /**
     * A sprint, queued like any other entity.
     *
     * The wire value is `sprint`, matching `WorkItemRecord`'s camelCase columns rather than
     * `ProjectRecord`'s singular convention — the same inconsistency that already exists
     * between `workItem` and `project`, and changing either is a protocol change.
     */
    Sprint("sprint"),
    ;

    companion object {
        fun parse(raw: String): OutboxEntity? = entries.firstOrNull { it.wireValue == raw }
    }
}

/** Lifecycle of a queued change. */
enum class OutboxStatus(val wireValue: String) {
    /** Waiting to be pushed. The only state a fresh record should be in. */
    Pending("pending"),

    /** Handed to the network layer right now. */
    InFlight("inFlight"),

    /** A transport or 5xx failure; will be retried. */
    Failed("failed"),

    /**
     * Gave up after exhausting attempts. Kept, never silently dropped, so the user can
     * see what did not make it.
     */
    Dead("dead"),
    ;

    companion object {
        fun parse(raw: String): OutboxStatus? = entries.firstOrNull { it.wireValue == raw }
    }
}

/**
 * A record was written by a newer build that knows about a value this one does not.
 *
 * Distinct from [AppError.Storage] on purpose: `SyncService` needs to tell "this one
 * queued row cannot be understood" apart from "the database is broken", and flattening
 * both into a storage failure would lose that.
 */
sealed class OutboxError(message: String) : Exception(message) {
    class UnknownEntity(val raw: String) :
        OutboxError("The queued change refers to an unknown record type ($raw).")

    class UnknownOperation(val raw: String) :
        OutboxError("The queued change has an unknown operation ($raw).")

    class UnknownStatus(val raw: String) :
        OutboxError("The queued change has an unknown status ($raw).")
}

/**
 * A sendable view of an [OutboxRecord].
 *
 * `SyncService` consumes this, never the entity type, which is what keeps Room out of
 * the sync layer entirely.
 *
 * Not a `data class`: [payload] is a `ByteArray`, and generated equality on one compares
 * by identity, so `entry == copyOfEntry` would be false for two rows holding the same
 * JSON and any test relying on it would quietly be testing nothing. [equals] compares
 * the bytes.
 */
class OutboxEntry(
    val id: String,
    val entity: OutboxEntity,
    val entityID: String,
    val operation: OutboxOperation,
    val payload: ByteArray,
    /**
     * The account that queued the change, or null for a legacy row written before the
     * queue was user-scoped. Carried so a transport can attribute the push, and so a test
     * can assert scoping without decoding payloads.
     */
    val userID: String?,
    val createdAt: Instant,
    val status: OutboxStatus,
    val attemptCount: Int,
    val lastError: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboxEntry) return false
        return id == other.id &&
            entity == other.entity &&
            entityID == other.entityID &&
            operation == other.operation &&
            payload.contentEquals(other.payload) &&
            userID == other.userID &&
            createdAt == other.createdAt &&
            status == other.status &&
            attemptCount == other.attemptCount &&
            lastError == other.lastError
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + entity.hashCode()
        result = 31 * result + entityID.hashCode()
        result = 31 * result + operation.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (userID?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + attemptCount
        result = 31 * result + (lastError?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "OutboxEntry(id=$id, entity=$entity, entityID=$entityID, operation=$operation, " +
            "userID=$userID, createdAt=$createdAt, status=$status, " +
            "attemptCount=$attemptCount, lastError=$lastError)"
}

/** A change to queue, before it becomes a row. */
class NewOutboxEntry(
    val id: String,
    val entity: OutboxEntity,
    val entityID: String,
    val operation: OutboxOperation,
    val payload: ByteArray,
    val createdAt: Instant,
    /**
     * Who queued the change, or null when there is no session to attribute it to — the
     * same honest blank as `StatusChange.changedBy`.
     */
    val userID: String? = null,
) {
    fun toRecord(): OutboxRecord =
        OutboxRecord(
            id = id,
            entityKind = entity.wireValue,
            entityID = entityID,
            operation = operation.wireValue,
            payload = payload,
            userID = userID,
            createdAt = createdAt,
            status = OutboxStatus.Pending.wireValue,
            attemptCount = 0,
            lastAttemptAt = null,
            lastError = null,
        )
}

/**
 * Decodes a stored row, refusing anything this build does not recognise.
 *
 * @throws OutboxError if a newer build wrote a state this one cannot understand. Reading
 * is where that surfaces, deliberately: the alternative is a `TypeConverter` throwing out
 * of SQLite with no indication of which row or which value was at fault.
 */
internal fun OutboxRecord.toEntry(): OutboxEntry =
    OutboxEntry(
        id = id,
        entity = OutboxEntity.parse(entityKind)
            ?: throw OutboxError.UnknownEntity(entityKind),
        entityID = entityID,
        operation = OutboxOperation.parse(operation)
            ?: throw OutboxError.UnknownOperation(operation),
        payload = payload,
        userID = userID,
        createdAt = createdAt,
        status = OutboxStatus.parse(status)
            ?: throw OutboxError.UnknownStatus(status),
        attemptCount = attemptCount,
        lastError = lastError,
    )
