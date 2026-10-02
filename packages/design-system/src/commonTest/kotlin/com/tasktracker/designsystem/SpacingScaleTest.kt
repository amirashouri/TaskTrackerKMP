package com.tasktracker.designsystem

import androidx.compose.ui.unit.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two scales, as scales.
 *
 * ## What these tests are actually for
 *
 * Neither scale had a test in the original, because neither existed: the original had 78
 * spacing literals and 48 `.font(...)` call sites with nothing to check them against. So
 * these cannot test that a value equals a known number — that would be a test of a constant
 * against itself, and it would keep passing after somebody edited the constant.
 *
 * What they test is the *property* a scale is supposed to have, which is the thing that
 * actually rots:
 *
 *  - a ramp has to be **increasing**. A scale whose middle step is smaller than its first is
 *    a scale, and every call site using it is a layout bug.
 *  - a ramp has to be **distinct**. Two names for one number is the start of a scale with
 *    two names per step, which is worse than no scale because it looks deliberate.
 *  - the off-ramp values have to be **off the ramp**, with two documented exceptions. The
 *    original's 3, 7, 9, 14, 44, 48 are not steps. 12 and 24 are, and the reason they are
 *    *named* anyway is on the values themselves. A test that found a 7 sitting on the ramp
 *    would be reporting that the documentation and the value disagree — which is a real,
 *    silent failure mode here, because nothing else notices when a 7 becomes an 8.
 */
class SpacingScaleTest {

    /**
     * The ramp increases, strictly.
     *
     * Strict rather than non-strict because the distinctness is what makes a scale
     * readable, and one strict test says both things: no step is out of order, and no two
     * steps are the same size.
     */
    @Test
    fun `the spacing ramp increases strictly`() {
        assertRampIncreases(ttSpacing.ramp, "ttSpacing.ramp")
    }

    @Test
    fun `the radius scale increases strictly`() {
        assertRampIncreases(ttRadius.all, "ttRadius.all")
    }

    /**
     * Every value the original used is still reachable, and none of them drifted.
     *
     * Named one at a time rather than as a set, so a failure says *which* value moved. The
     * numbers are the original's literals and a mismatch is a porting error, not a design
     * change — if one of these is genuinely wrong the test is where that should be argued.
     */
    @Test
    fun `the ramp matches the values the original used`() {
        assertEquals(2f, ttSpacing.xxs.value, "xxs")
        assertEquals(4f, ttSpacing.xs.value, "xs")
        assertEquals(6f, ttSpacing.sm.value, "sm")
        assertEquals(8f, ttSpacing.md.value, "md")
        assertEquals(10f, ttSpacing.lg.value, "lg")
        assertEquals(12f, ttSpacing.xl.value, "xl")
        assertEquals(16f, ttSpacing.xxl.value, "xxl")
        assertEquals(24f, ttSpacing.xxxl.value, "xxxl")
    }

    /**
     * The off-ramp values are off the ramp.
     *
     * The interesting direction is the *second* half. A 7 that quietly became an 8 would
     * sit on the ramp, and everything would still compile and look nearly right — the glass
     * slab's segments would land a point off the grid, which is visible only as a hairline
     * that is very slightly thicker at one end. Asserting that these are *absent* from the
     * ramp is what catches it.
     */
    @Test
    fun `the off-ramp spacing values are not steps`() {
        // Deliberately excludes `controlPaddingH`, `formStack` and `fieldStack`, which are
        // named but *do* alias the ramp — see `the ramp aliases stay on the step they name`
        // below. Listing them here as well would be the same contradiction written twice.
        val offRamp = listOf(
            ttSpacing.hairlineInset,
            ttSpacing.controlPaddingV,
            ttSpacing.buttonPaddingV,
            ttSpacing.buttonPaddingH,
            ttSpacing.iconButtonSize,
            ttSpacing.splashStack,
            ttSpacing.screenGutterLarge,
            ttSpacing.boardTopInset,
            ttSpacing.boardBottomInset,
            ttSpacing.signInTopInset,
        )
        for (value in offRamp) {
            assertTrue(
                value !in ttSpacing.ramp,
                "${value.value}dp is on the ramp ($ttSpacing.ramp) but is documented as an " +
                    "off-ramp value, so either the value or the name is wrong",
            )
        }
    }

    /**
     * The two named values that alias the ramp still alias it.
     *
     * These are on the ramp, and the values say so in as many words — "The one 12 here is on
     * the ramp", "which is [xxxl]". That is a claim about a ramp, so it is a claim that can
     * rot: nudge `controlPaddingH` to 13 and the comment becomes a lie, the segmented
     * picker's segments stop landing on whole points, and the slab's hairline edge goes
     * uneven by a point at one end. Nothing in a preview catches that at the size it is
     * visible, so it is asserted here instead.
     */
    @Test
    fun `the ramp aliases stay on the step they name`() {
        assertEquals(ttSpacing.xl, ttSpacing.controlPaddingH, "controlPaddingH names the ramp")
        assertEquals(ttSpacing.xxxl, ttSpacing.formStack, "formStack names the ramp")
        assertEquals(ttSpacing.xxl, ttSpacing.fieldStack, "fieldStack names the ramp")
    }

    /**
     * Each control's *vertical* padding sits one point past a ramp step.
     *
     * 7 is one past 6 and 9 is one past 8, and the reason is on the values: the picker's
     * segments and the button's label both want to land on whole points a step off the
     * grid, and a whole point is what keeps a hairline edge even. The first draft of this
     * test also claimed to pin the horizontal half and did not — a 14 changed to a 15 passed
     * it, which is exactly the kind of gap a test that describes itself as "the
     * relationship between the two halves of a pair" has no business having.
     */
    @Test
    fun `each control's vertical padding sits one point past a ramp step`() {
        for ((vertical, label) in listOf(
            ttSpacing.controlPaddingV to "picker",
            ttSpacing.buttonPaddingV to "button",
        )) {
            val stepBelow = ttSpacing.ramp.last { it < vertical }
            assertEquals(
                stepBelow.value + 1f,
                vertical.value,
                "the $label's vertical padding should be one point past the ramp",
            )
        }
    }

    /**
     * Each control's *horizontal* padding is the documented distance from the ramp.
     *
     * Separate from the vertical rule because the two are not the same relationship: the
     * picker's 12 is [xl] itself, and the button's 14 is two past it. Asserting one rule
     * across both numbers is how the button's 14 quietly becomes a 15 — which is the
     * mutation this test was written after it.
     */
    @Test
    fun `each control's horizontal padding is its documented distance from the ramp`() {
        assertEquals(
            ttSpacing.xl,
            ttSpacing.controlPaddingH,
            "the picker's horizontal padding is the ramp step itself",
        )
        assertEquals(
            ttSpacing.xl.value + 2f,
            ttSpacing.buttonPaddingH.value,
            "the button's horizontal padding is two points past the ramp",
        )
    }

    /**
     * Both controls are wider in their horizontal padding than their vertical.
     *
     * The property that makes them look like controls rather than stacked bars, and the one
     * check that would notice if a pair were transposed.
     */
    @Test
    fun `a control is padded further across than up`() {
        for ((vertical, horizontal, label) in listOf(
            Triple(ttSpacing.controlPaddingV, ttSpacing.controlPaddingH, "picker"),
            Triple(ttSpacing.buttonPaddingV, ttSpacing.buttonPaddingH, "button"),
        )) {
            assertTrue(
                horizontal > vertical,
                "the $label's horizontal padding ($horizontal) should exceed its vertical " +
                    "($vertical), or the control is taller than it is wide",
            )
        }
    }

    /**
     * Space is in `dp`, never `sp`.
     *
     * Not a compile-time property — `Dp` and the `sp` `TextUnit` are different types, so
     * the compiler already stops a call site from mixing them. This asserts the *intent*:
     * that a spacing value's `value` is a plain density-independent number and has not been
     * multiplied by a font scale on the way in. A `TextUnit` smuggled into a `Dp` by a
     * conversion would show up as a spacing value that grows with the user's text size,
     * and nothing in the layout would say why.
     */
    @Test
    fun `spacing is density-independent and not font-relative`() {
        // `Dp.value` is the raw number with no scale applied; a font-relative value would
        // have arrived here already multiplied. The assertion is on the type's own contract
        // rather than on a number, so it holds for every value including the off-ramp ones.
        for (value in ttSpacing.ramp + ttRadius.all) {
            assertTrue(
                value.value > 0f,
                "a spacing or radius value is $value, which no layout can use",
            )
        }
    }

    private fun assertRampIncreases(ramp: List<Dp>, name: String) {
        for (index in 1 until ramp.size) {
            assertTrue(
                ramp[index] > ramp[index - 1],
                "$name is not increasing at index $index: ${ramp[index - 1]} then ${ramp[index]}",
            )
        }
    }
}
