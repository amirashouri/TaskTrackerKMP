package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The banner's message has to clear the text bar against the banner's own fill.
 *
 * ## What this test is not
 *
 * **It does not catch the defect this file was written for, and `BannerSourceTest` beside it
 * exists because that is true.** This suite computes the fill rather than reading the one
 * `ttBanner` draws, so it stayed green through both mutations of the original bug: the
 * message colour moved to `ttErrorRim()`, and the fill alpha moved from 0.10 to 0.22. Neither
 * is a rounding error here. `ttLabel()` is black on light and white on dark, and a banner's
 * fill is always a pale tint near the canvas, so *black on pale pink passes 4.5:1* and this
 * question has an almost-unfalsifiable answer.
 *
 * What this is good for is the token-level relationship: if `ttLabel` or the error red moves,
 * these numbers move and the suite can fail. `BannerSourceTest` is the one that holds the
 * argument the composable actually passes.
 *
 * ## What this exists to stop
 *
 * There were two banners, one per feature module, and they drifted. `ErrorBanner` drew its
 * message in `ttLabel()`; `ProjectsErrorBanner` drew the same message in `ttErrorRim()` — the
 * *rim* token, error at 25% alpha — composited over this banner's own 10% fill. Measured, that
 * put the message at **1.37:1** on the light appearance's fill and **1.39:1** on the dark one:
 * pale pink on pale pink, in both, on the one component whose entire job is to be read. Seven
 * screens rendered it.
 *
 * The reason it survived is that `ttErrorRim()` is a perfectly good `Color`. Nothing about the
 * call site looks wrong, so no compiler and no rendering test in either module could see it.
 * The defect was *arithmetic*, and arithmetic is what this is.
 *
 * ## Why the pairings are listed by hand rather than read off the composable
 *
 * `ttBanner` resolves its fill from `ttError()`, which is `@Composable` and goes through the
 * theme, so measuring the real thing would mean composing — and this suite is deliberately
 * plain arithmetic with no composition, for the reason `AccentContrastTest` gives. What is
 * pinned here is the *relationship*: whatever `ttLabel()` composites to over a 10%-alpha error
 * fill, in each appearance, has to clear 4.5:1.
 *
 * That is the invariant that broke, and it is also the one that would break again if somebody
 * lightened the error red, darkened `ttLabel`, or changed the fill alpha — none of which are
 * the mistake anybody would expect to be caught by.
 */
class BannerContrastTest {

    @Test
    fun `the banner's message clears the text bar on its own fill in both appearances`() {
        for (dark in listOf(false, true)) {
            val fill = over(ttFixedColours.error(dark), BANNER_FILL_ALPHA, ttFixedColours.canvas(dark))
            val message = ttFixedColours.label(dark)
            val ratio = contrastRatio(message, fill)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${if (dark) "dark" else "light"} appearance: the banner's message is " +
                    "${message.hex()} on its own ${fill.hex()} fill, which is " +
                    "${ratio.ratioText()} to 1 - below the ${TEXT_CONTRAST_MINIMUM} to 1 a text " +
                    "label needs. The token that caused this was ttErrorRim(), the *rim* token " +
                    "(error at 25%), used as a text colour; it measured 1.37:1 and 1.39:1.",
            )
        }
    }

    /**
     * The error tint must not be the message colour, whatever the pairings above say.
     *
     * A separate assertion because the ratio alone could be satisfied by a *different* wrong
     * colour: if someone "fixed" 1.37:1 by darkening `ttErrorRim()`, this suite would go green
     * while the banner went dark red on pink. The banner's tone is carried by the fill, the rim
     * and the glyph; the message is ink.
     */
    @Test
    fun `the banner's message is ink rather than a tone tint`() {
        for (dark in listOf(false, true)) {
            assertTrue(
                ttFixedColours.label(dark) != ttFixedColours.error(dark),
                "the banner's message and its error tint are the same colour in the " +
                    "${if (dark) "dark" else "light"} appearance, so the banner has no readable " +
                    "ink of its own",
            )
        }
    }

    /**
     * Both tones stay above the bar, because the accent tone reaches the same fill alpha.
     *
     * `ttBannerTone.Accent` exists because the projects screens were already using their error
     * banner for "3 items archived instead", which is an outcome and not a failure. Repointing
     * it at the accent changed the fill it is measured against, so it is pinned here rather
     * than assumed to follow.
     */
    @Test
    fun `the accent tone also leaves the message above the bar`() {
        for (dark in listOf(false, true)) {
            // The accent values are the ones `AccentPalette.Classic` ships, which is what the
            // pinned appearance resolves to. Read from the palette rather than copied so a
            // change to the accent moves this number.
            val palette = AccentPalette.Classic
            val accent = if (dark) palette.dark else palette.light
            val fill = over(accent, BANNER_FILL_ALPHA, ttFixedColours.canvas(dark))
            val ratio = contrastRatio(ttFixedColours.label(dark), fill)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${if (dark) "dark" else "light"} appearance: the accent-toned banner's " +
                    "message is ${ratio.ratioText()} to 1 on ${fill.hex()}, below " +
                    "${TEXT_CONTRAST_MINIMUM} to 1",
            )
        }
    }

    private companion object {
        const val TEXT_CONTRAST_MINIMUM = 4.5

        /**
         * Read from the banner rather than restated.
         *
         * An earlier draft of this file held its own `0.10f`. That version could not fail for
         * the change it most needed to catch — somebody raising the fill's alpha until the
         * banner grew a tint strong enough to swallow the ink — because the test would have
         * measured a fill that no longer existed. [ttBannerMetrics] is `internal` so this is
         * the number the composable draws with.
         */
        val BANNER_FILL_ALPHA: Float = ttBannerMetrics.FILL_ALPHA
    }
}