package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/**
 * The theme dropdown's selection ring and the accent caption, as scenarios.
 *
 * ## Why the ring is measured and not read off the modifier chain
 *
 * "The selected option has a width-2 accent ring" is spelled in the code, but spelling is not
 * drawing: the ring is a `border` on a row the test cannot see through semantics, and a
 * source-level assertion could not tell it apart from a ring the compositor dropped. This is
 * the case the repo's pixel rule exists for — a hairline whose absence reads as a defect
 * (`rendered-ui-measurement`). The ring lives on a `Popup` in the real screen, though, and a
 * popup is a second composition root; mapping its coordinates onto a captured frame is exactly
 * the guesswork `MonthArrowRenderTest` warns about. So the scenario does what that test does
 * for `TTDayGrid`: it composes the extracted [ThemePickerOption] in the *main* root, where
 * bounds and frame share one coordinate space, and reads the ring there.
 *
 * ## Two rows, one assertion each
 *
 * The selected option's ring is `ttAccent()` and the others' is `ttSeparator()` — the same
 * vocabulary the accent-palette circles use. Sampling only the top edge keeps the title and
 * check-mark ink out of the measurement: in the dark theme the selected title is *also* accent
 * coloured, so a whole-row sample would pass a ringless option that still showed an accent
 * title. The top four pixels of a 48dp-tall row contain no text.
 *
 * The thresholds at the bottom are generous on purpose. The ring is 2px of near-pure accent at
 * the host density against a near-black backdrop, so any count above noise is a real ring; the
 * bar is set high enough that an absent ring cannot cross it and low enough that two
 * rasterisers cannot argue about it.
 */
@OptIn(ExperimentalTestApi::class)
object ThemeOptionScenarios {

    /** The selected theme option paints an accent ring; the others paint none. */
    val theSelectedSchemeOptionDrawsAnAccentRing: suspend ComposeUiTest.() -> Unit = {
        if (!thisHostRasterisesText()) {
            println("skipped: the Android host rasterises differently from Skia, so the ring is not measured there")
        } else {
            var accent = Color.Transparent
            setContent {
                ttTheme(ThemeScheme.Dark) {
                    accent = ttAccent()
                    // Opaque paper, so the ring is read against a known backdrop instead of
                    // whatever the transparent area of a captured frame shows through to.
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1B1B1F))) {
                        Column(modifier = Modifier.width(216.dp)) {
                            ThemePickerOption(
                                scheme = ThemeScheme.System,
                                isSelected = true,
                                onClick = {},
                            )
                            ThemePickerOption(
                                scheme = ThemeScheme.Light,
                                isSelected = false,
                                onClick = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()

            val frame = onRoot().captureToImage().toPixelMap()
            val root = onRoot().getBoundsInRoot()
            val scale = frame.width.toFloat() / max(root.right.value, 1f)
            val selected = onNodeWithTag(ThemeOptionTag(ThemeScheme.System)).getBoundsInRoot()
            val unselected = onNodeWithTag(ThemeOptionTag(ThemeScheme.Light)).getBoundsInRoot()

            val selectedRing = accentPixelsAlongTopEdge(frame, selected, scale, accent, insetRows = 0)
            // The rows are flush, so the selected row's *bottom* accent ring antialiases a row
            // or two into the next row's bounds. The unselected scan starts two rows inside its
            // own edge so that bleed is not read as an accent ring on the row below it.
            val unselectedRing = accentPixelsAlongTopEdge(frame, unselected, scale, accent, insetRows = 2)

            assertTrue(
                selectedRing >= 200,
                "the selected theme option painted only $selectedRing accent-coloured pixels " +
                    "along its frame. A ringless row is indistinguishable from the glass, which " +
                    "is exactly the bug reported — the check mark and accent title alone were " +
                    "not finding the selected option. Expected a 2px accent ring across the " +
                    "216px row (roughly 430 pixels), saw $selectedRing",
            )
            assertTrue(
                unselectedRing <= 16,
                "the unselected theme option painted $unselectedRing accent-coloured pixels " +
                    "along its frame, but its ring is ttSeparator(), which is grey. An " +
                    "accent-coloured ring on every option would defeat the point of the border",
            )
        }
    }

    /**
     * The second accent palette's caption shows whole by wrapping; it was clipped at one line.
     *
     * "Classic Light" is 13 characters on a six-column row. The screen is drawn at a fixed
     * 320dp width (and no host needs to be able to see more than that — the assertion is on
     * layout bounds, which are read regardless of what is on screen), so the caption has to
     * wrap. [AccentPalette.ClassicLight] and Music are the same font size, so the ratio of
     * their text-box heights is a pure "how many lines" signal with no font or rasteriser
     * dependency: two lines against one. A truncated caption collapses back to that one line
     * and the ratio falls below the bar.
     */
    val theLongAccentCaptionWrapsInsteadOfClipping: suspend ComposeUiTest.() -> Unit = {
        setContent {
            Themed {
                Box(modifier = Modifier.width(320.dp).background(Color.Transparent)) {
                    SettingsScreen(
                        connectivity = ConnectivitySummary.Online,
                        activity = SyncActivity(),
                        isForceOffline = false,
                        themeScheme = ThemeScheme.System,
                        version = SettingsPreviewData.version,
                        commandOutcome = null,
                        onThemeChange = {},
                        onForceOfflineChange = {},
                        onSyncNow = {},
                        onSignOut = {},
                        accentPalette = AccentPalette.ClassicLight,
                        onAccentPaletteChange = {},
                    )
                }
            }
        }
        // The caption is read from the unmerged tree. `selectable` merges its descendants, so
        // text matched in the merged tree is the whole palette column (swatch + caption +
        // spacing); the unmerged text node is the caption and only the caption, whose height
        // is a pure line count. "Music" is the shortest palette title: one line at 320dp on
        // every host, so it is the reference a two-line caption should clearly beat. Asserting
        // a ratio rather than an absolute height keeps the test independent of the theme's font
        // metrics.
        val twoLineCaption = onNodeWithText("Classic Light", useUnmergedTree = true).getUnclippedBoundsInRoot().height.value
        val oneLineCaption = onNodeWithText("Music", useUnmergedTree = true).getUnclippedBoundsInRoot().height.value
        assertTrue(
            twoLineCaption > oneLineCaption * 1.4f,
            "the 'Classic Light' caption measured ${twoLineCaption.roundToInt()}dp against " +
                "'Music's single line at ${oneLineCaption.roundToInt()}dp — a wrapped caption " +
                "is two lines and should beat one by a wide margin. A ratio near 1 means the " +
                "caption was clipped back to one line (the defect: maxLines = 1 ended the " +
                "second palette's name in an ellipsis)",
        )
    }

    // --- measurement ------------------------------------------------------------------------------

    /**
     * How many pixels in a strip just inside the row's top edge are near-enough to [accent] to
     * be the ring.
     *
     * The strip is [STRIP_ROWS] rows deep: enough for a 2dp ring plus the clip's own
     * antialiasing at any host density, and text never reaches it. [insetRows] rows are
     * skipped at the top so a flush neighbour's ring antialiasing is not counted — see the
     * caller.
     */
    private fun accentPixelsAlongTopEdge(
        frame: PixelMap,
        bounds: DpRect,
        scale: Float,
        accent: Color,
        insetRows: Int,
    ): Int {
        val left = (bounds.left.value * scale).toInt().coerceAtLeast(0)
        val right = (bounds.right.value * scale).toInt().coerceAtMost(frame.width - 1)
        val top = (bounds.top.value * scale).toInt().coerceAtLeast(0)
        val strip = (top + insetRows).coerceAtMost(frame.height - 1)
        val bottom = (strip + STRIP_ROWS).coerceAtMost(frame.height - 1)
        var count = 0
        for (y in strip..bottom) {
            for (x in left..right) {
                if (channelDistance(frame[x, y], accent) <= 64) count++
            }
        }
        return count
    }

    private const val STRIP_ROWS = 6

    private fun channelDistance(a: Color, b: Color): Int {
        fun byte(v: Float) = (v * 255f).toInt()
        return maxOf(
            abs(byte(a.red) - byte(b.red)),
            abs(byte(a.green) - byte(b.green)),
            abs(byte(a.blue) - byte(b.blue)),
        )
    }
}