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
import com.tasktracker.core.AppError
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
import kotlin.coroutines.cancellation.CancellationException
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
    var failure by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(status) {
        isLoading = true
        failure = null
        projects = try {
            port.projects(status)
        } catch (cancelled: CancellationException) {
            // Leaving the screen cancels this effect; that is not a load that failed, and
            // holding it as one both leaks a stale error onto the next status and stops the
            // cancellation propagating.
            throw cancelled
        } catch (error: AppError) {
            failure = error
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

        // Held as the AppError rather than as a sentence, and read through this local: `AppError`
        // owns the wording of every failure in the app, so re-wording it here would make the same
        // error read two ways. The local is what lets the `when` below smart-cast it.
        val error = failure
        when {
            isLoading -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_loading),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            error != null -> {
                ttText(
                    text = error.message.orEmpty(),
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
