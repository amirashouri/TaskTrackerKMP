package com.tasktracker.featuresettingspresentation

import androidx.compose.ui.test.ExperimentalTestApi

/**
 * [SettingsScenarios] on the iOS simulator, inherited from [SettingsViewScenarios].
 *
 * Identical entry points to [SettingsViewJvmTest] and [SettingsViewAndroidHostTest], and the
 * reason they are three files rather than one in `commonTest` is the Android host's Robolectric
 * runner, which `commonTest` cannot carry — see [SettingsViewJvmTest].
 */
@OptIn(ExperimentalTestApi::class)
class SettingsViewIosTest : SettingsViewScenarios()
