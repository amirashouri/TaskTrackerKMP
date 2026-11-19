package com.tasktracker.sharedui

import androidx.compose.runtime.Composable

/**
 * The Desktop host has no Reduce Transparency preference this shell can read.
 *
 * macOS has an Accessibility "Reduce transparency" switch, but the JVM exposes no API for it,
 * and the shell cannot invent a setting the operating system keeps to itself. The seam is
 * here and unused; the JVM host is also where the shell's test suite runs, so scenarios see
 * `false` unless one injects a preference.
 */
@Composable
actual fun platformReduceTransparency(): Boolean = false
