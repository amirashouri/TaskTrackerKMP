package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The JVM target exists only so `commonTest` runs on the host in seconds; it is never
 * shipped and has no platform theme to ask. Everything is therefore a fixed value rather
 * than adaptive, built from `ttFixedColours` so a host test and a pinned appearance on a
 * real target see the same numbers rather than two sets that happen to agree today.
 *
 * `isSystemInDarkTheme` is still consulted, through [ttIsDarkTheme]: a headless test can be
 * launched with a dark system appearance and the tokens should follow it, which is what
 * makes an appearance-dependent test possible here at all.
 */
@Composable
actual fun ttCanvas(): Color = ttFixedColours.canvas(ttIsDarkTheme())

@Composable
actual fun ttSurface(): Color = ttFixedColours.surface(ttIsDarkTheme())

@Composable
actual fun ttError(): Color = ttFixedColours.error(ttIsDarkTheme())

@Composable
actual fun ttLabel(): Color = ttFixedColours.label(ttIsDarkTheme())

@Composable
actual fun ttLabelSecondary(): Color = ttFixedColours.labelSecondary(ttIsDarkTheme())

@Composable
actual fun ttBodyLabel(): Color = ttFixedColours.bodyLabel(ttIsDarkTheme())

@Composable
actual fun ttSeparator(): Color = ttFixedColours.separator(ttIsDarkTheme())

@Composable
actual fun SystemBarAppearance(isDark: Boolean) {}
