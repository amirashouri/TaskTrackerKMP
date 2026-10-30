package com.tasktracker.domain

/** The field a single backlog sort key is ordered by. */
enum class BacklogSortBasis {
    Priority,
    CreatedAt,
}

/**
 * One active sort key: a field and the direction it is currently sorted in.
 *
 * [descending] means "highest priority first" for [BacklogSortBasis.Priority] and "newest first"
 * for [BacklogSortBasis.CreatedAt].
 */
data class BacklogSortKey(
    val basis: BacklogSortBasis,
    val descending: Boolean = true,
) {
    /** Flip direction, keeping the same basis. */
    fun flipped(): BacklogSortKey = copy(descending = !descending)
}

/**
 * The backlog's chosen ordering: the active sort keys, leading key first.
 *
 * Both keys can be active at once. The first key in [keys] leads — it is the order the list
 * reads in — and the second one only breaks the ties the first leaves, so "highest priority,
 * newest among equals" is one selection rather than a list of two orderings to choose between.
 * There is always at least one key, and no basis appears twice.
 *
 * ## Why one gesture carries three decisions
 *
 * The row is two chips and a label, so tapping is the only gesture available. A tap therefore
 * resolves on/off, direction, and leadership together, in the one way that keeps the list
 * showing what was just asked for:
 *
 * - **Tapping an inactive basis** turns it on *in front*, at its default direction. The old
 *   leader becomes the tie-breaker rather than disappearing: asking for a new order and still
 *   seeing the previous one lead would be a lie about the tap.
 * - **Tapping the leading basis** reverses it. Tapping it once more switches it off, and the
 *   tie-breaker takes over the lead — that is the way back to a single-key order.
 * - **Tapping the key that is only breaking ties** promotes it to lead, keeping its direction.
 *   Reversing it instead would silently change the tie-break order the user was looking at.
 *
 * The single exception to the off state is the last remaining key: a list has to be ordered by
 * something, so a lone key cycles between its two directions rather than switching off.
 *
 * The final tie-break is always the item's [WorkItem.key], so the order is fully deterministic.
 */
data class BacklogSortOrder(
    val keys: List<BacklogSortKey> = listOf(BacklogSortKey(BacklogSortBasis.Priority)),
) {
    init {
        require(keys.isNotEmpty()) { "BacklogSortOrder must have at least one key" }
        require(keys.distinctBy { it.basis }.size == keys.size) {
            "BacklogSortOrder cannot repeat a basis: $keys"
        }
    }

    /** The key that decides the order. */
    val leadingKey: BacklogSortKey get() = keys.first()

    /** The key that only breaks the leading key's ties, or null when just one key is active. */
    val tieBreakerKey: BacklogSortKey? get() = keys.getOrNull(1)

    /** Whether [basis] is currently one of the active sort keys. */
    fun isActive(basis: BacklogSortBasis): Boolean = keys.any { it.basis == basis }

    /** Whether [basis] decides the order, as opposed to only breaking ties. */
    fun isLeading(basis: BacklogSortBasis): Boolean = leadingKey.basis == basis

    /** The [BacklogSortKey] for [basis], or null if it is not currently active. */
    fun keyFor(basis: BacklogSortBasis): BacklogSortKey? = keys.firstOrNull { it.basis == basis }

    /**
     * Apply one tap on [basis]; see the class documentation for the three cases.
     *
     * The result always has at least one key, so callers never have to guard against an
     * unsorted list.
     */
    fun toggled(basis: BacklogSortBasis): BacklogSortOrder {
        val index = keys.indexOfFirst { it.basis == basis }
        return when {
            // Inactive → on, and in front: the tap asked for this ordering to be the one.
            index < 0 -> copy(keys = listOf(BacklogSortKey(basis)) + keys)
            // Tie-breaker → promoted, keeping the direction it was already reading in.
            index > 0 -> copy(keys = listOf(keys[index]) + keys.filterIndexed { i, _ -> i != index })
            // Leader, ascending, with somewhere to fall back to → off.
            keys.size > 1 && !keys[0].descending -> copy(keys = keys.drop(1))
            // Leader, descending (or the only key) → reversed.
            else -> copy(keys = listOf(keys[0].flipped()) + keys.drop(1))
        }
    }

    /**
     * The total order this selection describes, ending on [WorkItem.key] so it is deterministic.
     *
     * Keys are chained in list order: the leader first, then the tie-breaker.
     */
    fun comparator(): Comparator<WorkItem> {
        val comparators = keys.map { key ->
            when (key.basis) {
                BacklogSortBasis.Priority ->
                    if (key.descending) compareByDescending<WorkItem> { it.priority }
                    else compareBy { it.priority }
                BacklogSortBasis.CreatedAt ->
                    if (key.descending) compareByDescending<WorkItem> { it.createdAt }
                    else compareBy { it.createdAt }
            }
        }
        return comparators.reduce { acc, next -> acc.then(next) }.thenBy { it.key.rawValue }
    }
}
