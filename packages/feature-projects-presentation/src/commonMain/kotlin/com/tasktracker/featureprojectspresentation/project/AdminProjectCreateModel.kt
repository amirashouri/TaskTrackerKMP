package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.UserID
import com.tasktracker.domain.ProjectRules
import com.tasktracker.featureprojectsdomain.AdminProjectsPort

/**
 * The admin projects section's create form: a draft, and the create it sends to the server.
 *
 * ## Why this is not [ProjectEditorModel]
 *
 * That model writes through the offline-first store, whose outbox push the server authorizes by
 * membership — and an admin creating a project for other people is not on its member list. The
 * create has to land on the server for the section to show it, so it goes to [AdminProjectsPort]
 * and stays online-only, the same trade [AdminProjectEditorModel] makes for the edit.
 *
 * ## The draft is the model's, and that is why a success clears it
 *
 * On a phone the form lives in a sheet that is dismissed when the create lands, so it is emptied
 * by leaving the composition. The wide window's pane is never torn down: it is the same instance
 * before and after, holding whatever the model holds. So a create that succeeded has to empty the
 * draft here, or the next tap on Create would send the project that was just made a second time.
 * [completedCreates] is the other half of the same problem, for the shell: the panel is re-keyed
 * on it so the member picker's own search box starts empty, and the list re-reads, because the
 * project that just landed belongs at the top of a newest-first list rather than appended.
 *
 * ## Why the domain's rules are applied here
 *
 * The board's create reaches [ProjectRules] through the `CreateProject` use case; the admin create
 * has no use case of its own, so the model applies the same rules before the port rather than
 * letting a name no board would accept — a one-character key prefix, say — arrive at the server
 * from this pane. Two creates for the same screen, one set of rules.
 */
class AdminProjectCreateModel(
    private val port: AdminProjectsPort,
) : ProjectModel() {

    /** Whether a create is in flight, so the button disables rather than double-submitting. */
    var isCreating: Boolean by mutableStateOf(false)
        private set

    /**
     * How many creates have landed.
     *
     * The shell keys the create panel on it and hands it to the list as the reason to re-read;
     * see the class comment.
     */
    var completedCreates: Int by mutableStateOf(0)
        private set

    /** Whether the phone's create sheet is open. Unread in the wide arrangement, where the pane is. */
    var isAddFormOpen: Boolean by mutableStateOf(false)
        private set

    /** The project's name, raw as typed. Normalisation happens in [create]. */
    var name: String by mutableStateOf("")
        private set

    /** The key prefix, offered from the name and editable until it is typed over. */
    var keyPrefix: String by mutableStateOf("")
        private set

    /** Free-text notes. */
    var details: String by mutableStateOf("")
        private set

    /** Selected members, kept as stable identifiers rather than display names. */
    var memberIDs: List<UserID> by mutableStateOf(emptyList())
        private set

    /** True once the prefix has been typed over, so the name stops overwriting it. */
    private var isKeyPrefixEdited: Boolean = false

    /**
     * Whether Create is available.
     *
     * A name and a prefix, both load-bearing: the name is what everyone sees, and the prefix is
     * the root of every key the project will issue. Deliberately says nothing about
     * [isCreating] — that is a request in flight rather than a form to fill, and it travels
     * separately as the panel's `isBusy`.
     */
    val canSave: Boolean
        get() = name.isNotBlank() && keyPrefix.isNotBlank()

    /** Opens the phone's create sheet. */
    fun openAddForm() {
        dismissError()
        isAddFormOpen = true
    }

    /** Closes the phone's create sheet without creating anything. */
    fun closeAddForm() {
        isAddFormOpen = false
        dismissError()
    }

    /** Sets the name, re-deriving the prefix while it has not been typed over. */
    fun onNameChanged(value: String) {
        name = value
        if (!isKeyPrefixEdited) keyPrefix = suggestKeyPrefix(value)
    }

    /** Sets the key prefix. */
    fun onKeyPrefixChanged(value: String) {
        keyPrefix = value
        isKeyPrefixEdited = true
    }

    /** Sets the details. */
    fun onDetailsChanged(value: String) {
        details = value
    }

    /** Replaces the selected members while preserving selection order. */
    fun onMemberIDsChanged(value: List<UserID>) {
        memberIDs = value.distinct()
    }

    /**
     * Creates the project on the server.
     *
     * Returns whether it landed, so the caller can close the sheet on `true`. A failure keeps the
     * draft — the admin corrects a typo rather than retyping the form — and is reported through
     * [ProjectModel.errorMessage].
     */
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

            // The server allocated the version and the timestamps; nothing here needs the row
            // itself, only the fact that there is one, which is what the list re-reads for.
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
