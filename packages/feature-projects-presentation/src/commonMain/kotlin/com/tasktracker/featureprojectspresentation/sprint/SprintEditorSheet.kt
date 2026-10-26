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
import androidx.compose.ui.text.input.KeyboardType
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTDateField
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.sprint_editor_edit_title
import com.tasktracker.resources.sprint_editor_locked_note
import com.tasktracker.resources.sprint_editor_new_title
import com.tasktracker.resources.sprint_editor_overlap_field
import com.tasktracker.resources.sprint_end_before_start_field
import com.tasktracker.resources.sprint_end_label
import com.tasktracker.resources.sprint_goal_label
import com.tasktracker.resources.sprint_goal_placeholder
import com.tasktracker.resources.sprint_goal_text_label
import com.tasktracker.resources.sprint_goal_text_placeholder
import com.tasktracker.resources.sprint_name_label
import com.tasktracker.resources.sprint_owner_label
import com.tasktracker.resources.sprint_start_label
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet form for creating and editing sprints.
 * Disables date range and story point cap inputs when [windowLocked] is true.
 */
@Composable
fun SprintEditorSheet(
    name: String,
    goalStoryPoints: Int?,
    goal: String,
    startDate: LocalDate?,
    endDate: LocalDate?,
    ownerID: UserID?,
    isCreating: Boolean,
    canSave: Boolean,
    endIsBeforeStart: Boolean?,
    blockedBySprintName: String?,
    onNameChanged: (String) -> Unit,
    onGoalStoryPointsChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onStartDateChanged: (LocalDate) -> Unit,
    onEndDateChanged: (LocalDate) -> Unit,
    onOwnerChanged: (UserID?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    /** Freezes dates and goal story point inputs when true. */
    windowLocked: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
    today: LocalDate? = null,
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    /** Server or store validation error banner text. */
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SprintEditorSheetTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
        ) {
            ttText(
                text = if (isCreating) {
                    stringResource(Res.string.sprint_editor_new_title)
                } else {
                    stringResource(Res.string.sprint_editor_edit_title, name)
                },
                style = ttTypography.title,
                color = ttLabel(),
            )
            if (windowLocked) {
                ttText(
                    text = stringResource(Res.string.sprint_editor_locked_note),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(SprintEditorLockedNoteTag),
                )
            }
            ProjectsTextField(
                title = stringResource(Res.string.sprint_name_label),
                value = name,
                onValueChange = onNameChanged,
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(SprintEditorNameTag),
            )
            ProjectsTextField(
                title = stringResource(Res.string.sprint_goal_label),
                value = goalStoryPoints?.toString().orEmpty(),
                onValueChange = onGoalStoryPointsChanged,
                placeholder = stringResource(Res.string.sprint_goal_placeholder),
                enabled = !windowLocked,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(SprintEditorGoalPointsTag),
            )
            ProjectsTextField(
                title = stringResource(Res.string.sprint_goal_text_label),
                value = goal,
                onValueChange = onGoalChanged,
                placeholder = stringResource(Res.string.sprint_goal_text_placeholder),
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(SprintEditorGoalTag),
            )
            TTDateField(
                label = stringResource(Res.string.sprint_start_label),
                value = startDate,
                onValueChange = onStartDateChanged,
                enabled = !windowLocked,
                today = today,
                modifier = Modifier.testTag(SprintEditorStartDateTag),
            )
            Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
                TTDateField(
                    label = stringResource(Res.string.sprint_end_label),
                    value = endDate,
                    onValueChange = onEndDateChanged,
                    enabled = !windowLocked,
                    today = today,
                    modifier = Modifier.testTag(SprintEditorEndDateTag),
                )
                if (endIsBeforeStart == true) {
                    ttText(
                        text = stringResource(Res.string.sprint_end_before_start_field),
                        style = ttTypography.footnote,
                        color = ttError(),
                        modifier = Modifier.testTag(SprintEditorWindowErrorTag),
                    )
                }
                blockedBySprintName?.let { blocker ->
                    ttText(
                        text = stringResource(Res.string.sprint_editor_overlap_field, blocker),
                        style = ttTypography.footnote,
                        color = ttError(),
                        modifier = Modifier.testTag(SprintEditorOverlapErrorTag),
                    )
                }
            }
            WorkItemAssigneeRow(
                assigneeID = ownerID,
                onAssigneeChanged = onOwnerChanged,
                searchMembers = searchMembers,
                resolveSelected = resolveSelected,
                title = stringResource(Res.string.sprint_owner_label),
                testTag = SprintEditorOwnerTag,
            )
            errorMessage?.let { message ->
                ttBanner(
                    message = message,
                    onDismiss = onDismissError,
                    modifier = Modifier.testTag(SprintEditorErrorTag),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = onCancel,
                    modifier = Modifier.testTag(SprintEditorCancelTag),
                )
                ttGlassButton(
                    label = stringResource(Res.string.common_save),
                    onClick = onSave,
                    enabled = canSave && !isBusy,
                    modifier = Modifier.testTag(SprintEditorSaveTag),
                    style = TTGlassButtonStyle(prominent = true),
                )
            }
        }
    }
}

const val SprintEditorSheetTag = "sprints.editor"
const val SprintEditorNameTag = "sprints.editor.name"
const val SprintEditorGoalTag = "sprints.editor.goal"
const val SprintEditorGoalPointsTag = "sprints.editor.goalPoints"
const val SprintEditorLockedNoteTag = "sprints.editor.lockedNote"
const val SprintEditorStartDateTag = "sprints.editor.start"
const val SprintEditorEndDateTag = "sprints.editor.end"
const val SprintEditorWindowErrorTag = "sprints.editor.windowError"
const val SprintEditorOverlapErrorTag = "sprints.editor.overlapError"
const val SprintEditorErrorTag = "sprints.editor.error"
const val SprintEditorOwnerTag = "sprints.editor.owner"
const val SprintEditorSaveTag = "sprints.editor.save"
const val SprintEditorCancelTag = "sprints.editor.cancel"