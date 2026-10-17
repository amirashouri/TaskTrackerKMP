package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_projects_empty
import com.tasktracker.resources.admin_projects_loading
import com.tasktracker.resources.admin_projects_segment_active
import com.tasktracker.resources.admin_projects_segment_archived
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdminProjectsScreen(
    port: AdminProjectsPort,
    onProjectSelected: (ProjectID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf(ProjectAdminStatus.Active) }
    var projects by remember { mutableStateOf<List<AdminProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status) {
        isLoading = true
        errorMessage = null
        projects = try {
            port.projects(status)
        } catch (e: Exception) {
            errorMessage = e.message ?: "could not load projects"
            emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(ttSpacing.lg)) {
        TTGlassSegmentedPicker(
            options = ProjectAdminStatus.entries,
            selection = status,
            onSelect = { next -> status = next },
            title = { option ->
                when (option) {
                    ProjectAdminStatus.Active ->
                        stringResource(Res.string.admin_projects_segment_active)
                    ProjectAdminStatus.Archived ->
                        stringResource(Res.string.admin_projects_segment_archived)
                }
            },
        )

        when {
            isLoading -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_loading),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            errorMessage != null -> {
                ttText(
                    text = errorMessage!!,
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            projects.isEmpty() -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_empty, status.wireValue),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = projects,
                        key = { adminProject -> adminProject.project.id.stringValue },
                    ) { adminProject ->
                        ProjectRowView(
                            project = ProjectSummary(adminProject.project, itemCount = 0),
                            onOpen = { onProjectSelected(adminProject.project.id) },
                            onRemove = { /* admin project deletion is not in scope for this screen */ },
                            onEdit = { onProjectSelected(adminProject.project.id) },
                        )
                    }
                }
            }
        }
    }
}
