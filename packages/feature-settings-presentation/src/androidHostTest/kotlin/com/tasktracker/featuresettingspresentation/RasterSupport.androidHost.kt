package com.tasktracker.featuresettingspresentation

import org.junit.Assume

internal actual fun thisHostRasterisesText(): Boolean = false

/** A real JUnit skip, so a test this host cannot measure stops reading as a pass. */
internal actual fun assumeHostRasterisesText() {
    Assume.assumeTrue(
        "the Android host's Robolectric rasteriser disagrees with Skia about strokes and " +
            "coverage, so a frame measured here would not mean what the JVM's and iOS's do",
        false,
    )
}