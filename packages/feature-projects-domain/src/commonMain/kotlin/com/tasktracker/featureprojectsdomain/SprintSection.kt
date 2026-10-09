package com.tasktracker.featureprojectsdomain

/**
 * The two sections of the sprint list: active sprints and archived sprints.
 *
 * Active shows scheduled, in-progress and finished sprints. Archived shows only archived
 * sprints. This mirrors the backlog's three-section pattern but is specific to sprints.
 */
enum class SprintSection {
    /** Scheduled, in-progress and finished sprints — the sprints that are still in flight. */
    Active,

    /** Sprints that have been archived — filed away and no longer active. */
    Archived,
}
