package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi

/**
 * [TaskTrackerWideScenarios] on the iOS simulator.
 *
 * Worth having on this host specifically: it is the rasteriser that disagrees with the JVM on
 * glyph coverage, so a wide shell is checked where a small glyph's stroke is drawn least
 * completely.
 */
@OptIn(ExperimentalTestApi::class)
class TaskTrackerWideIosTest : TaskTrackerWideScenarios()
