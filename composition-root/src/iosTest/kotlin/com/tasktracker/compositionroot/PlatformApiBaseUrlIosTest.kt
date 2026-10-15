package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [iosApiBaseUrl] on the iOS simulator: the default, the `Info.plist` override, and the blank case.
 *
 * The plist lookup is a parameter rather than `NSBundle.mainBundle`, because a test host has no
 * app bundle to read — the same reason [platformAppVersion] returns blanks there. Passing the
 * reader in is what lets the key *name* be pinned as well as the value, which matters because
 * the key is a contract a deployment writes into its own `Info.plist`.
 */
class PlatformApiBaseUrlIosTest {

    @Test
    fun `an absent plist key falls back to the local server`() {
        assertEquals(
            "http://localhost:8080",
            iosApiBaseUrl { null },
            "with no key set, the app must reach the local server the simulator shares a host with",
        )
    }

    @Test
    fun `the plist key wins and is the key a deployment writes`() {
        val requestedKeys = mutableListOf<String>()
        val resolved = iosApiBaseUrl { key ->
            requestedKeys += key
            "https://staging.example.test/api"
        }

        assertEquals(
            "https://staging.example.test/api",
            resolved,
            "a configured key must win, or a build cannot reach a server that is not the local one",
        )
        // The key name is spelled out rather than read from `API_BASE_URL_PLIST_KEY`: the
        // string in `Info.plist` *is* the contract, and a constant-keyed assertion would still
        // pass after the constant were renamed and the plist key stopped matching.
        assertEquals(
            listOf("TaskTrackerApiBaseUrl"),
            requestedKeys,
            "the resolver must ask for the key the Info.plist declares, not some other one",
        )
    }

    @Test
    fun `a blank plist value falls back rather than reaching an unusable url`() {
        assertEquals(
            "http://localhost:8080",
            iosApiBaseUrl { "   " },
            "a blank value must fall back to the default rather than be used as an address",
        )
    }
}
