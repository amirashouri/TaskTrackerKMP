package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the invariant that the "white shadow" fix established: **a glass surface is never the
 * page at its own bottom edge.**
 *
 * ## Why this suite has to exist
 *
 * The fill is compositing arithmetic, and every defect it has had has been invisible to a test
 * that looks at a colour. `AccentContrastTest` was green while the sign-in button was a
 * near-white slab, and it was green again while the bottom third of every card and glass button
 * faded into the page — `TaskTrackerAccent.Light` really does clear 7.88:1 against the label
 * colour, and it was clearing it while the surface it was on had dissolved.
 *
 * Both defects lived in the same three lines of `ttGlassSurface`, and the reason neither was
 * caught is the real lesson: the arithmetic was **inside a `@Composable`**, so no test could
 * reach it at all. It now lives in [ttGlass.fillRamp], which is why this file can exist.
 *
 * ## What is being asserted, and what deliberately is not
 *
 * The bar is a channel separation from the canvas, and not the 3:1 non-text contrast ratio a
 * control boundary is normally held to. That is not a lower standard; it is the right one here.
 * A glass fill is *supposed* to be subtle — the whole point of the 0.22 tint floor is that it
 * should not read as a colour — so a 3:1 bar would fail the design rather than the defect. What
 * must never happen is the total one: a surface that is exactly the page has no edge, no
 * material, and no way of being seen.
 *
 * The compositing helpers are duplicated from [GlassFillContrastTest] rather than shared, for
 * the reason that file gives: a shared helper is one edit away from relaxing both suites at
 * once, and these two are the last line of defence for the fill.
 */
class GlassFillRampTest {

    /**
     * The defect itself.
     *
     * The old bottom stop was `canvas.copy(alpha = 0.30f)`. A canvas-coloured stop composites
     * to *exactly* the canvas over the canvas — not approximately, exactly, at every alpha —
     * so the last rows of every surface were the page. Measured off an emulator: a project card
     * ended at `(251,251,252)` against a `(250,250,250)` page.
     *
     * Checked against every appearance the app can paint and every `fillAlpha` any caller
     * passes, because the old arithmetic passed two of these six combinations by accident:
     * the disappearance only shows where the backdrop *is* the canvas.
     */
    @Test
    fun `the fill's bottom edge is not the page`() {
        for (appearance in appearances) {
            for (fillAlpha in fillAlphas) {
                for (tint in tints) {
                    val stops = ttGlass.fillRamp(tint, appearance.canvas, appearance.accent, fillAlpha, reducedSurface = appearance.surface)
                    val bottom = over(appearance.canvas, stops.last(), stops.last().alpha)
                    val separation = minChannelSeparation(bottom, appearance.canvas)
                    assertTrue(
                        separation >= MIN_EDGE_SEPARATION,
                        "a glass fill on the ${appearance.name} canvas with tint " +
                            "${tint?.let(::describe) ?: "none"} at fillAlpha " +
                            "$fillAlpha composites its bottom edge " +
                            "to ${describe(bottom)}, which is only $separation away from the " +
                            "${describe(appearance.canvas)} page — the surface has no edge and " +
                            "reads as a white shadow. ttGlass.FILL_FADE is ${ttGlass.FILL_FADE}; " +
                            "raising it toward 1 fades the fill into the page again",
                    )
                }
            }
        }
    }

    /**
     * Alpha is what makes glass translucent, so it cannot vary down the surface.
     *
     * This is the half of the fix that is easy to undo by accident. A vertical ramp between two
     * different alphas is a perfectly natural-looking gradient, and on a card it is
     * indistinguishable from the bug above until the bottom of the card goes missing.
     */
    @Test
    fun `the fill holds one alpha from its top edge to its bottom`() {
        for (fillAlpha in fillAlphas) {
            for (tint in tints) {
                val stops = ttGlass.fillRamp(tint, CANVAS_LIGHT, TaskTrackerAccent.Light, fillAlpha, reducedSurface = LIGHT_SURFACE)
                assertEquals(
                    stops.first().alpha,
                    stops.last().alpha,
                    "a glass fill's stops carry different alphas (${stops.first().alpha} at the " +
                        "top, ${stops.last().alpha} at the bottom), so the ramp is fading the " +
                        "material out instead of lighting it — pass one alpha for the whole fill",
                )
            }
        }
    }

    /**
     * The grammar the ramp exists for: lit where the light is, calmer below.
     *
     * Asserted as *distance from the canvas* rather than as lightness, because the accent sits
     * on the opposite side of the canvas in dark mode — it is lighter than a black canvas and
     * darker than a white one. "Closer to the canvas" is the only statement that survives both,
     * and it is what the ramp must do for the design to read as a lit surface rather than a
     * flat one.
     */
    @Test
    fun `the fill is lit at the top and calmer at the bottom`() {
        for (appearance in appearances) {
            val stops = ttGlass.fillRamp(null, appearance.canvas, appearance.accent, FILL_ALPHA_RESTING, reducedSurface = appearance.surface)
            val top = stops.first()
            val bottom = stops.last()
            val topDistance = minChannelSeparation(top, appearance.canvas)
            val bottomDistance = minChannelSeparation(bottom, appearance.canvas)
            assertTrue(
                bottomDistance < topDistance,
                "on the ${appearance.name} canvas the bottom stop ${describe(bottom)} is no " +
                    "closer to the page than the top stop ${describe(top)} " +
                    "($bottomDistance against $topDistance) — the surface has lost the lit " +
                    "gradient it is supposed to have",
            )
        }
    }

    /**
     * A caller's tint is honoured at the alpha the caller asked for.
     *
     * The knock-down to [ttGlass.TINT_ALPHA] is the caller's decision — the design system takes
     * the colour a feature already has and owns only the *how* — so a tint arriving at
     * [ttGlass.TINT_ALPHA] has to stay there rather than being replaced by [ttGlass.FILL_ALPHA].
     *
     * Compared in 8-bit channels rather than as floats, because `Color` packs its channels into
     * a `ULong` and reads them back quantised: 0.22 comes back as 56/255, which is not equal to
     * 0.22 and never will be. Anything that asserts on a channel with `assertEquals` is
     * asserting on a rounding artefact.
     */
    @Test
    fun `a caller's tint is used at its own alpha`() {
        val tint = TaskTrackerAccent.Light.copy(alpha = ttGlass.TINT_ALPHA)
        val stops = ttGlass.fillRamp(tint, CANVAS_LIGHT, TaskTrackerAccent.Light, FILL_ALPHA_RESTING, reducedSurface = LIGHT_SURFACE)

        assertEquals(
            channel(ttGlass.TINT_ALPHA),
            channel(stops.first().alpha),
            "a caller tint arrived at ${channel(ttGlass.TINT_ALPHA)} and the fill painted it at " +
                "${channel(stops.first().alpha)} — the tint floor belongs to the caller, not to " +
                "ttGlass.FILL_ALPHA",
        )
    }

    /**
     * A prominent control's stops have to be legal colours.
     *
     * [ttGlassButton] reaches [ttGlass.PROMINENT_TINT_ALPHA] by passing
     * `PROMINENT_TINT_ALPHA / FILL_ALPHA` and letting the product clamp, which used to leave
     * the stops holding an alpha above 1 — over 1 here, which is not a colour. It rendered as
     * opaque by accident, in the same way the rest of this file's history rendered as correct.
     */
    @Test
    fun `a prominent control's stops never exceed full opacity`() {
        val stops = ttGlass.fillRamp(
            tint = TaskTrackerAccent.Light,
            canvas = CANVAS_LIGHT,
            accent = TaskTrackerAccent.Light,
            fillAlpha = ttGlass.PROMINENT_TINT_ALPHA / ttGlass.FILL_ALPHA,
            reducedSurface = LIGHT_SURFACE,
        )

        for (stop in stops) {
            assertTrue(
                stop.alpha in 0f..1f,
                "a prominent glass fill is holding alpha ${stop.alpha}, which is not a colour",
            )
        }
    }

    /** [top] at [alpha] over [bottom], which is what the fill does to whatever is behind it. */
    private fun over(bottom: Color, top: Color, alpha: Float): Color = Color(
        red = top.red * alpha + bottom.red * (1f - alpha),
        green = top.green * alpha + bottom.green * (1f - alpha),
        blue = top.blue * alpha + bottom.blue * (1f - alpha),
        alpha = 1f,
    )

    /**
     * The smallest per-channel gap to [other], which is the strict reading of "not the same
     * colour": a surface that matches the page in its *lightest* channel is gone in that
     * channel regardless of how far the others travel.
     */
    private fun minChannelSeparation(color: Color, other: Color): Float = minOf(
        abs(color.red - other.red),
        abs(color.green - other.green),
        abs(color.blue - other.blue),
    ) * CHANNEL_SCALE

    /**
     * Written in integer hundredths, for the reason [GlassFillContrastTest] gives: `String.format`
     * is JVM-only and does not compile for Kotlin/Native.
     */
    private fun describe(color: Color): String {
        val red = channel(color.red)
        val green = channel(color.green)
        val blue = channel(color.blue)
        return "($red,$green,$blue)"
    }

    /** One channel as the 0-255 integer it is stored as, because it is stored as one. */
    private fun channel(value: Float): Int = round(value * CHANNEL_SCALE).toInt()

    /**
     * Carries [surface] as well as the accent and canvas, because `fillRamp` now takes the
     * neutral it arrives at when transparency is reduced. Every assertion in this file is made at
     * full intensity, where that argument cannot affect the result — it is threaded through
     * rather than defaulted because a defaulted neutral was how the accent became the end of the
     * travel, and a signature that permits the omission repeats the mistake.
     */
    private class Appearance(val name: String, val accent: Color, val canvas: Color, val surface: Color)

    /**
     * Every pairing the app can actually paint, on the same reasoning as [AccentContrastTest]: an
     * accent appears on the canvas of its own appearance, and Android's dark canvas is not iOS's,
     * so both are here rather than one representative value.
     */
    private val appearances = listOf(
        Appearance("light", TaskTrackerAccent.Light, CANVAS_LIGHT, LIGHT_SURFACE),
        Appearance("dark iOS", TaskTrackerAccent.Dark, CANVAS_DARK_IOS, DARK_SURFACE),
        Appearance("dark Android", TaskTrackerAccent.Dark, CANVAS_DARK_ANDROID, DARK_SURFACE),
    )

    /** No tint, a full-alpha tint, and a tint already knocked down to the floor. */
    private val tints = listOf<Color?>(
        null,
        TaskTrackerAccent.Light,
        TaskTrackerAccent.Light.copy(alpha = ttGlass.TINT_ALPHA),
    )

    /** The resting default, and the ratio `ttGlassButton` passes for a prominent control. */
    private val fillAlphas = listOf(
        FILL_ALPHA_RESTING,
        ttGlass.PROMINENT_TINT_ALPHA / ttGlass.FILL_ALPHA,
    )

    private companion object {
        const val FILL_ALPHA_RESTING = 1f

        const val CHANNEL_SCALE = 255f

        /** The app's own neutral, which is where a glass fill arrives at zero intensity. */
        val LIGHT_SURFACE = ttFixedColours.surface(false)
        val DARK_SURFACE = ttFixedColours.surface(true)

        val CANVAS_LIGHT = Color(0xFFFFFFFF)
        val CANVAS_DARK_IOS = Color(0xFF000000)
        val CANVAS_DARK_ANDROID = Color(0xFF121212)

        /**
         * The bar, in 8-bit channels: about 1.5% of full range.
         *
         * The defect scored exactly 0. The weakest legitimate case today is a 0.22-alpha tint on
         * Android's dark canvas at roughly 10, so this sits well clear of the design and still
         * fails the moment the ramp is allowed to reach the page again.
         */
        const val MIN_EDGE_SEPARATION = 4f
    }
}