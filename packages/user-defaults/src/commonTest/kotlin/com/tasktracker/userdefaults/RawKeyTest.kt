package com.tasktracker.userdefaults

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The raw-string key path — the one that stores the appearance scheme.
 *
 * ## Why this needs its own file
 *
 * [UserDefaultsStoreTest] covers the [DefaultsKey] path, where keys are *relative* and get
 * prefixed with the namespace. Every assertion in there depends on that. The raw path is
 * the opposite in the one way that matters: its keys are **absolute**, because the only
 * caller is `ThemeScheme.defaultsKey` — a full plist key the original reads through
 * `@AppStorage`, which does not apply a namespace.
 *
 * So the property worth stating is asymmetric with the rest of the store, and a test file
 * that mixed the two would make it look like a special case rather than the thing it is.
 */
class RawKeyTest {

    /**
     * A raw key is stored under exactly the key it was given.
     *
     * The one that matters. The first draft of this path ran the key through
     * `namespaced()` like everything else, which produced
     * `com.tasktracker.com.tasktracker.settings.themeScheme` for the appearance key. That
     * is a failure no test in the module would have caught on its own, because the read and
     * the write used the same doubled key and so the round trip was self-consistent: the
     * setting worked for anyone who set it in the new build, and came back as `system` for
     * anyone whose value had been written by the original.
     *
     * Asserting against the backend's actual key list, rather than round-tripping through
     * the store, is what makes it catch that. A round trip cannot distinguish a right key
     * from a consistently wrong one.
     */
    @Test
    fun `a raw key is stored under exactly the key it was given`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend, namespace = "com.tasktracker")

        store.set("dark", key = "com.tasktracker.settings.themeScheme")

        assertEquals(
            listOf("com.tasktracker.settings.themeScheme"),
            backend.keys(),
            "the raw key was rewritten; a doubled prefix is a key nothing else reads",
        )
    }

    /**
     * The contrast with a [DefaultsKey], in one test.
     *
     * Both kinds of key going through the same store is the thing being pinned: the enum one
     * is relative and *is* prefixed, the raw one is absolute and is *not*. Written as two
     * assertions in one test on purpose — the property is the difference between them, and
     * splitting it in two lets a future change make both behave the same way while both
     * tests still pass.
     */
    @Test
    fun `an enum key is namespaced and a raw key is not`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend, namespace = "com.tasktracker")

        store.set("p1", key = DefaultsKey.LastSeenProject)
        store.set("dark", key = "com.tasktracker.settings.themeScheme")

        assertEquals(
            listOf(
                "com.tasktracker.navigation.lastSeenProject",
                "com.tasktracker.settings.themeScheme",
            ),
            backend.keys(),
        )
    }

    /**
     * The reason the raw path exists: [removeAll] must not touch it.
     *
     * [removeAll] runs on sign-out, and the whole argument for keeping the appearance
     * outside the enum is that someone's light/dark choice is a property of the device
     * rather than of the session. If this test fails, the bug is the one users would call
     * "it forgets my theme every time I sign out" — silent, repeatable, and blamed on the
     * user.
     */
    @Test
    fun `removeAll leaves a raw key alone`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)

        store.set("p1", key = DefaultsKey.LastSeenProject)
        store.set("dark", key = "com.tasktracker.settings.themeScheme")

        store.removeAll()

        assertFalse(store.hasValue(DefaultsKey.LastSeenProject), "the session key should be gone")
        assertEquals("dark", store.value("com.tasktracker.settings.themeScheme"))
    }

    /**
     * The customer's server address survives sign-out.
     *
     * The same property as the test above, named for the key that depends on it. A server
     * address belongs to the device; if it moved into [DefaultsKey], this test goes red
     * while a generic "raw keys persist" test stays green.
     */
    @Test
    fun `the saved server address survives sign-out`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)

        store.set("https://tasks.example.com", key = API_BASE_URL_PREFERENCE_KEY)
        store.set("p1", key = DefaultsKey.LastSeenProject)

        store.removeAll()

        assertEquals(
            "https://tasks.example.com",
            store.value(API_BASE_URL_PREFERENCE_KEY),
            "signing out must not make the customer re-enter their server address",
        )
        assertNull(store.value(DefaultsKey.LastSeenProject))
    }

    /**
     * The value is the plain string, not a JSON encoding.
     *
     * Asserted on the stored bytes rather than on the round trip, for the same reason as the
     * key: a round trip through `encode`/`decode` cannot tell `"dark"` from `dark` if both
     * ends agree, and the difference is what lets anything other than this store — a plist
     * editor, a migration, the original app — read the value.
     */
    @Test
    fun `a raw value is stored as a plain string`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)

        store.set("dark", key = "com.tasktracker.settings.themeScheme")

        assertEquals(
            "dark",
            backend.bytes("com.tasktracker.settings.themeScheme")?.decodeToString(),
            "the value should not be JSON-encoded",
        )
    }

    /**
     * Reading a key that was never written is `null`, not an empty string and not a throw.
     *
     * `null` rather than `""` is the contract [ThemeScheme.fromStoredValue] relies on: it
     * has its own fallback for anything unrecognised, and a store that invented a second
     * one would give the appearance two places to decide what "unset" means.
     */
    @Test
    fun `an absent raw key reads as null`() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        assertNull(store.value("com.tasktracker.settings.themeScheme"))
    }

    /**
     * A value written by something else is readable.
     *
     * The interoperability case that the verbatim-key rule exists for, written as a test
     * because it is the only way to say it without referring to another build: the backend
     * is seeded directly, exactly as a previous version of the app — or the original, which
     * writes the same key through `UserDefaults` — would have left it.
     */
    @Test
    fun `a value written directly to the backend is readable`() = runTest {
        val backend = InMemoryPreferences(
            seed = mapOf("com.tasktracker.settings.themeScheme" to "light".encodeToByteArray()),
        )
        val store = UserDefaultsStore(backend, namespace = "com.tasktracker")

        assertEquals("light", store.value("com.tasktracker.settings.themeScheme"))
    }

    /** Removing a raw key takes only that key. */
    @Test
    fun `removing a raw key leaves the others`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)

        store.set("dark", key = "com.tasktracker.settings.themeScheme")
        store.set("p1", key = DefaultsKey.LastSeenProject)

        store.remove("com.tasktracker.settings.themeScheme")

        assertNull(store.value("com.tasktracker.settings.themeScheme"))
        assertTrue(store.hasValue(DefaultsKey.LastSeenProject))
    }
}
