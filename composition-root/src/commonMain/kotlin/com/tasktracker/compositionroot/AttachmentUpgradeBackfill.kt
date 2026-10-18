package com.tasktracker.compositionroot

import com.tasktracker.userdefaults.UserDefaultsStore

/**
 * The once-per-install half of the attachment backfill.
 *
 * ## Why this is here and not in the use case
 *
 * [BackfillUnqueuedAttachments] converges on its own — a row leaves the pending set as soon
 * as its upload lands — so the *work* needs no durable state. Running it on every sync cycle
 * would still be wrong, and for a reason that has nothing to do with the set: an attachment
 * whose metadata is already on the server but whose bytes keep failing to upload would be
 * re-announced every cycle, forever, to teammates who can already see the file. The work is
 * therefore idempotent by construction and *bounded* by this guard, which is the one place
 * that can hold durable state — the domain module has no preferences, by design.
 *
 * [backfill] is a function rather than the use case itself, and that is the only reason this
 * class is testable without a store: the graph hands over the real
 * [com.tasktracker.featureprojectsdomain.BackfillUnqueuedAttachments], and a test hands over
 * a counter. It must queue the changes and answer how many it queued.
 *
 * ## Why a raw key rather than a [com.tasktracker.userdefaults.DefaultsKey]
 *
 * Sign-out calls `UserDefaultsStore.removeAll`, which walks the enum. A member of it would be
 * wiped by the first sign-out, and the next launch would replay the backfill as though it had
 * never happened. The backfill is a property of the *store*, not of the session, so the key
 * lives outside the enum exactly as the appearance scheme does — see
 * [UserDefaultsStore.value] for the precedent.
 *
 * The value recorded is how many changes were queued, so a later "why did this device
 * re-upload everything" has an answer that names a number rather than a boolean.
 *
 * ## Failure leaves the flag unset
 *
 * [runIfNeeded] writes only after the enqueue has completed. A store that fails halfway
 * keeps the flag clear, so the next successful cycle picks the queue back up; the exception
 * itself travels to the post-sync hook, which contains and logs it.
 */
internal class AttachmentUpgradeBackfill(
    private val backfill: suspend () -> Int,
    private val defaults: UserDefaultsStore,
) {

    /**
     * Queues the missing changes, the first time only.
     *
     * @return how many changes were queued, or `0` when this install has already run it.
     */
    suspend fun runIfNeeded(): Int {
        // `value` rather than `hasValue`, because [KEY] is a raw string and the raw-key half of
        // the store has no `hasValue` — the `DefaultsKey` overload would refuse it, which is
        // the type telling us this key is deliberately not one of the enum's. A value that
        // cannot be *read* answers the same as an absent one, so a corrupt entry replays the
        // backfill rather than skipping it: the wrong answer there is "already done".
        if (defaults.value(key = KEY) != null) return 0
        val queued = backfill()
        defaults.set(queued.toString(), KEY)
        return queued
    }

    companion object {
        /**
         * Absolute, like the appearance scheme's key, and for the same reason: it must not
         * be namespaced by [UserDefaultsStore.namespaced]'s caller or wiped by `removeAll`.
         */
        const val KEY: String = "com.tasktracker.attachments.upgradeBackfill"
    }
}
