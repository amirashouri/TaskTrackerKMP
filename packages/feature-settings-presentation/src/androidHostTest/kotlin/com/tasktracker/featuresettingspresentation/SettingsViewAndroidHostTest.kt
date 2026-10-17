package com.tasktracker.featuresettingspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SettingsScenarios] on the Android host, under Robolectric.
 *
 * The counterpart to [SettingsViewJvmTest] and [SettingsViewIosTest]. Only the entry points are
 * here; every assertion lives in [SettingsScenarios].
 *
 * ## Why this file exists
 *
 * Robolectric only engages when a JUnit 4 runner is attached, and `@RunWith` cannot be written in
 * `commonTest`. A `commonTest` Compose UI class compiles but then fails identically on this target,
 * before reaching an assertion — which is why the entry points are split per host rather than
 * shared.
 *
 * [GraphicsMode.NATIVE] is what actually rasterises the composition. In Robolectric's legacy mode
 * nothing is drawn, and any assertion depending on a node having been laid out then fails on
 * geometry rather than on behaviour.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsViewAndroidHostTest : SettingsViewScenarios()
