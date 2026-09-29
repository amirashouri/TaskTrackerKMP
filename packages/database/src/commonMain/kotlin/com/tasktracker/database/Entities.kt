package com.tasktracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * One queued local change, waiting to reach the server.
 *
 * Deliberately stores the **whole** new state of the entity rather than a field-level
 * patch. The sync policy is server-wins, so the client never computes a merge; it just
 * replays the last known local state and lets the server's version win if it is newer.
 */
@Entity(tableName = "outbox")
data class OutboxRecord(
    @PrimaryKey val id: String,
    /**
     * Named `entityKind`, never `entity`.
     *
     * A property literally named `entity` compiles without complaint in SwiftData and
     * then corrupts the store: every insert aborts the process with "Could not cast
     * value of type 'NSEntityDescription' to 'NSString'", because Core Data remaps the
     * name onto the entity description itself instead of an attribute. Found the hard
     * way, so it is called out here rather than left as a naming quirk.
     */
    val entityKind: String,
    val entityID: String,
    val operation: String,
    /** JSON snapshot of the entity, or the bare id for a delete. */
    val payload: ByteArray,
    /**
     * Who queued the change. Optional and additive, so existing stores migrate in with
     * every row null; a null row is a legacy row and is claimable by any session.
     *
     * Scoping the queue by user is what lets sign-out keep unsynced work instead of
     * discarding it: the engine only claims rows that belong to the signed-in account,
     * so another user's queued work can sit here without any risk of being pushed into
     * the wrong account.
     */
    val userID: String?,
    val createdAt: Instant,
    val status: String,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val lastError: String?,
)

/**
 * How far a given scope has been synced.
 *
 * One row per scope (for example `"projects"` or `"workItems:<projectID>"`) so a partial
 * sync of one project does not advance the whole app's position.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataRecord(
    @PrimaryKey val scope: String,
    /** Opaque server cursor for the next pull. Null means "start from scratch". */
    val cursor: String?,
    val lastSyncedAt: Instant?,
    val lastPushedAt: Instant?,
    /** Monotonic server version of the last row we accepted, used for server-wins. */
    val serverVersion: Int,
    val lastError: String?,
)

/**
 * Who is signed in — **not** how.
 *
 * Tokens live in the keychain and are never written to the database. This is a cache of
 * who the last successful sign-in was, so the app can render "Signed in as ..." and pick
 * the right local data before the keychain has been read. Nothing here is a secret, and
 * nothing here can mint a session on its own.
 */
@Entity(tableName = "session")
data class SessionRecord(
    @PrimaryKey val userID: String,
    val email: String,
    val displayName: String?,
    val lastSignedInAt: Instant,
    /** The last known server version for this user's data, used to discard local rows
     * belonging to a different account. */
    val serverVersion: Int,
)
