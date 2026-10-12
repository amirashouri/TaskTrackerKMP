package com.tasktracker.keychain

import com.tasktracker.core.isDesktopApp
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The JVM keychain actual: which backend the process gets, and — opt-in — that the macOS one
 * actually stores anything.
 *
 * ## Why the round trip is behind a flag
 *
 * The routing tests below touch nothing. The round-trip test writes to the developer's real
 * login keychain, which makes it unfit for the default suite for three separate reasons: it
 * mutates state outside the build directory, it would prompt for access on a locked keychain
 * (hanging a headless run rather than failing it), and it cannot pass at all off macOS. So it
 * runs only when `TASKTRACKER_KEYCHAIN_INTEGRATION=1` is set, which is how it was verified:
 *
 * ```
 * TASKTRACKER_KEYCHAIN_INTEGRATION=1 ./gradlew :packages:keychain:jvmTest
 * ```
 *
 * Be aware, per `AGENTS.md`'s note on `thisHostRasterisesText()`, that a guarded test
 * registers as a **pass** when it does not run. The default suite therefore says nothing about
 * whether the JNA binding works — only the flagged run does.
 *
 * It uses a unique `kSecAttrService` per run and wipes that service in a `finally`, so it
 * cannot collide with the app's own items (`com.tasktracker.app`) or with a previous run.
 */
class MacOsKeychainTest {

    private val integrationEnabled: Boolean
        get() = System.getenv("TASKTRACKER_KEYCHAIN_INTEGRATION") == "1"

    @Test
    fun `the jvm test host keeps the in-memory keychain`() = runTest {
        // The property that keeps a test run out of the developer's keychain. Without it, this
        // module's own suite would write credentials to a real keychain on every build.
        withDesktopApp(false) {
            assertIs<InMemoryKeychain>(
                systemKeychain(),
                "the test host must not reach a real credential store",
            )
        }
    }

    @Test
    fun `a desktop process gets a real backend on macOS and an honest stub elsewhere`() = runTest {
        withDesktopApp(true) {
            val backend = systemKeychain()
            if (isMacOs()) {
                assertIs<MacOsKeychain>(
                    backend,
                    "a Desktop process on macOS must get OS-backed storage, or the session " +
                        "cannot survive a restart",
                )
            } else {
                // No Windows Credential Manager or libsecret binding exists yet. Asserted
                // rather than skipped so that adding one has to change this test.
                assertIs<InMemoryKeychain>(
                    backend,
                    "off macOS the backend must still be the stub, and say so in the log",
                )
            }
        }
    }

    @Test
    fun `a stored value is read back by a second backend over the same service`() = runTest {
        if (!integrationEnabled) return@runTest
        if (!isMacOs()) return@runTest

        val service = "com.tasktracker.test.${UUID.randomUUID()}"
        val key = KeychainKey.AccessToken.wireValue
        try {
            // A second instance, so the read cannot be served from anything the writer held in
            // memory — that is the property an in-memory stub fails and the OS store passes.
            MacOsKeychain(service).set("token-1".encodeToByteArray(), key)

            assertEquals(
                "token-1",
                MacOsKeychain(service).data(key)?.decodeToString(),
                "the item written under $service must be readable by a fresh backend",
            )

            // Overwrite rather than add: the second `set` hits errSecDuplicateItem and goes
            // down the update path, which is the branch a re-sign-in uses.
            MacOsKeychain(service).set("token-2".encodeToByteArray(), key)
            assertEquals(
                "token-2",
                MacOsKeychain(service).data(key)?.decodeToString(),
                "a second write must replace the item's data, not fail as a duplicate",
            )

            MacOsKeychain(service).remove(key)
            assertNull(
                MacOsKeychain(service).data(key),
                "a removed item must read back as absent",
            )
        } finally {
            // Scoped to this run's service name, so it cannot touch the app's own items even
            // if the assertions above threw.
            MacOsKeychain(service).removeAll()
        }
    }

    @Test
    fun `removeAll wipes every key in the service, not just the one that was named`() = runTest {
        if (!integrationEnabled) return@runTest
        if (!isMacOs()) return@runTest

        val service = "com.tasktracker.test.${UUID.randomUUID()}"
        try {
            MacOsKeychain(service).apply {
                set("access".encodeToByteArray(), KeychainKey.AccessToken.wireValue)
                set("refresh".encodeToByteArray(), KeychainKey.RefreshToken.wireValue)
            }

            // Both present first, so the wipe below is a deletion rather than an absence
            // proving nothing.
            assertTrue(MacOsKeychain(service).data(KeychainKey.AccessToken.wireValue) != null)
            assertTrue(MacOsKeychain(service).data(KeychainKey.RefreshToken.wireValue) != null)

            // Sign-out. The query omits kSecAttrAccount on purpose, and this is what holds
            // that: a key the caller never named must still be gone afterwards.
            MacOsKeychain(service).removeAll()

            assertNull(
                MacOsKeychain(service).data(KeychainKey.AccessToken.wireValue),
                "sign-out must leave no access token behind",
            )
            assertNull(
                MacOsKeychain(service).data(KeychainKey.RefreshToken.wireValue),
                "sign-out must leave no refresh token behind, even though only the access " +
                    "token was named",
            )
        } finally {
            MacOsKeychain(service).removeAll()
        }
    }

    @Test
    fun `an absent key reads as no item rather than failing`() = runTest {
        if (!integrationEnabled) return@runTest
        if (!isMacOs()) return@runTest

        val service = "com.tasktracker.test.${UUID.randomUUID()}"
        try {
            // errSecItemNotFound is the normal "not signed in" answer. Turning it into a
            // throw would make a signed-out launch look like a keychain fault.
            assertNull(
                MacOsKeychain(service).data(KeychainKey.AccessToken.wireValue),
                "a key that was never stored must read as null",
            )
        } finally {
            MacOsKeychain(service).removeAll()
        }
    }

    /**
     * Runs [block] with [isDesktopApp] set to [value], restoring it afterwards.
     *
     * The flag is process-global, so a leaked `true` would change what every later test in this
     * JVM constructs — including reaching a real keychain, which is exactly what the first test
     * exists to prevent.
     */
    private inline fun withDesktopApp(value: Boolean, block: () -> Unit) {
        val previous = isDesktopApp
        try {
            isDesktopApp = value
            block()
        } finally {
            isDesktopApp = previous
        }
    }
}
