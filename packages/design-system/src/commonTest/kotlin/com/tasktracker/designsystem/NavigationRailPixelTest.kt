package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the rail lens's own silhouette: that its wash does not reach the corners of its box.
 *
 * ## Why a rail needs this when the bar already has it
 *
 * [TabLensCornerTest] found the bar's lens drawing a plain `drawRect` and rounding only its
 * *rim*, so the four corner wedges of the rectangle were filled with white and nothing cut them
 * back to the capsule. [RailLens] is a separate composable with its own modifier chain, and
 * "inherited rather than re-derived" is a claim about the source, not a property the renderer
 * enforces — a chain written fresh on a different axis is exactly where the same omission comes
 * back.
 *
 * Measured on this rail, top-left corner, clipping removed: **`#20` (0.125) on the dark rail, on
 * both hosts**, against the bar's `#21`. Clipped, the same corner reads **`#02` on light and
 * `#00` on dark, and the JVM and the iOS simulator agree on both to the byte** — the one thing
 * this component's other measurements do not do. The captions differ between the two rasterisers
 * by most of a point of contrast, because a glyph stroke's coverage is a rasteriser's business;
 * this is a hard-edged rectangle overdrawing its own silhouette, and both rasterisers agree on
 * exactly how much white lands in the corner.
 *
 * ## How it finds the lens
 *
 * Two rails in one frame with the lens on different destinations, and everything else identical
 * and *blank* — an empty [ImageVector] and a space for every caption. Both rails lay out
 * identically, so the diff is the lens and the lens alone. This is the same construction
 * [TabLensCornerTest] uses, for the same reason.
 *
 * ## The window is the rail, not the cell
 *
 * The bar windows on the selected tab's cell, because a lens at rest is inset within its segment
 * and a cell therefore contains it. A rail cannot do that: its lens spans the rail's full width
 * while a cell is only as wide as its own caption, so windowing on a cell would clip the shape
 * under measurement. The window is the rail's own bounds instead, and the footprint found inside
 * it is the lens, whose bounding box is the lens's own claim about how far it reaches. The
 * assertion that the footprint is strictly inside the window below is what stops this from
 * becoming vacuous.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationRailPixelTest {

    /**
     * No ink in the corners of the rail lens's own bounding box.
     *
     * Reports the worst corner as a channel delta on the 0-to-1 scale rather than a bare failure,
     * because "the corners are wrong" is not a diagnosis — the number says how much of the wash
     * reached a pixel that belongs to the rail's glass and to nothing else.
     */
    @Test
    fun theRailLensWashStopsAtItsRimAtEveryCorner() = rasterising {
        for (scheme in APPEARANCES) {
            var reading: CornerReading? = null
            runComposeUiTest { reading = corners(scheme) }
            val measured = requireNotNull(reading) { "no frame for ${scheme.name}" }
            assertTrue(
                measured.worst <= CORNER_CEILING,
                "in ${scheme.name} appearance the rail lens's wash reaches ${measured.worst.hex()} " +
                    "past the glass in the ${measured.worstCorner} corner of its own bounding " +
                    "box, where the rim curves away and only glass should be. The lens fills its " +
                    "box with drawRect and rounds only its border, so the four corner wedges of " +
                    "the rectangle survive; the top two show because the gradient is strongest " +
                    "at startY = 0. Clipping the wash to the same shape the rim uses is the fix",
            )
        }
    }

    /**
     * Renders the two rails and measures how far the lens reaches.
     *
     * One [runComposeUiTest] per appearance because Compose allows one `setContent`, and the rails
     * are stacked rather than rendered in sequence so the lens's own animation state cannot differ
     * between them.
     */
    private fun ComposeUiTest.corners(scheme: ThemeScheme): CornerReading {
        setContent {
            ttTheme(scheme) {
                Box(Modifier.fillMaxSize().background(ttCanvas())) {
                    Column {
                        Rail(selected = 1, suffix = "inked")
                        // A gap wider than the rail's shadow. The shadow falls outward, so rails
                        // stacked flush would hand the first one's shadow to the second one's rows
                        // and the diff would report a band of difference across the whole window
                        // that has nothing to do with either lens.
                        Box(Modifier.height(SHADOW_CLEARANCE))
                        Rail(selected = 3, suffix = "blanked")
                    }
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage()
        val inked = onNodeWithTag(railTag("inked")).getBoundsInRoot()
        val blanked = onNodeWithTag(railTag("blanked")).getBoundsInRoot()
        val root = onRoot().getBoundsInRoot()

        // Bounds are in dp and the frame is in pixels. Deriving the scale from the measured root
        // keeps this honest at any density instead of assuming the host's.
        val scale = frame.width.toFloat() / max(root.right.value, 1f)
        val pixels = frame.toPixelMap()

        val width = ((inked.right.value - inked.left.value) * scale).toInt()
        val left = (inked.left.value * scale).toInt()
        val top = (inked.top.value * scale).toInt()
        val secondTop = (blanked.top.value * scale).toInt()
        assertTrue(width > 0, "the rail had no width")
        val height = ((inked.bottom.value - inked.top.value) * scale).toInt()
        assertTrue(height > 0, "the rail had no height")
        assertTrue(
            secondTop + height <= frame.height,
            "the second rail runs to ${secondTop + height} in a frame of ${frame.height} pixels, " +
                "so the diff would read off the bottom of the image rather than off a lens. The " +
                "window is not tall enough for two rails and a $SHADOW_CLEARANCE gap",
        )
        // The two rails have to be the same shape in the same place, or the diff is not a diff: it
        // is the glass's own ramp seen at two heights, which disagrees everywhere and would hand
        // this test a full-window bounding box whose corners are empty in both rails.
        assertTrue(
            blanked.left.value == inked.left.value && blanked.right.value == inked.right.value,
            "the two rails are not the same width: ${inked.left.value}..${inked.right.value} " +
                "against ${blanked.left.value}..${blanked.right.value}. Nothing about the lens can " +
                "cause this, so the window is wrong rather than the pixels",
        )
        assertTrue(
            blanked.top.value - inked.top.value == blanked.bottom.value - inked.bottom.value,
            "the two rails are not the same height, so the diff is comparing different rows of " +
                "the glass ramp rather than the same row of two rails",
        )

        fun inked(x: Int, y: Int): Color =
            pixels[left + x, top + y].let { Color(it.red, it.green, it.blue) }

        fun blanked(x: Int, y: Int): Color =
            pixels[left + x, secondTop + y].let { Color(it.red, it.green, it.blue) }

        /**
         * The largest channel difference between the two rails at one pixel, on the 0-to-1 scale.
         *
         * A max over channels rather than a distance, because the question is whether *any* channel
         * carries the wash. Exact inequality is not good enough: the two rails are laid out
         * identically but composited at different sub-pixel offsets, so roughly one colour step
         * shows up across the whole window.
         */
        fun delta(x: Int, y: Int): Float {
            val first = inked(x, y)
            val second = blanked(x, y)
            return max(abs(first.red - second.red), abs(first.green - second.green))
                .coerceAtLeast(abs(first.blue - second.blue))
        }

        // The lens's footprint: every pixel the two rails disagree about. Nothing else in the
        // window differs, because both rails carry the same blank icon and the same blank caption
        // on every destination, and the blanked rail's lens sits on destination 3, outside this
        // window.
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (delta(x, y) <= NOISE_FLOOR) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        assertTrue(
            minX <= maxX && minY <= maxY,
            "no pixel in the rail's window differed between the two rails, so the lens was not " +
                "drawn at all and there is nothing here to measure",
        )
        // The footprint must be strictly inside the window, and this is the assertion that makes
        // the diff mean "the lens".
        //
        // Without it the test has a vacuous green. If anything *other* than the lens ever differs
        // in this window — a layer on an empty node, a compositing phase that drifts past the
        // noise floor — the bounding box becomes the whole window, the four corner probes land on
        // bare glass outside the lens, both rails agree there, and the test passes with the lens's
        // corners as square as you like.
        assertTrue(
            minX > 0 && minY > 0 && maxX < width - 1 && maxY < height - 1,
            "the differing pixels run ${minX}..$maxX by ${minY}..$maxY inside a ${width}x$height " +
                "window, so they reach an edge of it. Either something other than the lens is in " +
                "this diff, or the lens is wider than the rail that was supposed to contain it " +
                "and the corners below would be measuring the window rather than the lens",
        )

        /**
         * How far into each corner of the bounding box to look, in pixels.
         *
         * Derived from the footprint rather than fixed, because the clearance it has to buy is a
         * fraction of the shape's own radius and the radius is in pixels. [CircleShape] on a
         * stadium rounds each end by half the *smaller* dimension, so the block is sized as a
         * share of the radius and lands at roughly a third of the clearance at any density. A
         * fixed block would have left a couple of pixels of margin at this density and twenty-odd
         * at density 3, which is a flake waiting for the next low-density host.
         */
        val radius = minOf(maxX - minX + 1, maxY - minY + 1) / 2f
        val block = (radius * CORNER_ARC_SHARE).toInt().coerceIn(1, CORNER_BLOCK)

        fun clampX(value: Int) = value.coerceIn(0, width - block)

        fun clampY(value: Int) = value.coerceIn(0, height - block)

        val corners = listOf(
            Triple("top-left", clampX(minX), clampY(minY)),
            Triple("top-right", clampX(maxX - block + 1), clampY(minY)),
            Triple("bottom-left", clampX(minX), clampY(maxY - block + 1)),
            Triple("bottom-right", clampX(maxX - block + 1), clampY(maxY - block + 1)),
        )

        /**
         * The largest difference over a square block.
         *
         * A block rather than a single pixel because a rim's antialiasing leaves the pixel
         * immediately inside it legitimately uncertain, and a one-pixel probe would be reading
         * that instead of the region.
         */
        fun worstIn(blockTopLeftX: Int, blockTopLeftY: Int): Float {
            var worst = 0f
            for (y in blockTopLeftY until blockTopLeftY + block) {
                for (x in blockTopLeftX until blockTopLeftX + block) {
                    worst = max(worst, delta(x, y))
                }
            }
            return worst
        }

        val worst = corners.maxBy { worstIn(it.second, it.third) }
        return CornerReading(
            worst = worstIn(worst.second, worst.third),
            worstCorner = worst.first,
        )
    }

    /**
     * One rail, with nothing in it that could differ from its twin.
     *
     * The icon is an empty [ImageVector] and every caption is a space, which leaves no glyph and
     * no letter for the diff to find, and both rails therefore lay out identically.
     *
     * Blank rather than merely absent, and that is the whole point of the pair: the selection
     * recolours and rescales the glyph and the label, so destination 1 in the inked rail is a
     * *different looking* destination from destination 1 in the blanked one. An icon that rendered
     * as something visible would put that difference straight into the footprint and hand the
     * assertion a bounding box that is not the lens's.
     *
     * Note what a blank caption does to the rail's width: with no real glyph the column collapses
     * to the icon's measure, which is why the lens is windowed on the rail rather than on a cell.
     */
    @Composable
    private fun Rail(selected: Int, suffix: String) {
        // A definite height, because the rail now fills the height it is offered. Left to wrap its
        // own content the first rail measured 1024dp wide and the second measured nothing at all —
        // a `Column` hands its children an unbounded height, so `fillMaxHeight` does nothing and
        // what the harness ends up measuring is whatever the surrounding layout happened to give
        // it. Pinning the height makes this deterministic instead, and 300dp is comfortably more
        // than a caption and an icon need, so the lens is the same shape the app draws.
        Box(Modifier.height(RAIL_HEIGHT)) {
            TTGlassNavigationRail(
                destinations = DESTINATIONS,
                selectedIndex = selected,
                onSelect = {},
                title = { " " },
                icon = { EMPTY_ICON },
                modifier = Modifier.testTag(railTag(suffix)),
            )
        }
    }

    private class CornerReading(val worst: Float, val worstCorner: String)

    /**
     * The delta as a whole byte, for a failure message that can be compared by eye.
     *
     * Scaled by 255 and rounded, not truncated. The first version of this file truncated, and it
     * made a reading of 0.03 print as `#00` — a failure message reporting that the wash reached
     * *nothing* past the glass, on the run that proved it reached a measurable amount. That is
     * the whole point of printing the number.
     */
    private fun Float.hex(): String =
        "#" + round(this * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')

    private companion object {
        val APPEARANCES = listOf(ThemeScheme.Light, ThemeScheme.Dark)

        val DESTINATIONS = listOf("Projects", "Board", "Backlog", "Sprints", "Settings")

        /**
         * An icon that draws nothing.
         *
         * [ImageVector.Builder] with no path added, rather than a vector carrying a transparent
         * path: a transparent path still costs a rasterised path and a blend for every frame, and
         * the only thing wanted here is the absence of one.
         */
        val EMPTY_ICON: ImageVector = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).build()

        private fun railTag(suffix: String) = "railCornerProbe-$suffix"

        /**
         * Vertical clearance between the two rails, in dp. Comfortably more than the shadow's
         * reach at [ttGlass.tabBarShadowElevation], which is the only thing that could put one
         * rail's pixels into the other's window.
         */
        val SHADOW_CLEARANCE = 64.dp

        /**
         * The height each rail is given, in dp.
         *
         * Two of these plus [SHADOW_CLEARANCE] is 664dp, which fits the harness window with room
         * to spare — the frame-height assertion above is what proves it rather than this comment.
         */
        val RAIL_HEIGHT = 300.dp

        /**
         * The smallest channel difference treated as a real change, on the 0-to-1 scale.
         *
         * Three colour steps, against compositing noise of about one — the same band
         * [TabLensCornerTest] uses, and for the same reason: it has a second threshold sitting just
         * above it, and the gap between the two is the width of the band of overhangs this test can
         * see. Three leaves six, which is the best available without letting the compositing phase
         * into the footprint.
         */
        const val NOISE_FLOOR = 0.012f

        /**
         * The most the wash may put into a corner pixel, on the 0-to-1 scale.
         *
         * Six colour steps, and the gap either side of the measurements is the point.
         *
         * Clipped, this rail's corner reads `#02` on light and `#00` on dark — below [NOISE_FLOOR],
         * so a correct lens contributes nothing to its own footprint. Unclipped it reads `#20` on
         * dark, eight times the ceiling. The test therefore sits in a wide band rather than on a
         * threshold balanced against its own noise, which is what would let a future drift on this
         * rail pass quietly.
         *
         * Kept at the bar's ceiling rather than tightened to this rail's own quieter light
         * reading, because the two are the same material on two axes and a tighter number here
         * would be a second threshold to keep true for no gain. The worst rail reading is a
         * factor of eight below it and the best unclipped one a factor of five above, so there is
         * nothing to tighten against.
         *
         * There is no threshold that catches everything: an overhang smaller than the floor is not
         * in the footprint and one under this ceiling does not fail, and that band is deliberate
         * rather than an oversight. It is why the assertion is on the shape rather than on a
         * magnitude.
         */
        const val CORNER_CEILING = 0.025f

        /**
         * The largest corner block, in pixels, and the share of the radius it may spend.
         *
         * The cap stops the probe growing without bound on a high-density host, where the arc is
         * already tens of pixels from the corner; the share is what sizes it, so the probe scales
         * with the shape rather than with the display.
         */
        const val CORNER_BLOCK = 4
        const val CORNER_ARC_SHARE = 0.14f
    }
}
