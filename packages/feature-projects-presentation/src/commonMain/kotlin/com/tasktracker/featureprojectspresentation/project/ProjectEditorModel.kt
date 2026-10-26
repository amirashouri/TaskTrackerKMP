package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.CreateProject
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.UpdateProject

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
    private var isKeyPrefixEdited: Boolean = existing != null

    var name: String by mutableStateOf(existing?.name.orEmpty())
        private set

    var keyPrefix: String by mutableStateOf(existing?.keyPrefix.orEmpty())
        private set

    var details: String by mutableStateOf(existing?.details.orEmpty())
        private set

    var memberIDs: List<UserID> by mutableStateOf(originalMemberIDs)
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
                    memberIDs != originalMemberIDs
        } else {
            name.trim() != originalName || details.trim() != originalDetails || memberIDs != originalMemberIDs
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
                )
            }
        } ?: return false
        savedProjectID = saved.id
        return true
    }
}