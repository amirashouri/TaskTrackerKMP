package com.tasktracker.domain

/**
 * The arithmetic of a sprint goal: what a sprint committed to, what it achieved, and
 * whether that counts as a hit.
 *
 * ## Why the two sums are not the same question
 *
 * A sprint *commits* to everything it holds, and *achieves* only what was actually
 * delivered. Those differ in two ways that both look like arithmetic bugs if they are not
 * spelled out:
 *
 * - **Unestimated tasks.** An item with no [WorkItem.storyPoints] is still in the sprint
 *   and still has to be reported; it just contributes nothing to either sum. That is the
 *   reason story points are nullable rather than `0`.
 * - **Closed items.** [WorkItemStatus.isComplete] is the *delivered* predicate — `Done`
 *   alone — while [WorkItemStatus.isTerminal] is the *left the flow* predicate, which also
 *   covers `Closed`. A closed item is reported under done (it is no longer outstanding
 *   work) but contributes **nothing** to [achievedStoryPoints], because nothing was
 *   delivered. Counting it would let a sprint claim points for work that was decided
 *   against.
 *
 * So the summary's two lists and its two numbers are answers to two different questions,
 * and the split between them is the whole reason this is its own file.
 */
object SprintGoal {

    /** Everything the sprint committed to: the points on every item in it. */
    fun committedStoryPoints(items: List<WorkItem>): Int =
        items.sumOf { it.storyPoints ?: 0 }

    /** Only what was delivered — see this file's note on closed items. */
    fun achievedStoryPoints(items: List<WorkItem>): Int =
        items.filter { it.status.isComplete }.sumOf { it.storyPoints ?: 0 }

    /**
     * Whether [achieved] reached [goal], or `null` if no goal was set.
     *
     * **Exactly at the goal is a hit**, not a miss. That is the case a `>` gets wrong, and
     * it is the most likely comparison to be written by hand somewhere else in the app,
     * so it is a named function with a test rather than an expression repeated at each
     * call site.
     *
     * `null` rather than a `false` for a zero goal, because a sprint nobody set a target
     * for has not **missed** it either — it has no goal to have missed, and a summary
     * claiming "missed" (or claiming "hit") would be inventing a verdict the user never
     * asked for. Forcing the answer into a `Boolean` is what produces a sprint that always
     * reports success.
     */
    fun didHitGoal(achieved: Int, goal: Int): Boolean? =
        if (goal <= 0) null else achieved >= goal
}

/**
 * What a finished sprint came to.
 *
 * A value rather than a live query, and the reason is timing: the summary has to be true
 * of the items **at the moment it is read**, so it is rebuilt from the current item list
 * rather than snapshotted when Finish was pressed. A snapshot goes stale the moment anyone
 * edits a task afterwards, and "the summary said we were done, then we reopened a task" is
 * a bug nobody notices until a stakeholder asks.
 *
 * [didHitGoal] is carried rather than left for the view to compute, for the same reason
 * [WorkItemStatus] ships a [WorkItemStatus.wireValue] instead of making every caller
 * switch on the enum: one place decides, so a second renderer cannot answer differently.
 */
data class SprintSummary(
    val sprint: Sprint,
    /** Items that have left the flow, whether delivered (`Done`) or closed (`Closed`). */
    val done: List<WorkItem>,
    /** Items still in the flow — anything that is not terminal. */
    val incomplete: List<WorkItem>,
    val committedStoryPoints: Int,
    val achievedStoryPoints: Int,
    /** `null` when the sprint had no goal set. See [SprintGoal.didHitGoal]. */
    val didHitGoal: Boolean?,
) {
    /** Whether the sprint holds nothing at all, which is a different thing from a miss. */
    val isEmpty: Boolean get() = done.isEmpty() && incomplete.isEmpty()
}

/**
 * Builds a [SprintSummary] for [sprint] from [items].
 *
 * [items] is **filtered to the sprint's own items**, rather than trusted to have been
 * filtered already. A caller that holds a project's whole work list — which is what both
 * the board and the backlog have — gets the right answer by passing it straight in, and
 * there is no way to produce a summary that silently counted another project's work.
 *
 * Both lists are ordered by [WorkItem.orderingKey], the same order the board and the
 * backlog use, rather than by [WorkItemGrouping.resolved]'s resolution date. A summary is
 * read as a list of the sprint's tasks, and a set of tasks that reshuffles itself by the
 * day they finished reads as though it were sorted by something.
 *
 * Pure, and taking no clock: every instant in here comes from the caller's data, so the
 * whole value is testable on any day.
 */
fun sprintSummary(sprint: Sprint, items: List<WorkItem>): SprintSummary {
    val own = items.filter { it.sprintID == sprint.id }
    return SprintSummary(
        sprint = sprint,
        done = own.filter { it.status.isTerminal }.sortedBy { it.orderingKey },
        incomplete = own.filterNot { it.status.isTerminal }.sortedBy { it.orderingKey },
        committedStoryPoints = SprintGoal.committedStoryPoints(own),
        achievedStoryPoints = SprintGoal.achievedStoryPoints(own),
        didHitGoal = SprintGoal.didHitGoal(
            achieved = SprintGoal.achievedStoryPoints(own),
            goal = sprint.goalStoryPoints,
        ),
    )
}