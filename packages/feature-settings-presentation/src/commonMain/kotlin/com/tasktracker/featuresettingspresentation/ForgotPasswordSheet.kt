package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.settings_forgot_password
import com.tasktracker.resources.settings_forgot_password_body
import com.tasktracker.resources.settings_forgot_password_have_code
import org.jetbrains.compose.resources.stringResource

/**
 * The forgot-password sheet: how to get a code, and the door into entering one.
 *
 * This sheet sends nothing and asks the server nothing, and that is the shape of the flow
 * rather than a missing piece. The project owns no mail infrastructure (AGENTS.md §7) — no
 * endpoint emails a member — so a code can only come from the admin who mints one in the
 * members screen, and what a member who forgot their password needs here is the instruction
 * plus a direct route to the field the code goes in. The code then travels out of band: admin
 * reads it off the dialog, tells the member, the member enters it as their credential on
 * [ChangePasswordSheet].
 *
 * Handing the code over *is* why this is not a request form: an endpoint that minted a code to
 * whoever asked — from inside their own signed-in session — would let a session that already
 * holds the account reset its password without the old one, which is precisely what requiring
 * the old password or an out-of-band code is there to prevent.
 */
@Composable
internal fun ForgotPasswordSheet(
    onHaveCode: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(ForgotPasswordSheetTag),
    ) {
        ttText(
            text = stringResource(Res.string.settings_forgot_password),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ttText(
            text = stringResource(Res.string.settings_forgot_password_body),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag(ForgotPasswordCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.settings_forgot_password_have_code),
                onClick = onHaveCode,
                modifier = Modifier.testTag(ForgotPasswordHaveCodeTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
            )
        }
    }
}

const val ForgotPasswordSheetTag = "settings.forgotPassword.sheet"
const val ForgotPasswordHaveCodeTag = "settings.forgotPassword.haveCode"
const val ForgotPasswordCancelTag = "settings.forgotPassword.cancel"
