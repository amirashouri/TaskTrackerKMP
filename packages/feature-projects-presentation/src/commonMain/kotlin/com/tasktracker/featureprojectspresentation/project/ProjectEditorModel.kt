@file:OptIn(ExperimentalUuidApi::class)

package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Project
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.StatusRules
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.CreateProject
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.UpdateProject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Presentation model for creating or editing a project via offline-first repository.
 * Snapshots original values at construction so [hasChanges] reflects local user edits
 * without racing concurrent remote sync updates. Key prefixes are immutable after creation.
 */
class ProjectEditorModel(
    private val existing: Project?,
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) : ProjectModel() {

    private val createProject = CreateProject(repository, dateProvider)
    private val updateProject = UpdateProject(repository, dateProvider)

    private val originalName: String? = existing?.name
    private val originalDetails: String? = existing?.details
    private val originalMemberIDs: List<UserID> = existing?.memberIDs.orEmpty()
    private val originalStatuses: List<StatusDefinition> = existing?.statuses.orEmpty()
    private var isKeyPrefixEdited: Boolean = existing != null

    var name: String by mutableStateOf(existing?.name.orEmpty())
        private set

    var keyPrefix: String by mutableStateOf(existing?.keyPrefix.orEmpty())
        private set

    var details: String by mutableStateOf(existing?.details.orEmpty())
        private set

    var memberIDs: List<UserID> by mutableStateOf(originalMemberIDs)
        private set

    /** The project's admin-defined workflow status catalog, in creation order. */
    var statuses: List<StatusDefinition> by mutableStateOf(existing?.statuses.orEmpty())
        private set

    var savedProjectID: ProjectID? by mutableStateOf(existing?.id)
        private set

    val isCreating: Boolean get() = existing == null

    val isKeyPrefixEditable: Boolean get() = isCreating

    /** True if draft fields differ from original snapshot (ignoring whitespace). */
    val hasChanges: Boolean
        get() = if (isCreating) {
            name.isNotBlank() ||
                    keyPrefix.isNotBlank() ||
                    details.isNotBlank() ||
                    memberIDs != originalMemberIDs ||
                    statuses != originalStatuses
        } else {
            name.trim() != originalName || details.trim() != originalDetails || memberIDs != originalMemberIDs || statuses != originalStatuses
        }

    val canSave: Boolean
        get() = if (isCreating) {
            name.isNotBlank() && keyPrefix.isNotBlank()
        } else {
            hasChanges
        }

    fun onNameChanged(value: String) {
        name = value
        if (isCreating && !isKeyPrefixEdited) keyPrefix = suggestKeyPrefix(value)
    }

    fun onKeyPrefixChanged(value: String) {
        if (isKeyPrefixEditable) {
            keyPrefix = value
            isKeyPrefixEdited = true
        }
    }

    fun onDetailsChanged(value: String) {
        details = value
    }

    fun onMemberIDsChanged(value: List<UserID>) {
        memberIDs = value.distinct()
    }

    /** Replaces the whole status catalog draft (e.g. after a bulk reorder). */
    fun onStatusesChanged(value: List<StatusDefinition>) {
        statuses = value.distinctBy { it.id }
    }

    /** Validates against [StatusRules] and appends a status if name is unique (case-insensitive). */
    fun addStatus(name: String, category: WorkItemStatus, color: TagColor): Boolean {
        if (!StatusRules.isNameValid(name)) return false
        if (statuses.size >= StatusRules.MAX_PER_PROJECT) return false
        val normalised = StatusRules.normaliseName(name)
        if (statuses.any { it.name.equals(normalised, ignoreCase = true) }) return false
        statuses = (statuses + StatusDefinition(
            id = Uuid.random().toString(),
            name = normalised,
            category = category,
            color = color,
        )).mapIndexed { index, status -> status.copy(order = index) }
        return true
    }

    fun removeStatus(id: String) {
        statuses = statuses.filterNot { it.id == id }
            .mapIndexed { index, status -> status.copy(order = index) }
    }

    /** Renames or recategorises an existing status draft entry by id. */
    fun updateStatus(id: String, name: String?, category: WorkItemStatus?, color: TagColor?) {
        statuses = statuses.map { s ->
            if (s.id != id) s else s.copy(
                name = name?.let { StatusRules.normaliseName(it) } ?: s.name,
                category = category ?: s.category,
                color = color ?: s.color,
            )
        }
    }

    /** Moves the status at [from] to [to] and renumbers every stage's board order. */
    fun reorderStatus(from: Int, to: Int) {
        if (from < 0 || from >= statuses.size || to < 0 || to >= statuses.size || from == to) return
        val mutable = statuses.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        statuses = mutable.mapIndexed { index, status -> status.copy(order = index) }
    }

    /** Dispatches [CreateProject] or [UpdateProject]. Returns true on success. */
    suspend fun save(): Boolean {
        if (!canSave) return false
        val saved = reporting {
            if (isCreating) {
                createProject(name = name, keyPrefix = keyPrefix, details = details, memberIDs = memberIDs)
            } else {
                updateProject(
                    id = requireNotNull(savedProjectID),
                    name = name,
                    details = details,
                    memberIDs = memberIDs.takeIf { it != originalMemberIDs },
                    statuses = statuses.takeIf { it != originalStatuses },
                )
            }
        } ?: return false
        savedProjectID = saved.id
        return true
    }
}