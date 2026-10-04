package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.resources.Res
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
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------
// The calendar arithmetic, as pure functions
// ---------------------------------------------------------------------------------------------
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

// ---------------------------------------------------------------------------------------------
// The grid
// ---------------------------------------------------------------------------------------------

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
                onClick = { onStepMonth(-1) },
            )
            ttText(
                text = "${ttMonthName(month.month)} ${month.year}",
                style = ttTypography.subheadlineSelected,
                color = ttLabel(),
                textAlign = TextAlign.Center,
            )
            MonthStepGlyph(
                glyph = ttActionIcons.chevronRight,
                contentDescription = stringResource(Res.string.date_next_month),
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

/** The month steppers, as a pair so both are sized and tinted identically. */
@Composable
private fun MonthStepGlyph(
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    ttIcon(
        image = glyph,
        contentDescription = contentDescription,
        tint = ttLabel(),
        modifier = Modifier
            .size(ttSpacing.iconButtonSize)
            .testTag(TTDateMonthStepTag(contentDescription))
            .clickable(onClick = onClick),
    )
}

// ---------------------------------------------------------------------------------------------
// The field
// ---------------------------------------------------------------------------------------------

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
 * An anchored [Popup] rather than a sheet, because a date picker has to sit *next to* the
 * field it is changing: a sheet covers the field, so the user picks a date with no
 * reference for what they are changing, and a form with two date fields needs one of them
 * hidden behind the other. The popup is dismissable and focusable, which is what lets an
 * outside tap or a back gesture close it.
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
    today: LocalDate? = null,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Opened by default, so a preview and a test do not have to tap first. */
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    // The month the grid is showing, which is *not* the field's value: a user who opens the
    // picker on a sprint that ended in March and steps forward to April must not have the
    // field's value follow them. It is seeded from the value and re-seeded when the value
    // changes from outside.
    var shownMonth by remember(value) { mutableStateOf(value ?: today) }
    var anchorWidthPx by remember { mutableStateOf(0) }

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
                    .onGloballyPositioned { anchorWidthPx = it.size.width }
                    .clickable { expanded = !expanded }
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

            if (expanded) {
                Popup(
                    popupPositionProvider = DateFieldPopupPositionProvider(
                        gapPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            ttSpacing.sm.roundToPx()
                        },
                        minWidthPx = anchorWidthPx,
                    ),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        modifier = Modifier
                            .ttGlassSurface(
                                shape = RoundedCornerShape(ttRadius.surface),
                                base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                            )
                            .padding(ttSpacing.xl)
                            .testTag(TTDateFieldPopupTag),
                    ) {
                        TTDayGrid(
                            month = shownMonth ?: today ?: value ?: LocalDate(1970, 1, 1),
                            selected = value,
                            today = today,
                            firstDayOfWeek = firstDayOfWeek,
                            onSelect = { day ->
                                onValueChange(day)
                                expanded = false
                            },
                            onStepMonth = { step ->
                                shownMonth = shownMonth?.let { ttShiftMonth(it, step) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Places the popup under the field, flipping above it when there is no room below.
 *
 * The flip is not a nicety: at the bottom of a sprint form — which is where the end date
 * lives — "below" is off-screen, and a picker the user cannot see is a picker they cannot
 * use. The horizontal clamp is the same reason `ProjectSwitcherButton` has one: a field
 * near the right edge would otherwise push its popup off the window.
 */
private class DateFieldPopupPositionProvider(
    private val gapPx: Int,
    private val minWidthPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left.coerceIn(0, maxX)
            LayoutDirection.Rtl ->
                (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        }
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else above.coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

// ---------------------------------------------------------------------------------------------
// Tags
// ---------------------------------------------------------------------------------------------

const val TTDateFieldTag = "design.dateField"
const val TTDateFieldLabelTag = "design.dateField.label"
const val TTDateFieldPopupTag = "design.dateField.popup"
const val TTDayGridTag = "design.dateField.grid"

/** One day's cell. Takes the date so a test can address the day it means. */
fun TTDayCellTag(day: LocalDate): String = "design.dateField.day.${day.year}-${day.month.number}-${day.day}"

/** A month stepper, by what it says rather than by which arrow it is. */
fun TTDateMonthStepTag(contentDescription: String): String = "design.dateField.monthStep.$contentDescription"

/**
 * A weekday's column header.
 *
 * Tagged because the header is where a contrast defect would land first and least visibly:
 * it is the row that says what each column *is*, it is `captionSmall`, and a header that is
 * too dim to read does not look broken — the calendar just becomes hard to use. So the ink
 * on it is measured in `DateFieldContrastTest` rather than assumed.
 */
fun TTWeekdayTag(day: DayOfWeek): String = "design.dateField.weekday.${day.name}"

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