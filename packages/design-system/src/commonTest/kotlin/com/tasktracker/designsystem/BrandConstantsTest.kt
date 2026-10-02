package com.tasktracker.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two brand components' constants, and the rules the glass file states about itself.
 *
 * ## The shape of this
 *
 * A Canvas component's *output* is a drawing, so its testable surface is its numbers. There
 * is no way to assert on what was drawn without a screenshot test, and a screenshot test on
 * a hand-drawn checkmark is a test that fails when someone changes the checkmark — which is
 * not a bug anyone was looking for.
 *
 * So these assert the properties the numbers have to have for the drawing to be the thing
 * it claims to be: the glyph fits inside the tile with room for its own stroke, the
 * gradient's floor is dimmer than its ceiling, the mark's shadow is offset downwards and
 * not sideways, and the checkmark's tip is above its vertex. Each of those is a drawing
 * mistake that is invisible in code review and obvious on screen.
 */
class BrandConstantsTest {

    /**
     * The glyph fits inside the tile, with room for the stroke.
     *
     * The arithmetic is the point. The original places a 30pt glyph in a 68pt frame, and
     * the stroke is drawn *on* the path rather than inside it — so a stroke that is a
     * fraction of the size has to fit in the space between the glyph and the tile's edge.
     * A glyph fraction of 1.0 would draw a checkmark whose stroke runs off the tile, and no
     * test of the constant alone would say so.
     */
    @Test
    fun `the glyph fits the tile with room for its stroke`() {
        val glyph = AppMarkDefaults.size.value * AppMarkDefaults.glyphFraction
        val stroke = AppMarkDefaults.size.value * AppMarkDefaults.strokeFraction
        val glyphPlusStroke = glyph + stroke

        assertTrue(
            glyphPlusStroke < AppMarkDefaults.size.value,
            "the glyph and its stroke span $glyphPlusStroke of a " +
                "${AppMarkDefaults.size.value}dp tile, so the stroke is clipped",
        )
        // The original's own proportion: a mark with a much larger glyph stops reading as a
        // mark in a small frame, and the splash is not the only place it appears.
        assertTrue(
            glyph in 28f..32f,
            "the glyph is ${glyph}dp; the original uses 30",
        )
    }

    /**
     * The mark's geometry is the original's.
     *
     * Asserted as a value because this one really is a number with no derivable property —
     * a 68dp frame and a 30dp glyph are the design's decisions, not consequences of
     * anything. The framing is the honest description of what this test is for, unlike the
     * ones above.
     */
    @Test
    fun `the frame is the original's`() {
        assertEquals(68f, AppMarkDefaults.size.value)
    }

    /** The gradient fades *out*: a floor brighter than its ceiling inverts the mark. */
    @Test
    fun `the gradient floor is dimmer than its ceiling`() {
        assertTrue(
            AppMarkDefaults.gradientFloorAlpha < 1f,
            "the gradient's second stop is fully opaque, so the mark has no fade",
        )
    }

    /**
     * The shadow is a drop, not a blur.
     *
     * `shadowY` is a vertical offset and a `0` would put the shadow directly under the mark,
     * which reads as a dark outline rather than as lift. A *negative* value would put it
     * above, which is a bug rather than a style.
     */
    @Test
    fun `the mark's shadow is offset downwards`() {
        assertTrue(
            AppMarkDefaults.shadowY.value > 0f,
            "the shadow offset is ${AppMarkDefaults.shadowY}, so it is not a drop",
        )
    }

    /**
     * The checkmark's tip is above its vertex.
     *
     * The one geometric assertion that distinguishes a checkmark from its two mirror images,
     * and therefore the one worth making. The vertex is the low point of a `v` and the tip
     * is the high point of its second arm; a checkmark drawn with the two swapped is a
     * checkmark pointing the wrong way, and every constant in the file is still in range,
     * so a bounds check would pass on it.
     */
    @Test
    fun `the checkmark's tip is above its vertex`() {
        assertTrue(
            AppMarkDefaults.TIP_Y < AppMarkDefaults.VERTEX_Y,
            "the checkmark's tip is below its vertex, so it is drawn upside down",
        )
    }

    /**
     * The checkmark stays inside the glyph box.
     *
     * The arms are described as fractions, and a fraction of 1.1 is a perfectly ordinary
     * value that draws outside the box. Both extremes are asserted, because the two
     * failure directions are different mistakes: a tip past the right edge is a
     * mis-transcribed fraction, and a short arm at 0 is a mark that starts mid-air.
     */
    @Test
    fun `the checkmark's geometry stays inside the glyph box`() {
        assertTrue(AppMarkDefaults.SHORT_ARM_X > 0f, "the checkmark's short arm starts off-box")
        assertTrue(
            AppMarkDefaults.TIP_X <= 1f,
            "the checkmark's tip is past the glyph box at ${AppMarkDefaults.TIP_X}",
        )
        assertTrue(
            AppMarkDefaults.TIP_Y > 0f && AppMarkDefaults.VERTEX_Y <= 1f,
            "the checkmark's y values are outside the glyph box",
        )
    }

    /**
     * The glass alphas are ordered the way the file says they are.
     *
     * Four numbers, and the failure mode is a material that stops looking like glass:
     * a [ttGlass.TINT_ALPHA] raised towards [ttGlass.FILL_ALPHA] turns a translucent slab
     * into a coloured one, and a [ttGlass.PROMINENT_TINT_ALPHA] at 1.0 stops the backdrop
     * showing through at all — which is the whole property the stand-in is approximating.
     */
    @Test
    fun `the glass alphas are in the order the file claims`() {
        assertTrue(
            ttGlass.TINT_ALPHA < ttGlass.FILL_ALPHA,
            "a resting tint (${ttGlass.TINT_ALPHA}) at or above the fill " +
                "(${ttGlass.FILL_ALPHA}) stops being translucent",
        )
        assertTrue(
            ttGlass.PROMINENT_TINT_ALPHA < 1f,
            "a prominent tint is opaque, so nothing shows through it",
        )
        assertTrue(
            ttGlass.PROMINENT_TINT_ALPHA > ttGlass.TINT_ALPHA,
            "a prominent button is not more prominent than a resting one",
        )
        assertTrue(
            ttGlass.HAIRLINE_ALPHA < 1f,
            "the hairline is opaque, so it draws a hard edge instead of a rim",
        )
    }

    /**
     * The press scale is a press, not a bounce.
     *
     * A value at or above 1 would make the control *grow* when pressed, and a value of
     * exactly 1 would mean no response at all — both compile, both look like a bug, and
     * neither is caught by anything else in the module.
     */
    @Test
    fun `the press scale shrinks the control`() {
        assertTrue(
            ttGlass.PRESSED_SCALE > 0f && ttGlass.PRESSED_SCALE < 1f,
            "the press scale is ${ttGlass.PRESSED_SCALE}, so the control does not respond " +
                "or grows instead",
        )
    }

    /**
     * The hairline is thinner than a pixel, which is the point.
     *
     * A hairline that rounds to a whole point is a border: it changes the size of the thing
     * it draws and it stops looking like a rim. The original specifies `lineWidth: 0.5`, and
     * this asserts the value survives the port rather than being rounded up somewhere in a
     * `Dp` conversion.
     */
    @Test
    fun `the hairline is sub-point`() {
        assertTrue(
            ttGlass.hairlineWidth.value < 1f,
            "the hairline is ${ttGlass.hairlineWidth}, which rounds to a whole point",
        )
    }
}
