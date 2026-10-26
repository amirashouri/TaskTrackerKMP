package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_discard
import com.tasktracker.resources.common_save
import com.tasktracker.resources.editor_discard_body
import com.tasktracker.resources.editor_discard_title
import com.tasktracker.resources.project_create
import com.tasktracker.resources.project_details_label
import com.tasktracker.resources.project_editor_edit_title
import com.tasktracker.resources.project_editor_new_title
import com.tasktracker.resources.project_key_prefix_label
import com.tasktracker.resources.project_key_prefix_placeholder
import com.tasktracker.resources.project_key_prefix_read_only
import com.tasktracker.resources.project_name_label
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen editor for creating or updating a project.
 * Renders read-only key prefix and prompts discard confirmation if [hasUnsavedChanges] is true.
 */
@Composable
fun ProjectEditorScreen(
    name: String,
    keyPrefix: String,
    details: String,
    isCreating: Boolean,
    canSave: Boolean,
    onNameChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    /** Prompts confirmation on cancel if true. */
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isBusy: Boolean = false,
    /** Optional custom slot (e.g. admin tag management). */
    tagsSection: (@Composable () -> Unit)? = null,
) {
    val saveEnabled = canSave && !isBusy
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.formStack)
                .testTag(ProjectEditorTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
        ) {
            ttText(
                text = if (isCreating) {
                    stringResource(Res.string.project_editor_new_title)
                } else {
                    stringResource(Res.string.project_editor_edit_title, name)
                },
                style = ttTypography.title,
                color = ttLabel(),
            )
            ProjectsTextField(
                title = stringResource(Res.string.project_name_label),
                value = name,
                onValueChange = onNameChanged,
                modifier = Modifier.testTag(ProjectEditorNameTag),
                imeAction = ImeAction.Next,
            )
            ProjectsTextField(
                title = stringResource(Res.string.project_key_prefix_label),
                value = keyPrefix,
                onValueChange = {},
                modifier = Modifier.testTag(ProjectEditorKeyPrefixTag),
                placeholder = stringResource(Res.string.project_key_prefix_placeholder),
                isReadOnly = true,
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Next,
            )
            ttText(
                text = stringResource(Res.string.project_key_prefix_read_only),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
            ProjectsTextField(
                title = stringResource(Res.string.project_details_label),
                value = details,
                onValueChange = onDetailsChanged,
                modifier = Modifier.testTag(ProjectEditorDetailsTag),
                singleLine = false,
                minLines = EDITOR_DETAILS_MIN_LINES,
                imeAction = ImeAction.Done,
                onImeAction = { if (saveEnabled) onSave() },
            )
            tagsSection?.invoke()
            if (errorMessage != null) {
                ttText(
                    text = errorMessage,
                    style = ttTypography.footnote,
                    color = ttError(),
                    modifier = Modifier.testTag(ProjectEditorErrorTag),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = {
                        if (hasUnsavedChanges) showDiscardConfirmation = true else onCancel()
                    },
                    modifier = Modifier.testTag(ProjectEditorCancelTag),
                )
                ttGlassButton(
                    label = if (isCreating) {
                        stringResource(Res.string.project_create)
                    } else {
                        stringResource(Res.string.common_save)
                    },
                    onClick = { if (saveEnabled) onSave() },
                    modifier = Modifier.testTag(ProjectEditorSaveTag),
                    style = if (saveEnabled) {
                        TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                    } else {
                        TTGlassButtonStyle()
                    },
                )
            }
        }

        if (showDiscardConfirmation) {
            ConfirmPopup(
                title = stringResource(Res.string.editor_discard_title),
                body = stringResource(Res.string.editor_discard_body),
                cancelLabel = stringResource(Res.string.common_cancel),
                confirmLabel = stringResource(Res.string.common_discard),
                onCancel = { showDiscardConfirmation = false },
                onConfirm = {
                    showDiscardConfirmation = false
                    onCancel()
                },
                confirmTint = ttError(),
                tag = ProjectEditorDiscardTag,
            )
        }
    }
}

const val ProjectEditorTag = "projects.editor"
const val ProjectEditorNameTag = "projects.editor.name"
const val ProjectEditorKeyPrefixTag = "projects.editor.keyPrefix"
const val ProjectEditorDetailsTag = "projects.editor.details"
const val ProjectEditorSaveTag = "projects.editor.save"
const val ProjectEditorCancelTag = "projects.editor.cancel"
const val ProjectEditorDiscardTag = "projects.editor.discard"
const val ProjectEditorErrorTag = "projects.editor.error"

private const val EDITOR_DETAILS_MIN_LINES = 4