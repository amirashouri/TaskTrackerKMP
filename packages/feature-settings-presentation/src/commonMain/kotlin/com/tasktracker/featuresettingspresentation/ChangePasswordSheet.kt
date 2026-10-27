package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.tasktracker.designsystem.LocalSheetPresentation
import com.tasktracker.designsystem.SheetPresentation
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTextField
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
import com.tasktracker.resources.settings_change_password_verify_generate
import com.tasktracker.resources.settings_change_password_verify_old
import org.jetbrains.compose.resources.stringResource

/**
 * Which credential the form is asking for — or, for an administrator, which end of the exchange
 * it is offering.
 *
 * The two halves of the forgot-password contract, as one choice: identity is either the
 * password the member already had or the one-time code an admin just minted for them. The
 * server reads whichever arrives in `old_password_or_code` — this enum only decides which
 * field the sheet draws and what it tells the model the credential *is*.
 *
 * ## [GenerateCode] is the third entry, and it is the administrator's side of the same segment
 *
 * An administrator is signed in, so they know their own password and have nothing to redeem: a
 * code they are handed is not a credential they can use. What they can do is produce one, so
 * for them the "admin code" segment becomes the generator — see [visibleTo]. Keeping it as an
 * enum entry rather than a role branch inside the `AdminCode` arm is what makes each of the
 * three arms single-purpose, and what stops the `when` that draws them from nesting a second
 * question inside the first.
 */
internal enum class VerifyMethod {
    OldPassword,
    AdminCode,
    GenerateCode,
    ;

    /**
     * Whether this segment belongs to the person signing in.
     *
     * A member sees [OldPassword] and [AdminCode]; an administrator sees [OldPassword] and
     * [GenerateCode]. [AdminCode] is deliberately not offered to an administrator: the field it
     * draws would be a credential they cannot obtain for themselves, and showing a form nobody
     * can complete is the "drawn but unusable" shape `SettingsScreen.isAdmin` refuses for the
     * Admin section.
     */
    fun visibleTo(isAdmin: Boolean): Boolean = when (this) {
        OldPassword -> true
        AdminCode -> !isAdmin
        GenerateCode -> isAdmin
    }
}

/**
 * The change-password sheet: verify with the old password or an admin's code, then set a new
 * one — or, for an administrator, mint the code a member will use.
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
 *
 * ## The administrator's segment is a different form, not a disabled one
 *
 * [isAdmin] selects between the two bodies the third segment can draw: the credential field a
 * member fills in, and the generator an administrator uses. The submit row belongs to the
 * former only — the generator's own button is the only action it has — which is why the row is
 * drawn inside the entry branch rather than under everything.
 *
 * ## A code the administrator minted is the delivery
 *
 * There is no mailer and none may be added for this flow (AGENTS.md §7), so the Copy button
 * beside the minted code is the whole hand-off. That is also why the code is shown rather than
 * sent: the administrator reads it out to whoever asked.
 */
@Composable
internal fun ChangePasswordSheet(
    outcome: ChangePasswordOutcome?,
    startWithCode: Boolean,
    onSubmit: (newPassword: String, oldPasswordOrCode: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    resetCode: ResetCodePanel = ResetCodePanel(),
    onLoadResetMembers: () -> Unit = {},
    onSelectResetMember: (ResetCodeCandidate) -> Unit = {},
    onGenerateResetCode: () -> Unit = {},
) {
    val methods = remember(isAdmin) { VerifyMethod.entries.filter { it.visibleTo(isAdmin) } }
    var method by remember(startWithCode, isAdmin) {
        // `startWithCode` is the forgot sheet's "I have a code". It asks for the first segment
        // that is not the old password — `AdminCode` for a member, and the generator for an
        // administrator, who cannot redeem a code but can produce one for the person who
        // asked. Falling back rather than asserting, because the selected segment must always
        // be one the picker is showing.
        mutableStateOf(
            if (startWithCode) {
                methods.firstOrNull { it != VerifyMethod.OldPassword } ?: VerifyMethod.OldPassword
            } else {
                VerifyMethod.OldPassword
            },
        )
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

    // Success is answered as a centred card, not as the sheet the form occupies.
    //
    // See [ChangePasswordSuccessCard] for why, and for why the form is not composed behind it.
    if (isSuccess) {
        ChangePasswordSuccessCard(onDone = dismiss, modifier = modifier)
        return
    }

    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = dismiss,
        modifier = modifier.testTag(ChangePasswordSheetTag),
    ) {
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
            options = methods,
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
                    VerifyMethod.GenerateCode -> stringResource(Res.string.settings_change_password_verify_generate)
                }
            },
        )

        // The third segment has two bodies and no third one: an administrator mints a code, a
        // member spends one. Everything below this point belongs to the member's form only.
        if (method == VerifyMethod.GenerateCode) {
            ResetCodeGenerator(
                panel = resetCode,
                onLoadMembers = onLoadResetMembers,
                onSelectMember = onSelectResetMember,
                onGenerate = onGenerateResetCode,
            )
        } else {
            ttTextField(
                value = credential,
                onValueChange = { credential = it },
                hint = stringResource(
                    when (method) {
                        VerifyMethod.OldPassword -> Res.string.settings_change_password_credential_old_hint
                        else -> Res.string.settings_change_password_credential_code_hint
                    },
                ),
                // The credential is masked whether it is a password or a code: both authorize the
                // change, so shoulder-surfing either one is the same win for whoever is looking.
                // It was not, in fact, masked before this — the comment said so and the call never
                // passed `masked`, which the shared field made visible as a missing argument.
                fieldModifier = Modifier.testTag(ChangePasswordCredentialTag),
                masked = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )
            ttTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                hint = stringResource(Res.string.settings_change_password_new_hint),
                fieldModifier = Modifier.testTag(ChangePasswordNewTag),
                masked = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )
            ttTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                hint = stringResource(Res.string.settings_change_password_confirm_hint),
                fieldModifier = Modifier.testTag(ChangePasswordConfirmTag),
                masked = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                // The submit button is the only action.
                keyboardActions = KeyboardActions(onDone = {}),
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
}

/**
 * The acknowledgement a completed change gets: one sentence and a Done, centred on the page.
 *
 * ## Why this is not the sheet the form was in
 *
 * A bottom sheet is the phone vocabulary for a *form* — thumb-reachable, and the surface system
 * back already dismisses. A success is not a form. Anchored to the bottom edge it read as one
 * more row of the sheet the user had just filled in rather than as the answer to whether the
 * password changed, which is the one thing this state exists to say.
 *
 * So it takes this component's other presentation, `Window`: the same surface, opaque base,
 * scrim and outside-tap dismissal, centred instead of bottom-anchored. That presentation is
 * provided here rather than passed because `TTBottomSheetScaffold` reads it from
 * [LocalSheetPresentation], and the shell's default is the phone one at every phone width — this
 * is the one surface in the app that is a confirmation rather than a sheet, so it is the one
 * place that answers that question differently.
 *
 * The form is not composed underneath while this is showing, and that is deliberate: the success
 * state cannot be returned to, so leaving three text fields alive behind the scrim would keep a
 * draft the user can no longer see, edit, or submit.
 */
@Composable
private fun ChangePasswordSuccessCard(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalSheetPresentation provides SheetPresentation.Window) {
        TTBottomSheetScaffold(
            visible = true,
            onDismissRequest = onDone,
            modifier = modifier.testTag(ChangePasswordSheetTag),
        ) {
            // Centred, because this card holds one sentence and nothing else: left-aligned it
            // read as the first row of a form that had lost its fields.
            ttText(
                text = stringResource(Res.string.settings_change_password_success),
                style = ttTypography.title,
                color = ttLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(ChangePasswordSuccessTag),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_done),
                    onClick = onDone,
                    modifier = Modifier.testTag(ChangePasswordDoneTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                )
            }
        }
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
