package com.tasktracker.keychain

import com.tasktracker.core.WorkItemID
import com.tasktracker.core.randomProjectID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Secret(val token: String, val expiresAt: String)

class InMemoryKeychainTest {

    @Test
    fun `absent key is null not an error`() = runTest {
        val keychain = InMemoryKeychain()
        assertNull(keychain.data("auth.accessToken"))
    }

    @Test
    fun `round-trips raw bytes`() = runTest {
        val keychain = InMemoryKeychain()
        val payload = byteArrayOf(1, 2, 3)

        keychain.set(payload, key = "k")

        assertContentEquals(payload, keychain.data("k"))
    }

    @Test
    fun `a stored payload is copied not aliased`() = runTest {
        // Without the copy, a caller mutating the array it passed in would silently
        // rewrite what the keychain "holds", and the reverse would expose the store's
        // internal buffer to a caller that keeps it.
        val keychain = InMemoryKeychain()
        val payload = byteArrayOf(1, 2, 3)
        keychain.set(payload, key = "k")

        payload[0] = 99
        assertContentEquals(byteArrayOf(1, 2, 3), keychain.data("k"))
    }

    @Test
    fun `removeAll leaves nothing behind`() = runTest {
        val keychain = InMemoryKeychain()
        keychain.set(byteArrayOf(1), key = "one")
        keychain.set(byteArrayOf(2), key = "two")

        keychain.removeAll()

        assertTrue(keychain.keys().isEmpty())
    }

    @Test
    fun `surfaces injected failures so error paths are testable`() = runTest {
        val keychain = InMemoryKeychain()
        keychain.failWith(KeychainError(-34018, "read", "x"))

        assertFailsWith<KeychainError> { keychain.data("anything") }

        keychain.stopFailing()
        assertNull(keychain.data("anything"))
    }

    @Test
    fun `injected failures apply to writes and removal too`() = runTest {
        val keychain = InMemoryKeychain()
        keychain.set(byteArrayOf(1), key = "k")
        keychain.failWith(KeychainError(-34018, "read", "x"))

        assertFailsWith<KeychainError> { keychain.set(byteArrayOf(2), key = "k") }
        assertFailsWith<KeychainError> { keychain.remove("k") }
        assertFailsWith<KeychainError> { keychain.removeAll() }

        keychain.stopFailing()
        assertContentEquals(byteArrayOf(1), keychain.data("k"), "a failed write must not land")
    }
}

class KeychainStoreTest {

    private val secret = Secret(token = "t", expiresAt = "2026-01-01T00:00:00Z")

    @Test
    fun `uses the namespaced well-known keys`() = runTest {
        val keychain = InMemoryKeychain()
        val store = KeychainStore(keychain)

        store.set(secret, key = KeychainKey.AccessToken)

        assertEquals(listOf("auth.accessToken"), keychain.keys())
        assertEquals(secret, store.value<Secret>(KeychainKey.AccessToken))
    }

    @Test
    fun `sign-out wipes every credential`() = runTest {
        val keychain = InMemoryKeychain()
        val store = KeychainStore(keychain)
        store.set(secret, key = KeychainKey.AccessToken)
        store.set(secret, key = KeychainKey.RefreshToken)

        store.removeAll()

        assertTrue(keychain.keys().isEmpty())
        assertNull(store.value<Secret>(KeychainKey.AccessToken))
    }

    @Test
    fun `removing one credential leaves the other`() = runTest {
        val keychain = InMemoryKeychain()
        val store = KeychainStore(keychain)
        store.set(secret, key = KeychainKey.AccessToken)
        store.set(secret, key = KeychainKey.RefreshToken)

        store.remove(KeychainKey.AccessToken)

        assertEquals(listOf("auth.refreshToken"), keychain.keys())
        assertEquals(secret, store.value<Secret>(KeychainKey.RefreshToken))
    }

    @Test
    fun `a value stored twice replaces rather than appends`() = runTest {
        val keychain = InMemoryKeychain()
        val store = KeychainStore(keychain)

        store.set(secret, key = KeychainKey.AccessToken)
        store.set(secret.copy(token = "refreshed"), key = KeychainKey.AccessToken)

        assertEquals(secret.copy(token = "refreshed"), store.value<Secret>(KeychainKey.AccessToken))
    }

    @Test
    fun `a corrupt payload throws instead of reading as absent`() = runTest {
        // The deliberate opposite of UserDefaultsStore.value: a keychain entry exists
        // only because the app wrote it, so unreadable bytes are a real fault and the
        // auth layer has to see them. Returning null here would sign the user out
        // silently and look like a session that simply expired.
        val keychain = InMemoryKeychain()
        val store = KeychainStore(keychain)
        store.setBytes("not json".encodeToByteArray(), key = KeychainKey.AccessToken)

        assertFailsWith<Exception> { store.value<Secret>(KeychainKey.AccessToken) }
    }

    @Test
    fun `every target can produce a keychain or says why it cannot`() = runTest {
        // Cannot assert the *behaviour* of the platform factory from common code: the JVM
        // one is the in-memory double, the iOS one is the real keychain, and the Android
        // one needs a Context that common code is not allowed to name. The invariant worth
        // holding everywhere is only that the expect/actual pair is wired up — so either a
        // store comes back, or the failure names the missing Context rather than
        // surfacing a bare ClassCastException from the cast.
        val result = runCatching { systemKeychain() }
        result.exceptionOrNull()?.let { failure ->
            assertTrue(
                failure.message.orEmpty().contains("Context"),
                "an unhelpful failure from systemKeychain(): ${failure.message}",
            )
        }
    }
}
