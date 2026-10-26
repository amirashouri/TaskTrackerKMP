package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.TagID
import com.tasktracker.core.randomTagID
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.domain.TagRules
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * Presentation model for the admin project editor screen.
 * Uses direct online [AdminProjectsPort] (admin editors bypass project membership requirements)
 * and enforces optimistic concurrency control using [AdminProject.version].
 */
class AdminProjectEditorModel(
    private val id: ProjectID,
    private val port: AdminProjectsPort,
) : ProjectModel() {

    private var seeded: AdminProject? = null
    private var originalName: String = ""
    private var originalDetails: String = ""
    private var originalTags: List<ProjectTag> = emptyList()

    var isLoading: Boolean by mutableStateOf(true)
        private set

    var isSaving: Boolean by mutableStateOf(false)
        private set

    /** True when server returns 404 (distinguished from network/retryable read failures). */
    var isMissing: Boolean by mutableStateOf(false)
        private set

    var name: String by mutableStateOf("")
        private set

    var keyPrefix: String by mutableStateOf("")
        private set

    var details: String by mutableStateOf("")
        private set

    /** Admin-managed project tag catalog. */
    var tags: List<ProjectTag> by mutableStateOf(emptyList())
        private set

    val hasChanges: Boolean
        get() = name.trim() != originalName ||
                details.trim() != originalDetails ||
                tags != originalTags

    val canSave: Boolean
        get() = seeded != null && name.isNotBlank() && hasChanges

    /** Loads project from admin port, separating missing entity state (404) from network errors. */
    suspend fun load() {
        isLoading = true
        isMissing = false
        try {
            val found = port.project(id)
            seeded = found
            if (found == null) {
                isMissing = true
                return
            }
            name = found.project.name
            keyPrefix = found.project.keyPrefix
            details = found.project.details
            tags = found.project.tags.map { it.copy() }
            originalName = found.project.name.trim()
            originalDetails = found.project.details.trim()
            originalTags = found.project.tags.map { it.copy() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: AppError) {
            onFailure(failure)
        } catch (unexpected: Exception) {
            onFailure(AppError.Unknown(unexpected::class.simpleName ?: "an unknown failure"))
        } finally {
            isLoading = false
        }
    }

    fun onNameChanged(value: String) {
        name = value
    }

    fun onDetailsChanged(value: String) {
        details = value
    }

    /** Validates against [TagRules] and appends tag if name is unique (case-insensitive). */
    fun addTag(name: String, color: TagColor, icon: TagIcon): Boolean {
        if (!TagRules.isNameValid(name)) return false
        if (tags.size >= TagRules.MAX_PER_PROJECT) return false
        val normalised = TagRules.normaliseName(name)
        if (tags.any { it.name.equals(normalised, ignoreCase = true) }) return false
        tags = tags + ProjectTag(id = randomTagID(), name = normalised, color = color, icon = icon)
        return true
    }

    fun removeTag(id: TagID) {
        tags = tags.filterNot { it.id == id }
    }

    /** Updates project via admin API using current optimistic lock version. Returns true on success. */
    suspend fun save(): Boolean {
        val current = seeded ?: return false
        if (!canSave || isSaving) return false
        isSaving = true
        try {
            val saved = reporting {
                port.updateProject(
                    current.project.copy(
                        name = name.trim(),
                        details = details.trim(),
                        tags = tags.map { it.copy(name = TagRules.normaliseName(it.name)) }
                            .filter { it.name.isNotEmpty() },
                    ),
                    current.version,
                )
            } ?: return false

            seeded = saved
            name = saved.project.name
            details = saved.project.details
            tags = saved.project.tags.map { it.copy() }
            originalName = saved.project.name.trim()
            originalDetails = saved.project.details.trim()
            originalTags = saved.project.tags.map { it.copy() }
            return true
        } finally {
            isSaving = false
        }
    }
}