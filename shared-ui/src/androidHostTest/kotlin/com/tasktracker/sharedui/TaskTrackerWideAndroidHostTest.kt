package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [TaskTrackerWideScenarios] on the Android host, at a desktop-width viewport.
 *
 * ## Why the qualifier, and why it is a separate class
 *
 * Robolectric's default display is a phone, so a 1000dp shell is clamped and the 1280dp sidebar
 * never composes. `qualifiers = "w1400dp-h1000dp"` asks for a display that fits both; measured
 * on this host, a `fillMaxSize()` composition comes back 1400dp wide. The qualifier is
 * class-level, which is exactly why the wide scenarios are their own base class: applying it to
 * [TaskTrackerAppAndroidHostTest] would lay out its 66 compact scenarios on a desktop window and
 * the phone-chrome assertions would fail for the wrong reason.
 *
 * `@GraphicsMode(NATIVE)` is still required: these scenarios read bounds and semantics, but the
 * composition itself does not rasterise without it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1400dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskTrackerWideAndroidHostTest : TaskTrackerWideScenarios()
