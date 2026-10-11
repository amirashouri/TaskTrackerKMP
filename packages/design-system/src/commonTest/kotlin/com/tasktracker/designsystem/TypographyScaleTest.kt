package com.tasktracker.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The type scale, as a scale.
 *
 * ## The property, not the values
 *
 * [ttTypography.all] is ordered smallest to largest, and that ordering is the scale. A
 * named list of font sizes is not a scale; what makes it one is that a reader can answer
 * "which of these do I want?" by name without reading the number. So this suite does not
 * assert that `body` is 17sp — asserting a constant against itself is how a scale gets
 * "protected" from the edit that fixes it. It asserts:
 *
 *  - sizes **never decrease** along [ttTypography.all], so a role added in the wrong place
 *    is caught;
 *  - the two **intentional** size pairs share a size and differ only in weight, because
 *    they are selected/unselected and emphasised/plain, and a copy-paste that gave them the
 *    same weight would leave a "selected" state with nothing selected about it;
 *  - every size is a whole number of `sp`, so a role does not land on a 16.5sp that rounds
 *    differently per platform and produces a baseline that drifts by a pixel.
 */
class TypographyScaleTest {

    /**
     * Sizes never decrease.
     *
     * Non-strict, unlike the spacing ramp. The two selected/plain pairs share a size by
     * design — that is what makes them a pair rather than two roles — so a strict
     * comparison would fail on a correct scale and the fix would be to break the pairing.
     */
    @Test
    fun `type sizes never decrease along the scale`() {
        for (index in 1 until ttTypography.all.size) {
            val previous = ttTypography.all[index - 1]
            val current = ttTypography.all[index]
            assertTrue(
                current.fontSize.value >= previous.fontSize.value,
                "the type scale decreases at index $index: " +
                    "${previous.fontSize} then ${current.fontSize}",
            )
        }
    }

    /**
     * A role is not a duplicate of the role above it.
     *
     * "Above it" rather than "any other", because the two pairs make duplicates correct and
     * *only* the pairs. Two adjacent roles that match in size, weight, and style is a
     * copy-paste; the scale then has two names for one appearance, which is the exact
     * ambiguity a scale exists to remove.
     */
    @Test
    fun `a role is not an exact copy of its neighbour`() {
        for (index in 1 until ttTypography.all.size) {
            val previous = ttTypography.all[index - 1]
            val current = ttTypography.all[index]
            assertTrue(
                current.fontSize != previous.fontSize || current.fontWeight != previous.fontWeight,
                "two adjacent roles are identical in size and weight at index $index, so the " +
                    "scale has two names for one appearance",
            )
        }
    }

    /**
     * The two selected/plain pairs are related the way they are meant to be.
     *
     * Same size, heavier weight, and only one of the two is heavy. Asserted per pair so a
     * failure names which one broke: "selected" that is not heavier than "unselected" is a
     * selection indicator that inverts, and "emphasised" that is not heavier is a caption
     * that looks like an apology.
     */
    @Test
    fun `the selected and emphasised variants are heavier at the same size`() {
        for ((plain, variant, label) in listOf(
            Triple(
                ttTypography.subheadline,
                ttTypography.subheadlineSelected,
                "subheadlineSelected",
            ),
            Triple(ttTypography.caption, ttTypography.captionEmphasised, "captionEmphasised"),
        )) {
            assertEquals(
                plain.fontSize,
                variant.fontSize,
                "$label should be the same size as its base, or the two are not a pair",
            )
            assertTrue(
                (variant.fontWeight?.weight ?: 0) > (plain.fontWeight?.weight ?: 0),
                "$label should be heavier than its base",
            )
        }
    }

    /**
     * Every size lands on a whole point.
     *
     * A fractional size rounds to different actual pixel sizes on each platform, so the
     * same text sits on a different baseline in the iOS and Android builds of the same
     * screen. Nothing in a preview of one platform shows it.
     */
    @Test
    fun `every size is a whole number of points`() {
        for (style in ttTypography.all) {
            val size = style.fontSize.value
            assertEquals(
                size,
                kotlin.math.floor(size),
                "a type size of $size.sp rounds differently per platform",
            )
        }
    }

    /**
     * Every role's line height is at least its font size.
     *
     * The one that bites hardest: a style whose `lineHeight` is below its `fontSize` has
     * nowhere to put its ascender and descender, so two lines of it overlap by the
     * difference. At `captionSmall` that is a hairline, which is why it survives review and
     * why a test is the right place for it — the eye is bad at noticing a 4sp error in a
     * 1.5%-of-the-screen role.
     */
    @Test
    fun `no role's line height is tighter than its font size`() {
        for (style in ttTypography.all) {
            assertTrue(
                style.lineHeight >= style.fontSize,
                "a style has lineHeight ${style.lineHeight} below fontSize ${style.fontSize}, " +
                    "so consecutive lines overlap",
            )
        }
    }

    /**
     * The body roles are the ones that wrap, so they are the ones that need leading.
     *
     * Narrower than the rule above, and deliberately: [largeTitle] legitimately sits tighter
     * than a body role would, because it is a single line that should not have a paragraph
     * of leading under it. Asserting "every style is airy" would be asserting a house style
     * this scale does not have.
     */
    @Test
    fun `the wrapping roles are set with leading`() {
        for (style in listOf(
            ttTypography.body,
            ttTypography.callout,
            ttTypography.footnote,
            ttTypography.subheadline,
        )) {
            assertTrue(
                style.lineHeight > style.fontSize,
                "a wrapping role is set at ${style.fontSize}/${style.lineHeight}, which is " +
                    "tighter than its own font size",
            )
        }
    }

    /**
     * The monospace helpers change width behaviour, and only that.
     *
     * [ttMonospaced] and [ttMonospacedDigits] both exist to stop text changing width as its
     * content changes. A helper that also changed the weight or the size would be a
     * different helper wearing this one's name — and the size one is worth catching, since a
     * monospaced font at a borrowed point size is a visible step in a column of counts.
     */
    @Test
    fun `the monospace helpers preserve size and weight`() {
        for (style in ttTypography.all) {
            for (monospaced in listOf(style.ttMonospaced(), style.ttMonospacedDigits())) {
                assertEquals(style.fontSize, monospaced.fontSize)
                assertEquals(style.fontWeight, monospaced.fontWeight)
                assertEquals(style.lineHeight, monospaced.lineHeight)
            }
        }
    }

    /** Tabular figures are a font *feature*, so the helper has to actually request them. */
    @Test
    fun `tabular figures are requested rather than assumed`() {
        assertEquals("tnum", ttTypography.body.ttMonospacedDigits().fontFeatureSettings)
    }

    /**
     * The scale covers the whole range without a gap nobody can explain.
     *
     * A loose sanity bound rather than an exact one: it says the scale is a scale, spanning
     * a caption to a large title, and that no role is a typo of its neighbour (a `100f`
     * where a `10f` was meant still passes every other test in this file).
     */
    @Test
    fun `the scale spans a caption to a large title`() {
        val sizes = ttTypography.all.map { it.fontSize.value }
        assertTrue(sizes.min() <= 12f, "the smallest role is ${sizes.min()}sp, too large for a caption")
        assertTrue(sizes.max() >= 30f, "the largest role is ${sizes.max()}sp, too small for a large title")
        assertTrue(
            sizes.max() / sizes.min() >= 2f,
            "the scale spans only ${sizes.min()} to ${sizes.max()}; a scale with no range " +
                "is a list",
        )
    }
}
