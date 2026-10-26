package com.tasktracker.featureprojectsdata

import com.tasktracker.core.Broadcaster
import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectsdomain.ProjectChange
import kotlinx.coroutines.flow.Flow

/**
 * Invalidation channel for project change events.
 */
class ProjectChangeBus {

    private val broadcaster = Broadcaster<ProjectChange>()

    fun changes(): Flow<ProjectChange> = broadcaster.stream()

    fun send(change: ProjectChange) {
        broadcaster.send(change)
    }

    val subscriberCount: Int get() = broadcaster.subscriberCount

    fun finish() {
        broadcaster.finish()
    }
}

internal fun ProjectChangeBus.announceProjectChange(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
}

internal fun ProjectChangeBus.announceProjects(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID))
}

internal fun ProjectChangeBus.announceWorkItems(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
}

internal fun ProjectChangeBus.announceSprints(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
}

internal fun ProjectChangeBus.announceProjectDeletion(projectID: ProjectID) {
    send(ProjectChange(kind = ProjectChange.Kind.Projects, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.WorkItems, projectID = projectID))
    send(ProjectChange(kind = ProjectChange.Kind.Sprints, projectID = projectID))
}
