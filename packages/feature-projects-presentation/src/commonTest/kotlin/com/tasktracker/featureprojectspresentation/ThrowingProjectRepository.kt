package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * A repository whose every call throws a plain [IllegalStateException].
 *
 * ## Why this one exists
 *
 * `ProjectModel.reporting` has three catch clauses, and the third one - a non-[AppError]
 * failure - is the one with no test. A failure that is not an `AppError` is a bug rather
 * than a condition the product describes, and a bug in a model would otherwise reach a user
 * as a crashed screen. So the behaviour is: name the type, do not rethrow, and do not let it
 * be mistaken for a product condition.
 *
 * `changes()` is the exception - it is never awaited by a test, and it is the one method a
 * screen's `observe` will touch, so it delegates rather than throwing.
 */
class ThrowingProjectRepository : ProjectRepositoryPort {

    private val delegate = FakeProjectRepository()

    private fun boom(): Nothing = error("deliberate failure")

    override suspend fun projects(includeArchived: Boolean): List<Project> = boom()

    override suspend fun project(id: ProjectID): Project = boom()

    override suspend fun workItems(projectID: ProjectID): List<WorkItem> = boom()

    override suspend fun workItem(id: WorkItemID): WorkItem = boom()

    override suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int> = boom()

    override suspend fun isEmpty(): Boolean = boom()

    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> = boom()

    override suspend fun seedSampleDataIfNeeded(): Boolean = boom()

    override suspend fun sprints(projectID: ProjectID): List<Sprint> = boom()

    override suspend fun sprint(id: SprintID): Sprint? = boom()

    override suspend fun sprintWorkItems(id: SprintID): List<WorkItem> = boom()

    override suspend fun createSprint(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int,
        goal: String?,
        startDate: Instant,
        endDate: Instant,
        owner: String?,
        now: Instant,
    ): Sprint = boom()

    override suspend fun updateSprint(
        id: SprintID,
        name: String?,
        goalStoryPoints: Int?,
        goal: Patch<String?>,
        startDate: Instant?,
        endDate: Instant?,
        owner: Patch<String?>,
        now: Instant,
    ): Sprint = boom()

    override suspend fun finishSprint(id: SprintID, now: Instant): Sprint = boom()

    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint = boom()

    override suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem> = boom()

    override suspend fun deleteSprint(id: SprintID, now: Instant): Unit = boom()

    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID>,
    ): Project = boom()

    override suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>?,
    ): Project = boom()

    override suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project = boom()

    override suspend fun deleteProject(id: ProjectID): Unit = boom()

    override suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        priority: Priority,
        assignee: String?,
        dueDate: Instant?,
        storyPoints: Int?,
        sprintID: SprintID?,
        now: Instant,
    ): WorkItem = boom()

    override suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
    ): WorkItem = boom()

    override suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem = boom()

    override suspend fun deleteWorkItem(id: WorkItemID): Unit = boom()

    override fun changes(): Flow<ProjectChange> = delegate.changes()
}
