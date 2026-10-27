package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSpinner
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttToast
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_members_reset_code_body
import com.tasktracker.resources.admin_members_reset_code_copy
import com.tasktracker.resources.copied_to_clipboard
import com.tasktracker.resources.settings_change_password_generate
import com.tasktracker.resources.settings_change_password_member_hint
import com.tasktracker.resources.settings_change_password_members_empty
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The member picker's popup width. Wider than the theme dropdown: names, not one word. */
internal val MemberDropdownWidth = 260.dp

/** The popup's height cap, so a long directory scrolls instead of running off the window. */
private val MemberDropdownMaxHeight = 280.dp

/** How long the copy confirmation stays up — the same two seconds the work-item toast uses. */
private const val CopiedToastMillis = 2_000L

/**
 * The administrator's half of the change-password sheet: pick a member, mint a code, copy it.
 *
 * ## Why this replaces the credential field rather than sitting beside it
 *
 * The segment is the same one a member fills with a code an admin gave them. For an
 * administrator that field has no use: they are signed in, so they already know their own
 * password, and a code they *cannot* redeem is not something to type. What is useful to them is
 * the other end of the same exchange — producing the code — so the segment swaps its body for
 * this generator. See `VerifyMethod.visibleTo` for the gate and `ChangePasswordSheet` for the
 * one `when` that draws either half.
 *
 * ## Why the code is drawn here and not in a popup
 *
 * The members screen raises a `ConfirmPopup` because it has no other surface to show one on.
 * This is already a sheet, and nesting a dialog inside a sheet to say one sentence is a second
 * dismissal to learn. The code appears in the form, under the button that produced it, with
 * Copy beside it — the same words, one less layer.
 *
 * ## The load is asked for from the composition, once
 *
 * [onLoadMembers] runs on first composition of this segment and the model ignores repeats, so
 * the directory is read when an administrator actually asks for a code — not on every visit to
 * Settings, and not again when they switch segments back and forth.
 */
@Composable
internal fun ResetCodeGenerator(
    panel: ResetCodePanel,
    onLoadMembers: () -> Unit,
    onSelectMember: (ResetCodeCandidate) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadMembers() }

    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val minted = panel.minted

    // The confirmation is counted rather than flagged: copying again while the first toast is
    // still up restarts the two seconds instead of leaving a stale one behind. The same shape
    // `ClipboardCopyHost` uses — it owns this for the work-item surfaces and is internal to its
    // own module, so the pattern travels and the function does not.
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        delay(CopiedToastMillis)
        copies = 0
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                MemberPickerAnchor(
                    selected = panel.selected,
                    isLoading = panel.isLoadingCandidates,
                    isEmpty = !panel.isLoadingCandidates && panel.candidates.isEmpty(),
                    onClick = { expanded = !expanded },
                )

                if (expanded) {
                    Popup(
                        popupPositionProvider = SettingsDropdownPositionProvider,
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        SettingsDropdownColumn(width = MemberDropdownWidth) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = MemberDropdownMaxHeight)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                panel.candidates.forEach { candidate ->
                                    ResetMemberOption(
                                        candidate = candidate,
                                        isSelected = candidate.id == panel.selectedID,
                                        onClick = {
                                            onSelectMember(candidate)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // The one action, disabled until it has something to act on. `loading` rather than a
            // second spinner: the button is where the administrator just tapped, so it is where the
            // wait belongs.
            ttGlassButton(
                label = stringResource(Res.string.settings_change_password_generate),
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth().testTag(SettingsResetGenerateTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                enabled = panel.selected != null,
                loading = panel.isGenerating,
            )

            // The server's refusal in the server's own words, like every other failure in this
            // sheet — `400` is in the error mapping's surface-safe set for exactly this.
            panel.failure?.let { failure ->
                ttText(
                    text = failure.message.orEmpty(),
                    style = ttTypography.footnote,
                    color = ttError(),
                    modifier = Modifier.testTag(SettingsResetCodeFailureTag),
                )
            }

            minted?.let { generated ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
                        .padding(ttSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    // The members screen's own sentence, reused rather than restated: who the code
                    // is for, that it dies after one use, when it dies, and the code itself on a
                    // line of its own. Two surfaces delivering the same credential must not word it
                    // two ways.
                    ttText(
                        text = stringResource(
                            Res.string.admin_members_reset_code_body,
                            generated.forMember.label,
                            generated.code,
                        ),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier.testTag(SettingsResetCodeTag),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
                    ) {
                        // The work-item card's affordance, not a worded button: the same glyph
                        // that copies a card copies this code, and the label the button used to
                        // carry becomes the content description, so a screen reader still gets it.
                        ttGlassIconButton(
                            image = ttActionIcons.copy,
                            contentDescription = stringResource(Res.string.admin_members_reset_code_copy),
                            onClick = {
                                clipboard.setText(AnnotatedString(generated.code))
                                copies++
                            },
                            modifier = Modifier.testTag(SettingsResetCopyTag),
                        )
                    }
                }
            }
        }

        // Overlaid on the bottom edge rather than appended to the column: a toast that pushed
        // the form would resize the sheet under the finger that just tapped Copy.
        if (copies > 0) {
            ttToast(
                message = stringResource(Res.string.copied_to_clipboard),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = ttSpacing.xxl),
            )
        }
    }
}

/**
 * The member picker, drawn as the field it replaced.
 *
 * Same geometry as `ttTextField` — glass, `ttRadius.row`, `ttSpacing.fieldMinHeight`, `xl`
 * horizontal padding — because it sits in that field's slot and a control that announced itself
 * as a different kind of thing would read as a second form.
 */
@Composable
private fun MemberPickerAnchor(
    selected: ResetCodeCandidate?,
    isLoading: Boolean,
    isEmpty: Boolean,
    onClick: () -> Unit,
) {
    val enabled = !isLoading && !isEmpty
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ttSpacing.fieldMinHeight)
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
            .clip(RoundedCornerShape(ttRadius.row))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md)
            .testTag(SettingsResetMemberPickerTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = when {
                isLoading -> stringResource(Res.string.settings_change_password_member_hint)
                isEmpty -> stringResource(Res.string.settings_change_password_members_empty)
                selected != null -> selected.label
                else -> stringResource(Res.string.settings_change_password_member_hint)
            },
            style = ttTypography.body,
            // A chosen member reads as a value; the hint reads as a placeholder.
            color = if (selected != null) ttLabel() else ttLabelSecondary(),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        when {
            isLoading -> ttSpinner(color = ttLabelSecondary(), size = ttSpacing.lg)
            enabled -> ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier.size(ttSpacing.sm * 1.5f),
            )
        }
    }
}

/**
 * One member in the picker's popup.
 *
 * The label is the line, the address is the second: two members can share a display name, and
 * the administrator is about to hand a credential to a specific account — the address is what
 * makes the row unambiguous when the name is not.
 */
@Composable
private fun ResetMemberOption(
    candidate: ResetCodeCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropdownOptionSurface(isSelected = isSelected)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag(resetMemberOptionTag(candidate))
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ttText(
                text = candidate.label,
                style = ttTypography.body,
                color = ttLabel(),
                maxLines = 1,
            )
            ttText(
                text = candidate.detail,
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                maxLines = 1,
            )
        }
    }
}

/** The picker's anchor: tapping it opens the member list. */
const val SettingsResetMemberPickerTag = "settings.changePassword.resetMember"

/** Per-member tag, so a scenario can choose a person without a positional matcher. */
fun resetMemberOptionTag(candidate: ResetCodeCandidate): String =
    "settings.changePassword.resetMember.${candidate.id}"

/** The button that mints the code. */
const val SettingsResetGenerateTag = "settings.changePassword.resetGenerate"

/** The minted code and its sentence. */
const val SettingsResetCodeTag = "settings.changePassword.resetCode"

/** The Copy button beside the minted code — the delivery, since there is no mailer. */
const val SettingsResetCopyTag = "settings.changePassword.resetCopy"

/** A mint that the server refused. */
const val SettingsResetCodeFailureTag = "settings.changePassword.resetFailure"
