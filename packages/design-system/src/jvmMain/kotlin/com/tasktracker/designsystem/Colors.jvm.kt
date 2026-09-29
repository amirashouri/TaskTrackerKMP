package com.tasktracker.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The JVM target exists only so `commonTest` runs on the host in seconds; it is never
 * shipped and has no platform theme to ask. These values are therefore fixed rather than
 * adaptive, and dark mode is honoured only to the extent a headless test can observe it.
 *
 * They are the light-mode values from UIKit, written out so a test comparing the tokens
 * against a golden is comparing the same numbers the iOS target produces in light mode.
 */
@Composable
actual fun ttCanvas(): Color = if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFFFFFFF)

@Composable
actual fun ttSurface(): Color =
    if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

@Composable
actual fun ttSeparator(): Color =
    if (isSystemInDarkTheme()) Color(0x38383838) else Color(0x3C000000)
