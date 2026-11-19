package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Holds the four presence colours to the bars the app draws them at.
 *
 * ## Why this suite exists
 *
 * The status tokens used to be one fixed set for both appearances. Measured on the light
 * canvas, only the red cleared 4.5:1: amber was **1.94:1**, grey **2.68:1** and green
 * **3.30:1**, and the settings row painted a 15sp status *name* in them. A token that only
 * works in dark is a light-mode bug, and nothing in the suite was asking.
 *
 * ## The two bars
 *
 *  - at least **4.5:1** against the canvas of its own appearance, so a status may be drawn as
 *    text (the settings row used to), and
 *  - at least **3:1** against the card surface, because the dropdown draws each status as a
 *    12dp dot.
 *
 * The ratios are recomputed from the tokens rather than asserted as literals, so editing a
 * status value moves the number and this suite can fail.
 */
class StatusContrastTest {

    @Test
    fun `every status clears the text bar on its own canvas`() {
        for (status in STATUSES) {
            assertAtLeast(
                status.light,
                ttFixedColours.canvasLight,
                TEXT_CONTRAST_MINIMUM,
                "${status.name} light on the light canvas",
            )
            assertAtLeast(
                status.dark,
                ttFixedColours.canvasDark,
                TEXT_CONTRAST_MINIMUM,
                "${status.name} dark on the iOS dark canvas",
            )
            assertAtLeast(
                status.dark,
                ANDROID_DARK_CANVAS,
                TEXT_CONTRAST_MINIMUM,
                "${status.name} dark on the Android dark canvas",
            )
        }
    }

    @Test
    fun `every status dot stays visible on the card surface`() {
        for (status in STATUSES) {
            assertAtLeast(
                status.light,
                ttFixedColours.surfaceLight,
                LARGE_TEXT_CONTRAST_MINIMUM,
                "${status.name} light dot on the light card surface",
            )
            assertAtLeast(
                status.dark,
                ttFixedColours.surfaceDark,
                LARGE_TEXT_CONTRAST_MINIMUM,
                "${status.name} dark dot on the dark card surface",
            )
        }
    }

    /**
     * A pair that collapsed to one value would pass every ratio above and drop dark support
     * for the one vocabulary that tells a reader whether a teammate is reachable.
     */
    @Test
    fun `the light and dark statuses are different colours`() {
        for (status in STATUSES) {
            assertTrue(
                status.light != status.dark,
                "${status.name} has one value for both appearances, so dark mode is not supported",
            )
        }
    }

    private fun assertAtLeast(ink: Color, background: Color, bar: Double, pairing: String) {
        val ratio = contrastRatio(ink, background)
        assertTrue(
            ratio >= bar,
            "$pairing is ${ratio.ratioText()} to 1 " +
                "(${ink.hex()} on ${background.hex()}), below the ${bar.ratioText()} to 1 bar",
        )
    }

    private class Status(val name: String, val light: Color, val dark: Color)

    private companion object {
        /** Android's `?android:attr/colorBackground` under a dark platform theme. */
        val ANDROID_DARK_CANVAS = Color(0xFF121212)

        val STATUSES = listOf(
            Status("active", ttFixedColours.statusActiveLight, ttFixedColours.statusActiveDark),
            Status(
                "be-right-back",
                ttFixedColours.statusBeRightBackLight,
                ttFixedColours.statusBeRightBackDark,
            ),
            Status("busy", ttFixedColours.statusBusyLight, ttFixedColours.statusBusyDark),
            Status("away", ttFixedColours.statusAwayLight, ttFixedColours.statusAwayDark),
        )
    }
}
