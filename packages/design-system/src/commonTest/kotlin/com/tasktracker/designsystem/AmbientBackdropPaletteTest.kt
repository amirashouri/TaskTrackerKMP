package com.tasktracker.designsystem

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The second ambient glow has to belong to the palette, not to the design system.
 *
 * The audit found `Color.Blue` hardcoded at 10% in the backdrop, so all six palettes kept
 * the same blue cast in the corner the glass samples. This renders the real
 * [AmbientBackdrop] under two palettes and reads the bottom-right corner, where the second
 * circle's falloff lands and the first circle does not reach.
 *
 * ## The circle used to be off-canvas
 *
 * At the old centre `(1.45w, 1.55h)` and a `0.35w` radius, the second circle's nearest edge
 * was `1.10w`: it painted no pixel at all, which is why the hardcoded blue never showed.
 * The test would have read identical corners under every palette and the defect would have
 * looked fixed. Part of the change moved the centre to `(1.05w, 1.10h)`; this test is what
 * makes that placement a contract rather than a coincidence.
 *
 * The delta is read against [Classic] rather than against an absolute blue, so a future
 * accent change does not invalidate the test — only a backdrop that stops reading the
 * accent does. The Classic corner itself is also checked for a cool cast, which is the
 * property the hardcoded blue had and the derivation has to keep.
 */
@OptIn(ExperimentalTestApi::class)
class AmbientBackdropPaletteTest {

    @Test
    fun `the secondary corner changes with the accent palette`() {
        val frames = mutableMapOf<AccentPalette, PixelMap>()
        for (palette in listOf(AccentPalette.Classic, AccentPalette.Sunset)) {
            rasterising {
                setContent {
                    ttTheme(ThemeScheme.Light, palette) {
                        AmbientBackdrop()
                    }
                }
                waitForIdle()
                frames[palette] = onRoot().captureToImage().toPixelMap()
            }
        }

        val classic = requireNotNull(frames[AccentPalette.Classic]) { "no Classic frame" }
        val sunset = requireNotNull(frames[AccentPalette.Sunset]) { "no Sunset frame" }
        val width = classic.width
        val height = classic.height

        // Three points inside the second circle's falloff and outside the first's. Reading a
        // few rather than one covers the rounded corner and the edge row.
        val points = listOf(
            (width - 3) to (height - 3),
            (width - 12) to (height - 8),
            (width * 0.95f).toInt() to (height * 0.92f).toInt(),
        )
        val delta = points.maxOf { (x, y) -> channelDistance(classic[x, y], sunset[x, y]) }
        println(
            "backdrop corner: Classic ${classic[width - 3, height - 3].hex()}, " +
                "Sunset ${sunset[width - 3, height - 3].hex()}, max delta $delta",
        )

        assertTrue(
            delta > 0.01f,
            "the bottom-right corner is identical to within $delta under Classic and Sunset. " +
                "The second glow is not derived from LocalAccentPalette, or its falloff does " +
                "not reach the canvas at all (the old centre of 1.45w/1.55h with a 0.35w " +
                "radius painted nothing).",
        )

        val corner = classic[width - 3, height - 3]
        assertTrue(
            corner.blue > corner.red,
            "Classic's secondary corner is ${corner.hex()}, which is not cool. Classic's " +
                "accent hue is 213 degrees; the +25 degree derivation lands on 238, within " +
                "two degrees of the blue it replaced.",
        )
    }
}
