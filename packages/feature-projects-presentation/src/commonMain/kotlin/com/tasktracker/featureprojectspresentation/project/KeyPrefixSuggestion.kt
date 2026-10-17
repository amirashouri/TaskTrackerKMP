package com.tasktracker.featureprojectspresentation

/**
 * The key prefix to offer for a project named [value], as the create form fills one in while the
 * name is typed.
 *
 * The initials of the first two words, falling back to the first two characters of a one-word
 * name: "Task Tracker" suggests `TT`, "Tracker" suggests `TR`. The prefix is the root of every
 * work item key the project will ever issue, so it is *offered* from the name and then left
 * editable rather than derived at save time.
 *
 * Shared by the board's editor and the admin create pane, because two forms that fill the same
 * field from the same name have to agree on the rule: a project named once in each would
 * otherwise issue differently-prefixed keys.
 */
internal fun suggestKeyPrefix(value: String): String {
    val words = value.split(' ', '-', '_').filter(String::isNotBlank)
    val initials = words.take(2).mapNotNull { it.firstOrNull(Char::isLetterOrDigit) }
        .joinToString("").uppercase()
    if (initials.length >= 2) return initials
    return words.firstOrNull()?.filter(Char::isLetterOrDigit)?.take(2)?.uppercase().orEmpty()
}
