package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The calendar popup's size in density and in a cramped window.
 *
 * ## Why these two are not scenarios with the rest
 *
 * Each probe needs a *second* composition root: the popup's height is measured at one density
 * and then at another, and `setContent` may be called only once per test host. So each probe
 * opens its own `runComposeUiTest`, which means these two cannot run inside a scenario that is
 * itself hosted by `runComposeUiTest` — unlike the thirteen in
 * [DateFieldInteractionScenarios].
 *
 * ## The Android host
 *
 * The gate is at the top of the *test*, not inside [probePopup], and that placement is
 * load-bearing. Returning a zeroed probe from the helper left the two probes being compared
 * against each other on a host that cannot compose a popup: the density test then passed
 * **vacuously** (0 == 0) and the short-window test failed on 0px. A skip that is allowed to
 * reach an assertion is not a skip.
 *
 * The two `runComposeUiTest` calls in one test are the reason this file remains on the JVM and
 * iOS only. A Robolectric entry point per probe would need one test method per probe, and the
 * density-invariance claim is the *comparison* between them.
 */
@OptIn(ExperimentalTestApi::class)
class DateFieldPopupLayoutTest {

    /**
     * The popup is the same height in dp at any density.
     *
     * The regression this whole phase exists for. `onSizeChanged` reports **pixels**, the
     * height was stored as a pixel count and applied as `popupContentHeightPx.dp` — which is the
     * same number at the tests' density of 1 and **three times too large** on a density-3 phone.
     * A card taller than the screen has nowhere to be centred: `ttCentredPopupOffset`'s
     * `coerceAtLeast(0)` puts `y` at 0, so the calendar sits at the top of the page under a
     * background running the window's full height. Reproduced at density 3 as a popup
     * 996x768px in a 768px window with its grid clamped from 696px.
     *
     * **Density-invariance, not a magic number.** A layout in dp is density-independent, so
     * `heightPx / density` has to come out the same at 1 and at 1.25; if the two differ by the
     * ratio of the densities, the height is being carried in the wrong unit. 1.25 is as high as
     * this can go and still let the calendar fit the 768px test viewport — at 2 the card no
     * longer fits and the cap below is what bounds it, so the two effects cannot be told apart.
     */
    @Test
    fun `the popup is the same height in dp whatever the density`() {
        assumeHostRasterisesText()
        val atOne = probePopup(densityScale = 1f)
        val atOneAndAQuarter = probePopup(densityScale = 1.25f)

        val dpAtOne = atOne.popupHeightPx.toFloat() / atOne.densityScale
        val dpAtOneAndAQuarter =
            atOneAndAQuarter.popupHeightPx.toFloat() / atOneAndAQuarter.densityScale

        assertTrue(
            kotlin.math.abs(dpAtOne - dpAtOneAndAQuarter) <= 1f,
            "the popup is ${dpAtOne.toInt()}dp tall at density 1 and " +
                "${dpAtOneAndAQuarter.toInt()}dp at density 1.25. A height that changes with " +
                "the density is a pixel count being read as dp: ${atOne.popupHeightPx}px became " +
                "${atOneAndAQuarter.popupHeightPx}px for the same card. One dp of slack is " +
                "rounding — 419px at density 1 is 523px at 1.25, which is 418.4dp",
        )
    }

    /**
     * In a window shorter than the calendar, the card is capped and the grid scrolls.
     *
     * The same symptom, the other cause: a landscape phone's window is about 360dp and the
     * calendar wants about 390dp, so even with the unit right the card is taller than the screen
     * it is centred in — clipped at the top again. So the popup takes at most
     * [TT_DATE_FIELD_MAX_HEIGHT_FRACTION] of the window's height, the grid takes what is left
     * once the footer is placed, and the overflow scrolls.
     *
     * Asserted by measuring the **same** popup twice rather than against a hard-coded window
     * height, because the test viewport differs between the JVM and the iOS simulator and the
     * popup is a second composition root whose own window cannot be measured from the test. The
     * claims are that a cramped window yields a *shorter dp* card, that the grid is scrollable,
     * and — the one that matters when space runs out — that **Done is still there**, because a
     * footer scrolled off the bottom is a picker with no way out, which is the bug this whole
     * phase was opened for.
     */
    @Test
    fun `in a window shorter than the calendar the card is capped and the grid scrolls`() {
        assumeHostRasterisesText()
        val roomy = probePopup(densityScale = 1f)
        // 1.75, and the number matters. The test window is 768px, so this is about 439dp of
        // height for a card that wants 419dp — **between 90% and 100% of the window**, which is
        // the only band where the cap is the binding constraint. At a higher density the card
        // exceeds the window outright, the window clamps it by itself, and the cap is not what
        // is being tested: P7, which deletes the cap, went green at 2.5 for exactly that reason.
        val cramped = probePopup(densityScale = 1.75f)

        assertTrue(
            cramped.popupHeightPx.toFloat() / cramped.densityScale <
                roomy.popupHeightPx.toFloat() / roomy.densityScale,
            "a window of " +
                "${cramped.popupHeightPx / cramped.densityScale}dp of card space produced the " +
                "same card as a roomy one: ${cramped.popupHeightPx}px at density " +
                "${cramped.densityScale} against ${roomy.popupHeightPx}px at density 1. The " +
                "cap is not engaging, so on a landscape phone the calendar is still taller " +
                "than the window and clipped at the top",
        )
        assertTrue(
            cramped.gridScrolls,
            "the day grid does not scroll in a window too short to hold it, so the rows past " +
                "the bottom edge are unreachable",
        )
        assertTrue(
            cramped.doneIsDisplayed,
            "Done is on the tree but not on the screen in a short window: a footer squeezed to " +
                "no height by a grid that took the whole cap is a picker with no way out of it, " +
                "which is the defect this phase was opened for",
        )
    }

    // ---------------------------------------------------------------------------------

    /**
     * Renders the opened field at [densityScale] and reports what the popup measured.
     *
     * The density is overridden through [LocalDensity] inside the content rather than through
     * the test, because the test's own density is the window's and the calendar has to be
     * squeezed by changing how many pixels a dp is worth.
     */
    private fun probePopup(densityScale: Float): PopupProbe {
        var probe = PopupProbe(0, densityScale, gridScrolls = false, doneIsDisplayed = false)
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(densityScale, 1f)) {
                    ttTheme(ThemeScheme.Light) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TTDateField(
                                label = "Start",
                                value = null,
                                onValueChange = {},
                                today = LocalDate(2026, Month.MARCH, 16),
                                initiallyExpanded = true,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val scrolls = onAllNodes(hasScrollAction() and hasTestTag(TTDateScrollTag))
                .fetchSemanticsNodes()
                .isNotEmpty()
            probe = PopupProbe(
                popupHeightPx = onNodeWithTag(TTDateFieldPopupTag).fetchSemanticsNode().size.height,
                densityScale = densityScale,
                gridScrolls = scrolls,
                doneIsDisplayed = runCatching {
                    onNodeWithTag(TTDateDoneTag).assertIsDisplayed()
                }.isSuccess,
            )
        }
        return probe
    }

    private data class PopupProbe(
        val popupHeightPx: Int,
        val densityScale: Float,
        val gridScrolls: Boolean,
        val doneIsDisplayed: Boolean,
    )
}
