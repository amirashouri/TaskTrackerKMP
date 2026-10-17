package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * The admin project editor: the shared project form, seeded from the admin API rather than
 * from the local store.
 *
 * ## Why this is a second model and not a second screen
 *
 * [ProjectEditorScreen] is already stateless — it takes the draft and the callbacks — so the
 * only thing the admin section changes about it is where the draft comes from and where it
 * goes. A second screen would be a second copy of the same fields, which is the drift that
 * screen's own note exists to prevent.
 *
 * ## Why it cannot borrow [ProjectEditorModel]
 *
 * That model writes through [com.tasktracker.featureprojectsdomain.ProjectRepositoryPort] — the
 * offline-first store, whose outbox push the server refuses for a project the writer is not a
 * member of. An admin edits projects they are not in, so their editor has to go to
 * [AdminProjectsPort] and stay online-only. The two models differ in exactly that seam.
 *
 * ## Why the seed is kept whole
 *
 * [AdminProjectsPort.updateProject] is a compare-and-swap: the save must carry the version the
 * row was read at, or it silently overwrites whatever another admin wrote in between. Keeping
 * the [AdminProject] pair — and replacing it with whatever the server returns — is what makes a
 * save-then-save-again sequence send the *new* version rather than the one the screen opened
 * with.
 */
class AdminProjectEditorModel(
    private val id: ProjectID,
    private val port: AdminProjectsPort,
) : ProjectModel() {

    private var seeded: AdminProject? = null
    private var originalName: String = ""
    private var originalDetails: String = ""

    /** Whether the seed read is in flight, so the screen can tell "loading" from "gone". */
    var isLoading: Boolean by mutableStateOf(true)
        private set

    /** Whether a save is in flight, so the button disables rather than double-submitting. */
    var isSaving: Boolean by mutableStateOf(false)
        private set

    /**
     * True once a read has confirmed the server no longer has this project.
     *
     * Deliberately not the same as a failed read. A `404` says the row is gone and the screen
     * must say so, while a `500` is a retry — and a screen that showed "gone" for a failure
     * would send an administrator to recreate a project that is still there.
     */
    var isMissing: Boolean by mutableStateOf(false)
        private set

    /** The project's name, raw as typed. */
    var name: String by mutableStateOf("")
        private set

    /** The key prefix. Shown, never typed: it is the root of every key the project has issued. */
    var keyPrefix: String by mutableStateOf("")
        private set

    /** Free-text notes. */
    var details: String by mutableStateOf("")
        private set

    /** Whether the draft differs from what the server sent. */
    val hasChanges: Boolean
        get() = name.trim() != originalName || details.trim() != originalDetails

    /** Whether save is available: something changed, and a name to save it under. */
    val canSave: Boolean
        get() = seeded != null && name.isNotBlank() && hasChanges

    /**
     * Reads the project this editor edits.
     *
     * Not routed through [ProjectModel.reporting], which answers `null` for both "the read
     * failed" and "there is no such project" — and telling those apart is the whole reason
     * [isMissing] exists.
     */
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
            originalName = found.project.name.trim()
            originalDetails = found.project.details.trim()
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

    /** Sets the name. */
    fun onNameChanged(value: String) {
        name = value
    }

    /** Sets the details. */
    fun onDetailsChanged(value: String) {
        details = value
    }

    /**
     * Saves through the admin API, carrying the version this editor read the row at.
     *
     * Returns whether the save happened, so the caller navigates on success and stays put on
     * failure. Only the edited fields are replaced: the members, the archived flag and the next
     * item number travel through from the seed unchanged, because the body is the whole project
     * and a field left out of it is a field the server would clear.
     */
    suspend fun save(): Boolean {
        val current = seeded ?: return false
        if (!canSave || isSaving) return false
        isSaving = true
        try {
            val saved = reporting {
                port.updateProject(
                    current.project.copy(name = name.trim(), details = details.trim()),
                    current.version,
                )
            } ?: return false

            // The form now holds what the server stored, not the draft: it is no longer dirty,
            // and the next save carries the version this one returned.
            seeded = saved
            name = saved.project.name
            details = saved.project.details
            originalName = saved.project.name.trim()
            originalDetails = saved.project.details.trim()
            return true
        } finally {
            isSaving = false
        }
    }
}
