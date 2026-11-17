package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
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
 *
 * The system-back scenarios run here too. This host used to declare that it could not deliver a
 * back press, because the press went through `activity.onBackPressed()` while the shell's
 * `BackHandler` had joined the dispatcher the scenario provides. Pressing the provided
 * dispatcher is the real chain, so no capability declaration is needed any more.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskTrackerAppAndroidHostTest : TaskTrackerAppScenarios()
