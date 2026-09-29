package com.tasktracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLengthTest {

    @Test
    fun emptyStringHasNoClusters() {
        assertEquals(0, graphemeClusterCount(""))
    }

    @Test
    fun asciiCountsAsItself() {
        assertEquals(5, graphemeClusterCount("hello"))
    }

    @Test
    fun bmpScriptsCountOnePerCharacter() {
        // Accented Latin, Cyrillic, CJK and Hangul are each one cluster.
        assertEquals(4, graphemeClusterCount("cafe\u0301"))
        assertEquals(6, graphemeClusterCount("\u043F\u0440\u043E\u0435\u043A\u0442"))
        assertEquals(3, graphemeClusterCount("\u30BF\u30B9\u30AF"))
        assertEquals(2, graphemeClusterCount("\uC791\uC5C5"))
    }

    @Test
    fun combiningMarksDoNotCreateNewClusters() {
        // Spelled with escapes on purpose: an editor that normalises the source
        // to NFC would silently turn this into two precomposed characters and
        // make the assertion vacuous. U+0065 is "e", U+0301 the combining acute.
        val decomposed = "e\u0301"
        assertEquals(1, graphemeClusterCount(decomposed))
        assertEquals(2, decomposed.length)
    }

    @Test
    fun zeroWidthJoinerSequenceIsOneCluster() {
        // Family emoji: 3 people joined by 2 ZWJs. UTF-16 length is 8.
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(1, graphemeClusterCount(family))
        assertEquals(8, family.length)
    }

    @Test
    fun flagEmojiPairIsOneCluster() {
        // Regional indicators pair up, so a flag is one cluster.
        val flag = "\uD83C\uDDEC\uD83C\uDDE7"
        assertEquals(1, graphemeClusterCount(flag))
        assertEquals(4, flag.length)
    }

    @Test
    fun emojiWithSkinToneModifierIsOneCluster() {
        val thumbs = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(1, graphemeClusterCount(thumbs))
    }

    @Test
    fun separateEmojiStillCountSeparately() {
        assertEquals(3, graphemeClusterCount("\uD83D\uDC4D\uD83C\uDF89\u2705"))
    }

    /**
     * The reason this function exists: a length guard written against
     * `String.length` would reject text the original accepted.
     */
    @Test
    fun maxLengthGuardsMeasureUserPerceivedCharacters() {
        val maxTitleLength = 40
        val title = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67".repeat(20)

        assertEquals(20, graphemeClusterCount(title))
        assertTrue(
            graphemeClusterCount(title) <= maxTitleLength,
            "20 family emoji should fit a 40 character title",
        )
        assertTrue(
            title.length > maxTitleLength,
            "UTF-16 length would wrongly reject this title: ${title.length}",
        )
    }
}
