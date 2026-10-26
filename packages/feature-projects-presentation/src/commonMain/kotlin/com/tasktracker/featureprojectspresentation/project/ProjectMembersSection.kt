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
 * Multi-select member assignment section for project editor forms.
 * Binds [MemberPicker] with project-specific labels and tags.
 */
@Composable
internal fun ProjectMembersSection(
    selectedMemberIDs: List<UserID>,
    onMemberIDsChanged: (List<UserID>) -> Unit,
    modifier: Modifier = Modifier,
    /** Searches unfiltered directory so prospective members can be found and selected. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /** Resolves selected members unfiltered; see [MemberPicker.resolveSelected]. */
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