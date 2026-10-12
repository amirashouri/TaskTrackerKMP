import com.tasktracker.core.AppError
import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import com.tasktracker.core.Broadcaster
import com.tasktracker.database.NotificationRecord
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.featurenotificationsdomain.LocalNotifier
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationKind
import com.tasktracker.featurenotificationsdomain.NotificationPort
import com.tasktracker.featurenotificationsdomain.NotificationChange
import com.tasktracker.featurenotificationsdomain.Trigger
import com.tasktracker.featurenotificationsdomain.wireValue
import com.tasktracker.featurenotificationsdomain.createLocalNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * NotificationPort backed by Room.
 *
 * Writes go through the DAO and announce changes through a [Broadcaster] so
 * the inbox can react without polling. The change signal is cold — one
 * subscriber per collection — which is what [Broadcaster] provides.
 *
 * Also schedules local notifications via [LocalNotifier] for immediate user
 * awareness (mentions, assignments, due dates, status changes).
 */
class RoomNotificationRepository(
    private val database: TaskTrackerDatabase,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val localNotifier: LocalNotifier = createLocalNotifier(null),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : NotificationPort {

    private val changesBus = Broadcaster<NotificationChange>()

    override suspend fun notifications(userID: UserID): List<Notification> {
        val records = database.notificationDao().byUser(userID.stringValue)
        return records.map(::toDomain)
    }

    override suspend fun unreadCount(userID: UserID): Int {
        return database.notificationDao().unreadCount(userID.stringValue)
    }

    override fun changes(userID: UserID): Flow<NotificationChange> {
        return changesBus.stream()
            .filter { it.userID == userID }
    }

    override suspend fun createNotification(
        userID: UserID,
        kind: NotificationKind,
        payload: String,
        now: Instant,
    ): Notification {
        // Idempotent insert: check if already exists (pull path may re-deliver)
        val existing = database.notificationDao().byID(kind.wireValue + "-" + userID.stringValue)
        if (existing != null) {
            return toDomain(existing)
        }

        val record = NotificationRecord(
            id = "${kind.wireValue}-${userID.stringValue}-${now.toEpochMilliseconds()}",
            userID = userID.stringValue,
            kind = kind.wireValue,
            payload = payload,
            isRead = false,
            createdAt = now,
            updatedAt = now,
        )
        database.notificationDao().insert(record)
        announce(userID, NotificationChange.Kind.Notifications)

        // Schedule local notification for immediate awareness
        val notification = toDomain(record)
        scheduleLocalIfNeeded(notification)

        return notification
    }

    /**
     * Schedules a local notification if the kind warrants immediate user awareness.
     *
     * Kinds that trigger a local notification:
     * - MentionedInComment: someone mentioned you
     * - TaskAssigned: a task was assigned to you
     * - DueToday: a task is due today (fire 10 minutes before due time)
     * - StatusChanged: a task you're watching changed status
     *
     * Runs on the IO dispatcher to avoid blocking the database transaction.
     */
    private fun scheduleLocalIfNeeded(notification: Notification) {
        scope.launch(dispatcher) {
            when (notification.kind) {
                NotificationKind.MentionedInComment,
                NotificationKind.TaskAssigned,
                NotificationKind.StatusChanged -> {
                    // Fire immediately for mentions, assignments, and status changes
                    localNotifier.schedule(notification, Trigger.In(Duration.ZERO))
                }
                NotificationKind.DueToday -> {
                    // Parse due date from payload and fire 10 minutes before
                    // Using simple string parsing to avoid kotlinx.serialization API issues
                    try {
                        val dueDateMatch = "\"dueDate\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(notification.payload)
                        dueDateMatch?.groupValues?.get(1)?.let { dueDateStr ->
                            val dueInstant = Instant.parse(dueDateStr)
                            val triggerAt = dueInstant - 10.minutes
                            val now = Clock.System.now()
                            val trigger = if (triggerAt > now) {
                                Trigger.At(triggerAt)
                            } else {
                                Trigger.In(Duration.ZERO)
                            }
                            localNotifier.schedule(notification, trigger)
                        }
                    } catch (e: Exception) {
                        // If parsing fails, fire immediately as fallback
                        localNotifier.schedule(notification, Trigger.In(Duration.ZERO))
                    }
                }
            }
        }
    }

    override suspend fun setRead(id: NotificationID, isRead: Boolean, now: Instant): Notification {
        database.notificationDao().setRead(id.stringValue, isRead, now)
        // We need the userID to announce the change — read it back
        val record = database.notificationDao().byID(id.stringValue)
            ?: throw AppError.Storage("Notification $id no longer exists")
        announce(UserID(record.userID), NotificationChange.Kind.Notifications)
        return toDomain(record)
    }

    override suspend fun markAllRead(userID: UserID, now: Instant): Int {
        val count = database.notificationDao().markAllRead(userID.stringValue, now)
        if (count > 0) {
            announce(userID, NotificationChange.Kind.Notifications)
        }
        return count
    }

    override suspend fun deleteForUser(userID: UserID): Int {
        val count = database.notificationDao().deleteByUser(userID.stringValue)
        if (count > 0) {
            announce(userID, NotificationChange.Kind.Notifications)
        }
        return count
    }

    override suspend fun deleteOlderThan(cutoff: Instant): Int {
        return database.notificationDao().deleteOlderThan(cutoff)
    }

    private fun toDomain(record: NotificationRecord): Notification {
        return Notification(
            id = NotificationID(record.id),
            userID = UserID(record.userID),
            kind = NotificationKind.fromWireValue(record.kind) ?: NotificationKind.MentionedInComment,
            payload = record.payload,
            isRead = record.isRead,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }

    private fun announce(userID: UserID, kind: NotificationChange.Kind) {
        changesBus.send(NotificationChange(kind, userID))
    }
}