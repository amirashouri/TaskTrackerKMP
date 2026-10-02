package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.CreateProject
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.UpdateProject

/**
 * The project editor, for both creating and editing.
 *
 * ## A draft, never a stored record
 *
 * The fields are plain `String` state and the original values are snapshotted into `val`s
 * at construction. Holding the `Project` and comparing against it live would be the
 * shorter version and it is wrong: `Project` is a data class of `var`s, so a record
 * re-assigned by the sync sink - or simply read again by a reload - would change what
 * "changed" means underneath the form, and `hasChanges` would start answering about the
 * store rather than about the draft. Snapshotting into immutable strings means a
 * concurrent remote rename loses, which is the correct outcome for a form mid-edit.
 *
 * ## Why the key prefix is not editable here
 *
 * A project's key prefix is the root of every work item key it has ever issued (`TT-142`).
 * `UpdateProject` has no `keyPrefix` parameter at all, so the read-only-ness is enforced
 * by the type rather than by a disabled control that a caller could forget to respect.
 * [isKeyPrefixEditable] exists for the *view*, which has to render the field as read-only
 * rather than pretending the field is not there.
 */
class ProjectEditorModel(
    /**
     * The project being edited, or `null` when creating.
     *
     * Read once, for the initial field values and the [originalName]/[originalDetails]
     * snapshot. Never read again afterwards - see the class comment.
     */
    private val existing: Project?,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ProjectModel() {

    private val createProject = CreateProject(repository, dateProvider)
    private val updateProject = UpdateProject(repository, dateProvider)

    private val originalName: String? = existing?.name
    private val originalDetails: String? = existing?.details

    /** The project's name. Raw text, as typed - normalisation happens in the use case. */
    var name: String by mutableStateOf(existing?.name.orEmpty())
        private set

    /** The key prefix. Set at creation; see the class comment for why it never changes. */
    var keyPrefix: String by mutableStateOf(existing?.keyPrefix.orEmpty())
        private set

    /** Free-text notes. */
    var details: String by mutableStateOf(existing?.details.orEmpty())
        private set

    /** The id of the project this editor saves into, once a save has succeeded. */
    var savedProjectID: ProjectID? by mutableStateOf(existing?.id)
        private set

    /** Whether this editor is creating rather than editing. */
    val isCreating: Boolean get() = existing == null

    /** Whether the key prefix field should accept input. False when editing. */
    val isKeyPrefixEditable: Boolean get() = isCreating

    /**
     * Whether the draft differs from what it started as.
     *
     * Trims before comparing, because a form that differs only by trailing whitespace has
     * nothing to save: the use case trims on the way in, so writing the untrimmed draft
     * would bump `updatedAt` and reorder the dashboard without changing anything - the
     * exact reason `UpdateProject` reads instead of writes when nothing changed.
     */
    val hasChanges: Boolean
        get() = if (isCreating) {
            name.isNotBlank() || keyPrefix.isNotBlank() || details.isNotBlank()
        } else {
            name.trim() != originalName || details.trim() != originalDetails
        }

    /**
     * Whether save is available.
     *
     * Requires a name and a key prefix when creating, because both are load-bearing: the
     * name is what the user sees everywhere, and the prefix is the root of every key the
     * project will issue. An edit needs only [hasChanges] - a project that already exists
     * has both by definition.
     */
    val canSave: Boolean
        get() = if (isCreating) {
            name.isNotBlank() && keyPrefix.isNotBlank()
        } else {
            hasChanges
        }

    /** Sets the name. */
    fun onNameChanged(value: String) {
        name = value
    }

    /** Sets the key prefix. Ignored while editing, so no call site can set an illegal one. */
    fun onKeyPrefixChanged(value: String) {
        if (isKeyPrefixEditable) keyPrefix = value
    }

    /** Sets the details. */
    fun onDetailsChanged(value: String) {
        details = value
    }

    /**
     * Saves, creating or updating as appropriate.
     *
     * Returns whether the save happened, so a caller can navigate on success and stay put
     * on failure. The key prefix is sent only when creating, because
     * [UpdateProject] has no way to accept one.
     */
    suspend fun save(): Boolean {
        if (!canSave) return false
        val saved = reporting {
            if (isCreating) {
                createProject(name = name, keyPrefix = keyPrefix, details = details)
            } else {
                updateProject(
                    id = requireNotNull(savedProjectID),
                    name = name,
                    details = details,
                )
            }
        } ?: return false
        savedProjectID = saved.id
        return true
    }
}
