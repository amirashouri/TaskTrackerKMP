package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.project_create
import com.tasktracker.resources.project_details_label
import com.tasktracker.resources.project_editor_edit_title
import com.tasktracker.resources.project_editor_new_title
import com.tasktracker.resources.project_key_prefix_label
import com.tasktracker.resources.project_key_prefix_placeholder
import com.tasktracker.resources.project_name_label
import org.jetbrains.compose.resources.stringResource

/**
 * Shared project form fields (name, auto-derived key prefix, details, and member picker).
 * Container-agnostic: rendered inside mobile bottom sheets or persistent wide-window panes.
 */
@Composable
fun NewProjectForm(
    name: String,
    keyPrefix: String,
    details: String,
    canSave: Boolean,
    onNameChanged: (String) -> Unit,
    onKeyPrefixChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    isCreating: Boolean = true,
    selectedMemberIDs: List<UserID> = emptyList(),
    onMemberIDsChanged: (List<UserID>) -> Unit = {},
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /** Resolves selected member IDs to profiles unfiltered. */
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    errorMessage: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(NewProjectFormTag),
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
        ProjectMembersSection(
            selectedMemberIDs = selectedMemberIDs,
            onMemberIDsChanged = onMemberIDsChanged,
            searchMembers = searchMembers,
            resolveSelected = resolveSelected,
        )
        if (errorMessage != null) {
            ttText(
                text = errorMessage,
                style = ttTypography.footnote,
                color = ttError(),
                modifier = Modifier.testTag(NewProjectFormErrorTag),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            if (onDismiss != null) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.testTag(NewProjectCancelTag),
                )
            }
            ttGlassButton(
                label = if (isCreating) stringResource(Res.string.project_create)
                else stringResource(Res.string.common_save),
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

const val NewProjectFormTag = "projects.newForm"
const val NewProjectCreateTag = "projects.newForm.create"
const val NewProjectCancelTag = "projects.newForm.cancel"
const val NewProjectFormErrorTag = "projects.newForm.error"

private const val DETAILS_MIN_LINES = 3