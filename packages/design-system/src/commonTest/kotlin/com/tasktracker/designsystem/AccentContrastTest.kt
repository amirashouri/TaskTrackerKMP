package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces the two contrast bars the accent has to clear, per appearance.
 *
 * ## The pairing that matters
 *
 * An accent only ever appears on the canvas of its own appearance: [TaskTrackerAccent.Light]
 * on a light canvas, [TaskTrackerAccent.Dark] on a dark one. So the visibility bar is
 * checked against *that* canvas, not against a fixed black. Checking the light accent
 * against the dark canvas is not a stricter test, it is a test of a combination that
 * cannot occur - and it fails for a correct colour, which is how this suite's first
 * draft reported `#34537A` at 2.66 to 1 against black and looked like a token bug when
 * the token was right.
 *
 * ## The two bars
 *
 *  - at least **4.5:1** against [ttOnAccent], for the label drawn on the accent, and
 *  - at least **3:1** against the canvas it sits on, or the control carrying that label
 *    is invisible.
 *
 * The second bar is the one a checker measuring only against white would miss. Genuine
 * charcoal, roughly `#2B3037` through `#4C545D`, clears the text bar easily and fails
 * visibility.
 *
 * ## Why this reads the token rather than a file
 *
 * The original enforced this in `AccentContrastTests` by reading
 * `AccentColor.colorset/Contents.json` off disk, located from `#filePath`, because the
 * accent lived in an asset catalog rather than in code. It deliberately did *not*
 * hardcode the two hexes - a test that hardcoded them would have stayed green after
 * somebody edited the catalog, which is the exact failure it exists to catch. That test
 * reached outside its own module to solve a problem this port does not have: the accent
 * is [TaskTrackerAccent.Light] and [TaskTrackerAccent.Dark] in `commonMain`, so this
 * suite asserts the values the token actually paints. See `Accent.kt`.
 *
 * The ratios are recomputed from WCAG 2.x rather than asserted as literals, because a
 * literal ratio would be a number nobody checks: editing the accent would change the
 * real ratio and leave the assertion true. Recomputing means editing the accent moves
 * the number and this suite can fail.
 */
class AccentContrastTest {

    @Test
    fun `every accent variant clears the text contrast bar against the label colour`() {
        for (variant in listOf(
                Variant("light", TaskTrackerAccent.Light),
                Variant("dark", TaskTrackerAccent.Dark),
            )) {
            val ratio = contrastRatio(variant.color, ttOnAccent)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${variant.name} accent ${variant.color.hex()} against the label colour is " +
                    "${ratio.ratioText()} to 1, below the ${TEXT_CONTRAST_MINIMUM} to 1 " +
                    "needed for the label drawn on it",
            )
        }
    }

    @Test
    fun `every selectable palette clears text and canvas contrast in both appearances`() {
        // Every palette is exercised, so a bad accent cannot be added without the
        // suite noticing. The classic pair and the five alternates share the same
        // two-bar rule: 4.5:1 against the white label and 3:1 against the canvas of
        // its own appearance.
        for (palette in AccentPalette.all) {
            val variants = listOf(
                Variant("${palette.title} light", palette.light),
                Variant("${palette.title} dark", palette.dark),
            )
            for (variant in variants) {
                val againstLabel = contrastRatio(variant.color, ttOnAccent)
                assertTrue(
                    againstLabel >= TEXT_CONTRAST_MINIMUM,
                    "${variant.name} accent ${variant.color.hex()} against the label colour " +
                        "is ${againstLabel.ratioText()} to 1, below the " +
                        "${TEXT_CONTRAST_MINIMUM} to 1 needed for the label drawn on it",
                )
                assertTrue(
                    variant.color.alpha == 1f,
                    "${variant.name} accent ${variant.color.hex()} has alpha " +
                        "${variant.color.alpha}, so its contrast depends on its backdrop",
                )
            }
            assertBothBars(palette.light, CANVAS_LIGHT)
            assertBothBars(palette.dark, CANVAS_DARK_IOS)
            assertBothBars(palette.dark, CANVAS_DARK_ANDROID)
        }
    }

    /**
     * The two variants have to actually differ. A refactor that collapsed
     * [TaskTrackerAccent] to one value would pass every test above - a single accent that
     * clears both bars is a perfectly legal colour - and would silently drop dark mode
     * support for the one fill in the app that carries a label.
     */
    @Test
    fun `the light and dark accents are different colours`() {
        assertTrue(
            TaskTrackerAccent.Light != TaskTrackerAccent.Dark,
            "the accent has one value for both appearances, so dark mode is not supported " +
                "for the fill that carries a label",
        )
    }

    /**
     * The error red has to clear the same bar the accent does, and it does not.
     *
     * ## Why the red was exempt and should not be
     *
     * This suite iterates [TaskTrackerAccent] and holds every one of its six palettes to
     * two bars. The error token was never in that list, which is how a destructive button
     * came to sit at **3.55:1** in light appearance and **3.41:1** in dark — white label on
     * opaque `#FF3B30` / `#FF453A`, from
     * `TTGlassButtonStyle(tint = ttError(), prominent = true)` at
     * `ProjectsListScreen.kt:340`, `WorkItemEditor.kt:453` and `WorkItemEditor.kt:625`.
     *
     * Dark is the *worse* of the two, which is the opposite of every other pairing in this
     * package and is what makes this look like a rounding error rather than a gap. It is
     * not: 3.41:1 is a white label on a red button, and the fix is a design decision
     * about the red or about where the label sits.
     *
     * The repository already knows this bug class. `LENS_ALPHA` was cut from 0.16 to 0.08
     * for the stated reason that "this wash lands on the *only* fill in the app that
     * carries a label, so it spends the accent's contrast margin", and
     * `GlassFillContrastTest` holds the resulting numbers. The error red never had that
     * treatment.
     *
     * ## This test used to be red
     *
     * It landed **red by design** in Phase 19, at 3.55:1 light and 3.41:1 dark, so the
     * decision about the red could not be deferred indefinitely by tidying a different file.
     * It went green in Phase 22 when `ttFixedColours.errorLight` / `errorDark` were darkened
     * to `#D3291F` / `#DC2B21` — same hue as UIKit's `systemRed`, saturation 0.74, and inside
     * the luminance window where white-on-red and red-on-black both clear their bars at once.
     * See `ttFixedColours.errorLight` for the arithmetic.
     *
     * It is kept as its own test rather than folded into the loop above because the red is
     * held to the *same two bars* as the accent for a reason worth stating: it is the only
     * other fill in the app that carries a label, and it was the only one exempt from this
     * suite for as long as it existed.
     *
     * ## Why the pinned pair and not `ttError()`
     *
     * `ttError()` is `@Composable` and resolves through the theme, so calling it here would
     * mean composing — and this suite is deliberately a plain unit test with no composition.
     * `ttFixedColours.errorLight` / `errorDark` are what it resolves to on **every** target
     * and in **every** scheme now, because Phase 22 made the token authoritative rather than
     * consulting `UIColor.systemRed` or `android.R.attr.colorError`. That is the one thing
     * this assertion could not have covered before: a pinned appearance resolved the token
     * while a following-system appearance got the platform's red, and the failing pairing
     * would have survived on every device whose theme was *not* pinned.
     */
    @Test
    fun `the error token carries a white label as legibly as the accent does`() {
        for (dark in listOf(false, true)) {
            val error = ttFixedColours.error(dark)
            val ratio = contrastRatio(error, ttOnAccent)
            assertTrue(
                ratio >= TEXT_CONTRAST_MINIMUM,
                "${if (dark) "dark" else "light"} appearance: a white label on a prominent " +
                    "destructive button (TTGlassButtonStyle(tint = ttError(), prominent = true)) " +
                    "sits on ${error.hex()} at ${ratio.ratioText()} to 1, below the " +
                    "${TEXT_CONTRAST_MINIMUM} to 1 a text label needs. Three call sites paint " +
                    "this: ProjectsListScreen.kt:340 and WorkItemEditor.kt:453 and 625.",
            )
        }
    }

    @Test
    fun `every accent variant is fully opaque`() {
        // A translucent accent is a different colour over every canvas it lands on, and
        // the bars above would then be asserting against a canvas that is not the one the
        // user sees.
        for (variant in listOf(
                Variant("light", TaskTrackerAccent.Light),
                Variant("dark", TaskTrackerAccent.Dark),
            )) {
            assertTrue(
                variant.color.alpha == 1f,
                "${variant.name} accent ${variant.color.hex()} has alpha " +
                    "${variant.color.alpha}, so its contrast depends on whatever it is " +
                    "drawn over",
            )
        }
    }

    /** Asserts the label bar and the visibility bar of one accent-on-canvas pairing. */
    private fun assertBothBars(accent: Color, canvas: Color) {
        val againstCanvas = contrastRatio(accent, canvas)
        assertTrue(
            againstCanvas >= CONTROL_CONTRAST_MINIMUM,
            "accent ${accent.hex()} on canvas ${canvas.hex()} is ${againstCanvas.ratioText()}" +
                " to 1, below the ${CONTROL_CONTRAST_MINIMUM} to 1 needed for the control " +
                "to be visible at all",
        )
    }

    /** A variant of the accent, paired with the name used to describe it in a failure. */
    private class Variant(val name: String, val color: Color)

    private companion object {
        const val TEXT_CONTRAST_MINIMUM = 4.5
        const val CONTROL_CONTRAST_MINIMUM = 3.0

        /**
         * `UIColor.systemBackground` in light appearance, and Android's
         * `?android:attr/colorBackground` in a light platform theme. Both are white, so
         * the light canvas is one value rather than two.
         */
        val CANVAS_LIGHT = Color(0xFFFFFFFF)

        /** `CanvasBackground`'s dark appearance in the original's asset catalog. */
        val CANVAS_DARK_IOS = Color(0xFF000000)

        /** Android's `?android:attr/colorBackground` under a dark platform theme. */
        val CANVAS_DARK_ANDROID = Color(0xFF121212)
    }
}
