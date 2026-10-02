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
 * this target's classpath precisely to fix that, and it does not fix it here: the tests in
 * `commonTest` reach the Android host as plain JUnit 4 classes, and Robolectric only takes
 * over for a class that names its runner, which an Android-only annotation cannot do from
 * `commonTest` without taking the iOS and JVM compilations down with it.
 *
 * So a test that reads rendered pixels asks this first and skips when the answer is no. Two
 * things follow that are worth being explicit about.
 *
 * The skip is not a coverage loss dressed up as a pass. The JVM and the iOS simulator both
 * rasterise, and the iOS simulator is the one that matters: it is where these tests caught
 * the light-appearance captions measuring a full half-point lower than on the JVM, purely
 * from that rasteriser's stroke coverage. Two hosts is what this assertion needs to be
 * worth anything, because the defect it exists for only appears when the hosts disagree.
 *
 * And the day the Android host can render, this returns true and the tests run there with no
 * edit to them. That is the point of asking rather than deleting.
 */
internal expect fun thisHostRasterisesText(): Boolean

/**
 * Runs [body] only where a Compose frame can actually be rasterised.
 *
 * ## What the skip looks like in a report, honestly
 *
 * On the Android host this returns without asserting, and the host records that as a *pass*,
 * not as a skip. `kotlin.test` has no portable skip, and the mechanism JUnit 4 would use -
 * `org.junit.Assume`, an assumption failure the runner turns into a skip - does not exist on
 * the iOS and JVM compilations this same code has to satisfy. So the reason is printed to the
 * build output instead of being recorded in the XML, and the test count on that host includes
 * these two as green.
 *
 * That is a weaker signal than a real skip and it is worth not dressing up: a reader of
 * `testAndroidHostTest` results alone would conclude these assertions ran, and they did not.
 * What is not weak is the other two hosts. The JVM and the iOS simulator both rasterise, both
 * run every assertion, and the pair is the reason this test is worth having at all - the
 * defect it exists for only appears when the two disagree. The Android host adds a third
 * opinion it is currently unable to form.
 */
@OptIn(ExperimentalTestApi::class)
internal fun rasterising(body: ComposeUiTest.() -> Unit) {
    if (!thisHostRasterisesText()) {
        println("skipped: this host cannot rasterise a Compose frame to read pixels from")
        return
    }
    // The composition is entered here rather than by the caller so that the host check
    // happens first. `runComposeUiTest` is what fails on the Android host, and it fails in
    // its own environment setup, so a test that called it before asking would die there
    // instead of reaching the branch that explains why.
    runComposeUiTest(block = body)
}
