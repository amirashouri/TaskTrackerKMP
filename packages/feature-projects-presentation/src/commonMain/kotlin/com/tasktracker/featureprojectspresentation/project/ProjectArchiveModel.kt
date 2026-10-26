package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.ArchiveProjection
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.datetime.TimeZone

/**
 * Presentation model for finished project work, grouped by completion month using [timeZone].
 * Read-only: displays terminal items without editing or reopening actions.
 */
class ProjectArchiveModel(
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    @Suppress("unused") private val dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    var projection: ArchiveProjection by mutableStateOf(
        ArchiveProjection(project = null, items = emptyList(), timeZone = timeZone),
    )
        private set

    /** True if all items in the project are terminal (evaluates all project tasks, not just archived months). */
    val allItemsAreFiledAway: Boolean
        get() = projection.items.all { it.status.isTerminal }

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        projection = ArchiveProjection(
            project = repository.project(projectID),
            items = repository.workItems(projectID),
            timeZone = timeZone,
        )
    }
}