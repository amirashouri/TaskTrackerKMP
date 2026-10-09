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

/** One row in the picker: enough to name a project and recognise it, and nothing else. */
data class PickableProject(
    val id: ProjectID,
    val name: String,
    val keyPrefix: String,
)

/**
 * The project picker: a compact list to choose the active project from.
 *
 * ## Not a [ProjectsListModel] with less on it
 *
 * Both read the same store, so the tempting shortcut is to hand the picker a
 * `ProjectsListModel` and let the sheet draw a subset. They are kept apart because they
 * answer different questions, and the difference is not cosmetic:
 *
 * - the list is the place a project is *managed*, so it carries the outstanding count that
 *   decides archive-or-delete, and it offers removal;
 * - the picker is the place a project is *chosen*, so it carries only a name and a
 *   selection, and it offers nothing destructive.
 *
 * A sheet that could delete a project is a sheet where a mis-tap loses work, and a picker
 * that showed outstanding counts would be answering a question nobody asked it. Sharing
 * the model would mean every future field added to the list appeared in the picker too,
 * which is how a screen ends up rendering something it has no opinion about.
 *
 * That is also why the row is [PickableProject] and not a `ProjectSummary` with the count
 * left at zero. A summary's `itemCount` is a real count read from the store, and a picker
 * that filled it with a placeholder zero would be publishing a number it had not measured
 * - and a future row that decided to show it would then show a confident lie.
 *
 * ## Selection is local, and is not a write
 *
 * [selectedProjectID] is Compose state and choosing a project does not touch the store.
 * The router owns which project is active; the picker only reports an intent, and the
 * shell decides whether that means a tab switch, a push, or nothing at all - which is not
 * this module's decision to make. Routing deliberately makes `openProject` a no-op on some
 * tabs, and a picker that wrote the selection itself would bypass that.
 */
class ProjectPickerModel(
    repository: ProjectRepositoryPort,
) : ObservingProjectModel(repository) {

    /** The projects available to choose, in the same order the list shows. */
    var projects: List<PickableProject> by mutableStateOf(emptyList())
        private set

    /** The currently chosen project, or `null` when the user has not chosen one. */
    var selectedProjectID: ProjectID? by mutableStateOf(null)
        private set

    /**
     * Whether a change matters here.
     *
     * A *work item* change does not: the picker shows no counts and no task names, so
     * moving a card between columns cannot change what it has to draw. Honouring those
     * changes would re-read every project on every drag on the board.
     */
    override fun isRelevant(change: ProjectChange): Boolean = change.kind == ProjectChange.Kind.Projects

    override suspend fun reload() {
        projects = repository.projects(includeArchived = false)
            .sortedWith(compareByDescending<Project> { it.updatedAt }.thenBy { it.name })
            .map { PickableProject(id = it.id, name = it.name, keyPrefix = it.keyPrefix) }
    }

    /** Records a choice. Purely local - see the class comment. */
    fun select(projectID: ProjectID?) {
        selectedProjectID = projectID
    }
}

/**
 * Creates a project from the picker's "new project" affordance, then reports its id.
 *
 * A separate model from [ProjectEditorModel] because the picker's job is to get the user
 * into a project, and the two fields it needs are a name and a key prefix. The full editor
 * has a details field, a read-only-on-edit key prefix and a change-snapshot it does not
 * need here, and asking a sheet for all of that would make a two-field form out of a
 * screen whose whole purpose is to be short.
 *
 * The prefix is derived from the name until the user types one, so the common case is a
 * name and nothing else.
 */
class NewProjectModel(
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
) : ProjectModel() {

    private val createProject = CreateProject(repository, dateProvider)

    /** The new project's name. */
    var name: String by mutableStateOf("")
        private set

    /** The new project's key prefix. Empty until [onNameChanged] derives one. */
    var keyPrefix: String by mutableStateOf("")
        private set

    /**
     * Free-text notes, carried through to the created project unchanged.
     *
     * Added with the shell rather than at port time because [NewProjectSheet] has always
     * drawn a details field: wiring that sheet to this model without a `details` here left
     * the caller with no honest option. `onDetailsChanged = {}` and `details = ""` would
     * have type-checked and produced a text field that accepted input and discarded it, and
     * `ProjectEditorModel` was no help either — it snapshots an `existing` project, has no
     * prefix derivation, and its screen deliberately has no prefix field at all, since the
     * prefix is the one part of a project that can never be edited afterwards.
     */
    var details: String by mutableStateOf("")
        private set

    /** Whether a key prefix was typed by hand, and so must not be overwritten by a rename. */
    private var isKeyPrefixEdited: Boolean = false

    /** The created project's id, once a create has succeeded. */
    var createdProjectID: ProjectID? by mutableStateOf(null)
        private set

    /** Whether create is available. Both fields are load-bearing; see the class comment. */
    val canCreate: Boolean get() = name.isNotBlank() && keyPrefix.isNotBlank()

    /** Sets the name, and re-derives the prefix unless one was typed by hand. */
    fun onNameChanged(value: String) {
        name = value
        if (!isKeyPrefixEdited) keyPrefix = suggestKeyPrefix(value)
    }

    /** Sets the key prefix, and remembers that the user chose it. */
    fun onKeyPrefixChanged(value: String) {
        keyPrefix = value
        isKeyPrefixEdited = true
    }

    /** Sets the details. */
    fun onDetailsChanged(value: String) {
        details = value
    }

    /**
     * Creates the project, and reports its id.
     *
     * `null` on failure, so the sheet stays open with the banner up. A created project's
     * name and prefix are fixed facts - the prefix in particular is the root of every key
     * the project will issue - so a second attempt is a *new* project rather than an
     * overwrite, and the sheet is expected to close rather than to offer a retry.
     */
    suspend fun create(): ProjectID? {
        if (!canCreate) return null
        val created = reporting {
            createProject(name = name, keyPrefix = keyPrefix, details = details)
        } ?: return null
        createdProjectID = created.id
        return created.id
    }

    private companion object {
        /**
         * Derives a candidate prefix from a project name.
         *
         * The first letter of each of the first two words, uppercased, so "Task Tracker"
         * gives `TT` and "Web Client" gives `WC`. A prefix is the one thing in this app a
         * user has to *remember* rather than read, so it is worth being short: taking the
         * words whole would make the ten-character limit the expected length and suggest
         * something like `TASKTRACKER`, which is no better than not guessing.
         *
         * A one-word name falls back to that word's first two letters - "Apollo" gives
         * `AP`. The fallback is not cosmetic: `ProjectRules` rejects a prefix shorter than
         * [ProjectRules.KEY_PREFIX_LENGTH]'s first, so a suggestion of one character would
         * leave the form filled in and unsaveable, with the user to work out why. A name
         * with no two usable characters in it suggests nothing at all, which is the one
         * case where asking is better than guessing. `Closed` is likewise never suggested,
         * because filing away is a decision rather than a lane.
         *
         * Deliberately not a uniqueness check. A prefix only has to be distinct enough to
         * read, and the port does not enforce uniqueness; guessing at one would need a read
         * the sheet has no reason to make.
         */
        fun suggestKeyPrefix(name: String): String {
            val words = name.split(' ', '-', '_').filter { it.isNotEmpty() }
            val initials = words
                .take(2)
                .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
                .joinToString("")
                .uppercase()
            if (initials.length >= ProjectRules.KEY_PREFIX_LENGTH.first) return initials

            // One word, or a word starting with punctuation. Take the first two usable
            // characters of the first word rather than the one character we have.
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
