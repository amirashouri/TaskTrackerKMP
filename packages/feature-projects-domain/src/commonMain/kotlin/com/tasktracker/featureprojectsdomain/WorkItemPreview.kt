package com.tasktracker.featureprojectsdomain

import com.tasktracker.domain.WorkItem

/**
 * Preview summary of a work item for board cards.
 */
data class WorkItemPreview(
    val title: String,
    val details: String,
) {
    val hasMoreToShow: Boolean get() = details.isNotEmpty()

    constructor(item: WorkItem) : this(title = item.title, details = item.details.trim())
}
