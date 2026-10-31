package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * How long a search box is allowed to be mid-word before the list reacts to it.
 *
 * 250ms, which is the member picker's value. One constant for both rather than two that happen
 * to agree: two 250s is how two searches drift apart, and the drift is invisible because each
 * screen is only ever compared against itself. It is also short enough not to read as lag and
 * long enough that typing a word does not flash a filtered list on every keystroke.
 */
internal const val SEARCH_DEBOUNCE_MILLIS = 250L

/**
 * The query a list should be filtered by: [query], once it has been still for [debounceMillis].
 *
 * ## Why the field is not debounced, only the list
 *
 * The field shows what was typed *now*; the list settles. Debouncing the value the field draws
 * would make the caret lag behind the fingers, which is the one thing a text input must never
 * do — so the caller keeps two values, and this returns the second one.
 *
 * ## Clearing is immediate
 *
 * A blank value applies on the next frame with no wait. The clear cross is a *command* rather
 * than typing: the user has said "show everything", and making them watch an empty list for a
 * quarter of a second reads as the cross not having worked.
 *
 * ## Cancellation
 *
 * The effect is keyed on [query], so a second keystroke inside the window cancels the pending
 * apply and only the last value is ever applied. Leaving the destination cancels it too, because
 * a `LaunchedEffect` dies with its composition — nothing outlives the screen that started it.
 */
@Composable
internal fun rememberDebouncedQuery(
    query: String,
    debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
): String {
    val trimmed = query.trim()
    var applied by remember { mutableStateOf(trimmed) }

    LaunchedEffect(trimmed, debounceMillis) {
        if (trimmed.isEmpty()) {
            applied = ""
            return@LaunchedEffect
        }
        delay(debounceMillis)
        applied = trimmed
    }

    return applied
}
