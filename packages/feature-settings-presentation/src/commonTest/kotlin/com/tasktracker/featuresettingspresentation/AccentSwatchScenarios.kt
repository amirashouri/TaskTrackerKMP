package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
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
 * are the same base colour. The lens is a *vertical* wash — strongest at the top of the cell,
 * nearly gone at the bottom — so on the selected swatch the two rows must differ by the lens's
 * falloff across the cell, and on an unselected swatch they must match. The sign depends on
 * the appearance (white lens in dark, black in light), so the assertion is on the magnitude.
 * The comparison needs no second composition with the selection moved, which the Android
 * host's `setContent`-once rule would otherwise force.
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

    /** The selected cell's pane covers both the swatch and the label and has a top-to-bottom wash. */
    val theSelectedAccentSwatchCarriesTheGlassLens: suspend ComposeUiTest.() -> Unit = {
        // A real skip, not a return: this test measures pixels, and a host that measured
        // nothing must not be recorded as agreeing.
        assumeHostRasterisesText()
        setContent {
                ttTheme(ThemeScheme.Light) {
                    // The app's own canvas, not a made-up one: the label's ink sits on it and its
                    // contrast is only meaningful against the surface it actually lands on.
                    Box(modifier = Modifier.fillMaxSize().background(ttCanvas())) {
                        AccentPalettePickerRow(
                            selected = AccentPalette.Slot1,
                            onSelect = {},
                        )
                    }
                }
            }
            waitForIdle()

            val frame = onRoot().captureToImage().toPixelMap()
            val root = onRoot().getBoundsInRoot()
            val scale = frame.width.toFloat() / max(root.right.value, 1f)

            val selected = onNodeWithTag(AccentSwatchTag(AccentPalette.Slot1), useUnmergedTree = true).getBoundsInRoot()
            val unselected = onNodeWithTag(AccentSwatchTag(AccentPalette.Slot3), useUnmergedTree = true).getBoundsInRoot()
            val pane = onNodeWithTag(AccentGlassTag(AccentPalette.Slot1), useUnmergedTree = true).getBoundsInRoot()
            val label = onNodeWithTag(AccentLabelTag(AccentPalette.Slot1), useUnmergedTree = true).getBoundsInRoot()

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
                abs(selectedDelta) >= LENS_DELTA_BAR,
                "the selected accent swatch's top row differs from its bottom row by only " +
                    "${abs(selectedDelta)} (sum of R+G+B) at the swatch's centre. The lens wash " +
                    "runs top to bottom and fades to a quarter, so a selected swatch must show a " +
                    "clear vertical gradient. Near zero means the lens is not painted and the " +
                    "swatch is a bare colour gradient again. The sign follows the appearance: " +
                    "the lens is white in dark and black in light, so the top is lighter in dark " +
                    "and darker here.",
            )
            assertTrue(
                abs(unselectedDelta) <= CONTROL_DELTA_BAR,
                "the unselected accent swatch's top row differs from its bottom row by " +
                    "${abs(unselectedDelta)}, but it carries no lens: its fill is a *horizontal* " +
                    "gradient, so its top and bottom rows are the same colour. Above noise " +
                    "means a lens is being drawn on an unselected swatch.",
            )
    }

    /**
     * The 3x2 grid gives every label room to read in full at the narrowest content width.
     *
     * The old six-cell row measured about 48dp per cell at 320dp and allowed two lines with
     * an ellipsis; `C-Light` was the only abbreviation in an otherwise spelled-out block. This
     * asks Compose's own text layout whether each label overflowed, rather than inspecting
     * the source's `maxLines`, because the question is the measured width at 320dp.
     *
     * It also holds the 44dp target on the whole cell, not just the 32dp swatch: the visible
     * circle is smaller than the thing a finger has to hit.
     */
    val theAccentLabelsReadInFullInAGrid: suspend ComposeUiTest.() -> Unit = {
        setContent {
            ttTheme(ThemeScheme.Light) {
                Box(modifier = Modifier.width(NARROW_CONTENT_WIDTH).background(ttCanvas())) {
                    AccentPalettePickerRow(
                        selected = AccentPalette.Slot1,
                        onSelect = {},
                    )
                }
            }
        }
        waitForIdle()

        for (palette in AccentPalette.all) {
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithTag(AccentLabelTag(palette), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val cell = onNodeWithTag(AccentCellTag(palette), useUnmergedTree = true).getBoundsInRoot()
            assertEquals(
                palette.title.length,
                layout.getLineEnd(0, visibleEnd = true),
                "${palette.title} is truncated at ${NARROW_CONTENT_WIDTH.value}dp " +
                    "(visible line ends at character ${layout.getLineEnd(0, visibleEnd = true)} " +
                    "of ${palette.title.length}, cell $cell). The grid must leave every " +
                    "palette name in full.",
            )
            assertEquals(
                1,
                layout.lineCount,
                "${palette.title} wraps onto ${layout.lineCount} lines at the narrowest width",
            )

            assertTrue(
                cell.width.value >= 44f && cell.height.value >= 44f,
                "${palette.title}'s tappable cell is ${cell.width}x${cell.height}dp, below the " +
                    "44dp target even though its 32dp swatch clears it visually",
            )
        }
    }

    /**
     * The lens still travels across the grid, including to the second row.
     *
     * The old layout was one row, so a horizontal lens animation was enough. Two rows add a
     * vertical component, and `ttLensPlacement` is now fed rectangles on both; this selects a
     * palette in the lower row and checks the pane ends up on its cell rather than partway.
     */
    val theLensTravelsToASecondRow: suspend ComposeUiTest.() -> Unit = {
        var selected by mutableStateOf(AccentPalette.Slot1)
        setContent {
            ttTheme(ThemeScheme.Light) {
                Box(modifier = Modifier.width(NARROW_CONTENT_WIDTH)) {
                    AccentPalettePickerRow(
                        selected = selected,
                        onSelect = { selected = it },
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag(AccentCellTag(AccentPalette.Slot6)).performClick()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        val cell = onNodeWithTag(AccentCellTag(AccentPalette.Slot6), useUnmergedTree = true)
            .getBoundsInRoot()
        val pane = onNodeWithTag(AccentGlassTag(AccentPalette.Slot6), useUnmergedTree = true)
            .getBoundsInRoot()
        assertTrue(
            pane.left.value <= cell.left.value + 0.5f &&
                pane.top.value <= cell.top.value + 0.5f &&
                pane.right.value >= cell.right.value - 0.5f &&
                pane.bottom.value >= cell.bottom.value - 0.5f,
            "after selecting ${AccentPalette.Slot6.title} the lens pane ($pane) did not settle on its cell " +
                "($cell). Two rows mean the lens has a vertical leg; a pane that stops on the " +
                "first row means the placement still assumes one.",
        )
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

    /** The narrowest content width the appearance block is laid out for. */
    private val NARROW_CONTENT_WIDTH = 320.dp

    /** Backdrop-noise headroom for the control row, which has no vertical variation at all. */
    private const val CONTROL_DELTA_BAR = 6

    /**
     * The wash's sign is the appearance's, not the test's: `ttGlassLens` draws white in dark and
     * black in light, so this light-appearance scenario measures a *negative* delta. The bar is
     * on the magnitude for that reason.
     *
     * The lens's top stop is [com.tasktracker.designsystem.ttGlass.SOLID_LENS_ALPHA] (0.45) in
     * dark, but light caps the effective wash at 0.14 of black and spreads it over the whole
     * cell. Across the 32dp swatch near the top, JVM and iOS both measure **13** on this
     * 0…765 scale; the bar at 10 leaves headroom above the unselected control's 0 while still
     * failing if the pane stops being painted. (The previous bar of 30 assumed a white wash and
     * was already red before the grid change.)
     */
    private const val LENS_DELTA_BAR = 10

    private fun channelSum(color: Color): Int {
        fun byte(v: Float) = (v * 255f).toInt()
        return byte(color.red) + byte(color.green) + byte(color.blue)
    }
}
