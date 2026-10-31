package com.tasktracker.domain

/**
 * What "matches a search" means for a work item, in one place.
 *
 * ## Why this is a rule and not a `filter` on each screen
 *
 * The board and the backlog's three segments all narrow to the same idea of a match, and the
 * dependency picker already had a fourth copy of it. Four copies of a substring comparison is
 * how the board starts finding a task the backlog says is not there — and neither screen is
 * obviously wrong on its own, so nobody looks.
 *
 * ## What is searched, and what is deliberately not
 *
 * The **key** (`TT-142`) and the **title**. Not details, not tag names, not assignee names, not
 * blocked-by keys. The reason is that a search field sits over a list of cards, and a card shows
 * a key, a title and some badges: a query that matched a word buried in a description would
 * return a row whose visible text does not contain the query, which reads as a broken filter
 * rather than as a thorough one.
 *
 * ## Substring, case-insensitive, and nothing else
 *
 * No fuzzy matching, no tokenising, no separator-insensitivity: `TT142` does **not** match
 * `TT-142`. Each of those is a rule that has to be tuned and explained, and the one property
 * that matters — that typing the first characters of a key finds the task — holds without any
 * of them.
 */
object WorkItemSearch {

    /**
     * Whether [item] matches [query].
     *
     * A blank query matches everything, which is what makes [filter] a no-op rather than a
     * special case at every call site.
     */
    fun matches(item: WorkItem, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return item.key.rawValue.contains(needle, ignoreCase = true) ||
            item.title.contains(needle, ignoreCase = true)
    }

    /**
     * [items] narrowed to those that match [query], in the order they were given.
     *
     * Order is the caller's: the backlog sorts by a user-chosen basis and the archive by
     * resolution date, so filtering must not re-order anything. A blank query returns [items]
     * itself rather than a copy, so a screen that filters on every recomposition of a blank
     * query does no allocation.
     */
    fun filter(items: List<WorkItem>, query: String): List<WorkItem> {
        val needle = query.trim()
        if (needle.isEmpty()) return items
        return items.filter { matches(it, needle) }
    }
}
