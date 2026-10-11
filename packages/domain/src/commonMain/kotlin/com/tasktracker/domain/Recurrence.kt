package com.tasktracker.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The rule that says when a work item comes round again.
 *
 * A sealed set of four cadences rather than a flags-and-integers record, because the
 * four cases carry different required parameters — a weekly rule is meaningless
 * without a weekday, a yearly one without a month *and* a day — and a data class of
 * nullable fields would make every one of those combinations representable and none
 * of them checked. Here an unrepresentable state does not exist at all.
 *
 * ## The rule lives on the item, not in a series table
 *
 * An item either repeats or it does not; the editor reads the whole item every time;
 * and a join table would turn "does this repeat" into a query. Completing a recurring
 * item does **not** slide its own due date — the expansion use case creates the next
 * instance, dated at [nextOccurrenceAfter] and carrying this same rule, so the
 * finished one stays finished with its own history and the series advances by a new
 * row rather than by rewriting an old one.
 *
 * ## `until` is inclusive and compared as an instant
 *
 * An occurrence landing exactly on [until] still happens; the first one *after* it
 * does not, and [nextOccurrenceAfter] returns `null` for it. Compared in the caller's
 * timezone through the occurrence instant, not by local date, because "stops at the
 * end of the 31st" is a question about an instant once a time-of-day is involved.
 */
@Serializable
sealed interface Recurrence {

    /** When the series stops — an occurrence exactly on this instant still happens. */
    val until: Instant?

    /**
     * The first occurrence strictly after [after], in [timeZone], or `null` when
     * [until] has passed.
     *
     * The time-of-day of the result is the time-of-day of [after] in [timeZone]: a
     * task due at noon recurs at noon, not at midnight, and the alternative — always
     * midnight — would silently move a task the moment the series advanced.
     *
     * Day-of-month rules clamp to the short month rather than skipping it: a task on
     * the 31st appears on the 28th of February and on the 31st of March. Skipping
     * would make the series *look* broken every February, and the clamp is what every
     * calendar a user has already seen does.
     *
     * [timeZone] is a parameter rather than a platform default, for the reason every
     * rule in this project takes its instant from its caller: a rule that reads the
     * device's zone is a rule whose tests can only be run in one zone.
     */
    fun nextOccurrenceAfter(after: Instant, timeZone: TimeZone = TimeZone.UTC): Instant? {
        val afterLocal = after.toLocalDateTime(timeZone)
        val afterDate = afterLocal.date
        val nextDate = when (this) {
            is Daily -> afterDate.plus(1, DateTimeUnit.DAY)
            is Weekly -> {
                val delta = (isoDayOfWeek - afterDate.dayOfWeek.isoDayNumber + 7) % 7
                afterDate.plus(if (delta == 0) 7 else delta, DateTimeUnit.DAY)
            }
            is Monthly -> afterDate.nextMonthly(dayOfMonth)
            is Yearly -> afterDate.nextYearly(month, dayOfMonth)
        }
        val candidate = nextDate.atTime(afterLocal.time).toInstant(timeZone)
        val stop = until ?: return candidate
        return candidate.takeIf { it <= stop }
    }

    /**
     * The stable machine key: the stored column value and the JSON name, one string
     * for both so they cannot drift apart — the same contract [WorkItemStatus.wireValue]
     * gives its enum, spelled as a function because these are data classes rather than
     * enums.
     */
    fun wireValue(): String = when (this) {
        is Daily -> "daily"
        is Weekly -> "weekly:$isoDayOfWeek"
        is Monthly -> "monthly:$dayOfMonth"
        is Yearly -> "yearly:$month-$dayOfMonth"
    }

    /** Every day. */
    @Serializable
    @SerialName("daily")
    data class Daily(override val until: Instant? = null) : Recurrence

    /** Every [isoDayOfWeek]'s week — 1 is Monday, 7 is Sunday (ISO-8601). */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val isoDayOfWeek: Int, override val until: Instant? = null) : Recurrence {
        init {
            require(isoDayOfWeek in 1..7) { "isoDayOfWeek must be 1..7, was $isoDayOfWeek" }
        }
    }

    /** Every month's [dayOfMonth] — clamped to the short months, see [nextOccurrenceAfter]. */
    @Serializable
    @SerialName("monthly")
    data class Monthly(val dayOfMonth: Int, override val until: Instant? = null) : Recurrence {
        init {
            require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31, was $dayOfMonth" }
        }
    }

    /** Every year's [month]/[dayOfMonth] — 1 is January; Feb 29 clamps in common years. */
    @Serializable
    @SerialName("yearly")
    data class Yearly(val month: Int, val dayOfMonth: Int, override val until: Instant? = null) : Recurrence {
        init {
            require(month in 1..12) { "month must be 1..12, was $month" }
            require(dayOfMonth in 1..31) { "dayOfMonth must be 1..31, was $dayOfMonth" }
        }
    }

    companion object {
        /**
         * Parses a [wireValue], or returns `null`.
         *
         * The fallible boundary for stored rows — the same trade [WorkItemStatus]
         * makes: a row written by a future build costs its recurrence, where a thrown
         * decode would cost the whole card.
         */
        fun parseWireValue(raw: String): Recurrence? {
            val parts = raw.split(':')
            return when {
                parts.size == 1 && parts[0] == "daily" -> Daily()
                parts.size == 2 && parts[0] == "weekly" ->
                    parts[1].toIntOrNull()?.takeIf { it in 1..7 }?.let { Weekly(it) }
                parts.size == 2 && parts[0] == "monthly" ->
                    parts[1].toIntOrNull()?.takeIf { it in 1..31 }?.let { Monthly(it) }
                parts.size == 2 && parts[0] == "yearly" -> {
                    val monthDay = parts[1].split('-')
                    if (monthDay.size != 2) return null
                    val month = monthDay[0].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
                    val day = monthDay[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
                    Yearly(month, day)
                }
                else -> null
            }
        }
    }
}

/**
 * The next month's occurrence of [dayOfMonth] at or after [after], clamped.
 *
 * This month is tried before the next one, so a rule on the 15th seen from the 10th
 * lands on the 15th rather than a month late — the clamp is applied first, which is
 * what stops a rule on the 31st from firing *twice* in February: seen from the 28th,
 * the clamped 28th is not strictly after the 28th, and the search moves on to March.
 */
private fun LocalDate.nextMonthly(dayOfMonth: Int): LocalDate {
    val thisMonth = LocalDate(year, monthNumber, dayOfMonth.coerceAtMost(lengthOfMonth(year, monthNumber)))
    if (thisMonth > this) return thisMonth
    val nextYear = if (monthNumber == 12) year + 1 else year
    val nextMonth = if (monthNumber == 12) 1 else monthNumber + 1
    return LocalDate(nextYear, nextMonth, dayOfMonth.coerceAtMost(lengthOfMonth(nextYear, nextMonth)))
}

/** The next year's occurrence, same shape as [nextMonthly] with a year step. */
private fun LocalDate.nextYearly(month: Int, dayOfMonth: Int): LocalDate {
    val thisYear = LocalDate(year, month, dayOfMonth.coerceAtMost(lengthOfMonth(year, month)))
    if (thisYear > this) return thisYear
    return LocalDate(year + 1, month, dayOfMonth.coerceAtMost(lengthOfMonth(year + 1, month)))
}

/**
 * How many days a given month has.
 *
 * Asked as "the day before the first of the next month" rather than a table, for the
 * reason `:packages:design-system`'s `ttDaysInMonth` is: a hard-coded
 * `31, 28, 31, 30…` is a leap-year bug waiting for February — and February is exactly
 * the month a day-29 or day-31 rule has to survive.
 */
private fun lengthOfMonth(year: Int, month: Int): Int =
    LocalDate(year, month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .plus(-1, DateTimeUnit.DAY)
        .day
