package com.tasktracker.featuresettingspresentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The free-text limits are counted the way the server counts them.
 *
 * `String.length` is UTF-16 units, so a naive `take(280)` admits 280 units — under the server's
 * 280 runes for ASCII, over it never, but able to cut a surrogate pair in half. A half pair is
 * not a character, and Kotlin will happily hold one, which is why this is measured rather than
 * assumed.
 */
class TextLimitsTest {

    @Test
    fun aStringUnderTheLimitIsReturnedUnchanged() {
        val value = "On vacation"
        assertEquals(value, value.capToCodePoints(280))
    }

    @Test
    fun anOverlongStringIsCutToTheLimit() {
        assertEquals(280, "x".repeat(300).capToCodePoints(280).length)
    }

    @Test
    fun aSurrogatePairIsNeverSplitAtTheBoundary() {
        // 300 emoji = 300 code points, 600 UTF-16 units. A unit-based `take(280)` would leave
        // 280 units — 140 whole emoji — and a `take(281)` would leave a lone high surrogate.
        val capped = "😀".repeat(300).capToCodePoints(280)

        assertEquals(560, capped.length, "280 code points, two units each")
        assertTrue(capped.last().isLowSurrogate(), "the last character must be a complete pair")
        assertEquals("😀".repeat(280), capped)
    }

    @Test
    fun aLimitThatFallsBetweenPairsKeepsWholeCharacters() {
        // The boundary lands exactly after a pair, so the cut is clean; the count is what the
        // server measures, not the unit length.
        val capped = "a😀".repeat(200).capToCodePoints(3)
        assertEquals("a😀a", capped)
    }
}
