package com.tasktracker.featureprojectsdata

import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomUserID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.StatusChange
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The record↔domain mapping, tested as a round trip.
 *
 * ## Why round trips and not "each field maps"
 *
 * Asserting `record.title == "x"` for each field is a test that passes when a converter is
 * wrong in the other direction, and one that has to be rewritten every time a column is added.
 * Round-tripping a fixture whose every field is non-default catches a whole class of bug at
 * once: a column the write direction forgets is a field that comes back as its default, and a
 * column the read direction forgets is a field that comes back as something else entirely.
 *
 * The fixture is [aProject] / [aWorkItem], and their defaults are the *opposite* of the
 * domain's — which is the point. The original's two record↔domain converters both dropped a
 * field (`resolvedAt`, `nextItemNumber`), and every value it was tested with had those at
 * their defaults, so the round trip agreed with itself. [RecordCompletenessTest] then walks
 * the entities by reflection to make sure the fixture keeps that property as columns are
 * added.
 */
class RecordMappingTest {

    // MARK: Projects

    @Test
    fun aProjectSurvivesTheRoundTripThroughItsRecord() {
        val project = aProject()

        assertEquals(project, project.toRecord().domainValue)
    }

    @Test
    fun aProjectRecordCarriesEveryColumnAsAValue() {
        val project = aProject()
        val record = project.toRecord()

        assertEquals(project.id.stringValue, record.id)
        assertEquals("TT", record.keyPrefix)
        assertEquals("Task Tracker", record.name)
        assertEquals("Everything, in one place.", record.details)
        assertEquals(true, record.isArchived)
        // The counter is the one the original's converter dropped, so it gets its own
        // assertion rather than being left to the equality above.
        assertEquals(42, record.nextItemNumber)
        assertEquals(Moments.start, record.createdAt)
        assertEquals(Moments.later, record.updatedAt)
    }

    @Test
    fun aProjectCounterBelowOneIsFlooredByTheDomainRatherThanStored() {
        // `Project.init` floors the counter, so a payload carrying 0 cannot be stored as 0 —
        // which would issue a key that is already in use.
        val record = aProject(nextItemNumber = 0).toRecord()

        assertEquals(1, record.nextItemNumber)
    }

    @Test
    fun aProjectWithAnUnreadableIdIsDroppedRatherThanInvented() {
        // The original read `ProjectID(stringValue:) ?? ProjectID()`, so a corrupt id produced
        // a *new, real* project: unreachable by any delete, unmatched by any sync, and
        // indistinguishable from data.
        val record = aProject().toRecord().copy(id = "not-a-uuid")

        assertNull(record.domainValue)
    }

    @Test
    fun applyingAProjectOverwritesEveryFieldItOwnsAndKeepsTheStoredIdentity() {
        val stored = aProject().toRecord()
        val incoming = aProject(
            keyPrefix = "ZZ",
            name = "Renamed",
            details = "New notes.",
            isArchived = false,
            nextItemNumber = 99,
            createdAt = Moments.muchLater,
            updatedAt = Moments.muchLater,
        )

        val updated = stored.apply(incoming)

        assertEquals("ZZ", updated.keyPrefix)
        assertEquals("Renamed", updated.name)
        assertEquals("New notes.", updated.details)
        assertEquals(false, updated.isArchived)
        assertEquals(Moments.muchLater, updated.updatedAt)
        // Identity is not the payload's to change.
        assertEquals(stored.id, updated.id)
        assertEquals(stored.createdAt, updated.createdAt)
    }

    @Test
    fun applyingAProjectWhoseCounterIsBehindDoesNotRewindIt() {
        // The load-bearing case: two devices create items offline, both advance their copy to
        // 3, and the server's copy of the project is whatever was true when the earlier edit
        // was pushed. Honouring it would reissue a key that is already in use.
        val stored = aProject(nextItemNumber = 40).toRecord()

        val updated = stored.apply(aProject(nextItemNumber = 3))

        assertEquals(40, updated.nextItemNumber)
    }

    @Test
    fun applyingAProjectWhoseCounterIsAheadIsAllowedToWasteNumbers() {
        val stored = aProject(nextItemNumber = 2).toRecord()

        val updated = stored.apply(aProject(nextItemNumber = 500))

        // A counter that jumps forward only ever skips key numbers. A counter that jumps back
        // issues a duplicate, so the two cases are deliberately asymmetric.
        assertEquals(500, updated.nextItemNumber)
    }

    // MARK: Work items

    @Test
    fun aWorkItemSurvivesTheRoundTripThroughItsRecord() {
        val item = aWorkItem()

        assertEquals(item, item.toRecord().domainValue)
    }

    @Test
    fun aWorkItemRecordCarriesEveryColumnAsAValue() {
        val item = aWorkItem()
        val record = item.toRecord()

        assertEquals(item.id.stringValue, record.id)
        assertEquals(item.projectID.stringValue, record.projectID)
        assertEquals("TT-42", record.key)
        assertEquals("Write the migration", record.title)
        assertEquals("By hand, then check the generated SQL.", record.details)
        assertEquals("in_review", record.statusRaw)
        assertEquals(Priority.High.rank, record.priorityRaw)
        assertEquals("Ada", record.assignee)
        assertEquals(Moments.muchLater, record.dueDate)
        assertEquals(7, record.rank)
        assertEquals(Moments.start, record.createdAt)
        assertEquals(Moments.later, record.updatedAt)
    }

    @Test
    fun aTerminalWorkItemIsDatedByTheWriteDirectionRatherThanLeftUndated() {
        // `resolvedAt` is the other field the original dropped, and the consequence was
        // specific: the archive files by resolution date, so a `Done` card with no date is
        // invisible in it.
        val item = aWorkItem(status = WorkItemStatus.Done, resolvedAt = null)

        val record = item.toRecord()

        assertEquals(Moments.later, record.resolvedAt)
        assertEquals(Moments.later, record.domainValue?.resolvedAt)
    }

    @Test
    fun aTerminalWorkItemKeepsTheResolutionDateItAlreadyHas() {
        // Overwriting with the local clock would move a card filed under last month into this
        // month every time it was touched.
        val item = aWorkItem(status = WorkItemStatus.Done, resolvedAt = Moments.start)

        assertEquals(Moments.start, item.toRecord().resolvedAt)
    }

    @Test
    fun aWorkItemReadBackWithATerminalStatusAndNoDateIsDatedOnTheWayOut() {
        // A row that predates the column, or one written with a terminal status and no date,
        // heals on the first read rather than reaching the archive unfilable.
        val record = aWorkItem().toRecord().copy(
            statusRaw = WorkItemStatus.Done.wireValue,
            resolvedAt = null,
        )

        val read = record.domainValue

        assertEquals(Moments.later, read?.resolvedAt)
    }

    @Test
    fun aWorkItemReadBackAsLiveWithAStaleResolutionDateHasItCleared() {
        // The other direction, and the one that gets forgotten: a stale `resolvedAt` on a
        // reopened task files it in the archive forever, and nothing about that looks like a
        // bug on the board.
        val record = aWorkItem(
            status = WorkItemStatus.Todo,
            resolvedAt = Moments.start,
        ).toRecord()

        assertNull(record.domainValue?.resolvedAt)
    }

    @Test
    fun aWorkItemWithAnUnreadableIdOrProjectIsDropped() {
        val stored = aWorkItem().toRecord()

        assertNull(stored.copy(id = "not-a-uuid").domainValue)
        assertNull(stored.copy(projectID = "not-a-uuid").domainValue)
    }

    @Test
    fun aStoredStatusThisBuildDoesNotKnowReadsAsBacklogRatherThanThrowing() {
        // The same trade as storing the raw string at all: one row from a future build costs
        // one card, where a thrown decode costs the whole board.
        val record = aWorkItem().toRecord().copy(statusRaw = "awaiting_legal")

        assertEquals(WorkItemStatus.Backlog, record.domainValue?.status)
    }

    @Test
    fun aStoredPriorityThisBuildDoesNotKnowReadsAsMedium() {
        val record = aWorkItem().toRecord().copy(priorityRaw = 99)

        assertEquals(Priority.Medium, record.domainValue?.priority)
    }

    @Test
    fun applyingAWorkItemOverwritesEveryFieldItOwnsAndKeepsTheStoredIdentity() {
        val stored = aWorkItem().toRecord()
        val incoming = aWorkItem(
            key = "TT-43",
            title = "Renamed",
            details = "New notes.",
            status = WorkItemStatus.Done,
            priority = Priority.Lowest,
            assignee = null,
            dueDate = null,
            rank = 99,
            createdAt = Moments.muchLater,
            updatedAt = Moments.muchLater,
        )

        val updated = stored.apply(incoming)

        assertEquals("TT-43", updated.key)
        assertEquals("Renamed", updated.title)
        assertEquals("New notes.", updated.details)
        assertEquals(WorkItemStatus.Done.wireValue, updated.statusRaw)
        assertEquals(Priority.Lowest.rank, updated.priorityRaw)
        assertNull(updated.assignee)
        assertNull(updated.dueDate)
        assertEquals(99, updated.rank)
        assertEquals(Moments.muchLater, updated.updatedAt)
        // Identity, parent and age are not the payload's to change.
        assertEquals(stored.id, updated.id)
        assertEquals(stored.projectID, updated.projectID)
        assertEquals(stored.createdAt, updated.createdAt)
    }

    @Test
    fun applyingAWorkItemCannotMoveItToAnotherProject() {
        // A payload naming a different parent is a server bug, and applying it would move a
        // card between projects with no delete and nothing in the history.
        val stored = aWorkItem().toRecord()
        val elsewhere = aProject().toRecord().copy(id = "33333333-3333-4333-8333-333333333333")

        val updated = stored.apply(aWorkItem(projectID = elsewhere.domainValue!!.id))

        assertEquals(stored.projectID, updated.projectID)
    }

    @Test
    fun applyingATerminalWorkItemDatedByTheLocalClockRatherThanLeftUndated() {
        // The sink's path: a pulled `Done` card with no `resolvedAt`, which is the normal case
        // for one moved on another device.
        val stored = aWorkItem().toRecord()
        val incoming = aWorkItem(status = WorkItemStatus.Done, resolvedAt = null, updatedAt = Moments.muchLater)

        val updated = stored.apply(incoming)

        assertEquals(Moments.muchLater, updated.resolvedAt)
    }

    // MARK: Ordering

    @Test
    fun aRecordsOrderingKeyIsBuiltFromTheStoredColumns() {
        val record = aWorkItem(rank = 3, priority = Priority.Critical, key = "TT-9").toRecord()

        val key = record.orderingKey

        assertEquals(3, key.rank)
        assertEquals(Priority.Critical, key.priority)
        assertEquals(Moments.start, key.createdAt)
        assertEquals("TT-9", key.tieBreaker)
    }

    @Test
    fun aRecordsOrderingKeyAgreesWithTheDomainValueItDescribes() {
        // The reason `orderingKey` exists at all: the SQL orders by four columns and the
        // projection sorts by the same four through the domain. Two implementations of one
        // rule is how a board reorders itself whenever one of them changes.
        for (item in listOf(
            aWorkItem(),
            aWorkItem(rank = 0, priority = Priority.Lowest, key = "TT-1"),
            aWorkItem(status = WorkItemStatus.Done, resolvedAt = Moments.start),
        )) {
            val record = item.toRecord()
            val fromRecord = record.orderingKey
            val fromDomain = record.domainValue?.orderingKey

            assertNotNull(fromDomain)
            assertEquals(0, fromRecord.compareTo(fromDomain))
        }
    }

    // MARK: Status changes

    @Test
    fun aStatusChangeSurvivesTheRoundTripThroughItsRecord() {
        val change = StatusChange(
            workItemID = aWorkItem().id,
            projectID = aProject().id,
            from = WorkItemStatus.Todo,
            to = WorkItemStatus.InProgress,
            changedAt = Moments.start,
            changedBy = randomUserID(),
        )

        assertEquals(change, change.toRecord().domainValue)
    }

    @Test
    fun aStatusChangeCreatedInPlaceKeepsItsNullFrom() {
        // `null` means "created here", which is a real state and not an absent value — a
        // history that cannot tell creation from a move cannot explain a new card.
        val change = StatusChange(
            workItemID = aWorkItem().id,
            projectID = aProject().id,
            from = null,
            to = WorkItemStatus.Backlog,
            changedAt = Moments.start,
            changedBy = null,
        )

        val record = change.toRecord()

        assertNull(record.fromRaw)
        assertTrue(record.domainValue?.isCreation == true)
    }

    @Test
    fun aStatusChangeWithAnUnreadableIdOrDestinationIsDropped() {
        val stored = aStatusChange().toRecord()

        assertNull(stored.copy(id = "not-a-uuid").domainValue)
        assertNull(stored.copy(toRaw = "awaiting_legal").domainValue)
        assertNull(stored.copy(workItemID = "not-a-uuid").domainValue)
        assertNull(stored.copy(projectID = "not-a-uuid").domainValue)
    }

    @Test
    fun aStatusChangeWithAnUnreadableAuthorLosesTheAuthorRatherThanTheRow() {
        // The author is *displayed* on a row of history, so an unparseable one has to become
        // nothing. Losing the author costs a blank; throwing would cost the transition.
        val record = aStatusChange().toRecord().copy(changedByUserID = "not-a-uuid")

        val read = record.domainValue

        assertNotNull(read)
        assertNull(read.changedBy)
    }

    @Test
    fun aStatusChangeWithAnUnreadableFromIsReadAsCreatedRatherThanDropped() {
        // The rest of the entry is still true, so dropping the row would lose a real
        // transition. It is *not* the same as a genuine null `from`, and the original
        // conflated the two — so a row with a corrupt `from` claimed to be a creation. The
        // two are indistinguishable at this level, which is why the behaviour is asserted
        // rather than left to chance.
        val record = aStatusChange().toRecord().copy(fromRaw = "awaiting_legal")

        val read = assertNotNull(record.domainValue)

        assertNull(read.from)
        assertEquals(WorkItemStatus.InProgress, read.to)
    }

    // MARK: Key

    @Test
    fun aWorkItemKeyIsCarriedAsItsRawStringRatherThanReassembled() {
        // `TT-42` is stored as typed. A server may issue a key this build's
        // `prefix-number` convention would never produce, and normalising it would corrupt a
        // key other devices and every server reference already use.
        val record = aWorkItem(key = "SOMETHING-ELSE").toRecord()

        assertEquals(WorkItemKey("SOMETHING-ELSE"), record.domainValue?.key)
    }

    private fun aStatusChange(): StatusChange = StatusChange(
        workItemID = aWorkItem().id,
        projectID = aProject().id,
        from = WorkItemStatus.Todo,
        to = WorkItemStatus.InProgress,
        changedAt = Moments.start,
        changedBy = randomUserID(),
    )
}
