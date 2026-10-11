package com.tasktracker.domain

import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.StatusChangeID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomWorkItemID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Where a work item sits in the flow.
 *
 * `Backlog` is a status rather than a separate entity, and that is a deliberate call:
 * an item's *position* is what changes when someone triages it, and modelling that as
 * one field means moving an item between the backlog and the board is an ordinary edit
 * with an ordinary audit trail, instead of a delete-and-recreate that loses its history.
 *
 * ## No user-facing text here
 *
 * The original carried `title` and `systemImage` on the enum. Both are gone, and
 * deliberately so: they are UI concerns, and `:packages:domain` is allowed to depend on
 * `:packages:core` alone, so it cannot read `composeResources` — which
 * `:packages:design-system` owns exclusively. Putting English strings here would mean
 * either inverting the layering to serve a label or hardcoding user-facing text in the
 * business rules, and the second is the one that quietly becomes untranslatable.
 *
 * What the domain provides instead is [wireValue], a stable machine key, and
 * presentation maps it with an **exhaustive** `when`. That is better than a string
 * lookup: adding a status makes every place that renders one fail to compile, instead
 * of falling through to a blank label. The mapping lives in
 * `:packages:feature-projects-presentation`.
 */
@Serializable
enum class WorkItemStatus {

    @SerialName("backlog")
    Backlog,

    @SerialName("todo")
    Todo,

    @SerialName("in_progress")
    InProgress,

    @SerialName("in_review")
    InReview,

    @SerialName("done")
    Done,

    @SerialName("closed")
    Closed,
    ;

    /**
     * The stable machine key: the JSON name, the Room column value, and the routing
     * deep-link segment. One string for all three, so they cannot drift apart.
     */
    val wireValue: String
        get() = when (this) {
            Backlog -> "backlog"
            Todo -> "todo"
            InProgress -> "in_progress"
            InReview -> "in_review"
            Done -> "done"
            Closed -> "closed"
        }

    /**
     * Whether the work is finished with, one way or another.
     *
     * A closed item is terminal rather than complete: nothing was delivered, but a
     * decision was taken and the item has left the flow. Both are archived, and neither
     * is overdue, which is why this is the predicate the due-date and archive rules use.
     */
    val isTerminal: Boolean
        get() = this == Done || this == Closed

    /**
     * Backlog items are triaged rather than worked, and a closed item has already been
     * dealt with, so neither takes up a column on the board.
     *
     * Note that `Done` *is* on the board, and is also archived. That overlap is intended:
     * the Done column is a workflow lane for what was just finished, while the archive is
     * a dated history of everything that was. Only `Closed` is archive-only.
     *
     * Written as a `when` rather than a set membership test so a new status cannot fall
     * through onto the board by default — it has to be placed deliberately.
     */
    val appearsOnBoard: Boolean
        get() = when (this) {
            Backlog, Closed -> false
            Todo, InProgress, InReview, Done -> true
        }

    /** Whether the item was actually delivered. */
    val isComplete: Boolean
        get() = this == Done

    companion object {
        /**
         * The board's columns, in left-to-right order. Derived from
         * [appearsOnBoard] rather than written out, so a new status cannot appear in a
         * column that was never designed for it.
         */
        val boardOrder: List<WorkItemStatus> = entries.filter { it.appearsOnBoard }

        /** Parses a [wireValue], or returns `null`. Deep links and stored rows. */
        fun fromWireValue(raw: String): WorkItemStatus? =
            entries.firstOrNull { it.wireValue == raw }
    }
}

/**
 * How urgent an item is.
 *
 * The ordering is the declaration order, which makes `compareTo` and `sortedBy` plain
 * ascending iteration — the alternative, a `String` raw value, would mean a dictionary
 * lookup inside a sort comparator once a board column has a few hundred items in it.
 */
@Serializable
enum class Priority {
    Lowest,
    Low,
    Medium,
    High,
    Critical,
    ;

    /** The stable machine key for storage and the wire. */
    val wireValue: String
        get() = name.lowercase()

    /** `Critical` is the highest priority, so the highest number sorts last ascending. */
    val rank: Int
        get() = ordinal

    companion object {
        /** Parses a [wireValue], or returns `null`. A stored row or a deep link. */
        fun fromWireValue(raw: String): Priority? =
            entries.firstOrNull { it.wireValue == raw }
    }
}

/**
 * A project: a key prefix, a name, and the work items filed under it.
 *
 * Mutable for the fields the editor writes, with [id] and [createdAt] `val` because
 * neither is ever rewritten — an entity that could change its own identity is a row the
 * sync layer can no longer match up.
 */
@Serializable
data class Project(
    val id: ProjectID = randomProjectID(),
    /** The prefix work item keys are built from, e.g. `TT` produces `TT-1`, `TT-2`. */
    var keyPrefix: String,
    var name: String,
    var details: String = "",
    /** Stable member identifiers assigned to this project. */
    var memberIDs: List<UserID> = emptyList(),
    /**
     * Archived projects keep their history and their work items but leave the default
     * list. Deleting them would cascade into work items that may already have shipped.
     */
    var isArchived: Boolean = false,
    /**
     * The next work item number this project will hand out.
     *
     * Carried on the entity rather than derived by counting rows, because counting is
     * wrong as soon as an item is deleted — `TT-3` would start being issued twice — and
     * because a project created offline on two devices needs a way for the two counters
     * to be merged rather than both starting from 1.
     */
    var nextItemNumber: Int = 1,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    init {
        // A counter below 1 would re-issue a key that has already been used, so the
        // floor is applied here rather than trusted to every writer. A `val` could not
        // be normalised in an `init`, which is why this one is a `var`.
        if (nextItemNumber < 1) nextItemNumber = 1
    }
}

/**
 * A time-boxed iteration of work: two goals, a window, an owner, and the tasks pulled into it.
 *
 * ## The goal is two fields, and they answer different questions
 *
 * `goalStoryPoints` is the number the sprint is measured against — the story points that may
 * be committed inside it — and "did it hit the goal" is [SprintGoal.didHitGoal] on the number
 * achieved. `goal` is the sentence: what the sprint is *for*, in prose, readable in a header.
 * An earlier revision deliberately kept only the number, on the reasoning that a sentence
 * would answer none of the questions the summary exists to answer; separating them keeps
 * that arithmetic intact and gives the prose a home that does not compete with it. The
 * capacity number is also the cap the sprint write path refuses to exceed — which is why it,
 * unlike `goal`, is only editable before the sprint starts: a window already open has a
 * committed set that changing the ceiling would silently invalidate.
 *
 * ## Two open sprints may not share one instant of their windows
 *
 * The rule the stores enforce on create, edit and reopen is no **overlap** between two
 * sprints that are not finished — not a cap of one open sprint. The question the rule
 * answers is scheduling: two sprints running concurrently make "what is in progress in
 * this project" ambiguous on the board and double the question at the end of each one.
 * [Sprint.overlaps] is that comparison, and [finishedAt]/[isFinished] are what keep a
 * finished sprint from blocking anything: it is history, not a timetable.
 *
 * Enforced in the stores rather than in this entity, because refusing depends on sibling
 * state the sprint cannot see.
 *
 * Mutable for the fields the editor writes, with [id] and [createdAt] `val` for the same
 * reason [Project]'s are: an entity that can change its own identity is a row the sync
 * layer can no longer match up.
 */
@Serializable
data class Sprint(
    val id: SprintID = randomSprintID(),
    val projectID: ProjectID,
    /** The sprint's name. [ProjectRules.normaliseName] applies the same rules as a project. */
    var name: String,
    /**
     * The story points that may be committed inside the sprint — its capacity, and the
     * number the summary measures it against. Never negative; see [SprintGoal].
     *
     * A cap, not only a target: the sprint write path refuses a move that would push the
     * committed total past it. Hence the edit rule — a number the window has already started
     * spending cannot be changed under it.
     */
    var goalStoryPoints: Int = 0,
    /**
     * The goal in prose: what the sprint is *for*, or `null`/blank for none.
     *
     * Separate from [goalStoryPoints] for the reason the class KDoc gives: one answers the
     * summary's arithmetic and the other answers a reader. Blank and padded values are
     * folded to `null` in [Sprint]'s `init`, so "cleared" and "never had one" are one
     * stored state.
     */
    var goal: String? = null,
    /** The first day of the sprint. */
    var startDate: Instant,
    /** The last day, inclusive. Never before [startDate]. */
    var endDate: Instant,
    /**
     * Who owns the sprint, and who may finish it.
     *
     * A `String?` and not a [UserID] for the reason [WorkItem.assignee] is one: an owner
     * that filtered and displayed differently from an assignee would be two concepts
     * wearing the same word, and "am I the owner" is then answered inconsistently in two
     * places. `null` is an unowned sprint, which anyone may finish.
     */
    var owner: String? = null,
    /** Whether the sprint has been closed and summarised. */
    var isFinished: Boolean = false,
    /**
     * When it was closed, or `null` while it is open.
     *
     * The invariant `finishedAt != null` exactly when [isFinished] is enforced in both
     * directions by [reconcileFinishedAt], for the same reason and with the same shape as
     * [WorkItem.reconcileResolutionAt] — including the half that gets forgotten, which is
     * that reopening has to *clear* the date.
     */
    var finishedAt: Instant? = null,
    /**
     * Whether the sprint has been archived — removed from the active list and kept as history.
     *
     * A sprint is archived **after** it is finished: the flow is scheduled → in-progress →
     * finished → archived. An archived sprint is finished history that the user has filed away
     * from the default view, like a closed work item in the backlog's archive.
     *
     * `isFinished` is still true while archived — archiving does not reopen a sprint — so the
     * two flags answer different questions: `isFinished` is "has this sprint's window closed"
     * and `isArchived` is "has the user put it away".
     */
    var isArchived: Boolean = false,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    init {
        // Floors rather than rejects, matching [Project]'s treatment of `nextItemNumber`:
        // a value that arrived from a server is normalised in place so every later reader
        // sees the same number, rather than the sprint holding a goal no screen can render.
        if (goalStoryPoints < 0) goalStoryPoints = 0
        // The same in-place rule for the prose goal: blank and absent are one state, so a
        // padded or empty value a sync carried arrives as the `null` every reader treats as
        // "no goal".
        goal = goal?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Puts [finishedAt] back in step with [isFinished].
     *
     * A date already present is kept rather than overwritten, so a value that arrived from
     * a server is not restamped with the local clock — the same rule as
     * [WorkItem.reconcileResolutionAt], and for the same reason.
     */
    fun reconcileFinishedAt(now: Instant) {
        finishedAt = if (isFinished) (finishedAt ?: now) else null
    }

    /**
     * The sprint's current status, derived from its flags and window.
     *
     * The four states the detail screen's action button answers to: scheduled, in-progress,
     * finished, archived. The ordering is the lifecycle — a sprint goes through them in
     * order, and a new status cannot fall through onto an earlier one by default.
     *
     * [now] is a parameter rather than a clock read, for the reason every write in this
     * project takes its instant from its caller: a rule that reads the wall clock is a rule
     * whose tests can only be run on a particular day.
     */
    fun statusAt(now: Instant): SprintStatus
        = when {
            isArchived -> SprintStatus.Archived
            isFinished || now > endDate -> SprintStatus.Finished
            now >= startDate -> SprintStatus.InProgress
            else -> SprintStatus.Scheduled
        }

    /**
     * Whether [now] falls inside the sprint's window, inclusive of both ends.
     *
     * A parameter rather than a clock read, for the reason every write in this project
     * takes its instant from its caller: a rule that reads the wall clock is a rule whose
     * tests can only be run on a particular day.
     */
    fun isActiveOn(now: Instant): Boolean = now >= startDate && now <= endDate

    /**
     * Whether this sprint's window shares any instant with [other]'s — end-meets-start and
     * start-meets-end are overlaps too.
     *
     * Both endpoints are bodies of the window because [isActiveOn] is inclusive on both.
     * A sprint ending on the 16th and one starting on the 16th therefore *do* conflict:
     * at 00:00 on the 16th both are active, and "what is in progress" would be ambiguous
     * for the whole day. The staggered next-day case everyone thinks of first — 2–15 against
     * 16–30 — is exactly one day shorter than that, and it is the case the `<=` on the
     * opposite end is there to permit.
     */
    fun overlaps(other: Sprint): Boolean = windowsOverlap(startDate, endDate, other)
}

/**
 * Whether a window given as raw instants shares any instant with [other]'s.
 *
 * A free function rather than a second member on [Sprint] because the common caller is an
 * editor whose dates are not a [Sprint] yet — a draft has a start and an end but no id, no
 * goal, no finished-at, and building a throwaway instance to call a method would make the
 * entity's constructor do work no constructor should. The comparison is the same one
 * [Sprint.overlaps] makes; this is just its window-only form.
 */
fun windowsOverlap(start: Instant, end: Instant, other: Sprint): Boolean =
    start <= other.endDate && other.startDate <= end

/**
 * The whole scheduling rule in one place: among [others], the sprint — if any — whose window
 * conflicts with the proposed window of a new or edited sprint.
 *
 * A finished sprint never blocks. Its window is history, not a timetable, and refusing a new
 * sprint because it lines up with a completed one would make the list unfillable once enough
 * sprints have run. The finished-half of the rule lives here rather than in the callers so
 * no store can re-derive it wrongly — every caller gets "refuse on this, name that sprint"
 * and none of them gets to remember which sprints count.
 */
fun sprintBlockingOverlap(start: Instant, end: Instant, others: List<Sprint>): Sprint? =
    others.firstOrNull { !it.isFinished && windowsOverlap(start, end, it) }

/**
 * A sprint's lifecycle status, as read by the detail screen's action button.
 *
 * The four states the button answers to — scheduled, in-progress, finished, archived — in
 * the order the sprint moves through them. A sprint does not skip: it is scheduled until its
 * window opens, in-progress while it is open, finished when its window closes (by hand or by
 * date), and archived when the user files it away from the default view.
 *
 * No user-facing text here, for the same reason as [WorkItemStatus]: the enum is the wire
 * contract and the presentation maps it with an exhaustive `when`. Adding a status makes every
 * place that renders one fail to compile rather than falling through to a blank label.
 *
 * Not a stored field — the status is [Sprint.statusAt] derived from [isFinished], [isArchived]
 * and the window — so there is nothing to migrate and nothing to keep in step with the flags.
 */
@Serializable
enum class SprintStatus {
    @SerialName("scheduled")
    Scheduled,

    @SerialName("in_progress")
    InProgress,

    @SerialName("finished")
    Finished,

    @SerialName("archived")
    Archived,
    ;

    /** The stable machine key for storage and the wire. */
    val wireValue: String
        get() = when (this) {
            Scheduled -> "scheduled"
            InProgress -> "in_progress"
            Finished -> "finished"
            Archived -> "archived"
        }

    companion object {
        /** Parses a [wireValue], or returns `null`. */
        fun fromWireValue(raw: String): SprintStatus? =
            entries.firstOrNull { it.wireValue == raw }
    }
}

/** One unit of work. */
@Serializable
data class WorkItem(
    val id: WorkItemID = randomWorkItemID(),
    val projectID: ProjectID,
    /**
     * The human-readable key, e.g. `TT-142`. Server-assigned in production; generated
     * locally per project so an item created offline still has a sensible key.
     */
    var key: WorkItemKey,
    var title: String,
    var details: String = "",
    var status: WorkItemStatus = WorkItemStatus.Backlog,
    var priority: Priority = Priority.Medium,
    var assignee: String? = null,
    var dueDate: Instant? = null,
    /**
     * How this item repeats, or `null` for a one-off.
     *
     * The rule travels with the item rather than living in a series table: an item
     * either repeats or it does not, and completing one creates the next instance
     * rather than sliding this one's due date — so the finished instance stays
     * finished with its own history, and the series advances by a new row.
     */
    var recurrence: Recurrence? = null,
    /**
     * The item's estimate, in points. `null` means **unestimated**.
     *
     * Nullable rather than `0` on purpose. A non-null zero makes "nobody estimated this"
     * and "this is worth nothing" the same value, and the difference is precisely what a
     * sprint's summary is for: an unestimated task still has to appear in the done or
     * incomplete list, it just contributes no points. Rendering it as `0` would let a
     * sprint claim it had measured its whole scope.
     *
     * A negative arriving from a server is floored at 0 in `init` rather than rejected,
     * for the reason [Sprint]'s goal is.
     */
    var storyPoints: Int? = null,
    /**
     * The tasks this one waits on, as stable ids.
     *
     * A plain list on the item rather than a join table: the relation is small, unordered,
     * and read with its owner every time the editor opens. Empty is "no blockers" rather
     * than "unknown" — items created before this field existed migrate in with none, and a
     * blocker deleted elsewhere shrinks the list on the next read.
     */
    var blockedByIDs: List<WorkItemID> = emptyList(),
    /**
     * The sprint this item has been pulled into, or `null` for "in the backlog".
     *
     * `null` is the state almost every item starts in and is not a temporary one — the
     * backlog is a real place, not an absence of a sprint. An item's [rank] is deliberately
     * untouched by a change here: rank is the item's position in its *status column* on
     * the board, and a sprint is not a column.
     */
    var sprintID: SprintID? = null,
    /**
     * Manual position within its status column. Lower sorts first.
     *
     * Stored as an integer rather than a timestamp or a float so that inserting an item
     * between two others is a single write, and so the order survives two devices
     * generating values for the same column.
     */
    var rank: Int = 0,
    val createdAt: Instant,
    var updatedAt: Instant,
    /**
     * When the item last reached a terminal status — done or closed — or `null` if it is
     * not currently in one.
     *
     * The archive groups by this rather than by `updatedAt`, because the month a task
     * finished is the useful question and "last touched" is not: opening a finished task
     * in the editor would otherwise reshuffle it to whatever month it was last edited.
     *
     * The invariant `resolvedAt != null` exactly when `status.isTerminal` is what makes
     * the archive and the board agree, and it is maintained on every path that writes a
     * status — a local move and a pulled server change alike, via
     * [reconcileResolutionAt].
     */
    var resolvedAt: Instant? = null,
) {

    init {
        // `null` is left exactly as it is. The floor applies only to a number that
        // arrived, and the pair of them is the point of the field: a floored `null` would
        // be an unestimated task claiming to be worth nothing. The local, rather than a
        // `storyPoints != null && storyPoints < 0`, because a `var` cannot be smart-cast
        // across the two reads.
        val points = storyPoints
        if (points != null && points < 0) storyPoints = 0
    }

    /**
     * The date the archive should file this item under.
     *
     * Falls back to `updatedAt` because a task moved to done on another device arrives
     * with no `resolvedAt` to file it by, and dropping it from the archive until the
     * user happened to move it again locally would be worse than filing it by when we
     * last heard.
     */
    val archivedOn: Instant get() = resolvedAt ?: updatedAt

    /**
     * Whether this item is past its due date and still live.
     *
     * `now` is a parameter rather than a clock read, for the reason every write in this
     * project takes its instant from its caller: a rule that reads the wall clock is a rule
     * whose tests can only be run on a particular day. A due date of exactly [now] is not
     * overdue — the day is not over until it is.
     *
     * **Terminal items are never overdue**, which is the part that is easy to get wrong. A
     * task that shipped last Tuesday is not late, and colouring it as though it were is a
     * claim about work that has already been delivered. `isTerminal` and the archive's
     * membership are the same predicate, so this and [archivedOn] cannot disagree about
     * which items are finished.
     */
    fun isOverdue(now: Instant): Boolean {
        if (status.isTerminal) return false
        val due = dueDate ?: return false
        return due < now
    }

    /**
     * Puts `resolvedAt` back in step with [status].
     *
     * Enforces the invariant in both directions, which is what makes it worth having as
     * one function: a terminal status with no date is dated at [now], and a status that
     * is no longer terminal has its date cleared. Reopening a task is the second case,
     * and it is the one that gets forgotten — a stale `resolvedAt` left behind files a
     * live task in the archive forever, and nothing about that looks like a bug on the
     * board.
     *
     * A date that is already present is kept rather than overwritten, so a value that
     * arrived from a server is not rewritten with the local clock.
     */
    fun reconcileResolutionAt(now: Instant) {
        resolvedAt = if (status.isTerminal) (resolvedAt ?: now) else null
    }

    /**
     * The one definition of "what order do these go in".
     *
     * Shared with the local store through the record's own ordering key so a board
     * column and the domain's [WorkItemGrouping] can never disagree about ordering —
     * two implementations of the same rule is how a board ends up reordering itself
     * whenever one of them is changed.
     */
    val orderingKey: WorkItemOrderingKey
        get() = WorkItemOrderingKey(
            rank = rank,
            priority = priority,
            createdAt = createdAt,
            tieBreaker = key.rawValue,
        )
}

/**
 * How a work item sorts relative to its peers.
 *
 * Deliberately a standalone value built from scalars, so the persistence record can
 * produce one without the domain having to know how a record is stored.
 */
data class WorkItemOrderingKey(
    val rank: Int,
    val priority: Priority,
    val createdAt: Instant,
    /**
     * Final tie-break. Two items can legitimately share a rank and a creation instant —
     * seeded data and imported data both do it — and without this their order would
     * depend on the sort algorithm's mood, which shows up as cards swapping places.
     */
    val tieBreaker: String,
) : Comparable<WorkItemOrderingKey> {

    override fun compareTo(other: WorkItemOrderingKey): Int = when {
        rank != other.rank -> rank.compareTo(other.rank)
        // Higher priority first, so the comparison is inverted on purpose.
        priority != other.priority -> other.priority.compareTo(priority)
        createdAt != other.createdAt -> createdAt.compareTo(other.createdAt)
        else -> tieBreaker.compareTo(other.tieBreaker)
    }
}

/**
 * One recorded move of a work item from one status to another.
 *
 * A value, not an entity with behaviour: a status change is only ever read — as the
 * per-task history in the editor — and never edited or moved itself. What it captures is
 * the two things a single `updatedAt` cannot: which way the status went, and who did it.
 *
 * **Only local writes produce one.** Two separate reasons, either of which would be
 * enough:
 *
 *  1. It never touches the outbox. Who moved a task on this device is a device-local
 *     fact, and pushing it would make the server echo it back.
 *  2. A pulled change does not produce one either, and this is less obvious. The data
 *     sync sink is required to be idempotent, because an interrupted sync cycle
 *     deliberately re-delivers the page it failed to finish. Re-applying the same
 *     payload writes the same values, so that is free; appending a history row is not,
 *     and doing it on every pull would fill the editor's history with the same
 *     transition repeated once per retry. There is no server-side author to attribute
 *     it to in any case.
 *
 * So a row here always describes a decision made on this device, and [changedBy] is the
 * signed-in user at the time or `null` when there was not one.
 */
@Serializable
data class StatusChange(
    val id: StatusChangeID = randomStatusChangeID(),
    val workItemID: WorkItemID,
    /**
     * Carried alongside the work item id so a project's history can be read without
     * first loading every work item in it. Status changes outlive the item they belong
     * to for the length of a transaction, not longer, but the query for the archive is
     * by project and would otherwise be a full scan.
     */
    val projectID: ProjectID,
    /**
     * Where the item came from, or `null` when it was created directly into [to].
     *
     * `null` rather than an optional-status sentinel like `Backlog`, because "created
     * here" and "moved here from the backlog" are genuinely different facts and a
     * history that cannot tell them apart cannot explain a new card.
     */
    val from: WorkItemStatus?,
    val to: WorkItemStatus,
    val changedAt: Instant,
    /**
     * Who made the change, or `null` when the app could not tell.
     *
     * A `null` here is a fact about the data rather than a missing value, and it is a
     * real case rather than a future one: a move made while no session was cached, which
     * happens on a cold launch before the session has been restored and in the window
     * around a sign-out. It has to be representable, because the alternative is either
     * dropping those changes or inventing an author for them, and an invented author is
     * worse than an honest blank.
     */
    val changedBy: UserID?,
) {
    /** Whether this records the item's creation rather than a move. */
    val isCreation: Boolean get() = from == null

    /**
     * Whether the change actually altered the status.
     *
     * A record of `Backlog → Backlog` is not a status change, and writing one would make
     * the history claim something that did not happen.
     */
    val altersStatus: Boolean get() = from != to
}
