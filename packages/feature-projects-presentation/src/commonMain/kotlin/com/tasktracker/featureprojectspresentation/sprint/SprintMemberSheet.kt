package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTextField
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.sprint_member_available_label
import com.tasktracker.resources.sprint_member_available_placeholder
import com.tasktracker.resources.sprint_member_invalid_points
import com.tasktracker.resources.sprint_member_picker_placeholder
import com.tasktracker.resources.sprint_member_picker_title
import com.tasktracker.resources.sprint_member_remove
import com.tasktracker.resources.sprint_member_remove_confirm
import com.tasktracker.resources.sprint_member_remove_confirm_body
import com.tasktracker.resources.sprint_member_sheet_edit_title
import com.tasktracker.resources.sprint_member_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * Adds a person to the sprint roster with the capacity they offered for this window.
 *
 * The member cannot be changed while editing — identity is the row's primary key — so the
 * picker is read-only in edit mode. Both modes share the capacity field and the same save
 * path; removal is confirmed here because it is destructive and cannot be undone locally.
 */
@Composable
fun SprintMemberSheet(
    visible: Boolean,
    isEditing: Boolean,
    selectedUserID: UserID?,
    availablePoints: String,
    isBusy: Boolean,
    onSelectedUserIDChanged: (UserID?) -> Unit,
    onAvailablePointsChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: (() -> Unit)?,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    editingName: String? = null,
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
) {
    var confirmRemove by remember(visible) { mutableStateOf(false) }
    val capacity = availablePoints.toIntOrNull()
    val capacityIsValid = capacity != null && capacity >= 0
    val canSave = !isBusy && capacityIsValid && (isEditing || selectedUserID != null)

    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SprintMemberSheetTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
        ) {
            ttText(
                text = stringResource(
                    if (isEditing) Res.string.sprint_member_sheet_edit_title
                    else Res.string.sprint_member_sheet_title,
                ),
                style = ttTypography.title,
                color = ttLabel(),
            )

            MemberPicker(
                title = stringResource(Res.string.sprint_member_picker_title),
                selectedIDs = selectedUserID?.let(::listOf) ?: emptyList(),
                onSelectedIDsChanged = { ids -> onSelectedUserIDChanged(ids.firstOrNull()) },
                multiSelect = false,
                placeholder = stringResource(Res.string.sprint_member_picker_placeholder),
                searchMembers = searchMembers,
                resolveSelected = resolveSelected,
                tagPrefix = SPRINT_MEMBER_PICKER_TAG_PREFIX,
            )

            ttText(
                text = stringResource(Res.string.sprint_member_available_label),
                style = ttTypography.callout,
                color = ttLabel(),
            )
            ttTextField(
                value = availablePoints,
                onValueChange = onAvailablePointsChanged,
                hint = stringResource(Res.string.sprint_member_available_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag(SprintMemberCapacityTag),
            )
            if (!capacityIsValid && availablePoints.isNotBlank()) {
                ttText(
                    text = stringResource(Res.string.sprint_member_invalid_points),
                    style = ttTypography.footnote,
                    color = ttError(),
                    modifier = Modifier.testTag(SprintMemberInvalidPointsTag),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                if (onRemove != null) {
                    ttGlassButton(
                        label = stringResource(
                            Res.string.sprint_member_remove,
                            editingName.orEmpty(),
                        ),
                        onClick = { confirmRemove = true },
                        enabled = !isBusy,
                        style = TTGlassButtonStyle(tint = ttError()),
                        modifier = Modifier.testTag(SprintMemberRemoveTag),
                    )
                }
                ttGlassButton(
                    label = stringResource(Res.string.common_save),
                    onClick = onSave,
                    enabled = canSave,
                    style = TTGlassButtonStyle(prominent = true),
                    modifier = Modifier.testTag(SprintMemberSaveTag)
                        .weight(1f),
                )
            }
        }
    }

    if (confirmRemove && onRemove != null) {
        ConfirmPopup(
            title = stringResource(
                Res.string.sprint_member_remove_confirm,
                editingName.orEmpty(),
            ),
            body = stringResource(Res.string.sprint_member_remove_confirm_body),
            cancelLabel = stringResource(Res.string.common_cancel),
            confirmLabel = stringResource(Res.string.sprint_member_remove),
            onCancel = { confirmRemove = false },
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            confirmTint = ttError(),
            tag = SprintMemberRemoveConfirmationTag,
        )
    }
}

private const val SPRINT_MEMBER_PICKER_TAG_PREFIX = "sprints.member.picker"

const val SprintMemberSheetTag = "sprints.member.sheet"
const val SprintMemberCapacityTag = "sprints.member.capacity"
const val SprintMemberInvalidPointsTag = "sprints.member.invalidPoints"
const val SprintMemberSaveTag = "sprints.member.save"
const val SprintMemberRemoveTag = "sprints.member.remove"
const val SprintMemberRemoveConfirmationTag = "sprints.member.removeConfirm"
