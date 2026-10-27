package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTextField
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.settings_name_body
import com.tasktracker.resources.settings_name_hint
import com.tasktracker.resources.settings_name_title
import org.jetbrains.compose.resources.stringResource

/**
 * The display-name editor, in the same sheet the Server field uses.
 *
 * A separate sheet rather than an inline field, because the identity header is centred and a
 * text field there would either grow the header on every keystroke or clip the name it is
 * editing. The draft lives here and only reaches the model on Save, so a user who changes
 * their mind and dismisses the sheet has changed nothing.
 *
 * The field is capped at the server's limit and the Save button is disabled while the trimmed
 * value is blank. Both are enforced on the server too; doing it here keeps a mistake local
 * instead of turning it into a `400` the banner would have to explain.
 */
@Composable
internal fun UserNameSheet(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val trimmed = draft.trim()
    // Blank would make the header fall back to the email; unchanged is a write that cannot
    // change anything. Both are refused here rather than sent and confirmed, so Save always
    // means "this value is different and I want it".
    val canSave = trimmed.isNotEmpty() && trimmed != initial.trim()

    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(UserNameSheetTag),
    ) {
        ttText(
            text = stringResource(Res.string.settings_name_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ttText(
            text = stringResource(Res.string.settings_name_body),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
        ttTextField(
            value = draft,
            onValueChange = { next ->
                // The server's limit is 120 runes; cutting on a code-point boundary keeps
                // a pasted name from being refused (or an emoji from being split).
                draft = next.capToCodePoints(MaxDisplayNameLength)
            },
            hint = stringResource(Res.string.settings_name_hint),
            fieldModifier = Modifier.testTag(UserNameFieldTag),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(trimmed) }),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag(UserNameCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.common_save),
                onClick = { onSave(trimmed) },
                modifier = Modifier.testTag(UserNameSaveTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                enabled = canSave,
            )
        }
    }
}

/** The server's limit, shared with the field so a long name fails locally. */
private const val MaxDisplayNameLength = 120

const val UserNameSheetTag = "settings.name.sheet"
const val UserNameFieldTag = "settings.name.field"
const val UserNameSaveTag = "settings.name.save"
const val UserNameCancelTag = "settings.name.cancel"
