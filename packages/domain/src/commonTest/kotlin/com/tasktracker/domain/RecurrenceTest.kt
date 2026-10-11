package com.tasktracker.domain

import com.tasktracker.core.JSONCoding
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurrenceTest {

    // 2026-01-05 is a Monday, so the Mondays of January 2026 are the 5th, 12th, 19th, 26th.
    private val thursdayNoon = Instant.parse("2026-01-15T12:00:00Z")

    // ------------------------------------------------------------------
    // Daily
    // ------------------------------------------------------------------

    @Test
    fun dailyStepsExactlyOneDayAndKeepsTheTimeOfDay() {
        val next = Recurrence.Daily().nextOccurrenceAfter(thursdayNoon)
        assertEquals(
            Instant.parse("2026-01-16T12:00:00Z"),
            next,
            "daily from Thu 15 Jan 12:00Z should be Fri 16 Jan 12:00Z, " +
                "not midnight and not two days — got $next",
        )
    }

    @Test
    fun dailyFromAMidnightInstantStaysAtMidnight() {
        val next = Recurrence.Daily().nextOccurrenceAfter(Instant.parse("2026-01-15T00:00:00Z"))
        assertEquals(
            Instant.parse("2026-01-16T00:00:00Z"),
            next,
            "daily from midnight should stay midnight — got $next",
        )
    }

    // ------------------------------------------------------------------
    // Weekly
    // ------------------------------------------------------------------

    @Test
    fun weeklyStepsForwardToTheNextMatchingWeekday() {
        // Thursday is ISO 4; the next Monday after Thu 15 Jan is Mon 19 Jan.
        val next = Recurrence.Weekly(DayOfWeek.MONDAY.isoDayNumber).nextOccurrenceAfter(thursdayNoon)
        assertEquals(
            Instant.parse("2026-01-19T12:00:00Z"),
            next,
            "weekly(Monday) from Thu 15 Jan should be Mon 19 Jan — got $next",
        )
    }

    @Test
    fun weeklySeenFromItsOwnWeekdayStepsAWholeWeekRatherThanItself() {
        val mondayNoon = Instant.parse("2026-01-19T12:00:00Z")
        val next = Recurrence.Weekly(DayOfWeek.MONDAY.isoDayNumber).nextOccurrenceAfter(mondayNoon)
        assertEquals(
            Instant.parse("2026-01-26T12:00:00Z"),
            next,
            "weekly(Monday) seen from Monday noon should be the *next* Monday, not itself — got $next",
        )
    }

    @Test
    fun weeklyOnSundayFromSaturdayStepsOneDay() {
        val saturday = Instant.parse("2026-01-17T09:00:00Z")
        val next = Recurrence.Weekly(DayOfWeek.SUNDAY.isoDayNumber).nextOccurrenceAfter(saturday)
        assertEquals(
            Instant.parse("2026-01-18T09:00:00Z"),
            next,
            "weekly(Sunday) from Sat 17 Jan should be Sun 18 Jan — got $next",
        )
    }

    // ------------------------------------------------------------------
    // Monthly, and the February clamp
    // ------------------------------------------------------------------

    @Test
    fun monthlyFindsThisMonthsDayWhenItHasNotPassedYet() {
        val januaryTenth = Instant.parse("2026-01-10T08:00:00Z")
        val next = Recurrence.Monthly(15).nextOccurrenceAfter(januaryTenth)
        assertEquals(
            Instant.parse("2026-01-15T08:00:00Z"),
            next,
            "monthly(15) seen from 10 Jan should land on 15 Jan of the same month, not a month late — got $next",
        )
    }

    @Test
    fun monthlySeenFromItsOwnDayMovesToTheNextMonth() {
        val next = Recurrence.Monthly(15).nextOccurrenceAfter(Instant.parse("2026-01-15T08:00:00Z"))
        assertEquals(
            Instant.parse("2026-02-15T08:00:00Z"),
            next,
            "monthly(15) seen from 15 Jan should be 15 Feb, not itself — got $next",
        )
    }

    @Test
    fun monthlyOnThe31stClampsToTheLastDayOfFebruary() {
        val januaryLast = Instant.parse("2026-01-31T10:00:00Z")
        val next = Recurrence.Monthly(31).nextOccurrenceAfter(januaryLast)
        assertEquals(
            Instant.parse("2026-02-28T10:00:00Z"),
            next,
            "monthly(31) from 31 Jan 2026 (common year) should clamp to 28 Feb — got $next",
        )
    }

    @Test
    fun monthlyOnThe31stSeenFromFebruarysClampedDayMovesOnToMarchNotBackIntoFebruary() {
        // The double-fire trap: seen from the clamped 28 Feb, the rule must not
        // produce 28 Feb again (it is not strictly after itself) nor clamp into a
        // second February date — it must reach for the next month, which has a 31st.
        val februaryClamped = Instant.parse("2026-02-28T10:00:00Z")
        val next = Recurrence.Monthly(31).nextOccurrenceAfter(februaryClamped)
        assertEquals(
            Instant.parse("2026-03-31T10:00:00Z"),
            next,
            "monthly(31) seen from the clamped 28 Feb should be 31 Mar — got $next",
        )
    }

    @Test
    fun monthlyOnThe30thClampsToFebruarys28thNotThe31st() {
        val next = Recurrence.Monthly(30).nextOccurrenceAfter(Instant.parse("2026-01-30T10:00:00Z"))
        assertEquals(
            Instant.parse("2026-02-28T10:00:00Z"),
            next,
            "monthly(30) from 30 Jan should clamp to 28 Feb, not overshoot into March — got $next",
        )
    }

    // ------------------------------------------------------------------
    // Yearly
    // ------------------------------------------------------------------

    @Test
    fun yearlyFromAfterItsDayWaitsForNextYear() {
        val july = Instant.parse("2026-07-01T12:00:00Z")
        val next = Recurrence.Yearly(6, 15).nextOccurrenceAfter(july)
        assertEquals(
            Instant.parse("2027-06-15T12:00:00Z"),
            next,
            "yearly(15 June) from 1 Jul 2026 should be 15 Jun 2027 — got $next",
        )
    }

    @Test
    fun yearlyBeforeItsDayFiresThisYear() {
        val may = Instant.parse("2026-05-01T12:00:00Z")
        val next = Recurrence.Yearly(6, 15).nextOccurrenceAfter(may)
        assertEquals(
            Instant.parse("2026-06-15T12:00:00Z"),
            next,
            "yearly(15 June) from 1 May 2026 should be 15 Jun 2026, not a year late — got $next",
        )
    }

    @Test
    fun yearlyFebruary29thClampsToThe28thInACommonYear() {
        val january = Instant.parse("2027-01-01T12:00:00Z")
        val next = Recurrence.Yearly(2, 29).nextOccurrenceAfter(january)
        assertEquals(
            Instant.parse("2027-02-28T12:00:00Z"),
            next,
            "yearly(29 Feb) from Jan 2027 (common year) should clamp to 28 Feb 2027 — got $next",
        )
    }

    @Test
    fun yearlyFebruary29thFiresOnThe29thInALeapYear() {
        val january = Instant.parse("2028-01-01T12:00:00Z")
        val next = Recurrence.Yearly(2, 29).nextOccurrenceAfter(january)
        assertEquals(
            Instant.parse("2028-02-29T12:00:00Z"),
            next,
            "yearly(29 Feb) from Jan 2028 (leap year) should be 29 Feb, not clamped — got $next",
        )
    }

    // ------------------------------------------------------------------
    // until
    // ------------------------------------------------------------------

    @Test
    fun anOccurrenceLandingExactlyOnUntilStillHappens() {
        val until = Instant.parse("2026-01-16T12:00:00Z")
        val next = Recurrence.Daily(until = until).nextOccurrenceAfter(thursdayNoon)
        assertEquals(
            until,
            next,
            "until is inclusive: an occurrence exactly on it should be returned — got $next",
        )
    }

    @Test
    fun theFirstOccurrenceAfterUntilIsRefused() {
        val until = Instant.parse("2026-01-16T12:00:00Z")
        val next = Recurrence.Daily(until = until)
            .nextOccurrenceAfter(Instant.parse("2026-01-16T12:00:00Z"))
        assertNull(
            next,
            "daily(until = 16 Jan noon) seen from 16 Jan noon should end the series, " +
                "not produce 17 Jan — got $next",
        )
    }

    @Test
    fun untilInThePastRefusesEvenTheFirstOccurrence() {
        val until = Instant.parse("2026-01-01T00:00:00Z")
        val next = Recurrence.Daily(until = until).nextOccurrenceAfter(thursdayNoon)
        assertNull(next, "until before the start should end the series immediately — got $next")
    }

    // ------------------------------------------------------------------
    // Timezone sensitivity
    // ------------------------------------------------------------------

    @Test
    fun theWeekdayIsTheCallersWeekdayNotUtc() {
        // 22:00Z on Sunday 18 Jan is already Monday 19 Jan 07:00 in Tokyo (UTC+9).
        // Seen from Tokyo the series is *on* its own weekday and must step a full
        // week; seen from UTC it is still Sunday and must step to Monday.
        val instant = Instant.parse("2026-01-18T22:00:00Z")
        val tokyo = TimeZone.of("Asia/Tokyo")
        val weekly = Recurrence.Weekly(DayOfWeek.MONDAY.isoDayNumber)

        assertEquals(
            // Mon 26 Jan 07:00 JST is Sun 25 Jan 22:00 UTC — the conversion is the
            // assertion: a rule that ignored the zone would return Monday *UTC*.
            Instant.parse("2026-01-25T22:00:00Z"),
            weekly.nextOccurrenceAfter(instant, tokyo),
            "weekly(Monday) in Tokyo from Mon 19 Jan 07:00 should be Mon 26 Jan 07:00 JST — the zone must be honoured",
        )
        assertEquals(
            Instant.parse("2026-01-19T22:00:00Z"),
            weekly.nextOccurrenceAfter(instant, TimeZone.UTC),
            "weekly(Monday) in UTC from Sun 18 Jan 22:00 should be Mon 19 Jan — the zone must be honoured",
        )
    }

    // ------------------------------------------------------------------
    // Construction guards
    // ------------------------------------------------------------------

    @Test
    fun aWeekdayOutsideOneToSevenIsRefused() {
        assertFailsWith<IllegalArgumentException> { Recurrence.Weekly(0) }
        assertFailsWith<IllegalArgumentException> { Recurrence.Weekly(8) }
    }

    @Test
    fun aDayOfMonthOutsideOneToThirtyOneIsRefused() {
        assertFailsWith<IllegalArgumentException> { Recurrence.Monthly(0) }
        assertFailsWith<IllegalArgumentException> { Recurrence.Monthly(32) }
    }

    @Test
    fun aYearlyMonthOutsideOneToTwelveIsRefused() {
        assertFailsWith<IllegalArgumentException> { Recurrence.Yearly(0, 1) }
        assertFailsWith<IllegalArgumentException> { Recurrence.Yearly(13, 1) }
        assertFailsWith<IllegalArgumentException> { Recurrence.Yearly(1, 32) }
    }

    // ------------------------------------------------------------------
    // wireValue round-trip — the stored column is this string
    // ------------------------------------------------------------------

    @Test
    fun wireValuesRoundTripThroughParse() {
        // until-carrying samples are excluded on purpose: until has its own column and
        // is *not* in the wire string, so parse cannot restore it — that contract is
        // asserted by untilIsDeliberatelyNotPartOfTheWireString below.
        val samples = listOf(
            Recurrence.Daily(),
            Recurrence.Weekly(1),
            Recurrence.Weekly(7),
            Recurrence.Monthly(1),
            Recurrence.Monthly(31),
            Recurrence.Yearly(1, 1),
            Recurrence.Yearly(12, 31),
            Recurrence.Yearly(2, 29),
        )
        for (sample in samples) {
            assertEquals(
                sample,
                Recurrence.parseWireValue(sample.wireValue()),
                "parse(wireValue) should round-trip ${sample.wireValue()}",
            )
        }
    }

    @Test
    fun untilIsDeliberatelyNotPartOfTheWireString() {
        // until has its own column, so two items sharing "monthly:15" with different
        // end dates must share a wire value — the pair, not the string, is the value.
        val openEnded = Recurrence.Monthly(15)
        val bounded = Recurrence.Monthly(15, until = Instant.parse("2027-01-01T00:00:00Z"))
        assertEquals(openEnded.wireValue(), bounded.wireValue())
        assertNotEquals(openEnded.until, bounded.until)
    }

    @Test
    fun parseRefusesOutOfRangeNumbersThisBuildCannotRepresent() {
        assertNull(Recurrence.parseWireValue("weekly:0"))
        assertNull(Recurrence.parseWireValue("weekly:8"))
        assertNull(Recurrence.parseWireValue("monthly:32"))
        assertNull(Recurrence.parseWireValue("yearly:13-1"))
        assertNull(Recurrence.parseWireValue("yearly:1-32"))
        assertNull(Recurrence.parseWireValue("yearly:1"))
    }

    @Test
    fun parseRefusesStringsItDoesNotKnow() {
        assertNull(Recurrence.parseWireValue(""))
        assertNull(Recurrence.parseWireValue("fortnightly"))
        assertNull(Recurrence.parseWireValue("weekly"))
        assertNull(Recurrence.parseWireValue("daily:extra"))
        assertNull(Recurrence.parseWireValue("hourly:3"))
    }

    // ------------------------------------------------------------------
    // JSON — a server payload carries the sealed hierarchy
    // ------------------------------------------------------------------

    @Test
    fun aRecurrenceSurvivesAJsonRoundTrip() {
        val original: Recurrence = Recurrence.Weekly(3, until = Instant.parse("2027-06-01T00:00:00Z"))
        val encoded = JSONCoding.json.encodeToString(Recurrence.serializer(), original)
        val decoded = JSONCoding.json.decodeFromString(Recurrence.serializer(), encoded)
        assertEquals(original, decoded, "weekly(3) should survive JSON as itself — got $decoded from $encoded")
        assertTrue(encoded.contains("weekly"), "the sealed discriminator should name the case: $encoded")
    }

    @Test
    fun aWorkItemCarryingARecurrenceSurvivesAJsonRoundTrip() {
        val item = WorkItem(
            projectID = com.tasktracker.core.randomProjectID(),
            key = com.tasktracker.core.WorkItemKey("TT-1"),
            title = "Water the plants",
            dueDate = Instant.parse("2026-01-15T12:00:00Z"),
            recurrence = Recurrence.Weekly(DayOfWeek.FRIDAY.isoDayNumber),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val encoded = JSONCoding.json.encodeToString(WorkItem.serializer(), item)
        val decoded = JSONCoding.json.decodeFromString(WorkItem.serializer(), encoded)
        assertEquals(item.recurrence, decoded.recurrence, "WorkItem.recurrence should survive JSON")
        assertEquals(item.dueDate, decoded.dueDate)
    }

    @Test
    fun aWorkItemFromBeforeRecurrenceExistedDecodesWithNone() {
        // The additive-field contract: a payload with no recurrence key is a one-off,
        // exactly as every item created before this column existed migrates in.
        // `assigneeID` rather than the pre-0.2 `assignee`: JSONCoding is deliberately
        // strict, so a payload naming a key the entity does not have fails to decode —
        // this fixture describes the current wire shape minus one optional key.
        val encoded = """
            {"id":"0192f0c2-6a1e-7c3d-8e4f-0a1b2c3d4e5f",
             "projectID":"0192f0c2-6a1e-7c3d-8e4f-0a1b2c3d4e50","key":"TT-1",
             "title":"Old","details":"","status":"backlog","priority":"Medium","assigneeID":null,
             "dueDate":null,"storyPoints":null,"blockedByIDs":[],"sprintID":null,"rank":0,
             "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z","resolvedAt":null}
        """.trimIndent()
        val decoded = JSONCoding.json.decodeFromString(WorkItem.serializer(), encoded)
        assertNull(decoded.recurrence, "a payload with no recurrence key should decode as a one-off")
    }
}
