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
 * Bottom sheet container for [NewProjectForm] displayed over the board.
 * Intercepts Cancel and scrim dismissals with a confirmation popup if [hasUnsavedChanges] is true.
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
    /** Prompts confirmation on dismissal if true. */
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
    isCreating: Boolean = true,
    selectedMemberIDs: List<UserID> = emptyList(),
    onMemberIDsChanged: (List<UserID>) -> Unit = {},
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    errorMessage: String? = null,
) {
    var pendingDiscard by remember(visible) { mutableStateOf<(() -> Unit)?>(null) }

    val guardDismiss: (() -> Unit) -> Unit = { action ->
        if (hasUnsavedChanges) pendingDiscard = action else action()
    }

    TTBottomSheetScaffold(
        visible = visible,
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

const val NewProjectDiscardTag = "projects.newForm.discard"