package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves the initials in a disc are readable *over the disc as it renders*.
 *
 * ## Why this measures rather than computes
 *
 * The disc's fill is a wash: `ttAccent()` at 18% over whatever is behind it. Computing
 * `over(accent, backdrop, 0.18f)` in a test and calling the result the fill is exactly the
 * mistake [GlassFillContrastTest] documents at length - a wash of a correct colour is not the
 * colour, and the sign-in button sat at 1.07:1 while every token test stayed green. So the
 * backdrop here is read off the frame, not composited.
 *
 * ## Why each disc is rendered twice
 *
 * Once with its initials and once without, so the glyph can be told apart from the two things
 * sharing its window - the wash and the rim hairline, which is 26% of a 24dp disc's area and is
 * a different colour from both. The blanked twin's *most common* pixel is the fill, measured;
 * the inked twin's pixel furthest from it is the glyph, measured. Neither is derived from a
 * token, so neither can be right about the frame the user sees.
 *
 * ## Where the numbers landed, measured by this file on both hosts
 *
 * Light 15.80:1 and dark 18.62:1, and the two rasterisers agree to the last bit of a float.
 * That agreement is a fact about the estimator rather than a claim that coverage never
 * matters: the ink is found by differencing against a blanked twin, which picks the
 * best-covered pixel of the glyph, and at 24dp two initials always have a solid core. The
 * disagreement this repository has seen before was a caption thin enough that no pixel reached
 * full coverage, where the two hosts then differed by half a point. A coverage-sensitive
 * question needs a percentile rather than this peak.
 *
 * ## The WCAG maths is deliberately duplicated
 *
 * As in [GlassFillContrastTest]: a shared helper is one edit away from relaxing both suites at
 * once. These are the last line of defence for the only text in the app drawn over a wash at
 * 18%, and if two implementations of the same formula ever disagree, one of them should be
 * visibly wrong rather than quietly consistent.
 */
@OptIn(ExperimentalTestApi::class)
class AvatarContrastTest {

    @Test
    fun theInitialsAreReadableOverTheirOwnDisc() = rasterising {
        for (scheme in APPEARANCES) {
            for (initials in CASES) {
                var readings: Map<String, Reading> = emptyMap()
                runComposeUiTest { readings = read(scheme = scheme) }
                val measured = requireNotNull(readings[initials]) {
                    "no reading for '$initials' in ${scheme.name} appearance; the frame " +
                        "produced ${readings.keys}"
                }
                assertTrue(
                    measured.ratio >= TEXT_CONTRAST_MINIMUM,
                    "the initials '$initials' in ${scheme.name} appearance render as " +
                        "${measured.ink.hex()} on ${measured.fill.hex()}, which is " +
                        "${measured.ratio.ratioText()} to 1 - below the " +
                        "${TEXT_CONTRAST_MINIMUM} to 1 that text this size needs. ttAvatar's " +
                        "FILL_ALPHA, or ttLabel() for this appearance, is what to look at",
                )
            }
        }
    }

    /**
     * Renders one appearance's twin frames and measures every case in them.
     *
     * The stack's own cases are measured rather than a convenient sample: `AR` is the widest
     * initials run a 24dp disc holds, `A` the narrowest a real name produces, and `+2` is the
     * overflow chip, which is the one disc drawn with no name behind it and so the one whose
     * contrast is decided entirely by this primitive.
     */
    private fun ComposeUiTest.read(scheme: ThemeScheme): Map<String, Reading> {
        setContent {
            ttTheme(scheme) {
                Box(Modifier.fillMaxSize().background(ttCanvas())) {
                    Column {
                        RowOfDiscs(inked = true)
                        RowOfDiscs(inked = false)
                    }
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage()
        val pixels = frame.toPixelMap()
        val root = onRoot().getBoundsInRoot()
        // Bounds are in dp and the frame is in pixels. Deriving the scale from the measured root
        // keeps this honest at any density instead of assuming the host's.
        val scale = frame.width.toFloat() / max(root.right.value, 1f)

        // Every case is measured out of one pair of frames per appearance, so the appearance is
        // composed once and the cases cannot drift apart in what they were rendered with.
        return CASES.withIndex().associate { (index, initials) ->
            val inkedBounds = onNodeWithTag(discTag(index, inked = true)).getBoundsInRoot()
            val blankedBounds = onNodeWithTag(discTag(index, inked = false)).getBoundsInRoot()
            val blankedWindow = blankedBounds.toWindow(scale)
            val blank = blankedWindow.pixels(pixels)
            val inked = inkedBounds.toWindow(scale).pixels(pixels)

            // The window of a 24dp disc is a square with four corners of *canvas* in it, and the
            // rim hairline is a third colour. Both have to go before either colour can be called
            // the fill: the corners are excluded by distance from the centre rather than by
            // picking the modal colour, because which colour wins a mode depends on the fill
            // being measured. At a fully opaque accent the canvas was the furthest pixel from
            // the fill, so the "ink" this file measured was a corner and the contrast it
            // reported was the accent's documented 7.88:1 against white - a number that has
            // nothing to do with the initials.
            val interior = blankedWindow.interiorOffsets()
            val fill = interior
                .map { blank[it] }
                .groupingBy { it }.eachCount().maxBy { it.value }.key

            // The glyph is the one thing the blanked twin does not have, so it is found by
            // differencing the two frames at the same offset rather than by looking for a colour
            // that contrasts with the fill. The colour reported is still the *inked* frame's, so
            // what is measured is what was rasterised and not a reconstruction of it.
            val glyph = interior.maxBy { abs(relativeLuminance(inked[it]) - relativeLuminance(blank[it])) }
            val ink = inked[glyph]

            initials to Reading(ink = ink, fill = fill, ratio = contrastRatio(ink, fill))
        }
    }

    /**
     * One row of discs, spaced by explicit `offset` rather than a `Row`'s arrangement.
     *
     * Positioned absolutely so the pixel window is arithmetic. A row's spacing is a gap *between*
     * children, which would mean re-deriving the stride from the very layout being measured -
     * and the stride is one of the numbers under test elsewhere.
     */
    @Composable
    private fun RowOfDiscs(inked: Boolean) {
        Box {
            CASES.forEachIndexed { index, value ->
                ttInitialsAvatar(
                    initials = if (inked) value else BLANK,
                    size = ttAvatarSize.avatar,
                    modifier = Modifier
                        .offset(x = (ttAvatarSize.avatar + PITCH) * index)
                        .testTag(discTag(index, inked = inked)),
                )
            }
        }
    }

    /**
     * Tagged per case as well as per frame.
     *
     * All three cases share one tag otherwise, and a node query that matches three nodes has no
     * single set of bounds to return - which is how the first version of this test failed,
     * with "Failed to retrieve bounds of the node" rather than a number.
     */
    private fun discTag(index: Int, inked: Boolean) = "avatar/${if (inked) "inked" else "blanked"}/$index"

    /** A private rectangle in frame pixels, derived from a dp bounds. */
    private data class Window(val left: Int, val top: Int, val width: Int, val height: Int) {

        /** Row-major pixel offsets, matching [pixels] so two windows can be differenced. */
        fun pixels(pixels: PixelMap): List<Color> =
            (0 until height).flatMap { y ->
                (0 until width).map { x -> pixels[left + x, top + y] }
            }

        /**
         * Offsets inside the disc, excluding the corners the circle does not reach and the rim
         * hairline, which is a different colour from the wash on purpose - it is the disc's
         * edge, not its face.
         */
        fun interiorOffsets(): List<Int> {
            val centreX = width / 2f
            val centreY = height / 2f
            val radius = minOf(width, height) / 2f - RIM_INSET_PX
            return (0 until height).flatMap { y ->
                (0 until width).mapNotNull { x ->
                    val dx = x - centreX
                    val dy = y - centreY
                    if (dx * dx + dy * dy <= radius * radius) y * width + x else null
                }
            }
        }
    }

    private fun DpRect.toWindow(scale: Float) = Window(
        left = (left.value * scale).roundToInt(),
        top = (top.value * scale).roundToInt(),
        width = ((right.value - left.value) * scale).roundToInt().coerceAtLeast(1),
        height = ((bottom.value - top.value) * scale).roundToInt().coerceAtLeast(1),
    )

    private data class Reading(val ink: Color, val fill: Color, val ratio: Double)

    private companion object {
        val APPEARANCES = listOf(ThemeScheme.Light, ThemeScheme.Dark)

        /** The widest initials a real name makes, the narrowest, and the overflow chip. */
        val CASES = listOf("AR", "A", "+2")

        /** Draws no glyph, so the twin frame is the disc alone. */
        const val BLANK = " "

        val PITCH: Dp = 32.dp

        /** The rim hairline, ~1dp, rounded to one pixel: see [Window.interiorOffsets]. */
        const val RIM_INSET_PX = 1

        const val HUNDREDTHS = 100
        const val TEXT_CONTRAST_MINIMUM = 4.5
    }
}