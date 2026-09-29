package com.tasktracker.userdefaults

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Simulation(val forceOffline: Boolean, val latencyMilliseconds: Int)

class UserDefaultsStoreTest {

    @Test
    fun `round-trips a Codable value`() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())

        assertNull(store.value<Simulation>(DefaultsKey.NetworkSimulation))
        assertFalse(store.hasValue(DefaultsKey.NetworkSimulation))

        val value = Simulation(forceOffline = true, latencyMilliseconds = 500)
        assertTrue(store.set(value, key = DefaultsKey.NetworkSimulation))

        assertEquals(value, store.value<Simulation>(DefaultsKey.NetworkSimulation))
        assertTrue(store.hasValue(DefaultsKey.NetworkSimulation))
    }

    @Test
    fun `keys are namespaced so two instances never collide`() = runTest {
        val backend = InMemoryPreferences()
        val a = UserDefaultsStore(backend, namespace = "com.tasktracker.a")
        val b = UserDefaultsStore(backend, namespace = "com.tasktracker.b")

        a.set("A", key = DefaultsKey.LastSeenProject)

        assertEquals("A", a.value<String>(DefaultsKey.LastSeenProject))
        assertNull(b.value<String>(DefaultsKey.LastSeenProject))
    }

    @Test
    fun `removeAll clears every declared key`() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())

        store.set("p1", key = DefaultsKey.LastSeenProject)
        store.set(true, key = DefaultsKey.HasSeenSyncHint)
        store.removeAll()

        assertFalse(store.hasValue(DefaultsKey.LastSeenProject))
        assertFalse(store.hasValue(DefaultsKey.HasSeenSyncHint))
    }

    @Test
    fun `a corrupt stored value degrades to nil instead of throwing`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)
        store.set(Simulation(forceOffline = false, latencyMilliseconds = 0), DefaultsKey.NetworkSimulation)
        // Poison the same entry with something that will not decode.
        backend.put("not json".encodeToByteArray(), store.namespaced(DefaultsKey.NetworkSimulation))

        assertNull(store.value<Simulation>(DefaultsKey.NetworkSimulation))
    }

    @Test
    fun `a corrupt value still counts as present`() = runTest {
        // hasValue asks the backend, not the decoder. Settings needs to tell "this user
        // chose the default" from "this user has never touched it", and a value that no
        // longer decodes is still something that was written.
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)
        backend.put("not json".encodeToByteArray(), store.namespaced(DefaultsKey.NetworkSimulation))

        assertTrue(store.hasValue(DefaultsKey.NetworkSimulation))
    }

    @Test
    fun `removing one key leaves the others`() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set("p1", key = DefaultsKey.LastSeenProject)
        store.set(true, key = DefaultsKey.HasSeenSyncHint)

        store.remove(DefaultsKey.LastSeenProject)

        assertFalse(store.hasValue(DefaultsKey.LastSeenProject))
        assertTrue(store.hasValue(DefaultsKey.HasSeenSyncHint))
    }

    @Test
    fun `a boolean preference round-trips`() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())

        assertTrue(store.set(true, key = DefaultsKey.BoardShowsBacklog))
        assertEquals(true, store.value<Boolean>(DefaultsKey.BoardShowsBacklog))

        // `false` is a real value, not an absence: it must survive the round trip
        // instead of reading back as null and falling through to the default.
        assertTrue(store.set(false, key = DefaultsKey.BoardShowsBacklog))
        assertEquals(false, store.value<Boolean>(DefaultsKey.BoardShowsBacklog))
    }

    @Test
    fun `the store writes only namespaced keys`() = runTest {
        val backend = InMemoryPreferences()
        val store = UserDefaultsStore(backend)

        store.set("p1", key = DefaultsKey.LastSeenProject)
        store.set(true, key = DefaultsKey.HasSeenSyncHint)

        assertEquals(
            listOf("com.tasktracker.navigation.lastSeenProject", "com.tasktracker.sync.hasSeenHint"),
            backend.keys(),
        )
    }

    @Test
    fun `a seeded backend restores an existing value`() = runTest {
        // What a relaunch looks like: the platform backend already holds the bytes and
        // the store is rebuilt on top of it.
        val backend = InMemoryPreferences()
        UserDefaultsStore(backend).set("p1", DefaultsKey.LastSeenProject)

        val relaunched = UserDefaultsStore(backend)

        assertEquals("p1", relaunched.value<String>(DefaultsKey.LastSeenProject))
    }

    @Test
    fun `every target can produce a preferences backend or says why it cannot`() = runTest {
        // Same reasoning as the keychain factory test: the three platform backends behave
        // differently, so the cross-platform invariant is only that the wiring exists and
        // that a missing Context is reported usefully.
        val result = runCatching { platformPreferences() }
        result.exceptionOrNull()?.let { failure ->
            assertTrue(
                failure.message.orEmpty().contains("Context"),
                "an unhelpful failure from platformPreferences(): ${failure.message}",
            )
        }
    }

    @Test
    fun `the declared keys are all namespaced and unique`() {
        val namespaced = DefaultsKey.entries.map { "com.tasktracker.${it.wireValue}" }
        assertEquals(namespaced.size, namespaced.toSet().size, "two keys share a plist name")
        assertTrue(namespaced.none { it.startsWith("com.tasktracker.com.tasktracker") })
    }

    @Test
    fun `cachedProfile is not one of the credential keys`() {
        // Guard against someone "tidying up" by moving a profile cache into the keychain
        // enum, or a token into the defaults enum, where it would be written to an
        // unencrypted plist.
        val keychainWorthy = listOf("auth.accessToken", "auth.refreshToken")
        assertFalse(DefaultsKey.entries.map { it.wireValue }.any { it in keychainWorthy })
        assertEquals("auth.cachedProfile", DefaultsKey.CachedProfile.wireValue)
    }
}
