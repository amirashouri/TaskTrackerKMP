package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BoardProjection
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Filing a task away, from **both** places it can be done, and the two agreeing.
 *
 * ## Why this suite exists at all
 *
 * Filing away is a *status move* to `Closed`, not a deletion, and it is offered from two
 * surfaces: the backlog row's button, and the board card's action slot. Two independent
 * paths to the same fact is exactly how they drift.
 *
 * If the board's card filed away and the backlog's row did not, the symptom would be a task
 * that reads as filed away on one tab and still untriaged on the other, with neither screen
 * reporting an error - both succeeded, they just meant different things. A test that covered
 * only one surface would pass for as long as the two happened to agree.
 *
 * ## Why it is two files' worth of test in one
 *
 * The convergence has two halves and they are not both UI concerns.
 *
 * [ArchiveRoundTripScenarios] is the UI half: each surface reports the *right task*. A
 * backlog row whose button filed away whatever task happened to be first would pass every
 * model test and be wrong in the hand.
 *
 * [ArchiveRoundTripTest] is the half that matters: both paths reach the *same stored state*.
 * A stub cannot show convergence, because it has no stored state - so this drives the real
 * models over the real repository and compares what they leave behind.
 */
@OptIn(ExperimentalTestApi::class)
object ArchiveRoundTripScenarios {

    /** The backlog row's "File Away" reports *that row's* task, not some other one. */
    val theBacklogRowFilesItsOwnTaskAway: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.backlogProjection().untriaged.first()
        var filedAway: WorkItemID? = null

        setContent {
            Themed {
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = {},
                    onFileAway = { filedAway = item.id },
                )
            }
        }
        onNodeWithTag(BacklogFileAwayTag(item)).performClick()

        assertEquals(item.id, filedAway)
    }

    /**
     * The board card's action reports *that card's* task.
     *
     * Driven through [ProjectBoardScreen] rather than by hand-wiring a card's `trailing`
     * slot, because the first version of this did exactly that and passed - while the real
     * screen could have had no action on its cards at all and this would not have noticed.
     * A test that builds its own version of the thing it is testing proves only that the
     * version it built works.
     */
    val theBoardCardFilesItsOwnTaskAway: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val item = projection.items.first { it.status.appearsOnBoard }
        var filedAway: WorkItemID? = null

        boardShowing(projection, movingItemID = null, onFileAway = { filedAway = it })

        onNested(BoardFileAwayTag(item)).performClick()

        assertEquals(item.id, filedAway, "the card's own action reports its own task")
    }

    /**
     * A busy card's action does nothing.
     *
     * The two surfaces offer the same verb, so they have to offer the same *inertness* too -
     * a card mid-move that still accepts a second press is a race, and the backlog's rows
     * already refuse one. And it is that card alone: the board is not locked while one card
     * moves, because a silent no-op on an unrelated card is the more confusing failure.
     */
    val aBusyCardDoesNotFileAnythingAway: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val busy = projection.items.first { it.status.appearsOnBoard }
        val idle = projection.items.filter { it.status.appearsOnBoard }[1]
        var filedAway: WorkItemID? = null

        boardShowing(projection, movingItemID = busy.id, onFileAway = { filedAway = it })

        onNested(BoardFileAwayTag(busy)).performClick()
        assertNull(filedAway, "the card that is moving does not accept a second press")

        onNested(BoardFileAwayTag(idle)).performClick()
        assertEquals(idle.id, filedAway, "and the rest of the board is still live")
    }

    /**
     * A delivered task offers no way to file away.
     *
     * `Done` is terminal and appears on the board, so it is the one card where the guard is
     * load-bearing: filing an already-closed task away is a no-op that would still report
     * success, and the button would be an invitation to press it.
     */
    val aDeliveredCardOffersNoFileAway: suspend ComposeUiTest.() -> Unit = {
        val projection = ViewPreviewData.boardProjection()
        val delivered = projection.items.first { it.status == WorkItemStatus.Done }

        boardShowing(projection, movingItemID = null, onFileAway = {})

        assertEquals(0, countOfTag(BoardFileAwayTag(delivered), useUnmerged = true))
        assertTrue(
            delivered in projection.columns.flatMap { it.items },
            "and a delivered task is still on the board, so the guard is doing real work",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardShowing(
    projection: BoardProjection,
    movingItemID: WorkItemID?,
    onFileAway: (WorkItemID) -> Unit,
) {
    setContent {
        Themed {
            ProjectBoardScreen(
                projection = projection,
                isLoading = false,
                errorMessage = null,
                movingItemID = movingItemID,
                onSelect = {},
                onFileAway = onFileAway,
                onMove = { _, _ -> },
                onDismissError = {},
            )
        }
    }
}

/**
 * Both filing paths, driven for real, landing in the same place.
 *
 * Not UI tests, because the question here is not what is drawn - it is what is *stored*. Two
 * clicks that look identical and leave different state is precisely the bug, and it is
 * invisible to a semantics tree.
 */
class ArchiveRoundTripTest {

    @Test
    fun filingFromTheBacklogAndFromTheBoardReachTheSameState() = runTest {
        val backlogRepository = ViewPreviewData.repository()
        val boardRepository = ViewPreviewData.repository()
        val backlog = ProjectBacklogModel(
            projectID = ViewPreviewData.seededProject.id,
            repository = backlogRepository,
            dateProvider = TestDateProvider(ViewPreviewData.instant),
        )
        val board = ProjectBoardModel(
            projectID = ViewPreviewData.seededProject.id,
            repository = boardRepository,
            dateProvider = TestDateProvider(ViewPreviewData.instant),
        )
        // Observed, not refreshed: a screen subscribes and lets the repository's change
        // feed it, and a write only reaches the projection by that route. `refresh()` alone
        // would load once and then go stale on the write, which is the bug this test exists
        // to be able to see - so the tests use the real arrangement.
        backlog.observe(eagerScope())
        board.observe(eagerScope())

        val backlogItem = backlog.projection.untriaged.first()
        val boardItem = board.projection.items.first { it.status.appearsOnBoard }

        assertTrue(backlog.fileAway(backlogItem.id), "the backlog's own verb files away")
        assertTrue(board.move(itemID = boardItem.id, to = WorkItemStatus.Closed), "the board moves to Closed")

        val viaBacklog = backlogRepository.workItem(backlogItem.id)?.status
        val viaBoard = boardRepository.workItem(boardItem.id)?.status

        assertEquals(WorkItemStatus.Closed, viaBacklog)
        assertEquals(viaBacklog, viaBoard, "the two filing surfaces must reach the same state")
    }

    @Test
    fun aFiledAwayTaskLeavesTheBoardAndIsInTheArchive() = runTest {
        val repository = ViewPreviewData.repository()
        val board = ProjectBoardModel(
            projectID = ViewPreviewData.seededProject.id,
            repository = repository,
            dateProvider = TestDateProvider(ViewPreviewData.instant),
        )
        board.observe(eagerScope())
        val item = board.projection.items.first { it.status.appearsOnBoard }

        board.move(itemID = item.id, to = WorkItemStatus.Closed)

        val filed = repository.workItem(item.id)
        assertTrue(filed?.status?.isTerminal == true, "a filed-away task is terminal")
        assertFalse(
            filed?.status?.appearsOnBoard == true,
            "and the board has no Closed lane, so it must not appear there",
        )
        // `BoardProjection.items` is every item in the project; `columns` is the board. A
        // `Closed` item is still *in the project* and simply not in any lane, so asserting
        // on `items` here would be asserting that filing away deletes the record - which is
        // the opposite of what filing away means.
        assertTrue(
            board.projection.columns.flatMap { it.items }.none { it.id == item.id },
            "the board's lanes drop it, even though the project still holds it",
        )
        assertTrue(
            board.projection.items.any { it.id == item.id },
            "and the record itself survives, which is the whole point of filing away",
        )
    }

    /**
     * The backlog empties as items are filed away, one press at a time.
     *
     * Asserted on the projection rather than on the store, because the projection is what
     * the screen draws - and a store that changed while the projection did not would leave
     * the user pressing a button on a row that is still on screen.
     */
    @Test
    fun theBacklogProjectionEmptiesAsItemsAreFiledAway() = runTest {
        val repository = ViewPreviewData.repository()
        val backlog = ProjectBacklogModel(
            projectID = ViewPreviewData.seededProject.id,
            repository = repository,
            dateProvider = TestDateProvider(ViewPreviewData.instant),
        )
        backlog.observe(eagerScope())
        val startingCount = backlog.projection.untriaged.size
        assertTrue(startingCount > 0, "the fixture has something to triage")

        val target: WorkItem = backlog.projection.untriaged.first()
        backlog.fileAway(target.id)

        assertEquals(startingCount - 1, backlog.projection.untriaged.size)
        assertTrue(backlog.projection.untriaged.none { it.id == target.id })
    }
}
