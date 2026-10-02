package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces the two contrast bars the accent has to clear, per appearance.
 *
 * ## The pairing that matters
 *
 * An accent only ever appears on the canvas of its own appearance: [TaskTrackerAccent.Light]
 * on a light canvas, [TaskTrackerAccent.Dark] on a dark one. So the visibility bar is
 * checked against *that* canvas, not against a fixed black. Checking the light accent
 * against the dark canvas is not a stricter test, it is a test of a combination that
 * cannot occur - and it fails for a correct colour, which is how this suite's first
 * draft reported `#34537A` at 2.66 to 1 against black and looked like a token bug when
 * the token was right.
 *
 * ## The two bars
 *
 *  - at least **4.5:1** against [ttOnAccent], for the label drawn on the accent, and
 *  - at least **3:1** against the canvas it sits on, or the control carrying that label
 *    is invisible.
 *
 * The second bar is the one a checker measuring only against white would miss. Genuine
 * charcoal, roughly `#2B3037` through `#4C545D`, clears the text bar easily and fails
 * visibility.
 *
 * ## Why this reads the token rather than a file
 *
 * The original enforced this in `AccentContrastTests` by reading
 * `AccentColor.colorset/Contents.json` off disk, located from `#filePath`, because the
 * accent lived in an asset catalog rather than in code. It deliberately did *not*
 * hardcode the two hexes - a test that hardcoded them would have stayed green after
 * somebody edited the catalog, which is the exact failure it exists to catch. That test
 * reached outside its own module to solve a problem this port does not have: the accent
 * is [TaskTrackerAccent.Light] and [TaskTrackerAccent.Dark] in `commonMain`, so this
 * suite asserts the values the token actually paints. See `Accent.kt`.
 *
 * The ratios are recomputed from WCAG 2.x rather than asserted as literals, because a
 * literal ratio would be a number nobody checks: editing the accent would change the
 * real ratio and leave the assertion true. Recomputing means editing the accent moves
 * the number and this suite can fail.
 */
class AccentContrastTest {

    @Test
    fun `every accent variant clears the text contrast bar against the label colour`() {
        for (variant in listOf(light, dark)) {
            val ratio = contrastRatio(variant.color, ttOnAccent)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${variant.name} accent ${variant.color.hex()} against the label colour is " +
                    "${ratio.ratioText()} to 1, below the ${TEXT_CONTRAST_MINIMUM} to 1 " +
                    "needed for the label drawn on it",
            )
        }
    }

    @Test
    fun `every selectable palette clears text and canvas contrast in both appearances`() {
        for (palette in AccentPalette.all) {
            val variants = listOf(
                Variant("${palette.title} light", palette.light),
                Variant("${palette.title} dark", palette.dark),
            )
            for (variant in variants) {
                val againstLabel = contrastRatio(variant.color, ttOnAccent)
                assertTrue(
                    againstLabel >= TEXT_CONTRAST_MINIMUM,
                    "${variant.name} accent ${variant.color.hex()} against the label colour " +
                        "is ${againstLabel.ratioText()} to 1, below the " +
                        "${TEXT_CONTRAST_MINIMUM} to 1 needed for the label drawn on it",
                )
                assertTrue(
                    variant.color.alpha == 1f,
                    "${variant.name} accent ${variant.color.hex()} has alpha " +
                        "${variant.color.alpha}, so its contrast depends on its backdrop",
                )
            }
            assertBothBars(palette.light, CANVAS_LIGHT)
            assertBothBars(palette.dark, CANVAS_DARK_IOS)
            assertBothBars(palette.dark, CANVAS_DARK_ANDROID)
        }
    }

    @Test
    fun `the light accent clears both bars on the light canvas`() {
        assertBothBars(TaskTrackerAccent.Light, CANVAS_LIGHT)
    }

    @Test
    fun `the dark accent clears both bars on the iOS dark canvas`() {
        assertBothBars(TaskTrackerAccent.Dark, CANVAS_DARK_IOS)
    }

    /**
     * Android's dark canvas is `?android:attr/colorBackground`, which the platform themes
     * set to `#121212` rather than the pure black the iOS `CanvasBackground` colorset uses.
     * It is checked separately because it is a genuinely different colour on a genuinely
     * different target, and it moves the ratio the other way: a lighter canvas *lowers*
     * contrast, so the `#5F6873` dark variant has less margin here (3.34 to 1) than against
     * black (3.72 to 1). The bar is the same either way, and a token tuned only against
     * the iOS canvas would have a thinner margin than it looks.
     */
    @Test
    fun `the dark accent clears both bars on the Android dark canvas`() {
        assertBothBars(TaskTrackerAccent.Dark, CANVAS_DARK_ANDROID)
    }

    /**
     * The two variants have to actually differ. A refactor that collapsed
     * [TaskTrackerAccent] to one value would pass every test above - a single accent that
     * clears both bars is a perfectly legal colour - and would silently drop dark mode
     * support for the one fill in the app that carries a label.
     */
    @Test
    fun `the light and dark accents are different colours`() {
        assertTrue(
            TaskTrackerAccent.Light != TaskTrackerAccent.Dark,
            "the accent has one value for both appearances, so dark mode is not supported " +
                "for the fill that carries a label",
        )
    }

    @Test
    fun `every accent variant is fully opaque`() {
        // A translucent accent is a different colour over every canvas it lands on, and
        // the bars above would then be asserting against a canvas that is not the one the
        // user sees.
        for (variant in listOf(light, dark)) {
            assertTrue(
                variant.color.alpha == 1f,
                "${variant.name} accent ${variant.color.hex()} has alpha " +
                    "${variant.color.alpha}, so its contrast depends on whatever it is " +
                    "drawn over",
            )
        }
    }

    /** Asserts the label bar and the visibility bar of one accent-on-canvas pairing. */
    private fun assertBothBars(accent: Color, canvas: Color) {
        val againstCanvas = contrastRatio(accent, canvas)
        assertTrue(
            againstCanvas >= CONTROL_CONTRAST_MINIMUM,
            "accent ${accent.hex()} on canvas ${canvas.hex()} is ${againstCanvas.ratioText()}" +
                " to 1, below the ${CONTROL_CONTRAST_MINIMUM} to 1 needed for the control " +
                "to be visible at all",
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * WCAG 2.x relative luminance: the sRGB components are linearised first, because
     * contrast is defined in linear light and the raw 0-1 values are not.
     */
    private fun relativeLuminance(color: Color): Double {
        val red = linearise(color.red)
        val green = linearise(color.green)
        val blue = linearise(color.blue)
        return RED_COEFFICIENT * red + GREEN_COEFFICIENT * green + BLUE_COEFFICIENT * blue
    }

    private fun linearise(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= SRGB_LINEAR_THRESHOLD) {
            value / SRGB_DIVISOR
        } else {
            ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
        }
    }

    /** A variant of the accent, paired with the name used to describe it in a failure. */
    private class Variant(val name: String, val color: Color)

    private val light = Variant("light", TaskTrackerAccent.Light)
    private val dark = Variant("dark", TaskTrackerAccent.Dark)

    private fun Color.hex(): String = "#" + channel(red) + channel(green) + channel(blue)

    /**
     * One 0-1 channel as two hex digits.
     *
     * `red` is a `Float` in 0..1, so it has to be scaled to 0..255 rather than truncated:
     * `0.20f.toInt()` is 0, and a helper that did that printed every colour as `#000000`,
     * which is how a first draft of this suite reported a failing accent without saying
     * which colour had failed.
     */
    private fun channel(value: Float): String =
        round(value * 255f).toInt().toString(16).padStart(2, '0')

    /**
     * Two decimal places, written out.
     *
     * `String.format` is JVM-only, so it is not available in `commonTest` and does not
     * compile for Kotlin/Native. The rounding is done in integer hundredths, which is
     * exact across the range a contrast ratio occupies.
     */
    private fun Double.ratioText(): String {
        val hundredths = round(this * HUNDREDTHS).toInt()
        return "${hundredths / HUNDREDTHS}.${(hundredths % HUNDREDTHS).toString().padStart(2, '0')}"
    }

    private companion object {
        const val TEXT_CONTRAST_MINIMUM = 4.5
        const val CONTROL_CONTRAST_MINIMUM = 3.0

        const val HUNDREDTHS = 100

        /**
         * `UIColor.systemBackground` in light appearance, and Android's
         * `?android:attr/colorBackground` in a light platform theme. Both are white, so
         * the light canvas is one value rather than two.
         */
        val CANVAS_LIGHT = Color(0xFFFFFFFF)

        /** `CanvasBackground`'s dark appearance in the original's asset catalog. */
        val CANVAS_DARK_IOS = Color(0xFF000000)

        /** Android's `?android:attr/colorBackground` under a dark platform theme. */
        val CANVAS_DARK_ANDROID = Color(0xFF121212)

        const val SRGB_LINEAR_THRESHOLD = 0.03928
        const val SRGB_DIVISOR = 12.92
        const val SRGB_OFFSET = 0.055
        const val SRGB_SCALE = 1.055
        const val SRGB_EXPONENT = 2.4

        const val RED_COEFFICIENT = 0.2126
        const val GREEN_COEFFICIENT = 0.7152
        const val BLUE_COEFFICIENT = 0.0722
    }
}
