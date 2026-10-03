package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the glass slider actually puts on screen, at full travel, at the bottom, and at the 20%
 * mark that was reported.
 *
 * ## Why this has to be rendered
 *
 * The slider's claim about its bottom end is that `0f` is Reduce Transparency — a *solid*
 * neutral surface — rather than no glass, a hole, or an opaque accent. That claim is not visible
 * in any token. `ttGlass.fillRamp` returning `alpha = 1f` is the arithmetic; what the user sees is
 * that arithmetic after the compositor has blended it over a real backdrop, and the two are
 * different questions.
 *
 * ## The defect this file originally asserted as correct
 *
 * The first version of this test measured the bottom of the travel against the **accent** and
 * passed. It was passing on the wrong premise, and the whole feature was wrong with it: fading
 * only the fill's *alpha* toward 1 makes every surface an opaque slab of the accent, so
 * lowering the slider a little darkened the app and cast it navy. The test was green *because* the
 * implementation tinted, and it read that as evidence the tint was intended.
 *
 * Two assertions had to be discarded rather than re-tuned. `zeroToCanvas > zeroToAccent * 4`
 * inferred opacity from a colour's distance to the page, which only works while the fill is the
 * accent; against an opaque neutral it fails by arithmetic, because a solid neutral is *supposed*
 * to sit near the page it was taken from. Opacity is now established the direct way — an opaque
 * fill of a solid colour lands on that colour, so the pixel must equal the neutral — and
 * see-through-ness is established by ordering, not by proximity.
 *
 * ## Why all three panels are on one frame
 *
 * The panels are siblings in one composition rather than separate renders, so they cannot be
 * composited against different backdrops or different appearances. The failure this guards against
 * is a measurement taken against a canvas that moved between frames, which would make the
 * difference look like the slider's doing.
 *
 * ## Why the numbers are flat areas and not glyphs
 *
 * Every window here is the centre of a large rectangle with a square shape, so there is no
 * antialiased edge in the measurement. That is deliberate: the JVM and the iOS simulator disagree
 * about glyph strokes by enough to matter (see `rendered-ui-measurement`), and a test that
 * depended on their agreement would be a test about rasteriser coverage wearing a measurement's
 * clothes. Flat fills agree, so the tolerance here is tight and the two hosts should both clear it.
 */
@OptIn(ExperimentalTestApi::class)
class GlassIntensityRenderTest {

    @Test
    fun theBottomOfTheSliderIsAnOpaqueNeutralRatherThanAbsent() = rasterising {
        for (scheme in APPEARANCES) {
            var reading: Reading? = null
            runComposeUiTest { reading = measure(scheme) }
            val measured = requireNotNull(reading) { "no reading in ${scheme.name} appearance" }

            assertTrue(
                measured.zeroToSurface <= OPAQUE_TOLERANCE,
                "at 0 glass intensity the surface painted ${measured.zero.hex()} but the app's " +
                    "neutral is ${measured.surface.hex()}, which is " +
                    "${measured.zeroToSurface.inChannels()} channels away. The bottom of the " +
                    "slider must be an opaque fill of the neutral — a " +
                    "translucent fill cannot land on it, and a hole in the page would land on " +
                    "${measured.canvas.hex()} instead. Check the cross-fade in ttGlass.fillRamp " +
                    "and LocalGlassIntensity",
            )
            assertTrue(
                measured.zeroToAccent >= CAST_FLOOR,
                "at 0 intensity the surface reads as ${measured.zero.hex()}, only " +
                    "${measured.zeroToAccent.inChannels()} channels from the accent " +
                    "reducing transparency is amplifying the accent instead of removing it, " +
                    "which is the colour cast this feature must not have",
            )
            assertTrue(
                measured.fifthToSurface < measured.fifthToAccent,
                "at 20% intensity the surface reads as ${measured.fifth.hex()}, " +
                    "${measured.fifthToSurface.inChannels()} channels from the neutral " +
                    "${measured.fifthToAccent.inChannels()} channels from the accent " +
                    "crossed to the accent side of the neutral while the user has only lowered " +
                    "the slider a fifth of the way. That is the setting that was reported as " +
                    "making the app dark and saturated",
            )
            assertTrue(
                measured.fullToZero > TOLERANCE,
                "moving the slider from 0 to full glass changed the surface by only " +
                    "${measured.fullToZero.inChannels()} channels (${measured.full.hex()} against " +
                    "${measured.zero.hex()}) in ${scheme.name} appearance, which is within " +
                    "rasteriser noise — the control would have no visible effect",
            )
            assertTrue(
                measured.fullSeesThePage,
                "at full intensity the surface's red channel is ${hex(measured.full.red)} " +
                    "against an accent at ${hex(measured.accent.red)} and a page at " +
                    "${hex(measured.canvas.red)}, so it is not between the two: a see-through " +
                    "fill has to be a blend of the accent and the page behind it, and this one is " +
                    "not showing the page",
            )
        }
    }

    /**
     * Reads the centre of each panel out of one frame.
     *
     * `RectangleShape` rather than a rounded one so no corner arc can reach the window, and the
     * centre rather than the edge so the hairline — which is one of the things the slider fades
     * out — cannot be what is being measured.
     */
    private fun ComposeUiTest.measure(scheme: ThemeScheme): Reading {
        var accent = Color.Transparent
        var canvas = Color.Transparent
        var surface = Color.Transparent
        setContent {
            // One `ttTheme` per panel, not one around the row: the measurements are differences
            // between these pixels, so the intensities have to differ while the appearance and the
            // backdrop stay identical. A single provider around all three panels renders them at
            // the *same* intensity and the test silently measures a surface against itself.
            Row(Modifier.fillMaxSize()) {
                ttTheme(scheme, glassIntensity = 1f) {
                    accent = ttAccent()
                    canvas = ttCanvas()
                    surface = ttSurface()
                    panel(FULL_TAG, canvas)
                }
                ttTheme(scheme, glassIntensity = 0.2f) {
                    panel(FIFTH_TAG, canvas)
                }
                ttTheme(scheme, glassIntensity = 0f) {
                    panel(ZERO_TAG, canvas)
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage()
        val pixels = frame.toPixelMap()
        val root = onRoot().getBoundsInRoot()
        val scale = frame.width.toFloat() / max(root.right.value, 1f)

        val full = centreOf(onNodeWithTag(FULL_TAG).getBoundsInRoot(), scale, pixels)
        val fifth = centreOf(onNodeWithTag(FIFTH_TAG).getBoundsInRoot(), scale, pixels)
        val zero = centreOf(onNodeWithTag(ZERO_TAG).getBoundsInRoot(), scale, pixels)

        return Reading(
            full = full,
            fifth = fifth,
            zero = zero,
            surface = surface,
            zeroToSurface = distance(zero, surface),
            zeroToAccent = distance(zero, accent),
            fifthToSurface = distance(fifth, surface),
            fifthToAccent = distance(fifth, accent),
            fullToZero = distance(full, zero),
            accent = accent,
            canvas = canvas,
            // Ordering, not arithmetic. A translucent fill over the page must land strictly
            // between the two things it is blending, and nearer the page than the accent; stating
            // it as an order avoids predicting the exact alpha, which would be modelling the
            // composite in a test whose subject is the composite.
            fullSeesThePage = isBetween(full.red, accent.red, canvas.red) &&
                abs(full.red - canvas.red) < abs(full.red - accent.red),
        )
    }

    @Composable
    private fun RowScope.panel(tag: String, canvas: Color) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(canvas)
                .ttGlassSurface(RectangleShape)
                .testTag(tag),
        )
    }

    /**
     * A normalised distance as whole channels, for a failure message a person can read.
     *
     * `0.74509805` is a number nobody acts on. The distances are 0..1 floats because that is what
     * the tolerances are written in, but the tolerances are also fractions of 255, so the same
     * scale in channels costs nothing and puts the defect's size in the message.
     */
    private fun Float.inChannels(): Int = (this * 255f).toInt()

    private fun isBetween(value: Float, a: Float, b: Float): Boolean =
        value > minOf(a, b) && value < maxOf(a, b)

    private fun centreOf(bounds: DpRect, scale: Float, pixels: PixelMap): Color {
        val cx = ((bounds.left.value + bounds.width.value / 2f) * scale)
            .toInt().coerceIn(0, pixels.width - 1)
        val cy = ((bounds.top.value + bounds.height.value / 2f) * scale)
            .toInt().coerceIn(0, pixels.height - 1)
        return pixels[cx, cy]
    }

    private fun distance(a: Color, b: Color): Float = max(
        max(abs(a.red - b.red), abs(a.green - b.green)),
        abs(a.blue - b.blue),
    )

    private data class Reading(
        val full: Color,
        val fifth: Color,
        val zero: Color,
        /** The app's neutral, captured inside the same theme as the panels it is compared to. */
        val surface: Color,
        val zeroToSurface: Float,
        val zeroToAccent: Float,
        val fifthToSurface: Float,
        val fifthToAccent: Float,
        val fullToZero: Float,
        /** The accent and canvas the frame was composed against, carried for the message. */
        val accent: Color,
        val canvas: Color,
        val fullSeesThePage: Boolean,
    )

    private companion object {
        const val FULL_TAG = "glassIntensity.full"
        const val FIFTH_TAG = "glassIntensity.fifth"
        const val ZERO_TAG = "glassIntensity.zero"

        val APPEARANCES = listOf(ThemeScheme.Light, ThemeScheme.Dark)

        val CANVAS_LIGHT = Color(0xFFFAFAFA)

        /**
         * Two channels out of 255.
         *
         * Flat fills are exact in principle and agree in practice across these two hosts; the
         * slack is for the compositor's own rounding of an alpha blend, not for rasteriser
         * disagreement, which is why this is tighter than the tolerance a glyph test needs.
         */
        const val TOLERANCE = 2f / 255f

        /** An opaque fill of a solid colour must land on that colour. */
        const val OPAQUE_TOLERANCE = 3f / 255f

        /**
         * How close the bottom of the travel may get to the accent and still be called neutral:
         * a twelfth of the range, about 21 channels.
         *
         * The defect landed the bottom of the slider *on* the accent, so this is nowhere near a
         * judgement call about how much tint is acceptable. It exists to catch a partial fade
         * that gets most of the way back, which is what a broken interpolation of the colour
         * looks like when only one of the two channels in it is right.
         */
        const val CAST_FLOOR = 21f / 255f
    }
}

/**
 * `#rrggbb`, written without [String.format].
 *
 * `format` is a JVM-flavoured convenience that does not resolve the same way on Kotlin/Native,
 * and a hex dump that only compiles on one of the three hosts is a hex dump two hosts cannot
 * print in a failure message — which is the only place it is used.
 */
private fun Color.hex(): String {
    fun channel(value: Float): String =
        (value * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

/** One channel of a [Color], hex, for a failure message that has to name the mix. */
private fun hex(value: Float): String = (value * 255f).toInt().coerceIn(0, 255).toString(16)
    .padStart(2, '0')