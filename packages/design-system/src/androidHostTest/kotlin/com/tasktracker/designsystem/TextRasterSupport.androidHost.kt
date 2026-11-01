package com.tasktracker.designsystem

import org.junit.Assume

/**
 * False, and not because the host cannot compose.
 *
 * The Android host rasterises under Robolectric — `SearchFieldAndroidHostTest` and its
 * siblings prove that with the same page. This is false because the pixel tests stay in
 * `commonTest` with no Robolectric entry point, and because the thresholds they assert are
 * calibrated to Skia, which Robolectric's native rasteriser is measured to disagree with
 * about strokes and coverage. See the common declaration for the whole of it.
 */
internal actual fun thisHostRasterisesText(): Boolean = false

/**
 * A real skip, recorded in the XML, rather than a silent pass.
 *
 * `Assume.assumeTrue(false, …)` throws `AssumptionViolatedException`; JUnit 4 turns that into
 * a skipped test instead of a green one. That distinction is the point of the harness change:
 * the old early return made a host that measured nothing look like a host that agreed.
 */
internal actual fun assumeHostRasterisesText() {
    Assume.assumeTrue(
        "this pixel test has no Robolectric entry point, and Robolectric's rasteriser is " +
            "measured to disagree with Skia about strokes and coverage, so a frame measured " +
            "here would not mean what the JVM's and iOS's do",
        false,
    )
}
