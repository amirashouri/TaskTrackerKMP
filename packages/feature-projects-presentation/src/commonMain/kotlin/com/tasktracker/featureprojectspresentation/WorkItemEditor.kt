package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassFlowPicker
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_assignee_label
import com.tasktracker.resources.work_item_details_label
import com.tasktracker.resources.work_item_due_clear
import com.tasktracker.resources.work_item_due_label
import com.tasktracker.resources.work_item_due_set
import com.tasktracker.resources.work_item_editor_edit_title
import com.tasktracker.resources.work_item_editor_new_title
import com.tasktracker.resources.work_item_key_read_only
import com.tasktracker.resources.work_item_priority_label
import com.tasktracker.resources.work_item_status_label
import com.tasktracker.resources.work_item_title_label
import org.jetbrains.compose.resources.stringResource

/**
 * The work item form, as a full screen.
 *
 * ## This is a draft, never a stored record
 *
 * Every field is local state that starts from a snapshot and is only sent on an explicit
 * save. The alternative - binding the fields straight to a persisted `WorkItem` - would
 * write an edit the moment a character was typed, which is how a record gets an outbox row
 * per keystroke and how a half-typed title becomes something a second device can pull.
 * [WorkItemEditorModel] holds the draft; this holds nothing.
 *
 * ## Why the key is shown and not offered for editing
 *
 * The project issues it, and a key that changed after the fact would break every message
 * that quotes one. The full sentence explaining that is rendered rather than a lock glyph,
 * because a lock with no explanation reads as a bug.
 *
 * ## Why there is no date picker
 *
 * There is no date-picker dependency, and a hand-rolled calendar grid is not something to
 * reimplement inside a feature module. "Set a due date" and "Clear the due date" toggle
 * between today's date and no date, which is honest about what it can do and does not
 * pretend to be a date picker.
 */
@Composable
fun WorkItemEditorScreen(
    title: String,
    details: String,
    status: WorkItemStatus,
    priority: Priority,
    assignee: String,
    hasDueDate: Boolean,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (String) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkItemEditorForm(
        title = title,
        details = details,
        status = status,
        priority = priority,
        assignee = assignee,
        hasDueDate = hasDueDate,
        isCreating = isCreating,
        issuedKey = issuedKey,
        selectableStatuses = selectableStatuses,
        isFiledAway = isFiledAway,
        canSave = canSave,
        isBusy = isBusy,
        errorMessage = errorMessage,
        onTitleChanged = onTitleChanged,
        onDetailsChanged = onDetailsChanged,
        onStatusChanged = onStatusChanged,
        onPriorityChanged = onPriorityChanged,
        onAssigneeChanged = onAssigneeChanged,
        onToggleDueDate = onToggleDueDate,
        onFileAway = onFileAway,
        onSave = onSave,
        onCancel = onCancel,
        onDismissError = onDismissError,
        modifier = modifier
            .fillMaxSize()
            .padding(ttSpacing.screenGutterLarge)
            .verticalScroll(rememberScrollState())
            // After the scroll, so it is range and not viewport: this form's save and cancel
            // buttons are its last children, and they are the reason the screen is a scroll at
            // all. `LocalTabBarScrollUnderInset` — the floating tab bar covers this screen's
            // bottom edge like it covers the four tabs'.
            .padding(bottom = LocalTabBarScrollUnderInset.current)
            .testTag(WorkItemEditorScreenTag),
    )
}

/**
 * The same form, drawn over the board.
 *
 * ## Why the body is a shared function and not a copy
 *
 * A screen and a sheet that "look the same" diverge at the fourth field, always in the
 * sheet, and nothing catches it. The only real difference between the two surfaces is what
 * surrounds the form - a full screen has the board's chrome behind it, a sheet has the board
 * itself - so that is the only thing that differs here, and both call this.
 */
@Composable
fun WorkItemEditorSheet(
    title: String,
    details: String,
    status: WorkItemStatus,
    priority: Priority,
    assignee: String,
    hasDueDate: Boolean,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (String) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        WorkItemEditorForm(
            title = title,
            details = details,
            status = status,
            priority = priority,
            assignee = assignee,
            hasDueDate = hasDueDate,
            isCreating = isCreating,
            issuedKey = issuedKey,
            selectableStatuses = selectableStatuses,
            isFiledAway = isFiledAway,
            canSave = canSave,
            isBusy = isBusy,
            errorMessage = errorMessage,
            onTitleChanged = onTitleChanged,
            onDetailsChanged = onDetailsChanged,
            onStatusChanged = onStatusChanged,
            onPriorityChanged = onPriorityChanged,
            onAssigneeChanged = onAssigneeChanged,
            onToggleDueDate = onToggleDueDate,
            onFileAway = onFileAway,
            onSave = onSave,
            onCancel = onCancel,
            onDismissError = onDismissError,
            modifier = Modifier.testTag(WorkItemEditorSheetTag),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun WorkItemEditorForm(
    title: String,
    details: String,
    status: WorkItemStatus,
    priority: Priority,
    assignee: String,
    hasDueDate: Boolean,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (String) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
    ) {
        ttText(
            text = if (isCreating) {
                stringResource(Res.string.work_item_editor_new_title)
            } else {
                stringResource(Res.string.work_item_editor_edit_title, issuedKey?.toString().orEmpty())
            },
            style = ttTypography.title,
            color = ttLabel(),
        )
        errorMessage?.let { message ->
            ProjectsErrorBanner(message = message, onDismiss = onDismissError)
        }
        issuedKey?.let { key ->
            ttText(
                text = stringResource(Res.string.work_item_key_read_only, key.toString()),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
        ProjectsTextField(
            title = stringResource(Res.string.work_item_title_label),
            value = title,
            onValueChange = onTitleChanged,
            imeAction = ImeAction.Next,
        )
        ProjectsTextField(
            title = stringResource(Res.string.work_item_details_label),
            value = details,
            onValueChange = onDetailsChanged,
            singleLine = false,
            minLines = DETAILS_MIN_LINES,
            imeAction = ImeAction.Next,
        )
        if (selectableStatuses.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
                ttText(
                    text = stringResource(Res.string.work_item_status_label),
                    style = ttTypography.captionEmphasised,
                    color = ttLabelSecondary(),
                )
                TTGlassFlowPicker(
                    options = selectableStatuses,
                    selection = status,
                    onSelect = onStatusChanged,
                    title = { statusLabel(it) },
                    modifier = Modifier.testTag(WorkItemStatusPickerTag),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
            ttText(
                text = stringResource(Res.string.work_item_priority_label),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            TTGlassFlowPicker(
                options = Priority.entries,
                selection = priority,
                onSelect = onPriorityChanged,
                title = { priorityLabel(it) },
                modifier = Modifier.testTag(WorkItemPriorityPickerTag),
            )
        }
        ProjectsTextField(
            title = stringResource(Res.string.work_item_assignee_label),
            value = assignee,
            onValueChange = onAssigneeChanged,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            onImeAction = { if (canSave) onSave() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText(
                text = stringResource(Res.string.work_item_due_label),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
                modifier = Modifier.weight(1f),
            )
            ttGlassButton(
                label = if (hasDueDate) {
                    stringResource(Res.string.work_item_due_clear)
                } else {
                    stringResource(Res.string.work_item_due_set)
                },
                onClick = { onToggleDueDate(!hasDueDate) },
                modifier = Modifier.testTag(WorkItemDueToggleTag),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            if (!isFiledAway) {
                ttGlassButton(
                    label = stringResource(Res.string.work_item_archive),
                    onClick = { if (!isBusy) onFileAway() },
                    modifier = Modifier.testTag(WorkItemFileAwayTag),
                    style = TTGlassButtonStyle(tint = ttError(), prominent = true),
                )
            }
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onCancel,
                modifier = Modifier.testTag(WorkItemCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.common_save),
                onClick = { if (canSave && !isBusy) onSave() },
                modifier = Modifier.testTag(WorkItemSaveTag),
                style = if (canSave && !isBusy) {
                    TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                } else {
                    TTGlassButtonStyle()
                },
            )
        }
    }
}

@Preview
@Composable
private fun WorkItemEditorScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    WorkItemEditorScreen(
        title = "Crash on launch when the vault is empty",
        details = "Reported on a cold install with no keychain entry.",
        status = WorkItemStatus.InProgress,
        priority = Priority.Critical,
        assignee = "Amir",
        hasDueDate = false,
        isCreating = false,
        issuedKey = ViewPreviewData.workItems.first { it.status == WorkItemStatus.InProgress }.key,
        selectableStatuses = WorkItemStatus.boardOrder,
        isFiledAway = false,
        canSave = true,
        isBusy = false,
        errorMessage = null,
        onTitleChanged = {},
        onDetailsChanged = {},
        onStatusChanged = {},
        onPriorityChanged = {},
        onAssigneeChanged = {},
        onToggleDueDate = {},
        onFileAway = {},
        onSave = {},
        onCancel = {},
        onDismissError = {},
    )
}

@Preview
@Composable
private fun WorkItemEditorFiledAwayPreview() = ProjectsPreview(ThemeScheme.Light) {
    WorkItemEditorSheet(
        title = "Archive the old sync experiments",
        details = "",
        status = WorkItemStatus.Closed,
        priority = Priority.Low,
        assignee = "",
        hasDueDate = false,
        isCreating = false,
        issuedKey = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Closed }.key,
        selectableStatuses = emptyList(),
        isFiledAway = true,
        canSave = true,
        isBusy = false,
        errorMessage = null,
        onTitleChanged = {},
        onDetailsChanged = {},
        onStatusChanged = {},
        onPriorityChanged = {},
        onAssigneeChanged = {},
        onToggleDueDate = {},
        onFileAway = {},
        onSave = {},
        onCancel = {},
        onDismissError = {},
    )
}

const val WorkItemEditorScreenTag = "workItem.editor.screen"
const val WorkItemEditorSheetTag = "workItem.editor.sheet"
const val WorkItemStatusPickerTag = "workItem.editor.status"
const val WorkItemPriorityPickerTag = "workItem.editor.priority"
const val WorkItemDueToggleTag = "workItem.editor.dueToggle"
const val WorkItemFileAwayTag = "workItem.editor.fileAway"
const val WorkItemSaveTag = "workItem.editor.save"
const val WorkItemCancelTag = "workItem.editor.cancel"

private const val DETAILS_MIN_LINES = 3
