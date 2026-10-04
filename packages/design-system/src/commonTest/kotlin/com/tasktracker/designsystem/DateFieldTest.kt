package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The date field's calendar arithmetic, asserted without composing anything.
 *
 * ## Why these are pure-function tests rather than Compose tests
 *
 * Everything interesting about a day grid is a question about *which day lands in which
 * cell*, and that question has nothing to do with pixels. Reading it off a rendered frame
 * would mean comparing a screenshot to a picture of a calendar — which is how a grid that is
 * off by one cell survives review, because "the 1st is under a T" is not a sentence anyone
 * says out loud.
 *
 * So the arithmetic is in functions over [LocalDate] ([ttMonthGrid], [ttShiftMonth],
 * [ttWeekdayOrder]) and it is tested here, with numbers. The components that draw it are
 * tested separately, for the things only drawing can be wrong about — see
 * `DateFieldContrastTest`.
 *
 * ## The cases below are the ones the arithmetic actually gets wrong
 *
 * A month that does not start on Monday (the ordinary case, 5 of 7), a February that is 29
 * days long (the only month where a month-length table is wrong), a 31st stepping into a
 * 30-day month (which clamps, and would land the popup on the wrong week), and a week that
 * starts on Sunday (which is what `isoDayNumber`'s Monday=1 base makes fiddly).
 */
@OptIn(ExperimentalTestApi::class)
class DateFieldTest {

    @Test
    fun `a month grid is six rows of seven and never anything else`() {
        // Every month of a year, and both week starts, because the cell count is what makes
        // the popup a fixed height and a grid that grew or shrank would move the control
        // under the user's finger.
        for (month in Month.entries) {
            for (first in ttWeekStarts) {
                val grid = ttMonthGrid(LocalDate(2026, month, 1), first)
                assertEquals(
                    TT_MONTH_GRID_CELLS,
                    grid.size,
                    "${month.name} with ${first.name} first is ${grid.size} cells, " +
                        "not ${TT_MONTH_GRID_CELLS}. The popup's height is this number.",
                )
                assertEquals(
                    TT_MONTH_GRID_ROWS * TT_MONTH_GRID_COLUMNS,
                    grid.size,
                    "the grid is ${TT_MONTH_GRID_ROWS} rows of ${TT_MONTH_GRID_COLUMNS}",
                )
            }
        }
    }

    @Test
    fun `a month's days fill the grid from its own first and in order`() {
        for (month in Month.entries) {
            val grid = ttMonthGrid(LocalDate(2026, month, 15))
            val days = grid.filterNotNull()
            // The expected length is a literal, from a calendar — never the production code's
            // own count of the month. Asking `ttMonthGrid` how many days it drew and then
            // asserting it drew that many is circular, and it would pass with every month's
            // length wrong.
            assertEquals(
                monthLengths2026.getValue(month),
                days.size,
                "${month.name} 2026 has ${monthLengths2026.getValue(month)} days but the grid " +
                    "drew ${days.size}",
            )
            assertEquals(
                (1..days.size).map { it },
                days.map { it.day },
                "${month.name}'s days are not 1..n in order: ${days.map { it.day }}",
            )
        }
    }

    @Test
    fun `the leading blanks are exactly the days before the first of the month`() {
        // October 2026 starts on a Thursday. Monday-first is 3 blanks; Sunday-first is 4.
        val october = LocalDate(2026, Month.OCTOBER, 1)
        assertEquals(
            listOf("-", "-", "-", "1", "2", "3", "4"),
            firstWeekOf(ttMonthGrid(october, DayOfWeek.MONDAY)),
            "October 2026 starts on a Thursday, so a Monday-first week is 3 blanks",
        )
        assertEquals(
            listOf("-", "-", "-", "-", "1", "2", "3"),
            firstWeekOf(ttMonthGrid(october, DayOfWeek.SUNDAY)),
            "the same month, Sunday-first, is 4 blanks — the week runs the other way",
        )
    }

    @Test
    fun `a month that starts on the first day of the week has no leading blanks`() {
        // February 2021 began on a Monday, and June 2026 does too.
        assertEquals(
            "1",
            firstWeekOf(ttMonthGrid(LocalDate(2021, Month.FEBRUARY, 1), DayOfWeek.MONDAY)).first(),
            "February 2021 starts on a Monday, so its first cell is the 1st",
        )
        assertEquals(
            "1",
            firstWeekOf(ttMonthGrid(LocalDate(2026, Month.JUNE, 1), DayOfWeek.MONDAY)).first(),
            "June 2026 starts on a Monday, so its first cell is the 1st",
        )
    }

    @Test
    fun `a month that starts on the last day of the week fills only its last column`() {
        // February 2015 began on a Sunday, so with a Monday-first week only column 7 is used.
        assertEquals(
            listOf("-", "-", "-", "-", "-", "-", "1"),
            firstWeekOf(ttMonthGrid(LocalDate(2015, Month.FEBRUARY, 1), DayOfWeek.MONDAY)),
            "February 2015 starts on a Sunday, so the 1st is in the last column",
        )
    }

    @Test
    fun `February is 29 days in a leap year and 28 in a common one`() {
        // The one month where a hard-coded length table is wrong, and the reason the
        // production code asks the calendar instead of counting.
        assertEquals(
            29,
            ttMonthGrid(LocalDate(2024, Month.FEBRUARY, 1)).count { it != null },
            "2024 is a leap year",
        )
        assertEquals(
            28,
            ttMonthGrid(LocalDate(2026, Month.FEBRUARY, 1)).count { it != null },
            "2026 is not",
        )
        assertEquals(
            29,
            ttMonthGrid(LocalDate(2000, Month.FEBRUARY, 1)).count { it != null },
            "2000 is divisible by 400, so it is a leap year — the century rule",
        )
        assertEquals(
            28,
            ttMonthGrid(LocalDate(1900, Month.FEBRUARY, 1)).count { it != null },
            "1900 is divisible by 100 but not 400, so it is not — and a rule that forgets the " +
                "400 exception gets this one wrong",
        )
        assertEquals(
            "29",
            ttMonthGrid(LocalDate(2024, Month.FEBRUARY, 29)).filterNotNull().last().day.toString(),
            "a leap February's 29th is in the grid at all — it is the day a length table of 28 " +
                "drops off the end",
        )
    }

    @Test
    fun `the whole year has the lengths a year is supposed to have`() {
        // A month-length table with two entries swapped is invisible until somebody opens
        // the picker in that month, so all twelve are pinned.
        for ((month, days) in monthLengths2026) {
            val drawn = ttMonthGrid(LocalDate(2026, month, 1)).count { it != null }
            assertEquals(days, drawn, "${month.name} 2026 drew $drawn days, not $days")
        }
    }

    @Test
    fun `stepping a month lands on the first and crosses a year boundary`() {
        // Built from the 1st on purpose: `LocalDate(2026, JANUARY, 31).plus(1, MONTH)` is
        // 28 February, and a caller passing today's date — the normal case — would have the
        // popup jump to the end of the month a day early every short month.
        assertEquals(
            LocalDate(2026, Month.FEBRUARY, 1),
            ttShiftMonth(LocalDate(2026, Month.JANUARY, 31), 1),
            "stepping forward from the 31st of January lands on the 1st of February, not the 28th",
        )
        assertEquals(
            LocalDate(2027, Month.JANUARY, 1),
            ttShiftMonth(LocalDate(2026, Month.DECEMBER, 31), 1),
            "stepping forward out of December lands in the next year",
        )
        assertEquals(
            LocalDate(2025, Month.DECEMBER, 1),
            ttShiftMonth(LocalDate(2026, Month.JANUARY, 15), -1),
            "stepping back out of January lands in the previous year",
        )
        assertEquals(
            LocalDate(2028, Month.MARCH, 1),
            ttShiftMonth(LocalDate(2028, Month.FEBRUARY, 29), 1),
            "stepping forward out of a leap February lands on the 1st, not the 28th",
        )
    }

    @Test
    fun `stepping twelve months returns the same month a year later`() {
        for (month in Month.entries) {
            assertEquals(
                LocalDate(2027, month, 1),
                ttShiftMonth(LocalDate(2026, month, 1), 12),
                "${month.name} stepped by twelve months",
            )
        }
    }

    @Test
    fun `the weekday header runs in the same order as the grid's columns`() {
        // The header and the cells are derived from one `firstDayOfWeek` on purpose, so the
        // only thing left to get wrong is the walk itself.
        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
            ),
            ttWeekdayOrder(DayOfWeek.MONDAY),
        )
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            ),
            ttWeekdayOrder(DayOfWeek.SUNDAY),
            "a Sunday-first week starts on Sunday and wraps at the end of the week",
        )
    }

    @Test
    fun `every day sits in the column whose header names its own weekday`() {
        // The end-to-end version of the tests above, and the one that actually matters on
        // screen: a blank-count assertion on its own cannot see a header that is rotated.
        // This walks all 42 cells of all twelve months under both week starts and checks
        // that each drawn day lands under its own weekday — so "the 14th is under a W" is
        // held for every cell, not just the first.
        for (first in ttWeekStarts) {
            val header = ttWeekdayOrder(first)
            for (month in Month.entries) {
                val grid = ttMonthGrid(LocalDate(2026, month, 1), first)
                grid.forEachIndexed { cell, day ->
                    if (day == null) return@forEachIndexed
                    val column = cell % TT_MONTH_GRID_COLUMNS
                    assertEquals(
                        header[column],
                        day.dayOfWeek,
                        "${month.name} 2026, ${first.name}-first: the ${day.day}th (a " +
                            "${day.dayOfWeek}) is in column $column, which the header reads as " +
                            "${header[column]}",
                    )
                }
            }
        }
    }

    @Test
    fun `the cells past a month's end are empty rather than the next month's days`() {
        // A greyed-out adjacent month is ambiguous — grey reads as "disabled", and
        // "disabled" is a different promise from "belongs to another month". Those cells are
        // exactly how a user picks the 1st of March while looking at February.
        val grid = ttMonthGrid(LocalDate(2026, Month.FEBRUARY, 1))
        assertEquals(28, grid.filterNotNull().size)
        assertTrue(
            grid.filterNotNull().none { it.month != Month.FEBRUARY },
            "February's grid contains a day from another month",
        )
        assertNull(
            grid.last(),
            "the last cell of a 28-day February is empty, not the 1st of March",
        )
    }

    @Test
    fun `no two weekday columns read the same`() {
        // The header shows a truncation of each name, and the whole reason it truncates at
        // three characters rather than one is that a two-"W" header is a header the user has
        // to resolve by counting columns from a remembered Monday. So the two things the
        // header can actually be are checked here: that the names are distinct to begin with,
        // and that the truncation does not collapse them.
        val shown = ttWeekdayOrder(DayOfWeek.MONDAY).map { it.name.take(TT_WEEKDAY_INITIALS) }
        assertEquals(
            7,
            shown.distinct().size,
            "the weekday header renders $shown, so at least two columns are indistinguishable: " +
                "a user cannot tell Wednesday from Thursday by looking at it",
        )
        assertEquals(
            7,
            ttWeekdayOrder(DayOfWeek.SUNDAY).map { it.name.take(TT_WEEKDAY_INITIALS) }.distinct().size,
            "the same under a Sunday-first week",
        )
        assertTrue(
            TT_WEEKDAY_INITIALS >= 3,
            "the header is truncated to $TT_WEEKDAY_INITIALS characters. The library's names " +
                "are MONDAY..SUNDAY, so one character is M T W T F S S and three is Mon Tue Wed " +
                "Thu Fri Sat Sun - and at 1/7 of a phone's width, about 44dp, three characters of " +
                "captionSmall do fit.",
        )
    }

    // MARK: The labels a sprint row and a field both read

    /**
     * Composes once and hands the block a way to resolve a label.
     *
     * [ttDateLabel] and [ttDateRange] read month names through `stringResource`, so they
     * cannot be called without a composition — and a design system has no locale other than
     * its resource bundle, which is the whole reason `strings.xml` carries the twenty-one
     * month and weekday names. So the assertion needs a frame even though nothing about it
     * is a question of pixels.
     *
     * The host guard is the same one every composing test here uses. It is not here for
     * pixels: it is here because the Android host cannot run a composition at all, so
     * without it this test dies in that host's environment setup rather than reporting
     * anything about the labels.
     */
    private fun withComposedLabels(body: @Composable () -> Unit) {
        if (!thisHostRasterisesText()) {
            println("skipped: this host cannot compose, so no label can be resolved here")
            return
        }
        runComposeUiTest {
            setContent { ttTheme(ThemeScheme.Light) { body() } }
            waitForIdle()
        }
    }

    @Test
    fun `a date label reads as day month year`() {
        var label = ""
        withComposedLabels { label = ttDateLabel(LocalDate(2026, Month.OCTOBER, 4)) }

        assertEquals("4 October 2026", label)
    }

    @Test
    fun `a window within one year prints the year once`() {
        var range = ""
        withComposedLabels {
            range = ttDateRange(LocalDate(2026, Month.OCTOBER, 4), LocalDate(2026, Month.OCTOBER, 18))
        }

        // "4 Oct – 18 Oct 2026" and not "4 Oct 2026 – 18 Oct 2026": the repetition makes a
        // single-year window read as though it spans a year boundary, which is exactly the
        // thing a reader of a sprint window is checking for.
        assertEquals("4 October – 18 October 2026", range)
        assertEquals(
            1,
            range.split("2026").size - 1,
            "the year is printed once, got: $range",
        )
    }

    @Test
    fun `a window across a year boundary keeps both years`() {
        var range = ""
        withComposedLabels {
            range = ttDateRange(LocalDate(2026, Month.DECEMBER, 28), LocalDate(2027, Month.JANUARY, 3))
        }

        // Here the year *is* the information, so dropping one would hide the boundary.
        assertEquals("28 December 2026 – 3 January 2027", range)
    }

    @Test
    fun `a window inside one month still prints the year once`() {
        var range = ""
        withComposedLabels {
            range = ttDateRange(LocalDate(2026, Month.SEPTEMBER, 28), LocalDate(2026, Month.NOVEMBER, 3))
        }

        // Same-*year*, not same-*month*: a window crossing months is common while one
        // crossing years is not, and the rule is about redundancy rather than brevity.
        assertEquals("28 September – 3 November 2026", range)
    }

    @Test
    fun `a day cell is at least the platform minimum tap target`() {
        // Rendered, because the cell's size is a layout result rather than a token: the code
        // names `ttSpacing.iconButtonSize`, and what a test can hold to is the size the cell
        // actually got. 44dp is the figure both platforms' accessibility guidance names, and a
        // cell sized to its numeral is about 20dp and unusable.
        val widths = mutableListOf<Float>()
        val heights = mutableListOf<Float>()
        rasterising {
            setContent {
                ttTheme(ThemeScheme.Light) {
                    Box(Modifier.fillMaxWidth()) {
                        TTDayGrid(
                            month = LocalDate(2026, Month.OCTOBER, 1),
                            onSelect = {},
                        )
                    }
                }
            }
            waitForIdle()
            for (column in 0 until TT_MONTH_GRID_COLUMNS) {
                val day = LocalDate(2026, Month.OCTOBER, 4 + column)
                val bounds = onNodeWithTag(TTDayCellTag(day)).getBoundsInRoot()
                widths += bounds.right.value - bounds.left.value
                heights += bounds.bottom.value - bounds.top.value
            }
        }
        assertEquals(TT_MONTH_GRID_COLUMNS, widths.size, "every column of the first week was measured")
        assertTrue(
            widths.min() >= MINIMUM_TAP_TARGET.value - 0.5f,
            "the narrowest day cell is ${widths.min()}dp wide, below the " +
                "${MINIMUM_TAP_TARGET.value}dp both platforms name as a minimum hit target",
        )
        assertTrue(
            heights.min() >= MINIMUM_TAP_TARGET.value - 0.5f,
            "the shortest day cell is ${heights.min()}dp tall, below the " +
                "${MINIMUM_TAP_TARGET.value}dp minimum",
        )
    }

    /** The two week starts the app offers, so both are exercised by everything above. */
    private companion object {

        val ttWeekStarts = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)

        /**
         * The grid's first week as day numbers, with `"-"` for a cell that is not this month.
         *
         * Readable in an assertion failure in a way `listOf(null, null, 1)` is not — the whole
         * point of these three tests is *where the 1st sits*, and a failure message that says
         * `[null, null, null, null, 1]` makes the reader count the nulls themselves.
         */
        fun firstWeekOf(grid: List<LocalDate?>): List<String> =
            grid.take(TT_MONTH_GRID_COLUMNS).map { it?.day?.toString() ?: "-" }

        /**
         * 2026's month lengths, written down.
         *
         * The point of a table in a *test* is that it is independent of the code under it.
         * Asking `ttMonthGrid` how many days it produced and asserting it produced that many
         * is a tautology that passes with every month the wrong length, so the expected
         * numbers live here instead, and the production code is the thing being checked.
         */
        /**
         * The minimum hit target both platforms' accessibility guidance names, in dp.
         *
         * Restated here rather than read from `ttSpacing.iconButtonSize` because the
         * assertion is about the *floor*, not about agreement with a token: if somebody
         * lowers the token to 20dp this test stays green, which is what it should do,
         * because the token's value is a design decision and this is a limit.
         */
        val MINIMUM_TAP_TARGET = 44.dp

        val monthLengths2026 = mapOf(
            Month.JANUARY to 31,
            Month.FEBRUARY to 28,
            Month.MARCH to 31,
            Month.APRIL to 30,
            Month.MAY to 31,
            Month.JUNE to 30,
            Month.JULY to 31,
            Month.AUGUST to 31,
            Month.SEPTEMBER to 30,
            Month.OCTOBER to 31,
            Month.NOVEMBER to 30,
            Month.DECEMBER to 31,
        )
    }
}