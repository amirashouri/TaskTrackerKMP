package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import org.junit.Assume
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [ShellScenarios] on the Android host.
 *
 * The only target of the three that needs a runner annotation, which is precisely why the
 * assertions live in `commonTest` and this file contributes nothing but entry points. See
 * [ShellScenarios] for the full explanation — against the host's stub `android.jar`,
 * `Build.FINGERPRINT` is null and Compose's own idling strategy dereferences it, so without
 * Robolectric every test dies in its constructor.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskTrackerAppAndroidHostTest : TaskTrackerAppScenarios()

/**
 * The Android host cannot, so the wide-layout scenarios skip here.
 *
 * Robolectric's viewport is 320dp, and a fixed width is clamped by its parent's constraints, so a
 * 1000dp shell is laid out at 320dp — comfortably under the expanded threshold, where the shell
 * correctly draws the phone chrome. Asserting there would assert nothing; failing there would be
 * blaming the host for a phone.
 *
 * This is the same bargain `thisHostRasterisesText` makes in `:packages:design-system`, for the
 * same reason, and it is recorded in `AGENTS.md` because `kotlin.test` has no portable skip and
 * the host records the return as a *pass*.
 */
internal actual fun thisHostExpressesDesktopWidth(): Boolean = false

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

/** A real skip, so the three wide-layout scenarios stop registering as passes here. */
internal actual fun assumeHostExpressesDesktopWidth() {
    Assume.assumeTrue(
        "this host cannot lay out a shell wider than the expanded threshold: Robolectric's " +
            "viewport clamps a fixed width, so the wide shell these scenarios are about cannot " +
            "be built here",
        false,
    )
}

/** A real skip, for the scenario whose whole claim is a delivered back press. */
internal actual fun assumeHostDeliversSystemBack() {
    Assume.assumeTrue(
        "this host cannot deliver a system back press into the composition: the press enters " +
            "through activity.onBackPressed() and does not reach the shell's BackHandler under " +
            "Robolectric",
        false,
    )
}
