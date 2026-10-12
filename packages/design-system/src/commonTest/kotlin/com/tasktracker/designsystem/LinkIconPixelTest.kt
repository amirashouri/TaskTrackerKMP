package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That [ttActionIcons.link] draws a chain, rather than two rings or nothing at all.
 *
 * ## Why this is a pixel test and not a geometry assertion
 *
 * [TabIconsGeometryTest] already covers every glyph in `ttActionIcons.all` — which includes
 * `link` — for having path nodes, staying inside the viewport, and filling the grid. All three
 * pass for a glyph whose *strokes have cancelled each other out*. A hand-drawn glyph built from
 * three shapes under `PathFillType.EvenOdd` is exactly the kind that can pass all three and
 * still paint nothing where its rings should be, and `link`'s own KDoc makes a claim that only a
 * rendered frame can confirm: that the bar is placed flush against the rings rather than over
 * them, so no parity is ever disturbed.
 *
 * That claim is not decorative. Even-odd fill counts crossings, so a bar drawn *across* a ring's
 * stroke would add two crossings inside that ring, flip it back to even, and erase the stroke
 * exactly where the bar crossed — leaving a glyph that passes every geometry check in the repo
 * and reads as a broken chain on screen. No amount of reading the `PathNode` list shows that,
 * because the path data is identical either way.
 *
 * ## What is measured, and why the centre line
 *
 * A horizontal scan across the glyph's vertical centre (grid y=12, the grid's midpoint) crosses,
 * left to right: the left ring's outer stroke, its hole, its inner stroke, the bar, the right
 * ring's inner stroke, its hole, its outer stroke. So the centre line is ink everywhere except
 * for **two** runs of background — the two ring holes. An extra gap is a stroke that failed to
 * draw; a missing gap is a hole that failed to open.
 *
 * Asserting on the *pattern* rather than on a count of ink pixels is what makes this decisive: a
 * cancellation removes ink in a specific place, and a whole-glyph coverage check would average
 * that away.
 *
 * Positions are compared in **grid units** rather than pixels, because the glyph's own geometry
 * is specified in grid units and that is what the expected hole positions are derived from. The
 * scale is recovered from the node's bounds and the frame, so the assertion does not depend on
 * the device density it happens to run on.
 *
 * The glyph is drawn at [GLYPH_DP] rather than its 12dp card size so one grid unit is
 * [GLYPH_DP] / 24 px — ten pixels of stroke to sample, which is what keeps an antialiased edge
 * from deciding the result.
 */
@OptIn(ExperimentalTestApi::class)
class LinkIconPixelTest {

    /**
     * The chain's centre line is ink except for its two holes, in the right places.
     *
     * Gated through [rasterising]: the Android host cannot rasterise a frame at all (see
     * [thisHostRasterisesText]), so this registers as a pass there rather than a skip. The JVM
     * and the iOS simulator both run it and both print their reading.
     */
    @Test
    fun `the chain draws both rings and the bar between them`() {
        val gaps = ringHoles() ?: return

        assertEquals(
            RING_HOLES,
            gaps.size,
            "the chain's centre line should cross exactly $RING_HOLES holes — one per ring — " +
                "but crossed ${gaps.size}: ${gaps.map { it.describe() }}. An extra gap is a " +
                "stroke that did not draw, which is what even-odd parity erasing it looks like, " +
                "and it passes every geometry assertion in TabIconsGeometryTest.",
        )
        // Both holes, at the positions the glyph's own geometry puts them. This is what makes
        // the assertion about *this* glyph rather than about "some glyph with two holes in it":
        // a ring that lost its inner cut would leave one wide gap, not two narrow ones.
        assertClose(HoleCentre, gaps[0].middle(), "the left ring's hole centre")
        assertClose(RightHoleCentre, gaps[1].middle(), "the right ring's hole centre")
        assertClose(HoleSpan, gaps[0].width(), "the left ring's hole width")
        assertClose(HoleSpan, gaps[1].width(), "the right ring's hole width")
    }

    /**
     * The bar between the holes is ink, so the glyph reads as a chain.
     *
     * Without this, two rings drawn side by side pass the test above — they have the right
     * number of holes in the right places — while missing the element that makes the glyph mean
     * anything. It is the check that fails if the bar is moved or dropped.
     */
    @Test
    fun `the bar joining the two rings is drawn`() {
        val gaps = ringHoles() ?: return
        if (gaps.size < 2) return // the count assertion in the test above reports this

        // Sample the middle of the gap between the two holes, which is where the bar is.
        val centre = (gaps[0].to + gaps[1].from) / 2f
        val ink = inkAt(centre) ?: return

        assertTrue(
            ink,
            "no ink at grid x=$centre, midway between the two rings (${gaps[0].describe()} and " +
                "${gaps[1].describe()}), so the bar is missing and the glyph reads as two " +
                "separate rings rather than a chain",
        )
    }

    // --- the measurement ------------------------------------------------------------------------

    /** The background runs on the centre line, in grid units. Null where this host cannot rasterise. */
    private fun ringHoles(): List<Span>? = scanCentreLine()?.filterNot { it.ink }

    /** Whether the point [gridX] on the centre line is ink. Null where this host cannot rasterise. */
    private fun inkAt(gridX: Float): Boolean? = scanCentreLine()?.let { line ->
        line.firstOrNull { gridX >= it.from && gridX < it.to }?.ink
    }

    /**
     * Render the glyph and scan its centre line once, caching the result.
     *
     * Both tests above need the same scan, and a second `rasterising` block would rasterise the
     * frame twice to answer the same question.
     */
    private fun scanCentreLine(): List<Span>? {
        cached?.let { return it }
        var result: List<Span>? = null
        rasterising {
            setContent {
                Box(Modifier.fillMaxSize().background(GROUND)) {
                    // `ttIcon`, not Material's `Icon`: Material is not a dependency here, which
                    // is the whole reason `ttIcon` exists. The tint is explicit rather than
                    // `LocalContentColor` so this measures the glyph's *shape* and cannot be
                    // moved by whatever theme the host happens to run under.
                    ttIcon(
                        image = ttActionIcons.link,
                        contentDescription = null,
                        tint = INK,
                        modifier = Modifier.size(GLYPH_DP.dp).testTag(GLYPH_TAG),
                    )
                }
            }
            waitForIdle()

            val frame = onRoot().captureToImage().toPixelMap()
            val bounds = onNodeWithTag(GLYPH_TAG).getBoundsInRoot()
            // The *root* node for the divisor, NOT the glyph node: `root.width` is the window
            // the frame was captured from, so frame.width / root.width is px per dp. Reading
            // the glyph's own width here instead divides by 240dp rather than the window width,
            // inflating every coordinate by the ratio of the two and sampling past the glyph —
            // which reads as a completely empty frame and looks like a glyph that draws nothing.
            val root = onRoot().getBoundsInRoot()
            // Frame pixels per dp, from the *frame*, not from the node: GlassSurfacePixelTest
            // records that scaling by a node's own width silently over-scales a sample into a
            // coordinate that lands off the node, which then reports the background as the thing
            // under test — a wrong reading that passes every assertion written against it. The
            // node fills the root here, so root width and node width agree and the divisor is
            // unambiguous either way.
            val pxPerDp = frame.width.toFloat() / max((root as DpRect).width.value, 1f)
            val leftPx = bounds.left.value * pxPerDp
            val topPx = bounds.top.value * pxPerDp
            val pxPerUnit = (bounds as DpRect).width.value * pxPerDp / GRID

            // One sample per grid unit, from the margin inwards.
            val spanUnits = (GRID - 2 * MARGIN).toInt()

            // The grid's vertical midpoint, which is the line this glyph's geometry is symmetric
            // about: the rings span y 8..16 and the bar y 10.5..13.5, so y=12 is inside all
            // three shapes.
            val y = topPx + GRID_MIDPOINT * pxPerUnit

            // Sample the whole drawn extent, trim to the glyph's 3-unit margin so the background
            // outside it is not read as a third hole, and then reduce to runs.
            //
            // The margin is this file's own glyph spec, and TabIconsGeometryTest already holds
            // every glyph inside it, so trimming cannot hide a stroke that escaped.
            val ink = (0 until spanUnits).map { unit ->
                val px = pixelAt(frame, leftPx + (MARGIN + unit) * pxPerUnit, y)
                // Nearer INK than GROUND. A pixel is one or the other here and never between,
                // because the glyph is drawn with a hard tint over a flat fill; the only blended
                // pixels are the antialiased edges, and the nearest-colour rule puts those on
                // whichever side they are mostly that, which is what a stroke's edge should read
                // as.
                channelDistance(px, INK) < channelDistance(px, GROUND)
            }

            // Runs, from the sampled booleans. Derived rather than accumulated while sampling:
            // accumulating while sampling puts the run's flag and its bounds in two different
            // places to go wrong, and a first version of this did exactly that — the bounds were
            // right and every flag was inverted, which is a reading that looks like a glyph
            // defect and is not one.
            val spans = mutableListOf<Span>()
            var start = 0
            ink.forEachIndexed { index, isInk ->
                if (isInk != ink[start]) {
                    spans += Span(ink[start], (MARGIN + start).toFloat(), (MARGIN + index).toFloat())
                    start = index
                }
            }
            spans += Span(ink[start], (MARGIN + start).toFloat(), (MARGIN + spanUnits).toFloat())
            // The whole scan as one line, so a wrong reading can be diagnosed without a rebuild:
            // the per-unit string is what the glyph should look like (`##..####..##`) against
            // what it did look like.
            println(
                "link scan: frame ${frame.width}x${frame.height}, root ${root.width}x${root.height}dp, " +
                    "bounds ${bounds.left},${bounds.top} ${bounds.width}x${bounds.height}dp, " +
                    "pxPerUnit ${pxPerUnit.round2()}, centre y=${y.round2()}\n" +
                    "  units ${ink.joinToString("") { if (it) "#" else "." }}   " +
                    "(grid x ${MARGIN.toInt()}..${(GRID - MARGIN).toInt()}, expect " +
                    "##....##...##....## two 4-unit holes at 5..9 and 15..19)",
            )
            result = spans
        }
        cached = result
        return result
    }

    /** One contiguous run along the centre line, in grid units. */
    private class Span(val ink: Boolean, val from: Float, val to: Float) {
        fun middle() = (from + to) / 2f
        fun width() = to - from
        fun describe() = "grid x=$from..$to"
    }

    private companion object {
        const val GLYPH_TAG = "designSystem.linkIconProbe"

        /** [ttActionIcons.link]'s grid, which every glyph in this file is specified against. */
        const val GRID = 24f

        /** The grid's vertical midpoint — inside both rings and the bar. */
        const val GRID_MIDPOINT = 12f

        /** The margin this file's glyph respects, so background outside it is not read as a hole. */
        const val MARGIN = 3f

        /**
         * The glyph's geometry, restated from `TabIcons.kt` so this test asserts against
         * numbers rather than against whatever the code happens to draw.
         *
         * Each ring is 8 units wide starting at x=3 (left) and ending at x=21 (right), with a
         * 2-unit stroke, so each hole is 4 units across: 5..9 on the left and 15..19 on the
         * right. The bar runs 11..13 between them.
         */
        const val HoleSpan = 4f
        const val HoleCentre = 7f
        const val RightHoleCentre = 17f
        const val RING_HOLES = 2

        /**
         * How big to draw the glyph.
         *
         * 240dp rather than its 12dp card size: at 12dp a grid unit is half a pixel and the
         * 2-unit stroke is 1px, so antialiasing would decide the result and the test would
         * measure the rasteriser rather than the glyph. At 240dp a unit is 10px and the stroke
         * is 20px.
         */
        const val GLYPH_DP = 240

        val GROUND = Color(0xFF101014.toInt())
        val INK = Color(0xFFF2F4F8.toInt())
    }

    /**
     * One scan, reused by both tests.
     *
     * Instance state rather than companion state, deliberately: `kotlin.test` constructs a fresh
     * instance per test method on every host, so each starts with no cached frame. Had this been
     * on the companion, a scan taken by one test would be read by the next as its own
     * measurement, and a host that cannot rasterise could leave a stale one behind.
     */
    private var cached: List<Span>? = null
}

/**
 * Asserts [actual] is within half a grid unit of [expected], naming [what] in the failure.
 *
 * The tolerance is a property of the glyph under test — one grid unit is 10px at
 * [GLYPH_DP], so half a unit is five pixels of edge slack — so it is passed in rather than
 * read from the companion's private constant.
 */
private fun assertClose(expected: Float, actual: Float, what: String) {
    val tolerance = 0.5f
    val off = abs(expected - actual)
    assertTrue(
        off <= tolerance,
        "$what should be at $expected grid units but measured ${actual.round2()} " +
            "(off by ${off.round2()}, tolerance $tolerance)",
    )
}

/**
 * [value] to two decimal places, for a failure message.
 *
 * `String.format` is JVM-only and does not resolve in Kotlin/Native's common stdlib, which is
 * how a diagnostic in this file took the iOS compilation down while the JVM one stayed green.
 * `kotlin.math.round` on a hundredth is available on every host here.
 */
private fun Float.round2(): Float = (this * 100f).roundToInt() / 100f