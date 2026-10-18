package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus

/**
 * The editor's own fields must show the draft, not a constant.
 *
 * ## The defect these exist for
 *
 * The title field was bound to the surface's header string — "New Task" on a create, "Edit
 * TT-1" on an edit — instead of the draft. The model updated correctly on every keystroke,
 * so no model test could see it; the field simply kept rendering the header, which made the
 * editor look dead. The header and the field share a width and a word for word name only
 * by accident, and one shared parameter is exactly how they came to.
 *
 * The field is found by the tag `ProjectsComponents` derives from its own label, the same
 * way the project-creator's name field is.
 *
 * Each value is a `suspend ComposeUiTest.() -> Unit` so per-target entry points in
 * `ProjectsUiJvmTest`/`ProjectsUiIosTest`/`ProjectsUiAndroidHostTest` run an identical body.
 */
@OptIn(ExperimentalTestApi::class)
object WorkItemEditorScenarios {

    /**
     * Type into the title field; it must render what was typed.
     *
     * A green here means the draft reached the field. Before the fix it went red: the field
     * still read "New Task" after the input, because it rendered the header string.
     */
    val theTitleFieldShowsWhatYouType: suspend ComposeUiTest.() -> Unit = {
        setContent { editorSheet() }

        onNodeWithTag("title").performTextInput("Ship the migration")

        onNodeWithTag("title").assertTextEquals("Ship the migration")
    }

    /**
     * The create editor, with the title as real state the way the host wires it.
     *
     * Every other field is a fixed value: this scenario is about the draft reaching the
     * field, so the title is the only thing that moves.
     */
    @Composable
    private fun editorSheet() {
        var title by remember { mutableStateOf("") }
        ttTheme(ThemeScheme.Light) {
            WorkItemEditorSheet(
                title = title,
                details = "",
                storyPointsText = "",
                status = WorkItemStatus.Backlog,
                priority = Priority.Medium,
                assigneeID = null,
                currentUserID = null,
                currentUserName = null,
                otherAssigneeLabel = null,
                hasDueDate = false,
                recurrence = null,
                repeatAnchor = null,
                isCreating = true,
                issuedKey = null,
                selectableStatuses = emptyList(),
                isFiledAway = false,
                canSave = title.isNotBlank(),
                isBusy = false,
                errorMessage = null,
                onTitleChanged = { title = it },
                onDetailsChanged = {},
                onStoryPointsChanged = {},
                onStatusChanged = {},
                onPriorityChanged = {},
                onAssigneeChanged = {},
                onToggleDueDate = {},
                onRecurrenceChanged = {},
                onFileAway = {},
                onSave = {},
                onCancel = {},
                onDismissError = {},
            )
        }
    }
}
