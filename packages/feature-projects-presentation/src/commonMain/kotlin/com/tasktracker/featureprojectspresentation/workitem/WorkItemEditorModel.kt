package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.TagID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.TagRules
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.AddComment
import com.tasktracker.featureprojectsdomain.AddCommentResult
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.AttachFile
import com.tasktracker.featureprojectsdomain.AttachFileResult
import com.tasktracker.featureprojectsdomain.AttachmentBlobTransport
import com.tasktracker.featureprojectsdomain.AttachmentOpener
import com.tasktracker.featureprojectsdomain.DeleteAttachment
import com.tasktracker.featureprojectsdomain.ExpandRecurrence
import com.tasktracker.featureprojectsdomain.FetchAttachment
import com.tasktracker.featureprojectsdomain.LoadAttachments
import com.tasktracker.featureprojectsdomain.LoadComments
import com.tasktracker.featureprojectsdomain.PickedFile
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.SaveWorkItem
import com.tasktracker.featureprojectsdomain.UploadAttachment
import com.tasktracker.featureprojectsdomain.UploadAttachmentResult
import com.tasktracker.featureprojectsdomain.WorkItemSaveOutcome
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Presentation model for creating and updating work items, isolating draft edits from store records.
 */
class WorkItemEditorModel(
    private val projectID: ProjectID,
    private val existing: WorkItem?,
    private val repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    initialStatus: WorkItemStatus? = null,
    private val initialSprintID: SprintID? = null,
    val currentUserID: UserID? = null,
    blobTransport: AttachmentBlobTransport? = null,
    opener: AttachmentOpener? = null,
    private val availableTags: List<ProjectTag>? = null,
) : ProjectModel() {

    private val saveWorkItem = SaveWorkItem(repository, dateProvider)
    private val archiveWorkItem = ArchiveWorkItem(repository, dateProvider)
    private val expandRecurrence = ExpandRecurrence(repository, dateProvider)

    private val loadComments = LoadComments(repository)
    private val postComment = AddComment(repository, dateProvider)
    private val loadAttachments = LoadAttachments(repository)
    private val storeAttachment = AttachFile(repository, dateProvider)
    private val dropAttachment = DeleteAttachment(repository)
    private val uploadBlob = blobTransport?.let { UploadAttachment(repository, it, dateProvider) }

    private val fetchBlob = FetchAttachment(repository, blobTransport)
    private val fileOpener = opener

    // MARK: Comments

    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set

    var commentDraft by mutableStateOf("")
        private set

    var commentReplyTo by mutableStateOf<CommentID?>(null)
        private set

    fun onCommentDraftChanged(value: String) {
        commentDraft = value
    }

    fun onCommentReply(parentID: CommentID) {
        commentReplyTo = parentID
    }

    fun onCommentCancelReply() {
        commentReplyTo = null
    }

    /** Reloads comment thread from store. */
    suspend fun refreshComments() {
        val id = existing?.id ?: return
        comments = reporting { loadComments(id) } ?: comments
    }

    /** Posts comment draft, updating thread list on success. */
    suspend fun submitComment() {
        val author = currentUserID ?: return
        val id = existing?.id ?: return
        val result = reporting {
            postComment(workItemID = id, authorID = author, body = commentDraft, parentID = commentReplyTo)
        } ?: return
        val success = result as? AddCommentResult.Success ?: return
        comments = comments + success.comment
        commentDraft = ""
        commentReplyTo = null
    }

    // MARK: Attachments

    var attachments by mutableStateOf<List<Attachment>>(emptyList())
        private set

    /** Files picked during creation, held locally until item ID is minted on save. */
    var stagedAttachments by mutableStateOf<List<PickedFile>>(emptyList())
        private set

    var isAttaching by mutableStateOf(false)
        private set

    private var uploadingIDs by mutableStateOf<Set<AttachmentID>>(emptySet())
    private var refusedIDs by mutableStateOf<Set<AttachmentID>>(emptySet())
    private var openingIDs by mutableStateOf<Set<AttachmentID>>(emptySet())

    fun isOpening(id: AttachmentID): Boolean = id in openingIDs

    /** Determines synchronization status of file attachment. */
    fun syncStateOf(attachment: Attachment): AttachmentSyncState = when {
        attachment.blobRef != null -> AttachmentSyncState.Synced
        uploadBlob == null -> AttachmentSyncState.LocalOnly
        attachment.id in uploadingIDs -> AttachmentSyncState.Uploading
        attachment.id in refusedIDs -> AttachmentSyncState.Failed
        else -> AttachmentSyncState.Waiting
    }

    /** Refreshes attachment list and retries pending blob uploads. */
    suspend fun refreshAttachments() {
        val id = existing?.id ?: return
        attachments = reporting { loadAttachments(id) } ?: attachments
        val pending = attachments
            .filter { it.blobRef == null && it.id !in refusedIDs }
            .map { it.id }
        for (pendingID in pending) {
            uploadAttachment(pendingID)
        }
    }

    /** Stores picked file attachment, reporting validation refusals if invalid. */
    suspend fun attachFile(picked: PickedFile): Attachment? {
        val id = savedWorkItemID ?: return null
        isAttaching = true
        val result = reporting {
            storeAttachment(
                workItemID = id,
                fileName = picked.fileName,
                mimeType = picked.mimeType,
                bytes = picked.bytes,
            )
        }
        isAttaching = false
        return when (result) {
            null -> null
            is AttachFileResult.Success -> {
                attachments = attachments + result.attachment
                result.attachment
            }
            else -> {
                reportRefusal(result)
                null
            }
        }
    }

    /** Stages file pick for creation; validates against [AttachFile.refusal]. */
    fun stageAttachment(picked: PickedFile) {
        AttachFile.refusal(picked.fileName, picked.bytes)?.let { refusal ->
            reportRefusal(refusal)
            return
        }
        stagedAttachments = stagedAttachments + picked
    }

    fun removeStagedAttachment(index: Int) {
        stagedAttachments = stagedAttachments.filterIndexed { i, _ -> i != index }
    }

    /** Uploads all staged attachments once item ID exists. */
    suspend fun flushStagedAttachments(): List<Attachment> {
        if (stagedAttachments.isEmpty()) return emptyList()
        val held = stagedAttachments
        stagedAttachments = emptyList()
        return held.mapNotNull { attachFile(it) }
    }

    private fun reportRefusal(refusal: AttachFileResult) {
        val error = when (refusal) {
            AttachFileResult.BlankName ->
                AppError.Validation("The picked file has no name to file it under.")
            AttachFileResult.EmptyFile ->
                AppError.Validation("The picked file is empty — nothing was stored.")
            is AttachFileResult.TooLarge -> AppError.Validation(
                "The file is ${formatAttachmentSize(refusal.sizeBytes)}; " +
                        "the limit is ${formatAttachmentSize(refusal.limitBytes)}.",
            )
            is AttachFileResult.Success -> return
        }
        onFailure(error)
    }

    /** Uploads attachment blob bytes in background. */
    suspend fun uploadAttachment(id: AttachmentID) {
        val useCase = uploadBlob ?: return
        uploadingIDs = uploadingIDs + id
        try {
            when (val result = useCase(id)) {
                UploadAttachmentResult.Uploaded, UploadAttachmentResult.AlreadySynced -> {
                    refusedIDs = refusedIDs - id
                    repository.attachment(id)?.let { updated ->
                        attachments = attachments.map { if (it.id == id) updated else it }
                    }
                }
                UploadAttachmentResult.NoLocalBytes -> refusedIDs = refusedIDs + id
                UploadAttachmentResult.NotYetOnServer -> Unit
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: AppError) {
            if (!failure.isRetryable) {
                refusedIDs = refusedIDs + id
                onFailure(failure)
            }
        } finally {
            uploadingIDs = uploadingIDs - id
        }
    }

    suspend fun attachmentBytes(id: AttachmentID): ByteArray? = reporting { fetchBlob(id) }

    /** Fetches attachment bytes if needed and invokes platform file opener. */
    suspend fun openAttachment(id: AttachmentID) {
        val opener = fileOpener ?: return
        if (id in openingIDs) return
        val attachment = attachments.firstOrNull { it.id == id } ?: return
        openingIDs = openingIDs + id
        try {
            val bytes = attachmentBytes(id) ?: return
            reporting { opener.open(attachment.fileName, attachment.mimeType, bytes) }
        } finally {
            openingIDs = openingIDs - id
        }
    }

    /** Removes attachment from repository and local list state. */
    suspend fun removeAttachment(id: AttachmentID) {
        reporting { dropAttachment(id) } ?: return
        attachments = attachments.filterNot { it.id == id }
        uploadingIDs = uploadingIDs - id
        refusedIDs = refusedIDs - id
    }

    private val original: Draft? = existing?.let { item ->
        val draft = Draft.of(item)
        draft.copy(tagIDs = draft.tagIDs.retainedIn(availableTags))
    }

    private val createBaseline: Draft = Draft(
        title = "",
        details = "",
        status = initialStatus ?: WorkItemStatus.Backlog,
        priority = Priority.Medium,
        assigneeID = null,
        dueDate = null,
        storyPoints = null,
        blockedByIDs = emptyList(),
        recurrence = null,
        tagIDs = emptyList(),
    )

    var title: String by mutableStateOf(existing?.title.orEmpty())
        private set

    var details: String by mutableStateOf(existing?.details.orEmpty())
        private set

    var storyPoints: Int? by mutableStateOf(existing?.storyPoints)
        private set

    var storyPointsText: String by mutableStateOf(existing?.storyPoints?.toString().orEmpty())
        private set

    var status: WorkItemStatus by mutableStateOf(
        existing?.status ?: initialStatus ?: WorkItemStatus.Backlog,
    )
        private set

    var priority: Priority by mutableStateOf(existing?.priority ?: Priority.Medium)
        private set

    var assigneeID: UserID? by mutableStateOf(existing?.assigneeID)
        private set

    var dueDate: Instant? by mutableStateOf(existing?.dueDate)
        private set

    var recurrence: Recurrence? by mutableStateOf(existing?.recurrence)
        private set

    var blockedByIDs: List<WorkItemID> by mutableStateOf(existing?.blockedByIDs.orEmpty())
        private set

    var tagIDs: List<TagID> by mutableStateOf(
        existing?.tagIDs.orEmpty().retainedIn(availableTags).distinct().take(TagRules.MAX_PER_ITEM),
    )
        private set

    var savedWorkItemID: WorkItemID? by mutableStateOf(existing?.id)
        private set

    val isCreating: Boolean get() = existing == null

    var issuedKey: WorkItemKey? by mutableStateOf(existing?.key)
        private set

    val isFiledAway: Boolean get() = status.isTerminal

    val selectableStatuses: List<WorkItemStatus>
        get() = if (status == WorkItemStatus.Closed) {
            emptyList()
        } else {
            WorkItemStatus.entries.filterNot { it == WorkItemStatus.Closed }
        }

    val hasChanges: Boolean
        get() {
            val current = currentDraft()
            return original == null || current != original
        }

    val hasUnsavedChanges: Boolean
        get() {
            if (currentDraft() != (original ?: createBaseline)) return true
            val pointsTextIsWork = storyPointsText.isNotBlank() && storyPoints == null
            return pointsTextIsWork || stagedAttachments.isNotEmpty()
        }

    private fun currentDraft() = Draft(
        title = title.trim(),
        details = details.trim(),
        status = status,
        priority = priority,
        assigneeID = assigneeID,
        dueDate = dueDate,
        storyPoints = storyPoints,
        blockedByIDs = blockedByIDs,
        recurrence = recurrence,
        tagIDs = tagIDs,
    )

    val canSave: Boolean get() = title.isNotBlank()

    fun onTitleChanged(value: String) {
        title = value
    }

    fun onDetailsChanged(value: String) {
        details = value
    }

    fun onStoryPointsChanged(value: String) {
        storyPointsText = value
        val trimmed = value.trim()
        storyPoints = when {
            trimmed.isEmpty() -> null
            else -> trimmed.toIntOrNull()?.takeIf { it >= 0 }
        }
    }

    fun onStatusChanged(value: WorkItemStatus) {
        if (status == WorkItemStatus.Closed) return
        status = value
    }

    fun onPriorityChanged(value: Priority) {
        priority = value
    }

    fun onAssigneeChanged(value: UserID?) {
        assigneeID = value
    }

    fun onDueDateChanged(value: Instant?) {
        dueDate = value
    }

    fun onRecurrenceChanged(value: Recurrence?) {
        recurrence = value
    }

    fun addBlockedBy(ids: Collection<WorkItemID>) {
        val self = savedWorkItemID
        blockedByIDs = (blockedByIDs + ids.filter { it != self }).distinct()
    }

    fun removeBlockedBy(id: WorkItemID) {
        blockedByIDs = blockedByIDs.filterNot { it == id }
    }

    fun toggleTag(id: TagID) {
        if (id in tagIDs) {
            tagIDs = tagIDs.filterNot { it == id }
            return
        }
        if (tagIDs.size >= TagRules.MAX_PER_ITEM) return
        if (availableTags != null && availableTags.none { it.id == id }) return
        tagIDs = tagIDs + id
    }

    /** Saves or updates work item draft, expanding recurrence series if transitioned to Done. Returns outcome. */
    suspend fun save(): WorkItemSaveOutcome? {
        if (!canSave) return null
        val outcome = reporting {
            saveWorkItem(
                id = savedWorkItemID,
                projectID = projectID,
                title = title,
                status = status,
                details = details,
                priority = priority,
                assigneeID = assigneeID,
                dueDate = dueDate,
                storyPoints = storyPoints,
                blockedByIDs = blockedByIDs,
                initialSprintID = if (savedWorkItemID == null) initialSprintID else null,
                recurrence = recurrence,
                tagIDs = tagIDs,
            )
        } ?: return null

        when (outcome) {
            is WorkItemSaveOutcome.Created -> {
                savedWorkItemID = outcome.workItemID
                issuedKey = repository.workItem(outcome.workItemID)?.key
            }
            is WorkItemSaveOutcome.Updated -> savedWorkItemID = outcome.workItemID
        }

        val expandedID = savedWorkItemID
        val enteredDone = status == WorkItemStatus.Done && original?.status != WorkItemStatus.Done
        if (recurrence != null && enteredDone && expandedID != null) {
            val next = reporting { expandRecurrence(expandedID) }
            if (next == null && errorMessage != null) return null
        }
        return outcome
    }

    /** Archives work item if not already terminal. Returns true on success. */
    suspend fun fileAway(): Boolean {
        val id = savedWorkItemID ?: return false
        if (isFiledAway) return false
        val archived = reporting { archiveWorkItem(id = id) } ?: return false
        status = archived.status
        return true
    }

    private data class Draft(
        val title: String,
        val details: String,
        val status: WorkItemStatus,
        val priority: Priority,
        val assigneeID: UserID?,
        val dueDate: Instant?,
        val storyPoints: Int?,
        val blockedByIDs: List<WorkItemID>,
        val recurrence: Recurrence?,
        val tagIDs: List<TagID>,
    ) {
        companion object {
            fun of(item: WorkItem) = Draft(
                title = item.title.trim(),
                details = item.details.trim(),
                status = item.status,
                priority = item.priority,
                assigneeID = item.assigneeID,
                dueDate = item.dueDate,
                storyPoints = item.storyPoints,
                blockedByIDs = item.blockedByIDs.toList(),
                recurrence = item.recurrence,
                tagIDs = item.tagIDs.toList(),
            )
        }
    }
}

private fun List<TagID>.retainedIn(catalog: List<ProjectTag>?): List<TagID> =
    catalog?.let { tags -> filter { id -> tags.any { it.id == id } } } ?: this