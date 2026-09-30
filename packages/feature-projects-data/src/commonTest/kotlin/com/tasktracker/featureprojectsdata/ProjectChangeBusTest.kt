package com.tasktracker.featureprojectsdata

import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The invalidation channel, tested as a channel.
 *
 * The properties that matter are all about *who hears what*, and every one of them is
 * invisible from a single collector — a bus that only ever served one subscriber would pass
 * every test that watched one stream. So each of these either watches two at once, or watches
 * none, which is the case that is easiest to get wrong and easiest not to notice: a
 * `MutableSharedFlow` with a replay buffer passes every "does the event arrive" test and is
 * still wrong here, because a replayed event tells a screen about a change it has already read.
 */
class ProjectChangeBusTest {

    @Test
    fun aSubscriberSeesWhatIsSentWhileItIsListening() = runTest {
        val bus = ProjectChangeBus()
        val seen = mutableListOf<ProjectChange>()
        collecting(bus.changes(), seen)

        bus.send(ProjectChange(kind = ProjectChange.Kind.Projects))

        assertEquals(listOf(ProjectChange(ProjectChange.Kind.Projects)), seen)
    }

    @Test
    fun everySubscriberSeesEveryEventRatherThanThemDividingThemUp() = runTest {
        // The dashboard, the project screen and the board can all be open at once. A single
        // shared cursor would hand each event to whichever collector got there first, and the
        // other two would never hear about the write — so the board would sit on stale data
        // until the user navigated away and back.
        val bus = ProjectChangeBus()
        val first = mutableListOf<ProjectChange>()
        val second = mutableListOf<ProjectChange>()
        collecting(bus.changes(), first)
        collecting(bus.changes(), second)

        bus.send(ProjectChange(kind = ProjectChange.Kind.WorkItems))
        bus.send(ProjectChange(kind = ProjectChange.Kind.Projects))

        assertEquals(2, first.size)
        assertEquals(2, second.size)
        assertEquals(first, second)
    }

    @Test
    fun twoStreamsFromTheSameBusAreIndependent() = runTest {
        // Each call to `changes()` registers its own subscription. A shared stream would make
        // the second caller observe only what was sent after it arrived.
        val bus = ProjectChangeBus()
        val first = mutableListOf<ProjectChange>()
        val late = mutableListOf<ProjectChange>()
        collecting(bus.changes(), first)

        bus.send(ProjectChange(kind = ProjectChange.Kind.Projects))
        collecting(bus.changes(), late)
        bus.send(ProjectChange(kind = ProjectChange.Kind.WorkItems))

        assertEquals(2, first.size)
        assertEquals(listOf(ProjectChange(ProjectChange.Kind.WorkItems)), late)
    }

    @Test
    fun anEventSentBeforeAnybodyListensIsDroppedRatherThanReplayed() = runTest {
        // The rule that makes "collect, then read" correct: a screen reads the current state
        // first, so a replay would only ever tell it about something it has already accounted
        // for — and a replay buffer would grow for the whole session behind a board that
        // never came back.
        val bus = ProjectChangeBus()

        bus.send(ProjectChange(kind = ProjectChange.Kind.Projects))

        val seen = mutableListOf<ProjectChange>()
        collecting(bus.changes(), seen)

        assertEquals(0, seen.size)
    }

    @Test
    fun aStreamIsNotSubscribedUntilItIsCollected() = runTest {
        // The counter is what a test uses to know its collector is live, so it must not count
        // a stream nobody has taken yet.
        val bus = ProjectChangeBus()

        assertEquals(0, bus.subscriberCount)

        collecting(bus.changes(), mutableListOf())

        assertEquals(1, bus.subscriberCount)
    }

    @Test
    fun aCancelledSubscriberUnregistersItself() = runTest {
        // A screen that goes away must not leave a queue behind, or every later event is
        // written into a buffer nobody will drain.
        val bus = ProjectChangeBus()
        val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.changes().collect { }
        }

        assertEquals(1, bus.subscriberCount)

        job.cancel()
        job.join()

        assertEquals(0, bus.subscriberCount)
    }

    @Test
    fun finishingClosesEveryOpenStream() = runTest {
        // Exists for teardown and tests. A collector that merely stopped has already
        // unregistered itself, so the only queues left open are ones whose collector was never
        // cancelled — and finishing is the deliberate way to say "nothing more is coming" to
        // everyone at once.
        val bus = ProjectChangeBus()
        val seen = mutableListOf<ProjectChange>()
        collecting(bus.changes(), seen)

        bus.finish()

        // Completing the flow is what a collector observes; the event list is untouched,
        // because completion is not an event and must not look like one.
        assertTrue(seen.isEmpty())
    }

    @Test
    fun announcingAProjectChangeSaysBothThingsAtOnce() = runTest {
        val bus = ProjectChangeBus()
        val seen = mutableListOf<ProjectChange>()
        collecting(bus.changes(), seen)
        val projectID = aProject().id

        bus.announceProjectChange(projectID)

        assertEquals(
            listOf(
                ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID),
                ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID),
            ),
            seen,
        )
    }
}
