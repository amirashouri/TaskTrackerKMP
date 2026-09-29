package com.tasktracker.database

import kotlin.time.Instant

/**
 * A sendable view of a [SyncMetadataRecord]. One per scope, so a partial sync of one
 * project does not advance the whole app's position.
 */
class SyncMetadata(
    val scope: String,
    val cursor: String?,
    val lastSyncedAt: Instant?,
    val lastPushedAt: Instant?,
    val serverVersion: Int,
    val lastError: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncMetadata) return false
        return scope == other.scope &&
            cursor == other.cursor &&
            lastSyncedAt == other.lastSyncedAt &&
            lastPushedAt == other.lastPushedAt &&
            serverVersion == other.serverVersion &&
            lastError == other.lastError
    }

    override fun hashCode(): Int {
        var result = scope.hashCode()
        result = 31 * result + (cursor?.hashCode() ?: 0)
        result = 31 * result + (lastSyncedAt?.hashCode() ?: 0)
        result = 31 * result + (lastPushedAt?.hashCode() ?: 0)
        result = 31 * result + serverVersion
        result = 31 * result + (lastError?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "SyncMetadata(scope=$scope, cursor=$cursor, lastSyncedAt=$lastSyncedAt, " +
            "lastPushedAt=$lastPushedAt, serverVersion=$serverVersion, lastError=$lastError)"
}

internal fun SyncMetadataRecord.toMetadata(): SyncMetadata =
    SyncMetadata(
        scope = scope,
        cursor = cursor,
        lastSyncedAt = lastSyncedAt,
        lastPushedAt = lastPushedAt,
        serverVersion = serverVersion,
        lastError = lastError,
    )

/** How far each sync scope has progressed. */
interface SyncMetadataStore {
    suspend fun metadata(scope: String): SyncMetadata?

    suspend fun record(metadata: SyncMetadata)

    suspend fun markSynced(scope: String, cursor: String?, at: Instant)

    suspend fun markPushed(scope: String, at: Instant)

    suspend fun recordFailure(scope: String, message: String)

    suspend fun removeAll()
}

class RoomSyncMetadataStore(
    private val database: TaskTrackerDatabase,
) : SyncMetadataStore {
    private val dao: SyncMetadataDao get() = database.syncMetadataDao()

    override suspend fun metadata(scope: String): SyncMetadata? = inTransaction(database) {
        dao.byScope(scope)?.toMetadata()
    }

    /**
     * Replaces the row for that scope wholesale.
     *
     * Last write wins, including for `serverVersion`: recording an older snapshot
     * rewinds it. That is what the original did, and it is the caller's job to only
     * record what it has accepted — this store does not second-guess the order it is
     * handed. (`serverVersion` is monotonic *on the server*; the local copy is a cache.)
     */
    override suspend fun record(metadata: SyncMetadata) {
        inTransaction(database) { dao.upsert(metadata.toRecord()) }
    }

    override suspend fun markSynced(scope: String, cursor: String?, at: Instant) {
        inTransaction(database) {
            dao.upsert(
                recordFor(scope).copy(cursor = cursor, lastSyncedAt = at, lastError = null),
            )
        }
    }

    override suspend fun markPushed(scope: String, at: Instant) {
        inTransaction(database) {
            dao.upsert(recordFor(scope).copy(lastPushedAt = at, lastError = null))
        }
    }

    override suspend fun recordFailure(scope: String, message: String) {
        inTransaction(database) { dao.upsert(recordFor(scope).copy(lastError = message)) }
    }

    override suspend fun removeAll() {
        inTransaction(database) { dao.deleteAll() }
    }

    /**
     * Fetch-or-create, so every mutating path shares one upsert.
     *
     * Read and write inside the caller's transaction, so two concurrent marks for the
     * same scope cannot both create a row and race on which one wins — the primary key
     * would reject the loser instead.
     */
    private suspend fun recordFor(scope: String): SyncMetadataRecord =
        dao.byScope(scope) ?: SyncMetadataRecord(
            scope = scope,
            cursor = null,
            lastSyncedAt = null,
            lastPushedAt = null,
            serverVersion = 0,
            lastError = null,
        )
}

private fun SyncMetadata.toRecord(): SyncMetadataRecord =
    SyncMetadataRecord(
        scope = scope,
        cursor = cursor,
        lastSyncedAt = lastSyncedAt,
        lastPushedAt = lastPushedAt,
        serverVersion = serverVersion,
        lastError = lastError,
    )

/** A sendable view of a [SessionRecord]. */
class CachedSession(
    val userID: String,
    val email: String,
    val displayName: String?,
    val lastSignedInAt: Instant,
    val serverVersion: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedSession) return false
        return userID == other.userID &&
            email == other.email &&
            displayName == other.displayName &&
            lastSignedInAt == other.lastSignedInAt &&
            serverVersion == other.serverVersion
    }

    override fun hashCode(): Int {
        var result = userID.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + lastSignedInAt.hashCode()
        result = 31 * result + serverVersion
        return result
    }

    override fun toString(): String =
        "CachedSession(userID=$userID, email=$email, displayName=$displayName, " +
            "lastSignedInAt=$lastSignedInAt, serverVersion=$serverVersion)"
}

internal fun SessionRecord.toCachedSession(): CachedSession =
    CachedSession(
        userID = userID,
        email = email,
        displayName = displayName,
        lastSignedInAt = lastSignedInAt,
        serverVersion = serverVersion,
    )

/**
 * The cached identity of the last successful sign-in.
 *
 * No tokens. The keychain owns those; see [SessionRecord] for why.
 */
interface SessionCache {
    suspend fun current(): CachedSession?

    suspend fun save(session: CachedSession)

    suspend fun clear()
}

class RoomSessionCache(
    private val database: TaskTrackerDatabase,
) : SessionCache {
    private val dao: SessionDao get() = database.sessionDao()

    override suspend fun current(): CachedSession? = inTransaction(database) {
        dao.current()?.toCachedSession()
    }

    override suspend fun save(session: CachedSession) {
        inTransaction(database) {
            dao.upsert(
                SessionRecord(
                    userID = session.userID,
                    email = session.email,
                    displayName = session.displayName,
                    lastSignedInAt = session.lastSignedInAt,
                    serverVersion = session.serverVersion,
                ),
            )
        }
    }

    override suspend fun clear() {
        inTransaction(database) { dao.deleteAll() }
    }
}
