package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.core.UserID
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
    /**
     * The directory behind the field.
     *
     * Never scoped to this project's own members — this *is* the control that edits that
     * membership, so filtering it to its current value would make every new member
     * unreachable. [assignableMembersFor] is for the pickers that consume membership, not the
     * one that writes it.
     */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /** Resolves the selected members, unfiltered; see [MemberPicker.resolveSelected]. */
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
) {
    MemberPicker(
        title = stringResource(Res.string.project_members_label),
        selectedIDs = selectedMemberIDs,
        onSelectedIDsChanged = onMemberIDsChanged,
        modifier = modifier,
        multiSelect = true,
        placeholder = stringResource(Res.string.member_picker_search),
        searchMembers = searchMembers,
        resolveSelected = resolveSelected,
        tagPrefix = ProjectMembersSectionTag,
    )
}

const val ProjectMembersSectionTag = "projects.editor.members"
