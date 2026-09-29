package com.tasktracker.core

import kotlin.jvm.JvmName

/**
 * Counts user-perceived characters: extended grapheme clusters.
 *
 * This exists because the original Swift code relied on `String.count`, which
 * counts grapheme clusters, while Kotlin's `String.length` counts UTF-16 code
 * units. For an emoji family those disagree by a factor of eight, so every
 * `maxNameLength` / `maxTitleLength` / `maxDetailsLength` guard would silently
 * weaken without this. `"👨‍👩‍👧"` is 1 here and 8 for `.length`.
 *
 * This is a UAX #29 subset covering what user-entered text actually contains:
 * combining marks, variation selectors, emoji skin-tone modifiers, zero-width
 * joiner sequences and regional-indicator pairs. It is exact for ASCII, all
 * BMP scripts (Latin with diacritics, Cyrillic, Greek, CJK, Hangul, Arabic,
 * Hebrew, Devanagari), and for emoji including ZWJ families, flags and
 * skin tones.
 *
 * It is deliberately a single common implementation rather than an
 * `expect`/`actual` pair. An iOS-only implementation delegating to
 * `NSString.rangeOfComposedCharacterSequenceAtIndex` was tried first and
 * rejected: it needs `CValue` struct plumbing and opt-ins for a behaviour this
 * already reproduces, and two implementations of one rule is one more thing to
 * keep in agreement. Text that mixes scripts inside a single cluster is the one
 * case where a full ICU segmentation would differ, and it does not arise for
 * project names, board titles or task details.
 */
@JvmName("graphemeClusterCount")
fun graphemeClusterCount(text: String): Int {
    if (text.isEmpty()) return 0

    var clusters = 0
    var index = 0
    // Regional indicators pair up, so whether the next code point joins the
    // current cluster depends on the current one, not the previous cluster.
    var clusterIsRegionalIndicator = false

    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        index += codePoint.charCount()
        clusters++

        clusterIsRegionalIndicator = codePoint.isRegionalIndicator()
        // A third regional indicator would have started a new cluster, so a pair
        // is only consumed while exactly two are in play.
        var regionalPairComplete = false

        while (index < text.length) {
            val next = text.codePointAt(index)

            when {
                next == ZERO_WIDTH_JOINER -> {
                    index += ZERO_WIDTH_JOINER.charCount()
                    if (index < text.length) {
                        index += text.codePointAt(index).charCount()
                        // Emoji modifiers may trail the joined pair.
                        while (index < text.length && text.codePointAt(index).isExtending()) {
                            index += text.codePointAt(index).charCount()
                        }
                    }
                    clusterIsRegionalIndicator = false
                }

                clusterIsRegionalIndicator && !regionalPairComplete && next.isRegionalIndicator() -> {
                    index += next.charCount()
                    regionalPairComplete = true
                    clusterIsRegionalIndicator = false
                }

                next.isExtending() -> index += next.charCount()

                else -> break
            }
        }
    }

    return clusters
}

private const val ZERO_WIDTH_JOINER = 0x200D

private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private fun Int.charCount(): Int = if (this > 0xFFFF) 2 else 1

private fun getType(codePoint: Int): CharCategory =
    if (codePoint <= 0xFFFF) {
        codePoint.toChar().category
    } else {
        // Supplementary-plane code points are all emoji, variation selectors or
        // unassigned; none of them are combining marks, so the BMP table above
        // does not apply here.
        CharCategory.PRIVATE_USE
    }

private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
        }
    }
    return high.code
}

/** Combining marks, variation selectors and emoji skin-tone modifiers. */
private fun Int.isExtending(): Boolean = when (val type = getType(this)) {
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
    -> true
    else ->
        this in 0xFE00..0xFE0F || // variation selector-1..16
            this in 0xE0100..0xE01EF || // variation selector-17..256
            this in 0x1F3FB..0x1F3FF // emoji modifiers, skin tones
}
