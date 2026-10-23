package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator

@Composable
actual fun HapticFeedback(): HapticFeedbackController {
    // Use default initializer (light style) to avoid enum namespace collision
    val generator = remember { UIImpactFeedbackGenerator() }
    generator.prepare()
    return IosHapticFeedback(generator)
}

private class IosHapticFeedback(private val generator: UIImpactFeedbackGenerator) : HapticFeedbackController {
    override fun tick() {
        generator.impactOccurred()
        generator.prepare()
    }
}