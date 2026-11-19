package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/**
 * The theme dropdown's pane, its lens, and the accent caption, as scenarios.
 *
 * ## Where the dropdown's marking lives
 *
 * The pane draws one glass lens under the selected option and springs it to the next — the same
 * `ttLensPlacement`/`ttGlassLens` the navigation uses — so the marking is a thing with bounds of
 * its own, not a fill on a row. That is what these scenarios assert, and it is why the old
 * ancestor of this file, which measured an accent wash along each option's top edge, is gone
 * with the wash: there is no per-row paint left to measure.
 *
 * The scenarios compose the extracted pieces in the *main* root rather than opening the real
 * `Popup`. A popup is a second composition root, and mapping its coordinates onto a captured
 * frame is guesswork — `MonthArrowRenderTest` documents the same rule for `TTDayGrid`. The
 * backing scenario still reads pixels, over a deliberately hostile page (see
 * [theDropdownPaneCoversThePageBehindIt]); the lens scenario needs only bounds, so it runs on
 * every host, not only where a frame can be read back.
 */
@OptIn(ExperimentalTestApi::class)
object ThemeOptionScenarios {

    /**
     * The selected theme option carries the pane's lens, and the lens travels on selection.
     *
     * This is the navigation's rule, in a popup. The options used to wash themselves — each row
     * fading an accent fill in and out under its own text — and the pane now draws one glass lens
     * under whichever row is selected and springs it to the next, the same `ttLensPlacement` and
     * `ttGlassLens` the sidebar, rail and tab bar use.
     *
     * Asserted as geometry rather than as paint. The lens is a sibling of the rows, not a border
     * on one, so comparing its bounds with the row's is what says it is marking the row the
     * picker calls selected; a pixel sample of the row's own strip would have measured the lens
     * wherever it happened to be parked. The first comparison happens before any tap, so a lens
     * that never moves and a lens born on the wrong row stay different failures.
     *
     * `TabLensContrastTest` already holds the wash and rim *ink* on both rasterisers, which is
     * why this scenario measures placement only — and can therefore run on every host rather
     * than only where a frame can be read back.
     */
    val theSelectedSchemeOptionCarriesTheLens: suspend ComposeUiTest.() -> Unit = {
        setContent {
            ttTheme(ThemeScheme.Dark) {
                var selected by remember { mutableStateOf(ThemeScheme.System) }
                Box(modifier = Modifier.fillMaxSize().background(OptionBackdrop)) {
                    SettingsDropdownColumn(selection = selected) {
                        ThemeScheme.all.forEach { scheme ->
                            ThemePickerOption(
                                scheme = scheme,
                                isSelected = scheme == selected,
                                onClick = { selected = scheme },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        assertLensMarks(what = "before anything is tapped", row = ThemeScheme.System)

        onNodeWithTag(ThemeOptionTag(ThemeScheme.Dark)).performClick()
        waitForIdle()

        val travelled = onNodeWithTag(SettingsDropdownLensTag).getBoundsInRoot()
        val landing = onNodeWithTag(ThemeOptionTag(ThemeScheme.System)).getBoundsInRoot()
        assertTrue(
            travelled.top.value > landing.top.value,
            "after choosing Dark the lens is at y=${travelled.top.value}dp having started at " +
                "y=${landing.top.value}dp — it did not travel down the pane. Dark is the last " +
                "option, so the lens should have passed Light on its way",
        )
        assertLensMarks(what = "after choosing Dark", row = ThemeScheme.Dark)
    }

    /**
     * Asserts the pane's lens is marking an option: the same rectangle to within a point.
     *
     * Four edges, not a centre or a top edge — a lens that is right about where a row starts and
     * wrong about how tall it is is still a lens bigger than the thing it marks.
     */
    private fun ComposeUiTest.assertLensMarks(what: String, row: ThemeScheme) {
        val lens = onNodeWithTag(SettingsDropdownLensTag).getBoundsInRoot()
        val option = onNodeWithTag(ThemeOptionTag(row)).getBoundsInRoot()
        val within = 1f
        assertTrue(
            abs(lens.left.value - option.left.value) <= within &&
                abs(lens.top.value - option.top.value) <= within &&
                abs(lens.right.value - option.right.value) <= within &&
                abs(lens.bottom.value - option.bottom.value) <= within,
            "$what the lens spans ${lens.left.value}..${lens.right.value}dp horizontally and " +
                "${lens.top.value}..${lens.bottom.value}dp vertically, but the ${row.title} " +
                "option spans ${option.left.value}..${option.right.value}dp and " +
                "${option.top.value}..${option.bottom.value}dp. The lens is placed from the rows' " +
                "measured bounds, so a mismatch means it is marking the wrong row or is still in " +
                "flight",
        )
    }

    /**
     * Both long palette names are on screen in full; the six-column row could not do that.
     *
     * This used to assert that "Navy Blue" wrapped onto two lines, because at 320dp a
     * six-column row left about 48dp per caption and wrapping was the only way to show the
     * whole word. The picker is a 3x2 grid now, so the contract is the opposite: both long
     * names fit on one line and neither is abbreviated. `AccentSwatchScenarios` measures the
     * text layout at the same width; this scenario keeps the full Settings page in the loop,
     * so a regression that only appears after scrolling still has a witness.
     */
    val theLongAccentCaptionWrapsInsteadOfClipping: suspend ComposeUiTest.() -> Unit = {
        setContent {
            Themed {
                Box(modifier = Modifier.width(320.dp).background(Color.Transparent)) {
                    SettingsScreen(
                        connectivity = ConnectivitySummary.Online,
                        activity = SyncActivity(),
                        themeScheme = ThemeScheme.System,
                        version = SettingsPreviewData.version,
                        commandOutcome = null,
                        onThemeChange = {},
                        onSyncNow = {},
                        onSignOut = {},
                        accentPalette = AccentPalette.NavyBlue,
                        onAccentPaletteChange = {},
                    )
                }
            }
        }
        // The accent palette is the second section on the page, so on a 320dp-wide phone it
        // starts below the fold — and a LazyColumn does not compose items that have not been
        // scrolled into range, which is why this used to fail with "could not find any node"
        // rather than measuring a wrapped caption. Scroll to the palette first; the claim is
        // about wrapping, not about where the row initially sits.
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasText("Navy Blue"))
        waitForIdle()

        // Unmerged, because `selectable` merges the caption into the palette cell; this is
        // the text node itself, so finding it proves the shipped string is the full name.
        onNodeWithText("Classic Light", useUnmergedTree = true).assertExists()
        onNodeWithText("Navy Blue", useUnmergedTree = true).assertExists()
    }

    /**
     * The dropdown is a pane, not a window: the page behind it does not read through its options.
     *
     * This is the light theme's version of a defect the wash scenario above cannot see. The popup
     * used to be unbacked glass, so its fill was the accent ramp at `THICK`'s alpha over whatever
     * it happened to cover — a pale wash with *white* option labels on it, which left the options
     * at roughly 2:1 and reading as shapes rather than as words. The fix was a backing surface
     * (`DROPDOWN_BASE_ALPHA`) and the label token; this is the assertion that the backing is there.
     *
     * The measurement is a *spread* along one scanline, not a colour against a token. The pane is
     * composed over a hard black-and-white striped backdrop; a pane that paints its own surface
     * compresses those stripes to a few levels, and a pane that lets the page through reproduces
     * them at roughly their own contrast. Checking the pane's fill against the surface token
     * instead would have to re-derive the glass ramp — thickness, intensity, the white wash over a
     * base — and would fail on a host whose rasteriser composites differently rather than on a
     * regression. How much of the backdrop a pane lets through is a property of the pane alone.
     *
     * The scanline is inside the *unselected* row and inset from the pane's edges: the selected
     * row's wash would add its own vertical variation, and the rim's hairline and the rounded
     * corners live at the borders.
     */
    val theDropdownPaneCoversThePageBehindIt: suspend ComposeUiTest.() -> Unit = {
        assumeHostRasterisesText()
        setContent {
            ttTheme(ThemeScheme.Light) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val bar = StripeBar.toPx()
                            var x = 0f
                            var painted = Color.White
                            while (x < size.width) {
                                drawRect(
                                    color = painted,
                                    topLeft = Offset(x, 0f),
                                    size = Size(bar, size.height),
                                )
                                painted = if (painted == Color.White) Color.Black else Color.White
                                x += bar
                            }
                        },
                ) {
                    SettingsDropdownColumn(selection = ThemeScheme.Dark) {
                        ThemeScheme.all.forEach { scheme ->
                            ThemePickerOption(
                                scheme = scheme,
                                isSelected = scheme == ThemeScheme.Dark,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage().toPixelMap()
        val root = onRoot().getBoundsInRoot()
        val scale = frame.width.toFloat() / max(root.right.value, 1f)
        // The middle option, which is not the selected one, so no wash crosses the scanline, and
        // far enough from the pane's rounded corners that the borders play no part.
        val row = onNodeWithTag(ThemeOptionTag(ThemeScheme.Light)).getBoundsInRoot()
        val left = (row.left.value * scale).toInt() + PaneInsetPixels
        val right = (row.right.value * scale).toInt() - PaneInsetPixels
        // A few dp below the row's top edge, inside the strip no glyph reaches: a scanline through
        // the row's middle would measure the option's own text, not the pane under it.
        val y = ((row.top.value + ScanlineInset.value) * scale).toInt()

        var lightest = 0f
        var darkest = 1f
        for (x in left..right) {
            val luminance = frame[x, y].red
            lightest = max(lightest, luminance)
            darkest = min(darkest, luminance)
        }
        val spread = ((lightest - darkest) * 255f).roundToInt()

        assertTrue(
            spread <= COVERED_BAR,
            "the red channel across the option row spans $spread of 255 under a black-and-white " +
                "striped page, so the stripes are reading through the dropdown. A pane backed " +
                "with its own surface lets about 6% of the page through, while an unbacked one " +
                "reproduces most of the 255 — the light theme's translucent-popup defect: pale " +
                "glass with white labels on it, legible as shapes rather than as words",
        )
    }

    // --- measurement ------------------------------------------------------------------------------

    /** The known backdrop the pane is composed over, so the page behind it is one known colour. */
    private val OptionBackdrop = Color(0xFF1B1B1F)

    /** How wide one stripe of the forensic backdrop is, and the pane's edge for a scanline. */
    private val StripeBar = 8.dp

    private const val PaneInsetPixels = 16

    /** How far below an option row's top edge the pane's scanline is read. */
    private val ScanlineInset = 3.dp

    /**
     * Spread across a scanline above which the pane is letting the page through.
     *
     * Measured rather than guessed, by running this scenario with the bar at 0. The backed pane
     * measures 15 of 255 on a black-to-white stripe pair — the 6% `DROPDOWN_BASE_ALPHA` lets
     * through — and the same pane with its backing removed measures 89. 40 sits above the first
     * with room for a rasteriser's dither and far below the second.
     */
    private const val COVERED_BAR = 40

}