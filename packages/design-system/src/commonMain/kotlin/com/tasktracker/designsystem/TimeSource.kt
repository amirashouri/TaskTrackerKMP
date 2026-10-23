package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

/**
 * Platform-specific time source for high-resolution timing.
 *
 * Used by [ttLensPlacement] to compute velocity from frame-to-frame time deltas.
 * Each platform provides its own nanosecond-precision clock.
 */
@Composable
expect fun currentNanoTime(): Long