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
 * Who this task belongs to, as a searchable picker rather than a text field.
 *
 * Identity is a [UserID] end to end: the old free-text field stored a display name, which
 * cannot answer "is this me" without fuzzy matching, and the board's "My Tasks" filter
 * was wrong for two people who share a first name. Names are presentation's problem —
 * resolved here as labels and on cards through the directory — while the model and the
 * store only ever hold the id.
 *
 * The old row offered exactly two choices, Unassigned and the signed-in user, because that
 * was honestly all the directory knew. [MemberPicker] replaces it: the same field the
 * project editor's members use, in single-select mode with an Unassigned escape, so "who is
 * this for" is one interaction everywhere it appears. The directory behind it is the server's
 * live account list when a backend is bound, and a member the directory cannot name still
 * shows as their raw id.
 *
 * The sprint editor's owner field is this same row wearing the sprint's label and test tag,
 * because "who is this for" is one interaction with two names — see [SprintEditorSheet].
 */
@Composable
fun WorkItemAssigneeRow(
    assigneeID: UserID?,
    onAssigneeChanged: (UserID?) -> Unit,
    modifier: Modifier = Modifier,
    /** The directory search behind the field; see [com.tasktracker.featureteamsdomain.TeamPort.searchMembers]. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /**
     * Resolves the selected id to a profile, unfiltered — see [MemberPicker.resolveSelected].
     * Defaults to the blank-query directory listing, which is correct wherever
     * [searchMembers] is not scoped to a project's members.
     */
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

/** The picker's test tag, so a test asserts on the picker rather than on its copy. */
const val WorkItemAssigneePickerTag = "workItem.editor.assigneePicker"
