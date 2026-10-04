package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `TTStoryPoints` keeps 23.4.2's two promises: it is a numeral in the caption style, and it
 * does not take the card's tint.
 *
 * ## Why the tint promise needs pixels
 *
 * "Does not take the card's tint" is structural by construction — there is no `tint`
 * parameter, so no call site *can* pass one. That is the right way to build it, and it is
 * also not a test: a parameter can be added, and a component can pick up a tint without one
 * being passed in. So the assertion that survives is the rendered one: over a card tinted
 * hard with the accent, the ink the rasteriser produced must be the app's label ink and must
 * **not** be the accent. That test goes red the moment somebody threads a tint through, and
 * it goes red on the pixels rather than on a source-level reading of a signature.
 *
 * ## Why the contrast assertion needs pixels as well
 *
 * A board card is glass, and `GlassSurfacePixelTest` records the fill as a translucent wash
 * that ramps down the card. A number sitting on that is "a value composited under another
 * translucent layer" — the case `AGENTS.md`'s table sends to pixels unconditionally. So the
 * background is sampled out of the rendered frame and the ink out of the rendered frame, and
 * nothing here is modelled.
 */
@OptIn(ExperimentalTestApi::class)
class StoryPointsTest {

    // MARK: Structural — the style, read off the component's own resolution

    @Test
    fun `an estimate is drawn in the caption style and not in a body or a headline`() {
        assertEquals(
            ttTypography.caption.fontSize,
            ttStoryPointsStyle(5).fontSize,
            "a five-point estimate is ${ttStoryPointsStyle(5).fontSize} rather than " +
                "caption's ${ttTypography.caption.fontSize}. 23.4.2 asked for the numeral to " +
                "be a caption so fifty of them are a metadata row rather than a wall of badges.",
        )
        assertEquals(
            ttTypography.caption.fontSize,
            ttStoryPointsStyle(1).fontSize,
            "the size is the reading's role, not the number's magnitude — a 1-point estimate " +
                "and a 13-point one are both metadata",
        )
    }

    @Test
    fun `an estimate uses monospaced digits so a changing count does not reflow the row`() {
        // `9` becoming `10` while the user watches it on a card they are re-ranking must not
        // slide the key beside it, and a proportional "10" is wider than a proportional "9".
        assertEquals(
            "tnum",
            ttStoryPointsStyle(5).fontFeatureSettings,
            "the estimate's digits are not monospaced, so a count changing from 9 to 10 " +
                "reflows the metadata row it sits in",
        )
    }

    @Test
    fun `an unestimated card reads a step below an estimated one`() {
        // Both by size and by ink. A dash drawn in the same weight as the number it stands in
        // for makes "nobody has said" look like an answer.
        assertNotEquals(
            ttStoryPointsStyle(5).fontSize,
            ttStoryPointsStyle(null).fontSize,
            "the unestimated reading is the same size as a real estimate, so the two are " +
                "indistinguishable at a glance on a board",
        )
        assertEquals(
            ttTypography.captionSmall.fontSize,
            ttStoryPointsStyle(null).fontSize,
            "the unestimated reading should be captionSmall, the scale's densest role",
        )
    }

    @Test
    fun `the estimate is text over nothing rather than text inside a pill`() {
        // 23.4.2's actual decision, measured rather than asserted from the signature. There is
        // no `Background` semantics property in Compose Multiplatform 1.12.1, so "there is no
        // fill behind this" is not readable off the semantics tree — it has to come out of the
        // frame.
        //
        // The measurement is *ink coverage*: what fraction of the component's own bounds is
        // painted at all. A numeral covers roughly a sixth of its box; a filled capsule covers
        // essentially all of it. The gap between those two is enormous and the threshold is
        // nowhere near either, so this does not depend on the font, the density or the
        // rasteriser.
        for ((scheme, name) in appearances) {
            val coverage = requireNotNull(inkCoverage(scheme)) { NO_READING }
            println("$name: the estimate's glyphs cover ${coverage.asPercent()} of its own box")
            assertTrue(
                coverage <= MAX_INK_COVERAGE,
                "$name appearance: ${coverage.asPercent()} of TTStoryPoints' own bounds are " +
                    "painted, so there is a filled shape behind the numeral — it has become " +
                    "the pill 23.4.2 rejected. Fifty of those across a board column is a wall " +
                    "of capsules, and the eye reads a wall of capsules as fifty statuses " +
                    "rather than fifty quantities.",
            )
        }
    }

    // MARK: Rendered — the ink on a card, on both rasterisers

    @Test
    fun `an estimate keeps the text bar over a glass card in both appearances`() {
        for ((scheme, name) in appearances) {
            val reading = requireNotNull(measure(scheme, tint = null)) { NO_READING }
            println(
                "$name: the estimate rendered ${reading.ink.hex()} on the card's " +
                    "${reading.fill.hex()}, ${reading.ratio.ratioText()}:1",
            )
            assertTrue(
                reading.ratio >= TEXT_CONTRAST_MINIMUM,
                "$name appearance: the estimate rendered ${reading.ink.hex()} on the glass " +
                    "card's ${reading.fill.hex()}, which is ${reading.ratio.ratioText()}:1, " +
                    "below the ${TEXT_CONTRAST_MINIMUM}:1 a ${ttTypography.caption.fontSize} " +
                    "numeral needs. This is the composite the app ships — a caption over a " +
                    "translucent fill — so it has to be measured rather than modelled.",
            )
        }
    }

    @Test
    fun `an estimate takes the card's tint on neither of them`() {
        // Rendered twice over the *same* tinted card, and the ink must not move. A component
        // that took the tint would produce the accent's own colour here, and a plain
        // source-level reading of the signature would not notice it had.
        for ((scheme, name) in appearances) {
            val plain = requireNotNull(measure(scheme, tint = null)) { NO_READING }
            val tinted = requireNotNull(measure(scheme, tint = true)) { NO_READING }
            println(
                "$name: ink ${plain.ink.hex()} on the plain card, ${tinted.ink.hex()} on " +
                    "the accent-tinted one",
            )
            assertEquals(
                plain.ink.hex(),
                tinted.ink.hex(),
                "$name appearance: tinting the card changed the estimate's ink from " +
                    "${plain.ink.hex()} to ${tinted.ink.hex()}. The component takes no tint " +
                    "parameter, so this can only happen if it is reading the card's colour " +
                    "itself — which is the thing 23.4.2 forbids.",
            )
        }
    }

    @Test
    fun `an estimate is never painted in the accent over a tinted card`() {
        // The blunt form of the assertion above, and the one that survives a rasteriser
        // disagreeing by a shade: whatever the ink is, it is not the accent.
        for ((scheme, name) in appearances) {
            val reading = requireNotNull(measure(scheme, tint = true)) { NO_READING }
            val accent = reading.accent
            val distance = channelDistance(reading.ink, accent)
            println(
                "$name: the estimate rendered ${reading.ink.hex()}, which is $distance from " +
                    "the accent ${accent.hex()}",
            )
            assertTrue(
                distance >= ACCENT_DISTANCE_FLOOR,
                "$name appearance: the estimate rendered ${reading.ink.hex()} on an " +
                    "accent-tinted card, only $distance from the accent ${accent.hex()}. " +
                    "An estimate that took the card's tint would paint every number in a " +
                    "board the colour of the column it sits in, and a quantity would read as " +
                    "a status.",
            )
        }
    }

    // MARK: Measurement

    /** What fraction of the component's own bounds is painted, rather than left as the page. */
    private fun inkCoverage(scheme: ThemeScheme): Double? {
        var coverage: Double? = null

        rasterising {
            setContent {
                ttTheme(scheme) {
                    Box(Modifier.fillMaxSize().background(ttCanvas())) {
                        ttGlassCard(tint = null)
                    }
                }
            }
            waitForIdle()
            val frame = onRoot().captureToImage().toPixelMap()
            val card = onNodeWithTag(CARD_TAG).getBoundsInRoot()
            val text = onNodeWithTag(TTStoryPointsTag).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            val scale = frame.width.toFloat() / max(root.right.value, 1f)

            val background = pixelAt(
                pixels = frame,
                x = (card.left.value + (card.right.value - card.left.value) / 2f) * scale,
                y = (card.top.value + 6f) * scale,
            )
            val left = (text.left.value * scale).toInt()
            val top = (text.top.value * scale).toInt()
            val right = (text.right.value * scale).toInt()
            val bottom = (text.bottom.value * scale).toInt()

            var painted = 0
            var total = 0
            for (y in top.coerceAtLeast(0)..bottom.coerceAtMost(frame.height - 1)) {
                for (x in left.coerceAtLeast(0)..right.coerceAtMost(frame.width - 1)) {
                    total += 1
                    if (channelDistance(frame[x, y], background) >= PAINTED_FLOOR) {
                        painted += 1
                    }
                }
            }
            if (total > 0) coverage = painted.toDouble() / total
        }

        return coverage
    }

    private class Reading(
        val ink: Color,
        val fill: Color,
        val accent: Color,
        val ratio: Double,
    )

    private fun measure(scheme: ThemeScheme, tint: Boolean?): Reading? {
        var ink: Color? = null
        var fill: Color? = null
        var accent: Color? = null

        rasterising {
            setContent {
                ttTheme(scheme) {
                    // No density override, unlike `GlassSurfacePixelTest`'s rim measurement,
                    // and the reason is worth recording because it cost a diagnostic.
                    //
                    // `getBoundsInRoot()` reports each node in *that node's own* density. The
                    // root is measured at the host's density 1, so overriding `LocalDensity`
                    // to 2 made the card and the text report half their pixel coordinates
                    // while the root kept reporting its own — and `scale = frame.width /
                    // root.right` then silently mapped the text's box onto the wrong half of
                    // the frame. The scan came back reading the card's own fill gradient as
                    // if it were the numeral's ink, and reported a 1.56:1 "contrast" for
                    // black-on-white text.
                    //
                    // A `caption` numeral at density 1 is 14 device pixels tall and does have
                    // fully-covered interior pixels, so the override bought nothing here.
                    Box(Modifier.fillMaxSize().background(ttCanvas())) {
                        ttGlassCard(tint = tint)
                        accent = ttAccent()
                    }
                }
            }
            waitForIdle()
            val frame = onRoot().captureToImage().toPixelMap()
            val card = onNodeWithTag(CARD_TAG).getBoundsInRoot()
            val text = onNodeWithTag(TTStoryPointsTag).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            val scale = frame.width.toFloat() / max(root.right.value, 1f)

            // The fill, from the card's own horizontal centre near its top edge.
            //
            // The *centre*, not the top-left corner: the card is rounded at
            // [ttRadius.surface] (16dp), so a sample 4dp in from the corner lands outside the
            // rounded rect entirely and reads the canvas behind it — which in light appearance
            // is the same white the glass composites to, so the mistake is invisible there and
            // shows up only as a 0% ink coverage in dark.
            //
            // And near the top edge rather than the centre, because the fill ramps from its
            // strongest tint at the top to [ttGlass.FILL_FADE] toward the canvas at the bottom.
            // A caption in a card's metadata row sits near the top, so that is the case.
            val background = pixelAt(
                pixels = frame,
                x = (card.left.value + (card.right.value - card.left.value) / 2f) * scale,
                y = (card.top.value + 6f) * scale,
            )
            fill = background
            ink = strongestPixel(
                pixels = frame,
                left = (text.left.value * scale).toInt(),
                top = (text.top.value * scale).toInt(),
                right = (text.right.value * scale).toInt(),
                bottom = (text.bottom.value * scale).toInt(),
                background = background,
            )
        }

        val measuredInk = ink ?: return null
        val measuredFill = fill ?: return null
        val measuredAccent = accent ?: return null
        return Reading(
            ink = measuredInk,
            fill = measuredFill,
            accent = measuredAccent,
            ratio = contrastRatio(measuredInk, measuredFill),
        )
    }

    @Composable
    private fun ttGlassCard(tint: Boolean?) {
        // Modifier order is load-bearing and was the whole of the first version's failure:
        // `padding` *before* `ttGlassCard` puts the padding outside the glass, so the card
        // shrink-wraps to the numeral and the background sample — taken from the card's own
        // top-left — lands in the padding rather than on the fill. The card then measured the
        // canvas and the ink measured the canvas, and the test reported 1:1.
        //
        // Glass first, then padding *inside* it: the card is a real surface with the numeral
        // somewhere in the middle of it, which is where a board card puts it.
        Box(
            Modifier
                .testTag(CARD_TAG)
                .fillMaxWidth()
                .ttGlassCard(tint = tint?.let { if (it) ttAccent() else null })
                .padding(horizontal = 120.dp, vertical = 120.dp),
        ) {
            TTStoryPoints(points = 5)
        }
    }

    /**
     * How far the estimate's ink has to be from the accent to count as "not the tint".
     *
     * Not zero, because a rasteriser does not reproduce a token exactly: an antialiased edge
     * blends toward the fill, and the strongest pixel in the glyph is the one nearest full
     * coverage. 0.20 across the largest channel is well inside what two genuinely different
     * colours can be at partial coverage, and well outside "the same colour composited over
     * two backgrounds".
     */
    private companion object {
        const val ACCENT_DISTANCE_FLOOR = 0.20f
        const val CARD_TAG = "design.storyPoints.card"

        /**
         * How far from the card's own fill a pixel has to be to count as painted.
         *
         * Below the glyph's antialiased edge and above the glass fill's own gradient stepping,
         * so a genuine glyph pixel counts and a rounding difference in the wash does not.
         */
        const val PAINTED_FLOOR = 0.08f

        /**
         * The largest share of its own box a numeral may cover.
         *
         * A filled capsule covers very nearly 100%. A `caption` numeral measures well under a
         * third at every density, so this sits between the two by a wide margin on purpose:
         * the threshold is meant to be one no plausible font and no plausible glyph could
         * reach without the shape actually being a shape.
         */
        const val MAX_INK_COVERAGE = 0.45

        val appearances = listOf(
            ThemeScheme.Light to "light",
            ThemeScheme.Dark to "dark",
        )

        /** A coverage figure as a percentage, for the failure message. */
        fun Double.asPercent(): String = "${(this * 100).toInt()}%"

        const val NO_READING =
            "this host rasterises, so a null reading means the measurement broke - the pixel " +
                "read returned nothing and the test would otherwise pass unmeasured"
    }
}