package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttTheme
import kotlin.math.max
import kotlin.test.assertTrue

/**
 * The accent picker's selected cell carries one glass pane over both the colour swatch *and* the
 * label beneath it — the segmented picker and tab bar's selection treatment, raised to
 * [com.tasktracker.designsystem.ttGlass.SOLID_LENS_ALPHA] because a flat saturated swatch, unlike
 * a glass slab, has nothing else to carry the "this is glass" reading.
 *
 * ## Why this is measured and not read off the modifier chain
 *
 * "The selected cell applies [com.tasktracker.designsystem.ttGlassLens]" is spelled in the
 * code, but spelling is not drawing. The lens is a translucent white wash whose only effect is
 * on the *pixels* the colour gradient produces, and a source-level assertion could not tell it
 * apart from one the compositor dropped — the case the repo's pixel rule exists for
 * (`rendered-ui-measurement`). The swatch has a tag ([AccentSwatchTag]) so the frame and the
 * bounds share one coordinate space, which is what keeps this from being a coordinate guess.
 *
 * ## The vertical delta is the lens, and the base colour cancels out
 *
 * Each swatch is a *horizontal* colour gradient, so at any one column its top and bottom rows
 * are the same base colour. The lens is a *vertical* white wash — strongest at the top of the
 * cell, nearly gone at the bottom — so on the selected swatch the top row must read lighter than
 * the bottom row by the lens's falloff across the cell, and on an unselected swatch the two rows
 * must match. The comparison needs no second composition with the selection moved, which the
 * Android host's `setContent`-once rule would otherwise force.
 *
 * ## Coverage is structural, not measured
 *
 * That the pane spans *both* the swatch and the label is a fact about bounds, not about ink, so
 * it is asserted structurally from the pane's tag ([AccentGlassTag]) against the swatch's and the
 * label's ([AccentLabelTag]). A pixel test cannot prove it cleanly — the top of the cell sits over
 * the colour gradient and the bottom over the label on the page, so a top-versus-bottom sample
 * conflates the pane's edge with a change of base.
 *
 * ## The label shares the tab bar's selection ink
 *
 * The pane covers the label, so its wash composites over the glyphs as well as the page and pulls
 * the ink toward the wash. To absorb that, the label takes [com.tasktracker.designsystem.ttSelectionLabelInk]
 * — the same selected/inactive ink the bottom navigation resolves — so the selected label is
 * white in Dark and a darkened accent in Light, rather than the raw accent that fell under 4.5:1
 * once the pane sat over it. Measured on the app's own canvas, the selected label's darkest ink
 * against its lightest page reads 6.96:1 in Light and 15.33:1 in Dark. The shared token's own
 * numbers, on the tab bar's glass — a harder backdrop than the canvas — are held on both
 * rasterisers by `TabLensContrastTest`.
 */
@OptIn(ExperimentalTestApi::class)
object AccentSwatchScenarios {

    /** The selected cell's pane is lighter at the top and covers both the swatch and the label. */
    val theSelectedAccentSwatchCarriesTheGlassLens: suspend ComposeUiTest.() -> Unit = {
        if (!thisHostRasterisesText()) {
            println("skipped: the Android host rasterises differently, so the lens is not measured there")
        } else {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    // The app's own canvas, not a made-up one: the label's ink sits on it and its
                    // contrast is only meaningful against the surface it actually lands on.
                    Box(modifier = Modifier.fillMaxSize().background(ttCanvas())) {
                        AccentPalettePickerRow(
                            selected = AccentPalette.Classic,
                            onSelect = {},
                        )
                    }
                }
            }
            waitForIdle()

            val frame = onRoot().captureToImage().toPixelMap()
            val root = onRoot().getBoundsInRoot()
            val scale = frame.width.toFloat() / max(root.right.value, 1f)

            val selected = onNodeWithTag(AccentSwatchTag(AccentPalette.Classic), useUnmergedTree = true).getBoundsInRoot()
            val unselected = onNodeWithTag(AccentSwatchTag(AccentPalette.Music), useUnmergedTree = true).getBoundsInRoot()
            val pane = onNodeWithTag(AccentGlassTag(AccentPalette.Classic), useUnmergedTree = true).getBoundsInRoot()
            val label = onNodeWithTag(AccentLabelTag(AccentPalette.Classic), useUnmergedTree = true).getBoundsInRoot()

            val selectedDelta = topToBottomDelta(frame, selected, scale)
            val unselectedDelta = topToBottomDelta(frame, unselected, scale)

            assertTrue(
                pane.top <= selected.top && pane.bottom >= label.bottom &&
                    pane.left <= selected.left && pane.right >= selected.right,
                "the selected cell's glass pane ($pane) does not cover both the swatch ($selected) " +
                    "and the label below it ($label). The pane is a sibling drawn after the swatch " +
                    "and the label, sized to the whole cell, so it must reach above the swatch's " +
                    "top edge and below the label's bottom edge.",
            )
            assertTrue(
                selectedDelta >= LENS_DELTA_BAR,
                "the selected accent swatch's top row measured only $selectedDelta lighter than " +
                    "its bottom row (sum of R+G+B). The lens is a white wash that is strongest " +
                    "at the top of the cell and fades to a quarter at the bottom, so a selected " +
                    "swatch must read lighter at the top by a clear margin. Near zero means the " +
                    "lens is not painted and the swatch is a bare colour gradient again.",
            )
            assertTrue(
                unselectedDelta <= CONTROL_DELTA_BAR,
                "the unselected accent swatch's top row measured $unselectedDelta lighter than " +
                    "its bottom row, but it carries no lens: its fill is a *horizontal* " +
                    "gradient, so its top and bottom rows are the same colour. Above noise " +
                    "means a lens is being drawn on an unselected swatch.",
            )
        }
    }

    /**
     * The difference between the swatch's top and bottom rows, as a sum of R+G+B, sampled one
     * column at the swatch's horizontal centre.
     *
     * The sample is inset [SAMPLE_INSET] from each edge so the unselected swatch's own separator
     * border — drawn along its perimeter — is not read as a wash. At the horizontal centre the
     * circle is at its full 32dp height, so those rows are inside the silhouette at any density.
     * The pane's white rim is at the *cell's* perimeter, well outside the swatch, so it is not a
     * concern here.
     */
    private fun topToBottomDelta(frame: PixelMap, bounds: DpRect, scale: Float): Int {
        val x = (((bounds.left.value + bounds.right.value) / 2f) * scale).toInt()
            .coerceIn(0, frame.width - 1)
        val top = ((bounds.top.value + SAMPLE_INSET) * scale).toInt().coerceIn(0, frame.height - 1)
        val bottom = ((bounds.bottom.value - SAMPLE_INSET) * scale).toInt().coerceIn(0, frame.height - 1)
        return channelSum(frame[x, top]) - channelSum(frame[x, bottom])
    }

    private const val SAMPLE_INSET = 4f

    /** Backdrop-noise headroom for the control row, which has no vertical variation at all. */
    private const val CONTROL_DELTA_BAR = 6

    /**
     * The solid-fill lens's top stop is [com.tasktracker.designsystem.ttGlass.SOLID_LENS_ALPHA]
     * (0.45) of white and its bottom stop a quarter of that, spread over the whole *cell* — so
     * across the 32dp swatch, which sits near the top, the measured lift is smaller than when the
     * lens hugged the circle. The JVM paints the selected Classic swatch at a lift of 62 on this
     * 0…765 scale. The bar sits at half that, well clear of the unselected control's 0, and a
     * revert to the tab-strength lens (0.14) would measure ~19 and fall short.
     */
    private const val LENS_DELTA_BAR = 30

    private fun channelSum(color: Color): Int {
        fun byte(v: Float) = (v * 255f).toInt()
        return byte(color.red) + byte(color.green) + byte(color.blue)
    }
}
