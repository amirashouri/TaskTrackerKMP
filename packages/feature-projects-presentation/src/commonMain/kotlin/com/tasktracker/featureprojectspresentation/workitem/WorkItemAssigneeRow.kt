package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTGlassFlowPicker
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_assignee_label
import com.tasktracker.resources.work_item_assignee_me
import com.tasktracker.resources.work_item_assignee_unassigned
import org.jetbrains.compose.resources.stringResource

/**
 * Who this task belongs to, as a picker rather than a text field.
 *
 * Identity is a [UserID] end to end: the old free-text field stored a display name, which
 * cannot answer "is this me" without fuzzy matching, and the board's "My Tasks" filter
 * was wrong for two people who share a first name. Names are presentation's problem —
 * resolved here as labels and on cards through the directory — while the model and the
 * store only ever hold the id.
 *
 * The options are unassigned, the signed-in user, and (when the item is assigned to
 * somebody the directory cannot yet name) that person by id. Offering only "me" is
 * honest about what the directory knows today: a member list arrives with sync, and this
 * same row gains real members without the model changing.
 */
@Composable
fun WorkItemAssigneeRow(
    assigneeID: UserID?,
    currentUserID: UserID?,
    currentUserName: String?,
    /** Label for [assigneeID] when it is neither `null` nor [currentUserID]. */
    otherAssigneeLabel: String?,
    onAssigneeChanged: (UserID?) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The field's own caption. Defaults to the work item's; the sprint editor's owner
     * field is the same picker wearing the sprint's label and test tag, because "who is
     * this for" is one interaction with two names — see [SprintEditorSheet].
     */
    title: String = stringResource(Res.string.work_item_assignee_label),
    testTag: String = WorkItemAssigneePickerTag,
) {
    val unassignedLabel = stringResource(Res.string.work_item_assignee_unassigned)
    val meLabel = currentUserName ?: stringResource(Res.string.work_item_assignee_me)

    val options = buildList {
        add(AssigneeChoice.Unassigned)
        currentUserID?.let { add(AssigneeChoice.User(it, meLabel)) }
        if (assigneeID != null && assigneeID != currentUserID) {
            add(AssigneeChoice.User(assigneeID, otherAssigneeLabel ?: assigneeID.stringValue))
        }
    }
    val selection = when {
        assigneeID == null -> AssigneeChoice.Unassigned
        else -> options.filterIsInstance<AssigneeChoice.User>().firstOrNull { it.id == assigneeID }
            ?: AssigneeChoice.Unassigned
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = title,
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
        )
        TTGlassFlowPicker(
            options = options,
            selection = selection,
            onSelect = { choice ->
                onAssigneeChanged((choice as? AssigneeChoice.User)?.id)
            },
            title = { choice ->
                when (choice) {
                    AssigneeChoice.Unassigned -> unassignedLabel
                    is AssigneeChoice.User -> choice.label
                }
            },
            modifier = Modifier.testTag(testTag),
        )
    }
}

/** One selectable assignment: nobody, or a person the directory can point at. */
private sealed interface AssigneeChoice {
    data object Unassigned : AssigneeChoice
    data class User(val id: UserID, val label: String) : AssigneeChoice
}

/** The picker's test tag, so a test asserts on the picker rather than on its copy. */
const val WorkItemAssigneePickerTag = "workItem.editor.assigneePicker"

@Preview(name = "Assignee picker", showBackground = true)
@Composable
private fun WorkItemAssigneeRowPreview() {
    ttTheme(ThemeScheme.Dark) {
        WorkItemAssigneeRow(
            assigneeID = null,
            currentUserID = null,
            currentUserName = null,
            otherAssigneeLabel = null,
            onAssigneeChanged = {},
        )
    }
}

@Preview(name = "Assignee picker — me", showBackground = true)
@Composable
private fun WorkItemAssigneeRowMePreview() {
    ttTheme(ThemeScheme.Dark) {
        WorkItemAssigneeRow(
            assigneeID = UserID("11111111-1111-1111-1111-111111111111"),
            currentUserID = UserID("11111111-1111-1111-1111-111111111111"),
            currentUserName = "Amir",
            otherAssigneeLabel = null,
            onAssigneeChanged = {},
        )
    }
}
