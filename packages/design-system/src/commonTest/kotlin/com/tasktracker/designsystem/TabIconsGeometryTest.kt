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
 * It does not compare against reference images and it does not assert the *shape* of a glyph.
 * Rasterising these to compare would make the test unreadable and would fail on an
 * anti-aliasing change rather than on a fault. Whether a drawing is recognisably the right
 * icon is a design question; the questions that *can* be answered mechanically — does every
 * tab have a glyph, does each one stand in for the symbol the native app names, and does each
 * chevron point the way its name says — are answered here and by the `when` in `tabIcon`,
 * which is exhaustive over `AppTab` and therefore a compile error rather than a test.
 */
class TabIconsGeometryTest {

    @Test
    fun `every glyph has path nodes`() {
        for (icon in ttTabIcons.all + ttActionIcons.all + ttTagIcons.all) {
            assertTrue(
                icon.nodes().isNotEmpty(),
                "${icon.name} has no path nodes, so it draws nothing at all",
            )
        }
    }

    @Test
    fun `every glyph is drawn inside the viewport`() {
        for (icon in ttTabIcons.all + ttActionIcons.all + ttTagIcons.all) {
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
    fun `every glyph fills the grid so the five read as one set`() {
        // A glyph that covers a fraction of the others' area renders as a smaller one, which is
        // the failure that survives review: nothing is wrong with any of the drawings, they
        // are just not the same size.
        //
        // The bound is loose on purpose. The gear is *legitimately* the largest — a cog fills
        // the cap height, a tray is wide and squat — so the test asserts the spread is bounded
        // rather than that they are equal. Tightening it to equality would mean shrinking
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
        // rather than drawings somebody liked. A glyph added without a row here fails this; a
        // row whose name is not a symbol the native app uses fails nothing, which is why the
        // names are asserted as literals rather than merely as a count.
        //
        // [ttTabIcons.NO_COUNTERPART] is expected rather than tolerated: Sprints is new in
        // this port, so it stands in for nothing, and the table saying so is the difference
        // between "nothing was decided" and "somebody forgot".
        assertEquals(
            listOf(
                "square.grid.2x2",
                "rectangle.split.3x1",
                "tray",
                ttTabIcons.NO_COUNTERPART,
                "gearshape",
            ),
            ttTabIcons.mapping.keys.toList(),
        )
        assertEquals(
            ttTabIcons.all.toSet(),
            ttTabIcons.mapping.values.toSet(),
            "every glyph should appear in the mapping exactly once",
        )
    }

    @Test
    fun `the sprints bars rise and share a baseline`() {
        // The glyph's whole reading. Three bars of equal height read as one measure sampled
        // three times; a bar whose tops do not share a bottom reads as three unrelated marks.
        // So the two properties that make it say "successive iterations" are asserted
        // directly, from the geometry rather than from a screenshot.
        val contours = ttTabIcons.sprints.contours()
        assertEquals(3, contours.size, "three bars, and each is one closed contour")

        val bounds = contours.map { it.bounds() }
        val bottoms = bounds.map { it.bottom }.distinct()
        assertEquals(
            1,
            bottoms.size,
            "the bars must share a baseline to read as a chart, got bottoms $bottoms",
        )
        val heights = bounds.map { it.bottom - it.top }
        assertEquals(
            heights.sorted(),
            heights,
            "the bars must rise left to right, got heights $heights",
        )
        assertTrue(
            heights.zipWithNext().all { (a, b) -> b > a },
            "each bar must be strictly taller than the one before it, got $heights — evenly " +
                "spaced steps read as one measure sampled three times rather than as three " +
                "iterations",
        )
    }

    @Test
    fun `the sprints glyph is not a frame with contents in it`() {
        // The first drawing was a rounded frame plus a progress band, on the theory that a
        // sprint is a time box. It was rejected because [ttTabIcons.backlog] is *also* a
        // frame with contents in it, and two such glyphs in the same bar are one family.
        //
        // This is the assertion that keeps the rejection: a solid disc or frame would have
        // contours nested inside one another, and a bar chart has three that are not.
        val bounds = ttTabIcons.sprints.contours().map { it.bounds() }
        val pairs = bounds.indices.flatMap { i ->
            (i + 1 until bounds.size).map { j -> bounds[i] to bounds[j] }
        }
        for ((a, b) in pairs) {
            assertTrue(
                !a.isInside(b) && !b.isInside(a),
                "the sprints glyph has a contour ${a} nested inside ${b}, so it is a frame " +
                    "with contents in it — which is the Backlog glyph's shape, and two of " +
                    "those in one bar are one family",
            )
        }
    }

    @Test
    fun `every icon size is a named positive step below the glyph grid`() {
        // The ramp is [close, tab]: an inline dismiss glyph inside a pill, and a control's
        // whole glyph. Asserted as an exact ordered set because a size added without being
        // named here is exactly the sort of thing that then gets used inconsistently — the
        // property is not "there are sizes", it is "every size was decided on purpose".
        //
        // Compared on `value` rather than with `>` against `Dp.Unspecified`: `Unspecified` is
        // NaN-based, so *every* comparison against it is false and an assertion written that
        // way fails no matter what the size is. The two real properties are that each size is
        // positive, and that each is smaller than the grid the glyphs are drawn on — a glyph
        // rendered at 24dp in a 49pt bar is the mistake the token exists to prevent.
        assertEquals(
            listOf(ttIconSize.close, ttIconSize.tab),
            ttIconSize.all,
            "the icon size ramp is [close, tab] — if a size was added, name it in this list " +
                "on purpose rather than growing the ramp silently",
        )
        for (size in ttIconSize.all) {
            assertTrue(size.value > 0f, "icon sizes must be positive, was $size")
            assertTrue(
                size.value < 24f,
                "an icon should be drawn smaller than its 24-unit grid, was $size",
            )
        }
        assertTrue(
            ttIconSize.all.zipWithNext().all { (a, b) -> b.value > a.value },
            "the size ramp must strictly increase, was ${ttIconSize.all} — evenly or " +
                "reversed steps read as one measure sampled twice rather than as two roles",
        )
    }

    // MARK: - Which way a chevron points

    /**
     * The regression: the month grid's two arrows were on each other's bodies.
     *
     * Both contours were correct rotations of `chevronDown` — the left one was exactly
     * `chevronDown` turned a quarter turn clockwise, apex at x = 17 — but they sat on each
     * other's names, so the arrow on the left of "March 2026" stepped *forward* and the one
     * on the right stepped back.
     *
     * Nothing else in this file could have caught it. Both paths are non-empty, inside the
     * viewport, and 18x18 units, so "every glyph has geometry", "every glyph is drawn inside
     * the viewport" and "every glyph fills the grid" all passed on a backwards arrow. And it
     * is invisible in review as a *drawing*, because both drawings are right; only the binding
     * of drawing to name is wrong, and a diff of two hexagons does not show that.
     *
     * So the assertion is about direction: a chevron's **apex** is the vertex at the extreme
     * of its own bounding box along the axis it points, and it must be that extreme *alone*
     * and sit on the centre line of the other axis. Read off the geometry, so it cannot pass
     * on a glyph pointing the wrong way.
     */
    @Test
    fun `each chevron's apex is the extreme that matches its name`() {
        assertPointsLeft(ttActionIcons.chevronLeft)
        assertPointsRight(ttActionIcons.chevronRight)
        assertPointsDown(ttActionIcons.chevronDown)
    }

    /**
     * The two horizontal chevrons face away from each other, which is the direction claim
     * stated *relationally* rather than against each glyph's own name.
     *
     * This is the assertion that is left if somebody renames a glyph, drops one, or draws a
     * new month arrow: whatever the declarations are called, the left-pointing one has to sit
     * to the left of the right-pointing one. A swap of two identical hexagons satisfies every
     * per-glyph property *and* fails this one, which is why it is here as well as above.
     */
    @Test
    fun `the horizontal chevrons' apexes face away from each other`() {
        val left = ttActionIcons.chevronLeft.points()
        val right = ttActionIcons.chevronRight.points()
        assertTrue(
            left.minOf { it.x } < right.minOf { it.x },
            "the left-facing chevron's apex (x=${left.minOf { it.x }}) is not left of the " +
                "right-facing one's (x=${right.minOf { it.x }}), so both point the same way",
        )
        assertTrue(
            right.maxOf { it.x } > left.maxOf { it.x },
            "the right-facing chevron's apex (x=${right.maxOf { it.x }}) is not right of the " +
                "left-facing one's (x=${left.maxOf { it.x }}), so both point the same way",
        )
    }

    /**
     * The horizontal pair are mirror images of each other.
     *
     * Not a style preference — it is what makes the two read as one family, and it is what
     * makes a swap of their *bodies* the only thing that could have produced the bug above.
     * Asserting it means the fix cannot drift into two independently-drawn arrows that look
     * like a matched pair.
     */
    @Test
    fun `the horizontal chevrons are mirror images`() {
        val left = ttActionIcons.chevronLeft.points().map { it.x to it.y }
        val right = ttActionIcons.chevronRight.points().map { it.x to it.y }
        val mirrored = right.map { (x, y) -> (VIEWPORT - x) to y }
        assertEquals(
            left.size,
            mirrored.size,
            "the two chevrons are built from different numbers of points: $left vs $mirrored",
        )
        for (i in left.indices) {
            assertEquals(
                left[i].first,
                mirrored[i].first,
                EPSILON,
                "point $i is at x=${left[i].first} on the left chevron but its mirror is at " +
                    "x=${mirrored[i].first}",
            )
            assertEquals(left[i].second, mirrored[i].second, EPSILON, "point $i differs in y")
        }
    }

    /**
     * The apex of [points], along [axis], must be a single vertex at the [extreme] of the
     * glyph's own bounds — and centred on the other axis.
     */
    private fun assertApex(
        icon: ImageVector,
        axis: Axis,
        extreme: (List<Offset>) -> Float,
    ) {
        val points = icon.points()
        val edge = extreme(points)
        val atEdge = points.filter { if (axis == Axis.Horizontal) it.x == edge else it.y == edge }
        assertEquals(
            1,
            atEdge.size,
            "${icon.name} has ${atEdge.size} vertices at the ${axis.label} extreme " +
                "($edge): a chevron's apex is one vertex, and an edge here means it is a " +
                "block rather than an arrow",
        )
        val apex = atEdge.single()
        val bounds = points.bounds()
        val centre = if (axis == Axis.Horizontal) {
            (bounds.top + bounds.bottom) / 2f
        } else {
            (bounds.left + bounds.right) / 2f
        }
        val actual = if (axis == Axis.Horizontal) apex.y else apex.x
        assertTrue(
            kotlin.math.abs(actual - centre) <= CENTRE_TOLERANCE,
            "${icon.name}'s apex is at ${axis.label} $actual but its ${axis.other} centre line " +
                "is $centre, so the point is off-axis and the chevron reads as skewed",
        )
    }

    private fun assertPointsLeft(icon: ImageVector) =
        assertApex(icon, Axis.Horizontal) { points -> points.minOf { it.x } }

    private fun assertPointsRight(icon: ImageVector) =
        assertApex(icon, Axis.Horizontal) { points -> points.maxOf { it.x } }

    private fun assertPointsDown(icon: ImageVector) =
        assertApex(icon, Axis.Vertical) { points -> points.maxOf { it.y } }

    private enum class Axis(val label: String, val other: String) {
        Horizontal("x", "y"),
        Vertical("y", "x"),
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
         * How far off the centre line a chevron's apex may sit.
         *
         * The drawn contours put it exactly on it, so this is not slack for a sloppy drawing —
         * it absorbs the arithmetic of comparing two floats, and nothing else.
         */
        const val CENTRE_TOLERANCE = 0.01f

        /** Float comparison for the mirror-image test. Same reasoning as above. */
        const val EPSILON = 0.01f

        /**
         * How far a point may stray outside the viewport before it is clipped rather than merely
         * untidy. Bezier control points legitimately sit outside the curves they shape.
         */
        const val SLACK = 3f
    }
}
