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
)

/**
 * The backlog's chosen ordering: the active sort keys, leading key first.
 *
 * Both keys can be active at once. The first key in [keys] leads — it is the order the list
 * reads in — and the second one only breaks the ties the first leaves, so "highest priority,
 * newest among equals" is one selection rather than a list of two orderings to choose between.
 * There is always at least one key, and no basis appears twice.
 *
 * The sheet that edits this order (the backlog's `BacklogSortSheet`) builds a new value with
 * `copy(keys = …)` and reports it whole, so this type stays a statement of the order and holds
 * no interaction rules: which control changes what is the screen's business.
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
