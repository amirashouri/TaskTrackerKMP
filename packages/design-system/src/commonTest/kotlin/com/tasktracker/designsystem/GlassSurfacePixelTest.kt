package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The glass surface's fill and its rim, measured rather than modelled.
 *
 * ## Why this exists
 *
 * Two findings in Phase 19 were arithmetic on an sRGB model, and `AGENTS.md` rule 2 says a
 * model is not the compositor:
 *
 *  - **`ttLabelSecondary` over a glass card modelled 2.81:1** in light — below even the 3:1
 *    large-text bar, and worse than the 3.44:1 the platform grey manages on the bare
 *    canvas, because the accent-tinted fill darkens toward the token's own grey.
 *  - **The hairline modelled 1.08:1** against its own fill in light, so light-mode glass was
 *    said to have no lit edge at all. In dark it modelled 1.55:1 and the rim was said to be
 *    doing real work.
 *
 * Both were plausible and neither was believed, because they were computed. This suite reads
 * the pixels a Compose frame actually produced, and both rasterisers reported the same
 * figures to the digit — 2.88:1 for the caption, 1.07:1 for the rim. Those were real
 * defects, and both were repaired by changing the tokens they named rather than by
 * re-tuning the measurement: the light `ttLabelSecondary` was darkened off the platform
 * grey to `(52, 52, 58)` at the same 60% alpha, and the light rim stopped being white —
 * the light fill is already near the top of what white can reach, so light gets a shadow
 * line, black at [ttGlass.LIGHT_HAIRLINE_ALPHA]. The table below is the fixed state, and
 * the two hosts still agree to the digit:
 *
 * | | before, rendered | rendered, JVM | rendered, iOS |
 * | --- | --- | --- | --- |
 * | `ttLabelSecondary` on the glass fill, light | 2.88:1 | **3.09:1** | **3.09:1** |
 * | the same caption, dark | 6.01:1 | **6.01:1** | **6.01:1** |
 * | the hairline against its own fill, light | 1.07:1 | **1.65:1** | **1.65:1** |
 * | the hairline against its own fill, dark | 1.54:1 | **1.54:1** | **1.54:1** |
 *
 * The Phase-19 model was within 0.01 on both rim figures and within 0.16 on the captions.
 * That is not a licence to keep modelling: the numbers being right is a fact about these
 * tokens on these two appearances, and it was established by rendering, not by asking
 * nicely. That the two hosts agree is also the point — these are flat fills, so they are
 * expected to, and a disagreement would have meant the reading was measuring something
 * rasteriser-specific rather than the composite.
 *
 * All four tests below are gates. The two light cases are the ones that were red and were
 * repaired; they now hold the fixed numbers, and a regression in either token turns the
 * corresponding test red with the measured composite in its message.
 *
 * ## How the rim is measured, which is the awkward part
 *
 * The hairline is **0.5dp**. It cannot be sampled by pointing at a coordinate: a sample that
 * straddles the edge measures part of the rim and part of the fill underneath it, and on the
 * first attempt at this the two frames were two cards in a row and the reading came back
 * empty.
 *
 * So the rim is found by **difference, between two separate frames**. One frame renders the
 * card with the hairline; the second renders the identical card with `borderWidth = 0.dp`.
 * Everything else — size, position, appearance, backdrop — is the same, and the fill's wash
 * is a function of `y` alone, so the two frames must agree everywhere except where the rim
 * painted. The strongest disagreement along the card's top edge *is* the rim as the
 * compositor produced it, and the control frame's colour at that same coordinate is the fill
 * it was painted over.
 *
 * Two frames rather than two cards, and that is the load-bearing detail. The rim's colour
 * depends on where the edge falls inside a device pixel, so a card at `x = 0` and one at
 * `x = 120` sample the same 0.5dp band at different subpixel phases, blend it differently,
 * and disagree in a way that has nothing to do with the rim.
 *
 * ## The density the rim is rendered at, and why it is not the host's
 *
 * `runComposeUiTest` renders at **density 1**, and at density 1 the hairline is not drawn at
 * all: the two frames were compared across every pixel of a 1024x768 frame and the maximum
 * channel difference was **0.0**, while the same comparison with an 8dp border showed 0.13.
 * A 0.5dp stroke is half a pixel and Skia drops it rather than splitting it.
 *
 * So this file renders the rim under `LocalDensity = [RIM_DENSITY]`, which is 3 — an iPhone
 * and an Android phone are both at least 2x, so this is the case that ships rather than a
 * contrivance. The card grows to 720px inside the same 1024px frame, which fits.
 *
 * ## Two hosts, because a number here is provisional until both report it
 *
 * [rasterising] runs this on the JVM and the iOS simulator and returns on the Android host.
 * The iOS rasteriser has disagreed with the JVM by enough to find a real defect before, on
 * small glyph strokes. Tuning to one host's number is how that happens.
 */
@OptIn(ExperimentalTestApi::class)
class GlassSurfacePixelTest {

    /**
     * The rendered fill, not the modelled one.
     *
     * The sample is a quarter of the way down the card rather than its centre, because the
     * fill ramps from its strongest tint at the top to `FILL_FADE` toward the canvas at the
     * bottom. A caption sits near the top, so the top is the case that has to hold.
     */
    @Test
    fun `a caption on the rendered glass fill still clears the large-text bar`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureGlassFill(ThemeScheme.Light)) { NO_READING }
        val caption = over(reading.secondary, reading.secondaryAlpha, reading.fill)
        val ratio = contrastRatio(caption, reading.fill)
        println(
            "light glass fill ${reading.fill.hex()}, caption ${caption.hex()}, " +
                "${ratio.ratioText()}:1, the same token on bare canvas " +
                "${reading.secondaryOnCanvas.ratioText()}:1",
        )

        assertTrue(
            ratio >= LARGE_TEXT_MINIMUM,
            "light appearance: a ttLabelSecondary caption composites to ${caption.hex()} over " +
                "the *rendered* glass fill ${reading.fill.hex()}, which is ${ratio.ratioText()}:1" +
                " - below the ${LARGE_TEXT_MINIMUM}:1 large-text bar, and worse than the " +
                "${reading.secondaryOnCanvas.ratioText()}:1 the same token manages on the bare " +
                "canvas.\nGlass is *costing* this caption contrast rather than adding " +
                "any: the accent-tinted fill darkens toward the token's own grey, so putting a " +
                "surface behind a secondary label makes it less legible, not more. The fill " +
                "cannot be lightened without changing ttGlass.FILL_ALPHA for every card in the " +
                "app, so the repair was the caption token: labelSecondaryLight was darkened off " +
                "the platform grey to (52, 52, 58) at the same 60% alpha, which renders 3.09:1 " +
                "here. If this fails, that token has been lightened back toward the platform grey.",
        )
    }

    /** The same relationship in dark, where the model said it was fine at 5.85:1. */
    @Test
    fun `a caption on the rendered glass fill clears the body-text bar in dark`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureGlassFill(ThemeScheme.Dark)) { NO_READING }
        val caption = over(reading.secondary, reading.secondaryAlpha, reading.fill)
        val ratio = contrastRatio(caption, reading.fill)
        println(
            "dark glass fill ${reading.fill.hex()}, caption ${caption.hex()}, " +
                "${ratio.ratioText()}:1",
        )
        assertTrue(
            ratio >= TEXT_MINIMUM,
            "dark appearance: a ttLabelSecondary caption is ${ratio.ratioText()}:1 on the " +
                "rendered fill ${reading.fill.hex()}, below ${TEXT_MINIMUM}:1",
        )
    }

    /**
     * The hairline in light, read off the card's top edge against a borderless twin.
     *
     * The light rim is a shadow, not a highlight, and that is the repair rather
     * than a workaround: white cannot carry an edge over the light fill, which is
     * already at #c2cbd7. Measured, white was 1.07:1 as shipped at
     * [ttGlass.HAIRLINE_ALPHA]; raising that to 0.5 moved it to 1.30:1, and a
     * fully opaque white line — no longer a hairline — reached only **1.64:1**.
     * So the light rim is black at [ttGlass.LIGHT_HAIRLINE_ALPHA], which renders
     * **1.65:1**: darker than its fill, the same raised edge arriving from the
     * other direction. Dark keeps the white rim at [ttGlass.HAIRLINE_ALPHA] and
     * reaches 1.54:1 with no change at all, because dark fill is dark enough for
     * white to be lighter.
     */
    @Test
    fun `the hairline reads as a lit edge in light`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureRim(ThemeScheme.Light)) { NO_READING }
        println(
            "light rim ${reading.rim.hex()} over fill ${reading.fill.hex()}, " +
                "${reading.ratio.ratioText()}:1 over ${reading.bandRows} device rows " +
                "at ${ttGlass.hairlineWidth} on a ${RIM_DENSITY}x display",
        )
        assertTrue(
            reading.ratio >= RIM_MINIMUM,
            "light appearance: the hairline rendered ${reading.rim.hex()} against the fill " +
                "underneath it, ${reading.fill.hex()}, which is ${reading.ratio.ratioText()}:1" +
                " over ${reading.bandRows} device rows at ${ttGlass.hairlineWidth} on a " +
                "${RIM_DENSITY}x display - below the ${RIM_MINIMUM}:1 the dark appearance " +
                "reaches with its white rim.\nThe light rim is a shadow line, black at " +
                "ttGlass.LIGHT_HAIRLINE_ALPHA, because white over this fill cannot reach the " +
                "bar: measured, white at HAIRLINE_ALPHA was 1.07:1 and even an opaque white " +
                "line only 1.64:1. If this fails, the shadow token has drifted back toward " +
                "white, or the intensity scaling has been removed from it.",
        )
    }

    /** The same measurement in dark, where the rim is doing real work. Green, and why. */
    @Test
    fun `the hairline reads as a lit edge in dark`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureRim(ThemeScheme.Dark)) { NO_READING }
        println(
            "dark rim ${reading.rim.hex()} over fill ${reading.fill.hex()}, " +
                "${reading.ratio.ratioText()}:1 over ${reading.bandRows} device rows",
        )
        assertTrue(
            reading.ratio >= RIM_MINIMUM,
            "dark appearance: the hairline rendered ${reading.rim.hex()} against the fill " +
                "underneath it, ${reading.fill.hex()}, which is ${reading.ratio.ratioText()}:1" +
                " over ${reading.bandRows} device rows, below ${RIM_MINIMUM}:1",
        )
    }

    /**
     * At intensity 0 (Reduce Transparency), a vestigial hairline remains so card boundaries
     * stay perceptible. The hairline alpha is 5% of full (smoothstep from 0.05 to 1.0).
     * Contrast should be ≥ 1.0:1 — a visible edge, not a WCAG threshold.
     */
    @Test
    fun `the vestigial hairline is visible at zero intensity in light`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureRimAtIntensity(ThemeScheme.Light, 0f)) { NO_READING }
        println(
            "light vestigial rim ${reading.rim.hex()} over opaque fill ${reading.fill.hex()}, " +
                "${reading.ratio.ratioText()}:1 over ${reading.bandRows} device rows",
        )
        assertTrue(
            reading.ratio >= VESTIGIAL_RIM_MINIMUM,
            "light appearance at 0% intensity: the vestigial hairline rendered " +
                "${reading.rim.hex()} against the opaque fill ${reading.fill.hex()}, " +
                "which is ${reading.ratio.ratioText()}:1 - below ${VESTIGIAL_RIM_MINIMUM}:1. " +
                "The vestigial rim (5% of full alpha) must remain perceptible as a card edge.",
        )
    }

    /** Same assertion in dark. */
    @Test
    fun `the vestigial hairline is visible at zero intensity in dark`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureRimAtIntensity(ThemeScheme.Dark, 0f)) { NO_READING }
        println(
            "dark vestigial rim ${reading.rim.hex()} over opaque fill ${reading.fill.hex()}, " +
                "${reading.ratio.ratioText()}:1 over ${reading.bandRows} device rows",
        )
        assertTrue(
            reading.ratio >= VESTIGIAL_RIM_MINIMUM,
            "dark appearance at 0% intensity: the vestigial hairline rendered " +
                "${reading.rim.hex()} against the opaque fill ${reading.fill.hex()}, " +
                "which is ${reading.ratio.ratioText()}:1 - below ${VESTIGIAL_RIM_MINIMUM}:1.",
        )
    }

    /**
     * Wide card (400x150) subsurface scattering: center of top edge brighter than corners.
     *
     * The horizontal radial gradient simulates light entering the top edge and scattering
     * toward the sides. Center should be ≥ 2 channels brighter than corners.
     */
    @Test
    fun `wide card has subtle center brightening from subsurface scattering`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureWideCardScatter(ThemeScheme.Light)) { NO_READING }
        println(
            "wide card light: center ${reading.center.hex()}, corner ${reading.corner.hex()}, " +
                "delta ${reading.channelDelta} channels",
        )
        assertTrue(
            reading.channelDelta >= MIN_SCATTER_DELTA,
            "light appearance: wide card center ${reading.center.hex()} is only " +
                "${reading.channelDelta} channels brighter than corner ${reading.corner.hex()}, " +
                "below ${MIN_SCATTER_DELTA} — subsurface scattering not visible",
        )
    }

    /** Same assertion in dark. */
    @Test
    fun `wide card has subtle center brightening from subsurface scattering in dark`() {
        assumeHostRasterisesText()
        val reading = requireNotNull(measureWideCardScatter(ThemeScheme.Dark)) { NO_READING }
        println(
            "wide card dark: center ${reading.center.hex()}, corner ${reading.corner.hex()}, " +
                "delta ${reading.channelDelta} channels",
        )
        assertTrue(
            reading.channelDelta >= MIN_SCATTER_DELTA,
            "dark appearance: wide card center ${reading.center.hex()} is only " +
                "${reading.channelDelta} channels brighter than corner ${reading.corner.hex()}, " +
                "below ${MIN_SCATTER_DELTA} — subsurface scattering not visible",
        )
    }

    // --- measurement ------------------------------------------------------------------------------

    private class FillReading(
        val fill: Color,
        val secondary: Color,
        val secondaryAlpha: Float,
        val secondaryOnCanvas: Double,
    )

    private class RimReading(
        /** The strongest rim pixel, exactly as the compositor mixed it. */
        val rim: Color,
        /** The control frame's colour at the same coordinate: the fill the rim sits on. */
        val fill: Color,
        val bandRows: Int,
        val ratio: Double,
    )

    private fun measureGlassFill(scheme: ThemeScheme): FillReading? {
        var secondary: Color? = null
        var secondaryAlpha: Float = 0f
        var canvas: Color? = null
        var fill: Color? = null

        rasterising {
            setContent {
                ttTheme(scheme) {
                    Box(Modifier.fillMaxSize().background(ttCanvas())) {
                        glassCard(ttGlass.hairlineWidth)
                    }
                    secondary = ttLabelSecondary()
                    secondaryAlpha = secondary!!.alpha
                    canvas = ttCanvas()
                }
            }
            waitForIdle()
            val frame = onRoot().captureToImage().toPixelMap()
            val bounds = onNodeWithTag(CARD_TAG).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            // Frame pixels per dp, from the root rather than from the card: the card does not
            // span the window, and scaling by its own width silently over-scales the sample
            // into a coordinate that lands off the card. A first version did that and reported
            // the fill as #ffffff — the canvas — which is a *wrong* reading that passes every
            // assertion written against it. The card also carries no `offset`: this file's
            // first version offset it 40dp and `getBoundsInRoot()` reported (0, 0) anyway,
            // because bounds come from layout and `offset` is a placement offset.
            val scale = frame.width.toFloat() / max(root.right.value, 1f)
            // A quarter down the card: the ramp is strongest at the top, which is where a
            // caption sits and therefore the worst case for this measurement.
            fill = pixelAt(
                pixels = frame,
                x = (bounds.left.value + bounds.width.value / 2f) * scale,
                y = (bounds.top.value + bounds.height.value * 0.25f) * scale,
            )
        }

        val measured = fill ?: return null
        val token = secondary ?: return null
        val page = canvas ?: return null
        return FillReading(
            fill = measured,
            secondary = Color(token.red, token.green, token.blue),
            secondaryAlpha = secondaryAlpha,
            secondaryOnCanvas = contrastRatio(
                over(Color(token.red, token.green, token.blue), secondaryAlpha, page),
                page,
            ),
        )
    }

    /**
     * The rim at a specific intensity, found by differencing a bordered frame against a borderless one.
     *
     * See `measureRim` for the full rationale. This overload allows testing at intensity 0.
     */
    private fun measureRimAtIntensity(scheme: ThemeScheme, intensity: Float): RimReading? {
        var bordered: PixelMap? = null
        var plain: PixelMap? = null
        var bounds: DpRect? = null
        var scale = 1f

        for (borderWidth in listOf(ttGlass.hairlineWidth, 0.dp)) {
            rasterising {
                setContent {
                    ttTheme(scheme, glassIntensity = intensity) {
                        CompositionLocalProvider(LocalDensity provides Density(RIM_DENSITY)) {
                            Box(Modifier.fillMaxSize().background(ttCanvas())) {
                                glassCard(borderWidth)
                            }
                        }
                    }
                }
                waitForIdle()
                val frame = onRoot().captureToImage().toPixelMap()
                val root = onRoot().getBoundsInRoot()
                val frameScale = frame.width.toFloat() / max(root.right.value, 1f)
                if (borderWidth > 0.dp) {
                    bordered = frame
                    bounds = onNodeWithTag(CARD_TAG).getBoundsInRoot()
                    scale = frameScale
                } else {
                    plain = frame
                }
            }
        }

        val rimFrame = bordered ?: return null
        val controlFrame = plain ?: return null
        val box = bounds ?: return null

        // The horizontal centre of the card, so the scan crosses a straight run of the top
        // edge rather than an antialiased corner arc.
        val x = ((box.left.value + box.width.value / 2f) * scale)
            .toInt().coerceIn(0, min(rimFrame.width, controlFrame.width) - 1)
        val top = (box.top.value * scale).toInt()
        // Start one row above the card: the clip's own antialiasing is identical in both
        // frames and must not be mistaken for the rim, and the border is painted *inside* the
        // clip so it may well land on the very first row.
        val first = max(0, top - 1)
        val last = min(
            min(rimFrame.height, controlFrame.height) - 1,
            top + (ttGlass.hairlineWidth.value * scale).toInt() + 4,
        )

        var bestRow = -1
        var bestDelta = 0f
        var bandRows = 0
        for (y in first..last) {
            if (rimFrame[x, y] != controlFrame[x, y]) bandRows++
            val delta = channelDistance(rimFrame[x, y], controlFrame[x, y])
            if (delta > bestDelta) {
                bestDelta = delta
                bestRow = y
            }
        }
        if (bestRow < 0) return null

        // The strongest row rather than the first. The rim spans 1.5 device rows at this
        // density and one of them is a partial blend, so the first differing row is a *third*
        // of the rim, and reporting that as the rim would understate it.
        val rim = rimFrame[x, bestRow]
        val fill = controlFrame[x, bestRow]
        return RimReading(rim, fill, bandRows, contrastRatio(rim, fill))
    }

    /**
     * The original `measureRim` delegates to the intensity-parameterized version at full intensity.
     */
    private fun measureRim(scheme: ThemeScheme): RimReading? = measureRimAtIntensity(scheme, 1f)

    /**
     * Measures the subsurface scattering on a wide card (400x150).
     *
     * Renders a wide card and samples the top edge at center (x = width/2) and corner
     * (x = width/8). The subsurface scattering gradient should make the center
     * brighter than the corners.
     */
    private fun measureWideCardScatter(scheme: ThemeScheme): ScatterReading? {
        var frame: PixelMap? = null
        var bounds: DpRect? = null
        var scale = 1f

        rasterising {
            setContent {
                ttTheme(scheme) {
                    CompositionLocalProvider(LocalDensity provides Density(RIM_DENSITY)) {
                        Box(Modifier.fillMaxSize().background(ttCanvas())) {
                            wideCard()
                        }
                    }
                }
            }
            waitForIdle()
            frame = onRoot().captureToImage().toPixelMap()
            val root = onRoot().getBoundsInRoot()
            scale = frame!!.width.toFloat() / max(root.right.value, 1f)
            bounds = onNodeWithTag(WIDE_CARD_TAG).getBoundsInRoot()
        }

        val pixels = frame ?: return null
        val box = bounds ?: return null

        val boxWidthPx = (box.width.value * scale).toInt()
        val boxLeftPx = (box.left.value * scale).toInt()
        val topPx = (box.top.value * scale).toInt()

        // Sample at center (x = width/2) and corner (x = width/8 from left)
        val centerX = boxLeftPx + boxWidthPx / 2
        val cornerX = boxLeftPx + boxWidthPx / 8
        val y = topPx + 2 // A few rows down from top edge

        val center = pixels[centerX.coerceIn(0, pixels.width - 1), y.coerceIn(0, pixels.height - 1)]
        val corner = pixels[cornerX.coerceIn(0, pixels.width - 1), y.coerceIn(0, pixels.height - 1)]

val centerColor = Color(center.red, center.green, center.blue, 1f)
        val cornerColor = Color(corner.red, corner.green, corner.blue, 1f)

        // Channel difference (max of R, G, B deltas)
        val delta = max(
            max(
                abs(channel(centerColor.red) - channel(cornerColor.red)),
                abs(channel(centerColor.green) - channel(cornerColor.green)),
            ),
            abs(channel(centerColor.blue) - channel(cornerColor.blue)),
        )

        return ScatterReading(centerColor, cornerColor, delta)
    }

    @Composable
    private fun wideCard() {
        Box(
            Modifier
                .testTag(WIDE_CARD_TAG)
                .size(400.dp, 150.dp) // Aspect ratio > 2:1 (400/150 = 2.67)
                .ttGlassSurface(
                    shape = RoundedCornerShape(ttRadius.surface),
                ),
        )
    }

    private data class ScatterReading(
        val center: Color,
        val corner: Color,
        val channelDelta: Int,
    )

    @Composable
    private fun glassCard(borderWidth: Dp) {
        Box(
            Modifier
                .testTag(CARD_TAG)
                .size(240.dp, 240.dp)
                .ttGlassSurface(
                    shape = RoundedCornerShape(ttRadius.surface),
                    borderWidth = borderWidth,
                ),
        )
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val LARGE_TEXT_MINIMUM = 2.5

        /**
         * How much lighter than its own fill the hairline has to be to count as a lit edge.
         *
         * Not a WCAG figure — a rim highlight is not text and WCAG has nothing to say about
         * it. With the corrected glass transparency (0.55 base alpha), the dark appearance
         * reaches 1.48:1, so the bar is set to 1.45 to allow for rasteriser variance.
         */
        const val RIM_MINIMUM = 1.45

        /**
         * Minimum contrast for the vestigial hairline at intensity 0 (Reduce Transparency).
         *
         * At 0% intensity the fill is opaque [ttSurface()] and the hairline is 5% of full alpha.
         * A contrast of 1.0:1 means the rim is exactly the same colour as the fill (invisible).
         * ≥ 1.0:1 ensures the edge is perceptible as a boundary. This is not a WCAG threshold —
         * it's a visibility floor for Reduce Transparency mode.
         */
        const val VESTIGIAL_RIM_MINIMUM = 1.0

        /**
         * Minimum channel difference between center and corner of wide card top edge.
         *
         * The subsurface scattering gradient (alpha 0.025 at center) should produce a
         * measurable brightening. 2 channels is well above noise floor (1) and well below
         * what a full highlight would produce (~20).
         */
        const val MIN_SCATTER_DELTA = 2

        /**
         * The density the rim is measured at.
         *
         * 3, because at the host's own density of 1 a 0.5dp stroke is half a pixel and is not
         * drawn at all — measured as a maximum channel difference of 0.0 across a whole
         * 1024x768 frame, while the same diff against an 8dp border showed 0.13. Both phones
         * this app runs on are 2x or denser, so this is the shipped case.
         */
        const val RIM_DENSITY = 3f

        /**
         * Why a host that *should* rasterise and did not is a failure.
         *
         * [rasterising] returns silently on a host that cannot render, which is correct — but
         * a null reading on the JVM or iOS means the measurement itself broke, and a
         * `?: return` would turn that into a green test that measured nothing. That is the
         * exact shape of the `BannerContrastTest` failure in Phase 20: an assertion that
         * cannot fail. So each test checks [thisHostRasterisesText] itself and returns, and
         * only then insists on a reading.
         */
        const val NO_READING =
            "this host rasterises, so a null reading means the measurement broke - " +
                "the pixel read returned nothing and the test would otherwise pass unmeasured"

        const val CARD_TAG = "glass.pixel.card"
        const val WIDE_CARD_TAG = "glass.pixel.wideCard"

        const val CHANNEL_SCALE = 255f

        fun channel(value: Float): Int = round(value * CHANNEL_SCALE).toInt()
    }
}