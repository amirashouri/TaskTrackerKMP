package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

@Composable
actual fun HapticFeedback(): HapticFeedbackController = JvmHapticFeedback

private object JvmHapticFeedback : HapticFeedbackController {
    override fun tick() {
        // No-op on JVM (Desktop/test)
    }
}