package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

/**
 * Platform haptic feedback for discrete slider milestones.
 *
 * Called when the user crosses a milestone (25%, 50%, 75%) during a slider drag.
 * The actual implementation is platform-specific:
 * - Android: `HapticFeedbackConstants.CLOCK_TICK`
 * - iOS: `UIImpactFeedbackGenerator(style: .light)`
 * - JVM (Desktop/test): no-op
 */
@Composable
expect fun HapticFeedback(): HapticFeedbackController

interface HapticFeedbackController {
    /** Fire a light haptic tick. */
    fun tick()
}