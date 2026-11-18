package com.tasktracker.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The app mark's checkmark on its own, filling the box it is given.
 *
 * ## Why this exists rather than an icon in [ttActionIcons]
 *
 * The mark's check is not a grid glyph: it is two round-capped segments drawn as a stroke, and
 * its proportions come from the launcher icon's canvas fractions ([AppMarkDefaults]). A badge
 * that wants the *app's* check therefore has two honest options — share this drawing, or redraw
 * the same shape as a filled vector, which is a second drawing that can drift in cap shape, join
 * and weight without anything failing. So it is shared, through the same `drawAppMarkCheck` the
 * mark itself calls.
 *
 * ## Why it is scaled to its box
 *
 * On the mark the check occupies a fraction of the 68dp tile, which is right there and wrong
 * anywhere else: at a badge's 18dp it would be a 5dp mark floating in a disc. So this maps the
 * check's **own** stroked bounds — both arms plus the round caps — onto the modifier's size,
 * centred, keeping the mark's proportions and its stroke-to-width ratio exactly. The only
 * thing that changes is the scale.
 *
 * [tint] defaults to [ttOnAccent] because the fills this is drawn on are the accent: the mark's
 * tile, and the complete badge's disc. Callers on another fill should pass their own ink.
 */
@Composable
fun AppMarkCheck(
    modifier: Modifier = Modifier,
    tint: Color = ttOnAccent,
) {
    Canvas(modifier = modifier) {
        val halfStroke = AppMarkDefaults.CHECK_STROKE / 2f
        val left = AppMarkDefaults.SHORT_ARM_X - halfStroke
        val right = AppMarkDefaults.TIP_X + halfStroke
        val top = AppMarkDefaults.TIP_Y - halfStroke
        val bottom = AppMarkDefaults.VERTEX_Y + halfStroke
        val width = right - left
        val height = bottom - top

        // One scale for both axes, the smaller of the two: a box that is not square letterboxes
        // the check rather than stretching it, which is the same choice the mark makes by being
        // drawn on a square tile.
        val scale = minOf(size.width / width, size.height / height)
        drawAppMarkCheck(
            tint = tint,
            origin = Offset(
                // Centre the check's bounds in the box, then shift so that a fraction of
                // `left`/`top` lands on the box's edge rather than at the origin.
                x = (size.width - width * scale) / 2f - left * scale,
                y = (size.height - height * scale) / 2f - top * scale,
            ),
            scale = scale,
        )
    }
}
