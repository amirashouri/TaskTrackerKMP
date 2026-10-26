package com.tasktracker.featureprojectspresentation

import com.tasktracker.domain.Project
import com.tasktracker.featureteamsdomain.User

/**
 * Resolves [directory] users for [project], preserving the ordering in `project.memberIDs`.
 * Falls back to returning the full [directory] if [project] is null or membership is empty
 * so unconfigured projects remain assignable.
 */
fun assignableMembersFor(
    project: Project?,
    directory: List<User>,
): List<User> {
    if (project == null) return directory

    val memberIDs = project.memberIDs
    if (memberIDs.isEmpty()) return directory

    val byID = directory.associateBy { it.id }
    return memberIDs.mapNotNull { byID[it] }
}