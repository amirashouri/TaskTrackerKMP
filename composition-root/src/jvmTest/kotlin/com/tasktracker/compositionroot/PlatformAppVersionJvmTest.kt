package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Desktop About row's value, mapped from the jar manifest.
 *
 * ## Why this is the mapping and not the lookup
 *
 * `platformAppVersion` reads `Package.implementationVersion`, which is only populated when
 * the class is loaded from a jar carrying `Implementation-Version`. The test suite loads
 * classes from directories, so there is no manifest to assert against here; the stamp
 * itself is checked by unzipping a `:composition-root:jvmJar` built with
 * `-PtasktrackerVersion`. This test pins the half that can be wrong independently: what the
 * row shows for a given stamped value, and that "no stamp" hides it.
 */
class PlatformAppVersionJvmTest {

    @Test
    fun aStampedReleaseVersionIsWhatTheRowShows() {
        assertEquals(
            "0.1.6",
            jvmAppVersion("0.1.6").display,
            "a packaged Desktop build must show the release tag, not hide the row or pad it " +
                "with a build number the JVM host does not have",
        )
    }

    @Test
    fun aMissingOrBlankStampHidesTheRow() {
        assertNull(
            jvmAppVersion(null).display,
            "a classes-directory host has no manifest; rendering a placeholder would be a " +
                "version nobody shipped",
        )
        assertNull(jvmAppVersion("").display, "an empty attribute is no version at all")
        assertNull(
            jvmAppVersion("   ").display,
            "whitespace is absent, not a marketing version made of spaces",
        )
    }

    @Test
    fun whitespaceAroundTheStampIsTrimmed() {
        assertEquals("0.1.6", jvmAppVersion(" 0.1.6 ").display)
    }
}
