package com.tasktracker.featurenotificationsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import com.tasktracker.core.parseNotificationID
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.OutboxRecord
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.featurenotificationsdomain.LocalNotifier
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationKind
import com.tasktracker.featurenotificationsdomain.Trigger
import com.tasktracker.sync.RemoteChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The notification repository, through both protocols it implements.
 *
 * There are two write paths and they intentionally differ:
 *
 * - **Local writes** (`setRead`, `deleteNotification`, `markAllRead`) queue an outbox row,
 *   because the change has to reach the server and every other device.
 * - **Pulled writes** (`apply`) never queue anything. A notification is server-authored, so
 *   turning a pulled notification into a push would make the server reject a change the
 *   client was never allowed to create.
 *
 * Most tests therefore assert on the outbox as well as the returned value. A repository that
 * stored a read flag locally and reported success while silently failing to push it is the
 * failure the outbox exists to prevent.
 *
 * The platform notifier is recorded here too. It sits on the same `apply` path as the row
 * write, and the two cases that matter are easy to get backwards: a newly delivered unread
 * notification is shown, while a read flag arriving from another device cancels the banner
 * instead of showing one.
 */
class RoomNotificationRepositoryTest {

    // MARK: Pulling notifications

    @Test
    fun deliveredNotificationsAreStoredUnreadNewestFirstAndNeverQueuedForPush() = runTest {
        val harness = Harness()
        val older = aNotification(
            id = firstNotificationID,
            kind = NotificationKind.CommentAdded,
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )
        val newer = aNotification(id = secondNotificationID)

        harness.repository.apply(anUpsert(older))
        harness.repository.apply(anUpsert(newer))

        assertEquals(listOf(newer, older), harness.repository.notifications(adaID))
        assertEquals(2, harness.repository.unreadCount(adaID))
        assertEquals(newer, harness.repository.notification(newer.id))

        // A pulled notification is not a client change. Queueing one would ask the server to
        // create a row it generated itself.
        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun aNewlyDeliveredUnreadNotificationReachesThePlatformNotifierOnce() = runTest {
        val harness = Harness()
        val incoming = aNotification()

        harness.repository.apply(anUpsert(incoming))

        val shown = harness.notifier.scheduled.single()
        assertEquals(incoming, shown.notification)
        assertEquals(Trigger.In(Duration.ZERO), shown.trigger)

        // A replayed page is idempotent: the row must not be inserted twice, and the user
        // must not be interrupted by the same notification a second time.
        harness.repository.apply(anUpsert(incoming))
        assertEquals(1, harness.notifier.scheduled.size)
    }

    @Test
    fun aNotificationDeliveredAlreadyReadIsNeitherAnArrivalNorABanner() = runTest {
        val harness = Harness()
        val arrivals = mutableListOf<Notification>()
        collectInto(harness.repository.arrivals(adaID), arrivals)

        harness.repository.apply(anUpsert(aNotification(isRead = true)))

        assertEquals(emptyList(), arrivals)
        assertEquals(emptyList(), harness.notifier.scheduled)
        assertEquals(0, harness.repository.unreadCount(adaID))
    }

    @Test
    fun anArrivalIsAnnouncedToItsRecipientAndNobodyElse() = runTest {
        val harness = Harness()
        val forAda = aNotification(id = firstNotificationID, userID = adaID)
        val forGrace = aNotification(id = secondNotificationID, userID = graceID)
        val adaArrivals = mutableListOf<Notification>()
        val graceArrivals = mutableListOf<Notification>()
        collectInto(harness.repository.arrivals(adaID), adaArrivals)
        collectInto(harness.repository.arrivals(graceID), graceArrivals)

        harness.repository.apply(anUpsert(forAda))
        harness.repository.apply(anUpsert(forGrace))

        assertEquals(listOf(forAda), adaArrivals)
        assertEquals(listOf(forGrace), graceArrivals)
    }

    @Test
    fun aReadDeliveredFromAnotherDeviceClearsThisDevicesBannerWithoutQueueingAPush() = runTest {
        val harness = Harness()
        val delivered = aNotification()
        harness.repository.apply(anUpsert(delivered))
        harness.notifier.cancelled.clear()

        harness.repository.apply(
            anUpsert(delivered.copy(isRead = true, updatedAt = Moments.muchLater)),
        )

        assertEquals(listOf(delivered.id), harness.notifier.cancelled)
        // Only the first delivery scheduled a banner. The read update cancels it rather than
        // putting a second one on screen.
        assertEquals(1, harness.notifier.scheduled.size)
        assertEquals(true, harness.repository.notification(delivered.id)?.isRead)
        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun anOlderReplayDoesNotOverwriteANewerLocalRead() = runTest {
        val harness = Harness()
        val delivered = aNotification()
        harness.repository.apply(anUpsert(delivered))
        val markedRead = harness.repository.setRead(delivered.id, true, Moments.muchLater)
        val queuedBeforeReplay = harness.queueDepth()
        harness.notifier.cancelled.clear()

        harness.repository.apply(
            anUpsert(
                delivered.copy(
                    payload = """{"workItemID":"stale"}""",
                    isRead = false,
                    updatedAt = Moments.later,
                ),
            ),
        )

        // The room's sync page can be replayed after a crash, and an older document must not
        // undo a read the user has already made on this device.
        assertEquals(markedRead, harness.repository.notification(delivered.id))
        assertEquals(queuedBeforeReplay, harness.queueDepth())
        assertTrue(harness.notifier.cancelled.isEmpty())
    }

    @Test
    fun aDeliveredDeleteDismissesTheRowWithoutQueueingAndIsIdempotent() = runTest {
        val harness = Harness()
        val delivered = aNotification()
        harness.repository.apply(anUpsert(delivered))
        harness.notifier.cancelled.clear()
        val dismissal = aDelete(delivered.id)

        harness.repository.apply(dismissal)

        assertNull(harness.repository.notification(delivered.id))
        assertEquals(listOf(delivered.id), harness.notifier.cancelled)
        // Dismissing on another device is not an edit this device has to push back.
        assertEquals(0, harness.queueDepth())

        // An interrupted page can be replayed, so a second delete must be a no-op rather
        // than a second cancellation or a queued change.
        harness.repository.apply(dismissal)
        assertEquals(listOf(delivered.id), harness.notifier.cancelled)
        assertEquals(0, harness.queueDepth())
    }

    // MARK: Local read and dismiss writes

    @Test
    fun markingReadStoresTheFlagAndQueuesTheWholeNotificationDocument() = runTest {
        val harness = Harness()
        val delivered = aNotification()
        harness.repository.apply(anUpsert(delivered))
        harness.notifier.cancelled.clear()

        val read = harness.repository.setRead(delivered.id, true, Moments.muchLater)

        assertTrue(read.isRead)
        assertEquals(Moments.muchLater, read.updatedAt)
        assertEquals(read, harness.repository.notification(delivered.id))
        assertEquals(listOf(delivered.id), harness.notifier.cancelled)

        val queued = harness.queued().single()
        assertEquals(OutboxEntity.Notification.wireValue, queued.entityKind)
        assertEquals(delivered.id.stringValue, queued.entityID)
        assertEquals(OutboxOperation.Upsert.wireValue, queued.operation)
        assertEquals(adaID.stringValue, queued.userID)
        // The server re-serves this payload to the account's other devices, so it has to be
        // the full document rather than a patch containing only `isRead`.
        assertEquals(read, decodeQueued(queued))
    }

    @Test
    fun markingUnreadQueuesTheChangeButLeavesTheBannerAlone() = runTest {
        val harness = Harness()
        val delivered = aNotification()
        harness.repository.apply(anUpsert(delivered))
        harness.notifier.cancelled.clear()

        harness.repository.setRead(delivered.id, false, Moments.muchLater)

        assertTrue(harness.notifier.cancelled.isEmpty())
        assertFalse(decodeQueued(harness.queued().single()).isRead)
    }

    @Test
    fun markingAnUnknownNotificationReadFailsAndQueuesNothing() = runTest {
        val harness = Harness()

        assertFailsWith<AppError.Storage> {
            harness.repository.setRead(firstNotificationID, true, Moments.start)
        }

        assertEquals(0, harness.queueDepth())
        assertTrue(harness.notifier.cancelled.isEmpty())
    }

    @Test
    fun dismissingAStoredNotificationQueuesTheBareIdAndCancelsItsBanner() = runTest {
        val harness = Harness()
        val delivered = aNotification()
        harness.repository.apply(anUpsert(delivered))
        harness.notifier.cancelled.clear()

        val dismissed = harness.repository.deleteNotification(delivered.id, Moments.muchLater)

        assertTrue(dismissed)
        assertNull(harness.repository.notification(delivered.id))
        assertEquals(listOf(delivered.id), harness.notifier.cancelled)

        val queued = harness.queued().single()
        assertEquals(OutboxEntity.Notification.wireValue, queued.entityKind)
        assertEquals(delivered.id.stringValue, queued.entityID)
        assertEquals(OutboxOperation.Delete.wireValue, queued.operation)
        assertEquals(delivered.id.stringValue, queued.payload.decodeToString())
    }

    @Test
    fun dismissingAnUnknownNotificationAnswersFalseWithoutQueueingAnything() = runTest {
        val harness = Harness()

        val dismissed = harness.repository.deleteNotification(firstNotificationID, Moments.start)

        assertFalse(dismissed)
        assertEquals(0, harness.queueDepth())
        assertTrue(harness.notifier.cancelled.isEmpty())
    }

    @Test
    fun markingAllReadQueuesOnlyTheUnreadRowsAndCancelsEveryBanner() = runTest {
        val harness = Harness()
        val first = aNotification(
            id = firstNotificationID,
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )
        val second = aNotification(id = secondNotificationID)
        val alreadyRead = aNotification(
            id = thirdNotificationID,
            isRead = true,
            createdAt = Moments.muchLater,
            updatedAt = Moments.muchLater,
        )
        listOf(first, second, alreadyRead).forEach {
            harness.repository.apply(anUpsert(it))
        }
        harness.notifier.cancelled.clear()

        val marked = harness.repository.markAllRead(adaID, Moments.muchLater)

        assertEquals(2, marked)
        assertEquals(0, harness.repository.unreadCount(adaID))
        assertEquals(1, harness.notifier.cancelAllCount)

        val queued = harness.queued()
        assertEquals(2, queued.size)
        assertEquals(
            setOf(first.id.stringValue, second.id.stringValue),
            queued.map { it.entityID }.toSet(),
        )
        assertTrue(
            queued.all {
                it.entityKind == OutboxEntity.Notification.wireValue &&
                    it.operation == OutboxOperation.Upsert.wireValue
            },
        )
        assertTrue(queued.all { decodeQueued(it).isRead })
        assertTrue(queued.all { decodeQueued(it).updatedAt == Moments.muchLater })
    }

    @Test
    fun markingAllReadWhenEverythingIsAlreadyReadTouchesNothing() = runTest {
        val harness = Harness()
        harness.repository.apply(anUpsert(aNotification(isRead = true)))

        val marked = harness.repository.markAllRead(adaID, Moments.muchLater)

        assertEquals(0, marked)
        assertEquals(0, harness.queueDepth())
        assertEquals(0, harness.notifier.cancelAllCount)
    }

    // MARK: Local cleanup

    @Test
    fun deletingAUsersInboxRemovesOnlyThatUsersRowsAndQueuesNothing() = runTest {
        val harness = Harness()
        val forAda = aNotification(id = firstNotificationID, userID = adaID)
        val forGrace = aNotification(id = secondNotificationID, userID = graceID)
        harness.repository.apply(anUpsert(forAda))
        harness.repository.apply(anUpsert(forGrace))

        val deleted = harness.repository.deleteForUser(adaID)

        assertEquals(1, deleted)
        assertEquals(emptyList(), harness.repository.notifications(adaID))
        assertEquals(listOf(forGrace), harness.repository.notifications(graceID))
        assertEquals(0, harness.queueDepth())
        assertEquals(1, harness.notifier.cancelAllCount)
    }

    @Test
    fun deletingAnEmptyInboxDoesNotCancelUnrelatedBanners() = runTest {
        val harness = Harness()
        harness.repository.apply(anUpsert(aNotification(userID = graceID)))

        val deleted = harness.repository.deleteForUser(adaID)

        assertEquals(0, deleted)
        assertEquals(0, harness.notifier.cancelAllCount)
        assertTrue(harness.repository.notifications(graceID).isNotEmpty())
    }

    @Test
    fun retentionDeletesOnlyRowsOlderThanTheCutoffAndQueuesNothing() = runTest {
        val harness = Harness()
        val old = aNotification(
            id = firstNotificationID,
            createdAt = Moments.start,
            updatedAt = Moments.start,
        )
        val recent = aNotification(
            id = secondNotificationID,
            createdAt = Moments.later,
            updatedAt = Moments.later,
        )
        harness.repository.apply(anUpsert(old))
        harness.repository.apply(anUpsert(recent))

        val deleted = harness.repository.deleteOlderThan(Moments.later)

        assertEquals(1, deleted)
        assertNull(harness.repository.notification(old.id))
        assertEquals(recent, harness.repository.notification(recent.id))
        // Retention is a local cleanup, not an edit the server has to learn about.
        assertEquals(0, harness.queueDepth())
    }

    // MARK: Payload and write integrity

    @Test
    fun payloadsAreAcceptedBothAsABareDocumentAndAsAQuotedString() = runTest {
        val harness = Harness()
        val bare = aNotification(id = firstNotificationID)
        val quoted = aNotification(id = secondNotificationID)

        harness.repository.apply(anUpsert(bare, quoted = false))
        harness.repository.apply(anUpsert(quoted))

        assertEquals(
            setOf(bare.id, quoted.id),
            harness.repository.notifications(adaID).map { it.id }.toSet(),
        )
    }

    @Test
    fun aLocallyMintedNotificationIsStoredWithoutBeingQueuedForPush() = runTest {
        val harness = Harness()

        val created = harness.repository.createNotification(
            userID = adaID,
            kind = NotificationKind.DueToday,
            payload = """{"workItemID":"wi-1"}""",
            now = Moments.start,
        )

        assertEquals(created, harness.repository.notification(created.id))
        assertEquals(1, harness.repository.unreadCount(adaID))
        assertEquals(0, harness.queueDepth())
        // Creating a notification locally does not schedule an OS banner; only a server
        // delivery does that.
        assertEquals(emptyList(), harness.notifier.scheduled)
    }

    @Test
    fun anUndecodableDeliveredChangeFailsLoudlyInsteadOfLeavingAPartialWrite() = runTest {
        val harness = Harness()

        assertFailsWith<AppError.Decoding> {
            harness.repository.apply(
                RemoteChange(
                    entity = OutboxEntity.Notification,
                    entityID = firstNotificationID.stringValue,
                    operation = OutboxOperation.Upsert,
                    payload = "{not-json".encodeToByteArray(),
                    version = null,
                ),
            )
        }

        assertEquals(emptyList(), harness.repository.notifications(adaID))
        assertEquals(0, harness.queueDepth())
        assertEquals(emptyList(), harness.notifier.scheduled)
    }
}

/**
 * One in-memory store, one repository, and a notifier a test can read afterwards.
 *
 * Each harness gets its own database, so these tests are independent and safe to run in
 * parallel. The repository is the production type over the production store; only the
 * platform notifier is a double, because a real one would put banners on the test host.
 */
private class Harness {
    val database: TaskTrackerDatabase = TaskTrackerDatabase.inMemory()
    val notifier = RecordingLocalNotifier()
    val repository = RoomNotificationRepository(database, notifier)

    suspend fun queued(): List<OutboxRecord> = database.outboxDao().all()

    suspend fun queueDepth(): Int = database.outboxDao().count()
}

/**
 * Records what the repository asked the operating system to show.
 *
 * All three methods are recorded, including `cancelAll`, because the difference between
 * "cancel this row" and "cancel everything" is real: a single read must not clear unrelated
 * banners, while sign-out and mark-all are allowed to.
 */
private class RecordingLocalNotifier : LocalNotifier {

    data class Scheduled(val notification: Notification, val trigger: Trigger)

    val scheduled = mutableListOf<Scheduled>()
    val cancelled = mutableListOf<NotificationID>()
    var cancelAllCount = 0

    override suspend fun schedule(notification: Notification, trigger: Trigger) {
        scheduled += Scheduled(notification, trigger)
    }

    override suspend fun cancel(notificationId: NotificationID) {
        cancelled += notificationId
    }

    override suspend fun cancelAll() {
        cancelAllCount += 1
    }
}

/**
 * The instants these tests use, named so an assertion can say which one it meant.
 *
 * Every write takes its instant from the caller, which is what makes these assertions about
 * the repository rather than about the day the suite happened to run.
 */
private object Moments {
    val start: Instant = Instant.fromEpochSeconds(1_760_000_000)
    val later: Instant = Instant.fromEpochSeconds(1_760_000_600)
    val muchLater: Instant = Instant.fromEpochSeconds(1_760_003_600)
}

private val adaID = UserID("10000000-0000-4000-8000-000000000001")
private val graceID = UserID("10000000-0000-4000-8000-000000000002")
private val firstNotificationID = parseNotificationID("20000000-0000-4000-8000-000000000001")!!
private val secondNotificationID = parseNotificationID("20000000-0000-4000-8000-000000000002")!!
private val thirdNotificationID = parseNotificationID("20000000-0000-4000-8000-000000000003")!!

/**
 * A notification with every field deliberately unlike its default.
 *
 * A fixture that left the payload or the timestamps at a default would round-trip through a
 * mapping that dropped them and still look correct. The payload is non-empty and the two
 * timestamps differ for exactly that reason.
 */
private fun aNotification(
    id: NotificationID = firstNotificationID,
    userID: UserID = adaID,
    kind: NotificationKind = NotificationKind.TaskAssigned,
    payload: String = """{"workItemID":"wi-1","workItemKey":"TT-42","assignedByName":"Ada"}""",
    isRead: Boolean = false,
    createdAt: Instant = Moments.start,
    updatedAt: Instant = Moments.later,
): Notification = Notification(
    id = id,
    userID = userID,
    kind = kind,
    payload = payload,
    isRead = isRead,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * A server upsert in the two shapes the sync log can produce.
 *
 * The client's transport declares a change payload as a JSON string, so a real pull usually
 * hands back one level of quoting around the document. Older rows for other entities are
 * bare documents, and the repository accepts both; the flag lets one test cover each shape.
 */
private fun anUpsert(notification: Notification, quoted: Boolean = true): RemoteChange {
    val document = JSONCoding.json.encodeToString(Notification.serializer(), notification)
    val payload = if (quoted) {
        JSONCoding.json.encodeToString(String.serializer(), document)
    } else {
        document
    }
    return RemoteChange(
        entity = OutboxEntity.Notification,
        entityID = notification.id.stringValue,
        operation = OutboxOperation.Upsert,
        payload = payload.encodeToByteArray(),
        version = null,
    )
}

/** A server dismissal: the bare id is the whole payload. */
private fun aDelete(id: NotificationID): RemoteChange = RemoteChange(
    entity = OutboxEntity.Notification,
    entityID = id.stringValue,
    operation = OutboxOperation.Delete,
    payload = id.stringValue.encodeToByteArray(),
    version = null,
)

/** Decodes a queued upsert, so an assertion can be about the document rather than its bytes. */
private fun decodeQueued(record: OutboxRecord): Notification =
    JSONCoding.json.decodeFromString(Notification.serializer(), record.payload.decodeToString())

/**
 * Collects [flow] into [into] for the lifetime of the test.
 *
 * Started on an unconfined test dispatcher because the repository's broadcaster registers a
 * subscriber only when collection begins. A collector that had not run yet would miss the
 * event and the test would be asserting on a bus nobody was listening to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> TestScope.collectInto(flow: Flow<T>, into: MutableList<T>): Job =
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.collect { into += it }
    }
