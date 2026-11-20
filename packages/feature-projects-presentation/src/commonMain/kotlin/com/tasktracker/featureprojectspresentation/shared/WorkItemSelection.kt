package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Immutable
import com.tasktracker.core.WorkItemID

/**
 * The items a multi-select action applies to, in the order they were selected.
 *
 * A list rather than a `Set` because the order is user-facing: a batch report names the first
 * item that failed, and "first" has to mean "first one you picked", not "whichever the hash
 * order put at the front". Duplicates are impossible by construction — [plus] is idempotent and
 * [toggle] removes rather than appends.
 *
 * Immutable so a model can hold it in `mutableStateOf` and Compose only recomposes on a real
 * change; the board's lane selection in `specs/board-column-multi-select.md` reuses this type, so
 * neither surface grows its own selection rules.
 */
@Immutable
data class WorkItemSelection(val ids: List<WorkItemID> = emptyList()) {

    val size: Int get() = ids.size

    val isEmpty: Boolean get() = ids.isEmpty()

    val isNotEmpty: Boolean get() = ids.isNotEmpty()

    operator fun contains(id: WorkItemID): Boolean = id in ids

    /** Adds [id] to the end unless it is already selected. */
    operator fun plus(id: WorkItemID): WorkItemSelection =
        if (id in ids) this else WorkItemSelection(ids + id)

    fun remove(id: WorkItemID): WorkItemSelection =
        if (id in ids) WorkItemSelection(ids - id) else this

    /** Selects [id] when it is absent and deselects it when it is present. */
    fun toggle(id: WorkItemID): WorkItemSelection = if (id in ids) remove(id) else this + id

    /**
     * Keeps only the selected ids in [keep], preserving selection order.
     *
     * This is how a partial batch leaves the failed rows selected: the outcome's retryable ids are
     * the keep set, and everything the batch dealt with drops out.
     */
    fun retaining(keep: Collection<WorkItemID>): WorkItemSelection {
        if (ids.isEmpty()) return this
        val allowed = keep.toSet()
        val kept = ids.filter { it in allowed }
        return if (kept.size == ids.size) this else WorkItemSelection(kept)
    }

    fun cleared(): WorkItemSelection = if (ids.isEmpty()) this else WorkItemSelection()

    /** A snapshot for a caller that needs set membership, e.g. a row's `selected` state. */
    fun asSet(): Set<WorkItemID> = ids.toSet()
}
