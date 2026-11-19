package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

/**
 * Runs [content] with the *system* appearance forced to dark, where the host can.
 *
 * ## Why this is an expect/actual
 *
 * `LocalSystemTheme` is the seam `isSystemInDarkTheme()` reads on desktop and iOS, but the
 * Android Compose artifact has no such local — the platform's appearance arrives through the
 * configuration instead. A `commonTest` import of it therefore compiles on JVM and iOS and
 * fails `compileAndroidHostTest`, which is exactly what the full matrix gate caught.
 *
 * The fringe test that needs this calls [assumeHostRasterisesText] first, so the Android
 * actual is never reached: it exists to satisfy the compiler, and it does not pretend to
 * force an appearance it cannot.
 */
@Composable
internal expect fun WithSystemDarkAppearance(content: @Composable () -> Unit)
