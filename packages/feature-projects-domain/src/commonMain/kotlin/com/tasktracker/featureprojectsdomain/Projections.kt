package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.ProjectID
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemGrouping
import com.tasktracker.domain.WorkItemStatus
import kotlinx.datetime.LocalDate
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

    val isEmpty: Boolean get() = columns.all { it.items.isEmpty() }

    val itemCount: Int get() = columns.sumOf { it.items.size }
}

/**
 * The backlog: only the untriaged work.
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
 * A month's worth of resolved work, used by [ArchiveProjection].
 *
 * The items are already sorted newest-first by [ArchiveProjection], so this does not
 * repeat that order — it is just a grouping boundary.
 */
data class Month(
    val monthStart: Instant,
    val items: List<WorkItem>,
) {
    val count: Int get() = items.size
}

/**
 * The archive: all resolved (done or closed) work, grouped by month.
 *
 * Sorted newest-first, because an archive is read backwards, from now towards the past.
 */
data class ArchiveProjection(
    val project: Project?,
    val items: List<WorkItem>,
    val timeZone: TimeZone,
) {
    val months: List<Month> = items
        .filter { it.status.isTerminal }
        .groupBy { it.archivedOn.startOfMonthIn(timeZone) }
        .map { (monthStart, monthItems) ->
            // The KDoc promises the same newest-first order the top-level `items` arrive in,
            // but `groupBy` preserves encounter order *within* a month, which is store order —
            // an archive section rendered from this read the list back front-to-back. Measured
            // against `ProjectionsTest`: three same-month items arrived TT-1, TT-2, TT-3 by
            // resolution date and read back in exactly that order, not [TT-2, TT-3, TT-1].
            Month(monthStart, monthItems.sortedByDescending { it.archivedOn.epochSeconds })
        }
        .sortedByDescending { it.monthStart }

    val itemCount: Int get() = months.sumOf { it.count }
    val isEmpty: Boolean get() = itemCount == 0
}

/**
 * The backlog as two sections: the untriaged work, and the work already filed away.
 */
enum class BacklogSection {
    /** The tasks that have not been put on a board yet. */
    Backlog,

    /** Tasks filed away into the archive — the `Closed`-only section of this tab. */
    Archived,
}

/**
 * Two partitions of the same project work, for the backlog tab's two sections.
 */
data class BacklogSectionedProjection(
    val project: Project?,
    val items: List<WorkItem>,
) {
    val backlog: List<WorkItem> = WorkItemGrouping.untriaged(items)
    val archived: List<WorkItem> = items
        .filter { it.status == WorkItemStatus.Closed }
        .sortedByDescending { it.archivedOn.epochSeconds }

    val currentSection: BacklogSection = BacklogSection.Backlog
    val backlogItemCount: Int get() = backlog.size
    val archivedItemCount: Int get() = archived.size
    val totalItemCount: Int get() = backlogItemCount + archivedItemCount
    val backlogIsEmpty: Boolean get() = backlog.isEmpty()
    val archivedIsEmpty: Boolean get() = archived.isEmpty()
    val bothAreEmpty: Boolean get() = backlogIsEmpty && archivedIsEmpty
    val hasAnyContent: Boolean get() = !(backlogIsEmpty && archivedIsEmpty)
}

/**
 * A one-line description of a project for a list row.
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
 */
private fun Instant.startOfMonthIn(timeZone: TimeZone): Instant =
    toLocalDateTime(timeZone).date.yearMonth.onDay(1).atStartOfDayIn(timeZone)
