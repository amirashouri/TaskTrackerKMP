package com.tasktracker.featuresettingsdomain

/**
 * Starts a sync cycle on the user's behalf.
 *
 * The decision it owns is the offline one, and it is here rather than in the view for the same
 * reason the sign-in form rejects a bad address instead of greying out its button: a control
 * that goes dead for a reason the user cannot see leaves them tapping it and concluding the app
 * is broken. The screen stays live, and this answers [SyncOutcome.Offline] so the Sync section
 * can say what is actually true — the edits are queued and safe.
 *
 * Asking first and dispatching second is a race in principle, and harmless in practice: the
 * only window is a path going down between the two calls, and the coordinator refuses that case
 * for its own safety, because a timer has no way to tell anybody why it did nothing.
 *
 * Invoked through [invoke] rather than a `callAsFunction` property, which is the Kotlin
 * spelling of the original's `callAsFunction()` and reads the same at the call site.
 */
class SyncNow(private val port: SettingsPort) {

    suspend operator fun invoke(): SyncOutcome {
        if (!port.canSyncNow()) return SyncOutcome.Offline
        return port.syncNow()
    }
}