package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.domain.Sprint
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.domain.sprintSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A sprint as a list row: its window, its progress, and whether it is still open.
 *
 * ## The row carries both totals rather than a percentage
 *
 * [achievedStoryPoints] and [goalStoryPoints] are both here, and [progress] is derived
 * from them rather than stored. A stored percentage is a third number that has to be kept
 * in step with the two it came from, and it is the one nobody checks — a row reading
 * "42%" against "5 of 12" is off by a rounding rule nobody wrote down. Deriving it means
 * the row cannot show a figure that disagrees with the pair above it.
 *
 * ## `goalStoryPoints == 0` is not progress of zero
 *
 * [progressFraction] is `null` when there is no goal, rather than `0f`. A sprint nobody
 * set a target for has not made no progress; it has no progress *to* measure against, and
 * a bar drawn at zero is a claim the sprint failed. This is the same distinction
 * [SprintGoal.didHitGoal] makes with `null` rather than `false`, and the row is the third
 * place it has to hold — see [hasGoal].
 */
data class SprintListRow(
    val sprint: Sprint,
    /** The sprint's first day, in the model's zone. See [sprintLocalDate]. */
    val startDate: LocalDate,
    /** The sprint's last day, in the model's zone. Inclusive — see [SprintLocalDates]. */
    val endDate: LocalDate,
    /** What the sprint holds, from [SprintGoal.committedStoryPoints]. */
    val committedStoryPoints: Int,
    /** What was delivered, from [SprintGoal.achievedStoryPoints]. Read, never recomputed. */
    val achievedStoryPoints: Int,
) {

    /** Whether the sprint has a target to be measured against. */
    val hasGoal: Boolean get() = sprint.goalStoryPoints > 0

    /**
     * Progress as a fraction, or `null` when the sprint set no goal.
     *
     * **Not clamped to 1.** A sprint that achieved 14 against a goal of 12 is over goal,
     * and a bar pinned at the end with no explanation is a worse reading than one that
     * runs past it — the overshoot is the interesting part of a sprint that beat its
     * target. The bar's *track* is what stops at 1, not the fill.
     */
    val progressFraction: Float?
        get() = if (!hasGoal) {
            null
        } else {
            achievedStoryPoints.toFloat() / sprint.goalStoryPoints.toFloat()
        }
}

/**
 * The conversion between a sprint's stored [Instant] and the [LocalDate] a screen shows.
 *
 * ## This is the edge 23.4 deferred to here, and it is one function for a reason
 *
 * `Sprint.startDate` is an `Instant` because a store stores instants, and `TTDateField`
 * is a `LocalDate` because a calendar is a calendar. An `Instant` cannot answer "the
 * 4th" without a zone to interpret it in, and the answer changes with the device — so a
 * sprint stored as an instant would begin on a different day for a user who flew. The
 * conversion needs the zone, which makes it a decision rather than a cast, and it belongs
 * at the boundary where the view's notion of a day meets the store's.
 *
 * **One zone per model, injected, never read from the system here.** The same reasoning
 * [ProjectArchiveModel] gives: a test can then pin it, so a row's dates do not depend on
 * the machine. And it is a real answer rather than a formatting preference — an instant at
 * 23:30 UTC on 31 October is 1 November in most of Asia, so the same sprint starts on two
 * different days depending on who reads it.
 *
 * The zone is a *model* parameter rather than a per-call one, which means every row in a
 * list agrees with every other row in the same list. A per-call zone would let two rows
 * disagree, and a list whose rows disagree about which year they are in is a list nobody
 * trusts.
 *
 * ## The end date is a whole day, and the start is a whole day
 *
 * Both directions round **toward the start of the day**, so a sprint stored at
 * 09:00 on the 4th reads back as the 4th. Rounding the end *up* to the end of its day
 * would be the other defensible choice, and it is the wrong one for a form: a user who
 * picks 18 October and is then shown 19 October has been told the field is lying, and the
 * fix they reach for is to re-pick it. The instant stored is the first moment of the day
 * the user chose, which is what they chose.
 *
 * The consequence is honest and stated rather than hidden: `Sprint.isActiveOn` compares
 * instants, so a sprint is active from 00:00 on its first day until 00:00 on its **last**
 * — the final day belongs to the window for display and to the day-after for
 * `isActiveOn`. That asymmetry is in the entity and is not this function's to fix; what
 * this function does is refuse to hide it by rounding in the direction that would paper
 * over it.
 */
internal fun sprintLocalDate(instant: Instant, timeZone: TimeZone): LocalDate =
    instant.toLocalDateTime(timeZone).date

/** The inverse of [sprintLocalDate]. See its KDoc for why the start of the day. */
internal fun sprintInstant(date: LocalDate, timeZone: TimeZone): Instant =
    date.atStartOfDayIn(timeZone)

/**
 * A project's sprints, as rows.
 *
 * ## One read of the project's items, then a filter per sprint
 *
 * [ProjectRepositoryPort.sprintWorkItems] exists and is the obvious call, but a list of
 * eight sprints would then be eight queries to answer one question — "how far along is
 * each of these" — when [ProjectRepositoryPort.workItems] already holds every answer.
 *
 * The filtering is **[sprintSummary]'s**, not this model's, and that is the part that
 * matters. The totals have to be counted over *this sprint's* items, and a `filter` here
 * is a second copy of a rule that `sprintSummary` already applies — the same kind of
 * duplication that `ProjectRow.removalOutcome` is careful about. Worse, the two copies
 * would disagree the first time sprint membership stopped meaning "`sprintID` equals the
 * sprint's id", which is precisely the kind of change that is cheap to make in one place
 * and silently wrong in the other. So this calls the domain's own summariser and reads two
 * numbers off the result.
 *
 * That also settles the hit/miss question for free: [SprintSummary.didHitGoal] is
 * carried, not recomputed, and the row's [progressFraction] is derived from the same two
 * totals — so a list row and the finished sprint's own summary can never report different
 * progress for the same items.
 *
 * The trade is one filter per sprint over the project's items, which is O(sprints ×
 * items) on a list a phone shows in one screen. A sprint's item count is bounded by the
 * project, not by anything a user can make large, so this does not become the thing that
 * is slow.
 *
 * ## The ordering is the store's, not ours
 *
 * [ProjectRepositoryPort.sprints] documents its order — unfinished first, then by start
 * date — and this model does not re-sort. The unfinished sprints are the ones being worked
 * on and they have to lead every list that shows sprints, and a second sort here would be
 * a second rule to keep in step with the first, at which point one of them is a day out of
 * date.
 */
class SprintListModel(
    /** The project whose sprints these are. */
    val projectID: ProjectID,
    repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ObservingProjectModel(repository) {

    /**
     * The sprints currently in progress: started but not yet finished.
     *
     * Empty until the first read finishes. A screen showing this list should state the
     * section's own emptiness rather than the project's — a project with no in-progress
     * sprints is not a project with no sprints.
     */
    var inProgressRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    /**
     * The sprints scheduled for the future: not yet started and not finished.
     */
    var scheduledRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    /**
     * The sprints that have been finished: closed and summarised.
     */
    var finishedRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    /**
     * The archived sprints, as one flat list.
     *
     * Archived is a terminal state and not a *phase* of the sprint's window, so it is not
     * partitioned the way the active sprints are: an archived sprint is filed away, and
     * "scheduled", "in progress" and "finished" are questions about a window that is still
     * being worked. The archive screen therefore reads this list directly, with no headers.
     */
    var archivedRows: List<SprintListRow> by mutableStateOf(emptyList())
        private set

    /**
     * Whether the active section has nothing to show, or `null` while the first read is in
     * flight.
     *
     * `null` rather than `false` for the reason the old single `isEmpty` gave: a screen that
     * claimed "no sprints" during the first read would flash an empty state over a populated
     * list on every cold start.
     */
    val activeIsEmpty: Boolean? get() = if (isLoading) null else {
        inProgressRows.isEmpty() && scheduledRows.isEmpty() && finishedRows.isEmpty()
    }

    /** Whether the archive has nothing to show, or `null` while the first read is in flight. */
    val archivedIsEmpty: Boolean? get() = if (isLoading) null else archivedRows.isEmpty()

    override fun isRelevant(change: ProjectChange): Boolean =
        change.projectID == null || change.projectID == projectID

    override suspend fun reload() {
        val allSprints = repository.sprints(projectID)
        // Read once, and only if there is something to answer for: a project with no
        // sprints does not need its items fetched to say so.
        val items = if (allSprints.isEmpty()) emptyList() else repository.workItems(projectID)

        // One row per sprint, then split into the active partitions and the archive. The
        // archive is deliberately not partitioned — see [archivedRows].
        val rowsByID = allSprints.associate { sprint ->
            val summary = sprintSummary(sprint, items)
            sprint.id to SprintListRow(
                sprint = sprint,
                startDate = sprintLocalDate(sprint.startDate, timeZone),
                endDate = sprintLocalDate(sprint.endDate, timeZone),
                committedStoryPoints = summary.committedStoryPoints,
                achievedStoryPoints = summary.achievedStoryPoints,
            )
        }

        // Categorise each active sprint by its lifecycle status. The ordering within each
        // partition is the store's: unfinished first, then by start date.
        val now = dateProvider.now()
        val inProgressList = mutableListOf<SprintListRow>()
        val scheduledList = mutableListOf<SprintListRow>()
        val finishedList = mutableListOf<SprintListRow>()
        val archivedList = mutableListOf<SprintListRow>()

        for (sprint in allSprints) {
            val row = rowsByID.getValue(sprint.id)
            if (sprint.isArchived) {
                archivedList.add(row)
            } else {
                when {
                    sprint.isFinished -> finishedList.add(row)
                    sprint.startDate > now -> scheduledList.add(row)
                    else -> inProgressList.add(row)
                }
            }
        }

        inProgressRows = inProgressList
        scheduledRows = scheduledList
        finishedRows = finishedList
        archivedRows = archivedList
    }
}
