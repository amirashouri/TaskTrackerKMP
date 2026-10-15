package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.DateProvider
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.SystemDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseSprintID
import com.tasktracker.core.parseUserID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.core.randomCommentID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.ProjectRecord
import com.tasktracker.database.SessionCache
import com.tasktracker.database.SprintRecord
import com.tasktracker.database.StatusChangeRecord
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.database.WorkItemRecord
import com.tasktracker.database.inTransaction
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemRules
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintBlockingOverlap
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The only way to change a project or a work item.
 *
 * ## One transaction per mutation, always
 *
 * Every write here puts the record **and** its outbox row inside a single [inTransaction].
 * That is the whole reason this class exists, and splitting the two steps leaves a real hole:
 * a crash in between leaves an edit that exists locally and will never reach the server, with
 * nothing in the outbox to reveal it. Not a retry, not a diff, not an error — the user was
 * told it saved, and it is gone the next time the store is rebuilt.
 *
 * The outbox row goes in through `database.outboxDao().insert` and **not** through
 * [com.tasktracker.database.OutboxQueue.enqueue], because `enqueue` opens a transaction of
 * its own and Room refuses to nest them. That is why `OutboxQueue` documents `enqueue` as
 * living outside the protocol: a repository's enqueue has to share the caller's transaction
 * or it defeats the property above.
 *
 * ## Announced after the commit, never inside it
 *
 * Every announcement — [ProjectChangeBus.announceProjects],
 * [ProjectChangeBus.announceWorkItems] or [ProjectChangeBus.announceProjectChange] — is outside
 * its `inTransaction`. A listener that re-read on an uncommitted write would fetch the old
 * value, decide nothing had changed, and never look again — a bug that only appears under load,
 * which is the worst time to find one.
 *
 * ## Validation before the transaction
 *
 * Every rule is applied to the arguments *before* the write opens its transaction, and the
 * session is read there too. Both are deliberate:
 *
 * - A rejected edit must not roll back anything, and must not leave a queued change for a
 *   project that was never written. `normaliseTitle` throws, and a throw inside
 *   `inTransaction` becomes [AppError.Storage] — which would report a typo in a title as a
 *   disk failure.
 * - `RoomSessionCache.current()` opens a transaction of its own, so reading it inside one
 *   would nest. Reading it outside is also more honest: a signed-out user gets a blank author,
 *   not a failed edit.
 *
 * ## Reads return domain values, never records
 *
 * A record is a mutable storage shape. Handing one out would let a caller mutate the store
 * without a transaction, without an outbox row, and without an announcement.
 */
@OptIn(ExperimentalUuidApi::class)
class RoomProjectRepository(
    private val database: TaskTrackerDatabase,
    private val changeBus: ProjectChangeBus,
    /**
     * Who to credit a status change to.
     *
     * Optional, and absent is a legitimate state rather than a stub: it is what a store wired
     * without auth looks like, and every status change is then recorded with an unknown
     * author. That is the honest answer. The alternative — refusing to record history until a
     * session exists — would mean a signed-out user could move a task and get no history at
     * all.
     */
    private val sessionCache: SessionCache? = null,
    /**
     * Only [seedSampleDataIfNeeded] needs this, and it needs it because
     * [ProjectRepositoryPort.seedSampleDataIfNeeded] takes no `now` — every other write does.
     *
     * Injected rather than read from a clock inside the method, so a test can seed a board
     * whose timestamps it knows. Defaulted, because a caller wiring production has one real
     * clock and no reason to name it.
     */
    private val dateProvider: DateProvider = SystemDateProvider(),
    /**
     * Where attachment bytes live.
     *
     * Injected rather than constructed, for the same reason [dateProvider] is: a test
     * must be able to aim the store at a directory it owns (or at memory) instead of the
     * device's support folder. Defaulted to the production store, because a caller wiring
     * production has one real location and no reason to name it.
     */
    private val blobStore: AttachmentBlobStore = FileBlobStore(),
) : ProjectRepositoryPort {

    override fun changes(): Flow<ProjectChange> = changeBus.changes()

    // MARK: Reads

    override suspend fun projects(includeArchived: Boolean): List<Project> =
        inTransaction(database) {
            database.projectDao().all()
                .mapNotNull { it.domainValue }
                .filter { includeArchived || !it.isArchived }
        }

    override suspend fun project(id: ProjectID): Project? =
        inTransaction(database) { recordForProject(id)?.domainValue }

    override suspend fun workItems(projectID: ProjectID): List<WorkItem> =
        inTransaction(database) {
            database.workItemDao().byProject(projectID.stringValue).mapNotNull { it.domainValue }
        }

    override suspend fun workItem(id: WorkItemID): WorkItem? =
        inTransaction(database) { recordForWorkItem(id)?.domainValue }

    /**
     * Column counts for the board, in one grouped query.
     *
     * The original computed this by fetching every work item in the project and counting in
     * Swift, on a path whose KDoc claimed to do it "in one fetch rather than one per column" —
     * it did avoid one query per column, but it read the whole project to do it.
     * `GROUP BY statusRaw` is the query that comment was reaching for.
     *
     * A status this build does not recognise is left out of the map rather than folded into
     * one, so a column total and the sum of the columns cannot quietly disagree.
     */
    override suspend fun counts(projectID: ProjectID): Map<WorkItemStatus, Int> =
        inTransaction(database) {
            database.workItemDao().countsByStatus(projectID.stringValue)
                .mapNotNull { row ->
                    WorkItemStatus.fromWireValue(row.statusRaw)?.let { it to row.total }
                }
                .toMap()
        }

    override suspend fun isEmpty(): Boolean =
        inTransaction(database) { database.projectDao().count() == 0 }

    /**
     * One item's history, newest first, later-recorded winning a tie.
     *
     * Two things happen here that are worth naming, because both were silent data loss in the
     * original:
     *
     * - **A row this build cannot read is dropped, not thrown on.** A history with a gap in
     *   it is still a history, and one bad row must not empty the list.
     * - **The SQL already has the tie-break.** [com.tasktracker.database.StatusChangeDao.byWorkItem]
     *   sorts on `changedAt DESC, sequence DESC`, so the order is total and decided in one
     *   place. Sorting again in Kotlin would be a *second* answer to the same question, and a
     *   stable sort over an already-sorted list happens to agree today — which is exactly how
     *   two implementations of one rule drift apart.
     */
    override suspend fun statusChanges(workItemID: WorkItemID): List<StatusChange> =
        inTransaction(database) {
            database.statusChangeDao().byWorkItem(workItemID.stringValue)
                .mapNotNull { it.domainValue }
        }

    override suspend fun comments(workItemID: WorkItemID): List<Comment> =
        inTransaction(database) {
            database.commentDao().byWorkItem(workItemID.stringValue)
                .mapNotNull { it.domainValue }
        }

    // MARK: Project writes

    /**
     * Creates a project, queues it, and puts its creator in its member list.
     *
     * ## Why the creator is added here, and not by the caller
     *
     * The server refuses a push for a project it does not already have unless the payload's
     * member list names the pusher. A project that does not exist has no membership rows to
     * authorize against, so the payload is the only thing that can — which is exactly what
     * lets a creator push a project before any membership row exists.
     *
     * A project created *without* its creator in that list is therefore not merely
     * unauthorized, it is permanently unsyncable: the server answers `Rejected`, and the queue
     * dead-letters the change rather than retrying it. Adding the creator is what keeps an
     * ordinary "new project" from being that case.
     *
     * The rule lives in this layer rather than in `CreateProject` because the session is what
     * it needs, and this is where the session is already read — the same [currentUserStamp]
     * the outbox row is stamped with. Moving it into the use case would mean threading a
     * second identity port through the editor and the host for a rule the store can apply on
     * its own.
     *
     * The creator is appended rather than prepended, so the members the user picked keep the
     * order they arranged them in. A store with no session adds nobody, and that is the honest
     * answer rather than a stub: an unauthenticated build cannot push, so there is no payload
     * for the rule to protect.
     */
    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID>,
    ): Project {
        val stamp = currentUserStamp()
        val project = Project(
            id = randomProjectID(),
            keyPrefix = ProjectRules.normaliseKeyPrefix(keyPrefix),
            name = ProjectRules.normaliseName(name),
            details = ProjectRules.normaliseDetails(details),
            memberIDs = (memberIDs + listOfNotNull(stamp?.let(::parseUserID))).distinct(),
            createdAt = now,
            updatedAt = now,
        )

        inTransaction(database) {
            database.projectDao().insert(project.toRecord())
            enqueueProject(project, stamp, now)
        }
        changeBus.announceProjects(project.id)
        return project
    }

    override suspend fun updateProject(
        id: ProjectID,
        name: String?,
        details: String?,
        now: Instant,
        memberIDs: List<UserID>?,
    ): Project {
        val normalisedName = name?.let { ProjectRules.normaliseName(it) }
        val normalisedDetails = details?.let { ProjectRules.normaliseDetails(it) }
        val stamp = currentUserStamp()

        val updated = inTransaction(database) {
            val project = readProject(id)
            // The domain value is the working copy: it is a `var`-bearing class built fresh
            // from the row, so patching it here and converting once at the end keeps every
            // column name inside `RecordMapping`.
            normalisedName?.let { project.name = it }
            normalisedDetails?.let { project.details = it }
            memberIDs?.let { project.memberIDs = it.distinct() }
            project.updatedAt = now
            database.projectDao().update(project.toRecord())
            // The project *is* the payload, not the record. Encoding the record instead would
            // put storage column names on the wire and make the server depend on this module's
            // schema.
            enqueueProject(project, stamp, now)
            project
        }
        changeBus.announceProjects(id)
        return updated
    }

    override suspend fun setArchived(id: ProjectID, isArchived: Boolean, now: Instant): Project {
        val stamp = currentUserStamp()

        val updated = inTransaction(database) {
            val project = readProject(id)
            project.isArchived = isArchived
            project.updatedAt = now
            database.projectDao().update(project.toRecord())
            enqueueProject(project, stamp, now)
            project
        }
        changeBus.announceProjects(id)
        return updated
    }

    /**
     * Deletes a project and every work item under it.
     *
     * A delete row is queued for the project *and* for each item, in the same transaction.
     * Queueing only the project would leave the server holding work items that nothing local
     * references and nothing will ever remove — the mirror of a lost edit, and just as
     * permanent.
     *
     * Status history goes with them. It is local-only, so there is nothing to tell a server,
     * and a leftover row is not merely untidy: it is unreachable, uncleanable, and it would
     * reattach itself to a new work item if one were ever issued the same id.
     *
     * The cascade is written out by hand rather than declared as a foreign key, and
     * [WorkItemRecord] explains why: a database cascade cannot write an outbox row, and the
     * delete rows are the point.
     */
    override suspend fun deleteProject(id: ProjectID) {
        val stamp = currentUserStamp()

        // Blob paths collect here because files cannot die inside the transaction —
        // same discipline as `deleteWorkItem`, one level up.
        val orphanedBlobs = mutableListOf<String>()

        inTransaction(database) {
            val project = readProject(id)
            for (item in database.workItemDao().byProject(id.stringValue)) {
                database.statusChangeDao().deleteByWorkItem(item.id)
                // Per-item rather than `deleteByProject`, because the comments table
                // carries no project column — see [CommentRecord]'s KDoc. Two queries
                // in one transaction beats a denormalised column every future write
                // would have to keep honest.
                database.commentDao().deleteByWorkItem(item.id)
                // Attachments follow the same two-query pattern for the same reason —
                // the table deliberately carries no project column either — plus one
                // more: the blobs cannot die in the transaction. Their paths collect on
                // the project's orphan list and the files go after the commit.
                val blobPaths = database.attachmentDao().byWorkItem(item.id)
                    .map { it.blobPath }
                database.attachmentDao().deleteByWorkItem(item.id)
                database.workItemDao().deleteByID(item.id)
                enqueueDelete(OutboxEntity.WorkItem, item.id, stamp, item.updatedAt)
                orphanedBlobs.addAll(blobPaths)
            }
            // Sprints go before the project, and for the same reason the items do: queueing
            // only the project would leave the server holding sprints for a project nothing
            // local references and nothing will ever remove — the mirror of a lost edit, and
            // just as permanent. `deleteProject` was written before sprints existed, so this
            // is the exact omission this feature would have introduced had nobody re-read it.
            for (sprint in database.sprintDao().byProject(id.stringValue)) {
                database.sprintDao().deleteByID(sprint.id)
                enqueueDelete(OutboxEntity.Sprint, sprint.id, stamp, sprint.updatedAt)
            }
            database.statusChangeDao().deleteByProject(id.stringValue)
            database.projectDao().deleteByID(id.stringValue)
            enqueueDelete(OutboxEntity.Project, id.stringValue, stamp, project.updatedAt)
        }
        orphanedBlobs.forEach { safeDeleteBlob(it) }
        changeBus.announceProjectDeletion(id)
    }

    // MARK: Sprint reads

    override suspend fun sprints(projectID: ProjectID): List<Sprint> =
        database.sprintDao().byProject(projectID.stringValue).mapNotNull { it.domainValue }

    override suspend fun sprint(id: SprintID): Sprint? =
        database.sprintDao().byID(id.stringValue)?.domainValue

    override suspend fun sprintWorkItems(id: SprintID): List<WorkItem> =
        database.workItemDao().bySprint(id.stringValue).mapNotNull { it.domainValue }

    // MARK: Sprint writes

    /**
     * Creates a sprint, refusing when its window overlaps an **unfinished** one.
     *
     * The rule is [sprintBlockingOverlap]'s, not a rewrite of it: read every sprint the
     * project already has, ask which unfinished one shares an instant with the proposed
     * window, and refuse on the answer. A finished sprint is skipped by that helper, so
     * running a new sprint over history is allowed — only a live sprint is a schedule.
     *
     * The check is inside the transaction on purpose: two devices creating a sprint offline
     * both pass any check made outside it, and this store is local-first against a server
     * that also merges.
     *
     * Refused with [AppError.Validation] rather than `null`, and the message names the
     * blocking sprint so the caller can offer "finish that one first" rather than a dead end.
     */
    override suspend fun createSprint(
        projectID: ProjectID,
        name: String,
        goalStoryPoints: Int,
        goal: String?,
        startDate: Instant,
        endDate: Instant,
        ownerID: UserID?,
        now: Instant,
    ): Sprint {
        val normalisedName = ProjectRules.normaliseName(name)
        val stamp = currentUserStamp()

        val created = inTransaction(database) {
            // Proves the project exists, and gives the "no project" refusal a better
            // message than the id-only one `readProject` produces.
            readProject(projectID)

            val blocker = sprintBlockingOverlap(
                startDate,
                endDate,
                database.sprintDao().byProject(projectID.stringValue).mapNotNull { it.domainValue },
            )
            if (blocker != null) {
                throw AppError.Validation(
                    "'${blocker.name}' overlaps this window. Finish it before starting another.",
                )
            }

            val sprint = Sprint(
                id = randomSprintID(),
                projectID = projectID,
                name = normalisedName,
                goalStoryPoints = goalStoryPoints,
                goal = goal,
                startDate = startDate,
                endDate = endDate,
                ownerID = ownerID,
                isFinished = false,
                finishedAt = null,
                createdAt = now,
                updatedAt = now,
            )
            database.sprintDao().insert(sprint.toRecord())
            enqueueSprint(sprint, stamp, now)
            sprint
        }
        changeBus.announceSprints(projectID)
        return created
    }

    /**
     * A partial edit.
     *
     * **Refuses to move the window past itself** — an end before a start is stored rather
     * than thrown, per [Sprint]'s own KDoc, but *editing into* that state is refused here.
     * A backwards window is survivable; an edit that silently produces one is how a user
     * ends up with a sprint that is never active.
     */
    override suspend fun updateSprint(
        id: SprintID,
        name: String?,
        goalStoryPoints: Int?,
        goal: Patch<String?>,
        startDate: Instant?,
        endDate: Instant?,
        ownerID: Patch<UserID?>,
        now: Instant,
    ): Sprint {
        val normalisedName = name?.let { ProjectRules.normaliseName(it) }
        val stamp = currentUserStamp()

        val updated = inTransaction(database) {
            val sprint = readSprint(id)
            normalisedName?.let { sprint.name = it }
            // Floored rather than refused, matching `Sprint`'s own `init`: a negative goal
            // from a sync is normalised in place so every later reader sees one number.
            goalStoryPoints?.let { sprint.goalStoryPoints = maxOf(0, it) }
            if (goal is Patch.Of) sprint.goal = goal.value
            startDate?.let { sprint.startDate = it }
            endDate?.let { sprint.endDate = it }
            if (ownerID is Patch.Of) sprint.ownerID = ownerID.value
            sprint.updatedAt = now

            if (sprint.startDate > sprint.endDate) {
                throw AppError.Validation(
                    "A sprint cannot end before it starts.",
                )
            }

            // The sprint's own row is excluded: an edit that does not move the window must
            // not find itself and refuse. A finished sprint is excluded by the helper.
            val blocker = sprintBlockingOverlap(
                sprint.startDate,
                sprint.endDate,
                database.sprintDao().byProject(sprint.projectID.stringValue)
                    .mapNotNull { it.domainValue }
                    .filter { it.id != sprint.id },
            )
            if (blocker != null) {
                throw AppError.Validation(
                    "'${blocker.name}' overlaps this window. Finish it before saving this sprint.",
                )
            }

            database.sprintDao().update(sprint.toRecord())
            enqueueSprint(sprint, stamp, now)
            sprint
        }
        changeBus.announceSprints(updated.projectID)
        return updated
    }

    /**
     * Closes a sprint.
     *
     * The date is set here and not taken from the caller, so a caller cannot write
     * "finished" without one — the state `Sprint.reconcileFinishedAt` exists to rule out.
     * A **second** finish is refused rather than being a no-op: the summary is computed from
     * the items' state at the moment it is read, so finishing twice would silently move
     * the date a reader is being told the sprint ended on.
     */
    override suspend fun finishSprint(id: SprintID, now: Instant): Sprint {
        val stamp = currentUserStamp()

        val finished = inTransaction(database) {
            val sprint = readSprint(id)
            if (sprint.isFinished) {
                throw AppError.Validation("'${sprint.name}' has already been finished.")
            }
            sprint.isFinished = true
            sprint.updatedAt = now
            sprint.reconcileFinishedAt(now)
            database.sprintDao().update(sprint.toRecord())
            enqueueSprint(sprint, stamp, now)
            sprint
        }
        changeBus.announceSprints(finished.projectID)
        return finished
    }

    /**
     * Reopens a sprint and clears its finished date.
     *
     * Refused when reopening would put this sprint's window back on the calendar next to an
     * **unfinished** sibling that shares an instant of it — the exact state `createSprint`
     * exists to prevent. The sprint's own row is excluded, because a finished sprint is not
     * a blocker for itself; a *finished* sibling is no blocker either, so the helper is what
     * decides and this store does not re-derive the finished-half of the rule.
     */
    override suspend fun reopenSprint(id: SprintID, now: Instant): Sprint {
        val stamp = currentUserStamp()

        val reopened = inTransaction(database) {
            val sprint = readSprint(id)
            val blocker = sprintBlockingOverlap(
                sprint.startDate,
                sprint.endDate,
                database.sprintDao().byProject(sprint.projectID.stringValue)
                    .mapNotNull { it.domainValue }
                    .filter { it.id != sprint.id },
            )
            if (blocker != null) {
                throw AppError.Validation(
                    "'${blocker.name}' overlaps, so '${sprint.name}' cannot be reopened.",
                )
            }
            sprint.isFinished = false
            sprint.updatedAt = now
            sprint.reconcileFinishedAt(now)
            database.sprintDao().update(sprint.toRecord())
            enqueueSprint(sprint, stamp, now)
            sprint
        }
        changeBus.announceSprints(reopened.projectID)
        return reopened
    }

    override suspend fun archiveSprint(id: SprintID, now: Instant): Sprint {
        val stamp = currentUserStamp()

        val archived = inTransaction(database) {
            val sprint = readSprint(id)
            if (!sprint.isFinished) {
                throw AppError.Validation("'${sprint.name}' is not finished yet.")
            }
            sprint.isArchived = true
            sprint.updatedAt = now
            database.sprintDao().update(sprint.toRecord())
            enqueueSprint(sprint, stamp, now)
            sprint
        }
        changeBus.announceSprints(archived.projectID)
        return archived
    }

    override suspend fun unarchiveSprint(id: SprintID, now: Instant): Sprint {
        val stamp = currentUserStamp()

        val restored = inTransaction(database) {
            val sprint = readSprint(id)
            sprint.isArchived = false
            sprint.updatedAt = now
            database.sprintDao().update(sprint.toRecord())
            enqueueSprint(sprint, stamp, now)
            sprint
        }
        changeBus.announceSprints(restored.projectID)
        return restored
    }

    /**
     * Moves items into a sprint, or back to the backlog.
     *
     * The work item rows are written **through the mapping rather than through
     * [WorkItemRecord.assignToSprint]**, because every changed item is an edit and an edit
     * has to queue for sync — and a bulk `UPDATE` produces no payload for the queue. So
     * each item is read, changed, written and enqueued, and the batch is one transaction, so
     * it either all happens or none of it does.
     */
    override suspend fun setWorkItemSprint(
        workItemIDs: List<WorkItemID>,
        sprintID: Patch<SprintID?>,
        now: Instant,
    ): List<WorkItem> {
        val stamp = currentUserStamp()
        // Empty is a no-op rather than an error, because "these tasks are already where I
        // asked" is a normal answer to a call like this and not a failure.
        if (workItemIDs.isEmpty() || sprintID is Patch.Unmentioned) return emptyList()

        val target = (sprintID as Patch.Of).value
        val changed = inTransaction(database) {
            workItemIDs.map { itemID ->
                val item = readWorkItem(itemID)
                item.sprintID = target
                item.updatedAt = now
                database.workItemDao().update(item.toRecord())
                enqueueWorkItem(item, stamp, now)
                item
            }
        }
        val owner = changed.first().projectID
        changeBus.announceSprints(owner)
        return changed
    }

    /**
     * Deletes a sprint and returns its items to the backlog.
     *
     * The items are released in the same transaction and each one is queued, because a
     * card that still claims to be in a sprint on the server is a card that reappears there
     * on the next pull.
     */
    override suspend fun deleteSprint(id: SprintID, now: Instant) {
        val stamp = currentUserStamp()

        val projectID = inTransaction(database) {
            val sprint = readSprint(id)
            for (item in database.workItemDao().bySprint(id.stringValue)) {
                val value = item.domainValue ?: continue
                value.sprintID = null
                value.updatedAt = now
                database.workItemDao().update(value.toRecord())
                enqueueWorkItem(value, stamp, now)
            }
            database.sprintDao().deleteByID(id.stringValue)
            enqueueDelete(OutboxEntity.Sprint, id.stringValue, stamp, now)
            sprint.projectID
        }
        changeBus.announceSprints(projectID)
        changeBus.announceWorkItems(projectID)
    }

    // MARK: Work item writes

    /**
     * Creates an item, and issues it the project's next key.
     *
     * The counter is the subtle part. It is read from the project row **inside** the
     * transaction and written back in the same one, because two devices creating items
     * offline and landing their pushes in either order both advance their own copy to 3 and
     * hand out `TT-3` — the only thing that can stop that is a read and a write that no other
     * write can interleave with. The advanced project is queued as well as the item, or the
     * server would never learn that the counter moved and a third device would reissue it.
     */
    override suspend fun createWorkItem(
        projectID: ProjectID,
        title: String,
        details: String,
        status: WorkItemStatus,
        priority: Priority,
        assigneeID: UserID?,
        dueDate: Instant?,
        storyPoints: Int?,
        sprintID: SprintID?,
        now: Instant,
        blockedByIDs: List<WorkItemID>,
        recurrence: Recurrence?,
    ): WorkItem {
        val normalisedTitle = WorkItemRules.normaliseTitle(title)
        val normalisedDetails = WorkItemRules.normaliseDetails(details)
        val stamp = currentUserStamp()
        val author = currentAuthor()

        val created = inTransaction(database) {
            val project = readProject(projectID)
            val number = project.nextItemNumber
            val freshID = randomWorkItemID()
            val item = WorkItem(
                id = freshID,
                projectID = projectID,
                key = ProjectRules.workItemKey(project.keyPrefix, number),
                title = normalisedTitle,
                details = normalisedDetails,
                status = status,
                priority = priority,
                assigneeID = assigneeID,
                dueDate = dueDate,
                recurrence = recurrence,
                storyPoints = storyPoints,
                blockedByIDs = blockedByIDs.filter { it != freshID }.distinct(),
                sprintID = sprintID,
                rank = topRankIn(projectID, status),
                createdAt = now,
                updatedAt = now,
            )
            // Creating straight into Done has to date the resolution, or the archive holds an
            // item it cannot file under any month.
            item.reconcileResolutionAt(now)
            project.nextItemNumber = number + 1

            database.workItemDao().insert(item.toRecord())
            recordStatusChange(
                StatusChange(
                    id = randomStatusChangeID(),
                    workItemID = item.id,
                    projectID = projectID,
                    from = null,
                    to = status,
                    changedAt = now,
                    changedBy = author,
                ),
            )
            enqueueWorkItem(item, stamp, now)

            database.projectDao().update(project.toRecord())
            enqueueProject(project, stamp, now)
            item
        }
        changeBus.announceProjectChange(projectID)
        return created
    }

    /**
     * A partial edit, and the [Patch] parameters are why the signature is not simpler.
     *
     * `title`, `details` and `priority` are plain optionals: `null` means leave alone, and
     * none of the three can be meaningfully emptied. `assigneeID` and `dueDate` are [Patch]es,
     * because both *can* be emptied and "not mentioned" is a different instruction from "set
     * it to nothing" — see [Patch].
     *
     * **No history row and no `resolvedAt` write here**, and neither is an oversight. This
     * path cannot change a status, so there is no transition to record, and `resolvedAt` has
     * nothing to reconcile against. Calling [WorkItem.reconcileResolutionAt] "just in case"
     * would be actively wrong: it clears the date whenever the status is not terminal, so
     * editing the title of a `Done` task would silently pull it out of the archive.
     */
    override suspend fun updateWorkItem(
        id: WorkItemID,
        title: String?,
        details: String?,
        priority: Priority?,
        assigneeID: Patch<UserID?>,
        dueDate: Patch<Instant?>,
        storyPoints: Patch<Int?>,
        now: Instant,
        blockedByIDs: Patch<List<WorkItemID>>,
        recurrence: Patch<Recurrence?>,
    ): WorkItem {
        val normalisedTitle = title?.let { WorkItemRules.normaliseTitle(it) }
        val normalisedDetails = details?.let { WorkItemRules.normaliseDetails(it) }
        val stamp = currentUserStamp()

        val updated = inTransaction(database) {
            val item = readWorkItem(id)
            normalisedTitle?.let { item.title = it }
            normalisedDetails?.let { item.details = it }
            priority?.let { item.priority = it }
            // `is Patch.Of` rather than an argument that was normalised above, so "not
            // mentioned" and "set to null" cannot be collapsed by a later change to this
            // signature. `Unmentioned` falls through as a no-op, which is its only correct
            // reading.
            if (assigneeID is Patch.Of) item.assigneeID = assigneeID.value
            if (dueDate is Patch.Of) item.dueDate = dueDate.value
            if (storyPoints is Patch.Of) item.storyPoints = storyPoints.value
            if (blockedByIDs is Patch.Of) item.blockedByIDs =
                blockedByIDs.value.filter { it != id }.distinct()
            if (recurrence is Patch.Of) item.recurrence = recurrence.value
            item.updatedAt = now

            database.workItemDao().update(item.toRecord())
            enqueueWorkItem(item, stamp, now)
            item
        }
        changeBus.announceWorkItems(updated.projectID)
        return updated
    }

    /**
     * Moves an item to another column, optionally at a specific [rank].
     *
     * The only place a status changes from a local edit, so it is where both the `resolvedAt`
     * invariant and the history row are maintained. A reorder within a column arrives here
     * too, with the status unchanged, and records nothing — a history claiming a task moved
     * when it was only nudged up a list is worse than no history at all.
     *
     * A `null` [rank] lands the item at the **bottom** of the destination, which is what
     * [ProjectRepositoryPort.moveWorkItem] specifies. The original put it at the *top*, from
     * the same `minRank` helper it used for a new card — one helper serving two opposite
     * instructions, so every drag reordered the lane around the card that came in. [topRankIn]
     * and [bottomRankIn] are the two aggregates that keep them apart.
     */
    override suspend fun moveWorkItem(
        id: WorkItemID,
        to: WorkItemStatus,
        rank: Int?,
        now: Instant,
    ): WorkItem {
        val stamp = currentUserStamp()
        val author = currentAuthor()

        val moved = inTransaction(database) {
            val item = readWorkItem(id)
            val previous = item.status
            item.status = to
            item.rank = rank ?: bottomRankIn(item.projectID, to)
            item.updatedAt = now
            // Dates the resolution on the way into a terminal status and clears it on the way
            // out again, so reopening a task does not leave it filed in the archive forever.
            item.reconcileResolutionAt(now)

            if (previous != to) {
                recordStatusChange(
                    StatusChange(
                        id = randomStatusChangeID(),
                        workItemID = item.id,
                        projectID = item.projectID,
                        from = previous,
                        to = to,
                        changedAt = now,
                        changedBy = author,
                    ),
                )
            }
            database.workItemDao().update(item.toRecord())
            enqueueWorkItem(item, stamp, now)
            item
        }
        changeBus.announceWorkItems(moved.projectID)
        return moved
    }

    override suspend fun deleteWorkItem(id: WorkItemID) {
        val stamp = currentUserStamp()
        // The owner is read inside the transaction, because afterwards the record is gone and
        // there is nothing left to ask which project this belonged to — and an announcement
        // that cannot name its project cannot wake a board.
        val outcome = inTransaction(database) {
            val item = readWorkItem(id)
            database.statusChangeDao().deleteByWorkItem(id.stringValue)
            // Comments go with the item for the same reason the history does: a leftover
            // thread is unreachable and would reattach itself to whatever work item is
            // issued the same id next. Unlike history, comments *do* queue an outbox row
            // on write — but a delete of the whole item is already announced by the item's
            // own delete row, so the thread goes silently, local first.
            database.commentDao().deleteByWorkItem(id.stringValue)
            // Attachments follow the thread's silent-row rule, with the thread's
            // impossibility on top: rows cannot go without their blobs (else a listing
            // points at files that do not exist), and blobs cannot be deleted in the
            // transaction. So the paths are collected inside, the rows drop inside, and
            // the files go after the commit — a crash between the two leaves orphan blobs,
            // which cost disk, never correctness.
            val blobPaths = database.attachmentDao().byWorkItem(id.stringValue)
                .map { it.blobPath }
            database.attachmentDao().deleteByWorkItem(id.stringValue)
            database.workItemDao().deleteByID(id.stringValue)
            enqueueDelete(OutboxEntity.WorkItem, id.stringValue, stamp, item.updatedAt)
            Pair(item.projectID, blobPaths)
        }
        val (owner, orphanedBlobs) = outcome
        orphanedBlobs.forEach { safeDeleteBlob(it) }
        changeBus.announceWorkItems(owner)
    }

    // MARK: Recovering refused changes

    /**
     * Puts every refused change back in the queue, rebuilding the payload of any the store
     * itself made un-sendable.
     *
     * See [ProjectRepositoryPort.retryStuckChanges] for why this is a repository operation
     * rather than a queue one, and for the guard on the single repair it performs.
     *
     * ## Rows are revived, never re-queued
     *
     * The id and `createdAt` of each row are left exactly as they are, and that is the point
     * rather than a shortcut. A retried batch contains a project and the work items that name
     * it, and the server applies a push in order — so re-dating the project to "now" would
     * put it *behind* its own work items, which is the refusal this is recovering from. A
     * fresh row would also leave the old one dead and counted, so the number on the Settings
     * screen would go up rather than down.
     *
     * ## Nothing is announced unless the write committed
     *
     * The repaired projects are collected inside the transaction and announced after it, for
     * the reason the class KDoc gives: a listener that re-read on an uncommitted write would
     * fetch the old member list and never look again.
     */
    override suspend fun retryStuckChanges(now: Instant): Int {
        val stamp = currentUserStamp()
        val userID = stamp?.let(::parseUserID)
        val repaired = mutableListOf<ProjectID>()

        val revived = inTransaction(database) {
            val dead = database.outboxDao().deadLetters(stamp)
            if (dead.isEmpty()) return@inTransaction 0

            var count = 0
            for (record in dead) {
                val entity = OutboxEntity.parse(record.entityKind)
                val operation = OutboxOperation.parse(record.operation)
                // A row this build cannot read cannot be rebuilt either, and it is left where
                // it is rather than dropped: it is the only record of what did not make it,
                // and pushing a payload whose shape this build cannot vouch for is worse than
                // leaving it visible.
                if (entity == null || operation == null) continue

                val payload = when (operation) {
                    // A delete's payload is the bare id, so the stored one is already the
                    // whole message and there is nothing to read back from the store.
                    OutboxOperation.Delete -> record.payload
                    OutboxOperation.Upsert ->
                        rebuiltPayload(entity, record.entityID, userID, now, repaired)
                } ?: continue

                count += database.outboxDao().revive(record.id, payload)
            }
            count
        }

        repaired.forEach(changeBus::announceProjectChange)
        return revived
    }

    /**
     * The payload to send for one refused change, or `null` when there is nothing left to
     * send because the entity has gone from the store since.
     *
     * This is the one place a payload is *rebuilt* rather than built fresh, and it has to
     * exist because an outbox row holds a snapshot: the server's refusal was a verdict on the
     * bytes it was handed, not on the change, so re-sending those bytes would earn the same
     * answer. Every other enqueue here builds from a value the caller already holds; this is
     * the one that has to read the value back first.
     *
     * [repaired] collects the projects this actually changed, so the caller can announce them
     * once the transaction has committed.
     */
    private suspend fun rebuiltPayload(
        entity: OutboxEntity,
        entityID: String,
        userID: UserID?,
        now: Instant,
        repaired: MutableList<ProjectID>,
    ): ByteArray? = when (entity) {
        OutboxEntity.Project -> {
            val id = parseProjectID(entityID) ?: return null
            val stored = recordForProject(id)?.domainValue ?: return null
            // The repair. The guard is "names nobody", not "does not name me": a project that
            // names other people but not the signed-in user is one they are not in, and
            // adding themselves would be a silent join. A project that names nobody is one
            // whose creator was never recorded, which is the whole of the defect.
            val resent = if (stored.memberIDs.isEmpty() && userID != null) {
                stored.copy(memberIDs = listOf(userID), updatedAt = now)
            } else {
                stored
            }
            if (resent != stored) {
                database.projectDao().update(resent.toRecord())
                repaired.add(resent.id)
            }
            JSONCoding.json.encodeToString(Project.serializer(), resent).encodeToByteArray()
        }

        OutboxEntity.WorkItem -> {
            val id = parseWorkItemID(entityID) ?: return null
            val item = recordForWorkItem(id)?.domainValue ?: return null
            JSONCoding.json.encodeToString(WorkItem.serializer(), item).encodeToByteArray()
        }

        OutboxEntity.Sprint -> {
            // The id is checked through the parser rather than used raw, so a row written by
            // a build that spelled ids differently is refused here instead of being handed to
            // a DAO that would simply find nothing.
            if (parseSprintID(entityID) == null) return null
            val sprint = database.sprintDao().byID(entityID)?.domainValue ?: return null
            JSONCoding.json.encodeToString(Sprint.serializer(), sprint).encodeToByteArray()
        }

        OutboxEntity.Comment -> {
            val comment = database.commentDao().byID(entityID)?.domainValue ?: return null
            JSONCoding.json.encodeToString(Comment.serializer(), comment).encodeToByteArray()
        }
    }

    override suspend fun addComment(
        workItemID: WorkItemID,
        authorID: UserID,
        body: String,
        parentID: CommentID?,
        now: Instant,
    ): Comment {
        val stamp = currentUserStamp()
        val comment = Comment(
            id = randomCommentID(),
            workItemID = workItemID,
            authorID = authorID,
            body = body,
            createdAt = now,
            parentID = parentID,
        )

        // The work item is read first — it proves the item exists and yields the project
        // to announce, both before any comment row is written.
        val owner = inTransaction(database) {
            val item = readWorkItem(workItemID)
            database.commentDao().insert(comment.toRecord())
            enqueueComment(comment, stamp, now)
            item.projectID
        }
        // Announced as a work-item change because that is the kind a subscriber can act
        // on: an open editor re-reads the thread it is showing, and a board re-reads a card
        // whose data did not change — cheap, and one new enum value on `ProjectChange.Kind`
        // away from being narrowed later. An unannounced comment write is the version of
        // this that can silently strand a second open editor.
        changeBus.announceWorkItems(owner)
        return comment
    }

    // MARK: Attachments

    override suspend fun attachments(workItemID: WorkItemID): List<Attachment> =
        inTransaction(database) {
            database.attachmentDao().byWorkItem(workItemID.stringValue)
                .mapNotNull { it.domainValue }
        }

    /**
     * Writes the blob first, then the row — the reverse order would index a file that may
     * never be written.
     *
     * The two writes cannot share the transaction, and the blob cannot be written inside
     * it either (file I/O is not transactional), so the failure mode is an orphaned blob
     * if the row write loses the race afterwards. The catch owns that half: a stored
     * file whose row never landed is deleted before the error surfaces, so an exception
     * here always means "nothing was stored", the same guarantee the transaction gives
     * the other writes in this class.
     */
    override suspend fun addAttachment(
        workItemID: WorkItemID,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        now: Instant,
    ): Attachment {
        // Existence is proven before the blob lands. A file stored for an item the
        // store then reports as missing would need deleting from the same catch that
        // guards the row — proving first keeps the catch to one job.
        val owner = inTransaction(database) {
            readWorkItem(workItemID).projectID
        }

        val attachment = Attachment(
            id = randomAttachmentID(),
            workItemID = workItemID,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            blobPath = "",
            createdAt = now,
        )
        val relativeName = blobStore.write(attachment.id, bytes)

        return try {
            inTransaction(database) {
                database.attachmentDao().insert(attachment.copy(blobPath = relativeName).toRecord())
            }
            // Announced as a work-item change, the comment-write choice again: an open
            // editor re-reads the attachments it is showing, and a board re-reads a card
            // whose data did not change — cheap. An unannounced attach would strand a
            // second open editor the same way an unannounced comment does.
            changeBus.announceWorkItems(owner)
            attachment.copy(blobPath = relativeName)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            safeDeleteBlob(relativeName)
            throw failure
        }
    }

    override suspend fun deleteAttachment(id: AttachmentID) {
        val blobPath = inTransaction(database) {
            val row = database.attachmentDao().byID(id.stringValue) ?: return@inTransaction null
            database.attachmentDao().deleteByID(id.stringValue)
            row.blobPath
        } ?: return
        safeDeleteBlob(blobPath)
    }

    /**
     * Removes a blob outside the transaction, never throwing.
     *
     * Blob deletion is always best-effort-after-commit: file I/O is not transactional, so
     * it cannot happen inside [inTransaction], and a failed row write must roll back while
     * a failed file delete must not. A leftover file costs disk space; a thrown delete
     * would cost the operation its result. No cancellation concern: neither this block
     * nor the store suspends, so there is no suspension point at which a `CancellationException`
     * could arrive to be swallowed.
     */
    private fun safeDeleteBlob(relativeName: String) {
        runCatching { blobStore.delete(relativeName) }
    }

    // MARK: Seeding

    /**
     * The one write whose instant this class does not take from its caller.
     *
     * [ProjectRepositoryPort.seedSampleDataIfNeeded] has no `now` parameter, and adding one
     * would put a wall clock in a signature that exists to keep clocks out of it. So the
     * instant comes from the injected [DateProvider] instead — which is the same value a test
     * can control, and the reason the seeded board's timestamps are assertable at all.
     */
    override suspend fun seedSampleDataIfNeeded(): Boolean =
        seedIfNeeded(SampleData.projects(), dateProvider.now()) > 0

    /**
     * Seeds only when there is nothing at all, so it is safe to call on every appearance.
     *
     * The emptiness check and the insert share one transaction. Two of them is a race: two
     * dashboards appearing at once could both read an empty store and both seed it, and the
     * loser's `insert` would then abort on a duplicate key with no way to tell that from a
     * genuine failure.
     *
     * @return how many rows were created, projects included.
     */
    suspend fun seedIfNeeded(seeds: List<ProjectSeed>, now: Instant): Int {
        val created = inTransaction(database) {
            if (database.projectDao().count() > 0) 0 else seedInTransaction(seeds, now)
        }
        if (created > 0) announceSeeded()
        return created
    }

    /**
     * Inserts a whole board in one transaction, queueing every row.
     *
     * Goes through the outbox like any other write rather than through a back door, so sample
     * data is not a special case that behaves differently from real work once a server is
     * attached — which is what [ProjectRepositoryPort.seedSampleDataIfNeeded] promises.
     *
     * Each project row is written once, at the end, with its counter already advanced past
     * every seeded item. The alternative — writing it before the loop and patching it per item
     * — would queue a project upsert whose `nextItemNumber` was 1 and then queue it again
     * thirteen times, which is fourteen rows to describe one project.
     *
     * @return how many rows were created, projects included.
     */
    suspend fun seed(seeds: List<ProjectSeed>, now: Instant): Int {
        val created = inTransaction(database) { seedInTransaction(seeds, now) }
        announceSeeded()
        return created
    }

    /**
     * [seed] without the transaction, for a caller that already has one — which is
     * [seedIfNeeded], whose emptiness check must be part of the same unit of work.
     */
    private suspend fun seedInTransaction(seeds: List<ProjectSeed>, now: Instant): Int {
        val stamp = currentUserStamp()
        val author = currentAuthor()

        var created = 0
        for (seed in seeds) {
            val project = Project(
                id = randomProjectID(),
                keyPrefix = seed.keyPrefix,
                name = seed.name,
                details = seed.details,
                nextItemNumber = seed.items.size + 1,
                // The seed's flag resolves against the signed-in user, exactly like
                // `Item.assignee`: a sample name cannot be a `UserID`, and the member
                // section can only *name* a person the directory knows — which at seed
                // time is the signed-in user.
                memberIDs = if (seed.members) listOfNotNull(author) else emptyList(),
                createdAt = now,
                updatedAt = now,
            )
            val projectRecord = project.toRecord()
            database.projectDao().insert(projectRecord)
            enqueueProject(project, stamp, now)
            created += 1

            for ((offset, entry) in seed.items.withIndex()) {
                val item = WorkItem(
                    id = randomWorkItemID(),
                    projectID = project.id,
                    key = ProjectRules.workItemKey(project.keyPrefix, offset + 1),
                    title = entry.title,
                    details = entry.details,
                    status = entry.status,
                    priority = entry.priority,
                    // The seed's flag resolves against the signed-in user: a sample name
                    // cannot be a `UserID`, and the sample board is the user's own board.
                    assigneeID = if (entry.assignee) author else null,
                    // Relative, so the sample board is never a wall of overdue cards the
                    // first time somebody runs this months from now.
                    dueDate = entry.dueInDays?.let { now + it.days },
                    rank = entry.rank,
                    // Offset by whole seconds per item, so the board has a stable creation
                    // order and the key tie-break is not doing all the work. Seeding every
                    // item at the same instant is what made the original's board reshuffle
                    // whenever two cards happened to share a rank.
                    createdAt = now + (offset.seconds),
                    updatedAt = now,
                )
                // Sample data seeds finished work, and the archive files by resolution date,
                // so the seeded Done column has to be dated to be visible at all.
                item.reconcileResolutionAt(now)
                database.workItemDao().insert(item.toRecord())
                recordStatusChange(
                    StatusChange(
                        id = randomStatusChangeID(),
                        workItemID = item.id,
                        projectID = project.id,
                        from = null,
                        to = entry.status,
                        changedAt = now,
                        changedBy = author,
                    ),
                )
                enqueueWorkItem(item, stamp, now)
                created += 1
            }

            for (entry in seed.sprints) {
                val sprint = Sprint(
                    id = randomSprintID(),
                    projectID = project.id,
                    name = ProjectRules.normaliseName(entry.name),
                    goalStoryPoints = maxOf(0, entry.goalStoryPoints),
                    goal = entry.goal,
                    startDate = now + entry.startsInDays.days,
                    endDate = now + entry.endsInDays.days,
                    ownerID = if (entry.owner) author else null,
                    isFinished = entry.isFinished,
                    // Kept in step with `isFinished` by hand, for the same reason the test
                    // fake does: a fixture that broke the entity's own invariant would seed a
                    // state the store cannot otherwise hold.
                    finishedAt = if (entry.isFinished) now else null,
                    isArchived = entry.isArchived,
                    createdAt = now,
                    updatedAt = now,
                )
                database.sprintDao().insert(sprint.toRecord())
                enqueueSprint(sprint, stamp, now)
                created += 1
            }
        }
        return created
    }

    /**
     * Unscoped, and both kinds: a seed writes several projects at once, so there is no single
     * project to name, and a board open anywhere needs to hear about it.
     */
    private suspend fun announceSeeded() {
        changeBus.send(ProjectChange(kind = ProjectChange.Kind.Projects))
        changeBus.send(ProjectChange(kind = ProjectChange.Kind.WorkItems))
    }

    // MARK: Reads within a write

    /**
     * The stored project as a mutable working copy, or a storage failure.
     *
     * **Not** wrapped in its own [inTransaction]: every caller is already inside one, and
     * Room refuses to nest. That is also why the reads the port exposes are wrapped and these
     * are not — a public read is its own unit of work, and a read that exists only inside
     * somebody else's transaction has nothing to be atomic with.
     *
     * A missing row is a failure rather than a `null`, which is a deliberate asymmetry with
     * the port's reads. `project(id)` returning `null` answers "is this here?", which is a
     * question a screen asks. A write against a row that is not there can only be a stale id
     * from a screen that was open when the row was deleted, and the only honest answer to that
     * is a failure the caller can show — silently doing nothing would leave a button that
     * reported success and changed nothing.
     */
    private suspend fun readProject(id: ProjectID): Project {
        val record = recordForProject(id) ?: throw missingProject(id)
        return record.domainValue ?: throw missingProject(id)
    }

    /** As [readProject]. */
    private suspend fun readWorkItem(id: WorkItemID): WorkItem {
        val record = recordForWorkItem(id) ?: throw missingWorkItem(id)
        return record.domainValue ?: throw missingWorkItem(id)
    }

    private suspend fun recordForProject(id: ProjectID): ProjectRecord? =
        database.projectDao().byID(id.stringValue)

    private suspend fun recordForWorkItem(id: WorkItemID): WorkItemRecord? =
        database.workItemDao().byID(id.stringValue)

    /** As [readProject]. */
    private suspend fun readSprint(id: SprintID): Sprint {
        val record = database.sprintDao().byID(id.stringValue) ?: throw missingSprint(id)
        return record.domainValue ?: throw missingSprint(id)
    }

    private fun missingSprint(id: SprintID): AppError.Storage =
        AppError.Storage("No sprint $id in the local store.")

    // MARK: Ranks

    /**
     * One above the top of a column, so a new card sorts above the rest.
     *
     * Ranks are only ever compared, so going negative costs nothing and means the column
     * fills from the top without a single renumber. The floor of 1 for an empty column means
     * the first card in a lane gets rank 1, the second 0, and the third -1.
     *
     * A `null` project is treated as an empty column rather than an error: creating an item
     * under an id no project row holds is already refused by the caller, and this is only
     * reached for a work item whose parent id is unreadable — where refusing to insert the
     * card would be worse than misplacing it.
     */
    private suspend fun topRankIn(projectID: ProjectID?, status: WorkItemStatus): Int {
        val owner = projectID?.stringValue ?: return 1
        return (database.workItemDao().minRank(owner, status.wireValue) ?: 1) - 1
    }

    /** One below the bottom of a column, for a card moved into it. See [topRankIn]. */
    private suspend fun bottomRankIn(projectID: ProjectID, status: WorkItemStatus): Int =
        (database.workItemDao().maxRank(projectID.stringValue, status.wireValue) ?: 0) + 1

    // MARK: History

    /**
     * Appends a history row, inside the caller's transaction, and nowhere else.
     *
     * The absence of an outbox row is the point rather than an oversight — see
     * [StatusChange], which explains both why a row never reaches the server and why a pulled
     * change must not produce one either.
     */
    private suspend fun recordStatusChange(change: StatusChange) {
        database.statusChangeDao().insert(change.toRecord())
    }

    // MARK: The session

    /**
     * The signed-in user as the outbox should stamp it: the raw cached id, without the parse
     * the *display* author applies.
     *
     * A stamp is an identity for the queue's claim filter to match, so it has to round-trip
     * exactly as the session cache holds it. Stamping a normalised form could leave a row no
     * session ever matches — a change that sits in the queue forever, unclaimed and
     * invisible.
     */
    private suspend fun currentUserStamp(): String? = sessionCache?.current()?.userID

    /**
     * The signed-in user for a history row, or `null` when the app cannot name one.
     *
     * Every failure mode here resolves to `null` rather than throwing, and the difference from
     * [currentUserStamp] is why there are two. A stamp is matched against a session, so an
     * unparseable one has to be passed through raw. An author is *displayed* on a row of
     * history, so an unparseable one has to become nothing: a cache that is missing, empty, or
     * holding an id this build cannot parse should cost the entry its author — never the move
     * itself.
     */
    private suspend fun currentAuthor(): UserID? = currentUserStamp()?.let { parseUserID(it) }

    // MARK: The outbox

    /**
     * Queues a project, inside the caller's transaction.
     *
     * The payload is the **domain value**, not the record. That is a protocol decision rather
     * than a convenience: the server must not depend on this module's column names, and adding
     * a column to [ProjectRecord] must not be a wire change.
     *
     * There is one entry point per entity rather than a generic `enqueue(payload: Any)`, and
     * that is what pins the serializer. A generic version would have to take a
     * `KSerializer<T>` from a `reified` type parameter, which resolves at the call site and
     * gives no more safety than passing the value — while making it possible to enqueue a
     * record as a project by mistake, and nothing would catch it until a server rejected the
     * payload.
     */
    private suspend fun enqueueProject(project: Project, userID: String?, now: Instant) {
        val payload = JSONCoding.json.encodeToString(Project.serializer(), project)
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(OutboxEntity.Project, project.id.stringValue, OutboxOperation.Upsert),
                entity = OutboxEntity.Project,
                entityID = project.id.stringValue,
                operation = OutboxOperation.Upsert,
                payload = payload.encodeToByteArray(),
                createdAt = now,
                userID = userID,
            ).toRecord(),
        )
    }

    /** As [enqueueProject]. */
    private suspend fun enqueueWorkItem(item: WorkItem, userID: String?, now: Instant) {
        val payload = JSONCoding.json.encodeToString(WorkItem.serializer(), item)
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(OutboxEntity.WorkItem, item.id.stringValue, OutboxOperation.Upsert),
                entity = OutboxEntity.WorkItem,
                entityID = item.id.stringValue,
                operation = OutboxOperation.Upsert,
                payload = payload.encodeToByteArray(),
                createdAt = now,
                userID = userID,
            ).toRecord(),
        )
    }

    /**
     * As [enqueueProject].
     *
     * The payload is the **domain** `Comment`, for the protocol reason [enqueueSprint]
     * gives: a column on [com.tasktracker.database.CommentRecord] must not be a wire
     * change. The server is told the field is `parentID`, which is what the domain calls
     * it — and the right way round.
     */
    private suspend fun enqueueComment(comment: Comment, userID: String?, now: Instant) {
        val payload = JSONCoding.json.encodeToString(Comment.serializer(), comment)
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(OutboxEntity.Comment, comment.id.stringValue, OutboxOperation.Upsert),
                entity = OutboxEntity.Comment,
                entityID = comment.id.stringValue,
                operation = OutboxOperation.Upsert,
                payload = payload.encodeToByteArray(),
                createdAt = now,
                userID = userID,
            ).toRecord(),
        )
    }

    /**
     * As [enqueueProject].
     *
     * The payload is the **domain** `Sprint`, not the record, for the same protocol reason:
     * adding a column to [SprintRecord] must not be a wire change. It does mean the server
     * is being told the field is called `goalStoryPoints` rather than whatever the column
     * ends up being called, which is the right way round.
     */
    private suspend fun enqueueSprint(sprint: Sprint, userID: String?, now: Instant) {
        val payload = JSONCoding.json.encodeToString(Sprint.serializer(), sprint)
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(OutboxEntity.Sprint, sprint.id.stringValue, OutboxOperation.Upsert),
                entity = OutboxEntity.Sprint,
                entityID = sprint.id.stringValue,
                operation = OutboxOperation.Upsert,
                payload = payload.encodeToByteArray(),
                createdAt = now,
                userID = userID,
            ).toRecord(),
        )
    }

    /**
     * Queues a delete, inside the caller's transaction.
     *
     * The payload is the bare id, as `OutboxRecord.payload` documents. Everything else is the
     * same as an upsert, so a delete is a first-class change rather than a flag: the server
     * needs to know which row to drop, and the only way to say that is to name it.
     */
    private suspend fun enqueueDelete(
        entity: OutboxEntity,
        entityID: String,
        userID: String?,
        now: Instant,
    ) {
        database.outboxDao().insert(
            NewOutboxEntry(
                id = newOutboxID(entity, entityID, OutboxOperation.Delete),
                entity = entity,
                entityID = entityID,
                operation = OutboxOperation.Delete,
                payload = entityID.encodeToByteArray(),
                createdAt = now,
                userID = userID,
            ).toRecord(),
        )
    }

    /**
     * A fresh queue id per row.
     *
     * A *stable* id derived from entity, id and operation would be better — it is what lets a
     * server dedupe a redelivered change — and this is a real limitation of the design, shared
     * with the original. A crash between the entity write and the push produces a new id for
     * what is logically the same change, so a deduping server would apply it twice. Fixed
     * properly it means a per-entity sequence column, which is the same trick
     * [StatusChangeRecord.sequence] uses and a second schema change in this phase; recorded
     * here rather than smuggled in.
     *
     * The uuid is also why `OutboxDao.claimable` cannot break a `createdAt` tie by id: a
     * random id is not an order. That is a Phase 4 limitation, and it is the reason history
     * carries an explicit sequence while the outbox does not.
     */
    private fun newOutboxID(
        entity: OutboxEntity,
        entityID: String,
        operation: OutboxOperation,
    ): String = "${entity.wireValue}:$entityID:${operation.wireValue}:${Uuid.random()}"

    // MARK: Errors

    private fun missingProject(id: ProjectID): AppError.Storage =
        AppError.Storage("No project $id in the local store.")

    private fun missingWorkItem(id: WorkItemID): AppError.Storage =
        AppError.Storage("No work item $id in the local store.")
}
