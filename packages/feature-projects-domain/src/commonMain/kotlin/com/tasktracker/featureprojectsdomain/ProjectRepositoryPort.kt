package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TagID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.SprintMember
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Repository port for reading and writing project data.
 */
interface ProjectRepositoryPort {

    suspend fun projects(includeArchived: Boolean): List<Project>

    suspend fun project(id: ProjectID): Project?

    suspend fun workItems(projectID: ProjectID): List<WorkItem>

    suspend fun workItem(id: WorkItemID): WorkItem?

    suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int>

    suspend fun isEmpty(): Boolean

    /** Recorded status history of a work item, newest first. */
    suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange>

    /** Comment thread of a work item, oldest first. */
    suspend fun comments(workItemID: WorkItemID): List<Comment>

    /** Project sprints, unfinished first then by start date. */
    suspend fun sprints(projectID: ProjectID): List<Sprint>

    suspend fun sprint(id: SprintID): Sprint?

    suspend fun sprintWorkItems(id: SprintID): List<WorkItem>

    suspend fun sprintMembers(sprintID: SprintID): List<SprintMember> = emptyList()

    suspend fun createSprintMember(
        sprintID: SprintID,
        userID: UserID,
        availableStoryPoints: Int,
        now: Instant,
    ): SprintMember = throw AppError.Storage("This store does not hold sprint members.")

    suspend fun updateSprintMember(
        id: String,
        availableStoryPoints: Int,
        now: Instant,
    ): SprintMember = throw AppError.Storage("This store does not hold sprint members.")

    suspend fun deleteSprintMember(id: String, now: Instant) = Unit

    suspend fun seedSampleDataIfNeeded(): Boolean

    suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID> = emptyList(),
        /**
         * The optional repository URL the create form collected.
         *
         * Only a create carries one. The server stores it from the push that creates the
         * project — nothing else can, for a project that does not exist yet — and after that
         * the URL is admin-route metadata, which is why [updateProject] takes none. Normalised
         * by the caller and again by the store, so a direct port call cannot put a value on the
         * row that the push would then be refused for.
         */
        repoUrl: String? = null,
    ): Project

    suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>? = null,
        statuses: List<StatusDefinition>? = null,
    ): Project

    suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project

    suspend fun deleteProject(id: ProjectID)

    suspend fun createSprint(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int,
        goal: String? = null,
        startDate: Instant,
        endDate: Instant,
        ownerID: UserID?,
        now: Instant,
    ): Sprint

    suspend fun updateSprint(
        id: SprintID,
        name: String?,
        goalStoryPoints: Int?,
        goal: Patch<String?> = Patch.Unmentioned,
        startDate: Instant?,
        endDate: Instant?,
        ownerID: Patch<UserID?>,
        now: Instant,
    ): Sprint

    suspend fun finishSprint(id: SprintID, now: Instant): Sprint

    suspend fun reopenSprint(id: SprintID, now: Instant): Sprint

    suspend fun archiveSprint(id: SprintID, now: Instant): Sprint

    suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint

    suspend fun setSprintLocked(id: SprintID, isLocked: Boolean, now: Instant): Sprint

    suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem>

    suspend fun deleteSprint(id: SprintID, now: Instant)

    suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        statusKey: String? = null,
        priority: Priority,
        assigneeID: UserID?,
        dueDate: Instant?,
        storyPoints: Int? = null,
        sprintID: SprintID? = null,
        now: Instant,
        blockedByIDs: List<WorkItemID> = emptyList(),
        recurrence: Recurrence? = null,
        tagIDs: List<TagID> = emptyList(),
    ): WorkItem

    suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assigneeID: Patch<UserID?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
        blockedByIDs: Patch<List<WorkItemID>> = Patch.Unmentioned,
        recurrence: Patch<Recurrence?> = Patch.Unmentioned,
        tagIDs: Patch<List<TagID>> = Patch.Unmentioned,
    ): WorkItem

    suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        statusKey: String? = null,
        rank: Int?,
        now: Instant,
    ): WorkItem

    suspend fun deleteWorkItem(id: WorkItemID)

    suspend fun retryStuckChanges(now: Instant): Int

    suspend fun addComment(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID?,
        now: Instant,
    ): Comment

    suspend fun attachments(workItemID: WorkItemID): List<Attachment>

    suspend fun addAttachment(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        now: Instant,
    ): Attachment

    suspend fun deleteAttachment(id: AttachmentID)

    suspend fun queueAttachmentChange(id: AttachmentID, now: Instant): Boolean = false

    suspend fun attachment(id: AttachmentID): Attachment? = null

    suspend fun attachmentBytes(id: AttachmentID): ByteArray? = null

    suspend fun cacheAttachmentBytes(id: AttachmentID, bytes: ByteArray): Attachment? = null

    suspend fun markAttachmentUploaded(id: AttachmentID, blobRef: String, uploadedAt: Instant) = Unit

    suspend fun pendingAttachmentIDs(): List<AttachmentID> = emptyList()

    fun changes(): Flow<ProjectChange>
}

data class ProjectChange(
    val kind: Kind,
    val projectID: ProjectID? = null,
) {
    enum class Kind {
        Projects,
        WorkItems,
        Sprints,
    }
}

/**
 * Represents a field value with distinction between "unmentioned" and "set to nothing".
 */
sealed interface Patch<out T> {
    data object Unmentioned : Patch<Nothing>
    data class Of<out T>(val value: T) : Patch<T>
}

suspend fun ProjectRepositoryPort.projects(): List<Project> = projects(includeArchived = false)
