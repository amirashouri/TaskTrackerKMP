package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.featureprojectsdomain.CreateProject
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/** Compact project summary displayed in the project switcher menu. */
data class PickableProject(
    val id: ProjectID,
    val name: String,
    val keyPrefix: String,
)

/**
 * Presentation model for the project switcher dropdown.
 * Selection is local state; the caller decides whether to navigate or switch tabs.
 */
class ProjectPickerModel(
    repository: ProjectRepositoryPort,
) : ObservingProjectModel(repository) {

    var projects: List<PickableProject> by mutableStateOf(emptyList())
        private set

    var selectedProjectID: ProjectID? by mutableStateOf(null)
        private set

    // Ignore work item moves; only project additions, updates, or deletions affect this list
    override fun isRelevant(change: ProjectChange): Boolean = change.kind == ProjectChange.Kind.Projects

    override suspend fun reload() {
        projects = repository.projects(includeArchived = false)
            .sortedWith(compareByDescending<Project> { it.updatedAt }.thenBy { it.name })
            .map { PickableProject(id = it.id, name = it.name, keyPrefix = it.keyPrefix) }
    }

    fun select(projectID: ProjectID?) {
        selectedProjectID = projectID
    }
}

/** Lightweight model for quickly creating a project directly from the project switcher sheet. */
class NewProjectModel(
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ProjectModel() {

    private val createProject = CreateProject(repository, dateProvider)

    var name: String by mutableStateOf("")
        private set

    var keyPrefix: String by mutableStateOf("")
        private set

    var details: String by mutableStateOf("")
        private set

    private var isKeyPrefixEdited: Boolean = false

    var createdProjectID: ProjectID? by mutableStateOf(null)
        private set

    val canCreate: Boolean get() = name.isNotBlank() && keyPrefix.isNotBlank()

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

    /** Creates project and records ID. Returns null on failure. */
    suspend fun create(): ProjectID? {
        if (!canCreate) return null
        val created = reporting {
            createProject(name = name, keyPrefix = keyPrefix, details = details)
        } ?: return null
        createdProjectID = created.id
        return created.id
    }

    private companion object {
        /** Derives 2-character uppercase prefix from first two words or first two characters. */
        fun suggestKeyPrefix(name: String): String {
            val words = name.split(' ', '-', '_').filter { it.isNotEmpty() }
            val initials = words
                .take(2)
                .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
                .joinToString("")
                .uppercase()
            if (initials.length >= ProjectRules.KEY_PREFIX_LENGTH.first) return initials

            val fromFirstWord = words
                .firstOrNull()
                ?.filter { it.isLetterOrDigit() }
                ?.take(ProjectRules.KEY_PREFIX_LENGTH.first)
                ?.uppercase()
                .orEmpty()
            return fromFirstWord.takeIf { it.length >= ProjectRules.KEY_PREFIX_LENGTH.first }.orEmpty()
        }
    }
}