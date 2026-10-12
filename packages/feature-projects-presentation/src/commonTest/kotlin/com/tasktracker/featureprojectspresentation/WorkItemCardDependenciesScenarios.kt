package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomWorkItemID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a card's dependency row is allowed to show, as scenarios, and the rule that resolves
 * its ids, as plain tests.
 *
 * ## The rules, and why each one is a rule
 *
 * **The row is absent for a task with no blockers.** Not an empty pill. This is the assertion
 * that matters most here: every other card row is conditional for the same reason, and a card
 * that grew a blank line per feature would make the board measurably taller while saying
 * nothing.
 *
 * **Keys, never ids.** A raw `0195…` beside `TT-142` is noise, not information, and the card
 * already refuses to print an unnameable assignee for the same reason.
 *
 * **Two named, the rest counted.** A card is scanned. Naming five blockers would make a
 * five-blocker card taller than the editor row it summarises, on the surface that has to stay
 * a grid.
 *
 * The rule that *decides* what the row says is [dependencyKeysFor], which is pure and so is
 * tested below rather than through a composition — including the case that actually makes it
 * worth having, a blocker that is not on the board being looked at.
 */
@OptIn(ExperimentalTestApi::class)
object WorkItemCardDependenciesScenarios {

    /** One blocker: the row names its key. */
    val aCardWithABlockerNamesIt: suspend ComposeUiTest.() -> Unit = {
        val blocker = previewItem("TT-1")
        val item = previewItem("TT-2", blockedByIDs = listOf(blocker.id))

        cardShowingDependencies(item, listOf(blocker.key))

        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        // `onNested`, because the card's root is clickable and a clickable merges its whole
        // subtree — see TestQueries' first habit, which is this exact failure.
        onNested(WorkItemCardDependsTag).assertIsDisplayed()
        assertText(WorkItemCardDependsKeysTag, "TT-1", useUnmerged = true)
    }

    /** Two blockers: both are named, and nothing is counted. */
    val twoBlockersAreBothNamed: suspend ComposeUiTest.() -> Unit = {
        val first = previewItem("TT-1")
        val second = previewItem("TT-2")
        val item = previewItem("TT-3", blockedByIDs = listOf(first.id, second.id))

        cardShowingDependencies(item, listOf(first.key, second.key))

        assertText(WorkItemCardDependsKeysTag, "TT-1, TT-2", useUnmerged = true)
        assertTagCount(WorkItemCardDependsMoreTag, expected = 0, useUnmerged = true)
    }

    /**
     * Four blockers: two are named and the other two are counted.
     *
     * Four rather than three, so the count is a genuine overflow rather than the boundary
     * case — a row that counted its third blocker would still pass a test written against three.
     */
    val beyondTwoNamesTwoAndCountsTheRest: suspend ComposeUiTest.() -> Unit = {
        val blockers = listOf("TT-1", "TT-2", "TT-3", "TT-4").map { previewItem(it) }
        val item = previewItem("TT-5", blockedByIDs = blockers.map { it.id })

        cardShowingDependencies(item, blockers.map { it.key })

        assertText(WorkItemCardDependsKeysTag, "TT-1, TT-2", useUnmerged = true)
        assertText(WorkItemCardDependsMoreTag, "+2", useUnmerged = true)
        // Named rows, not a text search: the overflow count is what proves the third and
        // fourth are counted rather than printed, because a row that printed them all would
        // still contain "TT-1, TT-2" as a prefix of its own list.
        assertText(WorkItemCardDependsKeysTag, "TT-1, TT-2", useUnmerged = true)
    }

    /**
     * No blockers: no row at all.
     *
     * The negative assertion a feature like this most often fails. Everything else here proves
     * the row can be drawn; this proves it is not drawn when there is nothing to say, which is
     * the half that decides whether every card on the board grew.
     *
     * Counted in the **unmerged** tree deliberately: the row lives inside a clickable card, so
     * a plain `onNodeWithTag(...).assertDoesNotExist()` would find nothing whether or not the
     * row was drawn, and this scenario would pass without testing anything. The positive
     * scenarios in this file failed for exactly that reason when first written.
     */
    val aCardWithNoBlockersDrawsNoDependencyRow: suspend ComposeUiTest.() -> Unit = {
        val item = previewItem("TT-9")

        cardShowingDependencies(item, emptyList())

        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        assertTagCount(WorkItemCardDependsTag, expected = 0, useUnmerged = true)
        assertTagCount(WorkItemCardDependsKeysTag, expected = 0, useUnmerged = true)
    }

    /**
     * A blocker the board cannot currently see still resolves to its key.
     *
     * The case the whole [dependencyKeysFor] contract exists for, asserted end to end so it
     * cannot be satisfied by a row that is merely correct when everything is visible.
     */
    val aBlockerOutsideTheVisibleSetStillNamesItsKey: suspend ComposeUiTest.() -> Unit = {
        // The blocker is a real project item, but it is *not* on this board: a different
        // sprint, or somebody else's task. `items` is the project's whole list.
        val offBoard = previewItem("TT-1")
        val item = previewItem("TT-2", blockedByIDs = listOf(offBoard.id))
        val resolved = dependencyKeysFor(items = listOf(offBoard, item), item = item)

        cardShowingDependencies(item, resolved)

        assertText(WorkItemCardDependsKeysTag, "TT-1", useUnmerged = true)
    }
}

/**
 * The rule that turns a card's `blockedByIDs` into the keys it draws.
 *
 * Plain [Test] methods rather than scenarios, because they assert on Kotlin values and not on
 * a composition. A `commonTest` class runs on every target, so these cost one JVM run and two
 * near-free native ones.
 */
class DependencyKeysForTest {

    @Test
    fun aBlockerResolvesToItsKey() {
        val blocker = previewItem("TT-1")
        val item = previewItem("TT-2", blockedByIDs = listOf(blocker.id))

        assertEquals(listOf(WorkItemKey("TT-1")), dependencyKeysFor(listOf(blocker, item), item))
    }

    @Test
    fun aTaskWithNoBlockersResolvesToNothing() {
        val item = previewItem("TT-1")

        assertEquals(emptyList(), dependencyKeysFor(listOf(item), item))
    }

    /**
     * A blocker deleted on another device leaves an id nothing resolves, and it is dropped.
     *
     * The card does not print the id: a raw `0195…` in a row of `TT-142` is noise, and the
     * count beside the named keys would otherwise claim a blocker the user cannot open.
     */
    @Test
    fun anIdWithNoMatchingItemIsDroppedRatherThanPrinted() {
        val gone = previewItem("TT-1")
        val present = previewItem("TT-2")
        val item = previewItem("TT-3", blockedByIDs = listOf(gone.id, present.id))

        // `gone` is deliberately withheld from the list the join reads.
        assertEquals(
            listOf(WorkItemKey("TT-2")),
            dependencyKeysFor(listOf(present, item), item),
            "a blocker nothing resolves must not be printed as a raw id",
        )
    }

    /**
     * The reason this function takes the project's items rather than the visible cards.
     *
     * A blocker is very often *not* on the board being looked at — a different sprint, or a
     * task assigned to somebody else while My Tasks is on. Joining against what is visible
     * would drop it, and the card would then claim this task waits on one thing when it waits
     * on two. The assertion pins the two answers apart: against the whole list the blocker
     * resolves, and against the visible subset it does not — so a regression that swaps the
     * list for the projection is a *failing* test rather than a quieter card.
     */
    @Test
    fun aBlockerOutsideTheVisibleSetStillResolves() {
        val offBoard = previewItem("TT-1")
        val visible = previewItem("TT-2")
        val item = previewItem("TT-3", blockedByIDs = listOf(offBoard.id, visible.id))
        val wholeProject = listOf(offBoard, visible, item)

        assertEquals(
            listOf(WorkItemKey("TT-1"), WorkItemKey("TT-2")),
            dependencyKeysFor(wholeProject, item),
            "joined against the project, both blockers resolve",
        )
        assertEquals(
            listOf(WorkItemKey("TT-2")),
            dependencyKeysFor(listOf(visible, item), item),
            "joined against only what is on screen, the off-board blocker is lost - which is " +
                "exactly the wrong answer the model avoids by passing rawItems",
        )
    }

    /**
     * Order is the task's own, not the board's.
     *
     * A blocker list is short and its order is the user's — they added them in an order.
     * Re-sorting by the board's key would make the row reshuffle whenever an unrelated card
     * moved, which reads as the dependency set changing when nothing about it did.
     */
    @Test
    fun blockersKeepTheOrderTheyWereListedIn() {
        val third = previewItem("TT-3")
        val first = previewItem("TT-1")
        val second = previewItem("TT-2")
        val item = previewItem("TT-4", blockedByIDs = listOf(third.id, first.id, second.id))

        assertEquals(
            listOf(WorkItemKey("TT-3"), WorkItemKey("TT-1"), WorkItemKey("TT-2")),
            dependencyKeysFor(listOf(first, second, third, item), item),
            "the list is sorted by TT-1 here, so this also proves the result is not one",
        )
    }

    /** An item absent from the list it is joined against resolves to nothing, not to itself. */
    @Test
    fun anItemMissingFromTheListResolvesToNothing() {
        val item = previewItem("TT-1")

        assertEquals(emptyList(), dependencyKeysFor(emptyList(), item))
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.cardShowingDependencies(
    item: WorkItem,
    blockerKeys: List<WorkItemKey>,
) {
    setContent {
        Themed {
            WorkItemCard(
                item = item,
                onClick = {},
                blockedByKeys = blockerKeys,
            )
        }
    }
}

/**
 * A work item for a dependency test.
 *
 * Ids are random and held by the caller, because these tests assert on *which* ids resolve
 * rather than on any particular identifier — a fixed id would only invite a second test to
 * depend on the first one's.
 */
private fun previewItem(key: String, blockedByIDs: List<WorkItemID> = emptyList()): WorkItem =
    WorkItem(
        id = randomWorkItemID(),
        projectID = randomProjectID(),
        key = WorkItemKey(key),
        title = "Task $key",
        status = WorkItemStatus.Todo,
        blockedByIDs = blockedByIDs,
        createdAt = ViewPreviewData.instant,
        updatedAt = ViewPreviewData.instant,
    )