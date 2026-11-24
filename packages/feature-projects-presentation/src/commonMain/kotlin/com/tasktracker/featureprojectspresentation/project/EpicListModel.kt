package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.EpicID
import com.tasktracker.domain.Epic
import com.tasktracker.domain.EpicStatus
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.deriveEpicStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.SetEpicArchived

/**
 * Which half of the Projects tab is showing.
 *
 * A preference rather than screen state: a `remember` in the destination dies on a tab switch,
 * and the user who was reading epics does not expect to come back to the project list. The
 * wire values are the stored form, so a rename of a Kotlin case cannot silently reset the
 * choice on every device.
 */
enum class ProjectsSegment(val wireValue: String) {
    Projects("projects"),
    Epics("epics"),
    ;

    companion object {
        fun fromWireValue(value: String?): ProjectsSegment =
            entries.firstOrNull { it.wireValue == value } ?: Projects
    }
}

/**
 * One epic, with the two things its card derives: the status and the progress.
 *
 * Both are computed from [subtasks] and never stored — see [deriveEpicStatus]. [projectNames]
 * is resolved from the ids the epic carries; an epic may name a project this device cannot
 * see, and the chip set is what arrived rather than a claim about the whole set.
 */
data class EpicListRow(
    val epic: Epic,
    val projectNames: List<String>,
    val subtasks: List<WorkItem>,
) {
    val doneCount: Int get() = subtasks.count { it.status.isTerminal }
    val totalCount: Int get() = subtasks.size
    val unfinishedCount: Int get() = totalCount - doneCount
    val status: EpicStatus get() = deriveEpicStatus(subtasks)
}

/**
 * The Epic segment's list.
 *
 * It listens to work-item changes as well as epic ones, because a subtask moving is the only
 * thing that can change an epic's derived status and progress: a model that woke on
 * [ProjectChange.Kind.Epics] alone would leave a card saying "2 of 8 done" after the eighth
 * item was finished. Project changes are watched too, so a renamed project updates its chips.
 */
class EpicListModel(
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ObservingProjectModel(repository) {

    private val setEpicArchived = SetEpicArchived(repository, dateProvider)

    var rows: List<EpicListRow> by mutableStateOf(emptyList())
        private set

    override fun isRelevant(change: ProjectChange): Boolean = when (change.kind) {
        ProjectChange.Kind.Epics,
        ProjectChange.Kind.WorkItems,
        ProjectChange.Kind.Projects,
        -> true
        ProjectChange.Kind.Sprints -> false
    }

    override suspend fun reload() {
        val projectsByID = repository.projects(includeArchived = true).associateBy { it.id }
        rows = repository.epics().map { epic ->
            EpicListRow(
                epic = epic,
                projectNames = epic.projectIDs.mapNotNull { projectsByID[it]?.name },
                subtasks = repository.workItemsForEpic(epic.id),
            )
        }
    }

    /** Archives [epicID], optionally filing its subtasks with it. Returns true on change. */
    suspend fun archive(epicID: EpicID, archiveSubtasks: Boolean): Boolean =
        reporting { setEpicArchived(epicID, isArchived = true, archiveSubtasks = archiveSubtasks) } != null

    suspend fun unarchive(epicID: EpicID, archiveSubtasks: Boolean): Boolean =
        reporting { setEpicArchived(epicID, isArchived = false, archiveSubtasks = archiveSubtasks) } != null

    suspend fun epic(id: EpicID): Epic? = repository.epic(id)
}
