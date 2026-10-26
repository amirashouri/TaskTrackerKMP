package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRemovalOutcome
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.featureprojectsdomain.RemoveProject

/** Row representation carrying pre-computed [removalOutcome] for delete/archive confirmation dialogs. */
data class ProjectRow(
    val summary: ProjectSummary,
    /** Non-Done tasks that will cause removal to fall back to archive rather than deletion. */
    val outstandingCount: Int,
) {
    val removalOutcome: ProjectRemovalOutcome
        get() = if (outstandingCount > 0) {
            ProjectRemovalOutcome.Archived
        } else {
            ProjectRemovalOutcome.Deleted
        }
}

/** Result of a project removal operation, mapped to localized copy in the view layer. */
sealed interface RemovalNotice {
    data class ArchivedInstead(
        val projectName: String,
        val outstandingCount: Int,
    ) : RemovalNotice

    data class AlreadyArchived(val projectName: String) : RemovalNotice
    data class Deleted(val projectName: String) : RemovalNotice
}

/**
 * Presentation model for the active projects list.
 * Automatically seeds sample data on initial load and coordinates project removal/archiving.
 */
class ProjectsListModel(
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) : ObservingProjectModel(repository) {

    private val removeProject = RemoveProject(repository, dateProvider)

    var rows: List<ProjectRow> by mutableStateOf(emptyList())
        private set

    var notice: RemovalNotice? by mutableStateOf(null)
        private set

    private var didSeed: Boolean = false

    // Observes all changes across the feature (not scoped to a single project)
    override fun isRelevant(change: ProjectChange): Boolean = true

    override suspend fun reload() {
        if (!didSeed) {
            repository.seedSampleDataIfNeeded()
            didSeed = true
        }

        val projects = repository.projects(includeArchived = false)
            .sortedWith(compareByDescending<Project> { it.updatedAt }.thenBy { it.name })

        this.rows = projects.map { project ->
            ProjectRow(
                summary = ProjectSummary(project, itemCountOf(project.id)),
                outstandingCount = outstandingCountOf(project.id),
            )
        }
    }

    /** Removes [projectID], falling back to archive if unfinished tasks exist. Returns true on change. */
    suspend fun remove(projectID: ProjectID): Boolean {
        // Resolve project name before removal removes the row from the active list
        val row = rows.firstOrNull { it.summary.id == projectID }
        val name = row?.summary?.name
            ?: repository.project(projectID)?.name
            ?: ""

        val outcome = reporting { removeProject(projectID) } ?: return false
        notice = when (outcome) {
            ProjectRemovalOutcome.Archived -> RemovalNotice.ArchivedInstead(
                projectName = name,
                outstandingCount = row?.outstandingCount ?: outstandingCountOf(projectID),
            )
            ProjectRemovalOutcome.AlreadyArchived -> RemovalNotice.AlreadyArchived(name)
            ProjectRemovalOutcome.Deleted -> RemovalNotice.Deleted(name)
        }
        return outcome != ProjectRemovalOutcome.AlreadyArchived
    }

    private suspend fun outstandingCountOf(projectID: ProjectID): Int {
        val counts = repository.counts(projectID)
        return counts.values.sum() - (counts[WorkItemStatus.Done] ?: 0)
    }

    private suspend fun itemCountOf(projectID: ProjectID): Int = repository.counts(projectID).values.sum()

    fun dismissNotice() {
        notice = null
    }
}