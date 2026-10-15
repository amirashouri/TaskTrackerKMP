package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [jvmApiBaseUrl] on the JVM: the default, the `TASKTRACKER_API_URL` override, and the blank case.
 *
 * Moved here from `DesktopEntryPointJvmTest` when the resolver became shared: the value is no
 * longer Desktop's alone, so its rule is tested where the rule lives rather than in one shell's
 * entry-point suite.
 *
 * The environment is a parameter rather than `System.getenv()`, which is what makes both the
 * default and the override assertable without owning the test process's environment — the same
 * seam `desktopWindowSize` uses for the stored geometry.
 */
class PlatformApiBaseUrlJvmTest {

    @Test
    fun `an unset environment falls back to the local server`() {
        assertEquals(
            "http://localhost:8080",
            jvmApiBaseUrl(emptyMap()),
            "with nothing set, the app must reach the docker-compose server on 8080",
        )
    }

    @Test
    fun `an exported override wins`() {
        // The name is spelled out rather than read from `API_BASE_URL_ENV_VAR`, deliberately:
        // a map keyed by the constant would still match after the constant were renamed, and
        // the exported name *is* the contract — a user who exported `TASKTRACKER_API_URL`
        // would silently be ignored.
        assertEquals(
            "https://staging.example.test/api",
            jvmApiBaseUrl(mapOf("TASKTRACKER_API_URL" to "https://staging.example.test/api")),
            "an exported override must win, or a build cannot reach a server that is not the "
                + "local one",
        )
    }

    @Test
    fun `a blank override falls back rather than reaching an unusable url`() {
        // A blank value is a real case — an unset-looking shell variable, or a CI job that
        // exports an empty string — and it must not reach `KtorHttpClient` as a base URL:
        // `URLBuilder("")` has no scheme, so every request would fail before it left.
        assertEquals(
            "http://localhost:8080",
            jvmApiBaseUrl(mapOf("TASKTRACKER_API_URL" to "   ")),
            "a blank override must fall back to the default rather than be used as an address",
        )
    }
}
