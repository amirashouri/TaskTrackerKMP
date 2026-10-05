package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which chevron is in which slot, and how big it is drawn — measured in pixels.
 *
 * ## Why this cannot be a structural test
 *
 * [TabIconsGeometryTest] proves `chevronLeft` points left. It says nothing about which of the
 * two slots that glyph ends up in, and **that is the binding that was wrong**. `TTDayGrid` names
 * its arrows `TTMonthStepTag(-1)` and `TTMonthStepTag(1)`, and the tag is built from the
 * `step` argument — so swapping `glyph = chevronLeft` and `glyph = chevronRight` at the call
 * site leaves both tags, both click handlers, and every one of the eight tests in
 * [DateFieldInteractionTest] passing, while the left arrow draws a right-pointing chevron and
 * the calendar's arrows read backwards.
 *
 * A position assertion does not close that gap either: the slots stay where they were. There
 * is no way to read "which vector did this node draw" off the semantics tree — the painter is
 * not in the node — so the claim has to be made against rendered ink, which is also what
 * `AGENTS.md` says about silhouettes. Per that table, a direction claim is a pixel claim.
 *
 * ## What is measured
 *
 * Each arrow's 44dp box is located from the semantics tree, so the pixel window is the arrow's
 * own box and nothing else can contribute ink to it. Inside that window the **centroid** of the
 * ink is compared with the box's centre:
 *
 *  * a left-pointing chevron puts its ink to the left of its box's centre, and
 *  * a right-pointing one puts it to the right.
 *
 * The centroid is used rather than the bounding box because the bounding box is *symmetric* —
 * a chevron is symmetric about its own axis, so ink extent says nothing about which way it
 * points. The centroid does: the apex is the thick end of the mark and the terminals are cut
 * square, so the ink leans towards the point.
 *
 * Measured on both rasterisers, which agree to the pixel: the previous arrow's ink is centred
 * at x=19.5 in a box centred on 22.0, and the next arrow's at 1003.5 against 1002.0. Those
 * margins are 2.5px and 1.5px — thin, and deliberately documented rather than widened. A
 * chevron drawn backwards measures about 5px the *other* way, so the separation between right
 * and wrong is 5px with the truth in the middle. That is the trade: too thin and an
 * anti-aliasing change produces a false red, too coarse and the assertion stops being about
 * direction at all. The numbers are in the failure message so a false red is diagnosable
 * rather than mysterious.
 *
 * ## Why the background is found rather than assumed
 *
 * The ink ceiling is derived from the window's own modal luminance, not from a hard-coded
 * "white". This grid is drawn over a glass surface whose base is `ttSurface()` at
 * `1f - LENS_ALPHA`, and a future appearance change would move it. `TabIconsRenderTest` can
 * hard-code white because it lays its own `PAPER` down; this test renders the real header and
 * inherits whatever the theme put there.
 */
@OptIn(ExperimentalTestApi::class)
class MonthArrowRenderTest {

    @Test
    fun `the previous arrow's ink leans left and the next arrow's leans right`() = rasterising {
        renderHeader()

        val pixels = onRoot().captureToImage().toPixelMap()
        val previous = slotInk(pixels, step = -1)
        val next = slotInk(pixels, step = 1)

        assertTrue(
            previous.centroidX < previous.centreX,
            "the previous-month arrow's ink is centred at x=${previous.centroidX} in a box " +
                "centred on ${previous.centreX} (extent ${previous.left}..${previous.right}, " +
                "${previous.count} px, background ${previous.background}). Ink to the RIGHT of " +
                "the box's centre means the chevron in this slot points right — the calendar's " +
                "arrows are backwards, which is the defect this test exists for",
        )
        assertTrue(
            next.centroidX > next.centreX,
            "the next-month arrow's ink is centred at x=${next.centroidX} in a box centred on " +
                "${next.centreX} (extent ${next.left}..${next.right}, ${next.count} px, " +
                "background ${next.background}). Ink to the LEFT of the box's centre means the " +
                "chevron in this slot points left — the calendar's arrows are backwards",
        )
    }

    /**
     * The mark is a good deal smaller than the target it sits in.
     *
     * 44dp is the tap target and it must stay that; the *glyph* is not supposed to fill it. It
     * used to: `MonthStepGlyph` sized the `Image` to `ttSpacing.iconButtonSize`, so a chevron
     * spanning 18 of its vector's 24 units rendered 33dp across inside a 44dp box — three
     * quarters of the target. Two of those flanking a month name read as two slabs, not as
     * navigation.
     *
     * The bound is on the ink's **span** against the box's span, which is the ratio a viewer
     * actually sees, and it is density-independent. Both numbers below are *measured*, and the
     * first version of this assertion used 0.55 on the strength of a geometric guess of 0.36
     * and 0.75 — which put the bound **above** the oversized arrow and let it pass. The ink
     * threshold stops short of the antialiased edge, so what lands here is about two thirds of
     * the drawn extent: 11px of 44px at [dateFieldChevronSize] and 22px of 44px at
     * `ttSpacing.iconButtonSize`, or 0.25 against 0.50. The bound sits between them.
     */
    @Test
    fun `the arrows are drawn well inside their 44dp targets`() = rasterising {
        renderHeader()

        val pixels = onRoot().captureToImage().toPixelMap()
        for (step in listOf(-1, 1)) {
            val ink = slotInk(pixels, step)
            assertTrue(
                ink.count > 0,
                "the $step arrow painted nothing at all in its ${ink.boxWidth}px box: a glyph " +
                    "with path nodes but no fill brush occupies its layout and draws nothing",
            )
            val span = (ink.right - ink.left).toFloat()
            val fraction = span / ink.boxWidth
            assertTrue(
                fraction < 0.35f,
                "the $step arrow's ink spans ${span.roundedTo(1)}px of its " +
                    "${ink.boxWidth.roundedTo(1)}px target — ${fraction.roundedTo(2)} of it. " +
                    "The glyph is meant to read as an affordance inside the tap target, not to " +
                    "fill it; 0.25 is the 22dp draw mark and 0.50 is the oversized one this " +
                    "replaced",
            )
        }
    }

    /**
     * The real header, with the arrows where a user sees them.
     *
     * `TTDayGrid` is composed directly rather than through `TTDateField` because this is where
     * the glyph-to-slot binding is written, and because composing it here keeps every node in
     * the *main* root — so `boundsInRoot` and `captureToImage()` share one coordinate space. A
     * `Popup` is a second composition root, and mapping between its coordinates and the
     * captured image is exactly the kind of guesswork that turns a measurement into a fiction.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.renderHeader() {
        setContent {
            ttTheme(ThemeScheme.Light) {
                // Paper, not the theme's window background. The capture comes back transparent
                // wherever nothing is painted, which reads as luminance 0 — the same value as
                // this theme's dark ink. With no opaque backdrop the modal background *is* the
                // ink and the arrow measures as absent.
                Box(modifier = Modifier.fillMaxSize().background(PAPER)) {
                    TTDayGrid(
                        month = LocalDate(2026, Month.MARCH, 1),
                        onSelect = {},
                        today = LocalDate(2026, Month.MARCH, 16),
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun rasterising(body: androidx.compose.ui.test.ComposeUiTest.() -> Unit) {
        if (!thisHostRasterisesText()) {
            println("skipped: this host does not rasterise, so a chevron has no ink to measure")
            return
        }
        runComposeUiTest { body() }
    }

    /** The measured ink inside the arrow box tagged [TTMonthStepTag] of [step]. */
    private fun androidx.compose.ui.test.ComposeUiTest.slotInk(
        pixels: androidx.compose.ui.graphics.PixelMap,
        step: Int,
    ): Ink {
        val bounds = onNodeWithTag(TTMonthStepTag(step)).fetchSemanticsNode().boundsInRoot
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        val right = bounds.right.toInt().coerceAtMost(pixels.width - 1)
        val bottom = bounds.bottom.toInt().coerceAtMost(pixels.height - 1)

        val sampled = mutableListOf<Pair<Int, Float>>()
        for (y in top until bottom) {
            for (x in left until right) {
                val rgb = pixels[x, y]
                sampled.add(x to (rgb.red + rgb.green + rgb.blue) / 3f)
            }
        }
        val background = sampled.modalLuminance()
        val inked = sampled.filter { (_, luminance) -> luminance < background - INK_MARGIN }
        val mean = (inked.map { it.first }.average().takeIf { !it.isNaN() }
            ?: bounds.center.x.toDouble()).toFloat()
        val leftmost = inked.minOfOrNull { it.first }?.toFloat() ?: bounds.left
        val rightmost = inked.maxOfOrNull { it.first }?.toFloat() ?: bounds.right
        return Ink(
            count = inked.size,
            centroidX = mean,
            centreX = bounds.center.x,
            left = leftmost,
            right = rightmost,
            boxWidth = (right - left).toFloat(),
            background = background,
        )
    }

    /**
     * The most common luminance, quantised.
     *
     * A mode rather than a mean because the window is *mostly* background with a thin mark on
     * it: the mean sits close to the background but is dragged by whatever ink there is, which
     * is least reliable exactly when there is most ink to find.
     */
    private fun List<Pair<Int, Float>>.modalLuminance(): Float {
        val buckets = groupingBy { (_, luminance) -> (luminance * QUANTISATION).toInt() }
            .eachCount()
        val winner = buckets.maxByOrNull { it.value }?.key ?: 0
        val bucket = filter { (_, luminance) ->
            (luminance * QUANTISATION).toInt() == winner
        }
        return bucket.sumOf { it.second.toDouble() }.toFloat() / bucket.size
    }

    /**
     * This number to [decimals] places.
     *
     * `String.format` is JVM-only and Kotlin/Native has no equivalent, so a message built with
     * it compiles for `jvmTest` and breaks `iosSimulatorArm64Test`. Only the second host finds
     * that, which is the whole argument for running both.
     *
     * Truncates rather than rounds, which is fine for the two callers: both numbers are
     * non-negative measurements. Nothing else may depend on this being a rounding.
     */
    private fun Float.roundedTo(decimals: Int): String {
        var scale = 1
        repeat(decimals) { scale *= 10 }
        return "${(this * scale).toInt() / scale.toDouble()}"
    }

    private data class Ink(
        val count: Int,
        val centroidX: Float,
        val centreX: Float,
        val left: Float,
        val right: Float,
        val boxWidth: Float,
        val background: Float,
    )

    private companion object {
        /** Opaque paper, so "no ink" and "dark ink" are different numbers. */
        val PAPER = Color(0xFFFFFFFF)

        /**
         * How far below the background a pixel must be to count as ink.
         *
         * Loose on purpose, for the reason [TabIconsRenderTest] gives: single-pixel strokes and
         * antialiased edges are the mark being measured, and a tight floor would report a
         * chevron that is present as absent.
         */
        const val INK_MARGIN = 0.12f

        /** Luminance buckets for the mode. 20 bins over 0..1 is finer than a soft edge needs. */
        const val QUANTISATION = 20f
    }
}
