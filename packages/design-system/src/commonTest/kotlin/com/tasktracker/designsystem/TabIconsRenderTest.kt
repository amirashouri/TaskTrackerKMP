package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves each tab glyph *paints*, which is a different claim from the one
 * [TabIconsGeometryTest] proves.
 *
 * ## Why this suite had to exist
 *
 * Every glyph in [ttTabIcons] was invisible. Each occupied its full layout box and drew zero
 * pixels, so the tab bar rendered as four bare captions — and the whole suite stayed green,
 * because `TabIconsGeometryTest` asserts properties that were still true of an invisible
 * glyph: the path had nodes, the nodes were inside the viewport, the glyphs filled the grid
 * together, the gear really had a hole. None of those can see that nothing is painted, and
 * that file says so itself: it does not rasterise.
 *
 * The cause was a missing fill brush on `ImageVector.Builder.addPath`. Path data with no brush
 * is not drawn at all, and `ttIcon` tints with `ColorFilter.tint`, which is `SrcIn` — it keeps
 * the source and discards the rest, so a source with no alpha leaves nothing to keep. The
 * colour in [ttTabIcons] is a means to an end; the tint is what is actually seen.
 *
 * So this asserts the only thing that would have caught it: ink on the screen.
 */
@OptIn(ExperimentalTestApi::class)
class TabIconsRenderTest {

    @Test
    fun everyGlyphPaintsInk() = rasterising {
        val glyphs = listOf(
            "projects" to ttTabIcons.projects,
            "board" to ttTabIcons.board,
            "backlog" to ttTabIcons.backlog,
            "settings" to ttTabIcons.settings,
        )

        setContent {
            Box(Modifier.fillMaxSize().background(PAPER)) {
                glyphs.forEachIndexed { index, (_, image) -> Swatch(index, image) }
            }
        }
        waitForIdle()

        val pixels = onRoot().captureToImage().toPixelMap()
        val span = SWATCH.value.toInt()
        val step = span + GAP.value.toInt()
        glyphs.forEachIndexed { index, (name, _) ->
            val left = index * step
            val ink = (0 until span).sumOf { y ->
                (0 until span).count { x ->
                    val rgb = pixels[left + x, y]
                    (rgb.red + rgb.green + rgb.blue) / 3 < INK_CEILING
                }
            }
            assertTrue(
                ink > 0,
                "ttTabIcons.'$name' painted nothing: $ink ink pixels in a ${SWATCH} box on " +
                    "white. A glyph with path nodes but no fill brush occupies its layout and " +
                    "draws nothing, which is exactly what this assertion exists to catch",
            )
        }
    }

    /**
     * One glyph, in a known-size box at a known offset, so the pixel window is deterministic.
     *
     * Positioned by explicit `offset` rather than laid out in a `Row` on purpose: a row's
     * spacing is a gap *between* children, so the probe would have to re-derive the stride from
     * the layout it is trying to measure. Here the stride is arithmetic.
     */
    @Composable
    private fun Swatch(index: Int, image: ImageVector) {
        Box(
            modifier = Modifier
                .offset(x = (SWATCH + GAP) * index)
                .size(SWATCH)
                .background(PAPER),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(ICON)) {
                ttIcon(image = image, contentDescription = null, tint = INK)
            }
        }
    }

    private companion object {
        /** White paper, so any ink is unambiguous. */
        val PAPER = Color(0xFFFFFFFF)

        /** Near-black ink, the colour [ttIcon] is asked to tint with. */
        val INK = Color(0xFF000000)

        /**
         * A pixel counts as inked when its channels average below this.
         *
         * On [androidx.compose.ui.graphics.Color]'s 0-to-1 scale, not 0-to-255: this number
         * was originally 200, which is above the largest value the channel can hold, so every
         * pixel in the frame — white paper included — counted as ink and the assertion below
         * could not fail. A threshold that is never false is not a loose threshold, it is no
         * threshold, and it is worth writing down that this one is reachable.
         *
         * Otherwise loose on purpose: antialiased edges and single-pixel strokes must count,
         * because the failure being guarded is *total*. Demanding solid coverage would turn a
         * future antialiasing change into a false alarm and invite someone to loosen this
         * until it proved nothing again.
         */
        const val INK_CEILING = 0.8f

        val SWATCH: Dp = 64.dp
        val ICON: Dp = 24.dp
        val GAP: Dp = 4.dp
    }
}
