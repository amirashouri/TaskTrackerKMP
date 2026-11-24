package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.epic_owner_label
import com.tasktracker.resources.member_picker_search
import org.jetbrains.compose.resources.stringResource

/**
 * Single-select owner control for the epic editor.
 *
 * [MemberPicker] offers an "unassigned" escape hatch for a work item's assignee. An epic
 * always has one — the server gives a newly pushed epic the admin as its owner — so an empty
 * selection is ignored here rather than offering a choice the domain refuses.
 */
@Composable
fun EpicOwnerPicker(
    ownerID: UserID?,
    onOwnerChanged: (UserID) -> Unit,
    modifier: Modifier = Modifier,
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    tag: String = EpicOwnerPickerTag,
) {
    MemberPicker(
        title = stringResource(Res.string.epic_owner_label),
        selectedIDs = listOfNotNull(ownerID),
        onSelectedIDsChanged = { ids -> ids.firstOrNull()?.let(onOwnerChanged) },
        modifier = modifier,
        multiSelect = false,
        clearLabel = null,
        placeholder = stringResource(Res.string.member_picker_search),
        searchMembers = searchMembers,
        resolveSelected = resolveSelected,
        tagPrefix = tag,
    )
}

const val EpicOwnerPickerTag = "projects.epic.ownerPicker"
