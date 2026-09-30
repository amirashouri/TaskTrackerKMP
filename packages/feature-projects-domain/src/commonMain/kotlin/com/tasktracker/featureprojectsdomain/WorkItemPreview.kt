package com.tasktracker.featureprojectsdomain

import com.tasktracker.domain.WorkItem

/**
 * How much of a work item a board card should show.
 *
 * `WorkItemRules.maxDetailsLength` allows 8,000 characters of details, so no card can hold
 * it. The card is a triage surface and the editor sheet is where a description gets read;
 * this value only decides whether there is anything worth expanding to. The line limits
 * themselves are layout and stay in the view.
 */
data class WorkItemPreview(
    /** The work item's title, unclamped. The card applies its own line limit. */
    val title: String,

    /** The work item's details with surrounding whitespace removed. */
    val details: String,
) {

    /**
     * Whether an expand affordance has anything to reveal.
     *
     * Derived from [details] rather than passed in beside it. Two properties that can
     * disagree is the failure this avoids: a preview built with a trimmed description and
     * an emptiness answer taken from the untrimmed one would show a disclosure that opens
     * onto a blank line, and a control that reveals nothing reads as a broken app rather
     * than as an empty field.
     */
    val hasMoreToShow: Boolean get() = details.isNotEmpty()

    /**
     * Trims on the way in, so the emptiness test and the displayed value are the same
     * decision.
     *
     * Local writes go through `WorkItemRules.normaliseDetails`, which trims, but a pulled
     * record is assigned straight into the store by the sync sink — so a whitespace-only
     * description from a server reaches this unchanged, and it is exactly that case the
     * original trimming was for.
     */
    constructor(item: WorkItem) : this(title = item.title, details = item.details.trim())
}
