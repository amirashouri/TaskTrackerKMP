package com.tasktracker.featureauthdata

import com.tasktracker.featureauthdomain.SessionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The channel the auth feature publishes on, tested as a channel.
 *
 * The properties that matter are all about *who hears what*, and every one of them is
 * invisible from a single collector — a bus that only ever served one subscriber would pass
 * every test that watched one stream.
 */
class SessionStateBusTest {

    @Test
    fun aSubscriberSeesWhatIsPublishedAfterItSubscribed() = runTest {
        val bus = SessionStateBus()
        val seen = mutableListOf<SessionState>()
        collecting(bus.states(), seen)

        bus.send(SessionState.Restoring)
        bus.send(SessionState.SignedOut)

        assertEquals(listOf<SessionState>(SessionState.Restoring, SessionState.SignedOut), seen)
        bus.finish()
    }

    @Test
    fun publishingWithNobodyListeningIsNotAnError() = runTest {
        val bus = SessionStateBus()

        // A screen that opens later calls `restore()` first and cannot have missed anything,
        // so a send into the void is discarded rather than buffered forever — a replayed
        // event would tell a screen about a state its read has already accounted for.
        bus.send(SessionState.SignedOut)

        assertEquals(0, bus.subscriberCount)
        bus.finish()
    }

    @Test
    fun everySubscriberSeesEveryEventRatherThanThemDividingThemUp() = runTest {
        // The lock screen, the board and a background sync can all be collecting at once. A
        // single shared cursor would hand each event to whichever collector got there first,
        // and the others would never hear about the sign-out.
        val bus = SessionStateBus()
        val first = mutableListOf<SessionState>()
        val second = mutableListOf<SessionState>()
        collecting(bus.states(), first)
        collecting(bus.states(), second)

        bus.send(SessionState.SignedOut)
        bus.send(SessionState.Restoring)

        assertEquals(2, first.size)
        assertEquals(2, second.size)
        assertEquals(first, second)
        bus.finish()
    }

    @Test
    fun anEventPublishedBeforeAnyoneSubscribesIsNotReplayed() = runTest {
        val bus = SessionStateBus()
        // Taken, but not collected: `Broadcaster` registers on collection rather than on
        // stream creation, so this is the shape of a screen that has the flow and has not
        // started collecting yet.
        val late = bus.states()
        bus.send(SessionState.SignedOut)

        val seen = mutableListOf<SessionState>()
        collecting(late, seen)

        // No replay. A buffer would tell a screen about a sign-out its `restore()` has already
        // accounted for, and would break the "subscribe, then read" order the launch path
        // depends on.
        assertTrue(seen.isEmpty())
        bus.finish()
    }

    @Test
    fun finishingReleasesEveryOpenStream() = runTest {
        val bus = SessionStateBus()
        val first = mutableListOf<SessionState>()
        val second = mutableListOf<SessionState>()
        collecting(bus.states(), first)
        collecting(bus.states(), second)
        assertEquals(2, bus.subscriberCount)
        bus.send(SessionState.SignedOut)

        // Both are closed while actually parked on their channels, which is the case that
        // iterates the subscriber map while each collector unregisters itself from it.
        bus.finish()

        assertEquals(0, bus.subscriberCount)
    }

    @Test
    fun aStreamTakenAfterFinishingIsAlreadyComplete() = runTest {
        val bus = SessionStateBus()
        bus.finish()

        val seen = mutableListOf<SessionState>()
        // Registering a queue that nothing will ever close would be the leak `finish` exists
        // to prevent, so a subscriber that arrives late gets a stream that ends immediately.
        collecting(bus.states(), seen)

        assertTrue(seen.isEmpty())
        assertEquals(0, bus.subscriberCount)
    }
}
