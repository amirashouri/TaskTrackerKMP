package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
object WorkItemEditorScenarios {

    val expandButtonTogglesExpandedState: suspend ComposeUiTest.() -> Unit = {
        var expandedRequested = false
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                WorkItemEditorSheet(
                    title = "Fix crash on launch",
                    details = "Details here",
                    status = WorkItemStatus.InProgress,
                    priority = Priority.High,
                    assignee = "Amir",
                    hasDueDate = false,
                    isCreating = false,
                    issuedKey = null,
                    selectableStatuses = WorkItemStatus.boardOrder,
                    isFiledAway = false,
                    canSave = true,
                    isBusy = false,
                    errorMessage = null,
                    onTitleChanged = {},
                    onDetailsChanged = {},
                    onStatusChanged = {},
                    onPriorityChanged = {},
                    onAssigneeChanged = {},
                    onToggleDueDate = {},
                    onFileAway = {},
                    onSave = {},
                    onCancel = {},
                    onDismissError = {},
                    expanded = false,
                    onToggleExpanded = { expandedRequested = it },
                )
            }
        }

        onNodeWithTag(WorkItemEditorSheetTag).assertIsDisplayed()
        onNested(WorkItemExpandTag).assertIsDisplayed().performClick()
        assertTrue(expandedRequested, "Clicking expand button should request expanded = true")
    }

    val collapseButtonTogglesExpandedState: suspend ComposeUiTest.() -> Unit = {
        var expandedRequested = true
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                WorkItemEditorSheet(
                    title = "Fix crash on launch",
                    details = "Details here",
                    status = WorkItemStatus.InProgress,
                    priority = Priority.High,
                    assignee = "Amir",
                    hasDueDate = false,
                    isCreating = false,
                    issuedKey = null,
                    selectableStatuses = WorkItemStatus.boardOrder,
                    isFiledAway = false,
                    canSave = true,
                    isBusy = false,
                    errorMessage = null,
                    onTitleChanged = {},
                    onDetailsChanged = {},
                    onStatusChanged = {},
                    onPriorityChanged = {},
                    onAssigneeChanged = {},
                    onToggleDueDate = {},
                    onFileAway = {},
                    onSave = {},
                    onCancel = {},
                    onDismissError = {},
                    expanded = true,
                    onToggleExpanded = { expandedRequested = it },
                )
            }
        }

        onNodeWithTag(WorkItemEditorFullScreenTag).assertIsDisplayed()
        onNested(WorkItemCollapseTag).assertIsDisplayed().performClick()
        assertTrue(!expandedRequested, "Clicking collapse button should request expanded = false")
    }
}
