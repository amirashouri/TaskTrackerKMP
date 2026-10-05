package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The calendar's behaviour when it is actually operated: the arrows, and choosing a year.
 *
 * ## Why this is a separate file from [DateFieldTest]
 *
 * The arithmetic is tested there, because arithmetic can be. What is left is "does pressing the
 * right arrow move the grid", and that can only be answered by a composition — there is no
 * function to call. Keeping the two apart also keeps the expensive ones apart from the cheap
 * ones, which is why this file is short.
 *
 * ## Why every assertion is about a *day cell* and never about text
 *
 * Reading "March 2026" off a rendered label would work and would be the obvious thing to do. It
 * is avoided because the label's text is formatted by `ttMonthName`, which is a resource lookup:
 * a locale change would fail these assertions for a reason that has nothing to do with the
 * calendar. Instead each assertion asks whether **the cell for the first of a given month is in
 * the tree** — `TTDayCellTag` carries the date, so "is March 2026 showing" is answered by the
 * presence of one unambiguous node rather than by parsing a localised sentence. A grid on the
 * wrong month cannot pass any of them.
 *
 * ## The host guard
 *
 * Every test here opens a `Popup`, which is the one thing the Android host's Robolectric
 * viewport is documented to handle differently. Per `AGENTS.md` the skip is registered as a
 * pass on that host, which is the honest outcome: this behaviour is verified on the JVM and on
 * the iOS simulator, and not claimed for a host that cannot compose a popup.
 */
@OptIn(ExperimentalTestApi::class)
class DateFieldInteractionTest {

    // ---------------------------------------------------------------------------------
    // The regression: a new sprint's picker opened on 1970, and its arrows did nothing
    // ---------------------------------------------------------------------------------

    /**
     * A field with no value and no `today` — which is exactly what the sprint editor passed —
     * opens on the current month.
     *
     * This is the 1970. The old code seeded `shownMonth` from `value ?: today`, both null here,
     * and rendered `shownMonth ?: today ?: value ?: LocalDate(1970, 1, 1)`.
     */
    @Test
    fun `a field with no value and no today opens on the current month`() = withField(
        value = null,
        today = null,
    ) { test ->
        val first = ttSystemToday().let { LocalDate(it.year, it.month, 1) }

        test.onNodeWithTag(TTDayCellTag(first)).assertExists()
    }

    /**
     * And its month arrows move the grid.
     *
     * The arrows did `shownMonth = shownMonth?.let { ttShiftMonth(it, step) }`, which assigns
     * null to null — so with a null seed the arrows were inert *permanently*, not just on the
     * first press. A single click here is the whole regression.
     */
    @Test
    fun `the month arrows move the grid when there is no value and no today`() = withField(
        value = null,
        today = null,
    ) { test ->
        val first = ttSystemToday().let { LocalDate(it.year, it.month, 1) }
        val next = ttShiftMonth(first, 1)

        test.onNodeWithTag(TTMonthStepTag(1)).performClick()
        test.waitForIdle()

        test.onNodeWithTag(TTDayCellTag(next)).assertExists()
        test.onNodeWithTag(TTDayCellTag(first)).assertDoesNotExist()
    }

    @Test
    fun `the previous month arrow goes back`() = withField(
        value = null,
        today = null,
    ) { test ->
        val first = ttSystemToday().let { LocalDate(it.year, it.month, 1) }

        test.onNodeWithTag(TTMonthStepTag(-1)).performClick()
        test.waitForIdle()

        test.onNodeWithTag(TTDayCellTag(ttShiftMonth(first, -1))).assertExists()
    }

    // ---------------------------------------------------------------------------------
    // Choosing a year, which is what the calendar could not do at all
    // ---------------------------------------------------------------------------------

    @Test
    fun `tapping the month and year opens the years and hides the days`() = withField(
        value = null,
        today = LocalDate(2026, Month.MARCH, 16),
    ) { test ->
        test.onNodeWithTag(TTDayGridTag).assertExists()

        test.onNodeWithTag(TTMonthLabelTag).performClick()
        test.waitForIdle()

        test.onNodeWithTag(TTYearGridTag).assertExists()
        // The days are *replaced*, not covered: this is the same popup showing different
        // contents, because a second popup would need the flip-above-the-field positioning
        // logic a second time.
        test.onNodeWithTag(TTDayGridTag).assertDoesNotExist()
    }

    @Test
    fun `picking a year moves the grid to that year and back to the days`() = withField(
        value = null,
        today = LocalDate(2026, Month.MARCH, 16),
    ) { test ->
        test.onNodeWithTag(TTMonthLabelTag).performClick()
        test.waitForIdle()
        test.onNodeWithTag(TTYearCellTag(2028)).performClick()
        test.waitForIdle()

        test.onNodeWithTag(TTDayGridTag).assertExists()
        test.onNodeWithTag(TTYearGridTag).assertDoesNotExist()
        test.onNodeWithTag(TTDayCellTag(LocalDate(2028, Month.MARCH, 1))).assertExists()
    }

    /**
     * Choosing a year keeps the month the user was looking at.
     *
     * The two are separate questions — "which year" and "which month" — and answering the first
     * by resetting to January would be a silent answer to the second.
     */
    @Test
    fun `choosing a year keeps the month`() = withField(
        value = null,
        today = LocalDate(2026, Month.MARCH, 16),
    ) { test ->
        // Move to November first, so the month being kept is not the one the seed happened to use.
        repeat(8) {
            test.onNodeWithTag(TTMonthStepTag(1)).performClick()
            test.waitForIdle()
        }
        test.onNodeWithTag(TTDayCellTag(LocalDate(2026, Month.NOVEMBER, 1))).assertExists()

        test.onNodeWithTag(TTMonthLabelTag).performClick()
        test.waitForIdle()
        test.onNodeWithTag(TTYearCellTag(2028)).performClick()
        test.waitForIdle()

        test.onNodeWithTag(TTDayCellTag(LocalDate(2028, Month.NOVEMBER, 1))).assertExists()
    }

    @Test
    fun `the years on offer are the window and not the whole calendar`() = withField(
        value = null,
        today = LocalDate(2026, Month.MARCH, 16),
    ) { test ->
        test.onNodeWithTag(TTMonthLabelTag).performClick()
        test.waitForIdle()

        // 2032 is five past the last year the window allows and 2020 is one before the first.
        test.onNodeWithTag(TTYearCellTag(2031)).assertExists()
        test.onNodeWithTag(TTYearCellTag(2021)).assertExists()
        test.onNodeWithTag(TTYearCellTag(2032)).assertDoesNotExist()
        test.onNodeWithTag(TTYearCellTag(2020)).assertDoesNotExist()
    }

    /**
     * A field whose value is a real date still opens on its value, not on today.
     *
     * The seeding change touched this path too, so it is checked rather than assumed: the fix
     * must not make every field open on today.
     */
    @Test
    fun `a field with a value opens on the value`() = withField(
        value = LocalDate(2027, Month.JULY, 9),
        today = LocalDate(2026, Month.MARCH, 16),
    ) { test ->
        test.onNodeWithTag(TTDayCellTag(LocalDate(2027, Month.JULY, 1))).assertExists()
        test.onNodeWithTag(TTDayCellTag(LocalDate(2026, Month.MARCH, 1))).assertDoesNotExist()
    }

    /**
     * The year screen takes up exactly as much room as the calendar did.
     *
     * This is the assertion behind "the year selection opens somewhere else", and it is stated
     * about *size* rather than about position on purpose. A popup's offset is computed from its
     * own measured size — `DateFieldPopupPositionProvider` opens below the field when the popup
     * fits and flips above it when it does not — so a popup whose height changes between the
     * two grids changes where it sits. The year grid is three rows against the day grid's six,
     * which is enough to cross the "does it fit below the field" line for a field low in a
     * sheet: the calendar opened *above* and the years opened *below*, and tapping the month
     * label appeared to throw the picker across the screen.
     *
     * `size` is measured inside the popup's own composition, so it needs no mapping between the
     * popup's coordinate space and any captured image. Asserting the *offset* would, and that
     * mapping is a guess rather than a measurement.
     */
    @Test
    fun `the year screen occupies the same space as the calendar`() = withField(
        value = null,
        today = LocalDate(2026, Month.MARCH, 16),
    ) { test ->
        val days = test.onNodeWithTag(TTDateFieldPopupTag).fetchSemanticsNode().size

        test.onNodeWithTag(TTMonthLabelTag).performClick()
        test.waitForIdle()
        val years = test.onNodeWithTag(TTDateFieldPopupTag).fetchSemanticsNode().size

        assertEquals(
            days.width,
            years.width,
            "the year grid is ${years.width}px wide where the calendar was ${days.width}px. " +
                "The popup clamps its x offset to windowWidth - contentWidth, so a grid that " +
                "stopped filling the popup's width would land somewhere else as well. Every row " +
                "of both grids is fillMaxWidth(), which is why this is equal today and why no " +
                "width constraint is needed in TTDateField",
        )
        assertEquals(
            days.height,
            years.height,
            "the year grid is ${years.height}px tall where the calendar was ${days.height}px. " +
                "The popup's y offset is a function of its height — below the field if it fits, " +
                "above it if it does not — so this is why the two screens opened in different " +
                "places",
        )
    }

    // ---------------------------------------------------------------------------------
    // The popup is the calendar's size, in the middle of the screen, and can be dismissed
    // ---------------------------------------------------------------------------------

    /**
     * The popup is as wide as the calendar, not as wide as the screen.
     *
     * This is the regression, and the cause was one modifier. Every row of [TTDayGrid] is
     * `fillMaxWidth()` and every cell is `weight(1f)`, so with nothing bounding them they
     * resolve to the *popup's maximum width* — the window's. Measured before the fix: a popup
     * **1000px wide inside a 1024px window**. Edge to edge, on every platform.
     *
     * And that is also why there was no way out of it. A popup that fills the window has no
     * outside left to tap, and `onDismissRequest` is reached by an outside tap or by back —
     * so the picker read as a page with no exit rather than as a picker.
     *
     * Asserted against [dateFieldPopupWidth] rather than a literal, so the expectation is
     * stated in the same terms the value is built from: seven 44dp cells and the surface's own
     * padding. A literal 332 would drift the first time a cell or the padding changed, and then
     * fail for a reason that is not a defect.
     */
    @Test
    fun `the popup is the width of the calendar and not the width of the window`() =
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            val popup = test.onNodeWithTag(TTDateFieldPopupTag).fetchSemanticsNode().size
            val calendar = (dateFieldPopupWidth.value * test.density.density).toInt()

            assertEquals(
                calendar,
                popup.width,
                "the popup is ${popup.width}px wide where the calendar is ${calendar}px. Every " +
                    "grid row is fillMaxWidth(), so an unbounded popup resolves to the window's " +
                    "width rather than the calendar's — which is what made it look like a page",
            )
        }

    /**
     * A day cell is still 44dp wide, because seven of them have to be.
     *
     * The second half of the same fix, and the one that could have broken silently: pinning the
     * popup's width only produces a 44dp cell if the arithmetic is right. Seven cells across
     * 308dp of content is 44dp each, and [DayCell] documents 44dp as the tap target both
     * platforms' accessibility guidance names. A width that did not divide would make every
     * cell the wrong size and every one of them still tappable.
     */
    @Test
    fun `a day cell is still 44dp wide inside the fixed-width popup`() =
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            val grid = test.onNodeWithTag(TTDayGridTag).fetchSemanticsNode().size
            val cell = test.onNodeWithTag(TTDayCellTag(LocalDate(2026, Month.MARCH, 11)))
                .fetchSemanticsNode().size

            assertEquals(
                grid.width / TT_MONTH_GRID_COLUMNS,
                cell.width,
                "a day cell is ${cell.width}px in a ${grid.width}px grid of " +
                    "$TT_MONTH_GRID_COLUMNS columns, so the cells are not sharing the row " +
                    "evenly and at least one tap target is the wrong size",
            )
        }

    /**
     * Done closes the picker, and it is there to be found.
     *
     * Back dismisses a popup, which is not something a user looks for and on a phone is not
     * something they can see. The reported symptom was a calendar with no way out of it, so the
     * way out is a control rather than a gesture.
     *
     * It also must not change the date. Choosing a day already closes the picker, so a Done
     * that also assigned would be a second, invisible way to answer the question — and the
     * field would change with nothing on screen to explain it. So the value is recorded, not
     * assumed: [withField] collects every date the field reports.
     */
    @Test
    fun `Done closes the picker without choosing a date`() {
        val picked = mutableListOf<LocalDate>()

        withField(value = null, today = LocalDate(2026, Month.MARCH, 16), picked = picked) { test ->
            test.onNodeWithTag(TTDateDoneTag).assertExists()
            test.onNodeWithTag(TTDayCellTag(LocalDate(2026, Month.MARCH, 20))).assertExists()

            test.onNodeWithTag(TTDateDoneTag).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTDateFieldPopupTag).assertDoesNotExist()
            assertTrue(
                picked.isEmpty(),
                "pressing Done chose $picked. Choosing a day already closes the picker, so a " +
                    "Done that also assigns is a second and invisible way to answer the " +
                    "question — and the field would change with nothing on screen to explain it",
            )
        }
    }

    /** Done is offered while choosing a year too, which is the state a user is most likely to regret. */
    @Test
    fun `Done is offered on the year screen as well`() =
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            test.onNodeWithTag(TTMonthLabelTag).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTYearGridTag).assertExists()
            test.onNodeWithTag(TTDateDoneTag).assertExists()
        }

    // ---------------------------------------------------------------------------------
    // Density, and windows shorter than the calendar
    // ---------------------------------------------------------------------------------

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
        // The guard is at the top of the *test*, not inside `probePopup`, and that placement is
        // load-bearing. Returning a zeroed probe from the helper left the two probes being
        // compared against each other on a host that cannot compose a popup: the density test
        // then passed **vacuously** (0 == 0) and the short-window test failed on 0px. A skip
        // that is allowed to reach an assertion is not a skip.
        if (!thisHostRasterisesText()) {
            println("skipped: this host cannot compose a popup, so the calendar cannot be measured")
            return
        }
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
        if (!thisHostRasterisesText()) {
            println("skipped: this host cannot compose a popup, so the calendar cannot be measured")
            return
        }
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

    /**
     * Composes one field, opened, and hands the test to [body].
     *
     * `initiallyExpanded` exists so this does not have to tap the field open first, and the
     * layout is given a width because a popup anchored to a zero-width anchor has no position.
     */
    private fun withField(
        value: LocalDate?,
        today: LocalDate?,
        /**
         * Every date the field reports, in order.
         *
         * Collected rather than discarded, so a test can assert that a control did *not*
         * change the value — which is a different claim from "the popup closed", and the one
         * that catches a Done button quietly answering the question.
         */
        picked: MutableList<LocalDate> = mutableListOf(),
        body: (ComposeUiTest) -> Unit,
    ) {
        if (!thisHostRasterisesText()) {
            println("skipped: this host cannot compose a popup, so the calendar cannot be operated")
            return
        }
        runComposeUiTest {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TTDateField(
                            label = "Start",
                            value = value,
                            onValueChange = { picked.add(it) },
                            today = today,
                            initiallyExpanded = true,
                        )
                    }
                }
            }
            waitForIdle()
            body(this)
        }
    }
}