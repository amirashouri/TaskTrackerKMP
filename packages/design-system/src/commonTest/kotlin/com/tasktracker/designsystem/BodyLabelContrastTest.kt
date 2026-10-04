package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `ttBodyLabel` keeps the promise its own KDoc makes.
 *
 * ## Why this test exists at all
 *
 * `ttBodyLabel` was added with an `expect`, three `actual`s and a long argument for why the
 * design system needed a fourth semantic role — and then **nothing asserted any of it**. Its
 * KDoc claims "White ink over an opaque dark surface is 17:1 in both appearances, and black ink
 * over an opaque light surface is the same", which is a number nobody was checking, and which
 * `AccentContrastTest` does not reach because that suite is about the accent.
 *
 * So the token sat there: documented, unreferenced by any call site, and unverified. A token
 * in that state is worse than a missing one, because its KDoc reads as a guarantee.
 *
 * ## What was adopted because of it
 *
 * Two call sites now use it, both where the text is *load-bearing* rather than decorative:
 *
 *  - `LabelledField`'s field title, drawn on the field's own opaque `ttSurface()`;
 *  - `ProjectsEmptyState`'s body, the only copy on an empty screen.
 *
 * Both were on `ttLabelSecondary()`, which measures **3.30:1** on the light surface and
 * **3.44:1** on the light canvas — below 4.5:1. A word naming an input and the single message
 * an empty screen exists to deliver were both under the body-text bar.
 *
 * ## What was deliberately *not* adopted
 *
 * The field's **placeholder** stays on `ttLabelSecondary()`, because a placeholder is a hint
 * about what could be typed and full ink makes an empty field look filled. And the ~50
 * `ttLabelSecondary` calls that sit on **glass** are untouched: over a glass card that token
 * measures **2.81:1** in light, which is a real defect, but fixing it needs rendered pixels on
 * both rasterisers (`PLAN.md` 19.9) and swapping in full ink across every caption would flatten
 * a deliberate hierarchy rather than fix a number.
 */
class BodyLabelContrastTest {

    @Test
    fun `body ink clears the text bar on the card surface in both appearances`() {
        for (dark in listOf(false, true)) {
            val surface = ttFixedColours.surface(dark)
            val ink = ttFixedColours.bodyLabel(dark)
            val ratio = contrastRatio(ink, surface)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${if (dark) "dark" else "light"} appearance: ttBodyLabel is ${ink.hex()} on " +
                    "the ${surface.hex()} card surface, which is ${ratio.ratioText()} to 1 - " +
                    "below the ${TEXT_CONTRAST_MINIMUM} to 1 its KDoc claims. The token's whole " +
                    "argument is that this is the pairing that is honest, so a number that " +
                    "fails here invalidates the reason the token exists.",
            )
        }
    }

    @Test
    fun `body ink also clears the text bar on the canvas where empty states sit`() {
        for (dark in listOf(false, true)) {
            val canvas = ttFixedColours.canvas(dark)
            val ink = ttFixedColours.bodyLabel(dark)
            val ratio = contrastRatio(ink, canvas)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${if (dark) "dark" else "light"} appearance: ttBodyLabel on the canvas is " +
                    "${ratio.ratioText()} to 1, below ${TEXT_CONTRAST_MINIMUM} to 1",
            )
        }
    }

    /**
     * Body ink is dimmer than the label, or it is not body ink.
     *
     * The relationship, not the numbers. `ttLabel` is the app's primary ink and `ttBodyLabel`
     * is a step below it; if the two ever became the same colour the hierarchy would be gone
     * while every individual ratio stayed passing, which is the failure a per-value assertion
     * cannot see.
     */
    @Test
    fun `body ink is a step below the primary label rather than equal to it`() {
        for (dark in listOf(false, true)) {
            val label = contrastRatio(ttFixedColours.label(dark), ttFixedColours.canvas(dark))
            val body = contrastRatio(
                ttFixedColours.bodyLabel(dark),
                ttFixedColours.canvas(dark),
            )
            assertTrue(
                body < label,
                "${if (dark) "dark" else "light"} appearance: ttBodyLabel (${body.ratioText()}:1) " +
                    "is not dimmer than ttLabel (${label.ratioText()}:1), so there is no hierarchy " +
                    "left between a title and its supporting text",
            )
        }
    }

    /** The two appearances must differ, for the same reason `AccentContrastTest` insists on it. */
    @Test
    fun `body ink is not one colour for both appearances`() {
        assertTrue(
            ttFixedColours.bodyLabelLight != ttFixedColours.bodyLabelDark,
            "ttBodyLabel has one value for both appearances, so dark mode paints near-black ink " +
                "on a near-black surface",
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearise(color.red) +
            0.7152 * linearise(color.green) +
            0.0722 * linearise(color.blue)

    private fun linearise(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= SRGB_LINEAR_THRESHOLD) value / SRGB_DIVISOR
        else ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
    }

    /** `String.format` is JVM-only, so the ratio is written out in integer hundredths. */
    private fun Double.ratioText(): String {
        val hundredths = round(this * 100).toInt()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }

    private fun Color.hex(): String =
        "#" + listOf(red, green, blue).joinToString("") {
            round(it * 255f).toInt().toString(16).padStart(2, '0')
        }

    private companion object {
        const val TEXT_CONTRAST_MINIMUM = 4.5

        const val SRGB_LINEAR_THRESHOLD = 0.03928
        const val SRGB_DIVISOR = 12.92
        const val SRGB_OFFSET = 0.055
        const val SRGB_SCALE = 1.055
        const val SRGB_EXPONENT = 2.4
    }
}