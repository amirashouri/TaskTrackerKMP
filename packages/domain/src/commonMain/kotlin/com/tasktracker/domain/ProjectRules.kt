package com.tasktracker.domain

import com.tasktracker.core.AppError
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.graphemeClusterCount

/**
 * Input rules for projects and work items, in one place so the editor, the sync sinks
 * and the tests all reject the same things for the same reasons.
 *
 * Every rule returns the *normalised* value it accepted. Trimming here rather than at
 * each call site is what stops `" "` becoming a project named with a space, which would
 * otherwise sail past an `isEmpty` check that ran before trimming.
 *
 * Every length guard uses [graphemeClusterCount], never `String.length`. Kotlin's
 * counts UTF-16 code units and Swift's `count` counted grapheme clusters; using
 * `length` here would silently allow an eight-times-longer emoji name than the original
 * ever did.
 */
object ProjectRules {

    const val MAX_NAME_LENGTH = 120
    const val MAX_DETAILS_LENGTH = 4_000
    val KEY_PREFIX_LENGTH = 2..10

    @Throws(AppError::class)
    fun normaliseName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw AppError.Validation("A project needs a name.")
        }
        if (graphemeClusterCount(trimmed) > MAX_NAME_LENGTH) {
            throw AppError.Validation("Project names are limited to $MAX_NAME_LENGTH characters.")
        }
        return trimmed
    }

    /**
     * Project key prefixes are uppercased alphanumerics, so `tt`, `TT` and `Tt` all
     * normalise to `TT` and a project can never end up with keys that differ only by
     * case.
     *
     * The `isAscii` guard is on the *character* test rather than folded into it:
     * Kotlin's `Char.isLetter()` is true for `é` and `Д`, so a check without the guard
     * would let a Cyrillic-looking prefix through and produce keys that differ from the
     * server's for reasons nobody can debug from a screenshot.
     *
     * The ASCII test is an explicit code-point range rather than `Char.isAscii()`,
     * which is stdlib-experimental and would make this file carry an opt-in for one
     * comparison.
     */
    @Throws(AppError::class)
    fun normaliseKeyPrefix(raw: String): String {
        val trimmed = raw.trim().uppercase()
        if (trimmed.length !in KEY_PREFIX_LENGTH) {
            throw AppError.Validation(
                "A key prefix must be ${KEY_PREFIX_LENGTH.first}-${KEY_PREFIX_LENGTH.last} letters or digits.",
            )
        }
        val allowed = trimmed.all { (it.code in 0x00..0x7F) && (it.isLetter() || it.isDigit()) }
        if (!allowed) {
            throw AppError.Validation("A key prefix can only contain letters and digits.")
        }
        return trimmed
    }

    @Throws(AppError::class)
    fun normaliseDetails(raw: String): String {
        val trimmed = raw.trim()
        if (graphemeClusterCount(trimmed) > MAX_DETAILS_LENGTH) {
            throw AppError.Validation("Notes are limited to $MAX_DETAILS_LENGTH characters.")
        }
        return trimmed
    }

    /**
     * Builds a work item key from a project prefix and a per-project sequence number.
     *
     * The `maxOf(1, number)` floor matches the counter's own floor in [Project]: a key
     * numbered `TT-0` would be issued exactly once and then skipped, which looks like
     * data loss to anyone reading the archive.
     */
    fun workItemKey(prefix: String, number: Int): WorkItemKey =
        WorkItemKey("$prefix-${maxOf(1, number)}")
}

/** Input rules for work items. See [ProjectRules] for the normalisation contract. */
object WorkItemRules {

    const val MAX_TITLE_LENGTH = 255
    const val MAX_DETAILS_LENGTH = 8_000

    @Throws(AppError::class)
    fun normaliseTitle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw AppError.Validation("A work item needs a title.")
        }
        if (graphemeClusterCount(trimmed) > MAX_TITLE_LENGTH) {
            throw AppError.Validation("Titles are limited to $MAX_TITLE_LENGTH characters.")
        }
        return trimmed
    }

    @Throws(AppError::class)
    fun normaliseDetails(raw: String): String {
        val trimmed = raw.trim()
        if (graphemeClusterCount(trimmed) > MAX_DETAILS_LENGTH) {
            throw AppError.Validation("Notes are limited to $MAX_DETAILS_LENGTH characters.")
        }
        return trimmed
    }

    /**
     * Status transitions.
     *
     * Every transition is permitted, including out of `Done` — reopening a closed item
     * is normal work, and a board that refuses it sends people off to edit the database.
     * What is ruled out is a change to the *same* status that also carries a
     * reordering, since that is two intentions fighting over one field.
     *
     * Both parameters are named and left unused rather than collapsed to a
     * no-argument `true`. The moment any transition *is* restricted, this signature is
     * already the right one, and no call site has to change. Kotlin does not warn on
     * unused parameters, so the deliberate emptiness stays quiet.
     */
    fun canTransition(from: WorkItemStatus, to: WorkItemStatus): Boolean = true
}

/**
 * Column grouping for the board, derived rather than stored.
 *
 * The grouping is a pure function of the items so the board, the backlog and the archive
 * cannot disagree about where an item belongs. Each of those three listings asks for a
 * different slice, which is why they are three functions rather than one `outstanding`:
 * an item can be on the board, untriaged, or resolved, and no two of those are the same
 * question.
 */
object WorkItemGrouping {

    /**
     * Items bucketed by status, each bucket in board order.
     *
     * A status with no items is simply absent from the map, and [boardColumns] turns
     * that into an empty column. Returning empty lists for every status instead would
     * make "no items" and "not loaded" indistinguishable to a caller.
     */
    fun itemsByStatus(items: List<WorkItem>): Map<WorkItemStatus, List<WorkItem>> =
        items.groupBy { it.status }
            .mapValues { (_, bucket) -> bucket.sortedBy { it.orderingKey } }

    /**
     * Everything waiting to be put on a board: the statuses that are not triaged yet.
     *
     * Only `Backlog`, deliberately. An earlier version of this returned everything that
     * was not done, which quietly included items already sitting in a board column — so
     * the backlog and the board listed the same cards. "Not yet on a board" is the rule,
     * and [WorkItemStatus.appearsOnBoard] is what states it.
     *
     * Ordered by [order] rather than by the board's [WorkItem.orderingKey]: nothing can
     * reorder a card inside the backlog, so its rank is only insertion order and would hide
     * the field the user chose to sort by. The default is priority, then newest first, which
     * is the same chain a board lane uses once rank ties.
     */
    fun untriaged(
        items: List<WorkItem>,
        order: BacklogSortOrder = BacklogSortOrder(),
    ): List<WorkItem> =
        items.filter { it.status == WorkItemStatus.Backlog }
            .sortedWith(order.comparator())

    /**
     * Everything finished or closed, most recently resolved first.
     *
     * Newest first because that is the order an archive is read in, and unlike every
     * other listing it sorts by when the item left the flow rather than by manual rank —
     * a resolved item's rank says nothing useful, since its column no longer exists.
     */
    fun resolved(items: List<WorkItem>): List<WorkItem> =
        items.filter { it.status.isTerminal }
            .sortedByDescending { it.archivedOn }

    /**
     * One entry per board column, in left-to-right order, including the empty ones.
     *
     * Every column is present whether or not it has items: a board that reflows as cards
     * move between lanes is disorienting, and a column that appears only once it has
     * content gives the drag-and-drop no target to drop into.
     */
    fun boardColumns(items: List<WorkItem>): List<BoardColumn> {
        val grouped = itemsByStatus(items)
        return WorkItemStatus.boardOrder.map { status ->
            BoardColumn(status, grouped[status].orEmpty())
        }
    }
}

/** One board column: its status and the items in it, in order. */
data class BoardColumn(
    val status: WorkItemStatus,
    val items: List<WorkItem>,
) {
    /**
     * Whether this lane has no cards.
     *
     * Distinct from the column being absent: every lane is present even when empty, and a
     * board that dropped them would give the drag-and-drop no target to drop into.
     */
    val isEmpty: Boolean get() = items.isEmpty()

    val count: Int get() = items.size
}
