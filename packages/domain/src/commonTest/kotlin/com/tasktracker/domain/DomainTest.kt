package com.tasktracker.domain

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.graphemeClusterCount
import com.tasktracker.core.parseStatusChangeID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomUserID
import com.tasktracker.core.randomWorkItemID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProjectRulesTest {

    @Test
    fun namesAreTrimmedAndMustNotBeBlank() {
        assertEquals("Apollo", ProjectRules.normaliseName("  Apollo  "))
        assertFailsWith<AppError> { ProjectRules.normaliseName("   ") }
        assertFailsWith<AppError> { ProjectRules.normaliseName("") }
    }

    @Test
    fun overLongNamesAreRejectedRatherThanSilentlyTruncated() {
        val long = "a".repeat(ProjectRules.MAX_NAME_LENGTH + 1)
        assertFailsWith<AppError> { ProjectRules.normaliseName(long) }
        val atLimit = "a".repeat(ProjectRules.MAX_NAME_LENGTH)
        assertEquals(atLimit, ProjectRules.normaliseName(atLimit))
    }

    @Test
    fun nameLengthIsCountedInGraphemesNotCodeUnits() {
        // The whole reason `graphemeClusterCount` exists: `length` counts UTF-16 units,
        // and a family emoji is 8 of them, so a `length` guard would let in eight times
        // as much text as the original ever did.
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        val atLimit = family.repeat(ProjectRules.MAX_NAME_LENGTH)
        assertEquals(ProjectRules.MAX_NAME_LENGTH * 8, atLimit.length)
        assertEquals(ProjectRules.MAX_NAME_LENGTH, graphemeClusterCount(atLimit))
        assertEquals(atLimit, ProjectRules.normaliseName(atLimit))
        assertFailsWith<AppError> { ProjectRules.normaliseName(atLimit + family) }
    }

    @Test
    fun keyPrefixesAreUppercasedSoKeysCannotDifferOnlyByCase() {
        assertEquals("TT", ProjectRules.normaliseKeyPrefix("tt"))
        assertEquals("APOLLO", ProjectRules.normaliseKeyPrefix(" apollo "))
    }

    @Test
    fun keyPrefixesOutsideTheLengthRangeAreRejected() {
        assertFailsWith<AppError> { ProjectRules.normaliseKeyPrefix("T") }
        assertFailsWith<AppError> { ProjectRules.normaliseKeyPrefix("A".repeat(11)) }
        // The boundaries themselves are allowed.
        assertEquals("AB", ProjectRules.normaliseKeyPrefix("AB"))
        assertEquals("A".repeat(10), ProjectRules.normaliseKeyPrefix("A".repeat(10)))
    }

    @Test
    fun keyPrefixesRejectPunctuation() {
        assertFailsWith<AppError> { ProjectRules.normaliseKeyPrefix("T-T") }
        assertFailsWith<AppError> { ProjectRules.normaliseKeyPrefix("T T") }
    }

    @Test
    fun keyPrefixesRejectNonAsciiLettersThatUppercaseWithoutChanging() {
        // `Д` is a letter, so a check that only asked "is it a letter?" would accept it
        // and mint a key the server can never produce.
        assertFailsWith<AppError> { ProjectRules.normaliseKeyPrefix("ДД") }
        assertFailsWith<AppError> { ProjectRules.normaliseKeyPrefix("éé") }
    }

    @Test
    fun workItemKeysAreBuiltFromThePrefixAndAOneBasedSequence() {
        assertEquals("TT-1", ProjectRules.workItemKey("TT", 1).rawValue)
        assertEquals("TT-142", ProjectRules.workItemKey("TT", 142).rawValue)
        assertEquals("TT-1", ProjectRules.workItemKey("TT", 0).rawValue)
    }

    @Test
    fun projectCounterIsFlooredAtOneSoAKeyIsNeverReissued() {
        val now = Instant.fromEpochSeconds(1_700_000_000)
        assertEquals(1, Project(keyPrefix = "TT", name = "A", createdAt = now, updatedAt = now, nextItemNumber = 0).nextItemNumber)
        assertEquals(1, Project(keyPrefix = "TT", name = "A", createdAt = now, updatedAt = now, nextItemNumber = -5).nextItemNumber)
        assertEquals(7, Project(keyPrefix = "TT", name = "A", createdAt = now, updatedAt = now, nextItemNumber = 7).nextItemNumber)
    }
}

class WorkItemRulesTest {

    @Test
    fun titlesAreTrimmedAndMustNotBeBlank() {
        assertEquals("Ship it", WorkItemRules.normaliseTitle("  Ship it  "))
        assertFailsWith<AppError> { WorkItemRules.normaliseTitle("  ") }
    }

    @Test
    fun aBlankAssigneeBecomesNullSoUnassignedHasOneRepresentation() {
        assertNull(WorkItemRules.normaliseAssignee(null))
        assertNull(WorkItemRules.normaliseAssignee(""))
        assertNull(WorkItemRules.normaliseAssignee("   "))
        assertEquals("Ada", WorkItemRules.normaliseAssignee("  Ada "))
    }

    @Test
    fun overLongTitlesAndAssigneesAreRejected() {
        assertFailsWith<AppError> {
            WorkItemRules.normaliseTitle("t".repeat(WorkItemRules.MAX_TITLE_LENGTH + 1))
        }
        assertFailsWith<AppError> {
            WorkItemRules.normaliseAssignee("a".repeat(WorkItemRules.MAX_ASSIGNEE_LENGTH + 1))
        }
    }

    @Test
    fun reopeningADoneItemStaysLegal() {
        assertTrue(WorkItemRules.canTransition(WorkItemStatus.Done, WorkItemStatus.InProgress))
        // Every transition is permitted, including out of Closed — the board that
        // refuses it sends people off to edit the database.
        assertTrue(WorkItemRules.canTransition(WorkItemStatus.Closed, WorkItemStatus.Todo))
    }
}

class WorkItemStatusTest {

    @Test
    fun backlogAndClosedWorkIsOffTheBoardEverythingElseIsOnIt() {
        assertEquals(
            listOf(WorkItemStatus.Todo, WorkItemStatus.InProgress, WorkItemStatus.InReview, WorkItemStatus.Done),
            WorkItemStatus.boardOrder,
        )
        assertFalse(WorkItemStatus.Backlog.appearsOnBoard)
        assertFalse(WorkItemStatus.Closed.appearsOnBoard)
        assertTrue(WorkItemStatus.Done.appearsOnBoard)
    }

    @Test
    fun boardOrderFollowsMembership() {
        // Derived rather than hand-listed, so adding a status to the enum cannot leave
        // the board quietly missing a column.
        assertEquals(
            WorkItemStatus.entries.filter { it.appearsOnBoard },
            WorkItemStatus.boardOrder,
        )
    }

    @Test
    fun doneAndClosedAreBothFinishedWith() {
        assertEquals(
            listOf(WorkItemStatus.Done, WorkItemStatus.Closed),
            WorkItemStatus.entries.filter { it.isTerminal },
        )
    }

    @Test
    fun onlyDoneCountsAsComplete() {
        assertEquals(listOf(WorkItemStatus.Done), WorkItemStatus.entries.filter { it.isComplete })
    }

    @Test
    fun closedIsTerminalButNotCompleteWhichIsTheWholePointOfIt() {
        // A closed item was never delivered, so a project holding one must be archived
        // rather than deleted. Collapsing the two predicates would delete its archive.
        assertTrue(WorkItemStatus.Closed.isTerminal)
        assertFalse(WorkItemStatus.Closed.isComplete)
    }

    @Test
    fun everyStatusHasAUniqueWireValueThatRoundTrips() {
        val values = WorkItemStatus.entries.map { it.wireValue }
        assertEquals(values.size, values.toSet().size)
        for (status in WorkItemStatus.entries) {
            assertEquals(status, WorkItemStatus.fromWireValue(status.wireValue))
        }
        assertNull(WorkItemStatus.fromWireValue("nonsense"))
    }

    @Test
    fun wireValuesAreTheSerializedNames() {
        // The JSON name, the Room column value and the deep-link segment are one string.
        // If these ever drift, a stored `in_progress` stops matching an enum constant.
        val expected = mapOf(
            WorkItemStatus.Backlog to "backlog",
            WorkItemStatus.Todo to "todo",
            WorkItemStatus.InProgress to "in_progress",
            WorkItemStatus.InReview to "in_review",
            WorkItemStatus.Done to "done",
            WorkItemStatus.Closed to "closed",
        )
        for ((status, wire) in expected) {
            assertEquals(wire, status.wireValue)
        }
    }
}

class WorkItemOrderingTest {

    private val origin = Instant.fromEpochSeconds(1_700_000_000)

    private fun item(
        key: String,
        rank: Int = 0,
        priority: Priority = Priority.Medium,
        createdOffsetSeconds: Long = 0,
    ) = WorkItem(
        projectID = randomProjectID(),
        key = WorkItemKey(key),
        title = key,
        priority = priority,
        rank = rank,
        createdAt = origin + createdOffsetSeconds.seconds,
        updatedAt = origin + createdOffsetSeconds.seconds,
    )

    @Test
    fun manualRankIsThePrimarySort() {
        val lowRank = item("TT-1", rank = 0, priority = Priority.Lowest)
        val highRank = item("TT-2", rank = 1, priority = Priority.Critical)
        assertTrue(lowRank.orderingKey < highRank.orderingKey)
    }

    @Test
    fun withinARankHigherPriorityComesFirst() {
        val sorted = listOf(
            item("TT-1", priority = Priority.Low),
            item("TT-2", priority = Priority.Critical),
        ).sortedBy { it.orderingKey }
        assertEquals(listOf("TT-2", "TT-1"), sorted.map { it.key.rawValue })
    }

    @Test
    fun equalRankAndPriorityFallsBackToOldestFirst() {
        val sorted = listOf(
            item("TT-1", priority = Priority.High, createdOffsetSeconds = 60),
            item("TT-2", priority = Priority.High, createdOffsetSeconds = 0),
        ).sortedBy { it.orderingKey }
        assertEquals(listOf("TT-2", "TT-1"), sorted.map { it.key.rawValue })
    }

    @Test
    fun itemsIdenticalInEveryOtherRespectStillOrderDeterministically() {
        val a = item("TT-1")
        val b = item("TT-2")
        assertTrue(a.orderingKey < b.orderingKey)
        assertEquals(listOf("TT-1", "TT-2"), listOf(a, b).sortedBy { it.orderingKey }.map { it.key.rawValue })
        // And the same answer from the other direction: the order does not depend on
        // the input order, which is what "deterministic" is protecting.
        assertEquals(listOf("TT-1", "TT-2"), listOf(b, a).sortedBy { it.orderingKey }.map { it.key.rawValue })
    }
}

class WorkItemGroupingTest {

    private val projectID = randomProjectID()
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private fun item(
        key: String,
        status: WorkItemStatus,
        priority: Priority = Priority.Medium,
        rank: Int = 0,
        resolvedOffsetSeconds: Long? = null,
    ) = WorkItem(
        projectID = projectID,
        key = WorkItemKey(key),
        title = key,
        status = status,
        priority = priority,
        rank = rank,
        createdAt = now,
        updatedAt = now,
        resolvedAt = resolvedOffsetSeconds?.let { now + it.seconds },
    )

    @Test
    fun boardColumnsCoverEveryBoardStatusInOrderEvenWhenEmpty() {
        val columns = WorkItemGrouping.boardColumns(listOf(item("TT-1", WorkItemStatus.Todo)))
        assertEquals(WorkItemStatus.boardOrder, columns.map { it.status })
        assertEquals(1, columns.first().items.size)
        assertTrue(columns.first { it.status == WorkItemStatus.Done }.items.isEmpty())
    }

    @Test
    fun columnContentsAreOrderedByTheSharedOrderingKey() {
        val columns = WorkItemGrouping.boardColumns(
            listOf(
                item("TT-1", WorkItemStatus.Todo, priority = Priority.Low),
                item("TT-2", WorkItemStatus.Todo, priority = Priority.Critical),
            ),
        )
        val todo = columns.first { it.status == WorkItemStatus.Todo }
        assertEquals(listOf("TT-2", "TT-1"), todo.items.map { it.key.rawValue })
    }

    @Test
    fun boardColumnsNeverHoldUntriagedOrClosedWork() {
        val columns = WorkItemGrouping.boardColumns(
            listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Closed, resolvedOffsetSeconds = 0),
            ),
        )
        assertTrue(columns.all { it.items.isEmpty() })
    }

    @Test
    fun untriagedHoldsOnlyWorkThatIsNotOnABoardYet() {
        val untriaged = WorkItemGrouping.untriaged(
            listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Done, resolvedOffsetSeconds = 0),
                item("TT-3", WorkItemStatus.Todo),
                item("TT-4", WorkItemStatus.Closed, resolvedOffsetSeconds = 0),
                item("TT-5", WorkItemStatus.InProgress),
            ),
        )
        // Same rank, priority and creation instant, so the key tie-break decides.
        assertEquals(listOf("TT-1"), untriaged.map { it.key.rawValue })
    }

    @Test
    fun theBacklogAndTheBoardNeverListTheSameItem() {
        val items = WorkItemStatus.entries.mapIndexed { index, status ->
            item(
                key = "TT-$index",
                status = status,
                resolvedOffsetSeconds = if (status.isTerminal) 0 else null,
            )
        }
        val onBoard = WorkItemGrouping.boardColumns(items).flatMap { it.items }.map { it.id }.toSet()
        val untriaged = WorkItemGrouping.untriaged(items).map { it.id }.toSet()
        assertTrue(onBoard.intersect(untriaged).isEmpty())
    }

    @Test
    fun resolvedHoldsDoneAndClosedWorkAndNothingStillInFlight() {
        val resolved = WorkItemGrouping.resolved(
            listOf(
                item("TT-1", WorkItemStatus.Backlog),
                item("TT-2", WorkItemStatus.Done, resolvedOffsetSeconds = 0),
                item("TT-3", WorkItemStatus.Todo),
                item("TT-4", WorkItemStatus.Closed, resolvedOffsetSeconds = 0),
                item("TT-5", WorkItemStatus.InReview),
            ),
        )
        assertEquals(listOf("TT-2", "TT-4"), resolved.map { it.key.rawValue }.sorted())
    }

    @Test
    fun resolvedIsNewestFirstNotByManualRank() {
        // Rank is inverted on purpose: if resolved honoured it, the order would flip.
        val resolved = WorkItemGrouping.resolved(
            listOf(
                item("TT-1", WorkItemStatus.Done, rank = 9, resolvedOffsetSeconds = 100),
                item("TT-2", WorkItemStatus.Closed, rank = 0, resolvedOffsetSeconds = 300),
                item("TT-3", WorkItemStatus.Done, rank = 5, resolvedOffsetSeconds = 200),
            ),
        )
        assertEquals(listOf("TT-2", "TT-3", "TT-1"), resolved.map { it.key.rawValue })
    }

    @Test
    fun closedWorkIsArchiveOnlyWhileDoneWorkIsBothALaneAndARecord() {
        val items = WorkItemStatus.entries.mapIndexed { index, status ->
            item(
                key = "TT-$index",
                status = status,
                resolvedOffsetSeconds = if (status.isTerminal) 0 else null,
            )
        }
        val doneColumn = WorkItemGrouping.boardColumns(items)
            .first { it.status == WorkItemStatus.Done }
            .items
            .map { it.id }
            .toSet()
        val archived = WorkItemGrouping.resolved(items)

        // Deliberate overlap: the Done column is what was just finished, the archive is
        // everything that ever was. Collapsing them would leave the board with no way to
        // show recent completions.
        assertEquals(
            archived.filter { it.status == WorkItemStatus.Done }.map { it.id }.toSet(),
            doneColumn,
        )

        // Closed is the one status the board cannot show, so the archive is the only
        // place it appears — which is what makes a closed item worth archiving a
        // project for.
        assertTrue(archived.any { it.status == WorkItemStatus.Closed })
        assertTrue(
            doneColumn.intersect(archived.filter { it.status == WorkItemStatus.Closed }.map { it.id }.toSet())
                .isEmpty(),
        )
    }
}

class StatusChangeTest {

    private val workItemID = randomWorkItemID()
    private val projectID = randomProjectID()
    private val whenAt = Instant.fromEpochSeconds(1_700_000_000)

    private fun change(
        from: WorkItemStatus?,
        to: WorkItemStatus,
        changedBy: UserID? = null,
    ) = StatusChange(
        workItemID = workItemID,
        projectID = projectID,
        from = from,
        to = to,
        changedAt = whenAt,
        changedBy = changedBy,
    )

    @Test
    fun aChangeWithNoOriginIsTheItemsCreation() {
        assertTrue(change(null, WorkItemStatus.Backlog).isCreation)
        assertTrue(change(null, WorkItemStatus.Todo).isCreation)
        assertFalse(change(WorkItemStatus.Backlog, WorkItemStatus.Todo).isCreation)
    }

    @Test
    fun movesHaveOrigins() {
        assertEquals(WorkItemStatus.Backlog, change(WorkItemStatus.Backlog, WorkItemStatus.Todo).from)
        assertEquals(WorkItemStatus.Done, change(WorkItemStatus.Done, WorkItemStatus.InProgress).from)
        // Closed is a real origin, not an absence: reopening a closed item is a move.
        assertEquals(WorkItemStatus.Closed, change(WorkItemStatus.Closed, WorkItemStatus.Todo).from)
    }

    @Test
    fun aChangeThatDidNotMoveTheStatusIsNotAStatusChange() {
        assertFalse(change(WorkItemStatus.Todo, WorkItemStatus.Todo).altersStatus)
        assertTrue(change(WorkItemStatus.Todo, WorkItemStatus.InProgress).altersStatus)
    }

    @Test
    fun anUnknownAuthorIsARepresentableValueNotAMissingField() {
        val unattributed = change(WorkItemStatus.Todo, WorkItemStatus.Done)
        assertNull(unattributed.changedBy)
        assertNotNull(change(WorkItemStatus.Todo, WorkItemStatus.Done, changedBy = randomUserID()).changedBy)
    }

    @Test
    fun changesSurviveARoundTripIncludingANullOriginAndAuthor() {
        val original = change(null, WorkItemStatus.Backlog)
        val encoded = JSONCoding.json.encodeToString(StatusChange.serializer(), original)
        val decoded = JSONCoding.json.decodeFromString(StatusChange.serializer(), encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.isCreation)
        assertNull(decoded.changedBy)
    }

    @Test
    fun identifiersAreDistinctAndStableThroughAString() {
        val a = randomStatusChangeID()
        val b = randomStatusChangeID()
        assertFalse(a == b)
        assertEquals(a, assertNotNull(parseStatusChangeID(a.stringValue)))
        assertNull(parseStatusChangeID("not-a-uuid"))
    }
}

class WorkItemResolvedAtTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private fun item(status: WorkItemStatus, resolvedAt: Instant?) = WorkItem(
        projectID = randomProjectID(),
        key = WorkItemKey("TT-1"),
        title = "TT-1",
        status = status,
        createdAt = now,
        updatedAt = now + 500.seconds,
        resolvedAt = resolvedAt,
    )

    @Test
    fun anItemThatHasLeftTheFlowIsFiledUnderWhenItDid() {
        assertEquals(now, item(WorkItemStatus.Done, now).archivedOn)
    }

    @Test
    fun anItemWithNoResolutionDateFallsBackToWhenItWasLastUpdated() {
        // An item finished on another device arrives with no resolvedAt. Filing it by
        // updatedAt keeps it in the archive rather than dropping it out of the view.
        assertEquals(now + 500.seconds, item(WorkItemStatus.Done, null).archivedOn)
    }

    @Test
    fun reachingATerminalStatusDatesTheItem() {
        val subject = item(WorkItemStatus.Todo, null)
        subject.status = WorkItemStatus.Done
        subject.reconcileResolutionAt(now)
        assertEquals(now, subject.resolvedAt)
    }

    @Test
    fun reopeningClearsTheDateSoALiveTaskIsNotArchivedForever() {
        // The case that gets forgotten: a stale resolvedAt files a live task in the
        // archive permanently, and nothing about that looks like a bug on the board.
        val subject = item(WorkItemStatus.Done, now)
        subject.status = WorkItemStatus.InProgress
        subject.reconcileResolutionAt(now + 60.seconds)
        assertNull(subject.resolvedAt)
    }

    @Test
    fun anExistingDateIsNotRewrittenWithTheLocalClock() {
        // Otherwise a pulled change would be stamped with the local time on every sync.
        val pulled = now - 3_600.seconds
        val subject = item(WorkItemStatus.Done, pulled)
        subject.reconcileResolutionAt(now)
        assertEquals(pulled, subject.resolvedAt)
    }
}
