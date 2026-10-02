package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces that the fill a glass surface *composites to* still carries its label.
 *
 * ## Why this suite exists at all
 *
 * [AccentContrastTest] was green the entire time the sign-in button was unreadable. It is
 * green because it tests the token: `#34537A` clears 7.88:1 against the label colour, and it
 * always did. What actually reached the screen was not the token. It was a translucent wash
 * of that token, and a wash of a correct colour is not the colour — measured on an emulator,
 * the enabled button's fill composited to `#F6F7F8` and the white label sat on it at
 * **1.07:1**. A test that only ever checks the token cannot see that, because the defect is
 * in the compositing, not in the colour.
 *
 * So the assertion here is against the *result*: the accent as [ttGlass] actually paints it,
 * with the lens over it. Any change to [ttGlass.LENS_ALPHA] or to a based surface's fill now
 * moves a number this suite can fail.
 *
 * ## The WCAG maths is deliberately duplicated
 *
 * [AccentContrastTest] computes the same ratios and this file recomputes them instead of
 * sharing a helper. That is on purpose. A shared helper is a single edit away from relaxing
 * both suites at once, and these two are the last line of defence for the one fill that
 * carries text; if they ever disagree, one of them should be visibly wrong rather than
 * quietly consistent.
 */
class GlassFillContrastTest {

    /**
     * The lens is the whole risk here, so it is checked at its strongest — the top of the
     * control, where the wash has not faded yet. Checking the midpoint instead would pass
     * for any lens up to twice this strength and fail the first time someone matched it to
     * the hairline.
     */
    @Test
    fun `the lens at full strength still carries the label on every accent variant`() {
        for (variant in listOf(light, dark)) {
            val composited = over(variant.color, Color.White, ttGlass.LENS_ALPHA)
            val ratio = contrastRatio(composited, ttOnAccent)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "a based glass surface on the ${variant.name} accent composites to " +
                    "${composited.hex()} under the lens and its label would sit at " +
                    "${ratio.ratioText()} to 1, below the ${TEXT_CONTRAST_MINIMUM} to 1 " +
                    "needed to read it — lower ttGlass.LENS_ALPHA",
            )
        }
    }

    /**
     * The lens has to stay a highlight rather than becoming the fill. This is the other half
     * of the bar above: a lens strong enough to reach the label colour is not a lens, it is
     * a white button, and it would pass a test that only checked that the label is visible.
     */
    @Test
    fun `the lens stays well short of the label colour`() {
        val composited = over(TaskTrackerAccent.Light, Color.White, ttGlass.LENS_ALPHA)
        val ratio = contrastRatio(composited, ttOnAccent)
        assertTrue(
            ratio <= LENS_MAX_CONTRAST,
            "the lens composites the light accent to ${composited.hex()}, only " +
                "${ratio.ratioText()} to 1 from the label colour — at that point the control " +
                "reads as a white button with a dark label rather than as glass",
        )
    }

    /**
     * A based surface has to actually keep its base.
     *
     * The base is the reason a control can carry an opaque label at all, so a surface that
     * quietly dropped it would be the original bug wearing a different hat: the wash would
     * still composite, still look plausible, and the label would be gone.
     */
    @Test
    fun `a based surface is more opaque than the canvas it sits on`() {
        assertTrue(
            ttGlass.LENS_ALPHA < 1f,
            "the lens is opaque, so a based surface paints over its own base and the " +
                "control's label has nothing behind it",
        )
    }

    /** [top] at [alpha] over [bottom], which is what the lens does to the base. */
    private fun over(bottom: Color, top: Color, alpha: Float): Color = Color(
        red = top.red * alpha + bottom.red * (1f - alpha),
        green = top.green * alpha + bottom.green * (1f - alpha),
        blue = top.blue * alpha + bottom.blue * (1f - alpha),
        alpha = 1f,
    )

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * WCAG 2.x relative luminance. The linearisation is not optional — contrast is defined
     * in linear light, and summing the raw 0-1 channels instead reports a light grey on
     * white as roughly 1.05 to 1, which is how the original measurement came to believe a
     * `#F6F7F8` fill and a white label were the same colour.
     */
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

    private class Variant(val name: String, val color: Color)

    private val light = Variant("light", TaskTrackerAccent.Light)
    private val dark = Variant("dark", TaskTrackerAccent.Dark)

    private fun Color.hex(): String = "#" + channel(red) + channel(green) + channel(blue)

    private fun channel(value: Float): String =
        round(value * 255f).toInt().toString(16).padStart(2, '0')

    /**
     * Two decimal places, written out: `String.format` is JVM-only and does not compile for
     * Kotlin/Native, so the rounding is done in integer hundredths.
     */
    private fun Double.ratioText(): String {
        val hundredths = round(this * HUNDREDTHS).toInt()
        return "${hundredths / HUNDREDTHS}.${(hundredths % HUNDREDTHS).toString().padStart(2, '0')}"
    }

    private companion object {
        const val TEXT_CONTRAST_MINIMUM = 4.5

        /**
         * The upper end of the same bar, so the lens stays a highlight. Generous on purpose:
         * this is a guard against a lens strong enough to become the fill, not a second copy
         * of the legibility rule.
         */
        const val LENS_MAX_CONTRAST = 7.0

        const val HUNDREDTHS = 100

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
