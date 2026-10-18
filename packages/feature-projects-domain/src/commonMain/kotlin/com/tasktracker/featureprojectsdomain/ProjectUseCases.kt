package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.MentionRules
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemRules
import com.tasktracker.domain.WorkItemStatus
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * The decisions a view makes before anything reaches the store.
 *
 * ## `@Throws` lists two classes, and that is not padding
 *
 * Every declaration here is `suspend`, and Kotlin/Native **rejects** `@Throws` on a
 * suspend function unless `CancellationException` is among the listed classes. A JVM-only
 * run compiles the identical annotation without complaint, so this is the kind of mistake
 * that reaches `./gradlew check` rather than the module you were working in — which is
 * why the fix is recorded as a rule rather than as a patch.
 *
 * The second class is also the correct contract, not a concession to the compiler. A
 * suspend function can be cancelled at any suspension point, and a caller that writes
 * `catch (e: AppError)` around a use case is then relying on the compiler to keep
 * cancellation from being swallowed into a user-facing "something went wrong". Naming it
 * says the same thing to a reader: a cancelled screen is not a failed save.
 */

/**
 * Creates a project, after checking the inputs.
 *
 * The validation rules themselves live in `:packages:domain` because they describe the
 * entity; what lives here is the *decision* to apply them before the write, which is what
 * keeps a rejected edit from ever reaching the store and leaving a queued change behind
 * for a project that was never created.
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
        // Normalised here, before the port is touched. The data layer checks the same rules
        // again, and that is not duplication: this is the layer the UI actually goes
        // through, and a rejected edit should never reach a store or leave a queued change
        // behind for a project that was never created.
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
 * Renames a project, or rewrites its notes.
 *
 * Deliberately narrow: the key prefix is missing from this use case on purpose, because
 * a prefix is the root of every work item key the project has ever issued (`TT-142`).
 * Editing it would either orphan the issued keys or demand a cascade rewrite that the
 * port does not offer, and neither is an edit a rename form should be able to trigger.
 * A project's prefix is fixed at creation; if it is wrong, the project is wrong.
 *
 * Both fields are optional and `null` means "leave it alone", which is what makes an
 * edit that changed nothing a read instead of a write — a write would bump `updatedAt`
 * and reorder the dashboard without changing anything.
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
            // Nothing to change: reading beats writing, because a write would bump
            // `updatedAt` and reorder the dashboard without changing anything.
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
        // Same reasoning as [CreateProject]: the rules run here so a bad edit is refused
        // before the port is reached. [assigneeID] needs no normalisation — a [UserID] is
        // already an identity, and "unassigned" is exactly its `null`.
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

/** Moves a work item between columns. */
class MoveWorkItem(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: WorkItemID, to: WorkItemStatus): WorkItem {
        // Confirms the item is really there before moving it, so a stale screen — or a
        // second device having deleted it since — produces a clear "no longer exists"
        // rather than a bare failure from the write.
        //
        // It also reads the current status, which is the value a transition rule would
        // have to be checked against. [WorkItemRules.canTransition] is deliberately
        // permissive until the product decides which moves are legal, so this does not
        // pretend to enforce a policy that does not exist. When that policy is written
        // down, this is the line that starts using the status it just read.
        val current = repository.workItem(id)
            ?: throw AppError.Validation("That work item no longer exists.")
        if (!WorkItemRules.canTransition(current.status, to)) {
            // [WorkItemStatus.wireValue] rather than a display title, and this is a
            // deviation worth naming. The original interpolated `status.title` — "In
            // Progress" — into this sentence, and `:packages:domain` dropped `title`
            // because user-facing text cannot live behind a module that may not depend on
            // `:packages:design-system`, where `composeResources` is owned. The wire value
            // is the same string the Room column stores and the deep link routes on, so a
            // user quoting this message and a developer grepping the database are looking
            // at the same words.
            throw AppError.Validation(
                "A work item cannot go from ${current.status.wireValue} to ${to.wireValue}.",
            )
        }
        return repository.moveWorkItem(id = id, to = to, rank = null, now = dateProvider.now())
    }
}

/** What happened when a work item was saved. */
sealed interface WorkItemSaveOutcome {

    data class Created(val workItemID: WorkItemID) : WorkItemSaveOutcome

    /**
     * [moved] says whether the status also changed, which is a second, different write
     * and is worth telling apart when testing or logging.
     */
    data class Updated(val workItemID: WorkItemID, val moved: Boolean) : WorkItemSaveOutcome
}

/**
 * Creates a work item, or updates an existing one.
 *
 * One use case rather than two, because saving is a single decision the caller should not
 * have to make. The caller knows what the fields say; only this knows that a status
 * change is *also* a move — it has to re-rank the card into its new column, which a
 * field update deliberately does not do. Left in a view model, a status change eventually
 * saves without re-ordering, which looks fine on one device and puts two cards in the
 * same slot on another.
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
    ): WorkItemSaveOutcome {
        val title = WorkItemRules.normaliseTitle(title)
        val details = WorkItemRules.normaliseDetails(details)
        // A task cannot wait on itself, and duplicates are one blocker stated twice.
        // Sanitised here so every caller gets the same rule rather than each screening it.
        val blockers = blockedByIDs.filter { it != id }.distinct()

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
                now = dateProvider.now(),
            )
            return WorkItemSaveOutcome.Created(created.id)
        }

        // Read first, because "did the status change" is the whole question here. The
        // port's update takes no status at all, which is exactly what makes it easy to
        // forget the move.
        val previous = repository.workItem(id)
        val updated = repository.updateWorkItem(
            id = id,
            title = title,
            details = details,
            priority = priority,
            // Always an instruction, never "unmentioned". The editor holds the truth for
            // every field, so a cleared assignee has to reach the store as a clear; a
            // [Patch.Unmentioned] here would silently keep the previous value.
            assigneeID = Patch.Of(assigneeID),
            dueDate = Patch.Of(dueDate),
            storyPoints = Patch.Of(storyPoints),
            blockedByIDs = Patch.Of(blockers),
            // Same reasoning as the assignee above: the editor is the truth, so clearing
            // a repeat rule has to reach the store as a clear, not as silence.
            recurrence = Patch.Of(recurrence),
            now = dateProvider.now(),
        )

        if (previous?.status == status) return WorkItemSaveOutcome.Updated(updated.id, moved = false)

        repository.moveWorkItem(id = id, to = status, rank = null, now = dateProvider.now())
        return WorkItemSaveOutcome.Updated(updated.id, moved = true)
    }
}

/** What happened to a project when the user asked for it to be removed. */
enum class ProjectRemovalOutcome {
    /** Unfinished work was found, so the project was archived instead of destroyed. */
    Archived,

    /** Nothing was outstanding, so the project and its work items were deleted. */
    Deleted,

    AlreadyArchived,
}

/**
 * Removes a project, or archives it if that would throw away unfinished work.
 *
 * This is the reason a use case layer is worth having. "Delete project" is one button in
 * the UI and two very different operations underneath: a project whose work is all done
 * can go, and one with open items must not, because deleting it cascades into work items
 * that somebody is still relying on — and the cascade also queues a delete for every one
 * of them, so the server would lose them too.
 *
 * Archiving is the safe default. Nothing is destroyed, the project leaves the default
 * list, and the user can still find it.
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
        // `isComplete`, not `isTerminal`, on purpose. A closed item is finished with but
        // it is also a decision that was taken, and it sits in the project's archive — so
        // a project holding any of those is archived rather than deleted, and the record
        // of what was decided survives.
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
 * Creates the next instance of a recurring work item, dated at its next occurrence.
 *
 * The finished instance is left exactly as it is — status, resolvedAt, history — and a
 * *new* row carries the series forward. Sliding the original's due date instead would
 * make the completed occurrence vanish from the archive the moment the next one was
 * created, which is the one thing a recurring task must not do: the point of the series
 * is that each occurrence happened and was filed.
 *
 * The new instance is a fresh backlog card: same title, details, priority, assignee,
 * estimate and blockers; a cleared sprint, because a series that silently refills a
 * finished sprint's capacity is a surprise; and the same [Recurrence], so the series
 * continues until its `until` says stop.
 *
 * Returns `null` when the series has ended — no rule, no due date to step from, or the
 * next occurrence would fall after `until`. A caller showing "repeat" as a button
 * should hide it on that result rather than treat it as a failure.
 *
 * [timeZone] is a parameter rather than a read of the device's zone, for the reason the
 * domain rule takes it: a use case that picked its own zone would be untestable outside
 * one zone, and the editor — which chose the due date the user sees — is the layer that
 * knows which zone the dates on screen are in.
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
        // Stepped from the due date rather than from `now`: a weekly task due last
        // Friday, completed today, recurs on the Friday after — not a week from today.
        // A task with no due date has nothing to step from, and guessing one would
        // invent a schedule the user never set.
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
            now = dateProvider.now(),
        )
    }
}

/** Reads one work item's comment thread, oldest first. */
class LoadComments(
    private val repository: ProjectRepositoryPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(workItemID: WorkItemID): List<Comment> =
        repository.comments(workItemID)
}

/**
 * Result of posting a comment, including any mentions parsed from the body.
 *
 * The mention parsing runs in the use case so both the editor (for highlighting)
 * and the sync sink (for notification delivery) share the same rule from
 * [MentionRules]. This avoids a second screen forgetting the rule, and keeps the
 * store agnostic to what a "mention" means.
 */
sealed interface AddCommentResult {
    /** Comment was created successfully with the given mentions. */
    data class Success(
        val comment: Comment,
        val mentions: List<MentionRules.Mention>,
    ) : AddCommentResult

    /** The comment body was empty after trimming. */
    data object EmptyBody : AddCommentResult

    /** The parent comment being replied to no longer exists. */
    data object ParentNotFound : AddCommentResult
}

/**
 * Posts one comment — top-level or a reply — after the two rules only this can enforce.
 *
 * Both rules live here rather than in the store, deliberately: neither needs to see
 * anything the transaction can see (unlike the sprint-overlap rule, which does, and is
 * therefore enforced in the port), so a use case is the earliest shared place every
 * caller passes through. A check in the view would be the second screen that forgets it.
 *
 * **The blank refusal and the trim are one rule, not two.** A body of `"   "` is empty
 * after normalisation, so the refusal is on the normalised value — otherwise whitespace
 * would sail past the check and store a comment that renders as nothing.
 *
 * **A reply must name a comment that is still in the thread.** An orphaned reply is a
 * sentence addressing nobody, and the two reads racing a delete across devices is a real
 * window — so the parent is checked against the thread as this device holds it, and a
 * reply to a deleted comment is refused with a message that says so rather than stored
 * pointing at nothing.
 *
 * **Mentions are parsed from the trimmed body** using [MentionRules.extractMentions].
 * The result carries both the created comment and the list of mentions so callers can
 * highlight them in the editor or deliver notifications (see 1.3) without re-parsing.
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

/** Reads one work item's attachments, oldest first. */
class LoadAttachments(
    private val repository: ProjectRepositoryPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(workItemID: WorkItemID): List<Attachment> =
        repository.attachments(workItemID)
}

/**
 * Result of attaching a file, including the reason for every refusal.
 *
 * The refusals carry what the editor needs to say, not just that it must say no: a
 * "too large" banner that does not name the limit teaches nothing, and a size check
 * whose message omits the measured size cannot be mutation-tested against the number
 * the guard actually enforces.
 */
sealed interface AttachFileResult {
    /** The file was stored; the editor shows the row the use case returns. */
    data class Success(val attachment: Attachment) : AttachFileResult

    /** The pick produced no name — nothing was chosen, or the platform could not say. */
    data object BlankName : AttachFileResult

    /** The pick produced no bytes. */
    data object EmptyFile : AttachFileResult

    /** The pick is over the cap the blob store is prepared to hold. */
    data class TooLarge(val sizeBytes: Long, val limitBytes: Long) : AttachFileResult
}

/**
 * Attaches one picked file, after the two rules only this can enforce.
 *
 * Both rules live here rather than in the store, for the same reason the comment trim
 * does: neither needs anything the transaction can see, so a use case is the earliest
 * shared place every caller passes through. A check in the view would be the second
 * screen that forgets it; a check in the store would put policy next to persistence.
 *
 * **The size cap is a product choice, not a technical limit.** 25 MiB holds a slide deck,
 * a phone photo burst, or a minutes PDF with room to spare, while keeping the eager
 * in-memory pick — which the seam's port documents — bounded on every device. A larger
 * cap arrives with a streaming blob store, not with a larger constant.
 */
class AttachFile(
    private val repository: ProjectRepositoryPort,
    private val dateProvider: DateProvider,
) {

    companion object {
        /** The largest file the blob store will take, in bytes. */
        const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024L * 1024L
    }

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
    ): AttachFileResult {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) {
            return AttachFileResult.BlankName
        }
        if (bytes.isEmpty()) {
            return AttachFileResult.EmptyFile
        }
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            return AttachFileResult.TooLarge(bytes.size.toLong(), MAX_ATTACHMENT_BYTES)
        }
        return AttachFileResult.Success(
            repository.addAttachment(
                workItemID = workItemID,
                fileName = trimmed,
                mimeType = mimeType,
                bytes = bytes,
                now = dateProvider.now(),
            ),
        )
    }
}

/**
 * Removes one attachment, dropping its row and its blob.
 *
 * Thin by design: unlike attaching, removing has no guard the store cannot own itself —
 * the port is idempotent, so there is no refusal to share between callers. The class
 * still exists (rather than letting the view call the port) because a second screen
 * that calls the port directly is how the thread about which writes go through use
 * cases gets lost.
 */
class DeleteAttachment(
    private val repository: ProjectRepositoryPort,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: AttachmentID) {
        repository.deleteAttachment(id)
    }
}

/** What happened when an attachment's bytes were pushed. */
sealed interface UploadAttachmentResult {
    /** The server now holds the bytes; the local row is marked synced. */
    data object Uploaded : UploadAttachmentResult

    /** There is nothing to do: already synced, or the row is gone. */
    data object AlreadySynced : UploadAttachmentResult

    /** The metadata row exists but its bytes are not on this device. */
    data object NoLocalBytes : UploadAttachmentResult

    /**
     * The server does not have the metadata row yet, so it refused the bytes with `404`.
     *
     * Expected, not exceptional: the row is in the outbox and the upload runs right after
     * the local write, so the first attempt can beat the metadata push. A later attempt
     * (after a sync) succeeds.
     */
    data object NotYetOnServer : UploadAttachmentResult
}

/**
 * Pushes one attachment's bytes to the server, after the metadata has been queued.
 *
 * Deliberately separate from [AttachFile]: attaching must commit locally and return
 * immediately, while uploading is a network round trip that can take seconds and fail.
 * Fusing them would make the attach button wait for the radio, which is the one thing an
 * offline-first editor must never do.
 *
 * The upload is idempotent end to end — the server overwrites a blob and re-records the
 * same `blob_ref` — so a retry after a transport error is safe.
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
        // A pulled row can name no project only if a build wrote it before `projectID`
        // existed; without one the file API cannot scope the request, so there is nothing
        // safe to send.
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
                // The server names the blob after the attachment id; mirroring that keeps
                // the local "synced" flag honest without a second round trip to read it.
                blobRef = id.stringValue,
                uploadedAt = dateProvider.now(),
            )
            UploadAttachmentResult.Uploaded
        } catch (_: AppError.NotFound) {
            UploadAttachmentResult.NotYetOnServer
        }
    }
}

/**
 * Returns one attachment's bytes, downloading and caching them on first read.
 *
 * The local cache is the fast path and the only path when the device is offline: a file
 * this device attached is already on disk, so opening it must not touch the network. A
 * file another member attached arrives as metadata only, and its bytes are fetched here,
 * once, then cached.
 *
 * [transport] is nullable, and `null` means "this build has no file API" rather than "this
 * use case is unavailable". The distinction matters: a demo graph has no server, and a file
 * it attached is still on this device, so it must still be openable. Refusing to build the
 * use case without a transport — which is what the editor did first — made every local file
 * unopenable in exactly the build that has nothing else to open. With no transport, a cache
 * hit answers and a cache miss returns `null`.
 *
 * Returns `null` when there is nothing to open: the row is gone, or its bytes have not
 * reached the server yet. Both are "not available now" rather than failures the editor
 * should paint red.
 */
class FetchAttachment(
    private val repository: ProjectRepositoryPort,
    private val transport: AttachmentBlobTransport? = null,
) {

    @Throws(AppError::class, CancellationException::class)
    suspend operator fun invoke(id: AttachmentID): ByteArray? {
        repository.attachmentBytes(id)?.let { return it }
        // Checked before the row read: with no file API there is nothing a download could
        // use, so the round trip to learn that would buy nothing.
        val transport = transport ?: return null
        val attachment = repository.attachment(id) ?: return null
        // No `blobRef` means the bytes were never uploaded, so there is nothing to fetch
        // and no reason to spend a round trip learning that.
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
