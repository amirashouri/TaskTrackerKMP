package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * The glyphs an administrator can give a project tag.
 *
 * ## Why these are hand-drawn, like the tab and action sets
 *
 * Same rejection as [ttTabIcons]: no icon dependency, no icon font, no traced SF Symbols.
 * These are eight plain geometric drawings, chosen to stay legible at the 12dp a chip draws
 * them at — a bug built out of a body, a head and six notched legs rather than a detailed
 * pictogram, because at 12dp the detail is the first thing to turn into a smudge.
 *
 * ## The grid and the fill rule
 *
 * The same 24-unit viewport and 3-unit margin as every other glyph, and the same
 * [PathFillType.EvenOdd] path for every icon — see [ttTabIcons] for why the rule is stated
 * once for the file. One consequence is load-bearing for the drawings here: **two contours
 * that overlap cancel**, so every part of a glyph either abuts its neighbour or leaves a
 * visible gap. The bell's clapper is deliberately separate from the bell, and the bug's legs
 * stop short of its body, for exactly that reason.
 */
object ttTagIcons {

    /** An insect: a body, a head, antennae and six legs. */
    val bug: ImageVector = tagIcon("TagBug") {
        tagCircle(12f, 14.5f, 5.2f)
        tagCircle(12f, 6f, 2.8f)
        // Antennae, above the head with a hair of air so they do not become a hole.
        tagRoundRect(8.5f, 1.2f, 9.6f, 3.0f, 0.55f)
        tagRoundRect(14.4f, 1.2f, 15.5f, 3.0f, 0.55f)
        // Three legs a side, each just clear of the body's edge at that height.
        tagRoundRect(4.5f, 10.4f, 7.6f, 11.5f, 0.55f)
        tagRoundRect(16.4f, 10.4f, 19.5f, 11.5f, 0.55f)
        tagRoundRect(4.5f, 13.9f, 6.4f, 15.0f, 0.55f)
        tagRoundRect(17.6f, 13.9f, 19.5f, 15.0f, 0.55f)
        tagRoundRect(4.5f, 17.6f, 7.6f, 18.7f, 0.55f)
        tagRoundRect(16.4f, 17.6f, 19.5f, 18.7f, 0.55f)
    }

    /** A phone: an outlined body with a speaker slot and a home dot inside it. */
    val mobile: ImageVector = tagIcon("TagMobile") {
        tagOutlinedRoundRect(6.5f, 2.2f, 17.5f, 21.8f, 2.4f, 1.6f)
        tagRoundRect(9.8f, 4.8f, 14.2f, 5.7f, 0.45f)
        tagCircle(12f, 18.5f, 0.9f)
    }

    /** A four-point sparkle with a smaller one beside it. */
    val sparkle: ImageVector = tagIcon("TagSparkle") {
        moveTo(12f, 2f)
        quadTo(12.9f, 11.1f, 22f, 12f)
        quadTo(12.9f, 12.9f, 12f, 22f)
        quadTo(11.1f, 12.9f, 2f, 12f)
        quadTo(11.1f, 11.1f, 12f, 2f)
        close()
        tagCircle(18.8f, 5.2f, 1.4f)
    }

    /** A lightning bolt. */
    val bolt: ImageVector = tagIcon("TagBolt") {
        moveTo(13.2f, 1.6f)
        lineTo(4.6f, 14.2f)
        lineTo(10.6f, 14.2f)
        lineTo(9.6f, 22.4f)
        lineTo(19.4f, 9.2f)
        lineTo(13.2f, 9.2f)
        close()
    }

    /** A five-point star. */
    val star: ImageVector = tagIcon("TagStar") {
        moveTo(12f, 2f)
        lineTo(14.2f, 9.3f)
        lineTo(21.5f, 9.3f)
        lineTo(15.6f, 13.6f)
        lineTo(18.3f, 21f)
        lineTo(12f, 16.7f)
        lineTo(5.7f, 21f)
        lineTo(8.4f, 13.6f)
        lineTo(2.5f, 9.3f)
        lineTo(9.8f, 9.3f)
        close()
    }

    /** A pennant on a pole. */
    val flag: ImageVector = tagIcon("TagFlag") {
        tagRoundRect(3.8f, 2f, 5.2f, 22f, 0.7f)
        // The flag's left edge meets the pole's right edge exactly; an overlap would be
        // cut out by the even-odd rule.
        moveTo(5.2f, 2.6f)
        lineTo(20.2f, 2.6f)
        lineTo(17.2f, 7.8f)
        lineTo(20.2f, 13f)
        lineTo(5.2f, 13f)
        close()
    }

    /** A closed book: an outlined cover, a spine and three ruled lines. */
    val book: ImageVector = tagIcon("TagBook") {
        tagOutlinedRoundRect(3.6f, 2.6f, 20.4f, 21.4f, 1.8f, 1.6f)
        tagRoundRect(5.2f, 4.2f, 6.4f, 19.8f, 0.2f)
        tagRoundRect(8.2f, 7.2f, 16.8f, 8.2f, 0.5f)
        tagRoundRect(8.2f, 11f, 16.8f, 12f, 0.5f)
        tagRoundRect(8.2f, 14.8f, 16.8f, 15.8f, 0.5f)
    }

    /** A bell with a clapper. */
    val bell: ImageVector = tagIcon("TagBell") {
        moveTo(5.2f, 17.2f)
        lineTo(6.4f, 15.2f)
        lineTo(6.4f, 11f)
        quadTo(6.4f, 5.2f, 12f, 5.2f)
        quadTo(17.6f, 5.2f, 17.6f, 11f)
        lineTo(17.6f, 15.2f)
        lineTo(18.8f, 17.2f)
        close()
        tagCircle(12f, 19.8f, 1.7f)
        tagCircle(12f, 3.6f, 1.3f)
    }

    /** Every glyph, in the order the picker offers them. */
    val all: List<ImageVector> = listOf(bug, mobile, sparkle, bolt, star, flag, book, bell)
}

/** Builds one 24-unit glyph from a single filled path. See [ttTabIcons]' own builder. */
private fun tagIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector {
    val nodes = PathBuilder().apply(draw).nodes
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = TAG_GRID,
        viewportHeight = TAG_GRID,
    ).addPath(
        pathData = nodes,
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black),
    ).build()
}

private const val TAG_GRID = 24f

/** A filled rounded rectangle. */
private fun PathBuilder.tagRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    fun corner(x: Float, y: Float) =
        arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = x, y1 = y)
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    corner(right, top + radius)
    lineTo(right, bottom - radius)
    corner(right - radius, bottom)
    lineTo(left + radius, bottom)
    corner(left, bottom - radius)
    lineTo(left, top + radius)
    corner(left + radius, top)
    close()
}

/** A rounded rectangle drawn as an outline [stroke] thick: an outer shape with its inside cut. */
private fun PathBuilder.tagOutlinedRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    stroke: Float,
) {
    tagRoundRect(left, top, right, bottom, radius)
    tagRoundRect(
        left = left + stroke,
        top = top + stroke,
        right = right - stroke,
        bottom = bottom - stroke,
        radius = (radius - stroke).coerceAtLeast(0f),
    )
}

/** A filled circle, walked clockwise from its left edge. */
private fun PathBuilder.tagCircle(centerX: Float, centerY: Float, radius: Float) {
    moveTo(centerX - radius, centerY)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = centerX, y1 = centerY + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = centerX + radius, y1 = centerY)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = centerX, y1 = centerY - radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = centerX - radius, y1 = centerY)
    close()
}
