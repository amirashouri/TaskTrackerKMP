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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
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
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_done
import com.tasktracker.resources.settings_change_password
import com.tasktracker.resources.settings_change_password_body
import com.tasktracker.resources.settings_change_password_confirm_hint
import com.tasktracker.resources.settings_change_password_credential_code_hint
import com.tasktracker.resources.settings_change_password_credential_old_hint
import com.tasktracker.resources.settings_change_password_mismatch
import com.tasktracker.resources.settings_change_password_new_hint
import com.tasktracker.resources.settings_change_password_success
import com.tasktracker.resources.settings_change_password_too_short
import com.tasktracker.resources.settings_change_password_verify_code
import com.tasktracker.resources.settings_change_password_verify_old
import org.jetbrains.compose.resources.stringResource

/**
 * Which credential the form is asking for.
 *
 * The two halves of the forgot-password contract, as one choice: identity is either the
 * password the member already had or the one-time code an admin just minted for them. The
 * server reads whichever arrives in `old_password_or_code` — this enum only decides which
 * field the sheet draws and what it tells the model the credential *is*.
 */
internal enum class VerifyMethod { OldPassword, AdminCode }

/**
 * The change-password sheet: verify with the old password or an admin's code, then set a new
 * one.
 *
 * Drafts live here and never reach the model — only a submitted (new password, credential)
 * pair does — so dismissing the sheet changes nothing, the same rule `UserNameSheet` states.
 * The [outcome] is the model's: `Started` disables the button while the request is in flight,
 * [ChangePasswordOutcome.Failure] renders the server's sentence inline (the `400` wording is
 * part of the API contract, so a policy refusal reads exactly what the server said), and
 * [ChangePasswordOutcome.Success] swaps the form for its confirmation, which is the
 * acknowledgment the sheet exists to give before it closes.
 *
 * Client-side validation mirrors the server's policy rather than replacing it: at least
 * [MinPasswordLength] characters, the confirmation matching, a non-empty credential. All three
 * are refused before the request so a typo never becomes a round trip — and the server still
 * enforces the same rules, because a client that could skip them would be the whole check.
 */
@Composable
internal fun ChangePasswordSheet(
    outcome: ChangePasswordOutcome?,
    startWithCode: Boolean,
    onSubmit: (newPassword: String, oldPasswordOrCode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var method by remember(startWithCode) {
        mutableStateOf(if (startWithCode) VerifyMethod.AdminCode else VerifyMethod.OldPassword)
    }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }

    val isSubmitting = outcome == ChangePasswordOutcome.Started
    val isSuccess = outcome == ChangePasswordOutcome.Success
    val failure = (outcome as? ChangePasswordOutcome.Failure)?.error

    // Dismissed from the confirmation, the Cancel button, a back gesture and a success alike —
    // the screen's handler closes the sheet *and* clears the outcome, so no stale banner can
    // greet the next open. One door, one exit.
    val dismiss: () -> Unit = { onDismiss() }

    val tooShort = newPassword.isNotEmpty() && newPassword.length < MinPasswordLength
    val mismatch = confirmation.isNotEmpty() && confirmation != newPassword
    val canSubmit = !isSuccess && !isSubmitting &&
        newPassword.length >= MinPasswordLength &&
        confirmation == newPassword &&
        credential.isNotEmpty()

    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = dismiss,
        modifier = modifier.testTag(ChangePasswordSheetTag),
    ) {
        if (isSuccess) {
            ttText(
                text = stringResource(Res.string.settings_change_password_success),
                style = ttTypography.title,
                color = ttLabel(),
                modifier = Modifier.testTag(ChangePasswordSuccessTag),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_done),
                    onClick = dismiss,
                    modifier = Modifier.testTag(ChangePasswordDoneTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                )
            }
            return@TTBottomSheetScaffold
        }

        ttText(
            text = stringResource(Res.string.settings_change_password),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ttText(
            text = stringResource(Res.string.settings_change_password_body),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )

        // The selector, not two radio rows: the choice is one tap either way, and the design
        // system's own segmented control is the shape the app already answers it with — the
        // members screen's bucket switch is the same control with the same job.
        TTGlassSegmentedPicker(
            options = VerifyMethod.entries,
            selection = method,
            onSelect = { next ->
                method = next
                // The credential field is being retyped as a different kind of thing: keeping
                // an old password in a code field (or the reverse) would submit a credential
                // the server reads as the wrong kind and refuse with a sentence that blames
                // the value rather than the swap.
                credential = ""
            },
            modifier = Modifier.fillMaxWidth().testTag(ChangePasswordVerifyTag),
            title = { option ->
                when (option) {
                    VerifyMethod.OldPassword -> stringResource(Res.string.settings_change_password_verify_old)
                    VerifyMethod.AdminCode -> stringResource(Res.string.settings_change_password_verify_code)
                }
            },
        )

        PasswordSheetField(
            value = credential,
            onValueChange = { credential = it },
            hint = stringResource(
                when (method) {
                    VerifyMethod.OldPassword -> Res.string.settings_change_password_credential_old_hint
                    VerifyMethod.AdminCode -> Res.string.settings_change_password_credential_code_hint
                },
            ),
            // The credential is masked whether it is a password or a code: both authorize the
            // change, so shoulder-surfing either one is the same win for whoever is looking.
            tag = ChangePasswordCredentialTag,
        )
        PasswordSheetField(
            value = newPassword,
            onValueChange = { newPassword = it },
            hint = stringResource(Res.string.settings_change_password_new_hint),
            masked = true,
            isLastField = false,
            tag = ChangePasswordNewTag,
        )
        PasswordSheetField(
            value = confirmation,
            onValueChange = { confirmation = it },
            hint = stringResource(Res.string.settings_change_password_confirm_hint),
            masked = true,
            isLastField = true,
            tag = ChangePasswordConfirmTag,
        )

        // The two refusals that are the user's own typing, said while they are still looking at
        // the field rather than after a round trip. Only shown once the field has something in
        // it — an empty confirmation is not a mismatch, it is a form that has not been filled.
        val localError = when {
            tooShort -> stringResource(Res.string.settings_change_password_too_short)
            mismatch -> stringResource(Res.string.settings_change_password_mismatch)
            else -> null
        }
        if (localError != null) {
            ttText(
                text = localError,
                style = ttTypography.footnote,
                color = ttError(),
                modifier = Modifier.testTag(ChangePasswordValidationErrorTag),
            )
        }

        // The server's refusal — wrong credential, spent or expired code, policy — in the
        // server's own words: `400` is in the mapping's surface-safe set for exactly this.
        if (failure != null) {
            ttText(
                text = failure.message.orEmpty(),
                style = ttTypography.footnote,
                color = ttError(),
                modifier = Modifier.testTag(ChangePasswordFailureTag),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = dismiss,
                modifier = Modifier.testTag(ChangePasswordCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.settings_change_password),
                onClick = { onSubmit(newPassword, credential) },
                modifier = Modifier.testTag(ChangePasswordSubmitTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                enabled = canSubmit,
            )
        }
    }
}

/**
 * One masked-or-not single-line field in the sheet's glass pill, with its hint drawn inside
 * while empty.
 *
 * The same construction `UserNameSheet` uses, factored out because this sheet needs three of
 * it — and a third inline copy of the pill geometry is how the pill ends up styled four ways.
 */
@Composable
private fun PasswordSheetField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    tag: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    isLastField: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = ttSpacing.sm)
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            textStyle = ttTypography.body.copy(color = ttLabel()),
            cursorBrush = SolidColor(ttAccent()),
            singleLine = true,
            maxLines = 1,
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (masked) KeyboardType.Password else KeyboardType.Text,
                imeAction = if (isLastField) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { /* The submit button is the only action. */ }),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.padding(
                        horizontal = ttSpacing.sm,
                        vertical = ttSpacing.xs,
                    ),
                ) {
                    if (value.isEmpty()) {
                        ttText(
                            text = hint,
                            style = ttTypography.body,
                            color = ttLabelSecondary(),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** The mirror of the server's policy (`internal/me`, 8 characters): one number on each side. */
private const val MinPasswordLength = 8

const val ChangePasswordSheetTag = "settings.changePassword.sheet"
const val ChangePasswordVerifyTag = "settings.changePassword.verify"
const val ChangePasswordCredentialTag = "settings.changePassword.credential"
const val ChangePasswordNewTag = "settings.changePassword.new"
const val ChangePasswordConfirmTag = "settings.changePassword.confirm"
const val ChangePasswordValidationErrorTag = "settings.changePassword.validation"
const val ChangePasswordFailureTag = "settings.changePassword.failure"
const val ChangePasswordSubmitTag = "settings.changePassword.submit"
const val ChangePasswordCancelTag = "settings.changePassword.cancel"
const val ChangePasswordSuccessTag = "settings.changePassword.success"
const val ChangePasswordDoneTag = "settings.changePassword.done"
