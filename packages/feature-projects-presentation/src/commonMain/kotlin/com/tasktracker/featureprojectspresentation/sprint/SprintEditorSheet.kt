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
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTDateField
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.core.UserID
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
 * The create-and-edit form, as a sheet.
 *
 * ## One sheet for both modes
 *
 * Creating a sprint and editing one are the same four fields, and a user who has just
 * created a sprint is usually straight into changing its window — so they are one
 * composable with a mode flag, the way [NewProjectSheet] is. The title and the confirm
 * label follow the mode, for the same reason [ProjectEditorScreen] does it that way: a
 * user who cannot tell which form is open will save an edit expecting a new sprint.
 *
 * ## Two goal fields, and one of them freezes
 *
 * The number ([SprintEditorModel.goalStoryPoints], the cap) and the sentence
 * ([SprintEditorModel.goal]) are separate inputs because they are separate facts, and they
 * diverge when the owner locks the sprint: `windowLocked` disables the number and both
 * dates, dims them with the same alpha every disabled glass control uses, and posts the
 * note under the title saying why. Name, sentence and owner stay live — they describe the
 * sprint rather than commit it. The sheet is handed that one flag rather than each field
 * deciding for itself, so the disabled set cannot drift from
 * [SprintEditorModel.windowLocked].
 *
 * ## The dates are two `TTDateField`s and both errors are under them
 *
 * The window rules ([SprintEditorModel.endIsBeforeStart] and
 * [SprintEditorModel.blockedBySprintName]) are shown against the **end** field rather than
 * as a banner. A banner says something went wrong; a message under the field says which
 * field is wrong, and these are the rules in the form about the relationship between the
 * window and something outside it — the start field, or an existing sprint — rather than
 * about the field alone.
 *
 * The overlap note is rendered only when the window is otherwise well-formed, because
 * [SprintEditorModel.blockedBySprintName] is `null` for a backwards window: showing both
 * under one field would be two complaints about one draft.
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
    /**
     * Whether the window and the point cap are frozen. The two fields answer to it, the
     * note under the title names it, and the prose goal and the text fields deliberately
     * do not — see [SprintEditorModel.windowLocked].
     */
    windowLocked: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
    today: LocalDate? = null,
    /** The directory behind the owner picker; see [com.tasktracker.featureteamsdomain.TeamPort.searchMembers]. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /** Resolves the selected owner, unfiltered; see [MemberPicker.resolveSelected]. */
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    /**
     * A refusal that came from the store rather than the form's local checks. The form
     * stays advisory; a race (another device creating a sprint after the sibling read) is
     * authoritative, and it arrives as this banner so the user knows why Save did nothing.
     * Rendered above the action row because the sheet scrolls and the buttons are the
     * targets the user just pressed.
     */
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
            // Beside the title rather than under any one field, because it is a statement
            // about the form as a whole: it says which half of the form has stopped
            // accepting input, not that one of its fields is wrong.
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
                // Under the field, not in a banner: this is the only rule in the form about
                // the relation between two inputs, so it belongs beside the one the user has
                // to change. A banner would say *what* is wrong and not *which field*.
                if (endIsBeforeStart == true) {
                    ttText(
                        text = stringResource(Res.string.sprint_end_before_start_field),
                        style = ttTypography.footnote,
                        color = ttError(),
                        modifier = Modifier.testTag(SprintEditorWindowErrorTag),
                    )
                }
                // Mutually exclusive with the note above in practice — the model reports
                // no blocker for a backwards window — but the two are drawn independently
                // so the screen needs no rule about which error wins.
                blockedBySprintName?.let { blocker ->
                    ttText(
                        text = stringResource(Res.string.sprint_editor_overlap_field, blocker),
                        style = ttTypography.footnote,
                        color = ttError(),
                        modifier = Modifier.testTag(SprintEditorOverlapErrorTag),
                    )
                }
            }
            // The same picker the work item editor's assignee field uses: ownership is an
            // id, and the searchable directory is the whole of what the picker offers.
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

/** The editor sheet. */
const val SprintEditorSheetTag = "sprints.editor"
const val SprintEditorNameTag = "sprints.editor.name"
/** The goal in prose. The number has [SprintEditorGoalPointsTag]; see `Sprint`'s two goals. */
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

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

// Pinned schemes, not `ThemeScheme.System`, and fixtures from `ViewPreviewData` rather than
// `seedSampleDataIfNeeded` — see `ProjectsPreview`'s KDoc and 23.8.

@Preview(name = "Sprint editor, new", showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun SprintEditorSheetPreview() = ProjectsPreview(ThemeScheme.Light) {
    SprintEditorSheet(
        name = "Sprint 8",
        goalStoryPoints = 13,
        goal = "Ship the migration to the new store",
        startDate = LocalDate(2026, 3, 30),
        endDate = LocalDate(2026, 4, 10),
        ownerID = ViewPreviewData.previewUserID,
        isCreating = true,
        canSave = true,
        endIsBeforeStart = false,
        blockedBySprintName = null,
        onNameChanged = {},
        onGoalStoryPointsChanged = {},
        onGoalChanged = {},
        onStartDateChanged = {},
        onEndDateChanged = {},
        onOwnerChanged = {},
        onSave = {},
        onCancel = {},
        today = LocalDate(2026, 3, 16),
    )
}

@Preview(name = "Sprint editor, window backwards", showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun SprintEditorSheetInvalidPreview() = ProjectsPreview(ThemeScheme.Dark) {
    SprintEditorSheet(
        name = "Sprint 8",
        goalStoryPoints = 13,
        goal = "",
        startDate = LocalDate(2026, 4, 10),
        endDate = LocalDate(2026, 4, 3),
        ownerID = null,
        isCreating = true,
        canSave = false,
        // The third state the rule has: a chosen end that is before a chosen start.
        endIsBeforeStart = true,
        blockedBySprintName = null,
        onNameChanged = {},
        onGoalStoryPointsChanged = {},
        onGoalChanged = {},
        onStartDateChanged = {},
        onEndDateChanged = {},
        onOwnerChanged = {},
        onSave = {},
        onCancel = {},
        today = LocalDate(2026, 3, 16),
    )
}
