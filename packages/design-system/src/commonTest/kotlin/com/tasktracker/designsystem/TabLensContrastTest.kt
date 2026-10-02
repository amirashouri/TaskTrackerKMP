package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Measures the tab captions against the pixels actually behind them.
 *
 * ## How a caption is found, and why it took three attempts
 *
 * The first attempt multiplied the tokens: composite the bar's fill over the canvas, composite
 * the lens over that, check the accent against the result. It reported the light caption at
 * 3.48:1 where the rendered frame measures 4.8:1, and it could not tell a defect from a
 * non-defect in the dark appearance at all.
 *
 * The second attempt read the pixels and looked for the caption as the lowest run of rows
 * containing ink. That failed for a reason worth recording, because it is the trap any
 * pixel-based check on a glass component falls into: **the lens is not background.** The lens
 * covers most of a tab and differs from the tab's own modal colour, so every row of a selected
 * tab reads as "inked", the run logic found one run spanning the whole tab, and the "ink" it
 * measured was the lens's bright rim rather than a letter. It reported a confident 5.75:1 for a
 * caption that is genuinely 3.28:1.
 *
 * So this does not look for text at all. It renders the bar **twice in one frame** — once with
 * captions, once with the same captions replaced by a single space — and differences the two.
 * Everything that is not a caption is identical between them: the glass, the lens, the rim, the
 * gradient, the icon, the backdrop. What is left is the caption and nothing else, and the
 * ground it sits on is read from the blanked twin, which has the same pixels everywhere except
 * exactly where the text was.
 *
 * That is why this is a diff and not a threshold. A threshold has to guess what counts as text
 * over a background that is itself a translucent gradient, and every guess was wrong.
 *
 * ## What it found
 *
 * The dark caption was a real defect at 2.83:1 as `TaskTrackerAccent.Dark`, a token documented as
 * clearing 5.65:1 *against white*, on a bar that is not white in dark appearance — the same
 * token-in-the-wrong-compositing mistake [GlassFillContrastTest] exists to catch, one component
 * over. Strengthening the lens is not the fix: the caption is below the lens, so no extra wash
 * reaches it, and the wash that would reach it turns the lens into an opaque pill.
 *
 * The unselected caption was the second defect, at 2.78:1 in light appearance, and it was
 * subtler: `ttLabelSecondary` is the platform's `secondaryLabel`, correct against an opaque white
 * background and short of the mark once a translucent bar puts a grey-blue behind it instead.
 *
 * Where the four captions land now, all measured by this file rather than asserted about:
 * selected light 4.61:1, selected dark 14.41:1, unselected light 6.07:1, unselected dark
 * 5.92:1. The light selected figure is the thinnest margin in the set, which is worth knowing
 * before anyone darkens the accent or cools the fill.
 */
@OptIn(ExperimentalTestApi::class)
class TabLensContrastTest {

    @Test
    fun theSelectedCaptionIsReadableInEveryAppearance() = rasterising {
        for (scheme in APPEARANCES) {
            // `runComposeUiTest` is `Unit`-returning, so the reading comes back through a
            // holder rather than as the block's value.
            var measured: Reading? = null
            runComposeUiTest { measured = read(scheme = scheme, index = 0) }
            val reading = requireNotNull(measured) { "no frame captured for ${scheme.name}" }
            assertTrue(
                reading.ratio >= TEXT_CONTRAST_MINIMUM,
                "the selected caption in ${scheme.name} appearance is ${reading.glyph.hex()} " +
                    "on ${reading.background.hex()}, which is ${reading.ratio.ratioText()} to " +
                    "1 — below the ${TEXT_CONTRAST_MINIMUM} to 1 needed to read it. Note the " +
                    "direction: a white lens over a dark bar pulls the backdrop toward the " +
                    "label, so this gets *worse* as the lens is strengthened, not better",
            )
        }
    }

    /**
     * The unselected captions, measured the same way.
     *
     * ## Why the unselected ink is not [ttLabelSecondary]
     *
     * This floor is the same [TEXT_CONTRAST_MINIMUM] the selected caption is held to, and it
     * started life as a lower ratchet rather than that. The unselected ink used to be
     * [ttLabelSecondary], which resolves to the platform's `secondaryLabel`: the label at 60%,
     * chosen against an *opaque* `systemBackground` where that alpha lands near 4.6:1. This bar
     * is not opaque. Its fill is a translucent wash, so what sits behind the caption is a mid
     * grey-blue around `#C5CEDA` rather than white, and the same 60% of the label over *that*
     * composites to `#757881` and measures 2.78:1 - under what normal text needs.
     *
     * The shortfall was not this file's to close by editing the token, because
     * [ttLabelSecondary] is read by every other surface in the app and its replacement is a
     * design decision. It is now closed at the bar instead: the tab cell carries its own
     * light-appearance alpha for unselected ink, which measures 6.07:1 and leaves the caption
     * clearly subordinate to the selected accent above it. Dark still uses the platform's own
     * value, which measures 5.92:1 over a near-black backdrop and needed no change.
     *
     * So the wanted number and the measured one agree, and this is a gate rather than a
     * ratchet. That matters for what the test is worth: a ratchet that pins the measured value
     * only proves the bar is not getting worse, whereas a gate on [TEXT_CONTRAST_MINIMUM]
     * fails if anyone lowers the caption's alpha or changes the fill's tint underneath it.
     */
    @Test
    fun theUnselectedCaptionsAreReadableInEveryAppearance() = rasterising {
        for (scheme in APPEARANCES) {
            for (index in UNSELECTED_INDICES) {
                var reading: Reading? = null
                runComposeUiTest { reading = read(scheme = scheme, index = index) }
                val measured = requireNotNull(reading) { "no frame for ${scheme.name}/$index" }
                assertTrue(
                    reading.ratio >= UNSELECTED_FLOOR,
                    "an unselected caption in ${scheme.name} appearance is " +
                        "${reading.glyph.hex()} on ${reading.background.hex()}, only " +
                        "${reading.ratio.ratioText()} to 1, below even the ${UNSELECTED_FLOOR} " +
                        "floor that every caption on this bar is held to. The light-appearance " +
                        "unselected alpha in TabBar.kt is the thing to look at; see this test's " +
                        "notes on why the bar does not use ttLabelSecondary here",
                )
            }
        }
    }

    /**
     * Renders both bars in one frame and reads one caption by differencing them.
     *
     * A single `runComposeUiTest` because Compose allows one `setContent`, and the two bars are
     * stacked rather than rendered in sequence so that the lens's own animation state cannot
     * differ between them. Each bar lays out identically, so the tab rectangles line up in
     * width and in internal geometry even though their `top` values differ.
     */
    private fun ComposeUiTest.read(scheme: ThemeScheme, index: Int): Reading {
        setContent {
            ttTheme(scheme) {
                Box(Modifier.fillMaxSize().background(ttCanvas())) {
                    Column {
                        Bar(captions = CAPTIONS, suffix = "inked")
                        // A gap wider than the bar's shadow. The bar drops a shadow, and a
                        // shadow falls *downward*: stacked flush, the first bar's shadow lands
                        // on the top rows of the second one, and the diff then reports a band
                        // of difference across the full width of the tab that has nothing to do
                        // with either caption. That band was enough to make the lens's bright
                        // rim look like the selected label.
                        Box(Modifier.height(SHADOW_CLEARANCE))
                        Bar(captions = BLANKED, suffix = "blanked")
                    }
                }
            }
        }
        waitForIdle()

        val frame = onRoot().captureToImage()
        val inked = onNodeWithTag(tabTag(index, "inked")).getBoundsInRoot()
        val blanked = onNodeWithTag(tabTag(index, "blanked")).getBoundsInRoot()
        val root = onRoot().getBoundsInRoot()

        // Bounds are in dp and the frame is in pixels. Deriving the scale from the measured
        // root keeps this honest at any density instead of assuming the host's.
        val scale = frame.width.toFloat() / max(root.right.value, 1f)
        return difference(frame, inked, blanked, scale)
    }

    /**
     * The caption's ink and the ground under it, from two rectangles of the same size.
     *
     * The ink is the pixel in the inked rectangle that is furthest from the blanked twin's
     * local ground; the ground is the *modal* colour of the blanked rectangle, which is the bar
     * exactly as it would have been without text — glass, lens and gradient included, since the
     * blanked bar's lens is in the same place.
     */
    private fun difference(
        frame: ImageBitmap,
        inked: DpRect,
        blanked: DpRect,
        scale: Float,
    ): Reading {
        val pixels = frame.toPixelMap()
        val width = ((inked.right.value - inked.left.value) * scale).toInt()
        val left = (inked.left.value * scale).toInt()
        val top = (inked.top.value * scale).toInt()
        val secondTop = (blanked.top.value * scale).toInt()

        assertTrue(width > 0, "the tab rectangle had no width")

        fun blank(x: Int, y: Int): Color =
            pixels[left + x, secondTop + y].let { Color(it.red, it.green, it.blue) }

        fun inked(x: Int, y: Int): Color =
            pixels[left + x, top + y].let { Color(it.red, it.green, it.blue) }

        /**
         * Whether two pixels differ by more than the noise floor.
         *
         * Exact inequality is not good enough, and finding that out is why this comment
         * mentions rounding. The two bars are laid out identically but composited at
         * different sub-pixel offsets, so roughly one colour step of difference shows up
         * across the whole rectangle — about 1/255 in a channel. Compared exactly, that noise
         * reads as "the caption covers the entire tab", and the brightest changed pixel in the
         * set is the lens's rim rather than a letter. The floor below is several colour steps,
         * against a caption stroke that differs from its ground by hundreds.
         */
        fun differs(first: Color, second: Color): Boolean =
            abs(first.red - second.red) > NOISE_FLOOR ||
                abs(first.green - second.green) > NOISE_FLOOR ||
                abs(first.blue - second.blue) > NOISE_FLOOR

        // The blanked bar's own modal colour, per column band, is overkill. One modal colour
        // over the whole rectangle is the bar's ground, and the caption never covers enough of
        // it to be the mode.
        val buckets = mutableMapOf<Int, MutableList<Color>>()
        val height = ((inked.bottom.value - inked.top.value) * scale).toInt()
        for (y in 0 until height) for (x in 0 until width) {
            val rgb = blank(x, y)
            val key = (rgb.red * 15).toInt() shl 8 or
                (rgb.green * 15).toInt() shl 4 or
                (rgb.blue * 15).toInt()
            buckets.getOrPut(key) { mutableListOf() }.add(rgb)
        }
        val groundSamples = buckets.maxBy { it.value.size }.value
        val background = Color(
            red = groundSamples.map { it.red }.average().toFloat(),
            green = groundSamples.map { it.green }.average().toFloat(),
            blue = groundSamples.map { it.blue }.average().toFloat(),
            alpha = 1f,
        )
        val backgroundLuminance = relativeLuminance(background)

        // A window on the middle of the tab, which is where a centred caption's strokes are
        // and where the lens is not.
        //
        // The caption is not the only thing the blanking exposes. The lens's bottom rim runs
        // across the caption's row band — the lens is inset and the caption sits at the foot
        // of the tab, so the two overlap — and that rim is the brightest thing in the tab by a
        // wide margin. It survives into the diff because the lens is laid out from the row
        // rather than from its content: a blanked caption is exactly as tall as a real one, so
        // the geometry matches, and the rim then differs under a sub-pixel compositing offset
        // by far more than the one colour step of background noise.
        //
        // Two masks take it out, and both are properties of the shape rather than of a colour
        // or a position a future caption size would move. Horizontally, the lens's rounded
        // bottom corners are the surviving bright pixels and they sit at the lens's own
        // extremes, hard against the tab's edges; the middle of the tab is clear of them. The
        // widest caption here is "Settings", which fills about a third of a tab and is
        // centred, so it stays well inside the window. Vertically, a rim inks a whole row
        // across while letters ink a fraction of one, so a per-row count separates the two
        // without knowing the caption's exact metrics.
        val windowStart = width * WINDOW_NEAR / WINDOW_SPAN
        val windowEnd = width * WINDOW_FAR / WINDOW_SPAN
        val changedPerRow = IntArray(height)
        for (y in 0 until height) for (x in windowStart until windowEnd) {
            if (differs(blank(x, y), inked(x, y))) changedPerRow[y]++
        }
        val strokeCeiling = ((windowEnd - windowStart) * STROKE_ROW_FRACTION).toInt()
        val strokeRows = changedPerRow.indices.filter { changedPerRow[it] in 1 until strokeCeiling }
        assertTrue(
            strokeRows.isNotEmpty(),
            "no row of this tab looked like text inside the middle window; per-row changed " +
                "counts were " + changedPerRow.filter { it > 0 },
        )

        // The caption's ink, taken as the *median* of what each stroke row contributes.
        //
        // Per row, the changed pixel furthest from the ground the blanked bar shows is the
        // best single sample of ink in that row: furthest rather than darkest or lightest,
        // because which of those the ink is depends on the appearance, since on the light bar
        // the caption is darker than its ground and on the dark bar it is lighter.
        //
        // Across rows, the median is what makes this robust. The lens's rim does not always
        // leave a full-width line to be filtered out — where a rounded corner crosses the
        // window it leaves a sliver of one or two pixels in a single row. In the dark bar that
        // sliver is the brightest thing in the tab and it wins a maximum outright, reporting
        // the rim as the caption and passing a caption that is actually unreadable. A sliver
        // in one row is not a glyph, and the rows of a caption agree with each other, so the
        // median takes the value the strokes actually show and discards the outlier without
        // needing a threshold tuned to the current caption size.
        val perRowInk = strokeRows.mapNotNull { y ->
            var rowInk: Color? = null
            var rowFurthest = 0.0
            for (x in windowStart until windowEnd) {
                if (!differs(blank(x, y), inked(x, y))) continue
                val distance = abs(relativeLuminance(inked(x, y)) - backgroundLuminance)
                if (distance > rowFurthest) {
                    rowFurthest = distance
                    rowInk = inked(x, y)
                }
            }
            rowInk
        }
        assertTrue(
            perRowInk.isNotEmpty(),
            "the stroke rows held no changed pixels, so no caption was drawn in this tab",
        )
        val glyph = perRowInk.sortedBy { relativeLuminance(it) }[perRowInk.size / 2]
        return Reading(glyph = glyph, background = background, ratio = contrastRatio(glyph, background))
    }

    @Composable
    private fun Bar(captions: List<String>, suffix: String) {
        // No tag on the bar itself: both bars would carry the same one, and the scale is
        // derived from the root, which is unique by construction.
        Box {
            TTGlassTabBar(
                tabs = TABS,
                selectedIndex = 0,
                onSelect = {},
                title = { captions[TABS.indexOf(it)] },
                icon = { ttTabIcons.projects },
                tabModifier = { Modifier.testTag(tabTag(TABS.indexOf(it), suffix)) },
            )
        }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** WCAG 2.x relative luminance. Linearised, because contrast is defined in linear light. */
    private fun relativeLuminance(color: Color): Double =
        RED_COEFFICIENT * linearise(color.red) +
            GREEN_COEFFICIENT * linearise(color.green) +
            BLUE_COEFFICIENT * linearise(color.blue)

    private fun linearise(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= SRGB_LINEAR_THRESHOLD) {
            value / SRGB_DIVISOR
        } else {
            ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
        }
    }

    private class Reading(val glyph: Color, val background: Color, val ratio: Double)

    private companion object {
        val APPEARANCES = listOf(ThemeScheme.Light, ThemeScheme.Dark)

        val TABS = listOf("Projects", "Board", "Backlog", "Settings")

        /**
         * The same labels, blanked.
         *
         * A single space rather than an empty string: an empty string can collapse the text
         * composable's height, which would change the column's layout and hand the diff the tab
         * bar's own geometry instead of its captions. A space has the metrics and none of the ink.
         */
        val CAPTIONS = TABS
        val BLANKED = TABS.map { " " }

        val UNSELECTED_INDICES = listOf(1, 2, 3)

        private fun tabTag(index: Int, suffix: String) = "captionProbe-$suffix-$index"

        /**
         * Vertical clearance between the two bars, in dp. Comfortably more than the shadow's
         * reach at [ttGlass.tabBarShadowElevation], which is the only thing that could put one
         * bar's pixels into the other's rectangle.
         */
        val SHADOW_CLEARANCE = 64.dp

        /**
         * The smallest channel difference treated as a real change, on the 0-to-1 scale.
         *
         * Five colour steps. The compositing noise this rejects measures about one, and the
         * caption's strokes it must keep measure more than a hundred.
         */
        const val NOISE_FLOOR = 0.02f

        /**
         * How much of a tab's width a row of text may ink, as a fraction.
         *
         * Half. The longest caption here is "Settings", which fills well under half a tab at
         * the caption size, while the lens's bottom rim inks all of it. There is a wide gap
         * between the two and this sits in it.
         */
        const val STROKE_ROW_FRACTION = 0.5

        /**
         * The middle of the tab, as a fraction of its width.
         *
         * [WINDOW_SPAN] is the denominator and [WINDOW_NEAR] and [WINDOW_FAR] are the bounds
         * within it. The window is the middle two fifths, which clears the lens's rounded
         * bottom corners and still contains the widest caption on a 246-point tab.
         */
        const val WINDOW_SPAN = 5
        const val WINDOW_NEAR = 1
        const val WINDOW_FAR = 4

        const val TEXT_CONTRAST_MINIMUM = 4.5

        /**
         * The floor an unselected caption is held to.
         *
         * The same [TEXT_CONTRAST_MINIMUM] the selected caption is held to, and not a lower
         * ratchet. It used to be 2.7, pinned just under the 2.78 that light appearance measured,
         * because the unselected ink was the platform's `secondaryLabel`: the label at 60%,
         * which is chosen against an opaque white background and lands near 4.6:1 there. Over
         * this bar's translucent fill the backdrop is a mid grey-blue instead, which pulled the
         * same alpha down to 2.78:1 and left the caption short of what normal text needs. The
         * bar now carries its own light-appearance alpha for unselected ink, which measures
         * 6.07:1, so the wanted number and the measured one finally agree and the tripwire can
         * sit on the real threshold.
         */
        const val UNSELECTED_FLOOR = TEXT_CONTRAST_MINIMUM

        const val RED_COEFFICIENT = 0.2126
        const val GREEN_COEFFICIENT = 0.7152
        const val BLUE_COEFFICIENT = 0.0722
        const val SRGB_LINEAR_THRESHOLD = 0.04045
        const val SRGB_DIVISOR = 12.92
        const val SRGB_OFFSET = 0.055
        const val SRGB_SCALE = 1.055
        const val SRGB_EXPONENT = 2.4
    }
}

/** Hex for failure messages, in whole bytes so it reads the same on every target. */
private fun Color.hex(): String {
    fun channel(value: Float): String =
        round(value * 255f).toInt().toString(16).padStart(2, '0')
    return "#" + channel(red) + channel(green) + channel(blue)
}

/**
 * Two decimal places, written out: `String.format` is JVM-only and does not compile for
 * Kotlin/Native, so the rounding is done in integer hundredths.
 */
private fun Double.ratioText(): String {
    val hundredths = round(this * 100).toInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
