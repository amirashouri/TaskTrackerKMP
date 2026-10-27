package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.resources.Res
import com.tasktracker.resources.date_choose_year
import com.tasktracker.resources.date_done
import com.tasktracker.resources.date_placeholder
import com.tasktracker.resources.date_next_month
import com.tasktracker.resources.date_previous_month
import com.tasktracker.resources.month_january
import com.tasktracker.resources.month_february
import com.tasktracker.resources.month_march
import com.tasktracker.resources.month_april
import com.tasktracker.resources.month_may
import com.tasktracker.resources.month_june
import com.tasktracker.resources.month_july
import com.tasktracker.resources.month_august
import com.tasktracker.resources.month_september
import com.tasktracker.resources.month_october
import com.tasktracker.resources.month_november
import com.tasktracker.resources.month_december
import com.tasktracker.resources.weekday_monday
import com.tasktracker.resources.weekday_tuesday
import com.tasktracker.resources.weekday_wednesday
import com.tasktracker.resources.weekday_thursday
import com.tasktracker.resources.weekday_friday
import com.tasktracker.resources.weekday_saturday
import com.tasktracker.resources.weekday_sunday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

// MARK: The calendar arithmetic, as pure functions
//
// Every interesting thing about a date grid is a piece of arithmetic about which day lands
// where, and none of it needs a frame. So it is here, over `LocalDate`, and it is testable
// without composing anything at all — which matters, because a grid that is wrong by one cell
// is the kind of bug a screenshot test reports as "it looks slightly off" and nobody acts on.

/**
 * The cells of a month grid, row-major, Monday-first by default.
 *
 * ## Why it is always six rows
 *
 * 6 x 7 = 42 cells, filled from the first of the month and padded with `null` on both ends.
 * A month that needs five rows still gets six, and the sixth is empty. The alternative —
 * returning only the weeks the month actually occupies — makes the popup **change height**
 * between months, so stepping from a 28-day February to a 31-day March makes the control
 * under the user's finger move. A calendar whose grid resizes is a calendar people mis-tap.
 *
 * ## `null` means "not this month", not "hidden"
 *
 * A `null` cell draws nothing. It is *not* the adjacent month's day greyed out: those cells
 * are exactly how a user ends up picking the 1st of March while looking at February, and the
 * cell is ambiguous — grey usually means "disabled", and "disabled" is a different promise
 * from "belongs to another month".
 */
fun ttMonthGrid(
    month: LocalDate,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<LocalDate?> {
    val firstOfMonth = LocalDate(month.year, month.month, 1)
    // `isoDayNumber` is Monday=1..Sunday=7, so the subtraction is the offset from the grid's
    // first column to this month's first day, and the `+ 7 % 7` keeps it non-negative for a
    // month that starts *before* the requested first day of the week.
    val leadingBlanks = (firstOfMonth.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
    val monthLength = ttDaysInMonth(firstOfMonth)

    return List(TT_MONTH_GRID_CELLS) { cell ->
        val dayOffset = cell - leadingBlanks
        if (dayOffset < 0 || dayOffset >= monthLength) {
            null
        } else {
            firstOfMonth.plus(dayOffset, DateTimeUnit.DAY)
        }
    }
}

/**
 * How many days [date]'s month has.
 *
 * Asked as "the day before the first of the next month" rather than read off a table,
 * because kotlinx-datetime 0.8.0 has no `daysInMonth`, and a hard-coded
 * `31, 28, 31, 30…` array is a leap-year bug waiting for February — the one month where the
 * two answers differ, and the month a date field is most likely to be opened on.
 */
private fun ttDaysInMonth(date: LocalDate): Int =
    LocalDate(date.year, date.month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .plus(-1, DateTimeUnit.DAY)
        .day

/**
 * The month's first day, [months] away.
 *
 * Built from the 1st rather than from whatever day the caller happened to pass, because
 * `LocalDate(2026, JANUARY, 31).plus(1, MONTH)` is 28 February, and a caller that passes
 * today's date — which is the normal case — would have the popup jump to the end of the
 * month a day earlier every month.
 */
fun ttShiftMonth(month: LocalDate, months: Int): LocalDate =
    LocalDate(month.year, month.month, 1).plus(months, DateTimeUnit.MONTH)

// MARK: The window of years the picker will show, and the clamp that keeps navigation inside it

/**
 * How many years either side of today the year grid offers.
 *
 * Five, because the picker has to cover "a sprint that starts in a fortnight" and "the fiscal
 * year that starts in January", which are the two real cases, with room either side of both
 * without turning the grid into a scrolling list of forty entries.
 */
const val TT_YEAR_WINDOW_SPAN: Int = 5

/** The years [ttYearGrid] offers for [today]: [span] either side, inclusive. */
fun ttYearWindow(today: LocalDate, span: Int = TT_YEAR_WINDOW_SPAN): IntRange =
    (today.year - span)..(today.year + span)

/**
 * [month] pulled back inside [ttYearWindow].
 *
 * Clamps to the **nearest reachable month in that direction** rather than to January or
 * December: stepping forward off the last December lands on December of the last year, not on
 * January of the *first* year, which is a jump backwards from where the user was pressing. The
 * month of the year is kept untouched for anything already inside the window, so this is
 * invisible until it has something to do.
 *
 * Exists because the window has to be enforced on month stepping too. A year grid listing
 * 2021-2031 that the month arrows can walk out of is a picker that shows a year its own list
 * does not contain, and the way to leave 1970 is to clamp what is shown as well as what is
 * offered.
 */
fun ttClampToYearWindow(
    month: LocalDate,
    today: LocalDate,
    span: Int = TT_YEAR_WINDOW_SPAN,
): LocalDate {
    val window = ttYearWindow(today, span)
    return when {
        month.year > window.last -> LocalDate(window.last, Month.DECEMBER, 1)
        month.year < window.first -> LocalDate(window.first, Month.JANUARY, 1)
        else -> month
    }
}

/** [ttShiftMonth] and then [ttClampToYearWindow], which is what every month arrow does. */
fun ttStepMonthInWindow(
    month: LocalDate,
    months: Int,
    today: LocalDate,
    span: Int = TT_YEAR_WINDOW_SPAN,
): LocalDate = ttClampToYearWindow(ttShiftMonth(month, months), today, span)

/**
 * Today, from the system clock, for the caller that did not supply one.
 *
 * The `today` parameter on [TTDateField] is the test seam and stays the one every test uses;
 * this is only the fallback so that a field with nothing to go on opens on a real date. It
 * existed as `LocalDate(1970, 1, 1)` inline at the one place that needed a value, which is how
 * a new sprint's picker opened on January 1970 with dead month arrows — see [TTDateField].
 */
internal fun ttSystemToday(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * The seven weekday abbreviations, starting at [firstDayOfWeek].
 *
 * Returned in the same order as [ttMonthGrid]'s columns, which is the whole point: the header
 * row and the cells below it are both derived from one call to [ttMonthGrid]'s `firstDayOfWeek`,
 * so they cannot disagree about which way round the week runs.
 */
fun ttWeekdayOrder(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<DayOfWeek> =
    List(7) { column ->
        // `isoDayNumber` is 1..7 and the grid's columns are 0..6, so this walks the ISO week
        // from the requested start and wraps at the end of it.
        DayOfWeek(((firstDayOfWeek.isoDayNumber - 1 + column) % 7) + 1)
    }

/**
 * A window of days as "4 Oct – 18 Oct 2026", or "28 Dec 2026 – 3 Jan 2027".
 *
 * ## The year is printed once when there is only one
 *
 * The two ends are formatted by [ttDateLabel] unless they share a year, in which case the
 * second drops it. "4 Oct 2026 – 18 Oct 2026" is two facts where the reader wanted one,
 * and the repetition is worse than the asymmetry: it makes a single-year sprint look like
 * it spans a year boundary, which is exactly the mistake a reader is checking for. Across
 * a year boundary both ends keep their year, because there the year *is* the information.
 *
 * Same-year rather than same-month, so a sprint that runs from late September into
 * November still prints one year — the rule is about redundancy, not about brevity, and a
 * window that crosses months is common while one that crosses years is not.
 */
@Composable
fun ttDateRange(start: LocalDate, end: LocalDate): String =
    if (start.year == end.year) {
        "${ttDateLabelWithoutYear(start)} – ${ttDateLabel(end)}"
    } else {
        "${ttDateLabel(start)} – ${ttDateLabel(end)}"
    }

/** The number of day cells a month grid holds, in rows. */
const val TT_MONTH_GRID_ROWS: Int = 6

/** The number of day cells a month grid holds, across. */
const val TT_MONTH_GRID_COLUMNS: Int = 7

/**
 * Columns in the year grid. Four, because eleven years at three per row is four ragged rows and
 * at four it is three with a short last one, and eleven is `2 x span + 1` for any span — so the
 * count is a property of the window, not a number picked for this year.
 */
const val TT_YEAR_GRID_COLUMNS: Int = 4

internal const val TT_MONTH_GRID_CELLS: Int = TT_MONTH_GRID_ROWS * TT_MONTH_GRID_COLUMNS

/**
 * How a month's name is read, resolved from resources rather than formatted.
 *
 * A [composed] function taking the month's name as an argument, so the calendar arithmetic
 * above stays free of `stringResource` — a `@Composable` in the middle of `ttMonthGrid`
 * would make the one part worth testing untestable without a frame.
 */
@Composable
internal fun ttMonthName(month: Month): String = stringResource(
    when (month) {
        Month.JANUARY -> Res.string.month_january
        Month.FEBRUARY -> Res.string.month_february
        Month.MARCH -> Res.string.month_march
        Month.APRIL -> Res.string.month_april
        Month.MAY -> Res.string.month_may
        Month.JUNE -> Res.string.month_june
        Month.JULY -> Res.string.month_july
        Month.AUGUST -> Res.string.month_august
        Month.SEPTEMBER -> Res.string.month_september
        Month.OCTOBER -> Res.string.month_october
        Month.NOVEMBER -> Res.string.month_november
        Month.DECEMBER -> Res.string.month_december
    },
)

/** A weekday's abbreviation, in the app's own words rather than the library's. */
@Composable
internal fun ttWeekdayName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> Res.string.weekday_monday
        DayOfWeek.TUESDAY -> Res.string.weekday_tuesday
        DayOfWeek.WEDNESDAY -> Res.string.weekday_wednesday
        DayOfWeek.THURSDAY -> Res.string.weekday_thursday
        DayOfWeek.FRIDAY -> Res.string.weekday_friday
        DayOfWeek.SATURDAY -> Res.string.weekday_saturday
        DayOfWeek.SUNDAY -> Res.string.weekday_sunday
    },
)

/**
 * A date as "4 Oct 2026", the form a field shows and a reader scans for.
 *
 * Public from 23.5 rather than `internal`, because a sprint's window is rendered outside
 * the field that picked it: a list row saying "4 Oct – 18 Oct 2026" has to format a
 * [LocalDate] and there is no other way to do it that reuses this file's month-name
 * lookup. Formatting it again at the call site is how a date grid and the row above it
 * end up disagreeing about what a month is called.
 */
@Composable
fun ttDateLabel(date: LocalDate): String =
    "${date.day} ${ttMonthName(date.month)} ${date.year}"

/**
 * A date without its year, for the second half of a window that is in the same year as
 * the first.
 *
 * "4 Oct – 18 Oct 2026" reads in one pass; "4 Oct 2026 – 18 Oct 2026" reads as two
 * separate facts and makes the reader check that the years match. So the year is printed
 * once, on whichever end is not [ttDateRange.start]'s year — and only when the caller has
 * established that they differ, which is why this is not the default.
 */
@Composable
fun ttDateLabelWithoutYear(date: LocalDate): String =
    "${date.day} ${ttMonthName(date.month)}"

// MARK: The grid

/**
 * A month of days, with the week above it and the month above that.
 *
 * ## The selected day is an accent disc, and the ink on it is [ttOnAccent]
 *
 * The accent is the only fill in this app that carries text — which is what [ttOnAccent] is
 * for, and why it is white rather than "whatever reads well". It measures **6.39:1** in light
 * and **4.73:1** in dark over the accent (the figures `ttGlass.LENS_ALPHA` is tuned against,
 * and which `GlassFillContrastTest` holds). Both clear the 4.5:1 body-text bar, so the day
 * numbers stay legible in either appearance without a second token.
 *
 * An unselected day is [ttLabel], which is why the grid needs no "normal day" ink of its own.
 *
 * ## Today is a ring, not a fill
 *
 * A filled disc for today would be indistinguishable from the selected day, and those two
 * mean opposite things. So today gets an accent **outline** and full ink, which is
 * distinguishable by shape as well as by colour — a ring is not a disc, and a viewer with the
 * common red/green deficiency still sees which one is which.
 */
@Composable
fun TTDayGrid(
    month: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    today: LocalDate? = null,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    onStepMonth: (Int) -> Unit = {},
    onPickYear: (() -> Unit)? = null,
) {
    val accent = ttAccent()
    val cells = remember(month, firstDayOfWeek) { ttMonthGrid(month, firstDayOfWeek) }

    Column(
        modifier = modifier.testTag(TTDayGridTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonthStepGlyph(
                glyph = ttActionIcons.chevronLeft,
                contentDescription = stringResource(Res.string.date_previous_month),
                step = -1,
                onClick = { onStepMonth(-1) },
            )
            // The label carries the year *and* is the way to change it. A separate pair of year
            // arrows was the alternative and needs four hit targets in a row that is also the
            // month navigation; making the one piece of text that already names the year the
            // control keeps the header at two arrows and gives the year a target the size of the
            // word "2026". The chevron is `chevronDown` rather than a new glyph because it is
            // the same affordance the closed field uses to say "this opens something".
            Row(
                modifier = Modifier
                    .then(
                        if (onPickYear == null) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = onPickYear)
                        },
                    )
                    .testTag(TTMonthLabelTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            ) {
                ttText(
                    text = "${ttMonthName(month.month)} ${month.year}",
                    style = ttTypography.subheadlineSelected,
                    color = ttLabel(),
                    textAlign = TextAlign.Center,
                )
                if (onPickYear != null) {
                    ttIcon(
                        image = ttActionIcons.chevronDown,
                        // The label's own text already reads "March 2026"; a description here
                        // would announce the year twice without saying what tapping it does.
                        contentDescription = stringResource(Res.string.date_choose_year),
                        tint = ttLabelSecondary(),
                        modifier = Modifier.size(dateFieldChevronSize),
                    )
                }
            }
            MonthStepGlyph(
                glyph = ttActionIcons.chevronRight,
                contentDescription = stringResource(Res.string.date_next_month),
                step = 1,
                onClick = { onStepMonth(1) },
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            for (day in ttWeekdayOrder(firstDayOfWeek)) {
                ttText(
                    text = ttWeekdayName(day).take(TT_WEEKDAY_INITIALS),
                    style = ttTypography.captionSmall,
                    modifier = Modifier.weight(1f).testTag(TTWeekdayTag(day)),
                    // `ttBodyLabel`, not `ttLabelSecondary()`. The secondary label measures
                    // 3.30:1 on this popup's base in light appearance, below the 4.5:1 that
                    // `captionSmall` text needs — and this is the row that says what each
                    // column *is*, so a user who cannot read "Wed" cannot tell which
                    // column they are aiming at. `ttBodyLabel` is documented for the opaque
                    // `ttSurface()` this popup's base is built from, and measures 17:1 on it.
                    color = ttBodyLabel(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }

        for (row in 0 until TT_MONTH_GRID_ROWS) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until TT_MONTH_GRID_COLUMNS) {
                    val day = cells[row * TT_MONTH_GRID_COLUMNS + column]
                    if (day == null) {
                        // An empty cell still occupies its slot. `Spacer` rather than nothing,
                        // because the alternative is the surviving days of a short month
                        // sliding left by a column width each time the month changes length.
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        DayCell(
                            day = day,
                            isSelected = day == selected,
                            isToday = day == today,
                            accent = accent,
                            onClick = { onSelect(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One day.
 *
 * Sized at [ttSpacing.iconButtonSize] rather than at a number chosen here: a day cell is a tap
 * target, it is the smallest thing a user has to hit accurately on the whole screen, and
 * 44dp is the figure both platforms' accessibility guidance names. A cell sized to its
 * numeral is about 20dp and is unusable.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.row)
    Box(
        modifier = modifier
            .size(ttSpacing.iconButtonSize)
            .testTag(TTDayCellTag(day))
            .clip(shape)
            .background(if (isSelected) accent else Color.Transparent)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(width = ttSeparatorWidth, color = accent, shape = shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = day.day.toString(),
            style = ttTypography.footnote,
            color = if (isSelected) ttOnAccent else ttLabel(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The years to choose from, in place of the day grid while the label is being tapped.
 *
 * **One screen rather than a second popup.** The field's popup already has a position provider
 * that flips above the field when there is no room below, because the end date of a sprint sits
 * near the bottom of the form. A year list in a popup *of* the popup would need that logic
 * twice and would open upward out of a popup that had itself been placed to stay on screen.
 * Swapping the grid's contents keeps one positioning decision in one place.
 *
 * A grid rather than a list, so eleven years fit without scrolling and the current one is
 * visible without moving anything — the same reason [TTDayGrid] is a grid.
 *
 * @param span years either side of [today], defaulting to [TT_YEAR_WINDOW_SPAN].
 */
@Composable
internal fun TTYearGrid(
    today: LocalDate,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    span: Int = TT_YEAR_WINDOW_SPAN,
) {
    Column(
        modifier = modifier.testTag(TTYearGridTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
    ) {
        for (row in ttYearRows(ttYearWindow(today, span))) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (year in row) {
                    val isCurrent = year == today.year
                    ttText(
                        text = year.toString(),
                        style = ttTypography.subheadlineSelected,
                        // `ttAccent` for the current year and `ttBodyLabel` for the rest, which
                        // is the same pair the day grid uses for "selected" and "ordinary" — a
                        // year list where the current year is indistinguishable from the rest is
                        // a list the reader has to count.
                        color = if (isCurrent) ttAccent() else ttBodyLabel(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onPick(year) }
                            .padding(vertical = ttSpacing.xs)
                            .testTag(TTYearCellTag(year)),
                    )
                }
                // A short final row still fills its width, so the last year is not left hanging
                // against the left edge while the rows above it are centred.
                repeat(TT_YEAR_GRID_COLUMNS - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** [years] in rows of [TT_YEAR_GRID_COLUMNS], last row short rather than padded out. */
internal fun ttYearRows(years: IntRange, columns: Int = TT_YEAR_GRID_COLUMNS): List<List<Int>> =
    years.toList().chunked(columns)

/** [step] is -1 or 1: the month arrow that moves the grid that many months. */
fun TTMonthStepTag(step: Int): String = "design.dateField.monthStep.$step"

/**
 * A month arrow: 44dp to hit, [dateFieldChevronSize] to look at.
 *
 * These are two different numbers doing two different jobs, and they used to be one. Sizing
 * the *icon* to `iconButtonSize` gave a 44dp chevron in a header row whose other content is the
 * word "March 2026". It read as a pair of slabs flanking the month name rather than as
 * navigation.
 *
 * So the 44dp is now the `Box` and the chevron is [dateFieldChevronSize], the same
 * "half the target, reads as an affordance" figure the closed field's own chevron uses.
 *
 * Measured, on the JVM and on the iOS simulator, which agree exactly: the arrow's ink spans
 * **11px of its 44px box** (0.25 of it) and covers **72 pixels**, against **22px** (0.50) and
 * **270 pixels** at the old size. Those are the numbers `MonthArrowRenderTest` bounds, and the
 * ink threshold stops short of the antialiased edge, so the drawn extent is larger than 11px —
 * the ratio is what the assertion uses, not the absolute.
 */
@Composable
private fun MonthStepGlyph(
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    step: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ttSpacing.iconButtonSize)
            // [step], not [contentDescription]: a tag built from a localised string is a tag
            // that changes when the translation does, so a test asserting "the next-month arrow"
            // would fail on a locale rather than on a defect.
            .testTag(TTMonthStepTag(step))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ttIcon(
            image = glyph,
            contentDescription = contentDescription,
            tint = ttLabel(),
            modifier = Modifier.size(dateFieldChevronSize),
        )
    }
}

// MARK: The field

/**
 * A date input: the current date, and a grid to change it.
 *
 * ## `LocalDate`, not `Instant`
 *
 * The value is a **calendar date**, and that is not a detail. A sprint starts "on the 4th",
 * and an `Instant` cannot answer that question without a time zone to interpret it in — and
 * the answer changes with the device, so a sprint stored as an instant begins on a different
 * day for a user who flies. The conversion to and from the domain's `Instant` belongs at the
 * edge, in 23.5, where the zone is a decision somebody actually made.
 *
 * ## The popup, and why the grid is not the field
 *
 * A [Popup] rather than a sheet, because a sheet covers the field: the user picks a date with
 * no reference for what they are changing, and a form with two date fields needs one of them
 * hidden behind the other. The popup is dismissable and focusable, which is what lets an
 * outside tap, a back gesture, or its own **Done** button close it.
 *
 * ## Centred and sized to the calendar — this reverses 23.4
 *
 * 23.4 anchored the popup beside the field, on the reasoning that the field staying in view was
 * the reference for what was being changed. That did not survive contact with the grid: **every
 * row of it is `fillMaxWidth()`**, and a `fillMaxWidth` child inside a wrap-content popup
 * resolves to the popup's *maximum* width. So the popup was window-width — measured at 1000px
 * inside a 1024px window — and a popup that fills the window has no outside left to tap. It read
 * as a page rather than a picker, and it had no visible way out of it.
 *
 * Anchoring cannot fix that, because the width is settled before any offset is asked for. So
 * the popup takes its width from the calendar ([dateFieldPopupWidth]) and
 * [DateFieldPopupPositionProvider] centres it on both axes. The field-as-reference is given up
 * deliberately: a picker the user can see all of, read at its own size, and dismiss three ways
 * is worth more than one beside a field it covers.
 *
 * ## The popup is a lens over an opaque base
 *
 * [ttGlassSurface] with a `base`, exactly as [TTBottomSheetScaffold] does it and for the same
 * reason: without a base the fill ramp's shipped alpha is `0.55 * 0.55` = 0.3025 tinted
 * toward the accent, and a calendar read through a third of an accent wash is a calendar the
 * user has to check twice. The base is `ttSurface()` at `1f - LENS_ALPHA`, so the grid's own
 * numerals sit on the same near-opaque field colour as everything else that has to be read.
 */
@Composable
fun TTDateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the field may be opened at all.
     *
     * A disabled field shows its value and refuses the picker: dimmed with
     * [ttGlass.DISABLED_ALPHA] over the whole control, exactly as [ttGlassIconButton] dims,
     * and inert — `clickable(enabled = false)` also takes the `Disabled` semantics, so a test
     * reads `assertIsNotEnabled()` on the same node a tap would land on. Present rather than
     * hidden, for the same reason a disabled toolbar button is: the lock is legible from the
     * page around it, and a field that vanished would leave the form's shape lying about
     * what is in it.
     */
    enabled: Boolean = true,
    today: LocalDate? = null,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Opened by default, so a preview and a test do not have to tap first. */
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    // Today, resolved once and for all. `today` is the test seam and every test passes it; the
    // system clock is only reached by a caller that passed nothing, and binding it here means
    // the seeding below and the year window cannot disagree about which day it is.
    val anchorToday = remember(today) { today ?: ttSystemToday() }
    // The month the grid is showing, which is *not* the field's value: a user who opens the
    // picker on a sprint that ended in March and steps forward to April must not have the
    // field's value follow them. It is seeded from the value and re-seeded when the value
    // changes from outside.
    //
    // Seeded from `anchorToday` rather than `value ?: today`, and clamped. Both halves were a
    // bug: `value ?: today` is null for a sprint being created, and every arrow then did
    // `shownMonth?.let { shift(it) }`, which assigns null to null — so the arrows moved nothing
    // *ever*, not just on the first press. A user creating a sprint got a calendar they could
    // not page through, showing `LocalDate(1970, 1, 1)` because that was the last fallback
    // standing. One non-null seed fixes both symptoms at their shared cause.
    var shownMonth by remember(value, anchorToday) {
        mutableStateOf(ttClampToYearWindow(value ?: anchorToday, anchorToday))
    }
    // Whether the popup is showing years rather than days. Local to the field and reset whenever
    // the popup closes, so reopening the picker lands on days rather than on the year list the
    // user was last browsing.
    var isChoosingYear by remember { mutableStateOf(false) }
    // The height the popup's *contents* take, learned from the day grid and then imposed on the
    // year grid, so the popup does not change size when the calendar becomes the year list.
    //
    // **A [Dp], not a pixel count.** This was the defect, and it was invisible at the tests'
    // density of 1 because at density 1 a pixel count *is* a dp count. `onSizeChanged` reports
    // pixels; a 349dp grid measures 1047px on a density-3 phone, and reading that back as
    // `1047.dp` asked for a card three times too tall — taller than the screen, so
    // [ttCentredPopupOffset]'s `coerceAtLeast(0)` put `y` at 0 and the popup sat at the top
    // under a background running the full height of the window. Measured at density 3: a popup
    // 996x768px in a 768px window, its grid clamped from 696px, which is the report exactly.
    // Holding the value as `Dp` means the unit travels with it and the same mistake cannot be
    // made twice; `BoardColumn`'s `headerHeight` is the same idiom.
    //
    // [Dp.Unspecified] rather than zero as the "not measured yet" sentinel, because `0.dp` is a
    // value this must never hold — applying it collapses the grid, `onSizeChanged` reports zero
    // back, and the popup never opens at all. That is not hypothetical either; see the `then`
    // below.
    //
    // Measured rather than computed, deliberately. The day grid's height is a sum of tokens
    // (a 44dp header, a caption line, six 44dp rows) and a typed line height, so a constant
    // would have to be re-derived whenever the typography or the spacing scale changed.
    var popupContentHeight by remember { mutableStateOf(Dp.Unspecified) }
    // Read here rather than inside the `onSizeChanged` lambda, which is not a composable scope
    // and so cannot read a `CompositionLocal` at all.
    val density = LocalDensity.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(text = label, color = ttBodyLabel(), modifier = Modifier.testTag(TTDateFieldLabelTag))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(ttRadius.control))
                    .ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))
                    // The dim before the tap, so a disabled field is visibly dead as well
                    // as actually dead — the same pairing [ttGlassIconButton] uses.
                    .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .padding(horizontal = ttSpacing.lg, vertical = ttSpacing.md)
                    .testTag(TTDateFieldTag),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ttText(
                    text = value?.let { ttDateLabel(it) } ?: stringResource(Res.string.date_placeholder),
                    style = ttTypography.callout,
                    // An empty field is a hint, so it takes the secondary ink for the same
                    // reason `LabelledField`'s placeholder does: full ink makes an unset field
                    // look set.
                    color = if (value == null) ttLabelSecondary() else ttLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ttIcon(
                    image = ttActionIcons.chevronDown,
                    // The field's own text already says what it is; a description here would
                    // announce the same control twice.
                    contentDescription = null,
                    tint = ttLabelSecondary(),
                    modifier = Modifier.size(dateFieldChevronSize),
                )
            }

            if (expanded && enabled) {
                // One dismissal, three ways: the outside tap and the back gesture arrive here,
                // and the popup's own Done button calls the same thing. Done deliberately does
                // *not* touch `value` — choosing a day already closes the popup, so a Done that
                // also assigned would be a second, invisible way to change the answer.
                val dismissPicker: () -> Unit = {
                    expanded = false
                    isChoosingYear = false
                }

                Popup(
                    popupPositionProvider = DateFieldPopupPositionProvider,
                    onDismissRequest = dismissPicker,
                    properties = PopupProperties(focusable = true),
                ) {
                    // `BoxWithConstraints` for one number: `maxHeight` is the height of the
                    // window this popup is in, which is the budget the card has to fit inside.
                    // There is no other way to ask — the popup is its own composition root, so
                    // `LocalWindowInfo` is not the app's window from inside it.
                    BoxWithConstraints {
                    Column(
                        modifier = Modifier
                            .ttGlassSurface(
                                shape = RoundedCornerShape(ttRadius.surface),
                                base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                            )
                            // Before `.padding`, so this is the popup's whole width and the
                            // padding comes out of it. Compose coerces the request into the
                            // popup window's own constraints, so on a screen narrower than the
                            // calendar this shrinks rather than overhanging, and no separate
                            // clamp is needed.
                            .width(dateFieldPopupWidth)
                            // Never taller than this share of the window. On a landscape phone
                            // the window is around 360dp and the calendar wants about 390dp, so
                            // without a cap the card is taller than the screen it is centred
                            // in: `ttCentredPopupOffset` clamps `y` to 0, the card sits at the
                            // top, and its background runs the full height of the window. That
                            // is the same symptom the density bug produced, from the other
                            // direction, so both are closed.
                            .heightIn(max = maxHeight * TT_DATE_FIELD_MAX_HEIGHT_FRACTION)
                            // The tag goes *outside* the padding, so [TTDateFieldPopupTag]
                            // marks the popup's own 332dp surface rather than the 308dp inside
                            // it. A tag after `.padding` attaches to the content, and every
                            // measurement taken through this one was 24dp short of the thing
                            // being measured.
                            .testTag(TTDateFieldPopupTag)
                            .padding(ttSpacing.xl),
                    ) {
                        // Both grids occupy the *same* box, so the popup does not change size
                        // when the calendar becomes the year list.
                        //
                        // This used to be about *position*: a popup's offset is computed from
                        // its own height, and the tall day grid and the short year grid
                        // straddled the "does it fit below the field" line, so the two opened
                        // on opposite sides of the field. Centring the popup has removed that
                        // dependency — the offset no longer refers to the field — and what
                        // remains is worth keeping: without this the popup visibly resizes
                        // under the user's finger when they tap the month label.
                        Box(
                            modifier = Modifier
                                // Budget first, then the pin. `weight(fill = false)` hands this
                                // box at most whatever the column has left once the footer is
                                // placed, and `height` then applies the pin only if it fits
                                // inside that. So on a roomy window the calendar is exactly its
                                // own height, and on a short one it is clamped to what is left
                                // and scrolls — rather than being laid out taller than the
                                // window and clipped by it.
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                // The scrolling node is this `Box`, not the grid inside it, so
                                // the grid's own tag cannot answer "does the calendar scroll".
                                .testTag(TTDateScrollTag)
                                // The unmeasured case must constrain *nothing*, and that
                                // guard is load-bearing rather than defensive: `height(0.dp)`
                                // would collapse the day grid to nothing, `onSizeChanged` would
                                // report zero back, and the value would stay zero for the life
                                // of the field — a popup that never opens, with no error
                                // anywhere. Dropping this guard is exactly how the width pin
                                // went: it looked like a tidy `then` and took the guard with it.
                                .then(
                                    if (popupContentHeight == Dp.Unspecified) {
                                        Modifier
                                    } else {
                                        Modifier.height(popupContentHeight)
                                    },
                                )
                                .onSizeChanged { size ->
                                    popupContentHeight = with(density) { size.height.toDp() }
                                },
                        ) {
                            if (isChoosingYear) {
                                TTYearGrid(
                                    today = anchorToday,
                                    onPick = { year ->
                                        // Keeps the month the user was looking at: choosing a
                                        // year and choosing a month are two questions, and
                                        // answering the first should not silently answer the
                                        // second.
                                        shownMonth = ttClampToYearWindow(
                                            LocalDate(year, shownMonth.month, 1),
                                            anchorToday,
                                        )
                                        isChoosingYear = false
                                    },
                                )
                            } else {
                                TTDayGrid(
                                    month = shownMonth,
                                    selected = value,
                                    // `anchorToday`, not `today`: the field passed no date, but
                                    // the ring marking which day is today is still true and is
                                    // how the user finds the date they were looking for.
                                    today = anchorToday,
                                    firstDayOfWeek = firstDayOfWeek,
                                    onSelect = { day ->
                                        onValueChange(day)
                                        expanded = false
                                    },
                                    // Shifting `shownMonth` directly, which is non-null by
                                    // construction. The old `shownMonth?.let { }` made this
                                    // arrow a no-op whenever the seed was null.
                                    onStepMonth = { step ->
                                        shownMonth =
                                            ttStepMonthInWindow(shownMonth, step, anchorToday)
                                    },
                                    onPickYear = { isChoosingYear = true },
                                )
                            }
                        }

                        // Done, below the grid rather than in the header.
                        //
                        // Two reasons, and the second is the one that matters. A calendar that
                        // fills the window can be dismissed by pressing back, which on a
                        // desktop target is not something a user looks for and on a phone is
                        // not something they can see: the picker had no *visible* way out. That
                        // is what this is for.
                        //
                        // And a footer leaves the header alone. The header row is where the
                        // month arrows live, at measured positions and sizes that
                        // `MonthArrowRenderTest` asserts against; adding a third control there
                        // would move both of them for no gain. Below the grid, the affordance is
                        // in the same place a dialog's is, and neither arrow moves.
                        //
                        // `End`, not `fillMaxWidth`, and `resting`, not `prominent`: closing a
                        // picker is not the sheet's primary action, and a full-width accent
                        // button would claim to be the most important thing on the screen when
                        // the thing the user came for is a date.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = ttSpacing.lg),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            ttGlassButton(
                                label = stringResource(Res.string.date_done),
                                onClick = dismissPicker,
                                modifier = Modifier.testTag(TTDateDoneTag),
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Centres the popup in the window, on both axes.
 *
 * **Ignoring the anchor is the fix, not a simplification.** 23.4 placed the picker beside the
 * field and flipped it above the field when a sheet left no room below. Both are gone, for the
 * reason given in [TTDateField]: the grid's `fillMaxWidth()` rows made the popup window-width,
 * so there was no outside left to tap and it read as a full-screen page with no visible exit.
 *
 * Anchoring cannot fix that, because the width is already settled by the time an offset is
 * asked for — [popupContentSize] arrives measured. Centring is also the only placement that
 * does not depend on where the field happens to be, which matters here because the field is
 * in a bottom sheet and moves with its own content.
 *
 * `anchorBounds` and `layoutDirection` are ignored *because* centring makes them irrelevant,
 * not because they were forgotten: the offset no longer refers to the field, and it is the
 * same number mirrored for a right-to-left layout.
 *
 * A popup taller than the window starts at the top rather than at a negative offset. That is
 * the only available choice — it is already clipped — and it is the `coerceAtLeast(0)`.
 */
private object DateFieldPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = ttCentredPopupOffset(windowSize, popupContentSize)
}

/**
 * Where a popup of [content] sits in a [window] of this size: dead centre.
 *
 * Pulled out of the provider as a plain function so it can be asserted on directly, and that
 * is not a formality — **a popup's rendered position cannot be read back.** A `Popup` is its
 * own composition root: `onRoot()` finds two of them once one is open, and the content's
 * `boundsInRoot` are reported in the popup's own coordinate space, before the window offset is
 * applied. So the size half of this fix is measurable on a rendered frame and the position half
 * is only reachable here. Which is the right way round: the width was the defect.
 *
 * `coerceAtLeast(0)` on both axes, because content larger than the window would otherwise be
 * given a negative offset and its top-left corner — the part carrying the month arrows — pushed
 * off-screen.
 */
internal fun ttCentredPopupOffset(window: IntSize, content: IntSize): IntOffset = IntOffset(
    x = ((window.width - content.width) / 2).coerceAtLeast(0),
    y = ((window.height - content.height) / 2).coerceAtLeast(0),
)

/**
 * The popup's width: the calendar's, not the window's.
 *
 * Seven 44dp day cells and the surface's own padding either side, which is
 * **332dp** — computed from the two tokens the grid already uses rather than written as a
 * number, so it cannot drift away from the cell size or the padding.
 *
 * This is the whole of the "sized to the calendar" half of the fix, and it works because of
 * how the grid is laid out: `TTDayGrid`'s rows are `fillMaxWidth()` and its cells are
 * `weight(1f)`, so given a width of 332dp each of the seven cells resolves to exactly 44dp,
 * the size [DayCell] documents as the tap target. Left to itself the popup instead resolves
 * those rows to its own *maximum* width, which is the window's — that is the bug.
 *
 * Internal rather than private so a test can state the expectation in the same terms the
 * value is built from, instead of hard-coding 332.
 */
internal val dateFieldPopupWidth: Dp =
    ttSpacing.iconButtonSize * TT_MONTH_GRID_COLUMNS + ttSpacing.xl * 2

// MARK: Tags

const val TTDateFieldTag = "design.dateField"
const val TTDateFieldLabelTag = "design.dateField.label"
const val TTDateFieldPopupTag = "design.dateField.popup"
const val TTDateDoneTag = "design.dateField.done"
const val TTDayGridTag = "design.dateField.grid"
const val TTDateScrollTag = "design.dateField.scroll"

/**
 * How much of the window's height the calendar may take.
 *
 * **0.9, and the margin is the point.** A card exactly as tall as the window is centred at
 * `y = 0` and touches both edges, so a tenth is left to see that it is a card and not the page.
 * It is a fraction of a *measured* window height rather than a fixed dp, so it behaves the same
 * on a phone, on a tablet and in the desktop window.
 */
internal const val TT_DATE_FIELD_MAX_HEIGHT_FRACTION: Float = 0.9f

/** The "March 2026" header, which is also the control that opens the year grid. */
const val TTMonthLabelTag = "design.dateField.monthLabel"

/** The year grid, shown in the popup in place of the day grid. */
const val TTYearGridTag = "design.dateField.yearGrid"

/** One day's cell. Takes the date so a test can address the day it means. */
fun TTDayCellTag(day: LocalDate): String = "design.dateField.day.${day.year}-${day.month.number}-${day.day}"

/**
 * A weekday's column header.
 *
 * Tagged because the header is where a contrast defect would land first and least visibly:
 * it is the row that says what each column *is*, it is `captionSmall`, and a header that is
 * too dim to read does not look broken — the calendar just becomes hard to use. So the ink
 * on it is measured in `DateFieldContrastTest` rather than assumed.
 */
fun TTWeekdayTag(day: DayOfWeek): String = "design.dateField.weekday.${day.name}"

/** One year in [TTYearGrid]. */
fun TTYearCellTag(year: Int): String = "design.dateField.year.$year"

/**
 * How many characters of a weekday's name the header shows.
 *
 * Three — "Mon", "Tue" — because the header column is `1/7` of a phone's width, about 44dp,
 * and three characters of [ttTypography.captionSmall] fit inside it. **One** does not fit as a
 * *label*: "W" is Wednesday and also Thursday, so a two-W header is a header the user has to
 * resolve by counting columns from a remembered Monday.
 */
internal const val TT_WEEKDAY_INITIALS = 3

/**
 * The field's chevron, at half [ttSpacing.iconButtonSize] so it reads as an affordance.
 *
 * The same relationship `ProjectSwitcherButton`'s chevron has to its monogram, and for the
 * same reason: an icon the same size as the control it sits in competes with it rather than
 * belonging to it.
 */
internal val dateFieldChevronSize = ttSpacing.iconButtonSize / 2

@Preview(name = "Date field, set and empty", showBackground = true, widthDp = 360)
@Composable
private fun TTDateFieldPreview() {
    ttTheme(ThemeScheme.System) {
        Box(modifier = Modifier.fillMaxWidth().background(ttCanvas())) {
            AmbientBackdrop()
            Column(
                modifier = Modifier.padding(ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
            ) {
                TTDateField(
                    label = "Start",
                    value = LocalDate(2026, Month.OCTOBER, 4),
                    onValueChange = {},
                )
                TTDateField(
                    label = "End",
                    value = null,
                    onValueChange = {},
                )
            }
        }
    }
}

@Preview(name = "Day grid", showBackground = true, widthDp = 360)
@Composable
private fun TTDayGridPreview() {
    ttTheme(ThemeScheme.Light) {
        Box(modifier = Modifier.fillMaxWidth().background(ttCanvas())) {
            AmbientBackdrop()
            Column(
                modifier = Modifier
                    .padding(ttSpacing.screenGutterLarge)
                    .ttGlassSurface(
                        shape = RoundedCornerShape(ttRadius.surface),
                        base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                    )
                    .padding(ttSpacing.xl),
            ) {
                TTDayGrid(
                    month = LocalDate(2026, Month.OCTOBER, 1),
                    selected = LocalDate(2026, Month.OCTOBER, 4),
                    today = LocalDate(2026, Month.OCTOBER, 4),
                    onSelect = {},
                )
            }
        }
    }
}