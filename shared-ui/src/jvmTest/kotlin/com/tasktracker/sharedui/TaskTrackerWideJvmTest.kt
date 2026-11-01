package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi

/**
 * [TaskTrackerWideScenarios] on the JVM, inherited from the wide base class.
 *
 * A second entry point rather than a second base class on [TaskTrackerAppJvmTest], because
 * Kotlin has single class inheritance and the split is by viewport. The 1000dp JVM window
 * carries both the 1000dp desktop shell and the 1280dp sidebar.
 */
@OptIn(ExperimentalTestApi::class)
class TaskTrackerWideJvmTest : TaskTrackerWideScenarios()
