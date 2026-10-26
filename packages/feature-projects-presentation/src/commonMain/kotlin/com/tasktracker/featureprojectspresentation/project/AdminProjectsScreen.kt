package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTGlassBackButton
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassIconButton
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
import com.tasktracker.resources.admin_projects_title
import com.tasktracker.resources.project_new
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.compose.resources.stringResource

/**
 * Admin screen displaying server-wide projects segmented by [ProjectAdminStatus] (Active/Archived).
 * Direct online queries via [AdminProjectsPort].
 */
@Composable
fun AdminProjectsScreen(
    port: AdminProjectsPort,
    onProjectSelected: (ProjectID) -> Unit,
    onBack: () -> Unit,
    onOpenAddForm: () -> Unit,
    modifier: Modifier = Modifier,
    showsAddAction: Boolean = true,
    /** Trigger key to re-fetch projects from server (preserves newest-first ordering). */
    refreshKey: Int = 0,
) {
    var status by remember { mutableStateOf(ProjectAdminStatus.Active) }
    var projects by remember { mutableStateOf<List<AdminProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(status, refreshKey) {
        isLoading = true
        failure = null
        projects = try {
            port.projects(status)
        } catch (cancelled: CancellationException) {
            // Re-throw to allow coroutine cancellation to propagate cleanly
            throw cancelled
        } catch (error: AppError) {
            failure = error
            emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(AdminProjectsScreenTag)
            .padding(ttSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        AdminProjectsHeader(
            onBack = onBack,
            onOpenAddForm = onOpenAddForm,
            showsAddAction = showsAddAction,
        )

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

@Composable
private fun AdminProjectsHeader(
    onBack: () -> Unit,
    onOpenAddForm: () -> Unit,
    showsAddAction: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        TTGlassBackButton(onBack = onBack)
        ttText(
            text = stringResource(Res.string.admin_projects_title),
            style = ttTypography.titleSmall,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        if (showsAddAction) {
            ttGlassIconButton(
                image = ttActionIcons.add,
                contentDescription = stringResource(Res.string.project_new),
                onClick = onOpenAddForm,
                modifier = Modifier.testTag(AdminProjectsAddTag),
            )
        }
    }
}

const val AdminProjectsScreenTag = "adminProjects.screen"
const val AdminProjectsAddTag = "adminProjects.add"