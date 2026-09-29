package com.tasktracker.core

import kotlin.time.Instant

/**
 * TaskTracker's wire format is ISO-8601 UTC at **whole-second** resolution:
 *
 * ```
 * 2026-09-26T20:13:32Z
 * ```
 *
 * Second resolution is deliberate. A finer format would mean every comparison after
 * a decode is subtly wrong: a value of `…000.123` cannot be encoded and read back
 * without loss, and a "which row is newer" check run against a lossy operand is
 * arbitrary. Milliseconds buy nothing for task management, so the format is fixed at
 * one second and parsing is lenient enough to accept a server that sends fractional
 * seconds anyway.
 */
object DateCoding {

    /** The canonical representation used in every TaskTracker payload. */
    fun format(value: Instant): String = value.roundedToSecond().toString()

    /**
     * Parses the canonical representation, tolerating fractional seconds.
     *
     * `Instant.parse` accepts fractional seconds, `+00:00` in place of `Z`, and
     * `-00:00`; all three are legitimate ISO-8601 and a real backend is not
     * forced to match our choice of zone designator.
     *
     * Returns `null` rather than throwing: this is called on server payloads, and a
     * malformed date is an [AppError.Decoding] at the layer above, not an exception
     * raised out of a formatting helper.
     */
    fun parse(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

    /**
     * Parses a JWT `exp` claim, which servers send as a JSON number *or* a JSON
     * string depending on the implementation. Accepting only one is how a refresh
     * loop starts silently never firing.
     *
     * Returns `null` for anything else rather than defaulting to "never expires" —
     * a token whose expiry cannot be read must be treated as expired, which is the
     * caller's decision to make.
     */
    fun parseEpochSeconds(raw: String): Instant? {
        val seconds = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong() ?: return null
        return Instant.fromEpochSeconds(seconds)
    }
}

/**
 * Drops sub-second precision so a value stays a faithful inverse of the wire
 * format. Applied on the way *in* at the data layer, never in the middle of a
 * comparison, where a rounded operand would quietly decide the wrong winner.
 *
 * Rebuilding from [Instant.epochSeconds] rather than `truncatedTo(SECONDS)` because
 * `truncatedTo` takes a `DateTimeUnit` while `kotlin.time`'s `seconds` is a
 * `Duration`; going through epoch seconds is one unambiguous conversion.
 */
fun Instant.roundedToSecond(): Instant = Instant.fromEpochSeconds(epochSeconds)
