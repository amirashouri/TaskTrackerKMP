package com.tasktracker.compositionroot

import com.tasktracker.userdefaults.API_BASE_URL_PREFERENCE_KEY
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.PreferencesBackend
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The saved API-root override: precedence over the build-time value, the raw-key read, and
 * the failure path that keeps an unreadable preference from refusing to launch.
 *
 * The three existing `PlatformApiBaseUrl*Test` files stay untouched. They pin the build-time
 * rules, which must not change when no override is saved; this file only adds the branch in
 * front of them.
 */
class PlatformApiBaseUrlOverrideTest {

    @Test
    fun `a saved address wins over the build-time value`() {
        assertEquals(
            "https://tasks.example.com",
            apiBaseUrlWithOverride("http://localhost:8080", "https://tasks.example.com"),
            "a customer's server must not be silently replaced by the build-time value",
        )
    }

    @Test
    fun `a blank saved address falls back to the build-time value`() {
        for (blank in listOf("", "   ", "\t\n")) {
            assertEquals(
                "http://10.0.2.2:8080",
                apiBaseUrlWithOverride("http://10.0.2.2:8080", blank),
                "a blank saved value has no scheme and must read as unset, not as the root",
            )
        }
    }

    @Test
    fun `no saved address keeps the build-time value exactly`() {
        assertEquals(
            "http://localhost:8080",
            apiBaseUrlWithOverride("http://localhost:8080", null),
            "the no-override path must be the build-time value, byte for byte",
        )
    }

    @Test
    fun `the saved address is read from the raw key`() {
        val backend = InMemoryPreferences()
        runBlocking {
            UserDefaultsStore(backend).set(
                "https://tasks.example.com",
                key = API_BASE_URL_PREFERENCE_KEY,
            )
        }

        assertEquals(
            "https://tasks.example.com",
            readStoredApiBaseUrl(backend),
            "the resolver must read the key Settings writes, verbatim",
        )
    }

    @Test
    fun `nothing stored reads as absent`() {
        assertNull(readStoredApiBaseUrl(InMemoryPreferences()))
    }

    @Test
    fun `an unreadable store reads as absent instead of throwing`() {
        assertNull(
            readStoredApiBaseUrl(UnreadablePreferences()),
            "a corrupt preference file must fall back to the build-time value, not block launch",
        )
    }

    @Test
    fun `a saved local address is still classified as local`() {
        val resolved = apiBaseUrlWithOverride(
            buildTime = "https://tasks.example.com",
            storedOverride = "http://192.168.1.25:8080",
        )

        assertTrue(
            apiRootRequiresLocalNetworkAccess(resolved),
            "the Android permission gate reads platformApiBaseUrl's answer, so the saved " +
                "address must be classified rather than the build-time value",
        )
    }
}

/** A backend whose stored bytes cannot be read, like a corrupt preferences file. */
private class UnreadablePreferences : PreferencesBackend {
    override suspend fun bytes(key: String): ByteArray? = error("corrupt preferences")

    override suspend fun put(bytes: ByteArray, key: String) = Unit

    override suspend fun remove(key: String) = Unit

    override suspend fun contains(key: String): Boolean = false
}
