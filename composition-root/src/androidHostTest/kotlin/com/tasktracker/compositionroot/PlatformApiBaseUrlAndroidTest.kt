package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [androidApiBaseUrl] on the Android host: the default, the manifest override, and the blank case.
 *
 * No `@RunWith(RobolectricTestRunner::class)` and no `Context`, because the rule under test is a
 * pure function of the value the manifest supplied. The manifest read itself is the one part not
 * covered here — it needs a real package manager, and the resolver falls back rather than
 * throwing when there is none, which is exactly what this host looks like from there.
 */
class PlatformApiBaseUrlAndroidTest {

    @Test
    fun `an absent manifest value falls back to the emulator host alias`() {
        assertEquals(
            "http://10.0.2.2:8080",
            androidApiBaseUrl(null),
            "with nothing configured, the app must reach the local Go server through the "
                + "emulator's host alias",
        )
        // The deliberate divergence from every other platform, stated rather than implied: an
        // emulator's `localhost` is the emulator, so `DEFAULT_LOCAL_API_BASE_URL` would point
        // at nothing here.
        assertNotEquals(
            DEFAULT_LOCAL_API_BASE_URL,
            androidApiBaseUrl(null),
            "Android must not fall back to `localhost`, which is the emulator rather than its host",
        )
    }

    @Test
    fun `the manifest value wins`() {
        assertEquals(
            "https://server.example.com",
            androidApiBaseUrl("https://server.example.com"),
            "a configured value must win, or a deployment cannot reach its own server",
        )
    }

    @Test
    fun `a blank manifest value falls back rather than reaching an unusable url`() {
        // The manifest placeholder is left empty when the Gradle property is unset, so this is
        // the production path and not only a defensive branch.
        assertEquals(
            "http://10.0.2.2:8080",
            androidApiBaseUrl("   "),
            "a blank value must fall back to the default rather than be used as an address",
        )
    }
}
