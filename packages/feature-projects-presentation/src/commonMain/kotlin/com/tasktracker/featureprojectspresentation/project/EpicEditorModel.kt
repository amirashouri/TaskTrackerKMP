package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.EpicID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Epic
import com.tasktracker.domain.EpicRules
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.featureprojectsdomain.CreateEpic
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.SetEpicArchived
import com.tasktracker.featureprojectsdomain.SetWorkItemEpic
import com.tasktracker.featureprojectsdomain.UpdateEpic
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The epic editor's draft.
 *
 * The progress and derived status are **not** here: they belong to the saved epic's subtasks
 * and are read by the screen from [subtasks]. On a create there is no epic yet, which is why
 * those two rows are absent from the form rather than showing an empty bar — an epic that does
 * not exist has no progress, and a zero bar would claim it had none yet.
 */
class EpicEditorModel(
    private val existing: Epic?,
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ProjectModel() {

    private val createEpic = CreateEpic(repository, dateProvider)
    private val updateEpic = UpdateEpic(repository, dateProvider)
    private val setEpicArchived = SetEpicArchived(repository, dateProvider)
    private val setWorkItemEpic = SetWorkItemEpic(repository, dateProvider)

    private val originalTitle: String = existing?.title.orEmpty()
    private val originalDetails: String = existing?.details.orEmpty()
    private val originalDueDate: LocalDate? = existing?.dueDate?.let { sprintLocalDate(it, timeZone) }
    private val originalProjectIDs: List<ProjectID> = existing?.projectIDs.orEmpty()

    var title: String by mutableStateOf(originalTitle)
        private set

    var details: String by mutableStateOf(originalDetails)
        private set

    var dueDate: LocalDate? by mutableStateOf(originalDueDate)
        private set

    var projectIDs: List<ProjectID> by mutableStateOf(originalProjectIDs)
        private set

    /** Every project on this device, including archived ones: an epic may span either. */
    var availableProjects: List<Project> by mutableStateOf(emptyList())
        private set

    /** The saved epic's subtasks, in board order — also its progress. */
    var subtasks: List<WorkItem> by mutableStateOf(emptyList())
        private set

    /** Items in the spanned projects that could join this epic, loaded when the picker opens. */
    var candidateItems: List<WorkItem> by mutableStateOf(emptyList())
        private set

    var savedEpicID: EpicID? by mutableStateOf(existing?.id)
        private set

    var savedEpic: Epic? by mutableStateOf(existing)
        private set

    val isCreating: Boolean get() = existing == null
    val isArchived: Boolean get() = savedEpic?.isArchived == true

    val hasChanges: Boolean
        get() = if (isCreating) {
            title.isNotBlank() || details.isNotBlank() || dueDate != null || projectIDs.isNotEmpty()
        } else {
            title.trim() != originalTitle ||
                details.trim() != originalDetails ||
                dueDate != originalDueDate ||
                projectIDs != originalProjectIDs
        }

    val canSave: Boolean
        get() = title.isNotBlank() &&
            projectIDs.distinct().size >= EpicRules.MIN_PROJECTS &&
            (isCreating || hasChanges)

    suspend fun load() {
        availableProjects = reporting { repository.projects(includeArchived = true) }.orEmpty()
        refreshSubtasks()
    }

    fun onTitleChanged(value: String) {
        title = value
    }

    fun onDetailsChanged(value: String) {
        details = value
    }

    fun onDueDateChanged(value: LocalDate?) {
        dueDate = value
    }

    fun onProjectIDsChanged(value: List<ProjectID>) {
        projectIDs = value.distinct()
    }

    /** Adds or removes one project from the draft, keeping the user's chosen order. */
    fun toggleProject(id: ProjectID) {
        projectIDs = if (id in projectIDs) projectIDs - id else projectIDs + id
    }

    /**
     * Creates or updates the epic. Returns true when the write committed.
     *
     * A project set that already names what is stored is not sent: `UpdateEpic` treats a null
     * set as "not mentioned", and re-sending an unchanged set would still cost every item in a
     * removed project a relink check for nothing.
     */
    suspend fun save(): Boolean {
        if (!canSave) return false
        val saved = reporting {
            if (isCreating) {
                createEpic(
                    projectIDs = projectIDs,
                    title = title,
                    details = details,
                    dueDate = dueDateInstant(),
                )
            } else {
                updateEpic(
                    id = requireNotNull(savedEpicID),
                    title = title.trim().takeIf { it != originalTitle },
                    details = details.trim().takeIf { it != originalDetails },
                    dueDate = if (dueDate != originalDueDate) {
                        Patch.Of(dueDateInstant())
                    } else {
                        Patch.Unmentioned
                    },
                    projectIDs = projectIDs.takeIf { it != originalProjectIDs },
                )
            }
        } ?: return false
        savedEpicID = saved.id
        savedEpic = saved
        refreshSubtasks()
        return true
    }

    /** Links [workItemID] to the saved epic, replacing any epic it was already in. */
    suspend fun link(workItemID: WorkItemID): Boolean {
        val epicID = savedEpicID ?: return false
        val changed = reporting { setWorkItemEpic(workItemID, Patch.Of(epicID)) } != null
        if (changed) refreshSubtasks()
        return changed
    }

    suspend fun unlink(workItemID: WorkItemID): Boolean {
        val changed = reporting { setWorkItemEpic(workItemID, Patch.Of(null)) } != null
        if (changed) refreshSubtasks()
        return changed
    }

    /** Archives or restores the epic, optionally taking its subtasks with it. */
    suspend fun setArchived(isArchived: Boolean, archiveSubtasks: Boolean): Boolean {
        val id = savedEpicID ?: return false
        val saved = reporting { setEpicArchived(id, isArchived, archiveSubtasks) } ?: return false
        savedEpic = saved
        refreshSubtasks()
        return true
    }

    /**
     * Items from the epic's projects that are not already linked here.
     *
     * Items linked to another epic are included on purpose: the product allows moving a task
     * between stories, and the link replaces the old one. Hiding them would make the picker
     * silently narrower than the rule it enforces.
     */
    suspend fun loadCandidates() {
        val epicID = savedEpicID ?: return
        val spanned = projectIDs.toSet()
        val loaded = reporting {
            spanned.flatMap { repository.workItems(it) }
                .filter { it.epicID != epicID }
                .sortedWith(compareBy({ it.projectID.stringValue }, { it.key.rawValue }))
        }.orEmpty()
        candidateItems = loaded
    }

    private suspend fun refreshSubtasks() {
        val id = savedEpicID ?: return
        subtasks = reporting { repository.workItemsForEpic(id) }.orEmpty()
    }

    private fun dueDateInstant(): Instant? = dueDate?.let { sprintInstant(it, timeZone) }
}
