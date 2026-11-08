@file:OptIn(ExperimentalUuidApi::class)

package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.core.TagID
import com.tasktracker.core.randomTagID
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.StatusRules
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.domain.TagRules
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Presentation model for the admin project editor screen.
 * Uses direct online [AdminProjectsPort] (admin editors bypass project membership requirements)
 * and enforces optimistic concurrency control using [AdminProject.version].
 */
class AdminProjectEditorModel(
    private val id: ProjectID,
    private val port: AdminProjectsPort,
    /**
     * The catalog an unconfigured project starts from — the built-in six, named by the caller
     * because the names are text and text is the presentation layer's job.
     *
     * Defaulted to empty so a host that has no labels to offer (a test, a preview) keeps the old
     * empty-list behaviour rather than inventing names.
     */
    private val defaultStatuses: List<StatusDefinition> = emptyList(),
) : ProjectModel() {

    private var seeded: AdminProject? = null
    private var originalName: String = ""
    private var originalDetails: String = ""
    private var originalRepoUrl: String? = null
    private var originalAgentEnabled: Boolean = false
    private var originalTags: List<ProjectTag> = emptyList()
    private var originalStatuses: List<StatusDefinition> = emptyList()

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

    /** Optional repository URL; empty means unset, which the server stores as absent. */
    var repoUrl: String by mutableStateOf("")
        private set

    /** Whether work here may be dispatched to a paired agent. */
    var agentEnabled: Boolean by mutableStateOf(false)
        private set

    /** Admin-managed project tag catalog. */
    var tags: List<ProjectTag> by mutableStateOf(emptyList())
        private set

    /** Admin-managed workflow status catalog, in board order. */
    var statuses: List<StatusDefinition> by mutableStateOf(emptyList())
        private set

    val hasChanges: Boolean
        get() = name.trim() != originalName ||
                details.trim() != originalDetails ||
                repoUrl.trim().takeIf { it.isNotEmpty() } != originalRepoUrl ||
                agentEnabled != originalAgentEnabled ||
                tags != originalTags ||
                statuses != originalStatuses

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
            // A project that never adopted a catalog reads as empty from the server while its
            // board draws the built-in six. Starting the draft from those six is what makes
            // "add a stage" *append*: a draft that began empty turned the first stage an admin
            // added into the whole catalog, and the board lost the other five lanes.
            val catalog = found.project.statuses.ifEmpty { defaultStatuses }
            name = found.project.name
            keyPrefix = found.project.keyPrefix
            details = found.project.details
            repoUrl = found.project.repoUrl.orEmpty()
            agentEnabled = found.project.agentEnabled
            tags = found.project.tags.map { it.copy() }
            statuses = catalog.map { it.copy() }
            originalName = found.project.name.trim()
            originalDetails = found.project.details.trim()
            originalRepoUrl = found.project.repoUrl
            originalAgentEnabled = found.project.agentEnabled
            originalTags = found.project.tags.map { it.copy() }
            // The seed is the project's *effective* catalog, so inheriting it is not an edit:
            // Save stays disabled until the admin actually changes something.
            originalStatuses = catalog.map { it.copy() }
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

    fun onRepoUrlChanged(value: String) {
        repoUrl = value
    }

    fun onAgentEnabledChanged(value: Boolean) {
        agentEnabled = value
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

    /** Validates against [StatusRules] and appends a status if its name is unique. */
    fun addStatus(name: String, category: WorkItemStatus, color: TagColor): Boolean {
        if (!StatusRules.isNameValid(name)) return false
        if (statuses.size >= StatusRules.MAX_PER_PROJECT) return false
        val normalised = StatusRules.normaliseName(name)
        if (statuses.any { it.name.equals(normalised, ignoreCase = true) }) return false
        statuses = renumbered(
            statuses + StatusDefinition(
                id = Uuid.random().toString(),
                name = normalised,
                category = category,
                color = color,
            ),
        )
        return true
    }

    /** Renames or recategorises an existing status draft entry by id. */
    fun updateStatus(id: String, name: String, category: WorkItemStatus, color: TagColor) {
        statuses = renumbered(
            statuses.map { status ->
                if (status.id != id) status else status.copy(
                    name = StatusRules.normaliseName(name),
                    category = category,
                    color = color,
                )
            },
        )
    }

    fun removeStatus(id: String) {
        statuses = renumbered(statuses.filterNot { it.id == id })
    }

    /** Moves the status at [from] to [to] and renumbers every stage's board order. */
    fun reorderStatus(from: Int, to: Int) {
        if (from < 0 || from >= statuses.size || to < 0 || to >= statuses.size || from == to) return
        val mutable = statuses.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        statuses = renumbered(mutable)
    }

    /** The list position is the board order, so one renumber keeps the two from drifting. */
    private fun renumbered(value: List<StatusDefinition>): List<StatusDefinition> =
        value.mapIndexed { index, status -> status.copy(order = index) }

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
                        repoUrl = repoUrl.trim().takeIf { it.isNotEmpty() },
                        agentEnabled = agentEnabled,
                        tags = tags.map { it.copy(name = TagRules.normaliseName(it.name)) }
                            .filter { it.name.isNotEmpty() },
                        statuses = renumbered(
                            statuses.map { it.copy(name = StatusRules.normaliseName(it.name)) }
                                .filter { it.name.isNotEmpty() },
                        ),
                    ),
                    current.version,
                )
            } ?: return false

            seeded = saved
            name = saved.project.name
            details = saved.project.details
            repoUrl = saved.project.repoUrl.orEmpty()
            agentEnabled = saved.project.agentEnabled
            tags = saved.project.tags.map { it.copy() }
            statuses = saved.project.statuses.map { it.copy() }
            originalName = saved.project.name.trim()
            originalDetails = saved.project.details.trim()
            originalRepoUrl = saved.project.repoUrl
            originalAgentEnabled = saved.project.agentEnabled
            originalTags = saved.project.tags.map { it.copy() }
            originalStatuses = saved.project.statuses.map { it.copy() }
            return true
        } finally {
            isSaving = false
        }
    }
}