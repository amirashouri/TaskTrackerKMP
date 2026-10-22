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
import com.tasktracker.core.UserID
import com.tasktracker.core.randomTagID
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
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
 * the rest of the card down, and "nobody" is not a fact worth the space. When there is one it
 * is the **first name** ([assigneeFirstName]) in a pill: a 300dp card has room for "Amir" beside
 * a due date, and a surname is the half of the name nobody scans a board for.
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

    /**
     * The assignee appears as a first name, when there is one.
     *
     * Drawn from a **two-part** name on purpose. The fixture's own assignee is a single word,
     * so asserting the full name would pass whether or not the card shortens anything - a test
     * that cannot fail. "Amir Reza" makes the omission observable: a card still printing the
     * surname fails here, which is the defect the rule exists to prevent.
     */
    val showsTheAssigneeWhenThereIsOne: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.assigneeID != null }

        cardShowing(item, assigneeName = TWO_PART_ASSIGNEE)

        onNested(WorkItemCardAssigneeTag).assertIsDisplayed()
        // The literal, not `assigneeFirstName(TWO_PART_ASSIGNEE)`: asking the function under
        // test what it should have produced means a card that stopped shortening anything
        // still passes, because both sides move together. Restated here, the shortening is
        // something the card can fail.
        onNodeWithText("Amir").assertIsDisplayed()
        onNodeWithText(TWO_PART_ASSIGNEE).assertDoesNotExist()
    }

    /** The tag strip: icons only, and a deleted tag's stale id draws nothing. */
    val showsTagsAsIconsWithoutTheirNames: suspend ComposeUiTest.() -> Unit = {
        val tag = ProjectTag(name = "Bug", color = TagColor.Red, icon = TagIcon.Bug)
        // The second id has no catalog entry, exactly as a deleted tag leaves it.
        val item = ViewPreviewData.workItems.first().copy(tagIDs = listOf(tag.id, randomTagID()))

        cardShowing(item, projectTags = listOf(tag))

        onNested(WorkItemTagChipTag(tag)).assertIsDisplayed()
        onNodeWithText("Bug").assertDoesNotExist()
    }

    /** An unassigned task gets no assignee row at all - not an empty one. */
    val omitsTheAssigneeWhenThereIsNone: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.assigneeID == null }

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
                WorkItemCard(item = item, onClick = { selected = item }, onCopy = {})
            }
        }
        onNodeWithTag(WorkItemCardTag(item)).performClick()

        assertEquals(item, selected)
    }

    /** Every card offers the copy control, including one with no assignee and no due date. */
    val showsTheCopyControl: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.dueDate == null && it.assigneeID == null }

        cardShowing(item)

        onNested(WorkItemCardCopyTag).assertIsDisplayed()
    }

    /**
     * Copying hands the host the title and the details, and does not also select the card.
     *
     * The expected payload is restated as a literal rather than produced by [clipboardText]:
     * asking the function under test what it should have handed over means a card that copied
     * the wrong text still passes, because both sides move together.
     *
     * Both halves are one scenario because they are one gesture. A copy control that also
     * opened the card would still hand the right string over, and only asserting both at once
     * holds the whole of what tapping it is supposed to do.
     */
    val copyingACardHandsTheTitleAndDetailsToTheHost: suspend ComposeUiTest.() -> Unit = {
        val item = ViewPreviewData.workItems.first { it.details.isNotBlank() }
        var copied: String? = null
        var selected: WorkItem? = null

        setContent {
            Themed {
                WorkItemCard(
                    item = item,
                    onClick = { selected = item },
                    onCopy = { copied = it },
                )
            }
        }
        onNested(WorkItemCardCopyTag).performClick()

        assertEquals(
            "${item.title}\n\n${item.details.trim()}",
            copied,
            "a copy should be the title, then a blank line, then the details",
        )
        assertEquals(
            null,
            selected,
            "tapping the copy control must not also open the card it copied",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.cardShowing(
    item: WorkItem,
    assigneeName: String? = null,
    projectTags: List<ProjectTag> = emptyList(),
) {
    setContent {
        Themed {
            WorkItemCard(
                item = item,
                assigneeName = assigneeName,
                projectTags = projectTags,
                onClick = {},
                onCopy = {},
            )
        }
    }
}

/**
 * The theme every scenario in this module is drawn in, so previews and tests agree.
 *
 * `content =` is named rather than positional because `ttTheme` grew an `accentPalette`
 * parameter between `scheme` and `content`, so a positional call now binds the palette to a
 * composable. That is a compile error here, and a silent one only until the module is built:
 * this file is `commonTest`, so the JVM host reports it and the iOS host reports it again.
 */
@Composable
internal fun Themed(content: @Composable () -> Unit) {
    ttTheme(ThemeScheme.Dark, content = content)
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

    @Test
    fun theBoardStackDeduplicatesSortsAndSkipsBlankAssignees() {
        val priya = UserID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        val amirLower = UserID("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
        val blank = UserID("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
        val amir = UserID("dddddddd-dddd-4ddd-8ddd-dddddddddddd")
        val items = listOf(
            ViewPreviewData.workItems[0].copy(assigneeID = priya),
            ViewPreviewData.workItems[1].copy(assigneeID = amirLower),
            ViewPreviewData.workItems[2].copy(assigneeID = priya),
            ViewPreviewData.workItems[3].copy(assigneeID = null),
            ViewPreviewData.workItems[4].copy(assigneeID = blank),
            ViewPreviewData.workItems[5].copy(assigneeID = amir),
        )
        val names = mapOf(
            priya to "  Priya  ",
            amirLower to "amir",
            blank to "   ",
            amir to "Amir",
        )

        assertEquals(
            // 'amir' before 'Amir', not the other way round: the sort key is lowercased, so the
            // two compare equal and the stable sort leaves them in board order. That is the
            // intended reading - a name that differs only in case is not ordered by the case.
            listOf("amir", "Amir", "Priya"),
            boardAssigneeNames(items, names),
            "sorted case-insensitively so 'Amir' and 'amir' are two, blank and null are none, " +
                "and the untrimmed '  Priya  ' is the same Priya as the trimmed one",
        )
    }

    @Test
    fun theAssigneeIsShortenedToItsFirstName() {
        assertEquals("Amir", assigneeFirstName("Amir Reza"), "the surname is the half nobody scans for")
        assertEquals("Amir", assigneeFirstName("Amir"), "a single-word name is already a first name")
        assertEquals(
            "Amir",
            assigneeFirstName("  Amir  Reza  "),
            "trimmed first, or an untrimmed name yields an empty pill",
        )
    }

    @Test
    fun anAssigneeWithNoSpaceInItIsShownWhole() {
        assertEquals("amir@example.com", assigneeFirstName("amir@example.com"), "an address has no first name to keep")
        assertEquals("Platform", assigneeFirstName("Platform"), "a team is left as it was written")
    }

    @Test
    fun aCopyIsTheTitleThenTheDetails() {
        val item = ViewPreviewData.workItems.first { it.details.isNotBlank() }

        assertEquals(
            "${item.title}\n\n${item.details}",
            clipboardText(item),
            "a blank line keeps the title a heading when the paste lands in a markdown renderer",
        )
    }

    @Test
    fun aTaskWithNoDetailsCopiesAsItsTitleAlone() {
        val item = ViewPreviewData.workItems.first().copy(details = "   ")

        assertEquals(
            item.title,
            clipboardText(item),
            "whitespace details must not become a title followed by blank lines",
        )
    }

    @Test
    fun theShorteningAlsoCutsASurnameThatIsMoreThanOneWord() {
        // The cost of the rule, asserted rather than left to be discovered on a card. A card
        // shows one word, so "Ana van der Berg" reads as "Ana" and a pair of people reads as
        // the first of them. Both are wrong as people; both are the price of not printing a
        // surname on a 300dp card, and the editor one tap away is where the full name is read.
        assertEquals("Ana", assigneeFirstName("Ana van der Berg"))
        assertEquals("Amir", assigneeFirstName("Amir + Priya"))
    }
}

/**
 * A two-part name, for the scenario that has to observe the shortening.
 *
 * Private rather than a fixture of its own because it is not data any other test wants: it
 * exists to make "the card dropped the surname" a failure rather than an unfalsifiable pass.
 */
private const val TWO_PART_ASSIGNEE = "Amir Reza"
