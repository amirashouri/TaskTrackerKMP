package com.tasktracker.featureauthdata

import com.tasktracker.core.Broadcaster
import com.tasktracker.featureauthdomain.SessionState
import kotlinx.coroutines.flow.Flow

/**
 * The one invalidation channel the auth feature publishes on.
 *
 * Deliberately its own small type rather than a bare [Broadcaster] passed around, for the
 * same reason `ProjectChangeBus` is: it gives the repository and the token vault one
 * obvious way to reach the same channel, and it is the one place that knows the fan-out
 * mechanics so neither of the two things that publish has to.
 *
 * ## Why not a `MutableSharedFlow`
 *
 * The two publish rules are both load-bearing:
 *
 * 1. **A fresh stream per subscriber.** The lock screen, the board and a background sync can
 *    all be collecting at once; a single shared cursor would hand each event to whichever
 *    collector got there first and the others would never hear about the sign-out.
 * 2. **A send with nobody listening is dropped, not buffered.** Every subscriber re-reads
 *    `AuthSessionPort.current` on start, so a latecomer cannot have missed anything.
 *    Buffering would replay a sign-in to a screen that had already read the signed-out
 *    state past it.
 *
 * Events are not replayed to a late subscriber, and cannot be: [Broadcaster] registers on
 * collection rather than on stream creation, so [subscriberCount] counts live collectors and
 * a test has to have one running to observe anything.
 *
 * ## Why this exists at all, when `AuthSessionPort.current` is readable
 *
 * Because the interesting half of a session change is invisible to a screen that only
 * polls. A token the server has stopped honouring is discovered by a *request* failing on a
 * background thread, and the only way the UI learns of it is being told. [current] answers
 * "what is true now"; this answers "something just changed, look again" — and the second
 * question is the one an interceptor firing mid-sync cannot ask for itself.
 */
class SessionStateBus {

    private val broadcaster = Broadcaster<SessionState>()

    /** A fresh stream per subscriber. See the class documentation. */
    fun states(): Flow<SessionState> = broadcaster.stream()

    /**
     * Publishes a state. Dropped when nobody is listening, which is correct rather than a
     * limitation — see the class documentation.
     */
    fun send(state: SessionState) {
        broadcaster.send(state)
    }

    /**
     * Live collector count. A stream is only a stream once something is collecting it, so
     * this is zero for a screen that has taken a [states] reference and not yet started.
     */
    val subscriberCount: Int get() = broadcaster.subscriberCount

    /**
     * Closes every open stream.
     *
     * Exists for tests and for teardown. A collector that has simply stopped — its screen
     * went away, or the test finished — unregisters itself when its coroutine is cancelled,
     * so the only queues left open are the ones whose collector was never cancelled.
     * Finishing is the deliberate way to say "nothing more is coming" to everyone at once.
     *
     * Not reversible, and not meant to be. One bus lives for one session; restarting it
     * would resurrect queues that a cancelled collector had already abandoned.
     */
    fun finish() {
        broadcaster.finish()
    }
}
