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
 * An indeterminate progress indicator.
 *
 * ## Why this is here and not in the one button that used it
 *
 * It was `PrimaryButton`'s private `Spinner`, and it was the only thing in that file the
 * *other* modules could not express. A control that can be busy for two seconds and cannot
 * say so is a control that invites a second tap — so the moment a second button wanted this,
 * the right answer was not a second spinner. `ttGlassButton(loading = true)` and a direct
 * `ttSpinner()` both take it from here.
 *
 * Drawn rather than imported, for the same reason the banner's glyph is: the design system
 * ships no icon set, deliberately, so no feature can grow a private picture vocabulary.
 *
 * ## Why an arc and not a ring
 *
 * A complete circle reads as a static image next to a label that has just vanished. The sweep
 * leaves a gap that travels, so the mark is legibly *moving* even in a still screenshot.
 *
 * ## Why `rememberInfiniteTransition` and not `animateFloatAsState`
 *
 * `animateFloatAsState` runs its animation once on the way to `targetValue` and then stops.
 * A spinner built on it would rotate exactly one turn and sit still — which is the one thing a
 * loading indicator must never do, and it fails *quietly*: the indicator is present, it is the
 * right size, and every screenshot of it looks correct.
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

/**
 * The spinner's numbers, named rather than inlined.
 *
 * Separate because these are the values that make the mark read as *busy* rather than as
 * *decorative*, and they are the first thing anyone would tune without knowing they are
 * load-bearing: a longer period reads as lag, a shorter one as a flicker.
 */
object ttSpinnerDefaults {

    /** One turn per 900ms. Slower reads as lag; faster reads as a flicker. */
    const val periodMillis: Int = 900

    /**
     * 280° rather than a full circle.
     *
     * A complete circle has no moving edge, so a still frame of one is indistinguishable
     * from a static ornament. The missing quadrant is what makes the rotation visible.
     */
    const val sweepDegrees: Float = 280f

    /** The arc's weight. A ring this thin disappears against a light fill; much thicker reads as a disc. */
    val strokeWidth: Dp = 2.dp
}