package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.project_create
import com.tasktracker.resources.project_details_label
import com.tasktracker.resources.project_key_prefix_label
import com.tasktracker.resources.project_key_prefix_placeholder
import com.tasktracker.resources.project_name_label
import com.tasktracker.resources.project_editor_edit_title
import com.tasktracker.resources.project_editor_new_title
import com.tasktracker.resources.project_members_empty
import com.tasktracker.resources.project_members_label
import com.tasktracker.resources.project_members_placeholder
import com.tasktracker.resources.project_member_remove
import org.jetbrains.compose.resources.stringResource
import com.tasktracker.core.UserID

/**
 * The shared project form: project details and member search, in create or edit mode.
 *
 * ## The prefix is shown, not chosen
 *
 * [NewProjectModel] fills it in from the name as the user types, and the field is a
 * read-only rendering rather than an editable one. That is a deliberate inversion of the
 * obvious design, where the user types the prefix themselves because it is a short field
 * that looks like a field.
 *
 * A prefix is a permanent, public identity: it goes on every task key in the project, and
 * those keys are quoted in issues and messages for as long as the project exists. Asking
 * somebody to invent one at the moment they are naming a project produces `A`, `NEW`, and
 * `P2` - and those cannot be changed afterwards. Deriving it from the name gives `AP` for
 * "Apollo Programme", which is right often enough to be worth the odd miss, and the field
 * stays *visible* so a miss is noticed at creation rather than discovered later. It is
 * editable - [onKeyPrefixChanged] exists and the model accepts it - but the default is
 * offered rather than demanded.
 *
 * ## Why this is a sheet and not a screen
 *
 * A project is created from the board, and the board is where the user wants to be
 * afterwards. A full-screen form would mean a navigation round trip to do something that
 * takes two fields, so this draws over the board and the board is still there underneath
 * when the sheet closes.
 */
@Composable
fun NewProjectSheet(
    name: String,
    keyPrefix: String,
    details: String,
    canSave: Boolean,
    onNameChanged: (String) -> Unit,
    onKeyPrefixChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
    isCreating: Boolean = true,
    selectedMemberIDs: List<UserID> = emptyList(),
    onMemberIDsChanged: (List<UserID>) -> Unit = {},
) {
    var memberQuery by remember { mutableStateOf("") }
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(NewProjectSheetTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
        ) {
        ttText(
            text = if (isCreating) stringResource(Res.string.project_editor_new_title)
                else stringResource(Res.string.project_editor_edit_title, name),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ProjectsTextField(
            title = stringResource(Res.string.project_name_label),
            value = name,
            onValueChange = onNameChanged,
            imeAction = ImeAction.Next,
        )
        ProjectsTextField(
            title = stringResource(Res.string.project_key_prefix_label),
            value = keyPrefix,
            onValueChange = onKeyPrefixChanged,
            placeholder = stringResource(Res.string.project_key_prefix_placeholder),
            isReadOnly = true,
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Next,
        )
        ProjectsTextField(
            title = stringResource(Res.string.project_details_label),
            value = details,
            onValueChange = onDetailsChanged,
            singleLine = false,
            minLines = DETAILS_MIN_LINES,
            imeAction = ImeAction.Next,
        )
        ProjectsTextField(
            title = stringResource(Res.string.project_members_label),
            value = memberQuery,
            onValueChange = { memberQuery = it },
            placeholder = stringResource(Res.string.project_members_placeholder),
            imeAction = ImeAction.Search,
        )
        if (memberQuery.isNotBlank()) {
            val matches = MockProjectMembers.search(memberQuery)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
                    .padding(ttSpacing.md)
                    .testTag(ProjectMembersDropdownTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            ) {
                if (matches.isEmpty()) {
                    ttText(
                        text = stringResource(Res.string.project_members_empty),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                    )
                } else {
                    matches.forEach { member ->
                        val selected = member.id in selectedMemberIDs
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    onMemberIDsChanged(
                                        if (selected) selectedMemberIDs - member.id
                                        else selectedMemberIDs + member.id,
                                    )
                                }
                                .testTag(ProjectMemberOptionTag(member)),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
                        ) {
                            ttText(
                                text = if (selected) "${member.name} ✓" else member.name,
                                style = ttTypography.callout,
                                color = if (selected) ttAccent() else ttLabel(),
                            )
                            ttText(text = member.email, style = ttTypography.captionSmall, color = ttLabelSecondary())
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            MockProjectMembers.members.forEach { member ->
                AnimatedVisibility(
                    visible = member.id in selectedMemberIDs,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
                            .padding(start = ttSpacing.md, end = ttSpacing.sm, top = ttSpacing.sm, bottom = ttSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
                        ) {
                            ttText(text = member.name, style = ttTypography.callout, color = ttLabel())
                            ttText(text = member.email, style = ttTypography.captionSmall, color = ttLabelSecondary())
                        }
                        ttGlassIconButton(
                            image = ttActionIcons.remove,
                            contentDescription = stringResource(Res.string.project_member_remove),
                            onClick = { onMemberIDsChanged(selectedMemberIDs - member.id) },
                            modifier = Modifier.testTag(ProjectMemberRemoveTag(member)),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onCancel,
                modifier = Modifier.testTag(NewProjectCancelTag),
            )
            ttGlassButton(
                label = if (isCreating) stringResource(Res.string.project_create)
                    else stringResource(Res.string.common_save),
                // Same reasoning as the backlog's busy rows: a button that looks inert and
                // is not is worse than one that looks live and briefly does nothing.
                onClick = { if (canSave && !isBusy) onSave() },
                modifier = Modifier.testTag(NewProjectCreateTag),
                style = if (canSave && !isBusy) {
                    TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                } else {
                    TTGlassButtonStyle()
                },
            )
        }
        }
    }
}

@Preview
@Composable
private fun NewProjectSheetPreview() = ProjectsPreview(ThemeScheme.Dark) {
    NewProjectSheet(
        name = "Apollo Programme",
        keyPrefix = "AP",
        details = "",
        canSave = true,
        onNameChanged = {},
        onKeyPrefixChanged = {},
        onDetailsChanged = {},
        onSave = {},
        onCancel = {},
    )
}

@Preview
@Composable
private fun NewProjectSheetDisabledPreview() = ProjectsPreview(ThemeScheme.Light) {
    NewProjectSheet(
        name = "",
        keyPrefix = "",
        details = "",
        canSave = false,
        onNameChanged = {},
        onKeyPrefixChanged = {},
        onDetailsChanged = {},
        onSave = {},
        onCancel = {},
    )
}

const val NewProjectSheetTag = "projects.newSheet"
const val NewProjectCreateTag = "projects.newSheet.create"
const val NewProjectCancelTag = "projects.newSheet.cancel"
const val ProjectMembersDropdownTag = "projects.editor.members.dropdown"
fun ProjectMemberOptionTag(member: ProjectMember): String = "projects.editor.members.option.${member.id}"
fun ProjectMemberRemoveTag(member: ProjectMember): String = "projects.editor.members.remove.${member.id}"

private const val DETAILS_MIN_LINES = 3
