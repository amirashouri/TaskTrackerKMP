package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.ProjectID

@Composable
fun AdminTwoPanes(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(PANE_GUTTER),
    ) {
        Box(modifier = Modifier.weight(PANE_WEIGHT).fillMaxHeight()) { listPane() }
        SprintPaneDivider()
        Box(modifier = Modifier.weight(PANE_WEIGHT).fillMaxHeight()) { detailPane() }
    }
}

@Composable
fun AdminMembersPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().testTag(AdminMembersListTag))
}

@Composable
fun AdminProjectsPlaceholder(
    projectID: ProjectID?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(
                if (projectID == null) {
                    AdminProjectsListTag
                } else {
                    AdminProjectDetailTag(projectID)
                },
            ),
    )
}

@Composable
fun AdminDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().testTag(AdminDetailPlaceholderTag))
}

/** The members section's list screen. */
const val AdminMembersListTag = "shell.admin.members"

/** The projects section's list screen. */
const val AdminProjectsListTag = "shell.admin.projects"

/** The projects section's detail screen for one project — the editor, once a project is selected. */
fun AdminProjectDetailTag(projectID: ProjectID): String =
    "shell.admin.project.${projectID.stringValue}"

/** The right pane of an admin section with nothing chosen. */
const val AdminDetailPlaceholderTag = "shell.admin.noSelection"
