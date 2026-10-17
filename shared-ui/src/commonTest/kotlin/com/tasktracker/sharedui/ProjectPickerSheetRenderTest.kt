package com.tasktracker.sharedui

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
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTBottomSheetScrimTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featureprojectspresentation.PickableProject
import com.tasktracker.featureprojectspresentation.ProjectPickerSheet
import com.tasktracker.featureprojectspresentation.ProjectPickerSheetTag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How much of the page behind the project picker reaches through its own background.
 *
 * ## The defect
 *
 * The picker used to draw its own surface — `ttGlassSurface` with no `base` — while the project
 * and task editors took [com.tasktracker.designsystem.TTBottomSheetScaffold]. With no base,
 * [com.tasktracker.designsystem.ttGlassSurface] takes the fill ramp: shipped alpha
 * `FILL_ALPHA * FILL_ALPHA` = 0.3025, tinted toward the accent. With the scaffold's base the
 * sheet is `ttSurface()` at `1f - LENS_ALPHA` = 0.92 under a 0.08 lens.
 *
 * So the sheet that lists the projects the user is choosing between was three times more
 * transmissive than the sheet that creates one, and was reported as too see-through. Same
 * rounded rect, same hairline, same product — which is why it survived review.
 *
 * ## Why this is measured and not asserted structurally
 *
 * "The sheet's background is the scaffold's" is readable off the modifier chain, but "the sheet
 * is opaque enough to read a list through" is a claim about what the compositor did, and the two
 * come apart: a surface can be handed the right base and still composite to something
 * translucent. This is the modelling trap `rendered-ui-measurement` exists to prevent — computing
 * `0.92 * neutral + 0.08 * backdrop` in the test and asserting on that would be asserting the
 * arithmetic rather than the frame.
 *
 * ## Why it is a difference between two backdrops, not a colour comparison
 *
 * The same sheet is rendered twice over pure black and pure white, and the assertion is on how
 * far the two frames differ *inside the sheet*. That needs no token, no predicted alpha and no
 * knowledge of which pixel is background:
 *
 * - an opaque sheet transmits the lens and little else, so the two frames are nearly identical;
 * - a translucent sheet lets most of the page through, so the two frames are nothing alike.
 *
 * The number is therefore a property of the frame rather than of the design system's intent,
 * which is also why it survives the accent or the neutral changing underneath it.
 *
 * ## Where it samples, and why not the middle of the sheet
 *
 * The measurement is a horizontal strip just *below* the sheet's content, inside the scaffold's
 * own `xxl` bottom padding. Sampling the content area instead would be a mistake: each row is a
 * `ttGlassCard`, which is itself an untinted fill ramp, and those rows sit on the sheet and so do
 * not move with the page however translucent the sheet is. A median over the content would be
 * dominated by rows and would report an opaque sheet while the sheet was the most translucent
 * thing in the frame. The padding strip is guaranteed to be the sheet's own surface, with no
 * text, no row and no corner arc in it.
 *
 * ## What a mutation of this has to look like, which cost a cycle to learn
 *
 * Adding an untinted `ttGlassSurface` back onto the *content* does not make this test fail, and
 * should not: that surface stacks on top of the scaffold's own frame, which is already opaque,
 * so the page still cannot reach the list. The first attempt at this mutation did exactly that
 * and left the reading at 19 channels, which is a green that means nothing.
 *
 * The mutation that reproduces the reported defect is removing the solid `base` from
 * `TTBottomSheetScaffold`'s own surface — the sheet then has a fill ramp and nothing behind it,
 * and the reading goes to 178 channels. Bypassing the scaffold altogether reads 255, which is
 * the same defect one step further out. Worth writing down, because the natural thing to try
 * first is the change that was actually made to this file, and that one is not the defect.
 */
@OptIn(ExperimentalTestApi::class)
class ProjectPickerSheetRenderTest {

    /**
     * The sheet must not transmit the page behind it.
     *
     * Both appearances, because the sheet's fill differs between them and a tolerance that only
     * holds in one of them is not a property of the sheet.
     */
    @Test
    fun theSheetDoesNotTransmitThePageBehindIt() {
        for (scheme in APPEARANCES) {
            var reading: Reading? = null
            rasterising { reading = measureTransmission(scheme) }
            val measured = requireNotNull(reading) { "no reading in ${scheme.name} appearance" }

            assertTrue(
                measured.transmission <= MAX_TRANSMISSION,
                "in ${scheme.name} appearance the picker's background read " +
                    "${measured.overBlack.hex()} over a black page and ${measured.overWhite.hex()} " +
                    "over a white one — ${measured.transmission.inChannels()} channels apart, so " +
                    "that much of the page behind the sheet reaches the list of project names. A " +
                    "modal sheet is opaque over a lens: check that ProjectPickerSheet still takes " +
                    "its frame from TTBottomSheetScaffold rather than drawing a ttGlassSurface " +
                    "with no base, which is a 0.3025 accent wash instead",
            )
        }
    }

    /**
     * The frame has to be the shared one, and this is the cheap half of that claim.
     *
     * The transmission test above would still pass if someone re-drew the sheet's background as
     * an opaque panel of their own — correct pixels, no shared vocabulary, and the next sheet
     * added to this screen would be free to diverge the same way. What it cannot do is draw one
     * without the scaffold's scrim and outside-tap handling, because the scrim *is* the
     * scaffold's, so its absence is directly observable in the tree.
     *
     * A structural assertion on its own would be a poor test of transparency — a modifier being
     * present says the intent was spelled, not that the pixels came out. It is here because it
     * measures a different property than the test above, not instead of it.
     */
    @Test
    fun theSheetIsFramedByTheSharedScaffold() {
        // Guarded for the same reason as the measurement above: this class composes directly
        // from `commonTest`, which the Android host can only run under Robolectric — and
        // `@RunWith` cannot be written here. It asks for a skip instead of dying in
        // `runComposeUiTest`'s environment setup.
        rasterising {
            setContent {
                ttTheme(ThemeScheme.Dark) {
                    Box(Modifier.fillMaxSize().background(PAGE_BLACK)) {
                        ProjectPickerSheet(
                            projects = PROJECTS,
                            selectedProjectID = null,
                            onSelect = {},
                        )
                    }
                }
            }
            waitForIdle()

            onNodeWithTag(ProjectPickerSheetTag).assertExists()
            // The scaffold's scrim, not a hand-rolled one. This tag is the scaffold's, so it can
            // only be present if the scaffold drew it.
            onNodeWithTag(TTBottomSheetScrimTag).assertExists()
        }
    }

    /**
     * Renders the sheet over each backdrop and returns how far apart the two frames are inside
     * the sheet's bottom padding.
     *
     * One `runComposeUiTest` per backdrop rather than two sheets in one composition: the two
     * frames have to be the same frame, same size and same layout, so that the difference is the
     * backdrop and nothing else. Two sheets side by side would be measured against each other's
     * arrangement.
     */
    private fun ComposeUiTest.measureTransmission(scheme: ThemeScheme): Reading {
        val overBlack = renderStrip(scheme, PAGE_BLACK)
        val overWhite = renderStrip(scheme, PAGE_WHITE)
        return Reading(
            overBlack = overBlack,
            overWhite = overWhite,
            transmission = perChannelDistance(overBlack, overWhite),
        )
    }

    /**
     * One appearance, one backdrop: the median colour of the sheet's bottom padding strip.
     *
     * The median rather than a single pixel because the strip is read at whatever sub-pixel
     * offset the two frames happen to land on, and two identically laid out fills composited at
     * different offsets differ by about a colour step. A median across ~300 columns absorbs that;
     * a single sample would be reading the offset rather than the surface.
     */
    private fun ComposeUiTest.renderStrip(scheme: ThemeScheme, backdrop: Color): Color {
        setContent {
            ttTheme(scheme) {
                Box(Modifier.fillMaxSize().background(backdrop)) {
                    ProjectPickerSheet(
                        projects = PROJECTS,
                        selectedProjectID = null,
                        onSelect = {},
                    )
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage()
        val pixels = frame.toPixelMap()
        val root = onRoot().getBoundsInRoot()
        val scale = frame.width.toFloat() / maxOf(root.right.value, 1f)

        val content = onNodeWithTag(ProjectPickerSheetTag).getBoundsInRoot()
        // Halfway into the scaffold's 16dp bottom padding, so the strip cannot reach the
        // surface's rounded corner or its hairline either side of the content's width.
        val y = ((content.bottom.value + PADDING_PROBE_DP) * scale).toInt()
            .coerceIn(0, pixels.height - 1)
        val from = ((content.left.value + PADDING_INSET_DP) * scale).toInt()
            .coerceIn(0, pixels.width - 1)
        val to = ((content.right.value - PADDING_INSET_DP) * scale).toInt()
            .coerceIn(from, pixels.width - 1)

        return medianColour(pixels, from, to, y)
    }

    /** The per-channel median of one row, so a composited edge or a stray hair cannot win it. */
    private fun medianColour(pixels: PixelMap, fromX: Int, toX: Int, y: Int): Color {
        val reds = ArrayList<Float>(toX - fromX + 1)
        val greens = ArrayList<Float>(toX - fromX + 1)
        val blues = ArrayList<Float>(toX - fromX + 1)
        for (x in fromX..toX) {
            val pixel = pixels[x, y]
            reds.add(pixel.red)
            greens.add(pixel.green)
            blues.add(pixel.blue)
        }
        reds.sort()
        greens.sort()
        blues.sort()
        val middle = reds.size / 2
        return Color(red = reds[middle], green = greens[middle], blue = blues[middle])
    }

    /**
     * The largest per-channel gap between two frames.
     *
     * Maximum and not mean: the defect is that *everything* under the sheet moved, so the frame
     * that shows it is wrong across the whole strip and averaging would only soften the evidence.
     */
    private fun perChannelDistance(first: Color, second: Color): Float = maxOf(
        abs(first.red - second.red),
        abs(first.green - second.green),
        abs(first.blue - second.blue),
    )

    private fun Float.inChannels(): Int = (this * 255f).toInt()

    private data class Reading(
        val overBlack: Color,
        val overWhite: Color,
        val transmission: Float,
    )

    private companion object {
        val APPEARANCES = listOf(ThemeScheme.Light, ThemeScheme.Dark)

        /**
         * Pure black and pure white, so the backdrop's contribution is the whole difference
         * between the frames. A pair of mid-greys would halve the signal and halve the headroom
         * for no gain.
         */
        val PAGE_BLACK = Color(0xFF000000)
        val PAGE_WHITE = Color(0xFFFFFFFF)

        /**
         * Three rows, which is enough for the list to look like a list and short enough that the
         * sheet clears the bottom padding this test samples. `ProjectID` wraps a `Uuid` and has
         * no integer form, so the ids are written out; they only have to be distinct and stable.
         */
        val PROJECTS = listOf(
            PickableProject(ProjectID("0195f8a0-0000-7000-8000-000000000001"), "Apollo Programme", "AP"),
            PickableProject(ProjectID("0195f8a0-0000-7000-8000-000000000002"), "Mercury", "ME"),
            PickableProject(ProjectID("0195f8a0-0000-7000-8000-000000000003"), "Gemini", "GE"),
        )

        /** Deep inside the scaffold's `xxl` bottom padding, in dp. */
        const val PADDING_PROBE_DP = 8f

        /** In from the content's edges, clear of the surface's corner arcs and its hairline. */
        const val PADDING_INSET_DP = 12f

        /**
         * How much of the backdrop may reach the sheet: a fifth of the channel range.
         *
         * Frozen from measured numbers rather than chosen, and taken on both rasterisers
         * because a token tuned on one host is how the tab bar captions shipped unreadable on
         * iOS. A based surface transmits `LENS_ALPHA` = 0.08 by construction and measured
         * **19 channels in Light and 20 in Dark, identically on the JVM and the iOS simulator** —
         * the two hosts agree because this is a flat fill rather than a glyph, which is the
         * whole reason the probe point is in a padding strip. The defect transmits 0.6975 and
         * measured **178 channels**, 3.5x this bar.
         *
         * So the threshold sits 31 channels above the correct reading — room for the compositor's
         * own rounding of an alpha blend — and 127 below the defect's. Nothing about it is a
         * judgement call about how translucent a modal sheet may reasonably be; it is a floor
         * under "mostly opaque".
         */
        const val MAX_TRANSMISSION = 51f / 255f
    }
}

/**
 * `#rrggbb`, written without [String.format].
 *
 * `format` does not resolve the same way on Kotlin/Native, and a hex dump that only compiles on
 * one of three hosts is a hex dump two of them cannot print in a failure message — which is the
 * only place it is used.
 */
private fun Color.hex(): String {
    fun channel(value: Float): String =
        (value * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}
