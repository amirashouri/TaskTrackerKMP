package com.tasktracker.core

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class BroadcasterTest {

    @Test
    fun fansOneEventOutToEverySubscriber() = runTest {
        val broadcaster = Broadcaster<Int>()
        val a = mutableListOf<Int>()
        val b = mutableListOf<Int>()

        // Unconfined so the collectors are already subscribed by the time the
        // assertion below runs. With the default dispatcher, `subscriberCount`
        // would be 0 and the test would be asserting on launch order rather than
        // on fan-out.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcaster.stream().collect { a += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcaster.stream().collect { b += it }
        }

        assertEquals(2, broadcaster.subscriberCount)
        broadcaster.send(7)
        advanceUntilIdle()

        assertEquals(listOf(7), a)
        assertEquals(listOf(7), b)
    }

    @Test
    fun sendsEveryEventInOrderToOneSubscriber() = runTest {
        val broadcaster = Broadcaster<Int>()
        val seen = mutableListOf<Int>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcaster.stream().collect { seen += it }
        }

        broadcaster.send(1)
        broadcaster.send(2)
        broadcaster.send(3)
        advanceUntilIdle()

        // Ordering is load-bearing for a status stream: an "idle" that arrives
        // before the "syncing" that caused it leaves the UI stuck.
        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test
    fun finishEndsEveryStreamAndDeregistersSubscribers() = runTest {
        val broadcaster = Broadcaster<Int>()
        var completed = false

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcaster.stream().collect { }
            completed = true
        }
        assertEquals(1, broadcaster.subscriberCount)

        broadcaster.finish()
        advanceUntilIdle()

        // Without completion a view subscribing after a sign-out would wait forever
        // on a stream that can only ever be silent.
        assertTrue(completed)
        assertEquals(0, broadcaster.subscriberCount)
    }

    @Test
    fun aCancelledCollectorIsDeregistered() = runTest {
        val broadcaster = Broadcaster<Int>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcaster.stream().collect { }
        }
        assertEquals(1, broadcaster.subscriberCount)

        job.cancel()
        advanceUntilIdle()

        // The leak this guards: a screen dismissed mid-sync leaving a queue that
        // nothing will ever drain.
        assertEquals(0, broadcaster.subscriberCount)
    }

    @Test
    fun aSubscriberThatStartsAfterFinishCompletesImmediately() = runTest {
        val broadcaster = Broadcaster<Int>()
        broadcaster.finish()

        var sawEvent = false
        var completed = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            broadcaster.stream().collect { sawEvent = true }
            completed = true
        }
        advanceUntilIdle()

        assertTrue(completed)
        assertFalse(sawEvent)
    }
}

@OptIn(ExperimentalUuidApi::class)
class IdentifiersTest {

    @kotlinx.serialization.Serializable
    private data class Envelope(val project: ProjectID, val item: WorkItemID)

    @Test
    fun encodeAsABareJsonStringAndRoundTrip() {
        val id = randomProjectID()
        val encoded = JSONCoding.json.encodeToString(ProjectID.serializer(), id)

        // A bare string, not {"rawValue":…}. The server sees the same identifier
        // from iOS and Android; changing this is a protocol change.
        assertEquals("\"${id.stringValue}\"", encoded)
        assertEquals(id, JSONCoding.json.decodeFromString(ProjectID.serializer(), encoded))

        // The two types share a representation on the wire…
        val asWorkItem = JSONCoding.json.decodeFromString(WorkItemID.serializer(), encoded)
        assertEquals(id.stringValue, asWorkItem.stringValue)
    }

    @Test
    fun identifierTypesShareARepresentationButStayDistinct() {
        val uuid = Uuid.random()

        // Same UUID, five types, identical text. What makes them distinct is that
        // they do not unify in code — `fun f(p: ProjectID)` cannot be called with a
        // `WorkItemID`, and that is the guarantee the compiler is giving us. Asserting
        // it at runtime is impossible by construction; what is asserted here is the
        // representation they agree on.
        assertEquals(uuid.toString(), ProjectID(uuid).stringValue)
        assertEquals(uuid.toString(), WorkItemID(uuid).stringValue)
        assertEquals(uuid.toString(), UserID(uuid).stringValue)
        assertEquals(uuid.toString(), SprintID(uuid).stringValue)
        assertEquals(uuid.toString(), StatusChangeID(uuid).stringValue)
        assertNotNull(parseUserID(uuid.toString()))
    }

    @Test
    fun envelopeRoundTripsWithBothIdentifiers() {
        val envelope = Envelope(randomProjectID(), randomWorkItemID())
        val text = JSONCoding.json.encodeToString(Envelope.serializer(), envelope)
        assertEquals(envelope, JSONCoding.json.decodeFromString(Envelope.serializer(), text))
    }

    @Test
    fun rejectsMalformedStringInput() {
        val uuid = Uuid.random()
        assertEquals(ProjectID(uuid), assertNotNull(parseProjectID(uuid.toString())))
        assertNull(parseProjectID("nope"))
        assertNull(parseProjectID(""))
    }

    @Test
    fun aSprintIdParsesAndRejectsLikeEveryOtherOne() {
        // Same contract as [parseProjectID], and asserted separately because a sprint id
        // reaches the parser from a deep link (`tasktracker://sprint/<pid>/<sid>`) rather
        // than from a server payload, which makes "silently null" the failure rather than
        // a rejected request.
        val uuid = Uuid.random()
        assertEquals(SprintID(uuid), assertNotNull(parseSprintID(uuid.toString())))
        assertNull(parseSprintID("nope"))
        assertNull(parseSprintID(""))
    }

    @Test
    fun generatedIdentifiersAreDistinct() {
        // Not a collision test — a UUID test. It exists so that accidentally making
        // the generator deterministic would fail here rather than produce two boards
        // sharing an id in production.
        assertFalse(randomProjectID() == randomProjectID())
        assertFalse(randomWorkItemID() == randomWorkItemID())
        assertFalse(randomUserID() == randomUserID())
        assertFalse(randomSprintID() == randomSprintID())
        assertFalse(randomStatusChangeID() == randomStatusChangeID())
    }

    @Test
    fun workItemKeyIsJustItsText() {
        assertEquals("TT-142", WorkItemKey("TT-142").rawValue)
        assertEquals("TT-142", WorkItemKey("TT-142").toString())
    }
}
