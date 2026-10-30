package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.ProjectID
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.Project
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemGrouping
import com.tasktracker.domain.WorkItemStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.onDay
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * Kanban board projection grouped by columns.
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
 * Backlog projection for untriaged items.
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
 * Group of resolved items by month.
 */
data class Month(
    val monthStart: Instant,
    val items: List<WorkItem>,
) {
    val count: Int get() = items.size
}

/**
 * Archive projection grouping resolved items by month.
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
            Month(monthStart, monthItems.sortedByDescending { it.archivedOn.epochSeconds })
        }
        .sortedByDescending { it.monthStart }

    val itemCount: Int get() = months.sumOf { it.count }
    val isEmpty: Boolean get() = itemCount == 0
}

enum class BacklogSection {
    Backlog,
    Kanban,
    Archived,
}

/**
 * Sectioned backlog projection containing backlog, kanban board, and archive items.
 *
 * The three sections are the same pile of work read three ways, and two of them overlap on
 * purpose: `Done` is both a board lane and archived work (see [WorkItemStatus.appearsOnBoard]),
 * so a finished task is filed away the moment it is finished rather than needing a second press
 * to become history. Only `Closed` is archive-only.
 *
 * That overlap is why the archive reads [WorkItemStatus.isTerminal] rather than `Closed` alone:
 * `ArchiveWorkItem` refuses to file a terminal item — a `Done` task really is already filed
 * away — so an archive that filtered on `Closed` would refuse the write *and* show nothing,
 * which reads to the user as a task that vanished.
 */
data class BacklogSectionedProjection(
    val project: Project?,
    val items: List<WorkItem>,
) {
    val backlog: List<WorkItem> = WorkItemGrouping.untriaged(items)
    fun backlogIn(order: BacklogSortOrder): List<WorkItem> = WorkItemGrouping.untriaged(items, order)
    val archived: List<WorkItem> = items
        .filter { it.status.isTerminal }
        .sortedByDescending { it.archivedOn.epochSeconds }

    val kanban: List<WorkItem> = WorkItemGrouping.boardColumns(items).flatMap { it.items }

    val currentSection: BacklogSection = BacklogSection.Backlog
    val backlogItemCount: Int get() = backlog.size
    val kanbanItemCount: Int get() = kanban.size
    val archivedItemCount: Int get() = archived.size

    /**
     * How many items the project holds — counted once each.
     *
     * Deliberately not the sum of the three sections: a `Done` item is in two of them, so adding
     * them would count it twice and make the total disagree with [items].
     */
    val totalItemCount: Int get() = items.size
    val backlogIsEmpty: Boolean get() = backlog.isEmpty()
    val kanbanIsEmpty: Boolean get() = kanban.isEmpty()
    val archivedIsEmpty: Boolean get() = archived.isEmpty()
    val allSectionsEmpty: Boolean get() = backlogIsEmpty && kanbanIsEmpty && archivedIsEmpty
    val hasAnyContent: Boolean get() = !(backlogIsEmpty && kanbanIsEmpty && archivedIsEmpty)
}

/**
 * Summary representation of a project.
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

private fun Instant.startOfMonthIn(timeZone: TimeZone): Instant =
    toLocalDateTime(timeZone).date.yearMonth.onDay(1).atStartOfDayIn(timeZone)
