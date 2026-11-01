package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import org.junit.Assume
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [ShellScenarios] on the Android host, at the phone viewport.
 *
 * The only target of the three that needs a runner annotation, which is precisely why the
 * assertions live in `commonTest` and this file contributes nothing but entry points. See
 * [ShellScenarios] for the full explanation — against the host's stub `android.jar`,
 * `Build.FINGERPRINT` is null and Compose's own idling strategy dereferences it, so without
 * Robolectric every test dies in its constructor.
 *
 * The wide-layout scenarios are not in this class: Robolectric's default display is a phone, and
 * the same host runs them at a 1400dp qualifier in [TaskTrackerWideAndroidHostTest]. Splitting
 * by viewport is what removed the `thisHostExpressesDesktopWidth` gate these scenarios used to
 * consult.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskTrackerAppAndroidHostTest : TaskTrackerAppScenarios()

/**
 * The Android host cannot, so a scenario's back assertions are skipped here rather than left red.
 *
 * `pressSystemBack` enters through `activity.onBackPressed()` — the device's own chain — and under
 * Robolectric that press does not reach the shell's `BackHandler`: the router is untouched, which
 * is why `systemBackPopsTheSprintDetail` fails on this host at its first back assertion. Declining
 * the capability keeps this scenario out of that red, and keeps the two assertions that do not need
 * a press — the stack it installs and the replacement it performs — running here.
 */
internal actual fun thisHostDeliversSystemBack(): Boolean = false

/** A real skip, for the scenario whose whole claim is a delivered back press. */
internal actual fun assumeHostDeliversSystemBack() {
    Assume.assumeTrue(
        "this host cannot deliver a system back press into the composition: the press enters " +
            "through activity.onBackPressed() and does not reach the shell's BackHandler under " +
            "Robolectric",
        false,
    )
}
