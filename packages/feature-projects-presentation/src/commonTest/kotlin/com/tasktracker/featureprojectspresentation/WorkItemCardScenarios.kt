package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a board card is allowed to show, as scenarios.
 *
 * ## The rules, and why each one is a rule
 *
 * **The key, always.** It is the only stable handle a task has. A title can be reworded, and
 * a card without its key cannot be pointed at in a message.
 *
 * **The priority, always, as a badge rather than a word in a list.** Five levels have to be
 * scannable down a column of cards, and a column of prose is not scannable.
 *
 * **The assignee only when there is one.** An empty assignee row is a blank line that pushes
 * the rest of the card down, and "nobody" is not a fact worth the space.
 *
 * **The due date only when there is one**, for the same reason.
 *
 * **Never the status.** The card sits in a column *named* with its status. Printing it again
 * is the duplication that goes stale the moment a move fails halfway.
 *
 * ## What is not in here, and why
 *
 * The strike-through. `TextDecoration` is not published as a semantics property in Compose
 * Multiplatform 1.12, so no UI test can see one - asserting it here would mean asserting
 * nothing. The rule that decides it is [isDelivered], and [WorkItemCardRulesTest] below
 * covers it where it is actually decided. A test that cannot fail is worse than a missing
 * test, because it reads as coverage.
 *
 * Each value is a `suspend ComposeUiTest.() -> Unit` rather than a function taking a test,
 * so a scenario reads as the interaction it performs. See `SignInScenarios` for why these
 * are shared values and each target supplies only its own entry points.
 */
@OptIn(ExperimentalTestApi::class)
object WorkItemCardScenarios {

    /** Key, title and priority are all present. */
    val showsTheKeyTitleAndPriority: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.status == WorkItemStatus.InProgress }

        cardShowing(item)

        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        onNodeWithText(item.key.toString()).assertIsDisplayed()
        onNodeWithText(item.title).assertIsDisplayed()
        onNested(PriorityBadgeTag(item.priority)).assertIsDisplayed()
    }

    /** The assignee appears when there is one. */
    val showsTheAssigneeWhenThereIsOne: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.assignee != null }

        cardShowing(item)

        onNested(WorkItemCardAssigneeTag).assertIsDisplayed()
        onNodeWithText(item.assignee!!).assertIsDisplayed()
    }

    /** An unassigned task gets no assignee row at all - not an empty one. */
    val omitsTheAssigneeWhenThereIsNone: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.assignee == null }

        cardShowing(item)

        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        onNested(WorkItemCardAssigneeTag).assertDoesNotExist()
    }

    /** A due date appears when there is one. */
    val showsTheDueDateWhenThereIsOne: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.dueDate != null }

        cardShowing(item)

        onNested(WorkItemCardDueTag).assertIsDisplayed()
    }

    /** A task with no due date gets no chip, rather than an empty one. */
    val omitsTheDueDateWhenThereIsNone: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.dueDate == null }

        cardShowing(item)

        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        onNested(WorkItemCardDueTag).assertDoesNotExist()
    }

    /** The status is never printed: the column already names it. */
    val doesNotRepeatTheStatusTheColumnAlreadyNames: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.status == WorkItemStatus.InReview }

        cardShowing(item)

        onNodeWithTag(WorkItemCardTag(item)).assertIsDisplayed()
        onNodeWithText("In Review").assertDoesNotExist()
    }

    /** The card is tappable, and tapping it selects that card's task. */
    val tappingACardSelectsItsTask: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first()
        var selected: WorkItem? = null

        setContent {
            Themed {
                WorkItemCard(item = item, onClick = { selected = item })
            }
        }
        onNodeWithTag(WorkItemCardTag(item)).performClick()

        assertEquals(item, selected)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.cardShowing(item: WorkItem) {
    setContent {
        Themed {
            WorkItemCard(item = item, onClick = {})
        }
    }
}

/** The theme every scenario in this module is drawn in, so previews and tests agree. */
@Composable
internal fun Themed(content: @Composable () -> Unit) {
    ttTheme(ThemeScheme.Dark, content)
}

/**
 * The rules a card decides but the semantics tree cannot report.
 *
 * Plain [Test] methods rather than scenarios, because they assert on Kotlin values and not on
 * a composition. A `commonTest` class runs on every target, so these cost one JVM run and
 * two near-free native ones.
 */
class WorkItemCardRulesTest {

    @Test
    fun onlyADeliveredTaskIsStruckThrough() {
        val done = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Done }
        val closed = ViewPreviewData.workItems.first { it.status == WorkItemStatus.Closed }
        val inProgress = ViewPreviewData.workItems.first { it.status == WorkItemStatus.InProgress }

        assertTrue(isDelivered(done), "a Done task was delivered")
        assertFalse(
            isDelivered(closed),
            "a Closed task was abandoned, not delivered - striking it through would claim otherwise",
        )
        assertFalse(isDelivered(inProgress))
    }

    @Test
    fun anOverdueTaskIsOneThatIsUnfinishedAndPastItsDate() {
        val now = ViewPreviewData.instant
        // Both halves of the condition, not either: a finished task with a date in the past
        // is not late, and an unfinished task with no date is not late either. Selecting on
        // one half alone picks the wrong fixture and the assertion then fails for a reason
        // that has nothing to do with the rule.
        val pastDue = ViewPreviewData.workItems.first { !it.status.isComplete && (it.dueDate ?: now) < now }
        assertTrue(isOverdue(pastDue, now), "unfinished and past its date is late")

        val futureDue = ViewPreviewData.workItems.first { !it.status.isComplete && (it.dueDate ?: now) > now }
        assertFalse(isOverdue(futureDue, now), "a date in the future is not late")

        val doneAndPastDue = ViewPreviewData.workItems.first {
            it.status == WorkItemStatus.Done && (it.dueDate ?: now) < now
        }
        assertFalse(
            isOverdue(doneAndPastDue, now),
            "a delivered task is not late, whatever its date said",
        )

        val noDueDate = ViewPreviewData.workItems.first { it.dueDate == null }
        assertFalse(isOverdue(noDueDate, now))
    }
}
