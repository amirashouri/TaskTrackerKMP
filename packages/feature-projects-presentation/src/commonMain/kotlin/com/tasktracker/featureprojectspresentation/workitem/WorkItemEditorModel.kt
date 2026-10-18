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
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.AddComment
import com.tasktracker.featureprojectsdomain.AddCommentResult
import com.tasktracker.featureprojectsdomain.ArchiveWorkItem
import com.tasktracker.featureprojectsdomain.AttachFile
import com.tasktracker.featureprojectsdomain.AttachFileResult
import com.tasktracker.featureprojectsdomain.AttachmentBlobTransport
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
 * The work item editor, for both creating and editing.
 *
 * ## A draft, never a stored record
 *
 * The fields are plain Compose state and the original values are snapshotted at
 * construction, exactly as [ProjectEditorModel] does. The rule is not stylistic. A form
 * bound directly to the persisted `WorkItem` would save through the sync sink's own object:
 * a save that failed halfway would leave the edited values already written to the store
 * with no outbox row to send them, so the edit would exist on this device and nowhere else.
 * A draft cannot do that, because nothing in the form is the record - the record only
 * comes into being inside `SaveWorkItem`.
 *
 * `WorkItem` is also a data class of `var`s, so holding one and reading it for `hasChanges`
 * would answer about the store rather than about the draft, and a concurrent remote edit
 * would move the goalposts under a form mid-edit.
 *
 * ## A status change is a move, and this model does not pretend otherwise
 *
 * [SaveWorkItem] is one use case rather than two precisely because a status change is also
 * a re-rank: the card has to land in its new column, which a field update deliberately does
 * not do. Left in a view model, a status change eventually saves without re-ordering,
 * which looks fine on one device and puts two cards in the same slot on another. So the
 * status is an ordinary field here and the use case is what knows it is two writes.
 */
class WorkItemEditorModel(
    private val projectID: ProjectID,
    /**
     * The work item being edited, or `null` when creating. Read once, for the initial field
     * values and the change snapshot; never read again - see the class comment.
     */
    private val existing: WorkItem?,
    /**
     * Held for exactly one read: the key the project issues on create.
     *
     * `WorkItemSaveOutcome.Created` carries the new id but not the key, and the key is
     * `prefix-number` with the number depending on what that project has already handed
     * out. So the editor shows the key it was given rather than composing one locally -
     * a locally composed `TT-1` would be right for a new project and wrong for the tenth.
     */
    private val repository: ProjectRepositoryPort,
    dateProvider: DateProvider,
    initialStatus: WorkItemStatus? = null,
    private val initialSprintID: SprintID? = null,
    /**
     * Who "me" is for the assignee picker, or `null` when no session is known.
     *
     * The picker offers unassigned and this identity — the only assignment the directory
     * can honestly resolve today, since a member list does not exist yet. An identity
     * arriving from a future sync keeps working: the field is a `UserID`, not a name.
     */
    val currentUserID: UserID? = null,
    /**
     * The server's file API, or `null` when this graph has no server.
     *
     * A null port is not an error: an offline demo has no blob store to upload to, so an
     * attachment stays local there. The upload/fetch use cases are built only when the port
     * is present, and a null one makes the background upload a no-op rather than a failure
     * the user would see after a perfectly successful attach.
     */
    blobTransport: AttachmentBlobTransport? = null,
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
    private val fetchBlob = blobTransport?.let { FetchAttachment(repository, it) }

    // MARK: Comments

    /**
     * The thread as this device holds it, oldest first.
     *
     * Loaded once per model (the host calls [refreshComments] when the editor opens) and
     * appended to after each [submitComment] — the write returns the stored comment, so
     * the view updates from the value it just created rather than re-reading the store.
     * A second device's comment arrives through the change announcement, which the host
     * turns into another [refreshComments].
     */
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set

    /** The composer's draft, and the comment a send would reply to. */
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

    /**
     * Reloads the thread — after open, and after a change announcement from elsewhere.
     *
     * A no-op while creating: there is no id yet, and a comment cannot be filed against a
     * work item that does not exist.
     */
    suspend fun refreshComments() {
        val id = existing?.id ?: return
        comments = reporting { loadComments(id) } ?: comments
    }

    /**
     * Posts the draft as [currentUserID], clearing the composer only on success.
     *
     * A refusal — blank body, reply to a comment that is gone — lands in the shared
     * [errorMessage] banner via [reporting] and leaves both the draft and the reply
     * target alone, so the text is never thrown away by a failed send.
     *
     * Mentions are parsed in the use case and available in [AddCommentResult.Success.mentions]
     * for the editor to highlight or for the sync sink to deliver notifications (see 1.3).
     */
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

    /**
     * The files as this device holds them, oldest first.
     *
     * Loaded once per model (the host calls [refreshAttachments] when the editor opens)
     * and appended to after each [attachFile] — the write returns the stored attachment,
     * so the view updates from the value it just created rather than re-reading the
     * store. A file is bigger than a comment and reading the store again would mean
     * touching rows for blobs, which is exactly what the listing does not do — so the
     * append matters more here than in the thread.
     */
    var attachments by mutableStateOf<List<Attachment>>(emptyList())
        private set

    /** Whether an attach is in flight — the model has no other in-flight flag. */
    var isAttaching by mutableStateOf(false)
        private set

    /**
     * Files whose bytes are moving right now.
     *
     * A set rather than a single flag because [refreshAttachments] can retry several at
     * once, and a row that said "uploading" only while it was the *last* one tried would
     * be wrong about the others.
     */
    private var uploadingIDs by mutableStateOf<Set<AttachmentID>>(emptySet())

    /**
     * Files the last attempt refused for a reason a retry cannot fix.
     *
     * Kept so the row stops saying "waiting", and so the refresh sweep stops retrying a
     * request that will be refused identically every time. Cleared on a success.
     */
    private var refusedIDs by mutableStateOf<Set<AttachmentID>>(emptySet())

    /**
     * What to tell the user about one file's progress towards the server.
     *
     * The order of the checks is the contract. A row with a `blobRef` is synced whatever
     * else is true of it, and a graph with no file API reports [AttachmentSyncState.LocalOnly]
     * before anything else — there is no queue to be waiting in when there is no server to
     * send to, and a "waiting" label there would be a spinner that can never resolve.
     */
    fun syncStateOf(attachment: Attachment): AttachmentSyncState = when {
        attachment.blobRef != null -> AttachmentSyncState.Synced
        uploadBlob == null -> AttachmentSyncState.LocalOnly
        attachment.id in uploadingIDs -> AttachmentSyncState.Uploading
        attachment.id in refusedIDs -> AttachmentSyncState.Failed
        else -> AttachmentSyncState.Waiting
    }

    /**
     * Reloads the files — after open, and after a change announcement from elsewhere.
     *
     * A no-op while creating, for the same reason comments are: there is no id yet, and
     * a file cannot be filed against a work item that does not exist.
     */
    suspend fun refreshAttachments() {
        val id = existing?.id ?: return
        attachments = reporting { loadAttachments(id) } ?: attachments
        // Retry any bytes that have not reached the server. A refresh follows a sync change
        // announcement, which is exactly when the metadata push is most likely to have
        // landed — so a `404` from the first, immediate attempt is recovered here rather
        // than waiting for the user to attach the file again. Snapshot the ids first: each
        // upload can rewrite `attachments`, and iterating a live list would be undefined.
        // Refused files are skipped: a `413` is a verdict, not a delay, and re-sending it on
        // every refresh would re-raise the banner for a file the user must shrink themselves.
        val pending = attachments
            .filter { it.blobRef == null && it.id !in refusedIDs }
            .map { it.id }
        for (pendingID in pending) {
            uploadAttachment(pendingID)
        }
    }

    /**
     * Stores one picked file with the use case's result handling the refusals.
     *
     * A refusal — blank name, empty file, over the cap — lands in the shared
     * [errorMessage] banner via [onFailure] rather than silently, deliberately unlike
     * [submitComment]'s quiet return: an attach attempt always comes from an explicit
     * button press the user watched fail, and swallowing it would teach that the button
     * does nothing. The banner names the measured size and the limit, so a TooLarge the
     * user can read about is one they can fix on their next pick.
     */
    suspend fun attachFile(picked: PickedFile): Attachment? {
        val id = existing?.id ?: return null
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

            AttachFileResult.BlankName -> {
                onFailure(AppError.Validation("The picked file has no name to file it under."))
                null
            }

            AttachFileResult.EmptyFile -> {
                onFailure(AppError.Validation("The picked file is empty — nothing was stored."))
                null
            }

            is AttachFileResult.TooLarge -> {
                onFailure(
                    AppError.Validation(
                        "The file is ${formatAttachmentSize(result.sizeBytes)}; " +
                            "the limit is ${formatAttachmentSize(result.limitBytes)}.",
                    ),
                )
                null
            }
        }
    }

    /**
     * Pushes one attachment's bytes in the background, after [attachFile] returned.
     *
     * ## Which failures are silent, and why that is not one rule
     *
     * The metadata is already queued, so a *transient* failure — no network, a 500, a
     * timeout — is retried by the next refresh and must stay quiet: painting a banner over
     * an attach the user watched succeed, for a problem that fixes itself, teaches them to
     * distrust the button. A *refusal* is the opposite. A `413` means this file will be
     * refused every time it is sent, so silence would leave a row saying "waiting" forever
     * on something that will never arrive. The split is [AppError.isRetryable], the same
     * property the sync engine retries on, so the two cannot drift into disagreeing about
     * what "try again" means.
     *
     * Success sets `blobRef`, which is what the list renders as "synced".
     */
    suspend fun uploadAttachment(id: AttachmentID) {
        val useCase = uploadBlob ?: return
        // Marked before the first suspension so the row says "uploading" from the frame the
        // attempt starts, and cleared in `finally` so a cancellation cannot strand a row in
        // a state no later attempt would clear.
        uploadingIDs = uploadingIDs + id
        try {
            when (val result = useCase(id)) {
                UploadAttachmentResult.Uploaded, UploadAttachmentResult.AlreadySynced -> {
                    refusedIDs = refusedIDs - id
                    // Re-read the one row rather than the whole list: the upload changed only
                    // `blobRef`/`uploadedAt`, and a full refresh would race a concurrent edit.
                    // `AlreadySynced` re-reads too, so a row that is synced stops saying
                    // "waiting" — it can be reached with a stale list after another device
                    // uploaded the same file.
                    repository.attachment(id)?.let { updated ->
                        attachments = attachments.map { if (it.id == id) updated else it }
                    }
                }

                // Nothing left on this device to send, and nothing that will put it back:
                // retrying cannot help, so the row stops promising rather than queueing
                // forever. No banner — the user did not press anything for this one.
                UploadAttachmentResult.NoLocalBytes -> refusedIDs = refusedIDs + id

                // The metadata push has not landed yet. The next refresh retries.
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

    /**
     * The bytes to open for one attachment, downloading and caching them on first read.
     *
     * Returns `null` when there is nothing to open yet — the row is gone, or its bytes have
     * not reached the server. A download failure is a real failure the user asked for, so
     * it lands in the banner via [reporting] rather than being swallowed.
     */
    suspend fun attachmentBytes(id: AttachmentID): ByteArray? {
        val useCase = fetchBlob ?: return null
        return reporting { useCase(id) }
    }

    /**
     * Removes one file, dropping it from the list only if the store agreed.
     *
     * The row is removed optimistically-but-verified — the local list follows the use
     * case's return rather than appending first — because a removal that failed in the
     * store while the view already dropped the row is the exact desync the comments'
     * append-only flow avoids having by never needing it.
     */
    suspend fun removeAttachment(id: AttachmentID) {
        reporting { dropAttachment(id) } ?: return
        attachments = attachments.filterNot { it.id == id }
        // The two upload sets are keyed by id and would otherwise keep an entry for a row
        // that no longer exists for as long as this editor stays open.
        uploadingIDs = uploadingIDs - id
        refusedIDs = refusedIDs - id
    }

    private val original: Draft? = existing?.let { Draft.of(it) }

    /** The title. Raw text as typed; `WorkItemRules` trims and refuses on save. */
    var title: String by mutableStateOf(existing?.title.orEmpty())
        private set

    /** The description. The card shows at most a couple of lines of this. */
    var details: String by mutableStateOf(existing?.details.orEmpty())
        private set

    /** The story points estimate, or `null` for unestimated. */
    var storyPoints: Int? by mutableStateOf(existing?.storyPoints)
        private set

    /** The story points text field value as typed. */
    var storyPointsText: String by mutableStateOf(existing?.storyPoints?.toString().orEmpty())
        private set

    /**
     * The requested board lane, or [WorkItemStatus.Backlog] for other creation paths.
     *
     * [WorkItemStatus.Closed] is not offered for a new task: it is the archive-only status,
     * and reaching it by picking it in a dropdown would make a status that is supposed to
     * be entered through the guarded move reachable by accident.
     */
    var status: WorkItemStatus by mutableStateOf(
        existing?.status ?: initialStatus ?: WorkItemStatus.Backlog,
    )
        private set

    /** The priority. */
    var priority: Priority by mutableStateOf(existing?.priority ?: Priority.Medium)
        private set

    /** Who the item is assigned to, or `null` for unassigned. */
    var assigneeID: UserID? by mutableStateOf(existing?.assigneeID)
        private set

    /** The due date, or `null` for none. */
    var dueDate: Instant? by mutableStateOf(existing?.dueDate)
        private set

    /**
     * The repeat rule, or `null` for a one-off.
     *
     * The day a weekly/monthly/yearly rule repeats on is fixed when the picker choice is
     * made, from the due date as it stands then — see [WorkItemRecurrenceRow]. The model
     * stores the rule as chosen and never re-derives it, so an edit to the due date does
     * not silently rewrite a series the user already set.
     */
    var recurrence: Recurrence? by mutableStateOf(existing?.recurrence)
        private set

    /**
     * The ids this task waits on, as a draft list.
     *
     * Held as ids rather than resolved items: the editor is a snapshot and must not re-read
     * the store on every keystroke, so resolution happens at the call site that renders the
     * pills. Dedupe and self-exclusion happen on add, so the save path never has to defend
     * against either.
     */
    var blockedByIDs: List<WorkItemID> by mutableStateOf(existing?.blockedByIDs.orEmpty())
        private set

    /** The id and key of the task once a save has succeeded. */
    var savedWorkItemID: WorkItemID? by mutableStateOf(existing?.id)
        private set

    /** Whether this editor is creating rather than editing. */
    val isCreating: Boolean get() = existing == null

    /**
     * The work item's key, which the editor shows but never lets the user change.
     *
     * `null` when creating, because the key does not exist until the project issues it -
     * which is also why the create path cannot show one and the save result can.
     */
    var issuedKey: WorkItemKey? by mutableStateOf(existing?.key)
        private set

    /**
     * Whether this task is already filed away, which hides every action that would move it.
     *
     * A terminal task has left the flow, so "file away" has nothing left to do and the
     * board has nowhere to move it to. [WorkItemStatus.isTerminal] is the test, and
     * `ArchiveWorkItem` enforces the same one.
     */
    val isFiledAway: Boolean get() = status.isTerminal

    /**
     * The statuses this editor offers.
     *
     * Hidden only for [WorkItemStatus.Closed], which is archive-only. [WorkItemStatus.Done]
     * is terminal for due-date and archive rules but remains a board lane, so choosing it
     * must not remove the status picker from the editor.
     */
    val selectableStatuses: List<WorkItemStatus>
        get() = if (status == WorkItemStatus.Closed) {
            emptyList()
        } else {
            WorkItemStatus.entries.filterNot { it == WorkItemStatus.Closed }
        }

    /**
     * Whether the draft differs from what it started as.
     *
     * Trims before comparing for the same reason [ProjectEditorModel.hasChanges] does: the
     * use case trims on the way in, so saving whitespace-only differences would bump
     * `updatedAt` and reorder the board without changing anything.
     */
    val hasChanges: Boolean
        get() {
            val current = Draft(
                title = title.trim(),
                details = details.trim(),
                status = status,
                priority = priority,
                assigneeID = assigneeID,
                dueDate = dueDate,
                storyPoints = storyPoints,
                blockedByIDs = blockedByIDs,
                recurrence = recurrence,
            )
            return original == null || current != original
        }

    /** Whether save is available. A task needs a title; everything else has a default. */
    val canSave: Boolean get() = title.isNotBlank()

    /** Sets the title. */
    fun onTitleChanged(value: String) {
        title = value
    }

    /** Sets the description. */
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

    /** Sets the status. Ignored for a closed task, which has left the workflow. */
    fun onStatusChanged(value: WorkItemStatus) {
        if (status == WorkItemStatus.Closed) return
        status = value
    }

    /** Sets the priority. */
    fun onPriorityChanged(value: Priority) {
        priority = value
    }

    /** Assigns to [value], or clears the assignment for `null`. */
    fun onAssigneeChanged(value: UserID?) {
        assigneeID = value
    }

    /** Sets or clears the due date. */
    fun onDueDateChanged(value: Instant?) {
        dueDate = value
    }

    /** Sets or clears the repeat rule. */
    fun onRecurrenceChanged(value: Recurrence?) {
        recurrence = value
    }

    /**
     * Adds blockers, ignoring duplicates and the item itself.
     *
     * Self-exclusion uses the saved id when editing; a new item has no id yet, so the
     * use case re-screens on save. Both layers screen because the model cannot see the
     * future id and the use case cannot see the draft's intent.
     */
    fun addBlockedBy(ids: Collection<WorkItemID>) {
        val self = savedWorkItemID
        blockedByIDs = (blockedByIDs + ids.filter { it != self }).distinct()
    }

    /** Removes one blocker. A no-op for an id that was never there. */
    fun removeBlockedBy(id: WorkItemID) {
        blockedByIDs = blockedByIDs.filterNot { it == id }
    }

    /**
     * Saves, creating or updating as appropriate, and returns the outcome.
     *
     * `null` when the save did not happen, so a caller can stay on the editor. The key is
     * captured from the result rather than being composed locally, because the project
     * issues it and the number depends on what the project has already handed out.
     */
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
            )
        } ?: return null

        when (outcome) {
            is WorkItemSaveOutcome.Created -> {
                savedWorkItemID = outcome.workItemID
                issuedKey = repository.workItem(outcome.workItemID)?.key
            }

            is WorkItemSaveOutcome.Updated -> savedWorkItemID = outcome.workItemID
        }

        // A recurring item that lands in Done is finished and the series has to advance —
        // by a new card, so this one keeps its own history. The test is the *transition*:
        // the original is the snapshot the form opened with, so re-editing an item that was
        // already Done (a typo in the title) must not spawn another instance per save, and
        // a create has no snapshot, so saving straight into Done expands exactly once.
        val expandedID = savedWorkItemID
        val enteredDone = status == WorkItemStatus.Done && original?.status != WorkItemStatus.Done
        if (recurrence != null && enteredDone && expandedID != null) {
            val next = reporting { expandRecurrence(expandedID) }
            // `reporting` nulls on failure *and* on a use case that found nothing to do;
            // the banner is what tells them apart. A series whose `until` has passed, or
            // one whose item has no anchor date, expands to nothing and that is not an
            // error — only a reported failure keeps the editor open on the saved card.
            if (next == null && errorMessage != null) return null
        }
        return outcome
    }

    /**
     * Files the task away, and reports whether it did.
     *
     * A no-op returning `false` for a task that is already filed away, rather than an
     * error: the editor is showing a terminal task, the "file away" action is hidden, and
     * reaching it at all means some call site is out of step with the state.
     */
    /**
     * Files the item away, and reports whether it did.
     *
     * Returns `false` without an error when the item is already filed away. That is a
     * local guard rather than a failure: the form knows the item is closed, so it does not
     * offer the action, and a second press is a no-op rather than a mistake to report. The
     * backlog has no such knowledge - anything can be handed to it - so it lets
     * `ArchiveWorkItem` refuse, and surfaces that refusal's wording. Two surfaces, two
     * behaviours, because the information they have differs.
     */
    suspend fun fileAway(): Boolean {
        val id = savedWorkItemID ?: return false
        if (isFiledAway) return false
        val archived = reporting { archiveWorkItem(id = id) } ?: return false
        // Adopt the status the store just decided on. This form does not observe the
        // store - a form is a snapshot, so that a change elsewhere cannot rewrite what the
        // user is typing - which means nothing would otherwise update it. Without this,
        // filing away succeeds and the form keeps offering statuses for a closed item, and
        // `selectableStatuses` stays non-empty because it is derived from [status].
        status = archived.status
        return true
    }

    /**
     * The draft's comparable state.
     *
     * A private value class rather than comparing the live fields against the live
     * `WorkItem`, for the reason in the class comment: the record is mutable and re-read
     * from under a form, and "has this changed" has to be a question about the draft.
     */
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
            )
        }
    }
}
