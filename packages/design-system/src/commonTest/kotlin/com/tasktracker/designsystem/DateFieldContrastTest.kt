package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The day grid's own ink, measured on the surface it is actually drawn over.
 *
 * ## Why this is pixels and not arithmetic
 *
 * Two composites are in play and neither is a token on a token:
 *
 *  - a day numeral on the **accent disc** — a solid fill, so that one *is* arithmetic;
 *  - an unselected day, and the **weekday header**, on the popup's **lens-over-base** fill:
 *    white at 0.08 over `ttSurface()`. A translucent layer over another translucent layer,
 *    which `AGENTS.md` rule 2 sends to pixels unconditionally.
 *
 * The header is the one that matters most and reads worst. It is the row that says what
 * each column *is*, at the smallest type on the screen, and a header that is too dim does
 * not look broken — the calendar just quietly becomes hard to use. That is why the component
 * draws it in [ttBodyLabel] rather than [ttLabelSecondary]: the secondary label measures
 * **3.30:1** on this popup's base in light appearance, and `captionSmall` needs the 4.5:1
 * that text below 18sp gets. If this test ever goes red on the header, check whether the
 * *base* changed before suspecting the ink.
 *
 * ## Both rasterisers, and why every test prints what it measured
 *
 * [rasterising] runs this on the JVM and the iOS simulator and returns on the Android host.
 * The two disagree by enough to find real defects on small glyph strokes — Phase 22 found
 * the same token measuring 5.37:1 on the JVM and 3.98:1 on iOS — so a number here is
 * provisional until both hosts have reported it, which is why the figures are printed
 * rather than only compared.
 */
@OptIn(ExperimentalTestApi::class)
class DateFieldContrastTest {

    @Test
    fun `an unselected day clears the text bar on the popup's own fill`() {
        for ((scheme, name) in appearances) {
            val reading = requireNotNull(measure(scheme, TTDayCellTag(UNSELECTED_DAY))) { NO_READING }
            println(
                "$name: an unselected day rendered ${reading.ink.hex()} on the popup's " +
                    "${reading.fill.hex()}, ${reading.ratio.ratioText()}:1",
            )
            assertTrue(
                reading.ratio >= TEXT_CONTRAST_MINIMUM,
                "$name appearance: an unselected day rendered ${reading.ink.hex()} on the " +
                    "popup's ${reading.fill.hex()}, which is ${reading.ratio.ratioText()}:1, " +
                    "below the ${TEXT_CONTRAST_MINIMUM}:1 a ${ttTypography.footnote.fontSize} " +
                    "numeral needs. This is a translucent lens over an opaque surface, so it " +
                    "has to be read out of a frame rather than computed.",
            )
        }
    }

    @Test
    fun `the weekday header clears the text bar on the popup's own fill`() {
        for ((scheme, name) in appearances) {
            val reading = requireNotNull(measure(scheme, TTWeekdayTag(WEEKDAY))) { NO_READING }
            println(
                "$name: the weekday header rendered ${reading.ink.hex()} on the popup's " +
                    "${reading.fill.hex()}, ${reading.ratio.ratioText()}:1",
            )
            assertTrue(
                reading.ratio >= TEXT_CONTRAST_MINIMUM,
                "$name appearance: the weekday header rendered ${reading.ink.hex()} on the " +
                    "popup's ${reading.fill.hex()}, which is ${reading.ratio.ratioText()}:1. " +
                    "The header is captionSmall and so needs ${TEXT_CONTRAST_MINIMUM}:1, and it " +
                    "is the row that says what each column *is* — a header too dim to read does " +
                    "not look broken, it just makes the calendar hard to use. ttLabelSecondary " +
                    "measures 3.30:1 on this base in light, which is why the header is drawn " +
                    "in ttBodyLabel.",
            )
        }
    }

    @Test
    fun `the selected day's number clears the text bar on the accent disc`() {
        for ((scheme, name) in appearances) {
            // Sample.CELL_INK, not Sample.INK, and the difference is a false green worth
            // recording: with the *popup's* fill as the background, the strongest pixel in a
            // selected day's bounds is the accent disc rather than the white numeral — white
            // `#ffffff` is nearly the same colour as this popup's `#f4f4f9`, while the disc is
            // not. So `INK` here measured accent-against-popup and happened to report 7.19:1,
            // which is about the right number for entirely the wrong pair. The background for
            // ink on a filled disc has to be the disc.
            val reading = requireNotNull(
                measure(scheme, TTDayCellTag(SELECTED_DAY), Sample.CELL_INK),
            ) { NO_READING }
            println(
                "$name: the selected day rendered ${reading.ink.hex()} on the accent disc's " +
                    "${reading.fill.hex()}, ${reading.ratio.ratioText()}:1",
            )
            assertTrue(
                reading.ratio >= TEXT_CONTRAST_MINIMUM,
                "$name appearance: the selected day rendered ${reading.ink.hex()} on the " +
                    "accent disc's ${reading.fill.hex()}, which is " +
                    "${reading.ratio.ratioText()}:1, below ${TEXT_CONTRAST_MINIMUM}:1. The " +
                    "accent is the only fill in this app that carries text, which is what " +
                    "ttOnAccent exists for — and it is white in *both* appearances, so this " +
                    "figure is the same pairing the sign-in button's label already clears.",
            )
        }
    }

    @Test
    fun `the selected day's ink is the same in both appearances`() {
        // This is the whole reason [ttOnAccent] is a separate token rather than "whichever ink
        // reads well". The disc is the accent, and the accent *changes* between the
        // appearances: `#39577d` rendered in light and `#636c76` in dark. An ink that follows
        // [ttLabel] follows the appearance too and has to happen to work both times.
        // `ttOnAccent` is white in both, so the pairing is right by construction rather than
        // by luck.
        //
        // It is here because of a mutation this suite could not otherwise catch. Swapping
        // `ttOnAccent` for `ttLabel` on the selected day left **every** contrast assertion
        // green: black on the light accent measures 7.5:1 and white on the dark accent
        // 5.33:1, both clear of 4.5:1. The defect was real — the token whose whole job is to
        // name the one fill in this app that carries text had been dropped — and no ratio
        // could see it. That is the "a measurement passes and the pixels are still wrong"
        // case, and this is the assertion that sees it.
        val light = requireNotNull(
            measure(ThemeScheme.Light, TTDayCellTag(SELECTED_DAY), Sample.CELL_INK),
        ) { NO_READING }
        val dark = requireNotNull(
            measure(ThemeScheme.Dark, TTDayCellTag(SELECTED_DAY), Sample.CELL_INK),
        ) { NO_READING }
        println(
            "the selected day rendered ${light.ink.hex()} on ${light.fill.hex()} in light " +
                "and ${dark.ink.hex()} on ${dark.fill.hex()} in dark",
        )
        assertEquals(
            light.ink.hex(),
            dark.ink.hex(),
            "the selected day's ink is ${light.ink.hex()} in light and ${dark.ink.hex()} in " +
                "dark. It is following the appearance rather than being [ttOnAccent], which is " +
                "white in both -- so the pairing is right by luck rather than by construction, " +
                "and it will stop being legible the moment the dark accent is lightened.",
        )
    }

    @Test
    fun `the selected day's disc is visibly different from the fill around it`() {
        // Not a contrast assertion, and the reason it exists: a disc painted the same colour
        // as its surround would satisfy every ratio above and still be a calendar with no way
        // to see what is selected. The numeral's ink and the surround are the same two colours
        // either way, so this cannot be seen from an ink measurement at all.
        //
        // `CELL_FILL` readings carry the cell's own colour in `ink` — the reading's `fill` is
        // always the popup's, so comparing `.fill` between the two would compare the popup
        // with itself and always report 0.0 apart. That was the first version, and it failed
        // for exactly the right reason: it was measuring the thing it was meant to distinguish.
        for ((scheme, name) in appearances) {
            val disc = requireNotNull(
                measure(scheme, TTDayCellTag(SELECTED_DAY), Sample.CELL_FILL),
            ) { NO_READING }
            val surround = requireNotNull(
                measure(scheme, TTDayCellTag(UNSELECTED_DAY), Sample.CELL_FILL),
            ) { NO_READING }
            val distance = channelDistance(disc.ink, surround.ink)
            println(
                "$name: the accent disc ${disc.ink.hex()} against the surround's " +
                    "${surround.ink.hex()}, $distance apart",
            )
            assertTrue(
                distance >= DISC_SEPARATION_FLOOR,
                "$name appearance: the selected day's disc ${disc.ink.hex()} is only " +
                    "$distance from the fill around it (${surround.ink.hex()}), so the " +
                    "selection has no visible shape. A calendar whose selected day cannot be " +
                    "seen is a calendar nobody can use.",
            )
        }
    }

    // MARK: Measurement

    private class Reading(
        val ink: Color,
        val fill: Color,
        val ratio: Double,
    )

    private enum class Sample {
        /**
         * The glyph: the furthest pixel from the popup's fill inside the node's own bounds.
         *
         * Lands in the reading's `ink`.
         */
        INK,

        /**
         * The cell's own fill: its centre, which lands in the reading's `ink` too.
         *
         * The centre rather than a point 3dp inside a corner, because at [ttRadius.row] a
         * corner sample is mostly still the popup's fill behind the disc — which would make
         * the disc test pass by measuring the very thing it is comparing against.
         */
        CELL_FILL,

        /**
         * Ink on a cell that has a fill of its own: the background is the cell's centre, so
         * the strongest pixel found is the numeral rather than the disc behind it.
         *
         * See the selected-day test for what happens without it.
         */
        CELL_INK,
    }

    private fun measure(scheme: ThemeScheme, tag: String, sample: Sample = Sample.INK): Reading? {
        var reading: Reading? = null

        rasterising {
            setContent {
                ttTheme(scheme) {
                    Box(Modifier.fillMaxSize().background(ttCanvas())) {
                        thePopup()
                    }
                }
            }
            waitForIdle()

            val frame = onRoot().captureToImage().toPixelMap()
            val surface = onNodeWithTag(SURFACE_TAG).getBoundsInRoot()
            val node = onNodeWithTag(tag).getBoundsInRoot()
            val root = onRoot().getBoundsInRoot()
            val scale = frame.width.toFloat() / max(root.right.value, 1f)

            // No density override, unlike `GlassSurfacePixelTest`'s rim measurement.
            // `getBoundsInRoot()` reports each node in *that node's own* density, so
            // overriding `LocalDensity` leaves the root reporting one set of coordinates and
            // the surface and the text reporting half of theirs — and the scale derived from
            // the root then maps every sample onto the wrong part of the frame.
            // `StoryPointsTest` recorded what that looks like: a black-on-white numeral
            // measured at 1.56:1, because the scan was reading a card's own fill ramp.

            // The popup's fill, from its horizontal centre near its top edge. The centre and
            // not a corner, because the frame is rounded at [ttRadius.surface] and a sample
            // inside the arc reads the canvas behind it.
            val fill = pixelAt(
                pixels = frame,
                x = (surface.left.value + (surface.right.value - surface.left.value) / 2f) * scale,
                y = (surface.top.value + 6f) * scale,
            )
            val centreX = (node.left.value + (node.right.value - node.left.value) / 2f) * scale
            val centreY = (node.top.value + (node.bottom.value - node.top.value) / 2f) * scale

            val sampled = when (sample) {
                Sample.INK -> strongestPixel(
                    pixels = frame,
                    left = (node.left.value * scale).toInt(),
                    top = (node.top.value * scale).toInt(),
                    right = (node.right.value * scale).toInt(),
                    bottom = (node.bottom.value * scale).toInt(),
                    background = fill,
                )

                Sample.CELL_FILL -> pixelAt(frame, centreX, centreY)

                Sample.CELL_INK -> {
                    val disc = pixelAt(frame, centreX, centreY)
                    strongestPixel(
                        pixels = frame,
                        left = (node.left.value * scale).toInt(),
                        top = (node.top.value * scale).toInt(),
                        right = (node.right.value * scale).toInt(),
                        bottom = (node.bottom.value * scale).toInt(),
                        background = disc,
                    )
                }
            }

            // The cell's own fill is what the ink is measured against only when the cell has
            // one. `CELL_FILL` and `INK` readings both take the popup's fill, so that is what
            // every reading's `fill` reports — including `CELL_FILL`, whose *sample* is the
            // cell's colour rather than the popup's.
            val background = if (sample == Sample.CELL_INK) {
                pixelAt(frame, centreX, centreY)
            } else {
                fill
            }
            reading = Reading(sampled, background, contrastRatio(sampled, background))
        }

        return reading
    }

    /**
     * The grid on the popup's own frame, so what is measured is the composite the app ships.
     *
     * `ttSurface()` at `1f - LENS_ALPHA` under [ttGlassSurface], exactly as [TTDateField]'s
     * popup and [TTBottomSheetScaffold]'s sheet both build it. Measuring the grid over a
     * bare canvas instead would flatter it by the difference between a near-opaque
     * `ttSurface()` and the page behind it — which is the whole reason the popup has a base.
     */
    @Composable
    private fun thePopup() {
        Box(
            Modifier
                .testTag(SURFACE_TAG)
                .ttGlassSurface(
                    shape = RoundedCornerShape(ttRadius.surface),
                    base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                )
                .padding(ttSpacing.xl),
        ) {
            TTDayGrid(
                month = LocalDate(2026, Month.OCTOBER, 1),
                selected = SELECTED_DAY,
                today = LocalDate(2026, Month.OCTOBER, 20),
                onSelect = {},
            )
        }
    }

    private companion object {
        val SELECTED_DAY = LocalDate(2026, Month.OCTOBER, 4)
        val UNSELECTED_DAY = LocalDate(2026, Month.OCTOBER, 14)
        val WEEKDAY = DayOfWeek.WEDNESDAY
        const val SURFACE_TAG = "design.dateField.contrast.surface"

        val appearances = listOf(
            ThemeScheme.Light to "light",
            ThemeScheme.Dark to "dark",
        )

        /**
         * How far a selected day's disc has to be from the fill around it to count as visible.
         *
         * Not a WCAG figure — this is a shape, not text, and WCAG has nothing to say about it.
         * It sits above the difference a rasteriser produces between two composites of the
         * *same* colour at different subpixel phases, and below the distance between an
         * accent and a neutral surface, so it separates "the disc painted" from "the disc is
         * the page" without needing to be exactly right.
         */
        const val DISC_SEPARATION_FLOOR = 0.08f

        const val NO_READING =
            "this host rasterises, so a null reading means the measurement broke - the pixel " +
                "read returned nothing and the test would otherwise pass unmeasured"
    }
}