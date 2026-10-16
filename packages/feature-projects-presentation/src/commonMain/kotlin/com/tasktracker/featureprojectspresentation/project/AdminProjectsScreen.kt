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
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomUserID
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.Project
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.resources.Res
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun AdminProjectsScreen(
    port: AdminProjectsPort,
    onProjectSelected: (ProjectID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf(ProjectAdminStatus.Active) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
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
            selected = status,
            options = ProjectAdminStatus.entries,
            label = stringResource(Res.string.admin_project_scope),
        ) { next ->
            status = next
        }

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
                    text = stringResource(Res.string.admin_projects_empty, status.name.lowercase()),
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
                        key = { it.id.stringValue },
                    ) { project ->
                        ProjectRowView(
                            project = ProjectSummary(project, itemCount = 0),
                            onOpen = { onProjectSelected(project.id) },
                            onRemove = { /* admin project deletion is not in scope for this screen */ },
                            onEdit = { onProjectSelected(project.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A fake admin-projects port for host tests that needs one, modelled on the member-admin test
 * doubles: it records the calls a test can assert on and hands back whatever the test seeded.
 */
class RecordingAdminProjectsPort(
    private val byStatus: Map<ProjectAdminStatus, List<Project>> = emptyMap(),
    private val projectByID: Map<ProjectID, Project> = emptyMap(),
    private val updateResult: ((Project) -> Project)? = null,
) : AdminProjectsPort {
    val projectsCalls = mutableListOf<ProjectAdminStatus>()
    val projectCalls = mutableListOf<ProjectID>()
    val updateCalls = mutableListOf<Project>()

    override suspend fun projects(status: ProjectAdminStatus): List<Project> {
        projectsCalls += status
        return byStatus[status] ?: emptyList()
    }

    override suspend fun project(id: ProjectID): Project? {
        projectCalls += id
        return projectByID[id]
    }

    override suspend fun updateProject(project: Project): Project {
        updateCalls += project
        return updateResult?.invoke(project) ?: project
    }

    companion object {
        fun singleActiveProject(name: String = "Demo", isArchived: Boolean = false): RecordingAdminProjectsPort {
            val id = randomProjectID()
            val project = Project(
                id = id,
                keyPrefix = "DM",
                name = name,
                details = "",
                memberIDs = listOf(com.tasktracker.core.randomUserID()),
                isArchived = isArchived,
                nextItemNumber = 1,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
            return RecordingAdminProjectsPort(
                byStatus = mapOf(ProjectAdminStatus.Active to listOf(project)),
                projectByID = mapOf(id to project),
            )
        }

        private val createdAt = Instant.fromEpochSeconds(1_700_000_000)
    }
}
