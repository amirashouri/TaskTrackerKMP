package com.tasktracker.featuresettingsdomain

/**
 * Sends the changes the server refused permanently, again, after repairing what made them
 * un-sendable.
 *
 * ## Two steps, and the order is the rule
 *
 * Re-queueing is a local write: a refused change is never claimed again, so it has to be put
 * back before a cycle can see it at all. The cycle is what actually tries the server. Doing
 * them the other way round would push an empty queue and report success — the exact shape of
 * bug this whole control exists to answer, where the user is told everything is fine and
 * nothing moved.
 *
 * ## The cycle goes through [SyncNow], not around it
 *
 * So that the offline rule is asked in the one place that owns it. A retry that started a
 * cycle directly would be a second copy of "is a cycle worth starting", and the copy that
 * drifts is always the one nobody is looking at.
 *
 * Offline is therefore not a refusal to retry: the changes go back regardless, because that
 * part needs no network, and the cycle is what declines and says so. That is the honest
 * split — the queue really did change, and the push really did not happen.
 */
class RetryStuckChanges(private val port: SettingsPort) {

    /**
     * @return what became of the cycle that followed, so the screen can say the same thing it
     *   says for [SyncNow] rather than inventing a second vocabulary for the same answer.
     */
    suspend operator fun invoke(): SyncOutcome {
        port.retryStuckChanges()
        return SyncNow(port)()
    }
}
