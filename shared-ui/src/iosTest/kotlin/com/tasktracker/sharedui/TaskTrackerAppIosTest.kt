package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi

/**
 * [ShellScenarios] on the iOS simulator, inherited from [TaskTrackerAppScenarios].
 *
 * The same scenarios on the iOS simulator, which is the only place the real iOS keychain and
 * * preferences backends are exercised through a real `AppGraph`.
 *
 * No `commonTest` entry point, for the reason [ShellScenarios] gives: an Android-host
 * Compose UI test only runs under Robolectric, and that runner annotation is not
 * expressible in `commonTest`.
 */
@OptIn(ExperimentalTestApi::class)
class TaskTrackerAppIosTest : TaskTrackerAppScenarios()
