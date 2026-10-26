package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.ttAvatarDescription
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttInitialsStack
import com.tasktracker.core.UserID
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_assignees_description
import org.jetbrains.compose.resources.pluralStringResource

/** Overlapping initials stack representing active assignees on the board header. */
@Composable
internal fun AssigneeStack(
    names: List<String>,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return

    ttInitialsStack(
        initials = names.map { ttInitialsFor(it) },
        modifier = modifier.ttAvatarDescription(
            pluralStringResource(
                Res.plurals.board_assignees_description,
                names.size,
                names.size,
                names.joinToString(),
            ),
        ),
    )
}

/** Extracts, resolves, filters, and case-insensitively sorts unique assignee names from [items]. */
internal fun boardAssigneeNames(
    items: List<WorkItem>,
    names: Map<UserID, String> = emptyMap(),
): List<String> = items
    .mapNotNull { it.assigneeID }
    .mapNotNull { names[it] }
    .filter { it.isNotBlank() }
    .map { it.trim() }
    .distinct()
    .sortedBy { it.lowercase() }

const val ProjectBoardAssigneesTag = "board.assignees"