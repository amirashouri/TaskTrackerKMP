package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the user's glass slider: that `1f` is the app as it shipped, and that `0f` is Reduce
 * Transparency rather than the absence of a surface.
 *
 * ## Why this is a plain unit test and not a rendered-pixel one
 *
 * The slider's whole decision is an interpolation between two alphas, and an interpolation is
 * arithmetic a test can read exactly — the same reason [ttGlass.fillRamp] was extracted out of
 * a `@Composable` in the first place. What a unit test *cannot* establish is how those alphas
 * land once the compositor has blended them over a real backdrop, and that is not what this
 * file claims; the rendered end is measured in `GlassIntensityRenderTest`.
 *
 * ## Why the direction is asserted rather than assumed
 *
 * The mapping inverts: **more glass means a lower fill alpha**, because glass is see-through
 * and a solid fill is what Reduce Transparency means. An implementation that read "more glass"
 * as "more of the material's tokens" and therefore raised every alpha would still produce a
 * plausible-looking ramp that fails these assertions, which is exactly the mistake worth
 * catching in a test rather than in a screenshot.
 */
class GlassIntensityTest {

    /**
     * The top of the slider is the app that already shipped, unchanged.
     *
     * This is the load-bearing assertion of the whole feature: every measured number in
     * `ttGlass` was measured at full intensity, so if `1f` moved by even one float step the
     * measured contrast in `GlassFillContrastTest` and the ramp in `GlassFillRampTest` would
     * be describing a configuration the app no longer paints.
     */
    @Test
    fun `full intensity is the fill that shipped`() {
        val stops = ttGlass.fillRamp(
            tint = null,
            canvas = CANVAS,
            accent = ACCENT,
            fillAlpha = 1f,
            intensity = 1f,
            reducedSurface = REDUCED,
        )
        assertEquals(
            channel(ttGlass.FILL_ALPHA),
            channel(stops.first().alpha),
            "an untinted fill at full intensity is the measured FILL_ALPHA, not something near it",
        )
        assertEquals(
            channel(ttGlass.FILL_ALPHA),
            channel(stops.last().alpha),
            "the fill's alpha is constant top to bottom — see FILL_FADE's doc on why",
        )
    }

    /**
     * A caller tint at full intensity keeps its own alpha as the floor.
     *
     * The knock-down to [ttGlass.TINT_ALPHA] is the caller's decision, so a tint that arrives
     * at 0.22 must be painted at 0.22 when the slider is at the top — otherwise a tinted card
     * would quietly darken or lighten for every user who had never opened Settings.
     */
    @Test
    fun `a caller tint keeps its own floor at full intensity`() {
        val tint = ACCENT.copy(alpha = ttGlass.TINT_ALPHA)
        val stops = ttGlass.fillRamp(tint, CANVAS, ACCENT, fillAlpha = 1f, intensity = 1f, reducedSurface = REDUCED)
        assertEquals(
            channel(ttGlass.TINT_ALPHA),
            channel(stops.first().alpha),
            "a tinted fill at full intensity is the tint's own alpha, the arithmetic that shipped",
        )
    }

    /**
     * The bottom of the slider is Reduce Transparency: opaque, and no ramp.
     *
     * Two things at once, and both matter. Opaque, because "less glass" means a *solid* fill —
     * a fill scaled towards zero is a hole in the page, which is the failure
     * `GlassFillRampTest` exists to catch and would be reintroduced from the other direction.
     * And un-ramped, because the fade models light falling across a translucent surface; over
     * an opaque one it is a gradient over nothing.
     */
    @Test
    fun `no intensity is opaque with no ramp`() {
        for (tint in listOf(null, ACCENT.copy(alpha = ttGlass.TINT_ALPHA))) {
            val stops = ttGlass.fillRamp(
                tint = tint,
                canvas = CANVAS,
                accent = ACCENT,
                fillAlpha = 1f,
                intensity = 0f,
                reducedSurface = REDUCED,
            )
            assertEquals(
                255,
                channel(stops.first().alpha),
                "at zero intensity the fill is solid whatever the tint was — tint=$tint",
            )
            assertEquals(
                stops.first(),
                stops.last(),
                "at zero intensity the two stops are one flat colour, because the fade is the " +
                    "light falling across a translucent fill and there is no translucency left " +
                    "to light — tint=$tint",
            )
        }
    }

    /**
     * The bottom of the travel is the app's neutral, and specifically **not** the accent.
     *
     * This is the assertion for a defect that shipped and was reported rather than designed:
     * fading only the fill's *alpha* toward 1 turns every surface into an opaque slab of the
     * accent, so dragging the slider down a little made the whole app darken and take on a
     * colour cast. Measured in Light appearance at 20% intensity, the fill was an 0.86-alpha
     * `#34537a` compositing to about `#506b8c` over the page. Reducing transparency is supposed
     * to remove the accent, not amplify it — the accent is the glass vocabulary.
     *
     * Both halves are needed. The first says the fill arrives at the neutral; the second says it
     * did not merely *also* be near it. The distance is reported in channels so the failure
     * message carries the number rather than "expected X but was Y" on two colours that look
     * alike in a diff.
     */
    @Test
    fun `no intensity is the neutral surface and not the accent`() {
        val stops = ttGlass.fillRamp(
            tint = null,
            canvas = CANVAS,
            accent = ACCENT,
            fillAlpha = 1f,
            intensity = 0f,
            reducedSurface = REDUCED,
        )
        val painted = stops.first()
        assertEquals(
            listOf(channel(REDUCED.red), channel(REDUCED.green), channel(REDUCED.blue)),
            listOf(channel(painted.red), channel(painted.green), channel(painted.blue)),
            "at zero intensity the fill is the neutral opaque surface " +
                "(${hex(REDUCED)}), not the accent (${hex(ACCENT)})",
        )
        val span = channelDistance(REDUCED, ACCENT)
        val fromAccent = channelDistance(painted, ACCENT)
        assertTrue(
            fromAccent > span / 2,
            "at zero intensity the fill is $fromAccent channels from the accent ${hex(ACCENT)}, " +
                "which is under half of the $span channels between the neutral and the accent — " +
                "so it is still substantially accent-coloured and lowering the slider is tinting " +
                "the app instead of reducing its transparency",
        )
    }

    /**
     * Part of the way down, the fill is still on the neutral side of the accent.
     *
     * The 20% mark is where the defect was reported, and it is the worst place for an accent
     * cast: the alpha has already risen to 0.86, so a surface that is both nearly opaque *and*
     * accent-coloured dominates the page, while a user who has barely moved the control would
     * not expect the app's colour to have changed at all. Enforced at 0.2 rather than at 0
     * because `0f` is the labelled end of the slider and is easy to get right by construction.
     */
    @Test
    fun `a fifth of the way down is still nearer the neutral than the accent`() {
        val fifth = 0.2f
        val painted = ttGlass.fillRamp(
            tint = null,
            canvas = CANVAS,
            accent = ACCENT,
            fillAlpha = 1f,
            intensity = fifth,
            reducedSurface = REDUCED,
        ).first()
        val toNeutral = channelDistance(painted, REDUCED)
        val toAccent = channelDistance(painted, ACCENT)
        assertTrue(
            toNeutral < toAccent,
            "at intensity $fifth the fill is $toNeutral channels from the neutral ${hex(REDUCED)} " +
                "and $toAccent from the accent ${hex(ACCENT)} — it has crossed most of the way " +
                "to the accent while the user has only lowered the slider to 20%, which is the " +
                "colour cast this feature must not have",
        )
    }

    /**
     * Alpha rises as intensity falls, all the way down.
     *
     * Asserted as a direction over every step rather than at two ends, because the ends are
     * the easy part: an implementation that got `0f` and `1f` right and interpolated
     * backwards in between would pass an ends-only test and invert the control for every value
     * in between, which is 99 of the 101 positions the slider offers.
     */
    @Test
    fun `alpha falls monotonically as intensity rises`() {
        var previous = Float.MAX_VALUE
        var first = 0f
        var last = 0f
        var step = 0f
        while (step <= 1f) {
            val alpha = ttGlass.fillRamp(null, CANVAS, ACCENT, 1f, intensity = step, reducedSurface = REDUCED).first().alpha
            assertTrue(
                alpha <= previous + ONE_CHANNEL,
                "at intensity $step the alpha is $alpha, which is above the previous step's " +
                    "$previous — more glass must never mean a more opaque fill",
            )
            if (step == 0f) first = alpha
            last = alpha
            previous = alpha
            step += 0.05f
        }
        // The strict half, and it is not redundant with the loop above. Non-increasing is
        // satisfied by a *constant* alpha, so an implementation that ignored the intensity
        // entirely — the exact mutation this suite exists to catch — passed the loop while
        // doing nothing. The control is only useful if moving it changes the fill, so the
        // two ends have to actually differ.
        assertTrue(
            first - last > ONE_CHANNEL,
            "the slider's full travel moved the fill alpha from $first to $last, which is not " +
                "a visible difference — a control whose ends look the same is not a control",
        )
    }

    /**
     * A value from outside `0f..1f` is brought into range rather than extrapolated.
     *
     * `lerp` walks past its endpoints without complaining, so an unclamped intensity from the
     * store or from a future caller would produce an alpha above 1 and a fill the rest of the
     * drawing code has no path for. The clamp lives in two places — the mapping and the
     * preference — and this holds the mapping's half of it.
     */
    @Test
    fun `an out of range intensity is clamped and not extrapolated`() {
        val below = ttGlass.fillRamp(null, CANVAS, ACCENT, 1f, intensity = -3f, reducedSurface = REDUCED).first().alpha
        val above = ttGlass.fillRamp(null, CANVAS, ACCENT, 1f, intensity = 7f, reducedSurface = REDUCED).first().alpha
        assertEquals(
            255,
            channel(below),
            "a negative intensity is Reduce Transparency, not a negative fill",
        )
        assertEquals(
            channel(ttGlass.FILL_ALPHA),
            channel(above),
            "an intensity above one is the same as one, not a fill more transparent than glass",
        )
    }

    /**
     * The fade is the measured one at full intensity, and it is the *production* one.
     *
     * Written against the two stops [ttGlass.fillRamp] actually returns and against
     * `lerp(top, canvas, FILL_FADE)` rather than against a local reimplementation of
     * `FILL_FADE * intensity`, because a test that recomputes the formula it is meant to be
     * checking agrees with a broken formula exactly as often as a correct one.
     */
    @Test
    fun `the ramp fades by the measured amount at full intensity`() {
        val tint = ACCENT.copy(alpha = ttGlass.TINT_ALPHA)
        val stops = ttGlass.fillRamp(tint, CANVAS, ACCENT, fillAlpha = 1f, intensity = 1f, reducedSurface = REDUCED)
        val expectedBottom = lerp(tint, CANVAS, ttGlass.FILL_FADE).copy(alpha = tint.alpha)
        assertEquals(
            channel(expectedBottom.red),
            channel(stops.last().red),
            "the bottom stop is the top stop FILL_FADE of the way to the canvas",
        )
        assertEquals(
            channel(expectedBottom.green),
            channel(stops.last().green),
            "the ramp is toward the canvas in every channel, not just red",
        )
        assertTrue(
            stops.last().red != stops.first().red,
            "and the two stops must actually differ, or the ramp assertion above is vacuous",
        )
    }

    /**
     * One channel as the 0-255 integer it is stored as, because it is stored as one.
     *
     * `Color` packs its channels into a `ULong` and reads them back quantised, so 0.55 comes
     * back as 140/255 and is not equal to 0.55 at any tolerance tighter than a channel. Every
     * alpha assertion here goes through this rather than comparing floats — the same reason
     * `GlassFillRampTest` does, and an assertion that pins the rounding artefact instead of
     * the value is a test that fails when Compose changes and passes when the fill breaks.
     */
    private fun channel(value: Float): Int = kotlin.math.round(value * CHANNEL_SCALE).toInt()

    /**
     * Distance between two colours in 8-bit channel space, worst channel rather than Euclidean.
     *
     * Worst channel, because a colour cast shows up as *one* channel moving far — an accent
     * bleeding in reads as red collapsing long before blue does — and a Euclidean sum would let a
     * small drift in two channels add up to a distance that looks like the defect when it is not.
     */
    private fun channelDistance(a: Color, b: Color): Int = maxOf(
        kotlin.math.abs(channel(a.red) - channel(b.red)),
        kotlin.math.abs(channel(a.green) - channel(b.green)),
        kotlin.math.abs(channel(a.blue) - channel(b.blue)),
    )

    /** `0xFF34537a` rather than `Color(r=.., g=..)`, so a failure message can be pasted anywhere. */
    private fun hex(color: Color): String =
        "#" + channel(color.red).toString(16).padStart(2, '0') +
            channel(color.green).toString(16).padStart(2, '0') +
            channel(color.blue).toString(16).padStart(2, '0')

    private companion object {
        const val CHANNEL_SCALE = 255f
        val CANVAS: Color = Color(0xFFFAFAFA)
        val ACCENT: Color = Color(0xFF526C8E)

        /** The app's own neutral for a solid surface — the Reduce Transparency destination. */
        val REDUCED: Color = ttFixedColours.surface(false)
        const val ONE_CHANNEL = 1f / 255f
    }
}