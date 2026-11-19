package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

/**
 * The Android host cannot force the system appearance through a composition local.
 *
 * This actual is never exercised: the fringe scenario calls `assumeHostRasterisesText()`
 * before composing, and the Android host throws a real skip there. It exists so `commonTest`
 * can compile against the seam without importing a JVM/iOS-only API.
 */
@Composable
internal actual fun WithSystemDarkAppearance(content: @Composable () -> Unit) {
    content()
}
