package com.tasktracker.userdefaults

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `WxH` encoding itself, without a store in the way.
 *
 * `WindowGeometryDefaultsTest` exercises [WindowGeometry.parse] through one unreadable value;
 * this pins the whole shape it accepts and every shape it refuses, because the KDoc promises
 * the refusal ("absent, empty, a hand-edited `wide`, a negative or zero side") and a promise
 * with no assertion behind it is the kind of coverage that is believed and is not there.
 */
class WindowGeometryTest {

    @Test
    fun readsBackWhatEncodeWrote() {
        assertEquals(
            WindowGeometry(width = 1280, height = 800),
            WindowGeometry.parse(WindowGeometry(width = 1280, height = 800).encode()),
            "the size the window writes is not the size the next launch reads",
        )
    }

    @Test
    fun onlyTwoPositiveSidesAreASize() {
        val notASize = listOf(
            null to "nothing stored yet",
            "" to "written empty",
            "wide" to "a person's word rather than a size",
            "1280" to "half a pair, with no separator",
            "1280x" to "a separator with nothing after it",
            "x800" to "a separator with nothing before it",
            "0x800" to "a zero-width window",
            "-1x800" to "a negative width",
            "1280x0" to "a zero-height window",
            "1280x-1" to "a negative height",
            "1280x800x600" to "three numbers, so the second side is not one",
        )
        notASize.forEach { (stored, why) ->
            assertNull(
                WindowGeometry.parse(stored),
                "\"$stored\" ($why) was read as a window size; only WxH with two positive " +
                    "sides may become one, and the entry point turns anything else into the " +
                    "default rather than opening a zero- or negative-sized frame",
            )
        }
    }
}
