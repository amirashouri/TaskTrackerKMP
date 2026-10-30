package com.tasktracker.domain

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.SprintID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.graphemeClusterCount
import com.tasktracker.core.parseStatusChangeID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomSprintID
import com.tasktracker.core.randomStatusChangeID
import com.tasktracker.core.randomUserID
import com.tasktracker.core.randomWorkItemID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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
    fun overLongTitlesAreRejected() {
        assertFailsWith<AppError> {
            WorkItemRules.normaliseTitle("t".repeat(WorkItemRules.MAX_TITLE_LENGTH + 1))
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
        createdOffsetSeconds: Long = 0,
        resolvedOffsetSeconds: Long? = null,
    ) = WorkItem(
        projectID = projectID,
        key = WorkItemKey(key),
        title = key,
        status = status,
        priority = priority,
        rank = rank,
        createdAt = now + createdOffsetSeconds.seconds,
        updatedAt = now + createdOffsetSeconds.seconds,
        resolvedAt = resolvedOffsetSeconds?.let { now + it.seconds },
    )

    /**
     * A sort key, spelled short so a two-key expectation stays readable on one line.
     *
     * Defaults to descending because that is what a basis reads in when it is first switched
     * on, so a test that only cares about *which* keys are active does not have to say it.
     */
    private fun key(basis: BacklogSortBasis, descending: Boolean = true) =
        BacklogSortKey(basis, descending)

    private fun order(vararg keys: BacklogSortKey) = BacklogSortOrder(keys.toList())

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
    fun theBacklogOrdersByPriorityRatherThanInsertionRank() {
        val untriaged = WorkItemGrouping.untriaged(
            listOf(
                item("TT-1", WorkItemStatus.Backlog, priority = Priority.Low, rank = 0),
                item("TT-2", WorkItemStatus.Backlog, priority = Priority.Critical, rank = 5),
                item(
                    "TT-3",
                    WorkItemStatus.Backlog,
                    priority = Priority.Critical,
                    rank = 2,
                    createdOffsetSeconds = 60,
                ),
            ),
        )

        // Rank would put TT-1 first and TT-3 before TT-2. The default order is the one key the
        // backlog opens with — priority — so the two critical items tie and fall to the key
        // tie-break. A tie-break by creation time is a second key the user has to switch on,
        // which is what aSecondKeyOnlyBreaksTheTiesTheLeaderLeaves covers.
        assertEquals(listOf("TT-2", "TT-3", "TT-1"), untriaged.map { it.key.rawValue })
    }

    @Test
    fun aSecondKeyOnlyBreaksTheTiesTheLeaderLeaves() {
        val items = listOf(
            item("TT-1", WorkItemStatus.Backlog, priority = Priority.Critical),
            item(
                "TT-2",
                WorkItemStatus.Backlog,
                priority = Priority.Critical,
                createdOffsetSeconds = 60,
            ),
            item(
                "TT-3",
                WorkItemStatus.Backlog,
                priority = Priority.Low,
                createdOffsetSeconds = 120,
            ),
        )

        // Priority alone leaves the two critical items tied, and the key tie-break then reads
        // TT-1 first. Adding creation time as the second key is what flips that pair.
        assertEquals(
            listOf("TT-1", "TT-2", "TT-3"),
            WorkItemGrouping.untriaged(items, order(key(BacklogSortBasis.Priority)))
                .map { it.key.rawValue },
        )
        assertEquals(
            listOf("TT-2", "TT-1", "TT-3"),
            WorkItemGrouping.untriaged(
                items,
                order(key(BacklogSortBasis.Priority), key(BacklogSortBasis.CreatedAt)),
            ).map { it.key.rawValue },
        )
    }

    @Test
    fun lowestPriorityFirstKeepsItsTieBreakWhenItLeads() {
        val untriaged = WorkItemGrouping.untriaged(
            listOf(
                item("TT-1", WorkItemStatus.Backlog, priority = Priority.Low),
                item("TT-2", WorkItemStatus.Backlog, priority = Priority.Critical),
                item(
                    "TT-3",
                    WorkItemStatus.Backlog,
                    priority = Priority.Critical,
                    createdOffsetSeconds = 60,
                ),
            ),
            order = order(
                key(BacklogSortBasis.Priority, descending = false),
                key(BacklogSortBasis.CreatedAt),
            ),
        )

        // Reversing the leader reverses only the leader: within equal priority the tie-breaker
        // still reads newest first, so TT-3 stays above TT-2.
        assertEquals(listOf("TT-1", "TT-3", "TT-2"), untriaged.map { it.key.rawValue })
    }

    @Test
    fun createdOrderPutsNewestFirstThenBreaksTiesByPriority() {
        // TT-2 and TT-3 share an instant, and the key tie-break would put TT-2 first. Priority
        // is the second key, so the critical item has to come first for the tie-break to be
        // doing anything at all.
        val items = listOf(
            item("TT-1", WorkItemStatus.Backlog, priority = Priority.Low),
            item(
                "TT-2",
                WorkItemStatus.Backlog,
                priority = Priority.Medium,
                createdOffsetSeconds = 120,
            ),
            item(
                "TT-3",
                WorkItemStatus.Backlog,
                priority = Priority.Critical,
                createdOffsetSeconds = 120,
            ),
        )

        assertEquals(
            listOf("TT-3", "TT-2", "TT-1"),
            WorkItemGrouping.untriaged(
                items,
                order(key(BacklogSortBasis.CreatedAt), key(BacklogSortBasis.Priority)),
            ).map { it.key.rawValue },
        )
        assertEquals(
            listOf("TT-1", "TT-3", "TT-2"),
            WorkItemGrouping.untriaged(
                items,
                order(
                    key(BacklogSortBasis.CreatedAt, descending = false),
                    key(BacklogSortBasis.Priority),
                ),
            ).map { it.key.rawValue },
        )
    }

    @Test
    fun tappingAnInactiveBasisPutsItInFrontAtItsDefaultDirection() {
        assertEquals(
            listOf(key(BacklogSortBasis.CreatedAt), key(BacklogSortBasis.Priority)),
            BacklogSortOrder().toggled(BacklogSortBasis.CreatedAt).keys,
        )
        // The key that loses the lead keeps the direction it was already reading in: a tap on
        // one chip is not a request to reverse the other.
        assertEquals(
            listOf(
                key(BacklogSortBasis.CreatedAt),
                key(BacklogSortBasis.Priority, descending = false),
            ),
            order(key(BacklogSortBasis.Priority, descending = false))
                .toggled(BacklogSortBasis.CreatedAt)
                .keys,
        )
    }

    @Test
    fun tappingTheLeadingBasisReversesItThenSwitchesItOff() {
        val both = BacklogSortOrder().toggled(BacklogSortBasis.CreatedAt)
        val reversed = both.toggled(BacklogSortBasis.CreatedAt)

        assertEquals(
            listOf(key(BacklogSortBasis.CreatedAt, descending = false), key(BacklogSortBasis.Priority)),
            reversed.keys,
        )
        // Reversing it twice leaves nothing to reverse, so the second tap is the way back to a
        // single-key order — and the tie-breaker takes the lead.
        assertEquals(
            listOf(key(BacklogSortBasis.Priority)),
            reversed.toggled(BacklogSortBasis.CreatedAt).keys,
        )
    }

    @Test
    fun tappingTheTieBreakerPromotesItAndKeepsItsDirection() {
        val both = BacklogSortOrder().toggled(BacklogSortBasis.CreatedAt)

        assertEquals(
            listOf(key(BacklogSortBasis.Priority), key(BacklogSortBasis.CreatedAt)),
            both.toggled(BacklogSortBasis.Priority).keys,
        )
        assertEquals(
            listOf(
                key(BacklogSortBasis.Priority, descending = false),
                key(BacklogSortBasis.CreatedAt),
            ),
            order(key(BacklogSortBasis.CreatedAt), key(BacklogSortBasis.Priority, descending = false))
                .toggled(BacklogSortBasis.Priority)
                .keys,
        )
    }

    @Test
    fun theLastRemainingKeyCyclesItsDirectionInsteadOfSwitchingOff() {
        // A list has to be ordered by something, so a lone key never reaches the off state.
        assertEquals(
            listOf(key(BacklogSortBasis.Priority, descending = false)),
            BacklogSortOrder().toggled(BacklogSortBasis.Priority).keys,
        )
        assertEquals(
            listOf(key(BacklogSortBasis.Priority)),
            BacklogSortOrder()
                .toggled(BacklogSortBasis.Priority)
                .toggled(BacklogSortBasis.Priority)
                .keys,
        )
    }

    @Test
    fun aBasisCannotAppearTwiceInTheOrder() {
        // Two keys for one basis would make one of them dead weight and the comparator's chain
        // meaningless, so the type refuses it rather than sorting by it twice.
        assertFailsWith<IllegalArgumentException> {
            order(key(BacklogSortBasis.Priority), key(BacklogSortBasis.Priority, descending = false))
        }
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

class SprintTest {

    private val projectID = randomProjectID()
    private val now = Instant.fromEpochSeconds(1_700_000_000)
    private val start = now
    private val end = now + 14.days

    private fun sprint(
        name: String = "Sprint 1",
        goalStoryPoints: Int = 21,
        startDate: Instant = start,
        endDate: Instant = end,
        ownerID: UserID? = UserID("11111111-1111-4111-8111-111111111111"),
        isFinished: Boolean = false,
        finishedAt: Instant? = null,
    ) = Sprint(
        projectID = projectID,
        name = name,
        goalStoryPoints = goalStoryPoints,
        startDate = startDate,
        endDate = endDate,
        ownerID = ownerID,
        isFinished = isFinished,
        finishedAt = finishedAt,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun aNegativeGoalIsFlooredRatherThanStored() {
        // Rejecting instead would leave a sprint the UI cannot render a goal for. This
        // mirrors Project's treatment of nextItemNumber: normalise on the way in.
        assertEquals(0, sprint(goalStoryPoints = -5).goalStoryPoints)
    }

    @Test
    fun aWindowThatEndsBeforeItStartsIsConstructibleButNotActive() {
        // Not refused. A sprint pulled back in time arrives that way from a server, and a
        // thrown constructor in a data class would take down the read of the whole list
        // rather than the one bad row. isActiveOn is what answers the question.
        val backwards = sprint(startDate = end, endDate = start)
        assertFalse(backwards.isActiveOn(now))
    }

    @Test
    fun theWindowIncludesBothEnds() {
        assertTrue(sprint().isActiveOn(start))
        assertTrue(sprint().isActiveOn(end))
        assertFalse(sprint().isActiveOn(start - 1.seconds))
        assertFalse(sprint().isActiveOn(end + 1.seconds))
    }

    @Test
    fun finishingDatesTheSprint() {
        val subject = sprint()
        subject.isFinished = true
        subject.reconcileFinishedAt(now + 3.days)
        assertEquals(now + 3.days, subject.finishedAt)
    }

    @Test
    fun reopeningClearsTheDate() {
        // The half of the invariant that gets forgotten, exactly as WorkItem's
        // resolvedAt does. A stale finishedAt would report a sprint as closed on the list
        // while the board still let tasks be added to it.
        val subject = sprint(isFinished = true, finishedAt = now + 3.days)
        subject.isFinished = false
        subject.reconcileFinishedAt(now + 4.days)
        assertNull(subject.finishedAt)
    }

    @Test
    fun anExistingFinishDateIsNotRestampedWithTheLocalClock() {
        val pulled = now + 2.days
        val subject = sprint(isFinished = true, finishedAt = pulled)
        subject.reconcileFinishedAt(now + 9.days)
        assertEquals(pulled, subject.finishedAt)
    }

    @Test
    fun anUnownedSprintIsNullRatherThanAFabricatedId() {
        // Not a blank string or a zero id. "nobody owns this sprint" is a state both the
        // summary and the Finish button have to handle — so it gets exactly one
        // representation: `ownerID == null`.
        assertNull(sprint(ownerID = null).ownerID)
    }

    @Test
    fun twoWindowsThatMeetAtAnEndOverlap() {
        // The first ends on the 16th and the second starts on it. `isActiveOn` is inclusive
        // on both, so both are active at 00:00 on the 16th — the case the rule exists to
        // refuse, and the one a half-open-interval reading would wave through.
        val first = sprint(startDate = start, endDate = start + 14.days)
        val second = sprint(startDate = start + 14.days, endDate = start + 28.days)
        assertTrue(first.overlaps(second))
        assertTrue(second.overlaps(first), "the relation is symmetric")
    }

    @Test
    fun aDayStaggeredWindowDoesNotOverlap() {
        // 2–15 against 16–30: the second starts the day after the first ends. This is one
        // day shorter than the meeting case above, and it is the case the rule permits.
        val first = sprint(startDate = start, endDate = start + 13.days)
        val second = sprint(startDate = start + 14.days, endDate = start + 28.days)
        assertFalse(first.overlaps(second))
    }

    @Test
    fun aWindowInsideAnotherOverlapsIt() {
        val outer = sprint(startDate = start, endDate = start + 30.days)
        val inner = sprint(startDate = start + 5.days, endDate = start + 10.days)
        assertTrue(outer.overlaps(inner))
        assertTrue(inner.overlaps(outer))
    }

    @Test
    fun theFreeWindowCheckAndTheMemberAgree() {
        // windowsOverlap is what the editor calls with a draft's raw instants; overlaps is
        // what a stored pair uses. They must not drift apart.
        val other = sprint(startDate = start + 7.days, endDate = start + 21.days)
        assertEquals(other.overlaps(sprint()), windowsOverlap(start, end, other))
        assertTrue(windowsOverlap(start, end, other))
    }

    @Test
    fun theBlockerIsTheUnfinishedSprintThatOverlaps() {
        val finished = sprint(name = "Done", isFinished = true, finishedAt = now)
        val live = sprint(name = "Live")
        assertEquals(live, sprintBlockingOverlap(start, end, listOf(finished, live)))
    }

    @Test
    fun aFinishedSprintIsNeverABlocker() {
        val finished = sprint(name = "Done", isFinished = true, finishedAt = now)
        assertNull(sprintBlockingOverlap(start, end, listOf(finished)))
    }

    @Test
    fun aWindowClearOfEverySprintHasNoBlocker() {
        val live = sprint()
        // Starts the day after the live sprint ends — the staggered case the rule allows.
        assertNull(sprintBlockingOverlap(end + 1.days, end + 14.days, listOf(live)))
    }
}

class WorkItemEstimationTest {

    private val projectID = randomProjectID()
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private fun item(storyPoints: Int? = null, sprintID: SprintID? = null) = WorkItem(
        projectID = projectID,
        key = WorkItemKey("TT-1"),
        title = "TT-1",
        storyPoints = storyPoints,
        sprintID = sprintID,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun anUnestimatedItemKeepsItsNull() {
        // The whole reason the field is nullable. Flooring this to 0 would make "nobody
        // estimated this" and "this is worth nothing" the same value, and a sprint would
        // then be able to claim it had measured its whole scope.
        assertNull(item(storyPoints = null).storyPoints)
    }

    @Test
    fun zeroIsAnEstimateAndIsNotTheSameAsUnestimated() {
        val subject = item(storyPoints = 0)
        assertEquals(0, subject.storyPoints)
        assertNotEquals(null, subject.storyPoints)
    }

    @Test
    fun aNegativeEstimateArrivingFromAServerIsFlooredAtZero() {
        assertEquals(0, item(storyPoints = -3).storyPoints)
    }

    @Test
    fun anItemWithNoSprintIsInTheBacklogRatherThanBroken() {
        // The state almost every item starts in, and a permanent one: the backlog is a
        // real place, not an absence.
        assertNull(item(sprintID = null).sprintID)
        assertNotNull(item(sprintID = randomSprintID()).sprintID)
    }

    @Test
    fun aNewItemLandsInTheBacklogWithoutBeingToldTo() {
        // The *default*, not the passed value. An earlier version of this class passed
        // `sprintID = null` explicitly and so never exercised the default at all, which
        // meant a default that handed every new item a random sprint passed the suite -
        // and would have silently scattered new work into sprints nobody created. The
        // mutation that caught this changed the default and left everything green.
        val fresh = WorkItem(
            projectID = projectID,
            key = WorkItemKey("TT-1"),
            title = "TT-1",
            createdAt = now,
            updatedAt = now,
        )
        assertNull(fresh.sprintID)
    }

    @Test
    fun puttingAnItemInASprintDoesNotMoveItOnTheBoard() {
        // Rank is the item's position in its status column. A sprint is not a column, so
        // letting the two interact is how a board starts reordering itself.
        val subject = WorkItem(
            projectID = projectID,
            key = WorkItemKey("TT-9"),
            title = "TT-9",
            status = WorkItemStatus.InProgress,
            rank = 7,
            createdAt = now,
            updatedAt = now,
        )
        subject.sprintID = randomSprintID()
        assertEquals(7, subject.rank)
        assertEquals(WorkItemStatus.InProgress, subject.status)
    }
}

class SprintSummaryTest {

    private val projectID = randomProjectID()
    private val now = Instant.fromEpochSeconds(1_700_000_000)
    private val sprint = Sprint(
        projectID = projectID,
        name = "Sprint 1",
        goalStoryPoints = 8,
        startDate = now,
        endDate = now + 14.days,
        createdAt = now,
        updatedAt = now,
    )

    private fun item(
        key: String,
        status: WorkItemStatus,
        storyPoints: Int?,
        sprintID: SprintID? = sprint.id,
        rank: Int = 0,
        priority: Priority = Priority.Medium,
    ) = WorkItem(
        projectID = projectID,
        key = WorkItemKey(key),
        title = key,
        status = status,
        storyPoints = storyPoints,
        sprintID = sprintID,
        priority = priority,
        rank = rank,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun theDoneAndIncompleteSplitAgreesWithTheArchivePredicate() {
        // Both use status.isTerminal, so a task cannot be filed under one and not the
        // other. An archive that disagrees with a sprint summary is two screens answering
        // "did this ship?" differently.
        val items = listOf(
            item("TT-1", WorkItemStatus.Done, 3),
            item("TT-2", WorkItemStatus.InProgress, 5),
            item("TT-3", WorkItemStatus.Closed, 0),
        )
        val summary = sprintSummary(sprint, items)

        assertEquals(
            WorkItemGrouping.resolved(items).map { it.key.rawValue }.sorted(),
            summary.done.map { it.key.rawValue }.sorted(),
        )
        assertEquals(listOf("TT-2"), summary.incomplete.map { it.key.rawValue })
    }

    @Test
    fun onlyDeliveredItemsCountTowardsTheAchievedPoints() {
        val items = listOf(
            item("TT-1", WorkItemStatus.Done, 3),
            item("TT-2", WorkItemStatus.Closed, 5),
            item("TT-3", WorkItemStatus.InProgress, 2),
        )
        val summary = sprintSummary(sprint, items)

        assertEquals(10, summary.committedStoryPoints)
        // The closed item is reported as done — it is no longer outstanding — but it
        // contributes nothing, because nothing was delivered. Counting it would let a
        // sprint claim points for work that was decided against.
        assertEquals(3, summary.achievedStoryPoints)
    }

    @Test
    fun anUnestimatedTaskIsReportedAndContributesNoPoints() {
        val items = listOf(
            item("TT-1", WorkItemStatus.Done, null),
            item("TT-2", WorkItemStatus.Todo, 4),
        )
        val summary = sprintSummary(sprint, items)

        assertEquals(listOf("TT-1"), summary.done.map { it.key.rawValue })
        assertEquals(4, summary.committedStoryPoints)
        assertEquals(0, summary.achievedStoryPoints)
    }

    @Test
    fun anItemInAnotherSprintIsNotCounted() {
        // The builder filters on the sprint id, so a caller holding the whole project's
        // work list cannot produce a summary that counted something else.
        val items = listOf(
            item("TT-1", WorkItemStatus.Done, 3),
            item("TT-2", WorkItemStatus.Done, 99, sprintID = randomSprintID()),
            item("TT-3", WorkItemStatus.Done, 99, sprintID = null),
        )
        val summary = sprintSummary(sprint, items)

        assertEquals(listOf("TT-1"), summary.done.map { it.key.rawValue })
        assertEquals(3, summary.achievedStoryPoints)
    }

    @Test
    fun anEmptySprintIsEmptyAndStillReportsTheGoalItMissed() {
        // Not "no verdict": this sprint *has* a goal of 8, so achieving none of it is a
        // genuine miss and the summary has to say so. The three-way answer is for a
        // sprint nobody set a target for, which is a different thing.
        val summary = sprintSummary(sprint, emptyList())
        assertTrue(summary.isEmpty)
        assertEquals(0, summary.committedStoryPoints)
        assertEquals(0, summary.achievedStoryPoints)
        assertEquals(false, summary.didHitGoal)
    }

    @Test
    fun bothListsComeBackInBoardOrderRatherThanTheOrderTheyWereHandedOver() {
        val first = item("TT-1", WorkItemStatus.Todo, 1, rank = 0, priority = Priority.Lowest)
        val second = item("TT-2", WorkItemStatus.Todo, 1, rank = 1, priority = Priority.Critical)

        // Handed over backwards on purpose. A builder that kept the input order answers
        // TT-2, TT-1 here, which is the defect: the same two tasks in two stores would
        // then be listed in two different orders.
        assertEquals(
            listOf("TT-1", "TT-2"),
            sprintSummary(sprint, listOf(second, first)).incomplete.map { it.key.rawValue },
        )
        assertEquals(
            listOf("TT-1", "TT-2"),
            sprintSummary(sprint, listOf(first, second)).incomplete.map { it.key.rawValue },
        )
    }
}

class SprintGoalTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    /**
     * One sprint, held as a property rather than built per call, because
     * [sprintSummary] filters on the id: an item built against a *different* sprint is
     * correctly excluded, which is the behaviour [SprintSummaryTest] asserts and which
     * would otherwise make this class assert nothing.
     */
    private fun sprint(goalStoryPoints: Int) = Sprint(
        projectID = randomProjectID(),
        name = "Sprint 1",
        goalStoryPoints = goalStoryPoints,
        startDate = now,
        endDate = now + 14.days,
        createdAt = now,
        updatedAt = now,
    )

    private fun item(
        status: WorkItemStatus,
        storyPoints: Int?,
        sprintID: SprintID,
    ) = WorkItem(
        projectID = randomProjectID(),
        key = WorkItemKey("TT-1"),
        title = "TT-1",
        status = status,
        storyPoints = storyPoints,
        sprintID = sprintID,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun landingExactlyOnTheGoalIsAHit() {
        // The case a `>` gets wrong, and the most likely thing to be re-derived by hand
        // somewhere else in the app.
        assertEquals(true, SprintGoal.didHitGoal(achieved = 8, goal = 8))
    }

    @Test
    fun underTheGoalMissesAndOverItHits() {
        assertEquals(false, SprintGoal.didHitGoal(achieved = 7, goal = 8))
        assertEquals(true, SprintGoal.didHitGoal(achieved = 9, goal = 8))
    }

    @Test
    fun aSprintWithNoGoalHasNoVerdictRatherThanAVictory() {
        // `false` here would read as "we missed", which is as much an invention as
        // `true`. The third state is the honest one and it is why the answer is nullable.
        assertNull(SprintGoal.didHitGoal(achieved = 0, goal = 0))
        assertNull(SprintGoal.didHitGoal(achieved = 12, goal = 0))
    }

    @Test
    fun theSummaryCarriesTheVerdictRatherThanLeavingItToTheView() {
        val subject = sprint(goalStoryPoints = 8)
        val items = listOf(item(WorkItemStatus.Done, 8, subject.id))

        assertEquals(true, sprintSummary(subject, items).didHitGoal)
        assertEquals(
            false,
            sprintSummary(subject, items.map { it.copy(status = WorkItemStatus.Todo) }).didHitGoal,
        )
    }
}

class MentionRulesTest {

    @Test
    fun `extractMentions finds username mentions`() {
        val mentions = MentionRules.extractMentions("Hello @alice, how are you?")
        assertEquals(1, mentions.size)
        val mention = mentions.first() as MentionRules.Mention.ByUsername
        assertEquals("@alice", mention.raw)
        assertEquals("alice", mention.username)
        assertEquals(6, mention.start)
        assertEquals(12, mention.end)
    }

    @Test
    fun `extractMentions finds UserID mentions`() {
        val mentions = MentionRules.extractMentions("Hey @<12345678-1234-1234-1234-123456789012>, check this")
        assertEquals(1, mentions.size)
        val mention = mentions.first() as MentionRules.Mention.ByUserID
        assertEquals("@<12345678-1234-1234-1234-123456789012>", mention.raw)
        assertEquals(UserID("12345678-1234-1234-1234-123456789012"), mention.userID)
        assertEquals(4, mention.start)
        assertEquals(43, mention.end)
    }

    @Test
    fun `extractMentions finds multiple mentions of both types`() {
        val mentions = MentionRules.extractMentions("@alice and @<87654321-4321-4321-4321-210987654321> please review")
        assertEquals(2, mentions.size)
        val first = mentions[0] as MentionRules.Mention.ByUsername
        assertEquals("alice", first.username)
        val second = mentions[1] as MentionRules.Mention.ByUserID
        assertEquals(UserID("87654321-4321-4321-4321-210987654321"), second.userID)
    }

    @Test
    fun `extractMentions returns empty for text without mentions`() {
        assertEquals(emptyList(), MentionRules.extractMentions("No mentions here"))
    }

    @Test
    fun `extractMentions handles trailing punctuation`() {
        val mentions = MentionRules.extractMentions("Thanks @alice! See @<11111111-2222-3333-4444-555555555555>.")
        assertEquals(2, mentions.size)
        assertEquals("alice", (mentions[0] as MentionRules.Mention.ByUsername).username)
        assertEquals(UserID("11111111-2222-3333-4444-555555555555"), (mentions[1] as MentionRules.Mention.ByUserID).userID)
    }

    @Test
    fun `extractMentions handles mid-word at symbol`() {
        // email-like strings should not be treated as mentions
        val mentions = MentionRules.extractMentions("Contact alice@example.com for help")
        assertEquals(emptyList(), mentions)
    }

    @Test
    fun `extractMentions handles username with dots and hyphens`() {
        val mentions = MentionRules.extractMentions("Hey @alice.smith and @bob-jones")
        assertEquals(2, mentions.size)
        assertEquals("alice.smith", (mentions[0] as MentionRules.Mention.ByUsername).username)
        assertEquals("bob-jones", (mentions[1] as MentionRules.Mention.ByUsername).username)
    }

    @Test
    fun `extractMentions handles mention at start of text`() {
        val mentions = MentionRules.extractMentions("@alice this is a test")
        assertEquals(1, mentions.size)
        assertEquals(0, mentions[0].start)
    }

    @Test
    fun `extractMentions handles mention at end of text`() {
        val mentions = MentionRules.extractMentions("Hello @alice")
        assertEquals(1, mentions.size)
        assertEquals(6, mentions[0].start)
        assertEquals(12, mentions[0].end)
    }

    @Test
    fun `extractUserIDMentions filters only UserID mentions`() {
        val mentions = MentionRules.extractUserIDMentions("@alice and @<12345678-1234-1234-1234-123456789012> and @bob")
        assertEquals(1, mentions.size)
        assertEquals(UserID("12345678-1234-1234-1234-123456789012"), mentions[0].userID)
    }

    @Test
    fun `extractUsernameMentions filters only username mentions`() {
        val mentions = MentionRules.extractUsernameMentions("@alice and @<12345678-1234-1234-1234-123456789012> and @bob")
        assertEquals(2, mentions.size)
        assertEquals("alice", mentions[0].username)
        assertEquals("bob", mentions[1].username)
    }
}

/**
 * The spelling of [Priority] on the wire, which is not the enum's entry name.
 */
class PriorityWireTest {

    @Test
    fun everyPriorityTravelsAsItsLowercaseWireValue() {
        // The server has enforced lowercase since `0001_initial`:
        // `work_items_priority_check` is `CHECK (priority IN ('lowest','low','medium','high',
        // 'critical'))`, and nothing in the Go layer normalizes case. kotlinx serializes an
        // enum by its *entry name* unless the entry carries `@SerialName`, so dropping one
        // annotation puts `"Medium"` on the wire; Postgres refuses it as SQLSTATE 23514,
        // which the client reads as a *retryable* failure. It then spends the whole retry
        // budget before dead-lettering — and because the push loop stops at the first
        // retryable failure, every change queued behind the item is never sent at all.
        for (priority in Priority.entries) {
            val encoded = JSONCoding.json.encodeToString(Priority.serializer(), priority)
            assertEquals(
                "\"${priority.wireValue}\"",
                encoded,
                "Priority.$priority must travel as its wireValue, not its entry name — got $encoded",
            )
        }
    }
}
