package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent

/**
 * The tick a row or card draws at its trailing edge while multi-select is on.
 *
 * Drawn rather than built from a text glyph because it has to show three things at 20dp: the box
 * while unticked, the accent fill while ticked, and a tick that stays crisp at any density. An
 * empty rounded box is the same shape the outline glyph uses, so the pair reads as one control
 * whether or not it is ticked.
 *
 * Shared by the backlog row and the board card: both surfaces are one checkbox in this mode, and
 * two drawings of it would drift the moment either's size or accent changed.
 */
@Composable
fun SelectionTickBox(isSelected: Boolean, modifier: Modifier = Modifier) {
    val accent = ttAccent()
    val outline = ttLabelSecondary()
    Canvas(modifier = modifier.size(SELECTION_TICK_SIZE)) {
        val corner = CornerRadius(SELECTION_TICK_CORNER.toPx())
        if (isSelected) {
            drawRoundRect(color = accent, cornerRadius = corner)
            val tick = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(
                path = tick,
                color = ttOnAccent,
                style = Stroke(
                    width = SELECTION_TICK_STROKE.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawRoundRect(
                color = outline,
                cornerRadius = corner,
                style = Stroke(width = SELECTION_TICK_BORDER.toPx()),
                size = Size(size.width, size.height),
            )
        }
    }
}

private val SELECTION_TICK_SIZE = 20.dp
private val SELECTION_TICK_CORNER = 5.dp
private val SELECTION_TICK_STROKE = 2.dp
private val SELECTION_TICK_BORDER = 1.5.dp
