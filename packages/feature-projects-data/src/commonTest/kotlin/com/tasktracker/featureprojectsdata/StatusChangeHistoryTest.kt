package com.tasktracker.featureprojectsdata

import com.tasktracker.core.randomUserID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The status history, which is the only append-only thing in the store.
 *
 * Three properties are load-bearing and each of them was wrong in the original:
 *
 * 1. **Newest first.** A chronological list with the reversal left to the caller is a history
 *    that reads backwards in one screen and forwards in another.
 * 2. **Later-recorded wins a tie.** A card created and triaged inside one clock tick produces
 *    two rows with the same `changedAt`. A sort on the timestamp alone hands them back in
 *    insertion order — the *oldest* order — so the order has to be made total rather than
 *    inherited from the stability of a sort. The original's history had no such column, which
 *    is why `StatusChangeRecord.sequence` exists.
 * 3. **Only a real transition appends.** A reorder within a column, and a title edit, are not
 *    status changes, and a history claiming they were is worse than no history at all.
 */
class StatusChangeHistoryTest {

    @Test
    fun creatingAWorkItemRecordsOneRowSayingItWasCreatedHere() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        val history = harness.repository.statusChanges(item.id)
        assertEquals(1, history.size)
        assertNull(history[0].from)
        assertEquals(WorkItemStatus.Todo, history[0].to)
        assertTrue(history[0].isCreation)
    }

    @Test
    fun aCreationIsAuthoredByWhoeverWasSignedIn() = runTest {
        val harness = Harness()
        val userID = harness.session.signIn()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        assertEquals(userID, harness.repository.statusChanges(item.id)[0].changedBy?.stringValue)
    }

    @Test
    fun aCreationWithNoSessionHasNoAuthorRatherThanNoHistory() = runTest {
        // A real case, not a future one: a move made on a cold launch before the session was
        // restored, or in the window around a sign-out. The alternative is either dropping the
        // change or inventing an author, and an invented author is worse than a blank.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)

        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        val history = harness.repository.statusChanges(item.id)
        assertEquals(1, history.size)
        assertNull(history[0].changedBy)
    }

    @Test
    fun aMoveRecordsWhereItCameFromAsWellAsWhereItWent() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )

        harness.repository.moveWorkItem(item.id, WorkItemStatus.InProgress, rank = null, now = Moments.later)

        val move = harness.repository.statusChanges(item.id)[0]
        assertEquals(WorkItemStatus.Backlog, move.from)
        assertEquals(WorkItemStatus.InProgress, move.to)
        assertTrue(!move.isCreation)
        assertTrue(move.altersStatus)
    }

    @Test
    fun historyComesBackNewestFirst() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )

        harness.repository.moveWorkItem(item.id, WorkItemStatus.Todo, rank = null, now = Moments.start)
        harness.repository.moveWorkItem(item.id, WorkItemStatus.InProgress, rank = null, now = Moments.later)
        harness.repository.moveWorkItem(item.id, WorkItemStatus.InReview, rank = null, now = Moments.muchLater)

        assertEquals(
            listOf(
                WorkItemStatus.InReview,
                WorkItemStatus.InProgress,
                WorkItemStatus.Todo,
                WorkItemStatus.Backlog,
            ),
            harness.repository.statusChanges(item.id).map { it.to },
        )
    }

    @Test
    fun twoChangesInTheSameInstantAreOrderedByWhenTheyWereRecorded() = runTest {
        // The case the sequence column exists for. A card created and triaged inside one clock
        // tick gives two rows with identical `changedAt`, and a sort on the timestamp alone
        // returns them in insertion order — so the *creation* would be shown above the move
        // that followed it, which is a history reading backwards.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )

        harness.repository.moveWorkItem(item.id, WorkItemStatus.Todo, rank = null, now = Moments.start)

        val history = harness.repository.statusChanges(item.id)
        assertEquals(2, history.size)
        assertTrue(history.all { it.changedAt == Moments.start })
        assertEquals(WorkItemStatus.Todo, history[0].to)
        assertEquals(WorkItemStatus.Backlog, history[1].to)
    }

    @Test
    fun theStoredSequenceIsStrictlyIncreasingSoTheOrderIsTotal() = runTest {
        // Asserted on the storage rather than through the repository's own reading of it: the
        // repository could be sorting in Kotlin and hiding a column that does not increase.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )
        harness.repository.moveWorkItem(item.id, WorkItemStatus.Todo, rank = null, now = Moments.start)
        harness.repository.moveWorkItem(item.id, WorkItemStatus.InProgress, rank = null, now = Moments.start)

        val stored = harness.database.statusChangeDao().all().map { it.sequence }

        assertEquals(3, stored.size)
        assertEquals(stored.sorted(), stored)
        assertEquals(stored.distinct(), stored)
    }

    @Test
    fun twoCardsInOneProjectKeepSeparateHistories() = runTest {
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val first = harness.repository.createWorkItem(
            project.id, "First", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )
        val second = harness.repository.createWorkItem(
            project.id, "Second", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )
        harness.repository.moveWorkItem(first.id, WorkItemStatus.Done, rank = null, now = Moments.later)

        assertEquals(2, harness.repository.statusChanges(first.id).size)
        assertEquals(1, harness.repository.statusChanges(second.id).size)
    }

    @Test
    fun anItemThatHasNeverMovedHasExactlyOneRowRatherThanNone() = runTest {
        // A creation *is* a history entry. An item with no history at all would be
        // indistinguishable from an item written by a build that did not record them.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Todo, Priority.Medium, null, null, Moments.start,
        )

        assertEquals(1, harness.repository.statusChanges(item.id).size)
    }

    @Test
    fun anUnknownWorkItemHasAnEmptyHistoryRatherThanAFailure() = runTest {
        val harness = Harness()

        assertEquals(
            emptyList(),
            harness.repository.statusChanges(com.tasktracker.core.randomWorkItemID()),
        )
    }

    @Test
    fun aRowThisBuildCannotReadIsSkippedRatherThanEmptyingTheList() = runTest {
        // A history with a gap in it is still a history. One row written by a future build must
        // not cost the reader the other two.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )
        harness.repository.moveWorkItem(item.id, WorkItemStatus.Todo, rank = null, now = Moments.later)
        harness.database.statusChangeDao().insert(
            aChange(item.id, project.id).toRecord().copy(id = "not-a-uuid", changedAt = Moments.muchLater),
        )

        val history = harness.repository.statusChanges(item.id)

        assertEquals(2, history.size)
        assertEquals(WorkItemStatus.Todo, history[0].to)
    }

    @Test
    fun aStoredRowIsAuthoredByTheUserWhoWasSignedInAtTheTimeNotWhoeverIsNow() = runTest {
        val harness = Harness()
        harness.session.signIn()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )
        val first = harness.repository.statusChanges(item.id)[0]

        harness.session.clear()
        harness.repository.moveWorkItem(item.id, WorkItemStatus.Done, rank = null, now = Moments.later)

        // The older row keeps its author and the newer one has none. Rewriting history as the
        // session changes would make the log describe the present rather than the past.
        assertEquals(first.changedBy, harness.repository.statusChanges(item.id)[1].changedBy)
        assertNull(harness.repository.statusChanges(item.id)[0].changedBy)
    }

    @Test
    fun aSessionIdThisBuildCannotParseCostsTheRowItsAuthorRatherThanTheMove() = runTest {
        val harness = Harness()
        harness.session.signIn(userID = "not-a-uuid")
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )

        val history = harness.repository.statusChanges(item.id)

        assertEquals(1, history.size)
        assertNull(history[0].changedBy)
    }

    @Test
    fun aStoredUserIdThisBuildCannotParseLosesTheAuthorAndKeepsTheRow() = runTest {
        // The same failure one layer down, at the column rather than the session: the author is
        // *displayed*, so an unparseable one becomes nothing. Throwing would cost the
        // transition, which is the one thing the row exists to say.
        val harness = Harness()
        val project = harness.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = harness.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Backlog, Priority.Medium, null, null, Moments.start,
        )
        harness.database.statusChangeDao().insert(
            aChange(item.id, project.id).toRecord().copy(changedByUserID = "not-a-uuid"),
        )

        assertEquals(2, harness.repository.statusChanges(item.id).size)
    }

    private fun aChange(
        workItemID: com.tasktracker.core.WorkItemID,
        projectID: com.tasktracker.core.ProjectID,
    ) = com.tasktracker.domain.StatusChange(
        workItemID = workItemID,
        projectID = projectID,
        from = WorkItemStatus.Backlog,
        to = WorkItemStatus.Todo,
        changedAt = Moments.muchLater,
        changedBy = randomUserID(),
    )
}
