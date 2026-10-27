package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS's answer: the system Reduce Motion switch.
 *
 * The same value UIKit exposes to every app, read rather than cached, so a user who changes it in
 * Settings and comes back to the app gets the new answer when the shell next composes.
 */
@Composable
actual fun platformReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
