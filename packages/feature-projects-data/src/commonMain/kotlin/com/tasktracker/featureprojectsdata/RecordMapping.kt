package com.tasktracker.featureprojectsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.TagID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.parseAttachmentID
import com.tasktracker.core.parseCommentID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseSprintID
import com.tasktracker.core.parseStatusChangeID
import com.tasktracker.core.parseTagID
import com.tasktracker.core.parseUserID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.database.AttachmentRecord
import com.tasktracker.database.CommentRecord
import com.tasktracker.database.ProjectRecord
import com.tasktracker.database.SprintRecord
import com.tasktracker.database.StatusChangeRecord
import com.tasktracker.database.WorkItemRecord
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemOrderingKey
import com.tasktracker.domain.WorkItemStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Instant

/**
 * The two directions of the record↔domain mapping, for all three records.
 *
 * The entities themselves live in `:packages:database`, because Room's `@Database` is read by
 * the annotation processor at compile time and cannot name a class it has no compile-time
 * reference to — see [ProjectRecord]. The *mapping* lives here, because everything in this
 * file knows what a project and a work item mean, and `:packages:database` is not allowed to
 * depend on a feature.
 *
 * They are extensions rather than members, and that is the same reason
 * `OutboxRecord.toEntry` is one in `:packages:database`: a record is a storage shape, and how
 * it becomes a domain value is a separate concern that has to be *asked* for rather than
 * being free with the type. A member `domainValue` would be available on every copy of the
 * record, including the ones a test builds by hand to set up a scenario — which is exactly
 * where a mapping bug should not be reachable by accident.
 *
 * There are three shapes of conversion here, and the difference between them is the whole
 * point of the file:
 *
 * - `toRecord()` — a *new* row from a domain value. Copies everything.
 * - `apply(value)` — an *existing* row overwritten from a domain value. Copies everything
 *   except the columns that identify the row, which is what stops a server payload from
 *   silently relocating somebody's work between projects.
 * - `domainValue` — a row read back, reconciling whatever the columns claim against what
 *   they should be, and returning `null` for a row that cannot be made into a value at all.
 *
 * `RecordCompletenessTest` exists to hold the first two honest: it reflects over each entity
 * and fails if a column is not named in both directions. Both record↔domain converters in the
 * original silently dropped a field — `resolvedAt` and `nextItemNumber` — and the compiler was
 * happy, because a default fills in whatever the call site forgot.
 */

// MARK: - Project

/**
 * A new row for [this].
 *
 * Note what is *not* here: no reconciliation. A project has no invariant of its own to repair,
 * and `Project`'s own `init` already floors `nextItemNumber` at 1 — which also runs on decode,
 * so a payload carrying `0` comes back as `1` rather than re-issuing a key that is in use.
 */
internal fun Project.toRecord(): ProjectRecord = ProjectRecord(
    id = id.stringValue,
    keyPrefix = keyPrefix,
    name = name,
    details = details,
    tags = tags.toTagColumn(),
    memberIDs = memberIDs.joinToString(",") { it.stringValue },
    isArchived = isArchived,
    nextItemNumber = nextItemNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * The project's tag catalog as the JSON document [ProjectRecord.tags] stores.
 *
 * Empty is the empty string rather than `"[]"`, so a row written before this column existed
 * and a project with no tags are the same value — the same "no blank check" rule
 * [List<WorkItemID>.toBlockedByColumn] uses.
 */
internal fun List<ProjectTag>.toTagColumn(): String =
    if (isEmpty()) "" else JSONCoding.json.encodeToString(ListSerializer(ProjectTag.serializer()), this)

/**
 * Decodes [ProjectRecord.tags], dropping a document this build cannot read.
 *
 * An unreadable catalog reads as empty, which is the only loss a reader can absorb: the
 * project still opens, its items still render, and the admin editor can replace the
 * catalog on the next save. Throwing here would cost the whole project row.
 */
internal fun String.toTags(): List<ProjectTag> =
    if (isEmpty()) {
        emptyList()
    } else {
        runCatching { JSONCoding.json.decodeFromString(ListSerializer(ProjectTag.serializer()), this) }
            .getOrDefault(emptyList())
    }

/**
 * A copy of this row carrying the state in [value], without touching [ProjectRecord.id] or
 * [ProjectRecord.createdAt].
 *
 * Returns a new record rather than mutating this one, which is the whole reason
 * [RoomProjectRepository] can work on a [Project] — mutable, and disposable — and convert it
 * once at the end. Mutating the record in place would mean naming a column at every call
 * site instead of here, and the columns would then be the repository's business: a fifth
 * column added to [ProjectRecord] would compile against a repository that quietly forgot it.
 * [RecordCompletenessTest] would catch that by reflection; this is the shape that makes the
 * reflection check worth having.
 *
 * [ProjectRecord.id] is not copied from the value because a record is *found* by it, and
 * letting a payload rename itself would make the write update a row nobody asked about.
 *
 * **`nextItemNumber` never goes backwards**, and this is the one line in the mapping that is
 * not a straight copy. Two devices create items offline; each advances its own counter to 3;
 * the server's copy of the project, whenever it comes back down, is whatever was true when
 * the *earlier* of those edits was last pushed. Honouring it would rewind the counter to 2,
 * and the next item created locally would be issued `TT-2` — a key already in use by a
 * different task, which is precisely the collision the stored counter exists to prevent.
 *
 * `maxOf` rather than "trust the server", and the asymmetry is deliberate: a counter that
 * jumps *forwards* only ever wastes key numbers, while one that jumps backwards issues a
 * duplicate. `TT-400` with nothing at 399 costs nothing; `TT-3` twice costs somebody their
 * history.
 */
internal fun ProjectRecord.apply(value: Project): ProjectRecord = copy(
    keyPrefix = value.keyPrefix,
    name = value.name,
    details = value.details,
    tags = value.tags.toTagColumn(),
    memberIDs = value.memberIDs.joinToString(",") { it.stringValue },
    isArchived = value.isArchived,
    updatedAt = value.updatedAt,
    nextItemNumber = maxOf(nextItemNumber, value.nextItemNumber),
)

/**
 * `null` for a row whose id cannot be parsed, rather than a fresh random one.
 *
 * The original read `ProjectID(stringValue: id) ?? ProjectID()`, so a corrupt id produced a
 * *new, real* project that no delete could reach, no sync could match, and no cascade could
 * clean up — a phantom that behaves like data. Dropping the row costs the list one entry;
 * inventing an id costs the store a lie.
 */
internal val ProjectRecord.domainValue: Project?
    get() {
        val projectID = parseProjectID(id) ?: return null
        return Project(
            id = projectID,
            keyPrefix = keyPrefix,
            name = name,
            details = details,
            tags = tags.toTags(),
            memberIDs = memberIDs.split(',').mapNotNull(::parseUserID),
            isArchived = isArchived,
            nextItemNumber = nextItemNumber,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

// MARK: - Work item

/**
 * The stored priority ordinal, or [Priority.Medium] for one this build does not know.
 *
 * Written as a single helper because it is needed in two places — the read path and the
 * ordering key — and two independent spellings of "ordinal or Medium" is how one of them ends
 * up disagreeing with the other. A row from a future build with a `Critical` above
 * `Critical` reads as Medium rather than throwing the board away.
 */
private fun priorityFor(rank: Int): Priority = Priority.entries.getOrElse(rank) { Priority.Medium }

/**
 * Encodes a blocker list for [WorkItemRecord.blockedByIDs].
 *
 * Comma-joined UUID strings, `null` for none — the same encoding `memberIDs` uses. `null`
 * rather than `""` so the migration default and the empty state are one value, and an
 * empty split never has to be defended against.
 */
internal fun List<WorkItemID>.toBlockedByColumn(): String? =
    if (isEmpty()) null else joinToString(",") { it.stringValue }

/**
 * Decodes [WorkItemRecord.blockedByIDs], dropping ids this build cannot parse.
 *
 * Drop rather than fail, for the same reason an unreadable sprint id reads as `null`:
 * one corrupt id costs one blocker, where a thrown decode costs the whole card.
 */
internal fun String?.toBlockedByIDs(): List<WorkItemID> =
    this?.split(',')?.mapNotNull { runCatching { WorkItemID(it) }.getOrNull() }.orEmpty()

/**
 * Encodes a tag list for [WorkItemRecord.tagIDs], in the same comma-separated shape as
 * [toBlockedByColumn]. Tags are capped by [com.tasktracker.domain.TagRules.MAX_PER_ITEM]
 * in the domain, so no truncation is repeated here.
 */
internal fun List<TagID>.toTagIDColumn(): String? =
    if (isEmpty()) null else joinToString(",") { it.stringValue }

/**
 * Decodes [WorkItemRecord.tagIDs], dropping ids this build cannot parse.
 *
 * Drop rather than fail for the same reason an unreadable blocker id is dropped: one corrupt
 * id costs one chip, where a thrown decode costs the whole card. An id the *project* no
 * longer defines is dropped later, by the renderer that resolves it.
 */
internal fun String?.toTagIDs(): List<TagID> =
    this?.split(',')?.mapNotNull(::parseTagID).orEmpty()

/**
 * Encodes a repeat rule for [WorkItemRecord.recurrenceRaw], or `null` for a one-off.
 *
 * `null` rather than `""` so "no rule" and "unreadable rule" are never the same
 * stored value — the same shape [toBlockedByColumn] uses.
 */
internal fun Recurrence?.toRecurrenceColumn(): String? = this?.wireValue()

/**
 * Decodes [WorkItemRecord.recurrenceRaw] and pairs it with [WorkItemRecord.recurrenceUntil].
 *
 * An unreadable cadence reads as `null` — "no rule" — which is the same trade as an
 * unreadable `statusRaw`: one row from a future build costs its recurrence, where a
 * thrown decode costs the whole card. The `until` is attached here rather than at the
 * call site so the two columns cannot be recombined differently in two places.
 */
internal fun String?.toRecurrence(until: Instant?): Recurrence? {
    val cadence = this ?: return null
    val parsed = Recurrence.parseWireValue(cadence) ?: return null
    return parsed.withUntil(until)
}

/**
 * A copy of this rule ending at [until].
 *
 * Always a copy, even when [until] is null: `until` never rides in the wire string
 * (see [toRecurrenceColumn]), so the column is the only place it can come from, and
 * a `null` column means the series genuinely has no end rather than a forgotten one.
 */
private fun Recurrence.withUntil(until: Instant?): Recurrence = when (this) {
    is Recurrence.Daily -> copy(until = until)
    is Recurrence.Weekly -> copy(until = until)
    is Recurrence.Monthly -> copy(until = until)
    is Recurrence.Yearly -> copy(until = until)
}

/**
 * A new row for [this], with `resolvedAt` reconciled first.
 *
 * Reconciled on the way *in* as well as on the way out, so a row written from a value that
 * arrived with a terminal status and no date is dated immediately rather than waiting to be
 * healed by whoever reads it next. Healing on read is the fallback, not the plan — a row that
 * is only correct while nobody is looking is a row that a projection built by a different
 * reader will file wrongly.
 *
 * The value is copied rather than mutated, so the caller's `WorkItem` is untouched. It is a
 * class full of `var`s, and a repository that quietly fixed up the object it was handed would
 * be editing a value its caller still holds.
 */
internal fun WorkItem.toRecord(): WorkItemRecord {
    val reconciled = copy().also { it.reconcileResolutionAt(it.updatedAt) }
    return WorkItemRecord(
        id = reconciled.id.stringValue,
        projectID = reconciled.projectID.stringValue,
        key = reconciled.key.rawValue,
        title = reconciled.title,
        details = reconciled.details,
        statusRaw = reconciled.status.wireValue,
        priorityRaw = reconciled.priority.rank,
        assigneeID = reconciled.assigneeID?.stringValue,
        dueDate = reconciled.dueDate,
        rank = reconciled.rank,
        createdAt = reconciled.createdAt,
        updatedAt = reconciled.updatedAt,
        resolvedAt = reconciled.resolvedAt,
        // The point of `copy()` above applies twice over: the caller still holds this
        // `WorkItem`, and a repository that quietly changed its `sprintID` would be editing
        // a value its caller goes on to re-save.
        storyPoints = reconciled.storyPoints,
        blockedByIDs = reconciled.blockedByIDs.toBlockedByColumn(),
        tagIDs = reconciled.tagIDs.toTagIDColumn(),
        recurrenceRaw = reconciled.recurrence.toRecurrenceColumn(),
        recurrenceUntil = reconciled.recurrence?.until,
        sprintID = reconciled.sprintID?.stringValue,
    )
}

/**
 * A copy of this row carrying the state in [value], without touching
 * [WorkItemRecord.id], [WorkItemRecord.projectID] or [WorkItemRecord.createdAt].
 *
 * See [ProjectRecord.apply] for why this returns a copy rather than mutating.
 *
 * [WorkItemRecord.projectID] is the load-bearing exclusion. A record is found by id, so a
 * payload claiming a different parent would either update a row under the wrong project or
 * move a card between projects without a single delete row — and nobody would be told. The
 * project a card belongs to changes only by deleting it and creating it again, which is
 * visible in the history.
 *
 * [WorkItemRecord.id] and [WorkItemRecord.createdAt] are excluded because a card's identity
 * and its age are facts, not opinions: a server that could rewrite them could make a week-old
 * task claim to have been created a moment ago, which is what `createdAt` is for.
 */
internal fun WorkItemRecord.apply(value: WorkItem): WorkItemRecord {
    val reconciled = value.copy().also { it.reconcileResolutionAt(it.updatedAt) }
    return copy(
        key = reconciled.key.rawValue,
        title = reconciled.title,
        details = reconciled.details,
        statusRaw = reconciled.status.wireValue,
        priorityRaw = reconciled.priority.rank,
        assigneeID = reconciled.assigneeID?.stringValue,
        dueDate = reconciled.dueDate,
        rank = reconciled.rank,
        updatedAt = reconciled.updatedAt,
        resolvedAt = reconciled.resolvedAt,
        storyPoints = reconciled.storyPoints,
        blockedByIDs = reconciled.blockedByIDs.toBlockedByColumn(),
        tagIDs = reconciled.tagIDs.toTagIDColumn(),
        // A pulled value like any other: a device has to be able to *learn* that a
        // card was made recurring, or that one stopped being — including its absence.
        recurrenceRaw = reconciled.recurrence.toRecurrenceColumn(),
        recurrenceUntil = reconciled.recurrence?.until,
        // Sprint membership is a pulled value like any other: a device that has been offline
        // for a week has to be able to *learn* which sprint a card was put into, not only
        // ever add one locally. The incoming value wins outright, including its absence —
        // the same treatment every other field here gets.
        sprintID = reconciled.sprintID?.stringValue,
    )
}

/**
 * Reconciles rather than copies, and **refuses to guess an id**.
 *
 * Reconciled on the way out as well as in: a row that predates the `resolvedAt` column, or
 * one written with a terminal status and no date, heals on the first read instead of reaching
 * the archive unfilable — a `Done` card with no `resolvedAt` files under the month it was last
 * *touched*, which is the month somebody opened it in the editor.
 *
 * An unreadable [WorkItemRecord.statusRaw] becomes [WorkItemStatus.Backlog] rather than
 * failing. That is the same trade as storing the raw string at all: one row from a future
 * build costs one card, where a thrown decode costs the whole board.
 */
internal val WorkItemRecord.domainValue: WorkItem?
    get() {
        val itemID = parseWorkItemID(id) ?: return null
        val owner = parseProjectID(projectID) ?: return null
        // An unreadable sprint id reads as `null` — "no sprint" — which is the same trade as
        // an unreadable `statusRaw` one line below, and it is the smaller of the two lies
        // available: a card listed in the backlog is still a card somebody can open, where
        // dropping the row loses the work entirely. The limitation is that a sprint id from
        // a future build silently returns its card to the backlog, and it cannot be recorded
        // here because this level has nowhere to record it.
        val parsedSprint = sprintID?.let { parseSprintID(it) }
        val value = WorkItem(
            id = itemID,
            projectID = owner,
            key = WorkItemKey(key),
            title = title,
            details = details,
            status = WorkItemStatus.fromWireValue(statusRaw) ?: WorkItemStatus.Backlog,
            priority = priorityFor(priorityRaw),
            // An unreadable user id reads as `null` — "unassigned" — the same trade as an
            // unreadable sprint id above: the card stays, the assignment is lost.
            assigneeID = assigneeID?.let { parseUserID(it) },
            dueDate = dueDate,
            rank = rank,
            createdAt = createdAt,
            updatedAt = updatedAt,
            resolvedAt = resolvedAt,
            storyPoints = storyPoints,
            blockedByIDs = blockedByIDs.toBlockedByIDs(),
            tagIDs = tagIDs.toTagIDs(),
            recurrence = recurrenceRaw.toRecurrence(recurrenceUntil),
            sprintID = parsedSprint,
        )
        value.reconcileResolutionAt(updatedAt)
        return value
    }

/**
 * Shared with the domain so a board column's order and a projection's order cannot be two
 * different rules.
 *
 * Built from the *columns* rather than from a domain value, so [WorkItemRecord]'s
 * `ORDER BY projectID, statusRaw, rank, priorityRaw, createdAt, key` and
 * `WorkItem.orderingKey` are visibly the same four fields in the same order. The query's sort
 * is the cheap one for a read; the domain's is the authority a projection sorts by — and
 * Phase 8's tests assert the two agree rather than assuming that they do.
 */
internal val WorkItemRecord.orderingKey: WorkItemOrderingKey
    get() = WorkItemOrderingKey(
        rank = rank,
        priority = priorityFor(priorityRaw),
        createdAt = createdAt,
        tieBreaker = key,
    )

// MARK: - Sprint

/**
 * A new row for [this], with `finishedAt` reconciled first.
 *
 * Reconciled on the way in as well as out, for the reason `WorkItem.toRecord` gives: a row
 * written from a value that arrived finished and undated is dated immediately rather than
 * waiting for whoever reads it next to heal it. A row that is only correct while nobody is
 * looking is a row that a projection built by a different reader will file wrongly.
 *
 * The value is copied, so the caller's `Sprint` is untouched — it is a class full of `var`s
 * and a repository that quietly fixed up the object it was handed would be editing a value
 * its caller still holds.
 */
internal fun Sprint.toRecord(): SprintRecord {
    val reconciled = copy().also { it.reconcileFinishedAt(it.updatedAt) }
    return SprintRecord(
        id = reconciled.id.stringValue,
        projectID = reconciled.projectID.stringValue,
        name = reconciled.name,
        goalStoryPoints = reconciled.goalStoryPoints,
        goal = reconciled.goal,
        startDate = reconciled.startDate,
        endDate = reconciled.endDate,
        ownerID = reconciled.ownerID?.stringValue,
        isFinished = reconciled.isFinished,
        finishedAt = reconciled.finishedAt,
        isArchived = reconciled.isArchived,
        isLocked = reconciled.isLocked,
        createdAt = reconciled.createdAt,
        updatedAt = reconciled.updatedAt,
    )
}

/**
 * A copy of this row carrying the state in [value], without touching
 * [SprintRecord.id], [SprintRecord.projectID] or [SprintRecord.createdAt].
 *
 * See [ProjectRecord.apply] for why this returns a copy rather than mutating, and
 * [WorkItemRecord.apply] for why `projectID` is the load-bearing exclusion: a record is
 * found by id, so a payload naming a different parent would move a sprint between projects
 * without a delete row, and nobody would be told.
 *
 * **Unlike a work item, `isFinished` *is* copied**, and that asymmetry is deliberate. A
 * card's status is a local edit that has to be pushed; a sprint's open/closed state arrives
 * from whichever device closed it, and a pull has to be able to say so. Refusing it would
 * make the closed state un-receivable, and the summary — which is the whole point of
 * closing one — would never arrive on the second device.
 */
internal fun SprintRecord.apply(value: Sprint): SprintRecord {
    val reconciled = value.copy().also { it.reconcileFinishedAt(it.updatedAt) }
    return copy(
        name = reconciled.name,
        goalStoryPoints = reconciled.goalStoryPoints,
        goal = reconciled.goal,
        startDate = reconciled.startDate,
        endDate = reconciled.endDate,
        ownerID = reconciled.ownerID?.stringValue,
        isFinished = reconciled.isFinished,
        finishedAt = reconciled.finishedAt,
        isArchived = reconciled.isArchived,
        isLocked = reconciled.isLocked,
        updatedAt = reconciled.updatedAt,
    )
}

/**
 * `null` for a row whose id cannot be parsed, for the reason [ProjectRecord.domainValue]
 * gives: inventing one produces a phantom that behaves like data.
 *
 * An unreadable [SprintRecord.projectID] drops the row rather than defaulting it, because a
 * sprint belongs to a project and a sprint attached to a guessed parent would show up on
 * somebody else's Sprints tab.
 */
internal val SprintRecord.domainValue: Sprint?
    get() {
        val sprintID = parseSprintID(id) ?: return null
        // Named `projectID` rather than reusing a shorter local, because `ownerID` below
        // is the sprint's own owner and a local called `owner` holding a [ProjectID]
        // reads as though the two were the same thing.
        val projectID = parseProjectID(this.projectID) ?: return null
        val value = Sprint(
            id = sprintID,
            projectID = projectID,
            name = name,
            goalStoryPoints = goalStoryPoints,
            goal = goal,
            startDate = startDate,
            endDate = endDate,
            // An unreadable user id reads as `null` — "unowned" — the same trade as an
            // unreadable status string elsewhere: the sprint stays, the owner is lost.
            ownerID = ownerID?.let { parseUserID(it) },
            isFinished = isFinished,
            finishedAt = finishedAt,
            isArchived = isArchived,
            isLocked = isLocked,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
        value.reconcileFinishedAt(updatedAt)
        return value
    }

// MARK: - Status change

/**
 * A new row for [this].
 *
 * `sequence` is left at its default so Room assigns it, which is the only thing that makes it
 * an insertion counter rather than a number this module chose.
 */
internal fun StatusChange.toRecord(): StatusChangeRecord = StatusChangeRecord(
    id = id.stringValue,
    workItemID = workItemID.stringValue,
    projectID = projectID.stringValue,
    fromRaw = from?.wireValue,
    toRaw = to.wireValue,
    changedAt = changedAt,
    changedByUserID = changedBy?.stringValue,
)

/**
 * `null` rather than throwing when a column holds something unreadable.
 *
 * A history missing one entry is still a history, and a single row written by a future build
 * should cost the reader one row rather than the whole list. The original skipped such rows
 * too, and for the same reason.
 *
 * An unreadable [StatusChangeRecord.fromRaw] is read as "came from nowhere" rather than
 * dropping the row, because the rest of the entry is still true and the alternative is a hole
 * in the list. That is *not* the same as [StatusChange.from] being genuinely `null`, which
 * means the item was created here — and the original conflated the two, so a row with a
 * corrupt `from` claimed to be a creation. The distinction cannot be recovered at this level,
 * so it is recorded here rather than papered over: an unparseable `fromRaw` is indistinguishable
 * from a real `null`, and the honest thing is to say so in the KDoc of the thing that has to
 * live with it.
 *
 * A [StatusChangeRecord.changedByUserID] this build cannot parse means the row was written by
 * something else, which is the same situation as not knowing who did it, so it becomes `null`
 * rather than costing the row.
 */
internal val StatusChangeRecord.domainValue: StatusChange?
    get() {
        val changeID = parseStatusChangeID(id) ?: return null
        val itemID = parseWorkItemID(workItemID) ?: return null
        val owner = parseProjectID(projectID) ?: return null
        val destination = WorkItemStatus.fromWireValue(toRaw) ?: return null
        return StatusChange(
            id = changeID,
            workItemID = itemID,
            projectID = owner,
            from = fromRaw?.let { WorkItemStatus.fromWireValue(it) },
            to = destination,
            changedAt = changedAt,
            changedBy = changedByUserID?.let { parseUserID(it) },
        )
    }

/**
 * A new row for [this].
 *
 * `sequence` is left at its default so Room assigns it — the insertion counter the
 * thread's `createdAt` tie-break depends on, for the reason
 * [StatusChangeRecord.sequence] gives.
 */
internal fun Comment.toRecord(): CommentRecord = CommentRecord(
    id = id.stringValue,
    workItemID = workItemID.stringValue,
    authorID = authorID.stringValue,
    body = body,
    createdAt = createdAt,
    parentID = parentID?.stringValue,
)

/**
 * `null` rather than throwing when a column holds something unreadable.
 *
 * A row written by a future build — an id this one cannot parse — costs the reader that
 * row, not the thread, for the reason [StatusChangeRecord.domainValue] gives: a thread
 * with a gap in it still reads, and one bad row emptying the conversation would be a
 * single bad row amplified into missing content. The body is never rewritten on the way
 * in or out: a comment is what was typed.
 */
internal val CommentRecord.domainValue: Comment?
    get() {
        val commentID = parseCommentID(id) ?: return null
        val itemID = parseWorkItemID(workItemID) ?: return null
        val author = parseUserID(authorID) ?: return null
        return Comment(
            id = commentID,
            workItemID = itemID,
            authorID = author,
            body = body,
            createdAt = createdAt,
            parentID = parentID?.let { parseCommentID(it) },
        )
    }

/**
 * A new row for [this].
 *
 * `sequence` is left at its default so Room assigns it — the same insertion counter the
 * listing's `createdAt` tie-break depends on, for the reason [CommentRecord.sequence]
 * gives.
 */
internal fun Attachment.toRecord(): AttachmentRecord = AttachmentRecord(
    id = id.stringValue,
    workItemID = workItemID.stringValue,
    projectID = projectID?.stringValue ?: "",
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    blobPath = blobPath,
    blobRef = blobRef,
    uploadedAt = uploadedAt,
    createdAt = createdAt,
)

/**
 * `null` rather than throwing when a column holds something unreadable.
 *
 * A row written by a future build — an id this one cannot parse — costs the reader that
 * row, not the list, for the reason [CommentRecord.domainValue] gives: a listing with
 * a gap in it still reads, and one bad row emptying the attachments would be a single
 * bad row amplified into missing content. The metadata is never rewritten on the way
 * in or out: an attachment is what was picked.
 */
internal val AttachmentRecord.domainValue: Attachment?
    get() {
        val attachmentID = parseAttachmentID(id) ?: return null
        val itemID = parseWorkItemID(workItemID) ?: return null
        return Attachment(
            id = attachmentID,
            workItemID = itemID,
            projectID = parseProjectID(projectID),
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            blobPath = blobPath,
            blobRef = blobRef,
            createdBy = null,
            createdAt = createdAt,
            uploadedAt = uploadedAt,
        )
    }
