package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
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
 * this for" is one interaction everywhere it appears. The directory behind it is a fake
 * until the backend member API lands — see [com.tasktracker.featureteamsdata.FakeMemberDirectory]
 * — and a member the directory cannot name still shows as their raw id.
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
        tagPrefix = testTag,
    )
}

/** The picker's test tag, so a test asserts on the picker rather than on its copy. */
const val WorkItemAssigneePickerTag = "workItem.editor.assigneePicker"

@Preview(name = "Assignee picker", showBackground = true)
@Composable
private fun WorkItemAssigneeRowPreview() {
    ttTheme(ThemeScheme.Dark) {
        WorkItemAssigneeRow(
            assigneeID = null,
            onAssigneeChanged = {},
        )
    }
}

@Preview(name = "Assignee picker — me", showBackground = true)
@Composable
private fun WorkItemAssigneeRowMePreview() {
    ttTheme(ThemeScheme.Dark) {
        WorkItemAssigneeRow(
            assigneeID = ViewPreviewData.previewUserID,
            onAssigneeChanged = {},
        )
    }
}
