package com.tasktracker.featureteamspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTGlassSurface
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassLensAlpha
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttMonospaced
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_members_reset_code_copy
import com.tasktracker.resources.admin_members_reset_code_for
import com.tasktracker.resources.admin_members_reset_code_title
import com.tasktracker.resources.common_done
import org.jetbrains.compose.resources.stringResource

/**
 * The minted password-reset code, drawn as the credential it is.
 *
 * ## Why this is not a [com.tasktracker.designsystem.ConfirmPopup]
 *
 * The delete confirmation is a question with two answers, which is what that popup is for. This
 * one has no question and one answer, and the thing the administrator came for is a *string* —
 * ending a paragraph with it, as a `ConfirmPopup` body has to, made the code look like a
 * sentence fragment and left the only way to copy it a button labelled "Copy" that also closed
 * the popup. A hand-off can go wrong: an admin who dismisses by reflex and then finds the
 * clipboard empty has to revoke and mint again.
 *
 * So the code has its own row — monospaced, on a glass field, with the copy icon beside it, the
 * same shape the agent pairing wizard uses for a pairing code — and copying no longer closes
 * anything. `Done` is the only dismissal, and it is a dismissal rather than a rollback: the code
 * exists on the server either way, so the wording does not pretend otherwise.
 *
 * The popup does not dismiss on a scrim tap, matching [com.tasktracker.designsystem.ConfirmPopup]:
 * a credential that vanishes because a finger landed 20dp off the card is a credential the admin
 * has to mint twice.
 */
@Composable
internal fun MembersResetCodePopup(
    generated: GeneratedResetCode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent, RoundedCornerShape(0.dp))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        TTGlassSurface(
            shape = RoundedCornerShape(ttRadius.surface),
            base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(ttSpacing.formStack)
                .testTag(MembersResetCodeTag),
        ) {
            // The card draws its own inset: `TTGlassSurface` is a container, not a padded
            // panel, so without this the heading and the Done button sit on the glass rim.
            Column(
                modifier = Modifier.padding(ttSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                ttText(
                    text = stringResource(Res.string.admin_members_reset_code_title),
                    style = ttTypography.titleSmall,
                    color = ttLabel(),
                )
                ttText(
                    // Who the code is for and how long it lives, in the members screen's own
                    // wording — the settings sheet shows the same credential and the two must
                    // not describe it two ways.
                    text = stringResource(
                        Res.string.admin_members_reset_code_for,
                        generated.member.preferredGreeting,
                    ),
                    style = ttTypography.callout,
                    color = ttLabelSecondary(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
                        .padding(start = ttSpacing.md, end = ttSpacing.xs, top = ttSpacing.xs, bottom = ttSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    ttText(
                        text = generated.code,
                        // Monospaced so the hyphens line up and an `l` cannot be read as a `1`
                        // when the admin is reading the code aloud instead of copying it.
                        style = ttTypography.footnote.ttMonospaced(),
                        color = ttLabel(),
                        modifier = Modifier.weight(1f).testTag(MembersResetCodeValueTag),
                    )
                    ttGlassIconButton(
                        image = ttActionIcons.copy,
                        contentDescription = stringResource(Res.string.admin_members_reset_code_copy),
                        onClick = { clipboard.setText(AnnotatedString(generated.code)) },
                        tint = ttAccent(),
                        modifier = Modifier.testTag(MembersResetCodeCopyTag),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
                ) {
                    ttGlassButton(
                        label = stringResource(Res.string.common_done),
                        onClick = onDismiss,
                        modifier = Modifier.testTag(MembersResetCodeDoneTag),
                    )
                }
            }
        }
    }
}

/** The popup itself, kept from the `ConfirmPopup` it replaced so existing selectors still land. */
const val MembersResetCodeTag = "adminMembers.resetCode"

/** The code's own field, so a scenario can read the credential rather than the sentence. */
const val MembersResetCodeValueTag = "adminMembers.resetCode.value"

/** The copy control beside the code. */
const val MembersResetCodeCopyTag = "adminMembers.resetCode.copy"

/** The only dismissal: copying leaves the popup open. */
const val MembersResetCodeDoneTag = "adminMembers.resetCode.done"
