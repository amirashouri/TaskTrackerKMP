package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

/**
 * iOS's answer: the system Reduce Transparency switch.
 *
 * The function, not the notification. The switch lives in Settings, so flipping it leaves the
 * app; the next foreground composition reads the new value. Subscribing to
 * `UIAccessibilityReduceTransparencyStatusDidChange` would only shorten the delay for a
 * change that cannot happen while the app is frontmost, at the cost of an observer per shell.
 */
@Composable
actual fun platformReduceTransparency(): Boolean = UIAccessibilityIsReduceTransparencyEnabled()
