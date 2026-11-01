package com.tasktracker.designsystem

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest

/**
 * Whether this host can rasterise a Compose frame for a test to read pixels out of.
 *
 * True on the JVM and on the iOS simulator, false on the Android host. The reason is the
 * Android host's own test environment rather than anything about the code under test: a
 * `runComposeUiTest` there dies in its environment setup, before a test body can take a
 * pixel, with
 *
 *     NullPointerException: Cannot invoke "String.toLowerCase()" because
 * "android.os.Build.FINGERPRINT" is null
 *
 * The Android host's `android.jar` is a stub whose `Build` fields are null, and Compose's
 * `RobolectricIdlingStrategy` dereferences one of them unconditionally. Robolectric is on
 * this target's classpath and it does fix that — for a class that names its runner. The
 * tests in `commonTest` reach the Android host as plain JUnit 4 classes, and an Android-only
 * runner annotation cannot be written from `commonTest` without taking the iOS and JVM
 * compilations down with it.
 *
 * So these pixel tests stay in `commonTest` without an Android entry point, while the
 * structural UI tests do have one (`SearchFieldAndroidHostTest`, `DeviceMotionAndroidHostTest`
 * and `DateFieldInteractionAndroidHostTest` are the pattern). The Android host *can* compose
 * and rasterise; what it cannot do is agree with Skia. `:packages:feature-settings-presentation`
 * measured Robolectric's native rasteriser disagreeing about strokes and coverage, and this
 * module's thresholds are calibrated to Skia's output, so running them on a third rasteriser
 * would mean per-host tolerances — the assertion weakening `AGENTS.md` §3 forbids.
 *
 * The skip is not a coverage loss dressed up as a pass. The JVM and the iOS simulator both
 * rasterise, and the iOS simulator is the one that matters: it is where these tests caught
 * the light-appearance captions measuring a full half-point lower than on the JVM, purely
 * from that rasteriser's stroke coverage. Two hosts is what this assertion needs to be
 * worth anything, because the defect it exists for only appears when the hosts disagree.
 *
 * If Android's rasteriser ever agrees, a pixel test that gains a Robolectric entry point
 * should stop asking this and assert its threshold there; the capability is declared rather
 * than assumed for exactly that reason.
 */
internal expect fun thisHostRasterisesText(): Boolean

/**
 * Records the current test as skipped on a host that cannot rasterise.
 *
 * The contract is an *assumption*, not an early return: a test that returns before its
 * assertions is recorded as a pass, which is the exact lie this harness exists to stop. The
 * Android actual throws `org.junit.AssumptionViolatedException` through `Assume`, and the
 * runner records the skip; the JVM and iOS actuals do nothing, because both can rasterise.
 *
 * The annotation cannot live in `commonTest` — `org.junit` is not on the iOS or Native
 * compilations — which is why this is an `expect` function whose only platform-specific
 * import sits in the Android source set.
 */
internal expect fun assumeHostRasterisesText()

/**
 * Runs [body] only where a Compose frame can actually be rasterised.
 *
 * ## What the skip looks like in a report
 *
 * On the Android host [assumeHostRasterisesText] throws through `org.junit.Assume`, so the
 * runner records a real *skip* in the XML rather than a pass. `kotlin.test` has no portable
 * skip, which is why the assumption is an `expect` function whose only platform-specific
 * import sits in the Android source set — the iOS and JVM actuals are no-ops.
 *
 * That matters for reading a build: a reader of `testAndroidHostTest` results sees these
 * tests listed as skipped, not as green assertions that never ran. What is not weak is the
 * other two hosts. The JVM and the iOS simulator both rasterise, both run every assertion,
 * and the pair is the reason this test is worth having at all - the defect it exists for
 * only appears when the two disagree.
 */
@OptIn(ExperimentalTestApi::class)
internal fun rasterising(body: ComposeUiTest.() -> Unit) {
    // The assumption is thrown before `runComposeUiTest`, which is what fails on the Android
    // host in its own environment setup. It is deliberately not an early return: a return
    // records a pass, and a pass on a host that measured nothing is the failure mode this
    // whole file is about.
    assumeHostRasterisesText()
    runComposeUiTest(block = body)
}
