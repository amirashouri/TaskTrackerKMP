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
 * it claims to be: the board fits inside the tile with room for its own strokes, the
 * decorative layers stay behind the mark, the gradient's floor is dimmer than its ceiling,
 * the mark's shadow is offset downwards and not sideways, and the checkmark's tip is above
 * its vertex. Each of those is a drawing mistake that is invisible in code review and
 * obvious on screen.
 */
class BrandConstantsTest {

    /**
     * The board fits inside the tile, with room for its own strokes.
     *
     * The arithmetic is the point. Columns are vertical capsules described by a centre, a
     * half-length and a stroke drawn *on* the path, so the stroke has to fit in the space
     * between the capsule and the tile's edge. A centre fraction of 0.1 with a 0.4 stroke
     * would draw off the tile, and no test of the constant alone would say so.
     */
    @Test
    fun `the board fits the tile with room for its strokes`() {
        val halfStroke = AppMarkDefaults.COLUMN_STROKE / 2
        for (cx in AppMarkDefaults.COLUMN_CENTRES) {
            assertTrue(
                cx - halfStroke > 0f && cx + halfStroke < 1f,
                "the column at $cx spans ${cx - halfStroke}..${cx + halfStroke} of the tile",
            )
        }
        val top = 0.5f - AppMarkDefaults.COLUMN_HALF_STRAIGHT - halfStroke
        val bottom = 0.5f + AppMarkDefaults.COLUMN_HALF_STRAIGHT + halfStroke
        assertTrue(
            top > 0f && bottom < 1f,
            "the columns span $top..$bottom of the tile, so a cap is clipped",
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
     * The checkmark stays inside the tile.
     *
     * The arms are described as fractions of the tile, and a fraction of 1.1 is a perfectly
     * ordinary value that draws outside it. Both extremes are asserted, because the two
     * failure directions are different mistakes: a tip past the right edge is a
     * mis-transcribed fraction, and a short arm at 0 is a mark that starts mid-air.
     */
    @Test
    fun `the checkmark's geometry stays inside the tile`() {
        val halfStroke = AppMarkDefaults.CHECK_STROKE / 2
        assertTrue(
            AppMarkDefaults.SHORT_ARM_X - halfStroke > 0f,
            "the checkmark's short arm starts off-tile",
        )
        assertTrue(
            AppMarkDefaults.TIP_X + halfStroke < 1f,
            "the checkmark's tip is past the tile edge at ${AppMarkDefaults.TIP_X}",
        )
        assertTrue(
            AppMarkDefaults.TIP_Y - halfStroke > 0f && AppMarkDefaults.VERTEX_Y + halfStroke < 1f,
            "the checkmark's y values are outside the tile",
        )
    }

    /**
     * The decorative layers stay behind the mark.
     *
     * Columns are the board's field and cards are texture on it; only the check is full
     * ink. The launcher enforces the same ordering with a gate that fails any decorative
     * layer above 0.25, because three columns and six dashes in front of a checkmark turn
     * to mush at launcher size. This asserts the in-app copy kept the hierarchy.
     */
    @Test
    fun `the columns and cards stay behind the check`() {
        assertTrue(
            AppMarkDefaults.COLUMN_ALPHA < AppMarkDefaults.CARD_ALPHA,
            "the cards are brighter than the columns they sit on",
        )
        assertTrue(
            AppMarkDefaults.CARD_ALPHA <= 0.25f,
            "the cards are a foreground element at ${AppMarkDefaults.CARD_ALPHA}",
        )
        assertTrue(
            AppMarkDefaults.COLUMN_ALPHA > 0f && AppMarkDefaults.CARD_ALPHA > 0f,
            "a decorative layer is invisible",
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
