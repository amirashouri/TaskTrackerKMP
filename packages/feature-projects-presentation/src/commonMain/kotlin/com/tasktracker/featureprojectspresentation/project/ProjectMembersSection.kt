package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.member_picker_search
import com.tasktracker.resources.project_members_label
import org.jetbrains.compose.resources.stringResource

/**
 * Who is on this project: the searchable member picker, in multi-select mode.
 *
 * ## Why this is a picker now and was chips before
 *
 * The first version rendered one glass pill per member plus an "Add me" button — the shape
 * a small removable set wants, and the honest answer while the directory could only name
 * the signed-in user. The picker's directory can list and filter people, so the chips' job
 * (show who is selected) and the add button's job (offer who you could add) collapse into
 * one control: ticked rows are the selection, and every unticked row is an add. The
 * [MemberPicker] KDoc holds the layout reasoning; this file is the project-members
 * binding of it — multi-select, the members caption, this section's tag.
 *
 * There is no invite field here any more. Adding a member is the backend admin's job; the
 * picker chooses among the people the directory already knows.
 */
@Composable
internal fun ProjectMembersSection(
    selectedMemberIDs: List<UserID>,
    onMemberIDsChanged: (List<UserID>) -> Unit,
    modifier: Modifier = Modifier,
    /** The directory behind the field; see [com.tasktracker.featureteamsdomain.TeamPort.searchMembers]. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
) {
    MemberPicker(
        title = stringResource(Res.string.project_members_label),
        selectedIDs = selectedMemberIDs,
        onSelectedIDsChanged = onMemberIDsChanged,
        modifier = modifier,
        multiSelect = true,
        placeholder = stringResource(Res.string.member_picker_search),
        searchMembers = searchMembers,
        tagPrefix = ProjectMembersSectionTag,
    )
}

@Preview(name = "Members, dark", showBackground = true, widthDp = 360)
@Composable
private fun ProjectMembersSectionDarkPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectMembersSection(
        selectedMemberIDs = ViewPreviewData.previewMemberIDs,
        onMemberIDsChanged = {},
        searchMembers = ViewPreviewData.previewSearchMembers,
    )
}

@Preview(name = "Members, light", showBackground = true, widthDp = 360)
@Composable
private fun ProjectMembersSectionLightPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectMembersSection(
        selectedMemberIDs = ViewPreviewData.previewMemberIDs,
        onMemberIDsChanged = {},
        searchMembers = ViewPreviewData.previewSearchMembers,
    )
}

const val ProjectMembersSectionTag = "projects.editor.members"
