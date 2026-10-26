package com.tasktracker.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Indeterminate progress spinner.
 */
@Composable
fun ttSpinner(
    modifier: Modifier = Modifier,
    color: Color = ttOnAccent,
    size: Dp = ttSpacing.xxl,
    strokeWidth: Dp = ttSpinnerDefaults.strokeWidth,
) {
    val rotation by rememberInfiniteTransition(label = "ttSpinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ttSpinnerDefaults.periodMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val diameter = this.size.minDimension - stroke
        val topLeft = Offset(
            x = (this.size.width - diameter) / 2f,
            y = (this.size.height - diameter) / 2f,
        )
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = ttSpinnerDefaults.sweepDegrees,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

object ttSpinnerDefaults {
    const val periodMillis: Int = 900
    const val sweepDegrees: Float = 280f
    val strokeWidth: Dp = 2.dp
}
