package com.tasktracker.featuresettingspresentation

/**
 * Cuts the string to at most [max] code points, without splitting a surrogate pair.
 *
 * The server limits both free-text profile fields by *runes*, not UTF-16 units, and Kotlin's
 * `String` counts units. A field that capped with `take(max)` would therefore be stricter than
 * the server for every character outside the BMP — and, because a surrogate pair is two units,
 * it could cut an emoji in half and send a lone surrogate, which is not text at all.
 *
 * The loop is only entered when the string is longer than the limit in units, which implies it
 * has more than [max] code points; the fast path is the common case.
 */
internal fun String.capToCodePoints(max: Int): String {
    if (length <= max) return this

    var index = 0
    var codePoints = 0
    while (index < length && codePoints < max) {
        val high = this[index]
        val isPair = high.isHighSurrogate() &&
            index + 1 < length &&
            this[index + 1].isLowSurrogate()
        index += if (isPair) 2 else 1
        codePoints += 1
    }
    return substring(0, index)
}
