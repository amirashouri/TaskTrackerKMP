package com.tasktracker.sharedui

import org.junit.Assume

/**
 * False, and the reason is this host's stub `android.jar` rather than anything about the code
 * under test — Compose's `RobolectricIdlingStrategy` dereferences `Build.FINGERPRINT`, which is
 * null here, and `runComposeUiTest` dies in its own setup before a test body can take a pixel.
 * The full account is on the common declaration.
 */
internal actual fun thisHostRasterisesText(): Boolean = false

/**
 * A real skip, recorded in the XML, rather than a silent pass.
 *
 * `Assume.assumeTrue(false, …)` throws `AssumptionViolatedException`, which JUnit 4 records as
 * skipped. That is the whole point of the change: the old early return made a host that measured
 * nothing look like a host that agreed.
 */
internal actual fun assumeHostRasterisesText() {
    Assume.assumeTrue(
        "the Android host cannot rasterise a Compose frame: Build.FINGERPRINT is null under " +
            "the stub android.jar and commonTest runs without a Robolectric runner",
        false,
    )
}
