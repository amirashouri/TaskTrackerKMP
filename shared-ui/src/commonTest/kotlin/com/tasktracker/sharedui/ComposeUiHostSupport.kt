package com.tasktracker.sharedui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest

/**
 * Whether this host can run a Compose frame that a test can read pixels out of.
 *
 * True on the JVM and on the iOS simulator, false on the Android host. The cause is the
 * Android host's own environment rather than anything about the code under test: a
 * `runComposeUiTest` there dies in its setup, before a test body can take a pixel, with
 *
 *     NullPointerException: Cannot invoke "String.toLowerCase()" because
 *     "android.os.Build.FINGERPRINT" is null
 *
 * Its `android.jar` is a stub whose `Build` fields are null, and Compose's
 * `RobolectricIdlingStrategy` dereferences one unconditionally. Robolectric is on this
 * target's classpath precisely to fix that and does not fix it here: tests in `commonTest`
 * reach the Android host as plain JUnit 4 classes, and Robolectric only takes over for a class
 * naming its runner — an Android-only annotation that cannot be written from `commonTest`
 * without taking the iOS and JVM compilations down with it.
 *
 * `:packages:design-system` declares the same `expect` for its own pixel tests. It is
 * duplicated rather than shared because a `commonTest` declaration is not visible across
 * modules; the reason it is worth repeating is the same in both places.
 *
 * The skip is not a coverage loss dressed up as a pass. The JVM and the iOS simulator both
 * render, and the iOS simulator is the one that matters — it is where the two disagree about
 * how much of a colour a pixel actually receives. Two hosts is what makes these assertions
 * worth having. Reading the Android host's results alone would conclude they ran; they did not.
 */
internal expect fun thisHostRasterisesText(): Boolean

/**
 * Records the current test as skipped on a host that cannot rasterise.
 *
 * An assumption, not an early return: a test that returns before its assertions is recorded as
 * a pass, and a green row for a host that measured nothing is the lie this harness exists to
 * stop. The Android actual throws through `org.junit.Assume`; the JVM and iOS actuals do
 * nothing. The annotation itself cannot live in `commonTest` — `org.junit` is not on the iOS
 * or Native compilations — which is why this is an `expect` function.
 */
internal expect fun assumeHostRasterisesText()

/**
 * Runs [body] only where a Compose frame can actually be rasterised.
 *
 * The guard is an assumption, so the Android host records a **skip** rather than a pass. The
 * composition is entered here rather than by the caller so this check happens first:
 * `runComposeUiTest` is what fails on the Android host, and it fails in its own environment
 * setup, so a caller that invoked it before asking would die there instead of reaching the
 * branch that explains why.
 */
@OptIn(ExperimentalTestApi::class)
internal fun rasterising(body: ComposeUiTest.() -> Unit) {
    assumeHostRasterisesText()
    runComposeUiTest(block = body)
}
