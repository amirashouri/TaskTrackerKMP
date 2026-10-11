package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import kotlin.time.Instant
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.SprintSummary
import com.tasktracker.domain.SprintStatus
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintBlockingOverlap
import com.tasktracker.domain.sprintSummary
import com.tasktracker.featureprojectsdomain.AddWorkItemsToSprint
import com.tasktracker.featureprojectsdomain.ArchiveSprint
import com.tasktracker.featureprojectsdomain.CreateSprint
import com.tasktracker.featureprojectsdomain.DeleteSprint
import com.tasktracker.featureprojectsdomain.FinishSprint
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.RemoveWorkItemsFromSprint
import com.tasktracker.featureprojectsdomain.ReopenSprint
import com.tasktracker.featureprojectsdomain.UpdateSprint
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

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
 * [UpdateSprint] takes `ownerID: Patch<UserID?>` because "leave it alone" and "hand it to
 * nobody" are different instructions. A picker cannot tell them apart either — an
 * "Unassigned" selection is ambiguous between them. The resolution is [hasChanges]: a
 * draft whose owner is unassigned when the original had one **is** a change, and it is
 * sent as `Patch.Of(null)`. A draft that did not touch the owner at all cannot happen,
 * because the draft starts as the original's value, so an Unassigned pick always means
 * the user cleared it. That is the whole of the reasoning, and it is why [originalOwnerID]
 * is kept rather than discarded.
 *
 * ## The goal is two fields, and they lock differently
 *
 * [goalStoryPoints] is the number — the cap [AddWorkItemsToSprint] refuses over — and
 * [goal] is the sentence saying what the sprint is for. Both arrive from one field in the
 * original design, and splitting them is why there are two setters with two behaviours:
 * the number is part of the window's commitment and freezes with it, while the sentence
 * stays editable forever, finished sprints included.
 *
 * ## Save is blocked on the draft, not on the store
 *
 * [canSave] refuses two things before a write is attempted: a window that ends before it
 * starts, and a window that overlaps an unfinished sibling. Both are refusals the store
 * would also make — it is the authority, and only it can see a concurrent edit — but the
 * user is not made to fill in a sheet, press a button, and read a banner for a mistake the
 * form could see.
 *
 * The sibling set is **injected** rather than read here: [projectSprints] is the caller's
 * read of the project's sprints, taken when the editor opened. That is the same shape as
 * every other snapshot in this class and for the same reason — a form that re-read its
 * siblings on every keystroke would answer about the store's current state, and the answer
 * would change under the draft between the sheet and the banner. The store is still what
 * refuses a race; this only keeps the first refusal local.
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
    /**
     * The project's sprints as they stood when the editor opened, excluding nothing.
     *
     * Used only to find a blocker for the draft's window; the sprint being edited is
     * excluded by id inside [blockedBySprintName], so a caller passes the whole list —
     * [ProjectRepositoryPort.sprints] already excludes nothing, and filtering here instead
     * of at the call site keeps every caller from having to remember to.
     */
    private val projectSprints: List<Sprint> = emptyList(),
    repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /**
     * The day the started-window lock is measured against.
     *
     * A second seam on one fact, and the reason it exists is that [DateProvider.now]
     * suspends by design while this answer is needed in the constructor: a model cannot
     * suspend to build itself. Both default to the same wall clock in production, and a
     * test injects this next to the clock it gives [dateProvider] so the two can never
     * disagree inside a suite either — a lock that answered from one date and a save that
     * wrote from another is the defect shape this parameter exists to prevent.
     */
    today: LocalDate = sprintLocalDate(Clock.System.now(), timeZone),
) : ProjectModel() {

    private val createSprint = CreateSprint(repository, dateProvider)
    private val updateSprint = UpdateSprint(repository, dateProvider)

    private val originalName: String? = existing?.name
    private val originalGoalStoryPoints: Int? = existing?.goalStoryPoints
    private val originalGoal: String? = existing?.goal
    private val originalOwnerID: UserID? = existing?.ownerID
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

    /** The goal in prose, raw as typed. Blank and absent are one state; see [onGoalChanged]. */
    var goal: String by mutableStateOf(existing?.goal.orEmpty())
        private set

    /** The first day of the sprint, or `null` when not chosen yet. */
    var startDate: LocalDate? by mutableStateOf(originalStartDate)
        private set

    /** The last day, inclusive. */
    var endDate: LocalDate? by mutableStateOf(originalEndDate)
        private set

    /** The owner, or `null` for unowned. An id, never a display name. */
    var ownerID: UserID? by mutableStateOf(existing?.ownerID)
        private set

    /** The id of the sprint once a save has succeeded. */
    var savedSprintID: SprintID? by mutableStateOf(existing?.id)
        private set

    /** Whether this editor is creating rather than editing. */
    val isCreating: Boolean get() = existing == null

    /**
     * Whether the window and the point cap are frozen: the sprint has already started.
     *
     * From the **original** start date rather than the draft's, deliberately. The lock is a
     * fact about the sprint the user opened, and deriving it from the draft would let a form
     * that opened editable move its own start into yesterday and lock itself under the
     * user's finger mid-edit. A save that pulls the window into the present therefore lands
     * as one write that starts the sprint, and the next open of the editor is locked.
     *
     * Never true while creating: a sprint nobody has written yet has no window to have
     * started. Name, prose goal and owner are outside this entirely and stay editable in
     * every state — they describe the sprint rather than commit it to a time or a number.
     *
     * The setters below refuse on this, and the sheet disables the same fields: the refusal
     * is the guarantee, and the disabled control is what keeps the user from typing into a
     * field whose answer would be dropped. Hiding the fields instead would leave the form's
     * shape lying about what is in it.
     */
    val windowLocked: Boolean = existing?.let { sprint ->
        sprintLocalDate(sprint.startDate, timeZone) <= today
    } ?: false

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
                goal.trim() != originalGoal.orEmpty() ||
                startDate != originalStartDate ||
                endDate != originalEndDate ||
                ownerID != originalOwnerID
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

    /**
     * The name of the unfinished sprint the draft's window would overlap, or `null`.
     *
     * `null` when either date is unset or the window is backwards — a half-chosen window
     * and a wrong-way-round one are already accounted for by other fields, and reporting an
     * overlap for them would be a second complaint about the same draft.
     *
     * The comparison is [sprintBlockingOverlap]'s, the same function the store refuses on,
     * so the form and the banner name the same sprint. A finished sibling is skipped by that
     * helper; the sprint being edited is excluded here because a sprint does not overlap
     * itself, and an edit that leaves its own window alone must not be refused.
     */
    val blockedBySprintName: String?
        get() {
            val start = startDate ?: return null
            val end = endDate ?: return null
            if (end < start) return null
            return sprintBlockingOverlap(
                sprintInstant(start, timeZone),
                sprintInstant(end, timeZone),
                projectSprints.filter { it.id != existing?.id },
            )?.name
        }

    /** Whether save is available. */
    val canSave: Boolean
        get() = name.isNotBlank() && startDate != null && endDate != null &&
            endIsBeforeStart != true && blockedBySprintName == null && hasChanges

    /** Sets the name. */
    fun onNameChanged(value: String) {
        name = value
    }

    /**
     * Sets the goal in points, treating blank as "no goal". Refused once the sprint has
     * started — see [windowLocked].
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
    fun onGoalStoryPointsChanged(value: String) {
        if (windowLocked) return
        val trimmed = value.trim()
        goalStoryPoints = when {
            trimmed.isEmpty() -> null
            else -> trimmed.toIntOrNull()?.takeIf { it >= 0 }
        }
    }

    /**
     * Sets the goal in prose — the free-text half of the goal, the other being
     * [goalStoryPoints]. Never locked: a sentence about what the sprint is for describes a
     * sprint in any state, including one already under way or finished.
     *
     * Held raw as typed and trimmed on the way out ([hasChanges] and [save] both compare
     * trimmed), for the reason [name] is held raw. Empty is a real value here rather than
     * an absence to preserve — clearing a goal is telling the store there is no goal
     * sentence, and `Patch.Unmentioned` is reserved for a draft that never touched it.
     */
    fun onGoalChanged(value: String) {
        goal = value
    }

    /** Sets the first day. Refused once the sprint has started — see [windowLocked]. */
    fun onStartDateChanged(value: LocalDate) {
        if (windowLocked) return
        startDate = value
    }

    /** Sets the last day. Refused once the sprint has started — see [windowLocked]. */
    fun onEndDateChanged(value: LocalDate) {
        if (windowLocked) return
        endDate = value
    }

    /** Sets the owner. `null` means unowned, which is a change rather than an absence. */
    fun onOwnerChanged(value: UserID?) {
        ownerID = value
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
                    goal = goal,
                    startDate = sprintInstant(start, timeZone),
                    endDate = sprintInstant(end, timeZone),
                    ownerID = ownerID,
                )
            } else {
                updateSprint(
                    id = requireNotNull(savedSprintID),
                    name = name.trim().takeIf { it != originalName },
                    goalStoryPoints = goalStoryPoints?.takeIf { it != originalGoalStoryPoints },
                    // The prose goal is a `Patch` for the reason [ownerID] is one: a draft
                    // that never touched it must say "leave it alone" rather than write an
                    // empty sentence over a goal another device set in the meantime.
                    goal = if (goal.trim() == originalGoal.orEmpty()) {
                        Patch.Unmentioned
                    } else {
                        Patch.Of(goal.trim().takeIf { it.isNotEmpty() })
                    },
                    startDate = start.takeIf { it != originalStartDate }
                        ?.let { sprintInstant(it, timeZone) },
                    endDate = end.takeIf { it != originalEndDate }
                        ?.let { sprintInstant(it, timeZone) },
                    ownerID = if (ownerID == originalOwnerID) {
                        Patch.Unmentioned
                    } else {
                        Patch.Of(ownerID)
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
    /**
     * Whether the sprint's window contains today, stamped at the read that built this value.
     *
     * A field rather than a property asking the clock again, for the same reason this
     * whole value is a snapshot: [SprintDetailModel.reload] reads the store and the clock
     * once per change, and a screen whose "is it active" answer moved between the totals
     * rendering and the button's `enabled` would be gating on a fact this value did not
     * have.
     */
    val isActive: Boolean,
    /**
     * The sprint's lifecycle status, derived from the same instant that produced [isActive].
     * Carried on the value rather than recomputed, for the same reason every other field is:
     * a screen whose status answer moved between the card rendering and the button's
     * `enabled` would be gating on a fact this value did not have.
     */
    val status: SprintStatus,
) {

    /**
     * Whether the viewer may change this sprint's task membership.
     *
     * Neither active nor finished — a planned sprint is still being assembled, and once
     * the window opens the committed set is what the progress bar and, at the end, the
     * summary report. [AddWorkItemsToSprint] and [RemoveWorkItemsFromSprint] restate this
     * rule as a refusal, because hiding a button is a UI state and not a guarantee; see
     * their KDoc. "Active" is [Sprint.isActiveOn], the same rule [com.tasktracker.
     * featureprojectspresentation.ProjectBoardModel] uses to pick the board's sprint, so
     * the two screens never disagree about which sprints are under way.
     */
    val canEditItems: Boolean
        get() = !sprint.isFinished && !isActive

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
        get() = !sprint.isFinished && !isActive && (!hasOwner || isViewerTheOwner)

    /** Whether the sprint has an owner at all. */
    val hasOwner: Boolean get() = sprint.ownerID != null

    /** Whether there is a summary worth opening as its own screen. */
    val hasSummaryToShow: Boolean get() = sprint.isFinished && !summary.isEmpty
}

/**
 * One sprint's screen: the goal, the window, the totals, and the tasks inside it.
 *
 * ## The viewer is injected, and a `UserID?` for a reason
 *
 * [currentUserID] is who the app believes is signed in, and it is `null` when it
 * does not know. `null` means "nobody in particular", which is treated as **not** the
 * owner: an owned sprint is closed by its owner, and a viewer the app cannot identify
 * cannot be shown to be that owner. The alternative — treating unknown as owning — would
 * put a button on screen for an identity the app has not established, and the check would
 * be enforced by the store anyway, so the user would press it and be refused.
 *
 * An exact id comparison against [Sprint.ownerID], which is the whole of0.2: the previous
 * name equality called a viewer whose display name drifted from the owner string not-the-
 * owner, and could call two people with the same name one person.
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
    /** The signed-in user's id, or `null` when the app does not know. See the class KDoc. */
    private val currentUserID: UserID? = null,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    private val finishSprint = FinishSprint(repository, dateProvider)
    private val archiveSprint = ArchiveSprint(repository, dateProvider)
    private val reopenSprint = ReopenSprint(repository, dateProvider)
    private val deleteSprint = DeleteSprint(repository, dateProvider)
    private val addWorkItemsToSprint = AddWorkItemsToSprint(repository, dateProvider)
    private val removeWorkItemsFromSprint = RemoveWorkItemsFromSprint(repository, dateProvider)

    /** The detail, or `null` before the first read and if the sprint has been deleted. */
    var detail: SprintDetail? by mutableStateOf(null)
        private set

    /**
     * Whether a write is in flight — Finish, Reopen, an add, or a remove. A screen shows
     * the wait, and disables the other buttons: they conflict, and a second press racing
     * the first is a double write rather than an impatient user.
     */
    var isBusy: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the add-tasks sheet is open.
     *
     * Here rather than in the destination, for the same reason `isSummaryOpen`'s sibling
     * states sit with their models: opening is gated on [SprintDetail.canEditItems], and a
     * gate the screen enforced by keeping its own boolean would be one the next caller of
     * this model could forget.
     */
    var isPickerOpen: Boolean by mutableStateOf(false)
        private set

    /**
     * The tasks the picker offers: in this project, in no sprint, not finished.
     *
     * **No sprint at all** rather than "not this sprint": an item belonging to *another*
     * sprint could be lifted through this sheet, and [AddWorkItemsToSprint] only checks
     * the sprint being written *to* — so offering an active sprint's task here would move
     * it out from under a window that has already frozen its membership, through a door
     * that is locked from the other side. An item in some other *planned* sprint is
     * likewise not offered; it travels by being removed there first, which is two explicit
     * steps instead of one silent one.
     *
     * **Not finished** — `Closed` (archived) *and* `Done* (delivered): a sprint commits
     * work it is going to do, and a finished task cannot be committed. [AddWorkItemsToSprint]
     * refuses one if it is ever offered anyway, which is how the same rule survives a
     * caller that does not mirror this filter — see the use case's KDoc for why a hidden
     * row is not a guarantee.
     *
     * Re-read on every [reload], so a task that left the backlog while this sheet was open
     * disappears from it rather than being addable from a stale list.
     */
    var pickableItems: List<WorkItem> by mutableStateOf(emptyList())
        private set

    /** The ids ticked in [pickableItems]. Cleared when the sheet opens, closes, or adds. */
    var selectedPickables: Set<WorkItemID> by mutableStateOf(emptySet())
        private set

    /** Whether at least one ticked task would actually be written. */
    val canAddSelected: Boolean
        get() = selectedPickables.isNotEmpty()

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
            pickableItems = emptyList()
            selectedPickables = emptySet()
            return
        }
        val items = repository.sprintWorkItems(sprintID)
        val now = dateProvider.now()
        // Auto-finish: if the sprint's end date has passed and it has not been finished,
        // mark it as finished so the status reflects the automatic completion.
        val effectiveSprint = if (!sprint.isFinished && now > sprint.endDate) {
            finishSprint(sprintID)
        } else {
            sprint
        }
        detail = SprintDetail(
            sprint = effectiveSprint,
            startDate = sprintLocalDate(effectiveSprint.startDate, timeZone),
            endDate = sprintLocalDate(effectiveSprint.endDate, timeZone),
            items = items,
            summary = sprintSummary(effectiveSprint, items),
            isViewerTheOwner = effectiveSprint.ownerID != null &&
                effectiveSprint.ownerID == currentUserID,
            isActive = effectiveSprint.isActiveOn(now),
            status = effectiveSprint.statusAt(now),
        )
        pickableItems = repository.workItems(sprint.projectID)
            .filter {
                it.sprintID == null &&
                    it.status != WorkItemStatus.Closed &&
                    it.status != WorkItemStatus.Done
            }
        // A ticked task that has gone — added somewhere else, archived, deleted — is
        // dropped rather than left to be sent: confirming against ids the store no longer
        // offers would either fail mid-write or, worse, succeed on a row the sheet is no
        // longer showing.
        selectedPickables = selectedPickables
            .filter { id -> pickableItems.any { it.id == id } }
            .toSet()
    }

    // MARK: Membership
    //
    // Every method here is gated on [SprintDetail.canEditItems] *and* refuses inside the
    // use case. The two are not the same check repeated: this one keeps a sheet from
    // opening for a sprint whose state changed a moment ago, and the use case's refusal is
    // what answers the race where it changed after the sheet opened.

    /** Opens the add-tasks sheet, if this sprint may still accept tasks. */
    fun openPicker() {
        if (detail?.canEditItems != true) return
        selectedPickables = emptySet()
        isPickerOpen = true
    }

    /** Closes the sheet and forgets the selection. */
    fun closePicker() {
        isPickerOpen = false
        selectedPickables = emptySet()
    }

    /** Ticks or unticks one candidate. */
    fun togglePickable(id: WorkItemID) {
        selectedPickables = if (id in selectedPickables) {
            selectedPickables - id
        } else {
            selectedPickables + id
        }
    }

    /**
     * Moves every ticked task into this sprint, and closes the sheet on success.
     *
     * Returns whether it happened, so a caller can keep the sheet open on a refusal — the
     * refusal itself arrives as [errorMessage], the same banner as every other write.
     */
    suspend fun addSelected(): Boolean {
        if (selectedPickables.isEmpty()) return false
        val moved = acting {
            addWorkItemsToSprint(workItemIDs = selectedPickables.toList(), sprintID = sprintID)
        }
        if (moved) closePicker()
        return moved
    }

    /**
     * Sends one task back to the backlog, and reports whether it went.
     *
     * Returns `false` rather than throwing so the row can stay put on a refusal; the
     * banner carries the reason.
     */
    suspend fun removeItem(id: WorkItemID): Boolean = acting {
        removeWorkItemsFromSprint(workItemIDs = listOf(id), sprintID = sprintID)
    }

    /**
     * Closes the sprint, and reports whether it did.
     *
     * Returns `false` rather than throwing, so a caller can stay on the screen on failure —
     * the most likely failure being a refusal by the store's own rules, which is a banner,
     * not a crash.
     */
    suspend fun finish(): Boolean = acting { finishSprint(sprintID) }

    /**
     * Archives a finished sprint, and reports whether it did.
     *
     * Returns `false` rather than throwing, so a caller can stay on the screen on failure.
     * An unowned or already-archived sprint is a no-op — the store refuses a sprint that is
     * not finished, and silently accepts one that is already archived.
     */
    suspend fun archive(): Boolean = acting { archiveSprint(sprintID) }

    /**
     * Removes this sprint (deletes it and returns its tasks to the backlog), and reports
     * whether it did.
     *
     * Returns `false` rather than throwing, so a caller can stay on the screen on failure.
     */
    suspend fun removeSprint(): Boolean = acting { deleteSprint(sprintID) }

    /** Reopens the sprint, and reports whether it did. See [finish]. */
    suspend fun reopen(): Boolean = acting { reopenSprint(sprintID) }

    private suspend fun acting(block: suspend () -> Unit): Boolean {
        isBusy = true
        return try {
            reporting { block() } != null
        } finally {
            isBusy = false
        }
    }
}
