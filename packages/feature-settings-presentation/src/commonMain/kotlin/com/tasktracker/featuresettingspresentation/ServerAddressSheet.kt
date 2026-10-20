package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.ServerAddressValidation
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.settings_server_body
import com.tasktracker.resources.settings_server_error_blank
import com.tasktracker.resources.settings_server_error_cleartext_not_permitted
import com.tasktracker.resources.settings_server_error_malformed
import com.tasktracker.resources.settings_server_error_missing_host
import com.tasktracker.resources.settings_server_error_missing_scheme
import com.tasktracker.resources.settings_server_error_unreachable
import com.tasktracker.resources.settings_server_error_unsupported_scheme
import com.tasktracker.resources.settings_server_hint
import com.tasktracker.resources.settings_server_reset
import com.tasktracker.resources.settings_server_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Server editor: one address field, the reason it was refused, and the three ways out.
 *
 * ## The rules are not here
 *
 * The field renders [draft] and reports every keystroke; it does no parsing and never decides
 * whether an address is good. That is 6.3's `CheckServerAddress`, reached through the port, so
 * the one address table in the codebase is the one the field obeys. All this composable owns is
 * mapping the port's typed [ServerAddressValidation.Reason] to a sentence.
 *
 * ## Reset is offered only when there is something to reset
 *
 * [canReset] comes from [ServerAddressSettings.isCustom] rather than from the sheet looking at
 * the field, because whether an override exists is the model's knowledge. A Reset that cleared
 * a default the user never set would be a control that does nothing and says it did.
 *
 * ## Save is disabled only for an empty field; a check shows a spinner instead
 *
 * The seconds the probe can take are why the button takes a prominent style and `loading`: a
 * control that looks inert while it waits invites the second tap that would start a second
 * probe. An empty field is refused before the check so the first thing a user sees is not
 * "not a web address" for a field they have not typed in yet. The same wait disables Reset and
 * Cancel, because neither can cancel the dispatched check and either would let the sheet close
 * over a save that still lands.
 */
@Composable
fun ServerAddressSheet(
    draft: String,
    isChecking: Boolean,
    failure: ServerAddressValidation.Invalid?,
    canReset: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(ServerAddressSheetTag),
    ) {
        ttText(
            text = stringResource(Res.string.settings_server_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ttText(
            text = stringResource(Res.string.settings_server_body),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = ttSpacing.sm)
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row)),
            // Centred for the reason `StatusTextEditor` gives: the pill is taller than one
            // line of body text, and a top-aligned field rides high inside it.
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ServerAddressFieldTag),
                enabled = !isChecking,
                textStyle = ttTypography.body.copy(color = ttLabel()),
                cursorBrush = SolidColor(ttAccent()),
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                // Guarded, because Done while a check is in flight would start a second one
                // over the editor the first one is about to close.
                keyboardActions = KeyboardActions(onDone = { if (!isChecking) onSave() }),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.padding(
                            horizontal = ttSpacing.sm,
                            vertical = ttSpacing.xs,
                        ),
                    ) {
                        if (draft.isEmpty()) {
                            ttText(
                                text = stringResource(Res.string.settings_server_hint),
                                style = ttTypography.body,
                                color = ttLabelSecondary(),
                            )
                        }
                        inner()
                    }
                },
            )
        }
        failure?.let { invalid ->
            ttText(
                text = serverAddressFailureLabel(invalid.reason),
                style = ttTypography.footnote,
                color = ttError(),
                modifier = Modifier.testTag(ServerAddressErrorTag),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            if (canReset) {
                ttGlassButton(
                    label = stringResource(Res.string.settings_server_reset),
                    onClick = onReset,
                    modifier = Modifier.testTag(ServerAddressResetTag),
                    // A reset dispatched during a check would race the save that check is
                    // about to write; whichever finished last would win, and the notice could
                    // name an action the user did not end on. Waiting the check out is the only
                    // coherent choice, so the two exits are dimmed rather than left live.
                    enabled = !isChecking,
                )
            }
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag(ServerAddressCancelTag),
                // Dismissing the sheet does not cancel the check — its coroutine belongs to the
                // shell scope, not the sheet's — so Cancel here would hide a save that then
                // lands and raises a notice over a closed editor.
                enabled = !isChecking,
            )
            ttGlassButton(
                label = stringResource(Res.string.common_save),
                onClick = onSave,
                modifier = Modifier.testTag(ServerAddressSaveTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                enabled = draft.isNotBlank(),
                loading = isChecking,
            )
        }
    }
}

/**
 * One refusal reason, as the sentence the field shows under itself.
 *
 * Total with no `else`, for the reason every `when` in this module is: a reason added to
 * [ServerAddressValidation.Reason] has to become a build error here rather than a field that
 * shows nothing for the one mistake somebody actually made.
 */
@Composable
private fun serverAddressFailureLabel(reason: ServerAddressValidation.Reason): String =
    stringResource(
        when (reason) {
            ServerAddressValidation.Reason.Blank -> Res.string.settings_server_error_blank
            ServerAddressValidation.Reason.MissingScheme ->
                Res.string.settings_server_error_missing_scheme
            ServerAddressValidation.Reason.UnsupportedScheme ->
                Res.string.settings_server_error_unsupported_scheme
            ServerAddressValidation.Reason.MissingHost ->
                Res.string.settings_server_error_missing_host
            ServerAddressValidation.Reason.CleartextNotPermitted ->
                Res.string.settings_server_error_cleartext_not_permitted
            ServerAddressValidation.Reason.Malformed ->
                Res.string.settings_server_error_malformed
            ServerAddressValidation.Reason.Unreachable ->
                Res.string.settings_server_error_unreachable
        },
    )

const val ServerAddressSheetTag = "settings.server.sheet"
const val ServerAddressFieldTag = "settings.server.field"
const val ServerAddressErrorTag = "settings.server.error"
const val ServerAddressSaveTag = "settings.server.save"
const val ServerAddressResetTag = "settings.server.reset"
const val ServerAddressCancelTag = "settings.server.cancel"
