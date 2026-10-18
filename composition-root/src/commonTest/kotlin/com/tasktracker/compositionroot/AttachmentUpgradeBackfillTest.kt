package com.tasktracker.compositionroot

import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The once-per-install half of the attachment backfill.
 *
 * The work itself — one queued change per pending row — belongs to
 * `BackfillUnqueuedAttachments` in the domain and converges without help; what is under test
 * here is the *bound*, because an unbounded backfill re-announces every file whose bytes are
 * stuck, forever. A counting lambda stands in for the use case, so the assertions are about
 * how often it ran rather than about what it wrote.
 */
class AttachmentUpgradeBackfillTest {

    @Test
    fun `the first run queues and records how many`() = runTest {
        var calls = 0
        val store = defaults()
        val subject = AttachmentUpgradeBackfill(
            backfill = { calls += 1; 3 },
            defaults = store,
        )

        val queued = subject.runIfNeeded()

        assertEquals(3, queued)
        assertEquals(1, calls)
        assertTrue(store.value(key = AttachmentUpgradeBackfill.KEY) != null)
        assertEquals("3", store.value(key = AttachmentUpgradeBackfill.KEY))
    }

    @Test
    fun `a second run does nothing because the flag survives`() = runTest {
        var calls = 0
        val subject = AttachmentUpgradeBackfill(
            backfill = { calls += 1; 3 },
            defaults = defaults(),
        )

        val first = subject.runIfNeeded()
        val second = subject.runIfNeeded()

        assertEquals(3, first)
        assertEquals(0, second, "the flag must hold the work to one run per install")
        assertEquals(1, calls)
    }

    /**
     * The reason the key is raw rather than a member of `DefaultsKey`: sign-out clears every
     * enum key, and an enum member would let the backfill replay after the first sign-out as
     * though it had never run.
     */
    @Test
    fun `a sign-out does not clear the flag`() = runTest {
        var calls = 0
        val store = defaults()
        val subject = AttachmentUpgradeBackfill(
            backfill = { calls += 1; 1 },
            defaults = store,
        )
        subject.runIfNeeded()

        store.removeAll()

        assertTrue(
            store.value(key = AttachmentUpgradeBackfill.KEY) != null,
            "the backfill is a property of the store, not of the session",
        )
        assertEquals(0, subject.runIfNeeded())
        assertEquals(1, calls)
    }

    /**
     * A store that fails halfway keeps the flag clear, so the next successful cycle picks the
     * queue back up — the exception itself is the post-sync hook's to contain.
     */
    @Test
    fun `a failed backfill leaves the flag unset`() = runTest {
        var attempts = 0
        val store = defaults()
        val subject = AttachmentUpgradeBackfill(
            backfill = { attempts += 1; if (attempts == 1) error("the store fell over") else 2 },
            defaults = store,
        )

        val thrown = runCatching { subject.runIfNeeded() }

        assertTrue(thrown.isFailure, "the failure is the hook's to contain, not this class's to hide")
        assertFalse(store.value(key = AttachmentUpgradeBackfill.KEY) != null, "a half-done upgrade must be retried")
        assertEquals(2, subject.runIfNeeded())
        assertEquals(2, attempts)
    }

    /**
     * An empty store is the common case: the backfill runs once, queues nothing, and the flag
     * records that rather than leaving the question open for every future launch.
     */
    @Test
    fun `an empty store still records that the backfill ran`() = runTest {
        var calls = 0
        val store = defaults()
        val subject = AttachmentUpgradeBackfill(
            backfill = { calls += 1; 0 },
            defaults = store,
        )

        assertEquals(0, subject.runIfNeeded())

        assertTrue(store.value(key = AttachmentUpgradeBackfill.KEY) != null)
        assertEquals("0", store.value(key = AttachmentUpgradeBackfill.KEY))
        assertEquals(1, calls)
    }

    private fun defaults() = UserDefaultsStore(backend = InMemoryPreferences())
}
