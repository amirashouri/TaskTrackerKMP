package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
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
 * The sprint form: creates a sprint, or edits the one [isCreating] is false for.
 *
 * ## One form, two surfaces
 *
 * [presentation] decides only what the form is *drawn on*. The fields, their order, their
 * tags and the action row are one `Column` — `body` below — and both arms invoke that same
 * `Column`, so a pane and a sheet cannot drift into asking for different things. The wide
 * shell passes [SprintEditorPresentation.Pane] to open a new sprint in the right pane beside
 * the list, the way a sprint's detail already opens there; every other caller takes the
 * default and gets the modal it always had.
 *
 * A pane is not a sheet with the backdrop removed. It has no scrim to dismiss it, so Cancel
 * is the only way out, and it carries its own scroll and the pane's gutters. That is also why
 * its bottom inset is padding *inside* the scroll rather than on the scroll container — the
 * same distinction [SprintDetailScreen] draws, and for the same reason: padding on the
 * container shrinks the viewport and leaves a band the content can never reach.
 *
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
    presentation: SprintEditorPresentation = SprintEditorPresentation.Sheet,
) {
    val body: @Composable () -> Unit = {
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

    when (presentation) {
        SprintEditorPresentation.Sheet -> TTBottomSheetScaffold(
            visible = visible,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            onDismissed = onDismissed,
        ) { body() }

        // The pane's gutters and top inset are `SprintDetailScreen`'s, so a sprint's form and
        // a sprint's detail start on the same line and the pane does not shift under the user
        // when one replaces the other.
        SprintEditorPresentation.Pane -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ttSpacing.xxl,
                    end = ttSpacing.xxl,
                    top = ttSpacing.screenGutterLarge,
                    bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current,
                ),
        ) { body() }
    }
}

/**
 * What [SprintEditorSheet] draws its form on.
 *
 * [Sheet] is the modal: anchored and sliding on a compact window, a centred window on a wide
 * one, both chosen by `LocalSheetPresentation` inside [TTBottomSheetScaffold]. [Pane] is the
 * same form standing in the wide shell's right pane, with no scrim and no window — the shell
 * asks for it when the form belongs *beside* the list rather than over it.
 */
enum class SprintEditorPresentation {
    Sheet,
    Pane,
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