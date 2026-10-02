package com.tasktracker.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tab glyphs are checked for being *drawn*, not for looking right.
 *
 * ## Why this test exists
 *
 * A glyph built by hand fails in a way a glyph from a font cannot: it compiles, it has a name,
 * it has a size, and it can render **nothing at all**. A `moveTo` with no `lineTo` is a valid
 * path. So is a `lineTo` to a point the viewport clips away. Both are invisible in review and
 * invisible on screen, which is the worst combination — the bar still lays out, the labels still
 * read, every other test still passes, and the icons are simply not there.
 *
 * That is not hypothetical: these glyphs are hand-drawn rather than imported precisely because
 * [ttTabIcons] rejects the dependencies that would have drawn them, and a hand-drawn glyph is
 * exactly the kind that comes out empty.
 *
 * So every glyph is walked node by node and asserted to have geometry, to stay inside the
 * viewport, and to keep a comparable extent. An empty path, a degenerate contour, and a glyph
 * that escapes the grid all fail here.
 *
 * ## What this deliberately does not assert
 *
 * It does not compare against reference images and it does not assert the *shape*. Rasterising
 * these to compare would make the test unreadable and would fail on an anti-aliasing change
 * rather than on a fault. Whether a drawing is recognisably the right icon is a design question;
 * the question that *can* be answered mechanically — does every tab have a glyph, and does each
 * one stand in for the symbol the native app names — is answered by the `when` in
 * `tabIcon`, which is exhaustive over `AppTab` and therefore a compile error rather than a test.
 */
class TabIconsGeometryTest {

    @Test
    fun `every glyph has path nodes`() {
        for (icon in ttTabIcons.all + ttActionIcons.all) {
            assertTrue(
                icon.nodes().isNotEmpty(),
                "${icon.name} has no path nodes, so it draws nothing at all",
            )
        }
    }

    @Test
    fun `every glyph is drawn inside the viewport`() {
        for (icon in ttTabIcons.all + ttActionIcons.all) {
            for (point in icon.points()) {
                assertTrue(
                    point.x in -SLACK..(VIEWPORT + SLACK) && point.y in -SLACK..(VIEWPORT + SLACK),
                    "${icon.name} draws a point at $point, outside the $VIEWPORT-unit viewport " +
                        "the class docs promise (slack $SLACK)",
                )
            }
        }
    }

    @Test
    fun `every glyph fills the grid so the four read as one set`() {
        // A glyph that covers a fraction of the others' area renders as a smaller one, which is
        // the failure that survives review: nothing is wrong with any of the four drawings, they
        // are just not the same size.
        //
        // The bound is loose on purpose. The gear is *legitimately* the largest — a cog fills
        // the cap height, a tray is wide and squat — so the test asserts the spread is bounded
        // rather than that the four are equal. Tightening it to equality would mean shrinking
        // the gear for no reason.
        val extents = ttTabIcons.all.map { icon ->
            val points = icon.points()
            val width = points.maxOf { it.x } - points.minOf { it.x }
            val height = points.maxOf { it.y } - points.minOf { it.y }
            width * height
        }
        assertTrue(
            extents.all { it > 0f },
            "a glyph has no measurable extent, which means it drew nothing: $extents",
        )
        assertTrue(
            extents.min() / extents.max() > 0.6f,
            "glyph extents differ too much to read as one set: $extents",
        )
    }

    @Test
    fun `the gear's hole is a hole`() {
        // The one glyph where a fill-rule mistake is possible rather than merely ugly, and the
        // mistake is silent: an even-odd fill quietly behaving as non-zero draws a solid disc
        // where the hole should be, and a settings icon with a solid centre is still
        // recognisable — so nothing else would catch it.
        //
        // A hole is exactly "a closed contour wholly inside another closed contour", so that is
        // what is asserted. It distinguishes a hole from a second, smaller gear, which a count
        // alone would not.
        val contours = ttTabIcons.settings.contours()
        assertEquals(2, contours.size, "the gear is an outer contour and a hole")
        val outer = contours.maxByOrNull { it.bounds().width + it.bounds().height }!!
        val hole = contours.first { it !== outer }
        assertTrue(
            hole.bounds().isInside(outer.bounds()),
            "the gear's hole ${hole.bounds()} is not wholly inside its outline ${outer.bounds()}",
        )
    }

    @Test
    fun `the tray's bars are inside its frame`() {
        // The other glyph making an "inside" claim. Two bars drawn in an outline that overshoots
        // them read as a tray with something spilling out of it, which is a different icon.
        //
        // Four contours, not three: the frame is an outline, so it is an outer edge and an inner
        // edge, and the inner edge is a contour too.
        val contours = ttTabIcons.backlog.contours()
        assertEquals(4, contours.size, "the tray is an outlined frame plus two bars")
        val frame = contours.maxByOrNull { it.bounds().width + it.bounds().height }!!
        for (inside in contours.filter { it !== frame }) {
            assertTrue(
                inside.bounds().isInside(frame.bounds()),
                "${inside.bounds()} is not inside the frame ${frame.bounds()}",
            )
        }
    }

    @Test
    fun `every native symbol has a glyph standing in for it`() {
        // The table is the record that these are deliberate stand-ins for specific SF Symbols
        // rather than four drawings somebody liked. A glyph added without a row here fails
        // this; a row whose name is not a symbol the native app uses fails nothing, which is why
        // the names are asserted as literals rather than merely as a count.
        assertEquals(
            listOf("square.grid.2x2", "rectangle.split.3x1", "tray", "gearshape"),
            ttTabIcons.mapping.keys.toList(),
        )
        assertEquals(
            ttTabIcons.all.toSet(),
            ttTabIcons.mapping.values.toSet(),
            "every glyph should appear in the mapping exactly once",
        )
    }

    @Test
    fun `the icon size scale is a single step`() {
        // One value today. Asserted anyway, because a second icon size added without a name is
        // exactly the sort of thing that then gets used inconsistently across the bar.
        //
        // Compared on `value` rather than with `>` against `Dp.Unspecified`: `Unspecified` is
        // NaN-based, so *every* comparison against it is false and an assertion written that
        // way fails no matter what the size is. The two real properties are that it is a
        // positive size, and that it is smaller than the grid the glyphs are drawn on — a
        // glyph rendered at 24dp in a 49pt bar is the mistake the token exists to prevent.
        assertEquals(1, ttIconSize.all.size)
        assertEquals(ttIconSize.tab, ttIconSize.all.single())
        assertTrue(ttIconSize.tab.value > 0f, "the tab icon must have a positive size")
        assertTrue(
            ttIconSize.tab.value < 24f,
            "the tab icon should be drawn smaller than its 24-unit grid, was ${ttIconSize.tab}",
        )
    }

    // MARK: - Walking the vector

    private fun ImageVector.paths(): List<VectorPath> {
        fun collect(node: VectorNode): List<VectorPath> = when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> node.flatMap(::collect)
        }
        return collect(root)
    }

    private fun ImageVector.nodes(): List<PathNode> = paths().flatMap { it.pathData }

    /**
     * Every point the glyph refers to, with relative and axis-aligned nodes resolved.
     *
     * Bezier control points are included along with on-curve endpoints, deliberately: a control
     * point outside the grid is how a curve leaves the box while every endpoint stays inside it,
     * so a bounds check that ignored them would pass on a glyph that visibly overflows.
     */
    private fun ImageVector.points(): List<Offset> {
        val points = mutableListOf<Offset>()
        var current = Offset.Zero
        for (node in nodes()) {
            when (node) {
                is PathNode.MoveTo -> current = Offset(node.x, node.y).also { points.add(it) }
                is PathNode.LineTo -> current = Offset(node.x, node.y).also { points.add(it) }
                is PathNode.HorizontalTo -> current = Offset(node.x, current.y).also { points.add(it) }
                is PathNode.VerticalTo -> current = Offset(current.x, node.y).also { points.add(it) }
                is PathNode.QuadTo -> {
                    points.add(Offset(node.x1, node.y1))
                    current = Offset(node.x2, node.y2).also { points.add(it) }
                }
                is PathNode.ReflectiveQuadTo -> current = Offset(node.x, node.y).also { points.add(it) }
                is PathNode.CurveTo -> {
                    points.add(Offset(node.x1, node.y1))
                    points.add(Offset(node.x2, node.y2))
                    current = Offset(node.x3, node.y3).also { points.add(it) }
                }
                // `ReflectiveCurveTo` carries no first control point: it infers one by
                // reflecting the previous curve's, so there is nothing to bounds-check.
                is PathNode.ReflectiveCurveTo -> {
                    points.add(Offset(node.x1, node.y1))
                    current = Offset(node.x2, node.y2).also { points.add(it) }
                }
                is PathNode.ArcTo -> current = Offset(node.arcStartX, node.arcStartY)
                    .also { points.add(it) }
                is PathNode.Close -> Unit
                else -> Unit
            }
        }
        return points
    }

    /**
     * The glyph's closed contours, each as its own point list.
     *
     * Split on [PathNode.Close] because a closed contour is what a hole *is*. Asserting on the
     * flattened point list instead could not tell a hole from a shape that merely happens to be
     * small, which is the whole distinction the gear test turns on.
     */
    private fun ImageVector.contours(): List<List<Offset>> {
        val contours = mutableListOf<MutableList<Offset>>()
        var current = mutableListOf<Offset>()
        var cursor = Offset.Zero
        for (node in nodes()) {
            val add: (Offset) -> Unit = { current.add(it) }
            when (node) {
                is PathNode.MoveTo -> {
                    if (current.isNotEmpty()) contours.add(current)
                    current = mutableListOf()
                    add(Offset(node.x, node.y)); cursor = Offset(node.x, node.y)
                }
                is PathNode.LineTo -> add(Offset(node.x, node.y)).also { cursor = Offset(node.x, node.y) }
                is PathNode.HorizontalTo -> add(Offset(node.x, cursor.y)).also { cursor = Offset(node.x, cursor.y) }
                is PathNode.VerticalTo -> add(Offset(cursor.x, node.y)).also { cursor = Offset(cursor.x, node.y) }
                is PathNode.QuadTo -> {
                    add(Offset(node.x1, node.y1))
                    add(Offset(node.x2, node.y2)).also { cursor = Offset(node.x2, node.y2) }
                }
                is PathNode.ReflectiveQuadTo -> add(Offset(node.x, node.y)).also { cursor = Offset(node.x, node.y) }
                is PathNode.CurveTo -> {
                    add(Offset(node.x1, node.y1))
                    add(Offset(node.x2, node.y2))
                    add(Offset(node.x3, node.y3)).also { cursor = Offset(node.x3, node.y3) }
                }
                is PathNode.ReflectiveCurveTo -> {
                    add(Offset(node.x1, node.y1))
                    add(Offset(node.x2, node.y2)).also { cursor = Offset(node.x2, node.y2) }
                }
                is PathNode.ArcTo -> add(Offset(node.arcStartX, node.arcStartY))
                    .also { cursor = Offset(node.arcStartX, node.arcStartY) }
                is PathNode.Close -> if (current.isNotEmpty()) {
                    contours.add(current)
                    current = mutableListOf()
                }
                else -> Unit
            }
        }
        if (current.isNotEmpty()) contours.add(current)
        return contours
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        /** Strictly inside on every side — touching the edge is not inside, for a hole. */
        fun isInside(other: Bounds) = left > other.left && right < other.right &&
            top > other.top && bottom < other.bottom
    }

    private fun List<Offset>.bounds() = Bounds(
        left = minOf { it.x },
        top = minOf { it.y },
        right = maxOf { it.x },
        bottom = maxOf { it.y },
    )

    private companion object {
        /** The drawing grid, as `ttTabIcons` states it. */
        const val VIEWPORT = 24f

        /**
         * How far a point may stray outside the viewport before it is clipped rather than merely
         * untidy. Bezier control points legitimately sit outside the curves they shape.
         */
        const val SLACK = 3f
    }
}
