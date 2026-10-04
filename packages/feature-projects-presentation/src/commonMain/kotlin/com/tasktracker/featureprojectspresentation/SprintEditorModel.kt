package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.SprintSummary
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.sprintSummary
import com.tasktracker.featureprojectsdomain.CreateSprint
import com.tasktracker.featureprojectsdomain.FinishSprint
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ReopenSprint
import com.tasktracker.featureprojectsdomain.UpdateSprint
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The sprint editor, for both creating and editing.
 *
 * ## A draft, never a stored sprint
 *
 * The same rule as [ProjectEditorModel] and [WorkItemEditorModel], for the same reason:
 * `Sprint` is a data class of `var`s, so binding the form to it would make `hasChanges`
 * answer about the store rather than about the draft, and a record re-assigned by the
 * sync sink would move the goalposts under a form mid-edit. The originals are snapshotted
 * into `val`s at construction and never read again.
 *
 * ## The dates are [LocalDate] in the form and [kotlin.time.Instant] in the store
 *
 * `TTDateField` is a calendar, so the draft holds [LocalDate]s and the conversion happens
 * once, on save, through [sprintInstant]. Converting on every keystroke instead would put
 * an instant in the draft that the grid then has to convert *back* to find which cell is
 * selected — a round trip through a time zone to answer a question about days.
 *
 * ## The owner is a [Patch], and the draft cannot express the difference
 *
 * [UpdateSprint] takes `owner: Patch<String?>` because "leave it alone" and "hand it to
 * nobody" are different instructions. A text field cannot tell them apart — an emptied
 * field is ambiguous between them. The resolution is [hasChanges]: a draft whose owner is
 * blank when the original had one **is** a change, and it is sent as `Patch.Of(null)`. A
 * draft that did not touch the owner at all cannot happen, because the draft starts as the
 * original's value, so an empty field always means the user cleared it. That is the whole
 * of the reasoning, and it is why [originalOwner] is kept rather than discarded.
 *
 * ## Save is blocked on the window, not on the store
 *
 * [canSave] refuses `end < start` before a write is attempted, because a refused edit that
 * reaches the store has already opened a transaction. The store still makes the same check
 * — it is the authority, and only it can see a concurrent edit — but the user is not made
 * to fill in a sheet, press a button, and read a banner for a mistake the form could see.
 */
class SprintEditorModel(
    /** The project a new sprint is created in. */
    private val projectID: ProjectID,
    /**
     * The sprint being edited, or `null` when creating.
     *
     * Read once, for the initial field values and the change snapshot; never read again.
     */
    private val existing: Sprint?,
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ProjectModel() {

    private val createSprint = CreateSprint(repository, dateProvider)
    private val updateSprint = UpdateSprint(repository, dateProvider)

    private val originalName: String? = existing?.name
    private val originalGoalStoryPoints: Int? = existing?.goalStoryPoints
    private val originalOwner: String? = existing?.owner
    private val originalStartDate: LocalDate? = existing?.let {
        sprintLocalDate(it.startDate, timeZone)
    }
    private val originalEndDate: LocalDate? = existing?.let {
        sprintLocalDate(it.endDate, timeZone)
    }

    /** The sprint's name. Raw text as typed; `ProjectRules` trims and refuses on save. */
    var name: String by mutableStateOf(existing?.name.orEmpty())
        private set

    /** The goal in points, or `null` for none. */
    var goalStoryPoints: Int? by mutableStateOf(existing?.goalStoryPoints)
        private set

    /** The first day of the sprint, or `null` when not chosen yet. */
    var startDate: LocalDate? by mutableStateOf(originalStartDate)
        private set

    /** The last day, inclusive. */
    var endDate: LocalDate? by mutableStateOf(originalEndDate)
        private set

    /** The owner, or `null`/blank for unowned. Held raw, as typed. */
    var owner: String by mutableStateOf(existing?.owner.orEmpty())
        private set

    /** The id of the sprint once a save has succeeded. */
    var savedSprintID: SprintID? by mutableStateOf(existing?.id)
        private set

    /** Whether this editor is creating rather than editing. */
    val isCreating: Boolean get() = existing == null

    /**
     * Whether the draft differs from what it started as.
     *
     * Trims the text fields before comparing, for the reason [ProjectEditorModel] gives: the
     * use case trims on the way in, so writing an untrimmed draft would bump `updatedAt`
     * and reorder the list without changing anything.
     *
     * The dates are compared as [LocalDate]s rather than as instants, so a save that
     * re-picks the same day does not count as a change. Round-tripping a date through a
     * zone is lossless for this purpose — [sprintLocalDate] and [sprintInstant] are
     * inverses — and comparing the instants would mean a draft whose date was *never
     * touched* could still differ, because the original instant was not at midnight.
     */
    val hasChanges: Boolean
        get() = isCreating || (
            name.trim() != originalName ||
                goalStoryPoints != originalGoalStoryPoints ||
                startDate != originalStartDate ||
                endDate != originalEndDate ||
                owner.trim() != originalOwner.orEmpty()
            )

    /**
     * Whether the window is backwards — the end is before the start.
     *
     * `null` when either end is unset, because a half-chosen window is not a backwards
     * one, and an error under a field the user has not filled in yet is a complaint about
     * the form rather than about the draft. Both dates are non-null whenever a `Sprint`
     * exists, so this can only be `true` for a draft.
     *
     * The comparison is on [LocalDate]s, not on instants. The two dates are days, and a day
     * after a day is a day after a day in every zone — comparing instants would make the
     * answer depend on which side of a midnight the device's offset falls.
     */
    val endIsBeforeStart: Boolean?
        get() {
            val start = startDate ?: return null
            val end = endDate ?: return null
            return end < start
        }

    /** Whether save is available. */
    val canSave: Boolean
        get() = name.isNotBlank() && startDate != null && endDate != null &&
            endIsBeforeStart != true && hasChanges

    /** Sets the name. */
    fun onNameChanged(value: String) {
        name = value
    }

    /**
     * Sets the goal, treating blank as "no goal".
     *
     * Blank is `null` rather than `0` because they are different answers: `0` is somebody
     * saying a sprint is worth nothing, and `null` is nobody having said. See
     * `TTStoryPoints` for the same distinction at the other end of the feature.
     *
     * A non-numeric string is dropped rather than stored, because [goalStoryPoints] is an
     * `Int?` and a half-typed number has no representation. The field keeps the text it
     * was given, so the user sees what they typed and the model simply has nothing to
     * save yet.
     */
    fun onGoalChanged(value: String) {
        val trimmed = value.trim()
        goalStoryPoints = when {
            trimmed.isEmpty() -> null
            else -> trimmed.toIntOrNull()?.takeIf { it >= 0 }
        }
    }

    /** Sets the first day. */
    fun onStartDateChanged(value: LocalDate) {
        startDate = value
    }

    /** Sets the last day. */
    fun onEndDateChanged(value: LocalDate) {
        endDate = value
    }

    /** Sets the owner. Blank means unowned, which is a change rather than an absence. */
    fun onOwnerChanged(value: String) {
        owner = value
    }

    /**
     * Saves, creating or updating as appropriate.
     *
     * Returns whether the save happened, so a caller can navigate on success and stay put
     * on failure.
     *
     * The update path sends **only what changed**, rather than the whole draft. The port's
     * plain optionals make a `null` mean "leave it alone", and sending every field would
     * turn a rename into a four-field write — one that bumps `updatedAt` when nothing the
     * user can see moved, and one that would overwrite a concurrent remote edit on the
     * fields this form does not show.
     */
    suspend fun save(): Boolean {
        if (!canSave) return false

        val start = startDate
        val end = endDate
        if (start == null || end == null) return false

        val saved = reporting {
            if (isCreating) {
                createSprint(
                    projectID = projectID,
                    name = name,
                    goalStoryPoints = goalStoryPoints ?: 0,
                    startDate = sprintInstant(start, timeZone),
                    endDate = sprintInstant(end, timeZone),
                    owner = owner.trim().takeIf { it.isNotEmpty() },
                )
            } else {
                updateSprint(
                    id = requireNotNull(savedSprintID),
                    name = name.trim().takeIf { it != originalName },
                    goalStoryPoints = goalStoryPoints?.takeIf { it != originalGoalStoryPoints },
                    startDate = start.takeIf { it != originalStartDate }
                        ?.let { sprintInstant(it, timeZone) },
                    endDate = end.takeIf { it != originalEndDate }
                        ?.let { sprintInstant(it, timeZone) },
                    owner = if (owner.trim() == originalOwner.orEmpty()) {
                        Patch.Unmentioned
                    } else {
                        Patch.Of(owner.trim().takeIf { it.isNotEmpty() })
                    },
                )
            }
        } ?: return false

        savedSprintID = saved.id
        return true
    }
}

/**
 * One sprint, its items, and the summary a finished sprint is read through.
 *
 * ## The summary is the domain's value, not this model's arithmetic
 *
 * [summary] is [sprintSummary] applied to the sprint's own items. It is **not** rebuilt
 * from [WorkItem]s here, and [SprintDetailModel] does not sum anything either — the reason
 * is in that file's own KDoc: the summary has to be true of the items *at the moment it is
 * read*, so it is computed from the current list rather than snapshotted when Finish was
 * pressed. Re-deriving it in the view would put the arithmetic in two places, and the two
 * would disagree the first time the done/incomplete split changed.
 *
 * ## The summary is always present, finished or not
 *
 * [SprintDetailModel] builds it whether or not [Sprint.isFinished], because an open
 * sprint's progress is the same computation and the detail screen shows the same numbers.
 * What changes is only whether the screen offers the summary *screen* — which is a
 * question about [sprintSummary.isEmpty] and [Sprint.isFinished], not about the value
 * existing.
 */
data class SprintDetail(
    val sprint: Sprint,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** The sprint's items, in the board's own order. See [SprintDetailModel.reload]. */
    val items: List<WorkItem>,
    /** The arithmetic, computed by the domain. */
    val summary: SprintSummary,
    /** Whether the person using the app is this sprint's owner. See [canFinish]. */
    val isViewerTheOwner: Boolean,
) {

    /**
     * Whether the viewer may finish this sprint.
     *
     * **An unowned sprint can be finished by anyone**, which follows `Sprint`'s own KDoc:
     * `owner == null` is a statement that nobody has claimed it, not a lock. Refusing
     * there would leave a sprint that nobody is ever able to close.
     *
     * An owned sprint is closed by its owner. That is a real restriction rather than a
     * visual one — finishing stamps a date and produces the summary a stakeholder will
     * quote — and it is why [isViewerTheOwner] is carried on the value instead of being
     * worked out by the screen from an owner string it happens to be holding.
     */
    val canFinish: Boolean
        get() = !sprint.isFinished && (!hasOwner || isViewerTheOwner)

    /** Whether the sprint has an owner at all. */
    val hasOwner: Boolean get() = sprint.owner != null

    /** Whether there is a summary worth opening as its own screen. */
    val hasSummaryToShow: Boolean get() = sprint.isFinished && !summary.isEmpty
}

/**
 * One sprint's screen: the goal, the window, the totals, and the tasks inside it.
 *
 * ## The viewer is injected, and a `String?` for a reason
 *
 * [currentUserName] is the name the app believes is signed in, and it is `null` when it
 * does not know. `null` means "nobody in particular", which is treated as **not** the
 * owner: an owned sprint is closed by its owner, and a viewer the app cannot identify
 * cannot be shown to be that owner. The alternative — treating unknown as owning — would
 * put a button on screen for an identity the app has not established, and the check would
 * be enforced by the store anyway, so the user would press it and be refused.
 *
 * It is a name rather than a [com.tasktracker.core.UserID] for the reason `Sprint.owner`
 * is one: the same two concepts wearing two different words is the inconsistency
 * `Sprint`'s KDoc rules out. Comparing a name to a name is also what makes this testable
 * without a session.
 *
 * ## The items are the sprint's, and are re-read on every change
 *
 * [ProjectRepositoryPort.sprintWorkItems] is used rather than filtering the project's
 * whole list, because the summary filters internally anyway and this is the read that
 * cannot be wrong for another sprint: there is no second copy of the membership rule here
 * to drift.
 *
 * The items are the sprint's **whole** set, not just the ones left in the flow, because a
 * finished sprint's screen is where the delivered tasks are read. The split into done and
 * incomplete is [SprintSummary]'s, not a filter applied at the call site.
 */
class SprintDetailModel(
    /** The sprint being shown. */
    val sprintID: SprintID,
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    /** The signed-in user's name, or `null` when the app does not know. See the class KDoc. */
    private val currentUserName: String? = null,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    private val finishSprint = FinishSprint(repository, dateProvider)
    private val reopenSprint = ReopenSprint(repository, dateProvider)

    /** The detail, or `null` before the first read and if the sprint has been deleted. */
    var detail: SprintDetail? by mutableStateOf(null)
        private set

    /** Whether the sprint has been closed, or a Finish is in flight. A screen shows the wait. */
    var isFinishing: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether any change matters here.
     *
     * Both kinds do, and the distinction earns its keep for the same reason it does in
     * [ProjectsListModel]: a `ProjectChange` carrying `projectID` is a *project's* id, and
     * a sprint write announces the project it belongs to, so the sprint's own id never
     * appears. Matching on it would be a filter that silently dropped every sprint change
     * in the feature — which is why this is a deliberate `true` rather than an oversight.
     */
    override fun isRelevant(change: ProjectChange): Boolean = true

    override suspend fun reload() {
        val sprint = repository.sprint(sprintID) ?: run {
            detail = null
            return
        }
        val items = repository.sprintWorkItems(sprintID)
        detail = SprintDetail(
            sprint = sprint,
            startDate = sprintLocalDate(sprint.startDate, timeZone),
            endDate = sprintLocalDate(sprint.endDate, timeZone),
            items = items,
            summary = sprintSummary(sprint, items),
            isViewerTheOwner = sprint.owner != null && sprint.owner == currentUserName,
        )
    }

    /**
     * Closes the sprint, and reports whether it did.
     *
     * Returns `false` rather than throwing, so a caller can stay on the screen on failure —
     * the most likely failure being a refusal by the store's own rules, which is a banner,
     * not a crash.
     */
    suspend fun finish(): Boolean = acting { finishSprint(sprintID) }

    /** Reopens the sprint, and reports whether it did. See [finish]. */
    suspend fun reopen(): Boolean = acting { reopenSprint(sprintID) }

    private suspend fun acting(block: suspend () -> Unit): Boolean {
        isFinishing = true
        return try {
            reporting { block() } != null
        } finally {
            isFinishing = false
        }
    }
}
