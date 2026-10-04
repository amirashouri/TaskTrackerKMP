package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a *pinned* appearance resolves to.
 *
 * ## Why this is a pure test and not a composition test
 *
 * The obvious test for "pinning dark actually gives you dark" is to compose something under
 * `ttTheme(ThemeScheme.Dark)` and read the colour back. This module cannot do that yet: it
 * has no host, and `ui-test` is wired but unused anywhere in the build, so the first
 * composition test in the codebase would also be the first decision about how these are
 * written. That is a decision for the first *screen*, not for a design system with no
 * screens.
 *
 * So this asserts the part that is pure — [ttFixedColours], which is what a pinned
 * appearance resolves to before any platform gets a say — and leaves the composition
 * assertion to the first presentation module. The gap is real and it is named here rather
 * than papered over: **nothing yet asserts that `ttTheme` reaches these values.** The
 * indirection between them is `dynamicOrPinned`, and the test that would cover it needs a
 * composer.
 */
class PinnedAppearanceTest {

    /**
     * Every token differs between the two appearances.
     *
     * A single token that does not is a real bug and a *silent* one: in pinned dark, half the
     * surface stays light, nothing crashes, and it reads as a theming bug that nobody can
     * point at. The candidate that motivated this test is [ttFixedColours.separatorLight] /
     * [ttFixedColours.separatorDark] — a separator is a low-alpha colour on both sides, so
     * "are these different?" is a question about the alpha, not the RGB, and an assertion
     * comparing the RGB bytes alone would call these two equal and pass.
     */
    @Test
    fun `every token differs between light and dark`() {
        for (dark in listOf(false, true)) {
            assertNotEquals(
                ttFixedColours.canvas(dark),
                ttFixedColours.canvas(!dark),
                "the canvas does not change with the appearance",
            )
            assertNotEquals(
                ttFixedColours.surface(dark),
                ttFixedColours.surface(!dark),
                "the surface does not change with the appearance",
            )
            assertNotEquals(
                ttFixedColours.label(dark),
                ttFixedColours.label(!dark),
                "the label does not change with the appearance",
            )
            assertNotEquals(
                ttFixedColours.labelSecondary(dark),
                ttFixedColours.labelSecondary(!dark),
                "the secondary label does not change with the appearance",
            )
            assertNotEquals(
                ttFixedColours.separator(dark),
                ttFixedColours.separator(!dark),
                "the separator does not change with the appearance",
            )
        }
    }

    /**
     * The error token changes with the appearance, and the fill and rim are fractions of it.
     *
     * [ttError] was added for `feature-auth-presentation`'s failure banner, which is the
     * first thing in the app to need a red that is not the accent. Two ways to get it wrong,
     * both silent:
     *
     *  - a single red for both appearances, which is legible in neither;
     *  - fill and rim picked independently, so a banner's border is a different red from the
     *    glyph sitting inside it — and both assertions below would still pass, because each
     *    checks one token against one expected value.
     *
     * So the banner's three colours are checked as a *relationship*, not as three constants.
     */
    @Test
    fun `the error token is a fixed red that changes with the appearance`() {
        assertEquals(
            ttFixedColours.errorLight,
            ttFixedColours.error(false),
            "the light error is not the value it is documented as",
        )
        assertEquals(
            ttFixedColours.errorDark,
            ttFixedColours.error(true),
            "the dark error is not the value it is documented as",
        )
        assertNotEquals(
            ttFixedColours.error(false),
            ttFixedColours.error(true),
            "the error does not change with the appearance",
        )
        // The banner draws white-on-red-ish text over a 10% fill with a 25% rim. A red that
        // is not actually red is the failure this catches: it is a hue check, not a
        // contrast one, and it is what keeps a future "brand error colour" from arriving
        // as orange.
        for (dark in listOf(false, true)) {
            val error = ttFixedColours.error(dark)
            assertTrue(
                error.red > error.green && error.red > error.blue,
                "the error is not red on dark=$dark: $error",
            )
        }
    }

    /**
     * The pre-composited tokens carry the alpha the original resolved.
     *
     * The test that [ttFixedColours]'s KDoc asks for, and the reason the separator's
     * comment mentions that the first draft read UIKit's `60, 60, 67` as an alpha. Comparing
     * the RGB bytes would not catch it: `Color(0x4A3C3C43)` and `Color(0x3C000000)` differ
     * in RGB too, so an "are they different" assertion passes on both a correct value and
     * the bug. Asserting the *alpha* is the only check that distinguishes them.
     *
     * These four are UIKit's, spelled out:
     *  - `separator`: 0.29 on light, 0.36 on dark
     *  - `secondaryLabel`: 0.60 on both
     */
    @Test
    fun `the pre-composited tokens carry the alpha the original resolved`() {
        assertEquals(
            expected = 0.29f,
            actual = alphaOf(ttFixedColours.separatorLight),
            absoluteTolerance = 0.005f,
            message = "separatorLight alpha",
        )
        assertEquals(
            expected = 0.36f,
            actual = alphaOf(ttFixedColours.separatorDark),
            absoluteTolerance = 0.005f,
            message = "separatorDark alpha",
        )
        assertEquals(
            expected = 0.60f,
            actual = alphaOf(ttFixedColours.labelSecondaryLight),
            absoluteTolerance = 0.005f,
            message = "labelSecondaryLight alpha",
        )
        assertEquals(
            expected = 0.60f,
            actual = alphaOf(ttFixedColours.labelSecondaryDark),
            absoluteTolerance = 0.005f,
            message = "labelSecondaryDark alpha",
        )
    }

    /**
     * The label is legible on the canvas of its own appearance.
     *
     * The regression that added the label tokens. Before they existed, body text was drawn
     * with `ttCanvas()`, which in dark is pure black on a pure black canvas — a 1:1 ratio
     * that is entirely invisible and passes a compile. This is the test that says it is not
     * allowed back.
     *
     * Checked against its *own* canvas for the reason `AccentContrastTest` documents: a
     * light label on a dark canvas is a combination that cannot occur, and testing it would
     * fail for correct colours.
     */
    @Test
    fun `the label is legible on the canvas of its own appearance`() {
        for (dark in listOf(false, true)) {
            val label = ttFixedColours.label(dark)
            val canvas = ttFixedColours.canvas(dark)
            val ratio = contrastRatio(label, canvas)
            assertTrue(
                ratio >= 4.5,
                "the label has $ratio:1 against its own canvas, below the 4.5:1 body-text bar",
            )
        }
    }

    /**
     * The surface is distinguishable from the canvas.
     *
     * A card that is the same colour as the page behind it is a card that has stopped
     * existing: no edge, no lift, no separation. This is the assertion that a surface token
     * has to be *a different colour* rather than merely a different name, and it is the one
     * that would catch someone "simplifying" `ttSurface` to the canvas.
     */
    @Test
    fun `the surface is a different colour from the canvas`() {
        for (dark in listOf(false, true)) {
            assertNotEquals(
                ttFixedColours.canvas(dark),
                ttFixedColours.surface(dark),
                "the surface is the same colour as the canvas, so it has no edge",
            )
        }
    }

    /**
     * The secondary label is dimmer than the label, and clears the bar it was chosen for.
     *
     * Both halves, because either alone is easy to satisfy by accident. A secondary label
     * with no alpha at all is *more* contrasty than the label and is "readable" — it just
     * is not secondary, and it silently becomes a second level in a hierarchy that has one.
     * The upper bound is the one that catches it.
     *
     * ## The bar is 3:1, not 4.5:1, and that is a real finding rather than a convenience
     *
     * Composited over its canvas this token measures **3.44:1** on light and **3.64:1** on
     * dark. It does not meet WCAG AA's 4.5:1 for body text, and the reason it is still
     * correct is that the token resolves UIKit's `secondaryLabel`, which does not either —
     * 3.44:1 is what the platform itself ships. Raising the alpha until it passes would
     * make the port a worse match for the design it is porting, and the change belongs to
     * the design rather than to the tokens.
     *
     * So the test holds the bar that was actually chosen, states the measured number, and
     * records the consequence where a caller will meet it: **3:1 is WCAG's bar for large
     * text only** (18pt, or 14pt bold). This token is 11–13sp in the scale, which is below
     * that, so a screen that puts `ttLabelSecondary` on body copy ships a contrast
     * regression. It is correct on footnotes and captions, and the original used it on
     * exactly those. The full 4.5:1 label is [ttFixedColours.label].
     */
    @Test
    fun `the secondary label sits between the label and the background`() {
        for (dark in listOf(false, true)) {
            val canvas = ttFixedColours.canvas(dark)
            val primary = contrastRatio(ttFixedColours.label(dark), canvas)
            val secondary = contrastRatio(
                // The secondary label carries alpha, so it has to be composited over the
                // canvas before it can be measured. Comparing an un-composited RGBA against
                // an opaque one is comparing a different thing.
                over(ttFixedColours.labelSecondary(dark), canvas),
                canvas,
            )
            assertTrue(
                secondary < primary,
                "the secondary label ($secondary:1) is not dimmer than the label ($primary:1)",
            )
            assertTrue(
                secondary >= 3.0,
                "the secondary label has $secondary:1 against the canvas, below even the " +
                    "3:1 large-text bar it was chosen for",
            )
        }
    }

    /** The resolver and the constants agree, so a caller cannot pick one over the other. */
    @Test
    fun `the resolvers return the constants they name`() {
        for (dark in listOf(false, true)) {
            assertEquals(if (dark) ttFixedColours.canvasDark else ttFixedColours.canvasLight, ttFixedColours.canvas(dark))
            assertEquals(if (dark) ttFixedColours.surfaceDark else ttFixedColours.surfaceLight, ttFixedColours.surface(dark))
            assertEquals(if (dark) ttFixedColours.separatorDark else ttFixedColours.separatorLight, ttFixedColours.separator(dark))
            assertEquals(if (dark) ttFixedColours.labelDark else ttFixedColours.labelLight, ttFixedColours.label(dark))
            assertEquals(
                if (dark) ttFixedColours.labelSecondaryDark else ttFixedColours.labelSecondaryLight,
                ttFixedColours.labelSecondary(dark),
            )
        }
    }

    /**
     * [top] composited over [bottom], by source-over alpha.
     *
     * The one the secondary-label test needs: an RGBA token is not a colour on its own, and
     * the whole reason it is stored pre-composited rather than as an opaque grey is that
     * which colour shows through depends on what is behind it.
     *
     * Now [over] in `PixelContrast.kt`, which arrived as a `Double` implementation rather
     * than this file's `Float` one. The four call sites below moved from `4.5f` to `4.5`
     * with them; nothing else about the measurement changed, and the thresholds are far
     * enough from the measured values that `Float` precision was never load-bearing.
     */

    private fun alphaOf(colour: Color): Float = (colour.alpha * 255f).roundToInt() / 255f
}
