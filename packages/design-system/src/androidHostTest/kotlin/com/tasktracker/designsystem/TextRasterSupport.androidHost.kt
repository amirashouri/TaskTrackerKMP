package com.tasktracker.designsystem

import org.junit.Assume

/**
 * The Android host cannot host a Compose frame at all: its `android.jar` is a stub, Compose's
 * idling strategy dereferences a null `Build` field, and the tests in `commonTest` reach this
 * target without a Robolectric runner. See the common declaration for the whole of it.
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
        "the Android host cannot rasterise a Compose frame: Build.FINGERPRINT is null under " +
            "the stub android.jar and commonTest runs without a Robolectric runner",
        false,
    )
}
