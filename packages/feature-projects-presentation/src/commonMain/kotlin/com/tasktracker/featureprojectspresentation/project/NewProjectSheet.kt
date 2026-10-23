package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.ttError
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_discard
import com.tasktracker.resources.editor_discard_body
import com.tasktracker.resources.editor_discard_title
import org.jetbrains.compose.resources.stringResource

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
    /**
     * Whether closing now would throw away work.
     *
     * Cancel and the sheet's scrim dismissal share the guard: both end the draft, and a
     * prompt on one but not the other would let the accidental tap through.
     */
    hasUnsavedChanges: Boolean,
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
    // The action to run once the user confirms the discard, if a dismissal has been interrupted.
    // Kept as the action rather than a boolean so the pill matches how it was closed: Cancel and
    // the scrim are the same content loss but not the same callback. Keyed on `visible` so a
    // prompt interrupted by some other close cannot greet the next time the sheet opens.
    var pendingDiscard by remember(visible) { mutableStateOf<(() -> Unit)?>(null) }

    val guardDismiss: (() -> Unit) -> Unit = { action ->
        if (hasUnsavedChanges) pendingDiscard = action else action()
    }

    TTBottomSheetScaffold(
        visible = visible,
        // The scrim tap is the other way out of this sheet. Guarding only the Cancel button
        // would leave a second route to lose the draft without a question.
        onDismissRequest = { guardDismiss(onDismissRequest) },
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
            onDismiss = { guardDismiss(onCancel) },
            isBusy = isBusy,
            isCreating = isCreating,
            selectedMemberIDs = selectedMemberIDs,
            onMemberIDsChanged = onMemberIDsChanged,
            searchMembers = searchMembers,
            resolveSelected = resolveSelected,
            errorMessage = errorMessage,
        )
    }

    // Draws over the sheet, which stays composed behind it. A `visible = false` sheet is on its
    // way out, so a confirmation over it would be a prompt about a draft that is already gone.
    if (visible) {
        pendingDiscard?.let { discard ->
            ConfirmPopup(
                title = stringResource(Res.string.editor_discard_title),
                body = stringResource(Res.string.editor_discard_body),
                cancelLabel = stringResource(Res.string.common_cancel),
                confirmLabel = stringResource(Res.string.common_discard),
                onCancel = { pendingDiscard = null },
                onConfirm = {
                    pendingDiscard = null
                    discard()
                },
                confirmTint = ttError(),
                tag = NewProjectDiscardTag,
            )
        }
    }
}

/** The discard prompt drawn over the project sheet. */
const val NewProjectDiscardTag = "projects.newForm.discard"
