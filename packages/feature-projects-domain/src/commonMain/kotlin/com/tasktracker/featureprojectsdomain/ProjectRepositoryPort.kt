package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.EpicID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TagID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Epic
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

    /**
     * Every epic this device holds, most recently touched first.
     *
     * Not scoped to a project: an epic spans several, and the Projects tab's Epic segment
     * is a single list. An epic may name projects this device cannot see — visibility is
     * decided by the server, so the list is "what arrived", not "what I may read".
     */
    suspend fun epics(): List<Epic> = emptyList()

    suspend fun epic(id: EpicID): Epic? = null

    /**
     * The items linked to an epic, in board order.
     *
     * This is also the epic's progress: the count is `size`, and the derived status is
     * [com.tasktracker.domain.deriveEpicStatus] over the same list. Only the items this
     * device can see are here, which is the permission boundary rather than a bug.
     */
    suspend fun workItemsForEpic(id: EpicID): List<WorkItem> = emptyList()

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

    /**
     * Creates an epic spanning [projectIDs], and queues it.
     *
     * The one-project minimum is the use case's rule, not this one: a store asked directly
     * for an epic with no projects writes what it was told. See
     * [com.tasktracker.featureprojectsdomain.CreateEpic].
     */
    suspend fun createEpic(
        projectIDs: List<ProjectID>,
        title: String,
        details: String,
        dueDate: Instant?,
        ownerID: UserID? = null,
        now: Instant,
    ): Epic = throw AppError.Storage("This store does not hold epics.")

    /**
     * A partial epic edit; [Patch.Unmentioned] leaves a field alone, and
     * `Patch.Of(null)` clears the due date.
     *
     * A changed project set **unlinks** items whose project left the set: a link the epic
     * no longer explains is one the server refuses on the item's next write. A null [ownerID]
     * leaves the stored owner alone; there is no unassigned epic.
     */
    suspend fun updateEpic(
        id: EpicID,
        title: String?,
        details: String?,
        dueDate: Patch<Instant?>,
        projectIDs: List<ProjectID>?,
        ownerID: UserID? = null,
        now: Instant,
    ): Epic = throw AppError.Storage("This store does not hold epics.")

    /**
     * Archives or restores an epic, and optionally its subtasks.
     *
     * [archiveSubtasks] is the difference between "put the story away" and "put the story
     * and its work away": when true every linked item's `isArchived` follows the epic's new
     * state, and the ones the epic filed are the ones a later unarchive restores.
     */
    suspend fun setEpicArchived(
        id: EpicID,
        isArchived: Boolean,
        archiveSubtasks: Boolean,
        now: Instant,
    ): Epic = throw AppError.Storage("This store does not hold epics.")

    /**
     * Links an item to an epic, or clears the link with `Patch.Of(null)`.
     *
     * The epic must exist and must span the item's project; the use case states that rule
     * to the user and the store enforces it again, because an offline queue can carry a
     * link the picker never saw.
     */
    suspend fun setWorkItemEpic(
        workItemID: WorkItemID,
        epicID: Patch<EpicID?>,
        now: Instant,
    ): WorkItem = throw AppError.Storage("This store does not hold epics.")

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
        /**
         * An epic document changed. Deliberately not a work-item kind: an epic spans
         * projects, and the list that shows it is not scoped to one.
         */
        Epics,
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
