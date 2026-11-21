package com.tasktracker.featurenotificationsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.Broadcaster
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import com.tasktracker.core.randomNotificationID
import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.NotificationRecord
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.database.inTransaction
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationChange
import com.tasktracker.featurenotificationsdomain.NotificationKind
import com.tasktracker.featurenotificationsdomain.NotificationPort
import com.tasktracker.featurenotificationsdomain.wireValue
import com.tasktracker.sync.RemoteChange
import com.tasktracker.sync.SyncApplySink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

/**
 * [NotificationPort] and [SyncApplySink] over the same Room table.
 *
 * ## One object, two protocols, and the split is real
 *
 * The port is the inbox's read/write path. The sink is the **pull** path, and it deliberately
 * never touches the outbox: a pulled notification that queued an outbox row would turn into a
 * push, which the server answers `rejected` because a client cannot create a notification.
 * Local read/dismiss writes queue their own rows; a remote apply does not.
 *
 * ## A notification is server-authored
 *
 * Only the server mints notifications. A pulled document is applied **server-wins**: an existing
 * row is overwritten with the delivered document, and an id this device has never seen is
 * inserted (it is the first delivery of that row). The reverse direction is narrower — the
 * client may change `isRead` or dismiss, and the queued upsert carries the whole document
 * because that is what the server's sync log re-serves to every other device.
 */
class RoomNotificationRepository(
    private val database: TaskTrackerDatabase,
) : NotificationPort, SyncApplySink {

    private val changesBus = Broadcaster<NotificationChange>()
    private val arrivalsBus = Broadcaster<Notification>()

    private val dao get() = database.notificationDao()

    // MARK: - Reads

    override suspend fun notifications(userID: UserID): List<Notification> =
        dao.byUser(userID.stringValue).map(::toDomain)

    override suspend fun unreadCount(userID: UserID): Int =
        dao.unreadCount(userID.stringValue)

    override suspend fun notification(id: NotificationID): Notification? =
        dao.byID(id.stringValue)?.let(::toDomain)

    override fun changes(userID: UserID): Flow<NotificationChange> =
        changesBus.stream().filter { it.userID == userID }

    override fun notificationsStream(userID: UserID): Flow<List<Notification>> = flow {
        emit(notifications(userID))
        changes(userID).collect { emit(notifications(userID)) }
    }

    override fun unreadCountStream(userID: UserID): Flow<Int> = flow {
        emit(unreadCount(userID))
        changes(userID).collect { emit(unreadCount(userID)) }
    }

    override fun arrivals(userID: UserID): Flow<Notification> =
        arrivalsBus.stream().filter { it.userID == userID }

    // MARK: - Local writes

    /**
     * Stores a locally minted notification.
     *
     * The server generates the inbox, so this does **not** queue an outbox row: a pushed
     * client-created notification is `rejected`, and dead-lettering it would be noise. It is
     * kept for tests and for a future local reminder, where a notification is a device concept.
     */
    override suspend fun createNotification(
        userID: UserID,
        kind: NotificationKind,
        payload: String,
        now: Instant,
    ): Notification {
        val record = NotificationRecord(
            id = randomNotificationID().stringValue,
            userID = userID.stringValue,
            kind = kind.wireValue,
            payload = payload,
            isRead = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(record)
        announce(userID)
        return toDomain(record)
    }

    override suspend fun setRead(id: NotificationID, isRead: Boolean, now: Instant): Notification {
        val stored = inTransaction(database) {
            val record = dao.byID(id.stringValue)
                ?: throw AppError.Storage("Notification $id no longer exists")
            val next = record.copy(isRead = isRead, updatedAt = now)
            dao.setRead(id.stringValue, isRead, now)
            enqueueUpsert(next, now)
            next
        }
        announce(UserID(stored.userID))
        return toDomain(stored)
    }

    override suspend fun markAllRead(userID: UserID, now: Instant): Int {
        val unread = dao.byUser(userID.stringValue).filterNot { it.isRead }
        if (unread.isEmpty()) return 0
        inTransaction(database) {
            for (record in unread) {
                dao.setRead(record.id, true, now)
                enqueueUpsert(record.copy(isRead = true, updatedAt = now), now)
            }
        }
        announce(userID)
        return unread.size
    }

    override suspend fun deleteNotification(id: NotificationID, now: Instant): Boolean {
        val stored = inTransaction(database) {
            val record = dao.byID(id.stringValue) ?: return@inTransaction null
            dao.deleteByID(id.stringValue)
            enqueueDelete(record.id, record.userID, now)
            record
        } ?: return false
        announce(UserID(stored.userID))
        return true
    }

    /** Sign-out cleanup. Local only: the server keeps the account's rows. */
    override suspend fun deleteForUser(userID: UserID): Int {
        val count = dao.deleteByUser(userID.stringValue)
        if (count > 0) announce(userID)
        return count
    }

    /** Retention cleanup. Local only, for the same reason as [deleteForUser]. */
    override suspend fun deleteOlderThan(cutoff: Instant): Int = dao.deleteOlderThan(cutoff)

    // MARK: - Pull

    override suspend fun apply(change: RemoteChange) {
        when (change.operation) {
            OutboxOperation.Delete -> {
                val existing = dao.byID(change.entityID) ?: return
                inTransaction(database) { dao.deleteByID(change.entityID) }
                announce(UserID(existing.userID))
            }

            OutboxOperation.Upsert -> {
                val incoming = decode(change.payload)
                    ?: throw AppError.Decoding(
                        "A notification change did not carry a decodable notification.",
                    )

                val inserted = inTransaction(database) {
                    val existing = dao.byID(incoming.id.stringValue)
                    if (existing == null) {
                        dao.insert(toRecord(incoming))
                        true
                    } else {
                        // Server-wins, but only forward in time: a replayed page must not
                        // overwrite a newer local read flag with an older delivered one.
                        if (incoming.updatedAt >= existing.updatedAt) {
                            dao.updateFromRemote(
                                id = incoming.id.stringValue,
                                kind = incoming.kind.wireValue,
                                payload = incoming.payload,
                                isRead = incoming.isRead,
                                createdAt = incoming.createdAt,
                                updatedAt = incoming.updatedAt,
                            )
                        }
                        false
                    }
                }

                announce(incoming.userID)
                if (inserted && !incoming.isRead) arrivalsBus.send(incoming)
            }
        }
    }

    // MARK: - Helpers

    private suspend fun enqueueUpsert(record: NotificationRecord, now: Instant) {
        val payload = JSONCoding.json.encodeToString(Notification.serializer(), toDomain(record))
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(OutboxEntity.Notification, record.id, OutboxOperation.Upsert),
                entity = OutboxEntity.Notification,
                entityID = record.id,
                operation = OutboxOperation.Upsert,
                payload = payload.encodeToByteArray(),
                createdAt = now,
                userID = record.userID,
            ).toRecord(),
        )
    }

    private suspend fun enqueueDelete(id: String, userID: String, now: Instant) {
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(OutboxEntity.Notification, id, OutboxOperation.Delete),
                entity = OutboxEntity.Notification,
                entityID = id,
                operation = OutboxOperation.Delete,
                payload = id.encodeToByteArray(),
                createdAt = now,
                userID = userID,
            ).toRecord(),
        )
    }

    private fun newOutboxID(
        entity: OutboxEntity,
        entityID: String,
        operation: OutboxOperation,
    ): String = "${entity.wireValue}:$entityID:${operation.wireValue}:${randomNotificationID()}"

    /**
     * Unwraps the payload the way the sync log stores it.
     *
     * The server re-serves a change's payload verbatim, and this client's transport declares
     * it as a JSON string. A pull therefore hands back `"{\"id\":…}"`, one level of quoting
     * around the document. The sync log also has rows from before that convention for other
     * entities, so both forms are accepted here rather than assuming one.
     */
    private fun decode(payload: ByteArray): Notification? {
        val text = payload.decodeToString()
        val unwrapped = runCatching {
            JSONCoding.json.decodeFromString(String.serializer(), text)
        }.getOrNull() ?: text
        return runCatching {
            JSONCoding.json.decodeFromString(Notification.serializer(), unwrapped)
        }.getOrNull()
    }

    private fun toRecord(notification: Notification): NotificationRecord = NotificationRecord(
        id = notification.id.stringValue,
        userID = notification.userID.stringValue,
        kind = notification.kind.wireValue,
        payload = notification.payload,
        isRead = notification.isRead,
        createdAt = notification.createdAt,
        updatedAt = notification.updatedAt,
    )

    private fun toDomain(record: NotificationRecord): Notification = Notification(
        id = NotificationID(record.id),
        userID = UserID(record.userID),
        kind = NotificationKind.fromWireValue(record.kind) ?: NotificationKind.MentionedInComment,
        payload = record.payload,
        isRead = record.isRead,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
    )

    private suspend fun announce(userID: UserID) {
        changesBus.send(NotificationChange(NotificationChange.Kind.Notifications, userID))
    }
}
