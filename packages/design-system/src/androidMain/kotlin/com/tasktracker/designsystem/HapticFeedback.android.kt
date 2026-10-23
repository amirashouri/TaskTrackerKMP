package com.tasktracker.designsystem

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

@Composable
actual fun HapticFeedback(): HapticFeedbackController {
    val view = LocalView.current
    return AndroidHapticFeedback(view)
}

private class AndroidHapticFeedback(private val view: View) : HapticFeedbackController {
    override fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}