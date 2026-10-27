package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.tasktracker.designsystem.ttTextField

const val SettingsStatusTextRowTag = "settings.statusText"

@Composable
internal fun StatusTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    /**
     * Called when the field is finished, not on every keystroke.
     *
     * The note is a free-text profile field, so a write per character would be one PATCH per
     * character and the same number of chances to race the next one. Done and focus loss are
     * the two moments the user is saying "this is the value".
     */
    onCommit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var hadFocus by remember { mutableStateOf(false) }
    // No label: the hint inside the field already says what this is,
    // and a label beside it just squeezed the field into half the row.
    ttTextField(
        value = value,
        onValueChange = { next ->
            // The server's limit is 280 runes; cutting on a code-point boundary is what
            // keeps a pasted note under it without leaving half an emoji in the field.
            onValueChange(next.capToCodePoints(MaxStatusTextLength))
        },
        hint = "E.g. on vacation, sick — why are you away?",
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        // Commit on the transition out, not on every focus event: `onFocusChanged` also
        // fires when the screen is first laid out, and committing there would write a value
        // the user has not touched.
        onFocusChanged = { isFocused ->
            if (hadFocus && !isFocused) onCommit()
            hadFocus = isFocused
        },
    )
}

/** The server's `presence_note` limit, shared with the field so an overrun fails locally. */
private const val MaxStatusTextLength = 280
