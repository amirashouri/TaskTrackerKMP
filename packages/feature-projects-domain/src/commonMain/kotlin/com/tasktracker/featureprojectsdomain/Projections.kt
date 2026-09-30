package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.ProjectID
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemGrouping
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.onDay
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * The board as the view layer wants it: one entry per status, in column order.
 *
 * Computed in the domain layer rather than in a view so the grouping rule has one home. A
 * view that filtered and sorted its own copy would eventually disagree with
 * [WorkItemGrouping] and the board would reorder itself whenever one of the two changed.
 *
 * The columns are the domain's own [BoardColumn] rather than a nested copy. The original
 * wrapped the tuple that `boardColumns` returned in a second struct that added `isEmpty`
 * and `count`; once that tuple became a named class in the domain, the wrapper was a
 * second type with the same two fields, and a `Column` handed to a component expecting a
 * `BoardColumn` would not compile for a reason no caller could see.
 *
 * A `data class` over the *input* rather than over [columns], so two projections built
 * from equal inputs are equal — which is what a Compose parameter comparison needs in
 * order to skip a recomposition. The cost is one wasted recomposition in the narrow case
 * where a store returns the same items in a different order; the saving is a state object
 * that never recomposes for content it already shows.
 */
data class BoardProjection(
    val project: Project?,
    val items: List<WorkItem>,
) {

    val columns: List<BoardColumn> = WorkItemGrouping.boardColumns(items)

    /** Total cards across every column, for a dashboard badge. */
    val itemCount: Int get() = columns.sumOf { it.count }
}

/**
 * The backlog as the view layer wants it: the work that has not been put on a board yet.
 *
 * Flat, where it used to be sectioned. Two sections only made sense while the second one
 * held in-flight work borrowed from the board's columns, and that borrowed set is exactly
 * what caused the bug: the backlog listed cards the board was already showing. With only
 * `Backlog` items here, every one of them is somewhere the board is not, so there is
 * nothing left to divide.
 */
data class BacklogProjection(
    val project: Project?,
    val items: List<WorkItem>,
) {

    val untriaged: List<WorkItem> = WorkItemGrouping.untriaged(items)

    val itemCount: Int get() = untriaged.size

    val isEmpty: Boolean get() = untriaged.isEmpty()
}

/**
 * The archive as the view layer wants it: finished and closed work, by the month it left
 * the flow.
 *
 * @param timeZone the zone months are cut on. Injectable so a test can pin it, and
 *   because a month boundary is a real answer that depends on the answerer: an instant at
 *   23:30 UTC on the last day of a month falls in the *next* month for anyone a few hours
 *   east, and the same archive would then list one item under two different sections.
 *
 *   The original passed a `Calendar` and defaulted it to `.current`. A [TimeZone] is the
 *   narrower thing that actually changes the answer — of a `Calendar`, only the zone
 *   participates in where the cut falls; its calendar system and locale do not, because
 *   the grouping reads year and month as numbers and never formats anything.
 *
 *   Kept as a property rather than consumed and forgotten, so that equality stays honest:
 *   the same items cut in two zones really are two different projections, and pretending
 *   otherwise would let a locale change leave a stale grouping on screen.
 */
data class ArchiveProjection(
    val project: Project?,
    val items: List<WorkItem>,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /** One month's worth of resolved work. */
    data class Month(
        /**
         * The first instant of the month, and the section's identity.
         *
         * An instant rather than a formatted title so that grouping is a comparison of
         * instants and the wording stays the view's decision. A locale-sensitive string
         * would be an identity that changed when the device's language did, turning every
         * list key and every lazy-list item id into something that has to be re-keyed on a
         * locale change.
         *
         * The view recovers the calendar month from this with its own zone, which is
         * correct: the section header is a caption, and the caption's wording and calendar
         * are the view's decisions, not the grouping's.
         */
        val monthStart: Instant,
        val items: List<WorkItem>,
    ) {
        val count: Int get() = items.size
    }

    val months: List<Month> = WorkItemGrouping.resolved(items)
        .groupBy { it.archivedOn.startOfMonthIn(timeZone) }
        .map { (monthStart, monthItems) -> Month(monthStart, monthItems) }
        // Newest month first: an archive is read backwards, from now towards the past.
        .sortedByDescending { it.monthStart }

    val itemCount: Int get() = months.sumOf { it.count }

    val isEmpty: Boolean get() = itemCount == 0
}

/**
 * A one-line description of a project for a list row.
 *
 * A projection rather than a `Project` with a count bolted on, so a list row's shape is
 * stated once here instead of every screen remembering which six fields it wanted.
 *
 * The fields are copied rather than delegated to the [Project] because `Project` is
 * mutable: a summary that read straight through to it would change under the row that is
 * already displaying it, and the list would need a `State` around a value it believes is
 * immutable.
 */
data class ProjectSummary(
    val id: ProjectID,
    val keyPrefix: String,
    val name: String,
    val details: String,
    val isArchived: Boolean,
    val itemCount: Int,
) {

    constructor(project: Project, itemCount: Int) : this(
        id = project.id,
        keyPrefix = project.keyPrefix,
        name = project.name,
        details = project.details,
        isArchived = project.isArchived,
        itemCount = itemCount,
    )
}

/**
 * The first instant of the month this falls in, as seen in [timeZone].
 *
 * Taken as a local date first and then cut the month on it, rather than done in UTC: UTC
 * would silently file every item in a zone behind UTC under the previous month for the
 * first few hours of the month, which is the bug the injected zone exists to rule out.
 *
 * Uses [kotlinx.datetime.atStartOfDayIn] rather than assuming the first instant of a
 * month is midnight, because that assumption is false in any zone whose offset changes
 * within the month — the day a zone springs forward is twenty-three hours long, and its
 * midnight belongs to the day before.
 */
private fun Instant.startOfMonthIn(timeZone: TimeZone): Instant =
    toLocalDateTime(timeZone).date.yearMonth.onDay(1).atStartOfDayIn(timeZone)
