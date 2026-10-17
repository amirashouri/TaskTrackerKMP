package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.featureteamsdomain.User

/**
 * [NewProjectForm] as the board opens it: a sheet over the board, which is still there underneath
 * when it closes.
 *
 * ## Why a sheet and not a screen
 *
 * A project is created from the board, and the board is where the user wants to be afterwards. A
 * full-screen form would mean a navigation round trip to do something that takes two fields.
 *
 * ## What this adds, and why it is only this
 *
 * The form itself is [NewProjectForm]; this composable is the container that makes it a sheet. The
 * split exists because a wide window draws the *same panel* in a pane beside the admin projects
 * list, where a sheet would slide over the list the form is working with. Two arrangements, one
 * form.
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
    /** The directory behind the member picker; see [com.tasktracker.featureteamsdomain.TeamPort.searchMembers]. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    errorMessage: String? = null,
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        NewProjectForm(
            name = name,
            keyPrefix = keyPrefix,
            details = details,
            canSave = canSave,
            onNameChanged = onNameChanged,
            onKeyPrefixChanged = onKeyPrefixChanged,
            onDetailsChanged = onDetailsChanged,
            onSave = onSave,
            onDismiss = onCancel,
            isBusy = isBusy,
            isCreating = isCreating,
            selectedMemberIDs = selectedMemberIDs,
            onMemberIDsChanged = onMemberIDsChanged,
            searchMembers = searchMembers,
            resolveSelected = resolveSelected,
            errorMessage = errorMessage,
        )
    }
}
