package com.tasktracker.featureteamspresentation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSelectionWash
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featuresettingsdomain.UserPosition
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_members_add_title
import com.tasktracker.resources.admin_members_create
import com.tasktracker.resources.admin_members_field_email
import com.tasktracker.resources.admin_members_field_password
import com.tasktracker.resources.admin_members_field_position
import com.tasktracker.resources.admin_members_field_role
import com.tasktracker.resources.admin_members_position_none
import com.tasktracker.resources.admin_members_role_admin
import com.tasktracker.resources.admin_members_role_member
import com.tasktracker.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * The add-member form: an address, a password, a role, and a job title.
 *
 * ## The panel, with no container of its own
 *
 * This is the form and nothing about where it is drawn. On a phone it sits inside
 * [AddMemberDialog], which adds the scrim and centres it; on a wide window the members section
 * puts it in the right pane, where a scrim over a half-empty pane would darken the list beside a
 * form that is not covering it. The caller owns the container, which is the only thing the two
 * arrangements differ in.
 *
 * ## The fields own themselves and nothing else
 *
 * The four values are local state, so a failed create keeps them and the admin corrects a typo
 * rather than retyping the form — which is why this composable is *not* torn down when
 * [errorMessage] becomes non-null. Everything that outlives one visit to the form — the list,
 * the failure, whether a create is in flight — is the model's, and arrives as a parameter.
 *
 * ## Why the dismiss is optional
 *
 * On the phone the form is drawn over a scrim, so it is Cancel that closes it. In the wide
 * window's pane the form *is* the pane, for as long as the members section is open: there is
 * nothing to dismiss, so the pane passes `null` and no Cancel is drawn. A Cancel wired to
 * nothing would be a control claiming an action the arrangement does not have.
 *
 * ## The role picker may set `Admin`, and that is the point of it
 *
 * This is the only screen in the app that can mint another administrator. [MemberRole.Member]
 * is the resting selection so that the deliberate act is the one that grants privilege; a form
 * that defaulted to `Admin` would hand out the role on a mis-tap.
 *
 * ## The position goes over the wire as its display name
 *
 * [UserPosition] carries both a `wireValue` ("product_owner") and a `displayName` ("Product
 * owner"), and this sends the second. The server stores `position` as free text — its own API
 * documents the field as `"Designer"` and `"Senior Designer"`, not as a slug — and the member
 * list renders whatever it stores verbatim, so a slug here would be shown to the next admin as
 * one. `wireValue` is the mapping for a value the server *parses*; position is the one field it
 * never does.
 */
@Composable
fun AddMemberForm(
    isCreating: Boolean,
    errorMessage: String?,
    onCreate: (email: String, password: String, role: MemberRole, position: String?) -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(MemberRole.Member) }
    var position by remember { mutableStateOf<UserPosition?>(null) }

    // Both fields are required and the server rejects a blank one, so the button stays down
    // until there is something to send. Not a validation message: an empty field that has not
    // been filled in yet is not an error, and a form that scolds before the first keystroke is
    // a form that reads as broken on arrival.
    val canCreate = email.isNotBlank() && password.isNotBlank() && !isCreating

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ttSpacing.xxl)
            .testTag(AddMemberFormTag)
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.surface))
            .padding(ttSpacing.formStack),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
    ) {
        ttText(
            text = stringResource(Res.string.admin_members_add_title),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )

        if (errorMessage != null) {
            ttBanner(message = errorMessage)
        }

        MemberField(
            title = stringResource(Res.string.admin_members_field_email),
            value = email,
            onValueChange = { email = it },
            testTag = AddMemberEmailFieldTag,
        )

        MemberField(
            title = stringResource(Res.string.admin_members_field_password),
            value = password,
            onValueChange = { password = it },
            testTag = AddMemberPasswordFieldTag,
            isPassword = true,
        )

        ttText(
            text = stringResource(Res.string.admin_members_field_role),
            style = ttTypography.footnote,
            color = ttBodyLabel(),
        )
        TTGlassSegmentedPicker(
            options = MemberRole.entries,
            selection = role,
            onSelect = { role = it },
            title = { option ->
                when (option) {
                    MemberRole.Admin -> stringResource(Res.string.admin_members_role_admin)
                    MemberRole.Member -> stringResource(Res.string.admin_members_role_member)
                }
            },
            testTag = { addMemberRoleOptionTag(it) },
        )

        ttText(
            text = stringResource(Res.string.admin_members_field_position),
            style = ttTypography.footnote,
            color = ttBodyLabel(),
        )
        PositionPicker(
            selected = position,
            onSelect = { position = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            // No dismiss means no Cancel, and Create then takes the width the pair would have
            // split between them.
            if (onDismiss != null) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).testTag(AddMemberCancelTag),
                )
            }
            ttGlassButton(
                label = stringResource(Res.string.admin_members_create),
                onClick = {
                    onCreate(email, password, role, position?.displayName)
                },
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                enabled = canCreate,
                loading = isCreating,
                modifier = Modifier.weight(1f).testTag(AddMemberCreateTag),
            )
        }
    }
}

/**
 * A labelled text field on the glass, with the label doubling as the placeholder.
 *
 * The stock styles are not available — this design system has no Material3 — and the auth
 * feature's `LabelledField` is in a module this one may not see, so the field is built here
 * from `BasicTextField` and the shared tokens. [title] is used twice on purpose: above the
 * field it says what the field is, and inside it, at secondary ink, it hints at what to type.
 * The two are the same word, and the alternative is a second resource per field whose only job
 * is to paraphrase its own label.
 */
@Composable
private fun MemberField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = title,
            style = ttTypography.footnote,
            color = ttBodyLabel(),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))
                .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.xl),
            textStyle = ttTypography.body.copy(color = ttLabel()),
            cursorBrush = SolidColor(ttAccent()),
            singleLine = true,
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email,
                imeAction = ImeAction.Next,
                // No autocorrect and no capitalization: an address that the keyboard rewrites
                // on the way out is an address the server rejects, and the failure would be
                // reported against the password.
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        ttText(
                            text = title,
                            style = ttTypography.body,
                            color = ttLabelSecondary(),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * The job-title dropdown, in the vocabulary the settings page established.
 *
 * A dropdown rather than a segmented control because there are five titles whose labels are
 * long enough that five segments would each be narrower than the word inside them. The options
 * are [UserPosition.all], and `null` — "No position" — is a sixth: the server field is optional
 * and a form that could not express "none" would make an admin pick a title that is not theirs.
 */
@Composable
private fun PositionPicker(
    selected: UserPosition?,
    onSelect: (UserPosition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                .testTag(AddMemberPositionRowTag)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                ) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = selected?.displayName
                    ?: stringResource(Res.string.admin_members_position_none),
                style = ttTypography.body,
                color = if (selected == null) ttLabelSecondary() else ttLabel(),
                modifier = Modifier.weight(1f),
            )
            ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier.size(ttSpacing.sm * 1.5f),
            )
        }

        if (expanded) {
            Popup(
                popupPositionProvider = MemberDropdownPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(PositionDropdownWidth)
                        .ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))
                        .padding(vertical = ttSpacing.xs),
                ) {
                    PositionOption(
                        label = stringResource(Res.string.admin_members_position_none),
                        tag = memberPositionOptionTag(null),
                        isSelected = selected == null,
                        onClick = {
                            onSelect(null)
                            expanded = false
                        },
                    )
                    UserPosition.all.forEach { position ->
                        PositionOption(
                            label = position.displayName,
                            tag = memberPositionOptionTag(position),
                            isSelected = position == selected,
                            onClick = {
                                onSelect(position)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionOption(
    label: String,
    tag: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.control))
            .ttSelectionWash(selected = isSelected)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The selected option takes the accent and the rest take the label ink — the pair the
        // segmented picker uses two screens away. The settings page's dropdown inverts to white
        // in the light theme instead, but that inversion exists to sit on a saturated accent
        // wash; this row's wash is the shared selection wash, which the accent ink already
        // reads against.
        ttText(
            text = label,
            style = ttTypography.body,
            color = if (isSelected) ttAccent() else ttLabel(),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Where the dropdown opens: under its trigger, right-aligned, flipped above when the bottom of
 * the screen is in the way.
 *
 * The same four rules as the settings page's dropdown provider. Two copies exist because the
 * two live in modules that cannot see each other, and the rule is four lines of clamping rather
 * than a shared vocabulary worth a module of its own.
 */
private object MemberDropdownPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val preferredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
            LayoutDirection.Rtl -> anchorBounds.left
        }
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val y = if (below + popupContentSize.height <= windowSize.height) below else above
        return IntOffset(
            x = preferredX.coerceIn(0, maxX),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** The dropdown popup's width, matching the settings page's dropdowns. */
private val PositionDropdownWidth = 216.dp

/** One row of the dropdown's own height; the settings dropdown uses the same 48dp target. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * The form's tag.
 *
 * Carries no `Dialog` in its name: the panel is the same node in both arrangements, and a tag
 * that named the phone's container would be matched by a desktop test that is asserting
 * something else.
 */
const val AddMemberFormTag = "adminMembers.addForm"

/** The email field's tag. */
const val AddMemberEmailFieldTag = "adminMembers.add.email"

/** The password field's tag. */
const val AddMemberPasswordFieldTag = "adminMembers.add.password"

/** The position dropdown's trigger tag. */
const val AddMemberPositionRowTag = "adminMembers.add.positionRow"

/** The create button's tag. */
const val AddMemberCreateTag = "adminMembers.add.create"

/** The cancel button's tag. */
const val AddMemberCancelTag = "adminMembers.add.cancel"

/** The tag on the segment that selects [role]. */
fun addMemberRoleOptionTag(role: MemberRole): String = "adminMembers.add.role.${role.wireValue}"

/**
 * The tag on the dropdown row for a position, or for the "No position" row when [position] is
 * `null`.
 *
 * Keyed on the enum's `wireValue` rather than on the label it draws: a tag is what a test
 * matches, and a label is a translated string that changes under a locale the test did not
 * choose.
 */
fun memberPositionOptionTag(position: UserPosition?): String =
    "adminMembers.add.position.${position?.wireValue ?: "none"}"
