package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
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
 * ## The host split
 *
 * These thirteen scenarios are popup behaviour and semantics-tree geometry; none of them reads
 * ink. They used to run through `assumeHostRasterisesText()`, which skipped the entire class on
 * the Android host for a reason that is about the test runner rather than about the claims. The
 * bodies live here and each host contributes entry points ([DateFieldInteractionJvmTest],
 * [DateFieldInteractionIosTest], [DateFieldInteractionAndroidHostTest]) — the same split
 * [SignInScenarios] documents.
 *
 * The two density-in-window scenarios next door in [DateFieldPopupLayoutTest] stay gated: each
 * probe needs its own composition root, so they cannot run inside a single scenario.
 */
@OptIn(ExperimentalTestApi::class)
object DateFieldInteractionScenarios {

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
    val aFieldWithNoValueAndNoTodayOpensOnTheCurrentMonth: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = null) { test ->
            val first = ttSystemToday().let { LocalDate(it.year, it.month, 1) }

            test.onNodeWithTag(TTDayCellTag(first)).assertExists()
        }
    }

    /**
     * And its month arrows move the grid.
     *
     * The arrows did `shownMonth = shownMonth?.let { ttShiftMonth(it, step) }`, which assigns
     * null to null — so with a null seed the arrows were inert *permanently*, not just on the
     * first press. A single click here is the whole regression.
     */
    val theMonthArrowsMoveTheGridWhenThereIsNoValueAndNoToday: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = null) { test ->
            val first = ttSystemToday().let { LocalDate(it.year, it.month, 1) }
            val next = ttShiftMonth(first, 1)

            test.onNodeWithTag(TTMonthStepTag(1)).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTDayCellTag(next)).assertExists()
            test.onNodeWithTag(TTDayCellTag(first)).assertDoesNotExist()
        }
    }

    val thePreviousMonthArrowGoesBack: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = null) { test ->
            val first = ttSystemToday().let { LocalDate(it.year, it.month, 1) }

            test.onNodeWithTag(TTMonthStepTag(-1)).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTDayCellTag(ttShiftMonth(first, -1))).assertExists()
        }
    }

    // ---------------------------------------------------------------------------------
    // Choosing a year, which is what the calendar could not do at all
    // ---------------------------------------------------------------------------------

    val tappingTheMonthAndYearOpensTheYearsAndHidesTheDays: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            test.onNodeWithTag(TTDayGridTag).assertExists()

            test.onNodeWithTag(TTMonthLabelTag).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTYearGridTag).assertExists()
            // The days are *replaced*, not covered: this is the same popup showing different
            // contents, because a second popup would need the flip-above-the-field positioning
            // logic a second time.
            test.onNodeWithTag(TTDayGridTag).assertDoesNotExist()
        }
    }

    val pickingAYearMovesTheGridToThatYearAndBackToTheDays: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            test.onNodeWithTag(TTMonthLabelTag).performClick()
            test.waitForIdle()
            test.onNodeWithTag(TTYearCellTag(2028)).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTDayGridTag).assertExists()
            test.onNodeWithTag(TTYearGridTag).assertDoesNotExist()
            test.onNodeWithTag(TTDayCellTag(LocalDate(2028, Month.MARCH, 1))).assertExists()
        }
    }

    /**
     * Choosing a year keeps the month the user was looking at.
     *
     * The two are separate questions — "which year" and "which month" — and answering the first
     * by resetting to January would be a silent answer to the second.
     */
    val choosingAYearKeepsTheMonth: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
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
    }

    val theYearsOnOfferAreTheWindowAndNotTheWholeCalendar: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            test.onNodeWithTag(TTMonthLabelTag).performClick()
            test.waitForIdle()

            // 2032 is five past the last year the window allows and 2020 is one before the first.
            test.onNodeWithTag(TTYearCellTag(2031)).assertExists()
            test.onNodeWithTag(TTYearCellTag(2021)).assertExists()
            test.onNodeWithTag(TTYearCellTag(2032)).assertDoesNotExist()
            test.onNodeWithTag(TTYearCellTag(2020)).assertDoesNotExist()
        }
    }

    /**
     * A field whose value is a real date still opens on its value, not on today.
     *
     * The seeding change touched this path too, so it is checked rather than assumed: the fix
     * must not make every field open on today.
     */
    val aFieldWithAValueOpensOnTheValue: suspend ComposeUiTest.() -> Unit = {
        withField(
            value = LocalDate(2027, Month.JULY, 9),
            today = LocalDate(2026, Month.MARCH, 16),
        ) { test ->
            test.onNodeWithTag(TTDayCellTag(LocalDate(2027, Month.JULY, 1))).assertExists()
            test.onNodeWithTag(TTDayCellTag(LocalDate(2026, Month.MARCH, 1))).assertDoesNotExist()
        }
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
    val theYearScreenOccupiesTheSameSpaceAsTheCalendar: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
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
    val thePopupIsTheWidthOfTheCalendarAndNotTheWidthOfTheWindow: suspend ComposeUiTest.() -> Unit = {
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
    val aDayCellIsStill44dpWideInsideTheFixedWidthPopup: suspend ComposeUiTest.() -> Unit = {
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
    val doneClosesThePickerWithoutChoosingADate: suspend ComposeUiTest.() -> Unit = {
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
    val doneIsOfferedOnTheYearScreenAsWell: suspend ComposeUiTest.() -> Unit = {
        withField(value = null, today = LocalDate(2026, Month.MARCH, 16)) { test ->
            test.onNodeWithTag(TTMonthLabelTag).performClick()
            test.waitForIdle()

            test.onNodeWithTag(TTYearGridTag).assertExists()
            test.onNodeWithTag(TTDateDoneTag).assertExists()
        }
    }

    // ---------------------------------------------------------------------------------

    /**
     * Composes one field, opened, and hands the test to [body].
     *
     * `initiallyExpanded` exists so this does not have to tap the field open first, and the
     * layout is given a width because a popup anchored to a zero-width anchor has no position.
     */
    private suspend fun ComposeUiTest.withField(
        value: LocalDate?,
        today: LocalDate?,
        /**
         * Every date the field reports, in order.
         *
         * Collected rather than discarded, so a scenario can assert that a control did *not*
         * change the value — which is a different claim from "the popup closed", and the one
         * that catches a Done button quietly answering the question.
         */
        picked: MutableList<LocalDate> = mutableListOf(),
        body: suspend (ComposeUiTest) -> Unit,
    ) {
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
