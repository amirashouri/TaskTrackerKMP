package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TagID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.MentionRules
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.TagRules
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemRules
import com.tasktracker.domain.WorkItemStatus
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Creates a project after validating inputs.
 */
class CreateProject(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        name: String,
        keyPrefix: String,
        details: String = "",
        memberIDs: List<UserID> = emptyList(),
    ): Project {
        val name = ProjectRules.normaliseName(name)
        val keyPrefix = ProjectRules.normaliseKeyPrefix(keyPrefix)
        val details = ProjectRules.normaliseDetails(details)
        return repository.createProject(
            name = name,
            keyPrefix = keyPrefix,
            details = details,
            now = dateProvider.now(),
            memberIDs = memberIDs,
        )
    }
}

/**
 * Updates a project's name, details, or members.
 */
class UpdateProject(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        id: ProjectID,
        name: String? = null,
        details: String? = null,
        memberIDs: List<UserID>? = null,
    ): Project {
        val normalisedName = name?.let { ProjectRules.normaliseName(it) }
        val normalisedDetails = details?.let { ProjectRules.normaliseDetails(it) }
        if (normalisedName == null && normalisedDetails == null && memberIDs == null) {
            return repository.project(id)
                ?: throw AppError.Storage("That project no longer exists.")
        }
        return repository.updateProject(
            id = id,
            name = normalisedName,
            details = normalisedDetails,
            now = dateProvider.now(),
            memberIDs = memberIDs,
        )
    }
}

/** Creates a work item in a project. */
class CreateWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        projectID: ProjectID,
        title: String,
        details: String = "",
        status: WorkItemStatus = WorkItemStatus.Backlog,
        priority: Priority = Priority.Medium,
        assigneeID: UserID? = null,
        dueDate: Instant? = null,
        recurrence: Recurrence? = null,
    ): WorkItem {
        val title = WorkItemRules.normaliseTitle(title)
        val details = WorkItemRules.normaliseDetails(details)
        return repository.createWorkItem(
            projectID = projectID,
            title = title,
            details = details,
            status = status,
            priority = priority,
            assigneeID = assigneeID,
            dueDate = dueDate,
            recurrence = recurrence,
            now = dateProvider.now(),
        )
    }
}

/** Moves a work item between statuses. */
class MoveWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: WorkItemID, to: WorkItemStatus): WorkItem {
        val current = repository.workItem(id)
            ?: throw AppError.Validation("That work item no longer exists.")
        if (!WorkItemRules.canTransition(current.status, to)) {
            throw AppError.Validation(
                "A work item cannot go from ${current.status.wireValue} to ${to.wireValue}.",
            )
        }
        return repository.moveWorkItem(id = id, to = to, rank = null, now = dateProvider.now())
    }
}

sealed interface WorkItemSaveOutcome {
    data class Created(val workItemID: WorkItemID) : WorkItemSaveOutcome
    data class Updated(val workItemID: WorkItemID, val moved: Boolean) : WorkItemSaveOutcome
}

/**
 * Creates or updates a work item, handling status moves and re-ranking.
 */
class SaveWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        id: WorkItemID? = null,
        projectID: ProjectID,
        title: String,
        status: WorkItemStatus,
        details: String = "",
        priority: Priority = Priority.Medium,
        assigneeID: UserID? = null,
        dueDate: Instant? = null,
        storyPoints: Int? = null,
        blockedByIDs: List<WorkItemID> = emptyList(),
        initialSprintID: SprintID? = null,
        recurrence: Recurrence? = null,
        tagIDs: List<TagID> = emptyList(),
    ): WorkItemSaveOutcome {
        val title = WorkItemRules.normaliseTitle(title)
        val details = WorkItemRules.normaliseDetails(details)
        val blockers = blockedByIDs.filter { it != id }.distinct()
        val tags = tagIDs.distinct().take(TagRules.MAX_PER_ITEM)

        if (id == null) {
            val created = repository.createWorkItem(
                projectID = projectID,
                title = title,
                details = details,
                status = status,
                priority = priority,
                assigneeID = assigneeID,
                dueDate = dueDate,
                storyPoints = storyPoints,
                blockedByIDs = blockers,
                sprintID = initialSprintID,
                recurrence = recurrence,
                tagIDs = tags,
                now = dateProvider.now(),
            )
            return WorkItemSaveOutcome.Created(created.id)
        }

        val previous = repository.workItem(id)
        val updated = repository.updateWorkItem(
            id = id,
            title = title,
            details = details,
            priority = priority,
            assigneeID = Patch.Of(assigneeID),
            dueDate = Patch.Of(dueDate),
            storyPoints = Patch.Of(storyPoints),
            blockedByIDs = Patch.Of(blockers),
            recurrence = Patch.Of(recurrence),
            tagIDs = Patch.Of(tags),
            now = dateProvider.now(),
        )

        if (previous?.status == status) return WorkItemSaveOutcome.Updated(updated.id, moved = false)

        repository.moveWorkItem(id = id, to = status, rank = null, now = dateProvider.now())
        return WorkItemSaveOutcome.Updated(updated.id, moved = true)
    }
}

enum class ProjectRemovalOutcome {
    Archived,
    Deleted,
    AlreadyArchived,
}

/**
 * Removes a project or archives it if it contains unfinished work.
 */
class RemoveProject(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: ProjectID): ProjectRemovalOutcome {
        val project = repository.project(id)
            ?: throw AppError.Storage("That project no longer exists.")
        if (project.isArchived) return ProjectRemovalOutcome.AlreadyArchived

        val items = repository.workItems(id)
        val outstanding = items.filter { !it.status.isComplete }
        if (outstanding.isNotEmpty()) {
            repository.setArchived(id = id, isArchived = true, now = dateProvider.now())
            return ProjectRemovalOutcome.Archived
        }

        repository.deleteProject(id)
        return ProjectRemovalOutcome.Deleted
    }
}

/**
 * Expands a recurring work item, creating the next occurrence.
 */
class ExpandRecurrence(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: WorkItemID, timeZone: TimeZone = TimeZone.currentSystemDefault()): WorkItem? {
        val current = repository.workItem(id)
            ?: throw AppError.Validation("That work item no longer exists.")
        val rule = current.recurrence ?: return null
        val anchor = current.dueDate ?: return null
        val next = rule.nextOccurrenceAfter(anchor, timeZone) ?: return null

        return repository.createWorkItem(
            projectID = current.projectID,
            title = current.title,
            details = current.details,
            status = WorkItemStatus.Backlog,
            priority = current.priority,
            assigneeID = current.assigneeID,
            dueDate = next,
            storyPoints = current.storyPoints,
            blockedByIDs = current.blockedByIDs,
            recurrence = rule,
            tagIDs = current.tagIDs,
            now = dateProvider.now(),
        )
    }
}

class LoadComments(
    private val repository: ProjectRepositoryPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(workItemID: WorkItemID): List<Comment> =
        repository.comments(workItemID)
}

sealed interface AddCommentResult {
    data class Success(
        val comment: Comment,
        val mentions: List<MentionRules.Mention>,
    ) : AddCommentResult

    data object EmptyBody : AddCommentResult
    data object ParentNotFound : AddCommentResult
}

/**
 * Posts a comment on a work item.
 */
class AddComment(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID? = null,
    ): AddCommentResult {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return AddCommentResult.EmptyBody
        }
        if (parentID != null && repository.comments(workItemID).none { it.id == parentID }) {
            return AddCommentResult.ParentNotFound
        }
        val comment = repository.addComment(
            workItemID = workItemID,
            authorID = authorID,
            body = trimmed,
            parentID = parentID,
            now = dateProvider.now(),
        )
        val mentions = MentionRules.extractMentions(trimmed)
        return AddCommentResult.Success(comment, mentions)
    }
}

class LoadAttachments(
    private val repository: ProjectRepositoryPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(workItemID: WorkItemID): List<Attachment> =
        repository.attachments(workItemID)
}

sealed interface AttachFileResult {
    data class Success(val attachment: Attachment) : AttachFileResult
    data object BlankName : AttachFileResult
    data object EmptyFile : AttachFileResult
    data class TooLarge(val sizeBytes: Long, val limitBytes: Long) : AttachFileResult
}

/**
 * Attaches a file to a work item.
 */
class AttachFile(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    companion object {
        const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024L * 1024L

        fun refusal(fileName: String, bytes: ByteArray): AttachFileResult? = when {
            fileName.trim().isEmpty() -> AttachFileResult.BlankName
            bytes.isEmpty() -> AttachFileResult.EmptyFile
            bytes.size > MAX_ATTACHMENT_BYTES ->
                AttachFileResult.TooLarge(bytes.size.toLong(), MAX_ATTACHMENT_BYTES)
            else -> null
        }
    }

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): AttachFileResult {
        refusal(fileName, bytes)?.let { return it }
        return AttachFileResult.Success(
            repository.addAttachment(
                workItemID = workItemID,
                fileName = fileName.trim(),
                mimeType = mimeType,
                bytes = bytes,
                now = dateProvider.now(),
            ),
        )
    }
}

class DeleteAttachment(
    private val repository: ProjectRepositoryPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: AttachmentID) {
        repository.deleteAttachment(id)
    }
}

sealed interface UploadAttachmentResult {
    data object Uploaded : UploadAttachmentResult
    data object AlreadySynced : UploadAttachmentResult
    data object NoLocalBytes : UploadAttachmentResult
    data object NotYetOnServer : UploadAttachmentResult
}

/**
 * Uploads an attachment's binary bytes.
 */
class UploadAttachment(
    private val repository: ProjectRepositoryPort,
    private val transport: AttachmentBlobTransport,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: AttachmentID): UploadAttachmentResult {
        val attachment = repository.attachment(id) ?: return UploadAttachmentResult.AlreadySynced
        if (attachment.blobRef != null) return UploadAttachmentResult.AlreadySynced
        val projectID = attachment.projectID ?: return UploadAttachmentResult.AlreadySynced
        val bytes = repository.attachmentBytes(id) ?: return UploadAttachmentResult.NoLocalBytes
        return try {
            transport.upload(
                projectID = projectID,
                workItemID = attachment.workItemID,
                attachmentID = id,
                bytes = bytes,
            )
            repository.markAttachmentUploaded(
                id = id,
                blobRef = id.stringValue,
                uploadedAt = dateProvider.now(),
            )
            UploadAttachmentResult.Uploaded
        } catch (_: AppError.NotFound) {
            UploadAttachmentResult.NotYetOnServer
        }
    }
}

data class AttachmentSweepFailure(
    val id: AttachmentID,
    val error: AppError,
)

data class AttachmentSweepReport(
    val uploaded: List<AttachmentID> = emptyList(),
    val alreadySynced: List<AttachmentID> = emptyList(),
    val noLocalBytes: List<AttachmentID> = emptyList(),
    val notYetOnServer: List<AttachmentID> = emptyList(),
    val refused: List<AttachmentSweepFailure> = emptyList(),
    val interruptedBy: AttachmentSweepFailure? = null,
) {
    val attempted: Int
        get() = uploaded.size + alreadySynced.size + noLocalBytes.size +
            notYetOnServer.size + refused.size + if (interruptedBy == null) 0 else 1

    val isEmpty: Boolean get() = attempted == 0
}

/**
 * Sweeps and uploads pending attachments.
 */
class SweepPendingAttachments(
    private val repository: ProjectRepositoryPort,
    private val upload: UploadAttachment,
) {

    @Throws(CancellationException::class)
    suspend operator fun invoke(): AttachmentSweepReport {
        var report = AttachmentSweepReport()
        for (id in repository.pendingAttachmentIDs()) {
            val outcome = try {
                upload(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: AppError) {
                if (!failure.isRetryable) {
                    report = report.copy(refused = report.refused + AttachmentSweepFailure(id, failure))
                    continue
                }
                return report.copy(interruptedBy = AttachmentSweepFailure(id, failure))
            }
            report = when (outcome) {
                UploadAttachmentResult.Uploaded -> report.copy(uploaded = report.uploaded + id)
                UploadAttachmentResult.AlreadySynced ->
                    report.copy(alreadySynced = report.alreadySynced + id)
                UploadAttachmentResult.NoLocalBytes ->
                    report.copy(noLocalBytes = report.noLocalBytes + id)
                UploadAttachmentResult.NotYetOnServer ->
                    report.copy(notYetOnServer = report.notYetOnServer + id)
            }
        }
        return report
    }
}

class BackfillUnqueuedAttachments(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(CancellationException::class)
    suspend operator fun invoke(): Int {
        var queued = 0
        for (id in repository.pendingAttachmentIDs()) {
            if (repository.queueAttachmentChange(id, dateProvider.now())) queued += 1
        }
        return queued
    }
}

/**
 * Fetches an attachment's bytes, downloading and caching on first read.
 */
class FetchAttachment(
    private val repository: ProjectRepositoryPort,
    private val transport: AttachmentBlobTransport? = null,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: AttachmentID): ByteArray? {
        repository.attachmentBytes(id)?.let { return it }
        val transport = transport ?: return null
        val attachment = repository.attachment(id) ?: return null
        if (attachment.blobRef == null) return null
        val projectID = attachment.projectID ?: return null
        val bytes = transport.download(
            projectID = projectID,
            workItemID = attachment.workItemID,
            attachmentID = id,
        )
        repository.cacheAttachmentBytes(id, bytes)
        return bytes
    }
}
