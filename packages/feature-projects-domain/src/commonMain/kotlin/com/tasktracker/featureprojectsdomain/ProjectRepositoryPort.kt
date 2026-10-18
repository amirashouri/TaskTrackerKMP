package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The port through which the projects feature reads and writes its data.
 *
 * Declared in the domain layer and implemented in the data layer, so the dependency
 * points inwards. Presentation depends on this interface and never on the Room
 * implementation, which is what stops an `@Entity` from leaking into a view — and in a
 * multiplatform port that is enforced rather than merely intended: a
 * `ProjectRecordEntity` is in `:packages:database`, which presentation is not allowed to
 * depend on at all, so a signature written here could not name one even by accident.
 *
 * Two things are deliberate about the shape:
 *
 * 1. **Every value crossing this interface is immutable.** A Room entity is mutable and
 *    the `ModelContext` equivalent explicitly refuses to be shared, so a record can never
 *    appear in a signature here. Domain values in, domain values out.
 * 2. **Writes return the value they produced.** A caller that creates a work item gets
 *    the stored version back, including the per-project key it was issued, rather than
 *    having to re-read to find out what it got. The alternative — a `suspend fun
 *    createWorkItem(…): Unit` — makes every caller a second round trip and lets the
 *    number it displays drift from the number it holds.
 *
 * `now` is a parameter on every write rather than something the implementation reads from
 * a clock. A store that calls `Instant.now()` itself is a store whose tests depend on
 * wall-clock time, and a store that takes the instant from its caller can be exercised
 * with a fixed one — which is what makes the `updatedAt` assertions in
 * `:packages:database` possible at all.
 */
interface ProjectRepositoryPort {

    // MARK: Reads

    suspend fun projects(includeArchived: Boolean): List<Project>

    suspend fun project(id: ProjectID): Project?

    suspend fun workItems(projectID: ProjectID): List<WorkItem>

    suspend fun workItem(id: WorkItemID): WorkItem?

    suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int>

    suspend fun isEmpty(): Boolean

    /**
     * The recorded status history of one work item, newest first.
     *
     * Newest first because that is the order the editor shows it in, and asking every
     * caller to remember to reverse a chronological list is how a history ends up reading
     * backwards in one screen and forwards in another.
     *
     * "Newest" means the instant, with the later-recorded change winning a tie. A card
     * created and triaged inside one clock tick produces two rows with the same
     * `changedAt`, and a sort on the timestamp alone would hand those back in insertion
     * order — which is the oldest order — so the order has to be made total deliberately
     * rather than inherited from the stability of the sort.
     *
     * Empty rather than throwing for an item with no history, which is the normal case for
     * one that has never been moved.
     */
    suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange>

    /**
     * One work item's comment thread, oldest first, replies carrying [Comment.parentID]
     * rather than being nested by the store.
     *
     * Oldest first — the opposite of [statusChanges], and for the mirror reason: a history
     * reads as a stack of recent events (newest on top), a conversation reads the way it
     * happened (the first comment where a reader starts). Grouping replies under parents is
     * the view's job; the order within the thread is the contract here.
     *
     * "Oldest" is `createdAt` with the insertion order breaking a tie, for the same reason
     * the status history breaks it: both timestamps are epoch milliseconds, so two comments
     * inside one clock tick are the normal case in a fast reply, and a sort on the instant
     * alone would hand them back in whichever order SQLite felt like.
     *
     * Empty for a comment-less item — the normal case, not an error.
     */
    suspend fun comments(workItemID: WorkItemID): List<Comment>

    /**
     * A project's sprints, unfinished ones first and then by start date.
     *
     * The ordering is part of the read contract, not a display concern: an unfinished sprint
     * is one that can be worked in, and the live ones have to lead every list that shows
     * them. Several may be unfinished at once; the ordering does not assume one.
     */
    suspend fun sprints(projectID: ProjectID): List<Sprint>

    suspend fun sprint(id: SprintID): Sprint?

    /**
     * Every work item in a sprint, in the board's own order.
     *
     * The domain's [com.tasktracker.domain.sprintSummary] filters a list it is given, so
     * this exists so a caller can hand it exactly the right items — and so the summary can
     * also be reached for the *whole project's* list without a second shape of filter.
     */
    suspend fun sprintWorkItems(id: SprintID): List<WorkItem>

    /**
     * Inserts the first-launch sample board if the store is completely empty.
     *
     * Returns whether anything was inserted, and does nothing once a single project
     * exists — so it is safe to call on every appearance of the dashboard, which is
     * exactly what the view layer does. It writes through the same path as any other
     * edit, so sample data is queued for sync like real work rather than being a back
     * door that behaves differently once a server is attached.
     */
    suspend fun seedSampleDataIfNeeded(): Boolean

    // MARK: Writes

    suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID> = emptyList(),
    ): Project

    /**
     * Applies a partial edit to a project.
     *
     * A `null` [name], [details] or [memberIDs] means "leave this field alone", never "set it to
     * null" — project fields are non-nullable, so an absent argument means no intention to
     * change that field. The
     * "clear the notes" affordance is therefore spelled as an empty string, which
     * [com.tasktracker.domain.ProjectRules.normaliseDetails] accepts. An empty member list
     * explicitly removes all members.
     *
     * The project key is deliberately absent. It is the prefix every work item key in the
     * project is built from, so rewriting it would orphan keys that other devices and
     * every server reference already use.
     */
    suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>? = null,
    ): Project

    suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project

    suspend fun deleteProject(id: ProjectID)

    // MARK: Sprints

    /**
     * Creates a sprint, and refuses if its window overlaps an **unfinished** sibling's.
     *
     * **The overlap rule is enforced here rather than in a use case**, and the reason is
     * that this is the only place that can see the project's other sprints. A check in a use
     * case is bypassed by anyone holding this port, and a check outside the write is a race
     * — two devices creating a sprint offline both read the same siblings and both store
     * theirs. Inside the transaction the read and the insert cannot interleave. The rule is
     * [com.tasktracker.domain.sprintBlockingOverlap]: two unfinished windows may not share
     * an instant, and a finished sprint is history rather than a timetable.
     *
     * Throws [com.tasktracker.core.AppError.Validation] rather than returning `null`: the
     * caller asked for something the store refuses to hold, and a nullable return would
     * make "created" and "refused" the same shape. The message names the blocking sprint.
     */
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

    /**
     * Applies a partial edit to a sprint.
     *
     * `name`, `goalStoryPoints`, `startDate` and `endDate` are plain optionals — `null`
     * leaves the field alone. [ownerID] and `goal` are [Patch]es, because both *can* be
     * emptied and "not mentioned" is a different instruction from "set it to nothing"; see
     * [Patch].
     *
     * `isFinished` is deliberately absent: finishing is [finishSprint] and reopening is
     * [reopenSprint], because each of them has an invariant the other does not — the date
     * that goes with it, and the outbox row that tells the server. A setter could not
     * maintain either.
     */
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

    /**
     * Closes a sprint and dates it.
     *
     * The date is set here and not left to the caller, because "finished" without a date is
     * the state [com.tasktracker.domain.Sprint.reconcileFinishedAt] exists to rule out, and
     * a caller who passed it would be able to write the state that method forbids.
     */
    suspend fun finishSprint(id: SprintID, now: Instant): Sprint

    /** Reopens a sprint and clears its finish date. See [finishSprint]. */
    suspend fun reopenSprint(id: SprintID, now: Instant): Sprint

    /**
     * Archives a finished sprint, filing it away from the default list.
     *
     * A sprint can be archived only once it is finished — the flow is scheduled →
     * in-progress → finished → archived. An already-archived sprint is a no-op rather than
     * an error, because the user's intent ("file this away") is already satisfied.
     *
     * The outbox row is the whole point: a server needs to know the sprint was archived, and
     * the local flag alone is not enough once a second device is in the picture.
     */
    suspend fun archiveSprint(id: SprintID, now: Instant): Sprint

    /**
     * Restores an archived sprint back to the active list.
     *
     * The inverse of [archiveSprint], offered so a user who archived by mistake can recover
     * without having to recreate the sprint. An unarchived sprint keeps its `isFinished` —
     * unarchiving does not reopen it.
     */
    suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint

    /**
     * Moves work items into a sprint, or back to the backlog.
     *
     * [Patch] rather than a nullable argument for the same reason as every other patch: a
     * caller that does not care about sprint membership and one that is emptying it are
     * giving different instructions.
     *
     * Returns the items as they now stand, so a caller can render the result without a
     * second read — the same contract as every other write on this interface.
     */
    suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem>

    /**
     * Deletes a sprint, returning its items to the backlog.
     *
     * Separate from the cascade in [deleteProject] because a sprint is deleted on its own:
     * an abandoned sprint's *history* is worth keeping, and its tasks are not worth losing.
     */
    suspend fun deleteSprint(id: SprintID, now: Instant)

    suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        priority: Priority,
        assigneeID: UserID?,
        dueDate: Instant?,
        storyPoints: Int? = null,
        sprintID: SprintID? = null,
        now: Instant,
        /**
         * Last, and defaulted, so every existing positional call site still reads `now` as its
         * final argument. Putting it before `now` silently re-typed that argument and broke a
         * dozen tests at once.
         */
        blockedByIDs: List<WorkItemID> = emptyList(),
        /** The repeat rule, or `null` for a one-off. Last for the same reason as [blockedByIDs]. */
        recurrence: Recurrence? = null,
    ): WorkItem

    /**
     * Applies a partial edit to a work item.
     *
     * [title], [details] and [priority] are plain optionals — a `null` leaves the field
     * alone, and none of the three can be meaningfully emptied.
     *
     * [assigneeID] and [dueDate] are [Patch]es, because both *can* be emptied and "not
     * mentioned" is a different instruction from "set it to nothing". See [Patch] for why
     * a plain nullable could not carry this.
     */
    suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assigneeID: Patch<UserID?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
        /** Last and defaulted, for the same reason as [createWorkItem]'s. */
        blockedByIDs: Patch<List<WorkItemID>> = Patch.Unmentioned,
        /**
         * The repeat rule as a [Patch], because a series can be *ended* by clearing it —
         * "stop repeating" is a real instruction, not an absence of one. Last, as above.
         */
        recurrence: Patch<Recurrence?> = Patch.Unmentioned,
    ): WorkItem

    /**
     * Moves a work item to [status], optionally at a specific [rank].
     *
     * A `null` [rank] appends to the end of the destination column rather than
     * preserving the item's current one, which would put a task dragged from the middle
     * of one lane at an arbitrary place in the next.
     */
    suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem

    suspend fun deleteWorkItem(id: WorkItemID)

    // MARK: Recovering refused changes

    /**
     * Puts every change the server refused permanently back in the queue, rebuilding the
     * payload of any the store itself made un-sendable, and answers how many went back.
     *
     * ## Why this is a repository method and not a queue one
     *
     * A dead row is a row nothing will ever claim again, and "retry" therefore cannot mean
     * "un-deaden it": the row holds a **snapshot** of the payload taken when the change was
     * made, and the server's refusal was a verdict on that snapshot. Sending the same bytes
     * again earns the same answer. Recovering means rebuilding each payload from the store
     * as it stands now — which is what this interface's writes already do — so the operation
     * belongs here, beside them, rather than in the sync layer that can only see opaque
     * bytes and does not know what a project is.
     *
     * ## The repair, and why its guard is what it is
     *
     * A project that names **nobody at all** was refused because the server authorizes the
     * creation of a project it does not yet hold against the payload's own member list — so
     * an empty list can never be accepted, no matter how often it is sent. [createProject]
     * now names the signed-in user on every project it makes, but a project created before
     * it did is stored with an empty list and its queued row holds that same empty list.
     * This is the only place that can still fix one: the project is read, the signed-in user
     * is added to a list that is empty, and the result is written back before the fresh
     * payload is queued.
     *
     * The guard is "names nobody" rather than "does not name me" on purpose. A project that
     * names other people but not the signed-in user is one they are not a member of, and
     * adding themselves would be a silent join. A project that names nobody is one whose
     * creator was never recorded, which is the whole of the defect.
     *
     * ## A repair that cannot succeed is not a hazard
     *
     * If the server does hold the project — the case this method cannot tell apart, because
     * the local store keeps no per-project server version — the refreshed payload is refused
     * again for the same reason and the row dead-letters again. Nothing is corrupted and
     * nothing is lost; the only cost is a push that was going to fail anyway. That is what
     * makes a guard this narrow sufficient without a version column to lean on.
     *
     * @param now the instant stamped on a repaired project's own `updatedAt`, so the change
     *   it records is dated when it was made rather than when it was rescued.
     * @return how many rows are back in the queue. A row whose entity no longer exists
     *   locally is dropped rather than sent: there is nothing left to tell the server about.
     */
    suspend fun retryStuckChanges(now: Instant): Int

    // MARK: Comments

    /**
     * Stores one comment on [workItemID] and queues its outbox row **in the same
     * transaction**, returning the stored value.
     *
     * The transaction is the whole rule from this port's KDoc applied to a new entity: the
     * comment write and its queued upsert commit together or not at all — a comment saved
     * with nothing queued to push it is exactly the disagreement the outbox exists to
     * prevent. `enqueue` therefore runs inside the caller's transaction, not in its own.
     *
     * Confirms the work item exists first, so a stale editor — or a second device having
     * deleted it — produces a clear "no longer exists" rather than a bare storage failure
     * from an id nothing references. That read also yields the project to announce.
     *
     * [body] is stored as given: trimming and the blank refusal belong to the
     * [com.tasktracker.featureprojectsdomain.AddComment] use case, where every caller
     * shares one rule instead of each remembering its own.
     */
    suspend fun addComment(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID?,
        now: Instant,
    ): Comment

    // MARK: Attachments

    /**
     * One work item's attachments, oldest first.
     *
     * Metadata only — the bytes live in the blob store and are not read to answer this.
     * A listing is what the editor renders; opening a file is a separate, platform-local
     * act that needs the blob, and conflating the two would make a thread listing depend
     * on files that may have been cleaned up behind the index.
     */
    suspend fun attachments(workItemID: WorkItemID): List<Attachment>

    /**
     * Stores one file's bytes in the blob store and writes its index row, returning the
     * stored value.
     *
     * **The blob is written before the row and deleted if the row fails.** The two writes
     * cannot share a transaction — file I/O is not transactional — so the order is chosen
     * such that the failure mode is an orphaned blob (cleaned up here, invisible to the
     * user) rather than an index row pointing at a file that does not exist (which would
     * render a card that can never open). A row without its bytes is useless, so unlike a
     * comment there is no honest "it will sync later": the write either produces both or
     * throws.
     *
     * **Queues an outbox row.** The metadata now travels the change feed, so another member
     * sees the file appear on the card; the bytes follow through the blob API (see
     * `UploadAttachment`). The row and the outbox entry commit in one transaction, so a
     * stored file always has a queued change behind it.
     *
     * Confirms the work item exists first, for the same reason [addComment] does.
     */
    suspend fun addAttachment(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        now: Instant,
    ): Attachment

    /**
     * Deletes one attachment's row and its blob, whichever is still there.
     *
     * Idempotent on the row — a delete of an already-gone id is a no-op, not an error —
     * and the blob is removed best-effort after the row commits: a leftover file costs
     * disk space, while a row whose file survives its own delete renders a card that
     * cannot open. Like a work item's child tables, the row goes silently; the editor's
     * own re-read is the announcement.
     */
    suspend fun deleteAttachment(id: AttachmentID)

    /**
     * One attachment's metadata row, or `null` when it has gone.
     *
     * The upload path needs the row's `projectID`, `workItemID` and `blobRef`, none of
     * which the work-item listing hands back for a single id without reading the whole
     * list. A read that names the id is also the honest answer to "did this row survive?".
     */
    suspend fun attachment(id: AttachmentID): Attachment? = null

    /**
     * The bytes cached on this device for one attachment, or `null` when they are not here.
     *
     * A metadata row can arrive from the server before its bytes do, so "no bytes" is a
     * normal state rather than an error — the caller decides whether to download.
     *
     * The default answers `null`: a store with no blob cache (a test fake, or a future
     * remote-only store) has nothing cached, and saying so is the truthful answer.
     */
    suspend fun attachmentBytes(id: AttachmentID): ByteArray? = null

    /**
     * Writes downloaded bytes into the local cache and points the row at them.
     *
     * Returns the updated metadata, or `null` when the row has gone. Never queues an outbox
     * entry: bytes are not part of the change feed, and a "cached a file" change would
     * push a write nobody asked for.
     *
     * The default is a no-op returning `null`, for a store with no blob cache.
     */
    suspend fun cacheAttachmentBytes(id: AttachmentID, bytes: ByteArray): Attachment? = null

    /**
     * Records that the bytes are now on the server, so the UI can say "synced".
     *
     * Local bookkeeping only — the server's `blob_ref` is what other members read — so this
     * must not queue an outbox entry. A missing row is a no-op: the attachment may have
     * been deleted while its upload was in flight.
     *
     * The default is a no-op, for a store with no blob cache.
     */
    suspend fun markAttachmentUploaded(id: AttachmentID, blobRef: String, uploadedAt: Instant) = Unit

    /**
     * Every attachment whose bytes are on this device and nowhere else, oldest first.
     *
     * The editor's own retry covers the rows it can see, so the case this answers is the one
     * it cannot: a user who attached a file and left the editor before the upload landed.
     * Their bytes sat local-only until they came back. A sweep that runs after a successful
     * sync cycle — when the metadata is known to be on the server, so an upload cannot be
     * refused with `404` — drains the list and closes that hole.
     *
     * A row that is already synced, and a pulled row whose bytes are not here, are both
     * excluded: the first has nothing to send and the second has nothing to send *with*, so
     * including either would have a sweep spin over rows it can only conclude nothing about.
     *
     * The default answers an empty list, the same "no blob cache" contract as the bytes
     * methods above: a store with nothing attached has nothing pending.
     */
    suspend fun pendingAttachmentIDs(): List<AttachmentID> = emptyList()

    // MARK: Change notifications

    /**
     * Emits once after every committed write — local edits and pulled remote changes
     * alike.
     *
     * This replaces SwiftUI's `@Query` in the view layer. `@Query` would mean importing
     * SwiftData into presentation and handing it model types, which is exactly the
     * coupling the module split exists to prevent. An invalidation signal plus a re-read
     * keeps the store an implementation detail: the view model holds plain `List<WorkItem>`
     * values and this is the only way it finds out they are stale.
     *
     * **A cold [Flow]**, with one subscriber per collection — which is what
     * `com.tasktracker.core.Broadcaster` provides, and why that class exists in `:core`
     * rather than a `MutableSharedFlow` at each call site. A hot shared flow would be
     * wrong here: the dashboard, the project screen and the board can each be collecting
     * at once, and a single shared cursor would hand the same event to whichever
     * collector got there first while the others never heard about the write.
     *
     * Events that arrive before a caller starts collecting are not replayed, and that is
     * correct: the first thing a collector does is read the current state, so a replay
     * would only ever tell it about something it has already accounted for.
     */
    fun changes(): Flow<ProjectChange>
}

/**
 * What changed, so a subscriber can decide whether it cares.
 *
 * A unit payload would say only "something, somewhere". That forces every view model to
 * re-read on every write anywhere in the feature — and it makes the invalidation contract
 * untestable, because a test cannot assert that a project-only change woke the board
 * model. Naming the entities is what lets both happen.
 *
 * @param projectID scopes the change to one project when it can be named, and is `null`
 *   when it cannot: a create has no prior project to scope to, and a pull that touched
 *   several projects at once is not more expensive to handle for being unscoped. A
 *   subscriber compares this against the project it is showing and ignores anything else.
 */
data class ProjectChange(
    val kind: Kind,
    val projectID: ProjectID? = null,
) {
    /** Which aggregate moved. */
    enum class Kind {
        Projects,
        WorkItems,
        /**
         * A sprint, or the membership of one.
         *
         * Separate from [WorkItems] rather than folded into it, because a board watching
         * this feature has to tell "the card moved" from "the card joined a sprint": the
         * first re-reads one lane and the second re-reads the sprint's whole item list. One
         * kind for both would make every board re-read every card whenever somebody tidied a
         * sprint — which is the over-broadcast this whole type exists to avoid.
         */
        Sprints,
    }
}

/**
 * A field's new value, distinguishing "not mentioned" from "set it to nothing".
 *
 * The original spells this `String??` and `Date??` — an optional optional, where the
 * outer `nil` means *leave this alone* and an inner `nil` means *clear it*. Kotlin has
 * no second level of nullability, and the two obvious replacements are both wrong:
 *
 * - A plain `UserID?` collapses the two instructions. A caller clearing an assignee
 *   passes `null`, the store reads it as "no instruction", and the field silently keeps
 *   its old value. The failure is invisible: the edit is reported as saved and the value
 *   is wrong, which is the one combination a user reports as a bug with no reproduction.
 * - A sentinel such as `""` for unassigned, or a magic `Instant`, puts a fake value
 *   into the domain to mean "no value". A work item then has a due date of `Instant.MIN`
 *   that has to be filtered out of every query, every sort and every test.
 *
 * So the three states are named rather than encoded in nesting. [Unmentioned] is the
 * absent instruction, [Of] is a value, and an [Of] whose value is `null` is the clear —
 * a real instruction, carried as one, and readable at the call site rather than inferred
 * from a type's parentheses.
 *
 * ```
 * repo.updateWorkItem(id, assigneeID = Patch.of(ada.id))   // assigned to Ada
 * repo.updateWorkItem(id, assigneeID = Patch.of(null))     // unassigned
 * repo.updateWorkItem(id, assigneeID = Patch.Unmentioned)  // dragged from the board
 * ```
 */
sealed interface Patch<out T> {

    /** The caller said nothing about this field. Leave whatever is stored alone. */
    data object Unmentioned : Patch<Nothing>

    /**
     * The caller set this field, possibly to `null`.
     *
     * The parameter is the nullable type it is assigned to, so [Patch.Of] over `UserID?`
     * is what clearing an assignee is built from — a `null` here is an instruction, not
     * an absence.
     */
    data class Of<out T>(val value: T) : Patch<T>
}

/**
 * The projects the default list shows.
 *
 * A default argument rather than the original's protocol extension, so a caller cannot
 * accidentally pass `includeArchived` positionally the wrong way round — `projects(true)`
 * reads as "give me projects, archived" until you go looking for what the `true` means.
 */
suspend fun ProjectRepositoryPort.projects(): List<Project> = projects(includeArchived = false)
