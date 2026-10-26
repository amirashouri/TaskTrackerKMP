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
 * The full-screen project editor, for creating a project or renaming an existing one.
 *
 * ## Why this exists alongside [NewProjectSheet]
 *
 * They are two shapes of the same form, and the difference is only where they are opened
 * from. A project is created from the board, so creating draws *over* the board in a sheet
 * and the board is still there when it closes. Editing a project is a deliberate,
 * whole-screen act reached from the project's own header, so it gets a screen.
 *
 * Both are stateless and take the model's state, which is why they cannot drift in what
 * fields they offer: the fields live in [ProjectsTextField] and the rules live in
 * [ProjectEditorModel], and neither screen decides what is valid.
 *
 * ## The title says which of the two this is
 *
 * "New Project" and "Edit Apollo Programme" are different screens that share a form, and a
 * user who cannot tell which one is open will save an edit expecting a new project. So the
 * title is a parameter of the mode rather than a label the caller remembers to change - and
 * the confirm button follows it, reading Create when creating and Save when editing, for the
 * same reason.
 *
 * ## The key prefix is shown, never typed
 *
 * A prefix is a permanent public identity, and the project already suggests one derived from
 * the name (see [NewProjectSheet] for why it is derived rather than asked for). Here it is
 * rendered read-only in both modes, and the explanation sits under it. In edit mode the
 * reason is hard - the project has already issued task keys from this prefix and they are
 * quoted in issues forever - and stating that in the editor is cheaper than answering it in
 * a support thread later.
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
    /**
     * Whether closing now would throw away work.
     *
     * Supplied by the model rather than derived here: this screen has no snapshot to compare
     * against, and a form that guessed "dirty" from a non-blank field would prompt on an
     * unchanged edit. Clean and dirty differ only in whether Cancel asks first.
     */
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isBusy: Boolean = false,
    /**
     * An optional section between the fields and the actions.
     *
     * A slot rather than tag parameters because the shared screen serves two editors with
     * different rights: the member's offline editor has no tag management at all, while the
     * admin's adds it. A slot keeps the tag UI in its own file and keeps this screen from
     * growing a `canManageTags` flag that the member editor would have to pass `false` to
     * forever. `null` — the default — renders exactly the screen that existed before tags.
     */
    tagsSection: (@Composable () -> Unit)? = null,
) {
    val saveEnabled = canSave && !isBusy

    // Kept here rather than in a host: Cancel is this screen's own action, and every caller
    // that hands it a draft must get the same guard without re-implementing one.
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
                // No `onValueChange`: the field cannot be typed into, so there is nothing to
                // report. The model still exposes one for a caller that wants to offer a
                // suggestion, and it refuses the call while editing regardless.
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
                        // A clean form closes at once: prompting there would make Cancel feel
                        // broken on the common case. Only work that would disappear asks first.
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
                    // Disabled rather than merely ignored, and *styled* as disabled: a button
                    // that looks live and does nothing is the more annoying of the two, because
                    // the user cannot tell whether the tap landed.
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
