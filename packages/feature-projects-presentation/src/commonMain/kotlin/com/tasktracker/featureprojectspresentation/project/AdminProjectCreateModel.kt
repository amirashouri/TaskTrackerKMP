package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.UserID
import com.tasktracker.domain.ProjectRules
import com.tasktracker.featureprojectsdomain.AdminProjectsPort

/**
 * Presentation model for the admin project creation form.
 * Uses direct online [AdminProjectsPort] (admin creators are not guaranteed project membership required by the outbox)
 * and applies [ProjectRules] normalization before dispatching.
 */
class AdminProjectCreateModel(
    private val port: AdminProjectsPort,
) : ProjectModel() {

    var isCreating: Boolean by mutableStateOf(false)
        private set

    /** Increment count used to trigger parent list refresh and re-key the create panel. */
    var completedCreates: Int by mutableStateOf(0)
        private set

    /** Whether the mobile modal sheet is open (unused in persistent wide-pane layout). */
    var isAddFormOpen: Boolean by mutableStateOf(false)
        private set

    var name: String by mutableStateOf("")
        private set

    var keyPrefix: String by mutableStateOf("")
        private set

    var details: String by mutableStateOf("")
        private set

    var memberIDs: List<UserID> by mutableStateOf(emptyList())
        private set

    private var isKeyPrefixEdited: Boolean = false

    val canSave: Boolean
        get() = name.isNotBlank() && keyPrefix.isNotBlank()

    /** True when draft contains any unsubmitted user input. */
    val hasChanges: Boolean
        get() = name.isNotBlank() ||
                keyPrefix.isNotBlank() ||
                details.isNotBlank() ||
                memberIDs.isNotEmpty()

    fun openAddForm() {
        dismissError()
        isAddFormOpen = true
    }

    fun closeAddForm() {
        isAddFormOpen = false
        dismissError()
    }

    fun onNameChanged(value: String) {
        name = value
        if (!isKeyPrefixEdited) keyPrefix = suggestKeyPrefix(value)
    }

    fun onKeyPrefixChanged(value: String) {
        keyPrefix = value
        isKeyPrefixEdited = true
    }

    fun onDetailsChanged(value: String) {
        details = value
    }

    fun onMemberIDsChanged(value: List<UserID>) {
        memberIDs = value.distinct()
    }

    /** Normalizes draft fields and submits project creation. Returns true on success. */
    suspend fun create(): Boolean {
        if (!canSave || isCreating) return false
        isCreating = true
        try {
            reporting {
                port.createProject(
                    name = ProjectRules.normaliseName(name),
                    keyPrefix = ProjectRules.normaliseKeyPrefix(keyPrefix),
                    details = ProjectRules.normaliseDetails(details),
                    memberIDs = memberIDs,
                )
            } ?: return false

            completedCreates++
            clearDraft()
            return true
        } finally {
            isCreating = false
        }
    }

    private fun clearDraft() {
        name = ""
        keyPrefix = ""
        details = ""
        memberIDs = emptyList()
        isKeyPrefixEdited = false
    }
}