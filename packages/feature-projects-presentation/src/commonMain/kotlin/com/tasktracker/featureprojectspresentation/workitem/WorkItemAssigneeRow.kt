package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.member_picker_search
import com.tasktracker.resources.work_item_assignee_label
import com.tasktracker.resources.work_item_assignee_unassigned
import org.jetbrains.compose.resources.stringResource

/**
 * Single-select assignee picker binding [MemberPicker] to a nullable [UserID] with an unassigned escape hatch.
 */
@Composable
fun WorkItemAssigneeRow(
    assigneeID: UserID?,
    onAssigneeChanged: (UserID?) -> Unit,
    modifier: Modifier = Modifier,
    /** Directory search callback. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /** Resolves selected user ID to a profile unfiltered. */
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    title: String = stringResource(Res.string.work_item_assignee_label),
    testTag: String = WorkItemAssigneePickerTag,
) {
    MemberPicker(
        title = title,
        selectedIDs = listOfNotNull(assigneeID),
        onSelectedIDsChanged = { onAssigneeChanged(it.firstOrNull()) },
        modifier = modifier,
        multiSelect = false,
        clearLabel = stringResource(Res.string.work_item_assignee_unassigned),
        placeholder = stringResource(Res.string.member_picker_search),
        searchMembers = searchMembers,
        resolveSelected = resolveSelected,
        tagPrefix = testTag,
    )
}

const val WorkItemAssigneePickerTag = "workItem.editor.assigneePicker"