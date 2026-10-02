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
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the lens's own silhouette: that its wash does not reach the corners of its box.
 *
 * ## The defect this was written for
 *
 * The lens drew its wash with a plain `drawRect` and only its *rim* with [CircleShape], so the
 * four corner wedges of the rectangle were filled with white and nothing cut them back to the
 * capsule. The gradient is strongest at `startY = 0`, so the top two wedges are the ones you
 * see: the indicator read as a rounded pill wearing two squared-off blocks at its top corners.
 *
 * ## How it finds the lens
 *
 * Two bars in one frame with the lens on different tabs, and everything else identical and
 * *blank* — an empty [ImageVector] and a space for every caption. The two differences cancel out
 * of the diff and the lens is all that is left, which is the same construction
 * [TabLensContrastTest] uses for the same reason.
 *
     * The lens sits on tab 1 in the upper bar and tab 3 in the lower one, and a lens *at rest* is
     * inset [ttSpacing.tabBarLensInset] on every side of a segment, so the tab cell's own bounds
     * contain the whole lens and no geometry has to be recomputed here: a tagged tab cell *is* the
     * window to search, and the test cannot go stale against a token.
     *
     * "At rest" is doing real work in that sentence, and it is not asserted by anything except the
     * footprint's own edges — a lens at the peak of its stretch is
     * [ttGlass.TAB_LENS_WIDEN] wider than the segment and would overhang the window. Both bars are
     * at rest because the selection never changes after composition, which is a property of
     * `lensStretch` and of `animateFloatAsState` starting at its target, neither of which this
     * file controls. So the edge assertion below is load-bearing rather than belt-and-braces: if
     * either of those ever changes, the window starts clipping the shape and the corners stop
     * meaning anything. It fails loudly rather than quietly passing.
     *
     * ## Why the assertion is on the bounding box's corners and not on a point
     *
     * A hard-coded corner coordinate would be an assertion about today's geometry — the lens's
     * width, the segment width, the density. The bounding box of the ink is the shape's own claim
     * about how far it reaches, and "the corners of your own bounding box carry no ink" is the one
     * statement that is false for a rectangle filled inside a capsule and true for a capsule. It
     * also holds for any shape with rounded ends, which is what the lens is allowed to become.
     */
@OptIn(ExperimentalTestApi::class)
class TabLensCornerTest {

    /**
     * No ink in the corners of the lens's own bounding box.
     *
     * Reports the worst corner as a channel delta on the 0-to-1 scale rather than a bare
     * failure, because "the corners are wrong" is not a diagnosis. The number says how much of
     * the wash reached a pixel that belongs to the bar's glass and to nothing else.
     *
     * The two appearances carry very different amounts of it, and the ceiling has to clear both
     * the noise and the *weaker* of them: unclipped, the wash measured `#09` into the corner on
     * the light bar and `#21` on the dark one, against a `#05` noise floor. So the light figure
     * is the thin margin here — 1.8 times the noise — and the dark one is comfortable. If a
     * future change makes this fail on light alone, the fix is to make the corner quieter, not
     * to raise the ceiling until it passes.
     *
     * Both hosts report the same two numbers to the byte, which is worth recording because it is
     * the opposite of every other measurement on this component. The captions differ between the
     * JVM and iOS by most of a point of contrast, because a stroke's coverage is a rasteriser's
     * business; this is a hard-edged rectangle overdrawing its own silhouette, and both
     * rasterisers agree on exactly how much white lands in the corner.
     */
    @Test
    fun theLensWashStopsAtItsRimAtEveryCorner() = rasterising {
        for (scheme in APPEARANCES) {
            var reading: CornerReading? = null
            runComposeUiTest { reading = corners(scheme) }
            val measured = requireNotNull(reading) { "no frame for ${scheme.name}" }
            assertTrue(
                measured.worst <= CORNER_CEILING,
                "in ${scheme.name} appearance the lens's wash reaches ${measured.worst.hex()} " +
                    "past the bar's glass in the ${measured.worstCorner} corner of its own " +
                    "bounding box, where the rim curves away and only glass should be. The lens " +
                    "fills its box with drawRect and rounds only its border, so the four corner " +
                    "wedges of the rectangle survive; the top two show because the gradient is " +
                    "strongest at startY = 0. Clipping the wash to the same shape the rim uses " +
                    "is the fix",
            )
        }
    }

    /**
     * Renders the two bars and measures how far the lens reaches.
     *
     * One [runComposeUiTest] because Compose allows one `setContent`, and the bars are stacked
     * rather than rendered in sequence so the lens's own animation state cannot differ between
     * them. Each bar lays out identically, so the same local coordinates address the same thing
     * in both.
     */
    private fun ComposeUiTest.corners(scheme: ThemeScheme): CornerReading {
        setContent {
            ttTheme(scheme) {
                Box(Modifier.fillMaxSize().background(ttCanvas())) {
                    Column {
                        Bar(selected = 1, suffix = "inked")
                        // A gap wider than the bar's shadow. The shadow falls downward, so bars
                        // stacked flush would hand the first one's shadow to the second one's top
                        // rows and the diff would report a band of difference across the whole
                        // window that has nothing to do with either lens.
                        Box(Modifier.height(SHADOW_CLEARANCE))
                        Bar(selected = 3, suffix = "blanked")
                    }
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage()
        val inked = onNodeWithTag(tabTag(1, "inked")).getBoundsInRoot()
        val blanked = onNodeWithTag(tabTag(1, "blanked")).getBoundsInRoot()
        val root = onRoot().getBoundsInRoot()

        // Bounds are in dp and the frame is in pixels. Deriving the scale from the measured root
        // keeps this honest at any density instead of assuming the host's.
        val scale = frame.width.toFloat() / max(root.right.value, 1f)
        val pixels = frame.toPixelMap()

        val width = ((inked.right.value - inked.left.value) * scale).toInt()
        val left = (inked.left.value * scale).toInt()
        val top = (inked.top.value * scale).toInt()
        val secondTop = (blanked.top.value * scale).toInt()
        assertTrue(width > 0, "the tab rectangle had no width")
        val height = ((inked.bottom.value - inked.top.value) * scale).toInt()
        assertTrue(height > 0, "the tab rectangle had no height")
        assertTrue(
            secondTop + height <= frame.height,
            "the second bar runs to ${secondTop + height} in a frame of ${frame.height} pixels, " +
                "so the diff would read off the bottom of the image rather than off a lens. The " +
                "window is not tall enough for two bars and a ${SHADOW_CLEARANCE} gap",
        )
        // The two bars have to be the same shape in the same place, or the diff is not a diff: it
        // is the glass's own vertical ramp seen at two heights, which disagrees everywhere and
        // would hand this test a full-window bounding box whose corners are empty in both bars.
        // The lens cannot make either of these untrue, so they are free.
        assertTrue(
            blanked.left.value == inked.left.value && blanked.right.value == inked.right.value,
            "the two bars are not the same width: ${inked.left.value}..${inked.right.value} " +
                "against ${blanked.left.value}..${blanked.right.value}. Nothing about the lens can " +
                "cause this, so the window is wrong rather than the pixels",
        )
        assertTrue(
            blanked.top.value - inked.top.value == blanked.bottom.value - inked.bottom.value,
            "the two tab cells are not the same height, so the diff is comparing different rows of " +
                "the glass ramp rather than the same row of two bars",
        )

        fun inked(x: Int, y: Int): Color =
            pixels[left + x, top + y].let { Color(it.red, it.green, it.blue) }

        fun blanked(x: Int, y: Int): Color =
            pixels[left + x, secondTop + y].let { Color(it.red, it.green, it.blue) }

        /**
         * The largest channel difference between the two bars at one pixel, on the 0-to-1 scale.
         *
         * A max over channels rather than a distance, because the question is whether *any*
         * channel carries the wash. Exact inequality is not good enough: the two bars are laid
         * out identically but composited at different sub-pixel offsets, so roughly one colour
         * step shows up across the whole window.
         */
        fun delta(x: Int, y: Int): Float {
            val first = inked(x, y)
            val second = blanked(x, y)
            return max(abs(first.red - second.red), abs(first.green - second.green))
                .coerceAtLeast(abs(first.blue - second.blue))
        }

        // The lens's footprint: every pixel the two bars disagree about. Nothing else in the
        // window differs, because both bars carry the same blank icon and the same blank caption
        // on every tab, so this is the lens and the lens alone.
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
            "no pixel in the tab's window differed between the two bars, so the lens was not " +
                "drawn at all and there is nothing here to measure",
        )
        // The footprint must be strictly inside the window, and this is the assertion that makes
        // the diff mean "the lens".
        //
        // Without it the test has a vacuous green. If anything *other* than the lens ever differs
        // in this window — a layer on an empty node, a compositing phase that drifts past the
        // noise floor — the bounding box becomes the whole window, the four corner probes land on
        // bare glass three points outside the lens, both bars agree there, and the test passes
        // with the lens's corners as square as you like. That is not hypothetical: TabBar.kt
        // records an empty child darkening its backdrop by 0.14 of luminance as a defect that
        // shipped, and the selection does rescale the tab cell, so the inked and blanked bars
        // really do differ in ways that have nothing to do with the lens.
        //
        // It also checks the premise the KDoc above relies on. A lens at rest is inset
        // [ttSpacing.tabBarLensInset] on every side of its segment, so it cannot reach the
        // window's edge — and if it ever did, the window would be clipping the shape under
        // measurement and the corners below would be measuring the clipping instead of the lens.
        // Widen it at the peak of the stretch, or let the selection start mid-travel, and this is
        // what says so.
        assertTrue(
            minX > 0 && minY > 0 && maxX < width - 1 && maxY < height - 1,
            "the differing pixels run ${minX}..$maxX by ${minY}..$maxY inside a ${width}x$height " +
                "window, so they reach an edge of it. Either something other than the lens is in " +
                "this diff, or the lens is wider than the tab cell that was supposed to contain " +
                "it and the corners below would be measuring the window rather than the lens",
        )

        /**
         * How far into each corner of the bounding box to look, in pixels.
         *
         * Derived from the footprint rather than fixed, because the clearance it has to buy is a
         * fraction of the shape's own radius and the radius is in pixels. [CircleShape] on a
         * stadium rounds each end by half the *smaller* dimension, and the nearest point of the
         * arc to a corner is that radius times `sqrt(2) - 1` — about 0.41 of it. So
         * [CORNER_ARC_SHARE] is a share of the *radius* and lands the probe at roughly a third of
         * the clearance, at any density: here the radius is about nineteen pixels, the clearance
         * is about eight, and the block lands at two. A fixed four would have left about two
         * pixels of margin at this density and twenty-three at density 3, which is a flake
         * waiting for the next low-density host.
         */
        val radius = minOf(maxX - minX + 1, maxY - minY + 1) / 2f
        val block = (radius * CORNER_ARC_SHARE).toInt().coerceIn(1, CORNER_BLOCK)

        // The four corners of the ink's own bounding box, which is the lens's claim about how far
        // it reaches. A capsule has empty corners; a rectangle filled inside one does not.
        //
        // Clamped into the window as well, so that a footprint only a pixel or two across probes
        // this component's own glass rather than the neighbouring tab's. The assertion above
        // should make that unreachable; a clamp is cheaper than trusting it.
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
     * One bar, with nothing in it that could differ from its twin.
     *
     * The icon is an empty [ImageVector] and every caption is a space, which leaves no glyph and no
     * letter for the diff to find. Both bars therefore lay out identically: the row's height comes
     * from `ttIconSize.tab` and the label's from the caption's metrics, neither of which an empty
     * vector or a space changes.
     *
     * Blank rather than merely absent, and that is the whole point of the pair. The selection
     * recolours and rescales the glyph and the label, so tab 1 in the inked bar is a *different
     * looking* tab from tab 1 in the blanked one. An icon that rendered as something visible would
     * put that difference straight into the footprint and hand the assertion a bounding box that
     * is not the lens's.
     */
    @Composable
    private fun Bar(selected: Int, suffix: String) {
        // No tag on the bar itself: both bars would carry the same one, and the scale is derived
        // from the root, which is unique by construction.
        Box {
            TTGlassTabBar(
                tabs = TABS,
                selectedIndex = selected,
                onSelect = {},
                title = { " " },
                icon = { EMPTY_ICON },
                tabModifier = { Modifier.testTag(tabTag(TABS.indexOf(it), suffix)) },
            )
        }
    }

    private class CornerReading(val worst: Float, val worstCorner: String)

    private companion object {
        val APPEARANCES = listOf(ThemeScheme.Light, ThemeScheme.Dark)

        val TABS = listOf("Projects", "Board", "Backlog", "Settings")

        /**
         * An icon that draws nothing.
         *
         * [ImageVector.Builder] with no path added, rather than a vector carrying a transparent
         * path: a transparent path still costs a rasterised path and a blend for every frame, and
         * the only thing wanted here is the absence of one. The size is declared in both units
         * because the viewport is what a path would be scaled against, and there are no paths to
         * scale. Note that this declared size is not what sets the bar's height — `ttIcon` sizes
         * the image itself from `ttIconSize.tab` — which is why replacing a real glyph with this
         * changes nothing about either bar's layout.
         */
        val EMPTY_ICON: ImageVector = ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).build()

        private fun tabTag(index: Int, suffix: String) = "cornerProbe-$suffix-$index"

        /**
         * Vertical clearance between the two bars, in dp. Comfortably more than the shadow's
         * reach at [ttGlass.tabBarShadowElevation], which is the only thing that could put one
         * bar's pixels into the other's window.
         */
        val SHADOW_CLEARANCE = 64.dp

        /**
         * The smallest channel difference treated as a real change, on the 0-to-1 scale.
         *
         * Three colour steps, against compositing noise of about one. It is set low rather than at
         * the five [TabLensContrastTest] uses because this file has a *second* threshold sitting
         * just above it, and the gap between the two is the width of the band of overhangs this
         * test can see: anything above the floor enters the footprint, anything above the ceiling
         * fails. Five and a quarter left a band two steps wide, in which a five-step overhang
         * entered neither. Three leaves six, which is the best available without letting the
         * compositing phase into the footprint.
         */
        const val NOISE_FLOOR = 0.012f

        /**
         * The most the wash may put into a corner pixel, on the 0-to-1 scale.
         *
         * Six colour steps, set from the *weaker* of the two appearances on purpose: unclipped,
         * the corner measured `#09` on the light bar and `#21` on the dark one. A ceiling that
         * only cleared dark would let the light appearance regress silently, and light is the
         * appearance whose two rasterisers differ most elsewhere in this component.
         *
         * Six steps is also roughly where an overhang stops being visible at all, which is where
         * this belongs. There is no threshold that catches everything: an overhang smaller than
         * the floor is not in the footprint and one under this ceiling does not fail, and that
         * band is deliberate rather than an oversight. It is why the assertion is on the shape
         * rather than on a magnitude.
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

/** Hex for failure messages, in whole bytes so it reads the same on every target. */
private fun Float.hex(): String {
    fun channel(value: Float): String = round(value * 255f).toInt().toString(16).padStart(2, '0')
    return "#" + channel(this)
}
