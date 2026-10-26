package com.tasktracker.featureprojectspresentation

/**
 * Suggests a 2-character uppercase key prefix from [value] (e.g. "Task Tracker" -> "TT", "Tracker" -> "TR").
 * Uses the initials of the first two words, falling back to the first two alphanumeric characters of a single word.
 */
internal fun suggestKeyPrefix(value: String): String {
    val words = value.split(' ', '-', '_').filter(String::isNotBlank)
    val initials = words.take(2).mapNotNull { it.firstOrNull(Char::isLetterOrDigit) }
        .joinToString("").uppercase()
    if (initials.length >= 2) return initials
    return words.firstOrNull()?.filter(Char::isLetterOrDigit)?.take(2)?.uppercase().orEmpty()
}