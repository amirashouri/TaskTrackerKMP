package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateProvider
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SystemDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.parseUserID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.database.NewOutboxEntry
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.ProjectRecord
import com.tasktracker.database.SessionCache
import com.tasktracker.database.StatusChangeRecord
import com.tasktracker.database.TaskTrackerDatabase
import com.tasktracker.database.WorkItemRecord
import com.tasktracker.database.inTransaction
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectRules
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemRules
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.Patch
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import kotlinx.coroutines.flow.Flow
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

    // MARK: Project writes

    override suspend fun createProject(
        name: String,
        keyPrefix: String,
        details: String,
        now: Instant,
        memberIDs: List<UserID>,
    ): Project {
        val project = Project(
            id = randomProjectID(),
            keyPrefix = ProjectRules.normaliseKeyPrefix(keyPrefix),
            name = ProjectRules.normaliseName(name),
            details = ProjectRules.normaliseDetails(details),
            memberIDs = memberIDs.distinct(),
            createdAt = now,
            updatedAt = now,
        )
        val stamp = currentUserStamp()

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

        inTransaction(database) {
            val project = readProject(id)
            for (item in database.workItemDao().byProject(id.stringValue)) {
                database.statusChangeDao().deleteByWorkItem(item.id)
                database.workItemDao().deleteByID(item.id)
                enqueueDelete(OutboxEntity.WorkItem, item.id, stamp, item.updatedAt)
            }
            database.statusChangeDao().deleteByProject(id.stringValue)
            database.projectDao().deleteByID(id.stringValue)
            enqueueDelete(OutboxEntity.Project, id.stringValue, stamp, project.updatedAt)
        }
        changeBus.announceProjectChange(id)
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
        assignee: String?,
        dueDate: Instant?,
        now: Instant,
    ): WorkItem {
        val normalisedTitle = WorkItemRules.normaliseTitle(title)
        val normalisedDetails = WorkItemRules.normaliseDetails(details)
        val normalisedAssignee = WorkItemRules.normaliseAssignee(assignee)
        val stamp = currentUserStamp()
        val author = currentAuthor()

        val created = inTransaction(database) {
            val project = readProject(projectID)
            val number = project.nextItemNumber
            val item = WorkItem(
                id = randomWorkItemID(),
                projectID = projectID,
                key = ProjectRules.workItemKey(project.keyPrefix, number),
                title = normalisedTitle,
                details = normalisedDetails,
                status = status,
                priority = priority,
                assignee = normalisedAssignee,
                dueDate = dueDate,
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
     * none of the three can be meaningfully emptied. `assignee` and `dueDate` are [Patch]es,
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
        assignee: Patch<String?>,
        dueDate: Patch<Instant?>,
        now: Instant,
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
            if (assignee is Patch.Of) item.assignee = WorkItemRules.normaliseAssignee(assignee.value)
            if (dueDate is Patch.Of) item.dueDate = dueDate.value
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
        val owner = inTransaction(database) {
            val item = readWorkItem(id)
            database.statusChangeDao().deleteByWorkItem(id.stringValue)
            database.workItemDao().deleteByID(id.stringValue)
            enqueueDelete(OutboxEntity.WorkItem, id.stringValue, stamp, item.updatedAt)
            item.projectID
        }
        changeBus.announceWorkItems(owner)
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
                    assignee = entry.assignee,
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
