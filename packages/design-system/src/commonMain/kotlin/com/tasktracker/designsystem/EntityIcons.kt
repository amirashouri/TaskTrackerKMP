package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * The three entity glyphs the app labels its containers with: a task, a project, an epic.
 *
 * ## Why these exist rather than reusing the tab set
 *
 * A tab glyph names a *screen*, an entity glyph names *what a row is*. The project row and an
 * epic card sit in the same list and must read as different kinds of thing at a glance, so the
 * epic's glyph is deliberately built from stacked layers — several projects under one story —
 * while the project's is a folder and the task's is a checked card.
 *
 * ## The grid
 *
 * The same 24-unit viewport and 3-unit margin as [ttTabIcons] and [ttTagIcons], and one
 * [PathFillType.EvenOdd] path, so the rules are the same ones those files state: overlapping
 * contours cancel, which is why every part of a glyph abuts its neighbour rather than covering
 * it.
 */
object ttEntityIcons {

    /** A card with a check inside it: one unit of work. */
    val task: ImageVector = entityIcon("EntityTask") {
        entityOutlinedRoundRect(3f, 3.5f, 21f, 20.5f, 3f, 1.8f)
        // The tick is two abutting bars, drawn as one contour so the bend cannot open.
        moveTo(7.4f, 12.2f)
        lineTo(10.4f, 15.2f)
        lineTo(17f, 8.6f)
        lineTo(15.6f, 7.2f)
        lineTo(10.4f, 12.4f)
        lineTo(8.8f, 10.8f)
        close()
    }

    /** A folder: the container a project is. */
    val project: ImageVector = entityIcon("EntityProject") {
        entityFolder()
    }

    /**
     * Stacked layers: several projects held by one story.
     *
     * The top diamond is filled; the two below it are bands, so the glyph reads as a pile
     * rather than as three unrelated lozenges.
     */
    val epic: ImageVector = entityIcon("EntityEpic") {
        moveTo(12f, 3f)
        lineTo(21f, 8f)
        lineTo(12f, 13f)
        lineTo(3f, 8f)
        close()
        moveTo(4.2f, 11.4f)
        lineTo(12f, 15.8f)
        lineTo(19.8f, 11.4f)
        lineTo(19.8f, 13.6f)
        lineTo(12f, 18f)
        lineTo(4.2f, 13.6f)
        close()
        moveTo(4.2f, 15.4f)
        lineTo(12f, 19.8f)
        lineTo(19.8f, 15.4f)
        lineTo(19.8f, 17.6f)
        lineTo(12f, 22f)
        lineTo(4.2f, 17.6f)
        close()
    }
}

/** Builds one 24-unit glyph from a single filled path. See [ttTagIcons]' own builder. */
private fun entityIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector {
    val nodes = PathBuilder().apply(draw).nodes
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = ENTITY_GRID,
        viewportHeight = ENTITY_GRID,
    ).addPath(
        pathData = nodes,
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(Color.Black),
    ).build()
}

private const val ENTITY_GRID = 24f

/**
 * A folder outline: one contour with the inside cut out, plus a tab that meets the body
 * exactly rather than overlapping it.
 */
private fun PathBuilder.entityFolder() {
    // Outer body and tab, walked as one shape.
    moveTo(3f, 6.2f)
    lineTo(3f, 19.4f)
    quadTo(3f, 21f, 4.6f, 21f)
    lineTo(19.4f, 21f)
    quadTo(21f, 21f, 21f, 19.4f)
    lineTo(21f, 9.4f)
    quadTo(21f, 7.8f, 19.4f, 7.8f)
    lineTo(10.6f, 7.8f)
    lineTo(9f, 5.4f)
    quadTo(8.6f, 4.8f, 7.9f, 4.8f)
    lineTo(4.6f, 4.8f)
    quadTo(3f, 4.8f, 3f, 6.2f)
    close()
    // The cut-out, inset by the stroke and stopping at the folder's mouth.
    moveTo(4.8f, 9.6f)
    lineTo(19.2f, 9.6f)
    lineTo(19.2f, 19.2f)
    lineTo(4.8f, 19.2f)
    close()
}

/** A rounded rectangle drawn as an outline [stroke] thick, on the entity grid. */
private fun PathBuilder.entityOutlinedRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    stroke: Float,
) {
    entityRoundRect(left, top, right, bottom, radius)
    entityRoundRect(
        left = left + stroke,
        top = top + stroke,
        right = right - stroke,
        bottom = bottom - stroke,
        radius = (radius - stroke).coerceAtLeast(0f),
    )
}

/** A filled rounded rectangle, on the entity grid. */
private fun PathBuilder.entityRoundRect(
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
