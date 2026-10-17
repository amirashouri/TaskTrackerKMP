package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.tasktracker.designsystem.DISC_TAG
import com.tasktracker.designsystem.OVERFLOW_TAG
import com.tasktracker.designsystem.ttAvatarSize
import com.tasktracker.designsystem.ttAvatarStackDefaults
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.core.UserID
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.WorkItem
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_kanban
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

/**
 * How the board's lane row sits on the screen, as scenarios.
 *
 * ## What is being held still
 *
 * The lane row is the one thing on this screen that runs to the edges. Everything else - the
 * project switcher, the error banner, the empty state - is inset by the screen gutter, because
 * text and buttons need air around them and a full-bleed button reads as a bug.
 *
 * The row is the exception because it *scrolls*. A gutter applied to the row's own container
 * becomes a gutter on the scroll viewport: the row stops 32dp short of each edge no matter
 * where it has been dragged to, so a lane arriving from the right slides up to a strip of bare
 * canvas and stops there. On a page whose background shows through, that strip reads as a
 * panel lying on top of the lanes rather than behind them.
 *
 * So the gutter moves *inside* the row - to a leading spacer that scrolls away with the
 * content, the way a `LazyRow`'s `contentPadding` behaves. Lane 0 then rests with the gutter
 * visible at the left, exactly as before, and no lane is ever inset from the right.
 *
 * These scenarios assert bounds rather than appearance. Whether a lane *looks* flush with the
 * edge is a pixel question; whether its container reaches the edge is a fact about the layout
 * that survives a theme change, and it is the one that went wrong.
 */
@OptIn(ExperimentalTestApi::class)
object BoardLaneScenarios {

    /** The My Tasks person filter icon button is displayed in the board header. */
    val theMyTasksFilterButtonIsDisplayed: suspend ComposeUiTest.() -> Unit = {
        boardShowing()
        onNodeWithTag(ProjectBoardMyTasksTag).assertIsDisplayed()
    }

    /** The lane row's own container runs the whole width of the screen. */
    val theLaneRowReachesBothEdgesOfTheScreen: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        val screen = onRoot().fetchSemanticsNode().size
        val row = onNodeWithTag(ProjectBoardColumnsTag).fetchSemanticsNode()

        assertEquals(
            0f,
            row.boundsInRoot.left,
            "the row's viewport starts at the left edge, so a lane arriving from the left is not inset",
        )
        assertEquals(
            screen.width.toFloat(),
            row.boundsInRoot.right,
            "and ends at the right edge, so a lane arriving from the right is not inset either",
        )
    }

    /**
     * The first lane still rests with a gutter beside it.
     *
     * The counterpart to the scenario above, and the reason the row is not simply given
     * `padding(0.dp)`: reaching the edge is what the *viewport* should do, and lane 0 is not
     * the viewport's left edge. Without a leading spacer it would sit against the bezel.
     *
     * Held against [ttSpacing.xxl] and not against a literal, because the point is that the
     * gutter is *the same token* the rest of the screen insets by - this used to be held at
     * [ttSpacing.screenGutterLarge], which is the app's *vertical* inset, and was red for the
     * whole of that drift. The assertion still has teeth: it fails if the gutter moves back
     * onto the viewport as padding, or if the row's spacer is given a different token.
     */
    val theFirstLaneRestsAGutterFromTheLeftEdge: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        val row = onNodeWithTag(ProjectBoardColumnsTag).fetchSemanticsNode()
        val todo = onNodeWithTag(BoardColumnTag(WorkItemStatus.Todo)).fetchSemanticsNode()
        val gutter = densityPixels(ttSpacing.xxl)

        assertEquals(
            gutter,
            todo.boundsInRoot.left - row.boundsInRoot.left,
            "lane 0 is held off the edge by the gutter, which scrolls away with it",
        )
    }

    /**
     * The switcher, which is not the row, is still inset from the left.
     *
     * It used to be asserted at both edges, which only made sense while the switcher was a
     * full-width slab. It is a compact, content-width pill now - sized to the project name it
     * names - so there is no trailing edge to hold 16dp clear, and asserting one would be
     * asserting the stretch this change removed.
     */
    val theSwitcherIsInsetFromTheLeftEdge: suspend ComposeUiTest.() -> Unit = {
        setContent {
            Themed {
                ProjectBoardScreen(
                    projection = ViewPreviewData.boardProjection(),
                    isLoading = false,
                    errorMessage = null,
                    movingItemID = null,
                    onSelect = {},
                    onFileAway = {},
                    onMove = { _, _ -> },
                    onDismissError = {},
                    onOpenSwitcher = {},
                )
            }
        }

        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode()

        assertEquals(
            densityPixels(ttSpacing.xxl),
            switcher.boundsInRoot.left,
            "insets itself from the left by the screen's gutter",
        )
    }

    /**
     * The switcher is a pill around the project name, not a bar across the screen.
     *
     * The regression this holds: the switcher was `fillMaxWidth`, so a 390dp screen carried a
     * ~358dp glass slab holding a 26dp monogram, a short name and a chevron - a surface that
     * read as empty and left the board's own add-task action looking like the less important of
     * the two. Asserted as a *bound* rather than as an appearance, because "does not look empty"
     * is not a fact and "is 240dp or narrower" is.
     */
    val theSwitcherIsNoWiderThanItsContentAllows: suspend ComposeUiTest.() -> Unit = {
        boardShowing(onOpenSwitcher = {})

        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode()

        assertTrue(
            switcher.boundsInRoot.width <= densityPixels(switcherWidthMax),
            "the pill is capped, so a long project name cannot take the header's whole width",
        )
        assertTrue(
            switcher.boundsInRoot.width <
                onRoot().fetchSemanticsNode().size.width - 2 * densityPixels(ttSpacing.xxl),
            "and it is content-width rather than filled: it does not span the screen's gutters",
        )
    }

    /**
     * On the board the pill is the monogram and the chevron, not the project name.
     *
     * The header's spare width belongs to the sprint / Kanban title, so the switcher drops the
     * name text and keeps the key prefix - the one fact about a project that is guaranteed
     * unique. The name is not gone from the app, only from the pill: the dropdown still renders
     * every project's full name. Asserted by the name node's *absence* as well as the monogram's
     * presence, because "the monogram is shown" would be green with the name text also on the
     * pill - the absent half is the whole of the change.
     */
    val theSwitcherShowsTheMonogramAndNotTheName: suspend ComposeUiTest.() -> Unit = {
        boardShowing(onOpenSwitcher = {})

        // The pill is a clickable Row, and clickable merges its descendants' semantics, so the
        // monogram and chevron tags only exist on the unmerged tree (same as the stack discs).
        onNodeWithTag(ProjectSwitcherMonogramTag, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(ProjectSwitcherChevronTag, useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithText(ViewPreviewData.seededProject.name).assertCountEquals(0)
    }

    /**
     * The header's discs overlap, and each one keeps part of itself showing.
     *
     * The property is the *overlap*, asserted as geometry: each disc after the first must start
     * before the one before it ends, or the "stack" is a row of separate circles and the feature
     * is a list. `zIndex` is the other half and cannot be read off the tree, so what this holds
     * is the spacing; the paint order is held by the pixels, in `BoardHeaderStackPixelTest`.
     *
     * Four discs is the cap, and the count chip is what makes the cap honest - a stack that
     * quietly dropped names would be worse than one that admits it stopped, so the fifth
     * assignee has to be visible *as a number*.
     */
    val theAssigneeStackOverlapsAndCountsWhatItHides: suspend ComposeUiTest.() -> Unit = {
        boardShowing(
            projection = BoardProjection(project = ViewPreviewData.seededProject, items = MANY_ASSIGNEES),
            assigneeNames = MANY_ASSIGNEE_NAMES,
        )

        // Sorted by `left` before anything is compared, and that is not tidiness. The semantics
        // tree hands these nodes back in an order this test does not control, and an unsorted
        // list compared pairwise passes for *any* spacing: read right to left, every disc starts
        // before the one to its right ends. This assertion was green with a 100dp gap between
        // 24dp discs before the sort went in.
        val discs = onAllNodesWithTag(DISC_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.left }

        assertEquals(
            ttAvatarStackDefaults.maxVisible,
            discs.size,
            "exactly maxVisible discs, whatever the project actually holds",
        )
        assertEquals(
            1,
            onAllNodesWithTag(OVERFLOW_TAG, useUnmergedTree = true).fetchSemanticsNodes().size,
            "and one chip for the names it is not drawing",
        )
        // Six names, four drawn, so the chip has to say two. A stack that quietly dropped names
        // would look correct and be wrong, and this is the only place that shows it.
        onNodeWithText("+2").assertIsDisplayed()
        discs.zipWithNext().forEach { (left, right) ->
            assertTrue(
                right.left < left.right,
                "a disc starts at ${right.left} and the one before it ends at ${left.right}: the " +
                    "stack is a row of separate circles, not a stack",
            )
        }
        // The stride *is* the visible width, and that identity is the whole geometry: each disc
        // covers the 8dp the next one starts inside it, so what is left showing is the distance
        // between two lefts. A stack built with a positive gap has a stride of 24 and shows
        // nothing, which is why this is asserted rather than assumed from the negative spacing.
        val stride = discs[1].left - discs.first().left
        assertEquals(
            densityPixels(ttAvatarStackDefaults.overlapVisible),
            stride,
            absoluteTolerance = 1f,
        )
        assertEquals(
            densityPixels(ttAvatarSize.avatar),
            discs.first().width,
            absoluteTolerance = 1f,
        )
    }

    /**
     * A board nobody has assigned anything to shows no stack at all.
     *
     * Not an empty one, and not a placeholder disc. The card's rule is the same one - "nobody" is
     * not a fact worth the space - and an empty stack on the trailing end of the header would
     * read as a rendering failure rather than as the absence of something.
     */
    val anUnassignedBoardHasNoStack: suspend ComposeUiTest.() -> Unit = {
        boardShowing(projection = BoardProjection(project = ViewPreviewData.seededProject, items = UNASSIGNED))

        onAllNodesWithTag(ProjectBoardAssigneesTag).assertCountEquals(0)
    }

    /**
     * A board with no active sprint is named "Kanban".
     *
     * 24.2: the header's leftover width shows the board's identity, and when no sprint is
     * running that identity is the word Kanban - the same word a task added from the board
     * goes to. Asserted as text, because the whole point is the name, not a tag's presence.
     */
    val aBoardWithoutAnActiveSprintIsNamedKanban: suspend ComposeUiTest.() -> Unit = {
        boardShowing(onOpenSwitcher = {})

        onNodeWithTag(ProjectBoardTitleTag).assertTextEquals(getString(Res.string.board_kanban))
    }

    /**
     * A board with an active sprint is named after that sprint, and has no banner.
     *
     * The sprint name replaces the old full-width `Active Sprint:` banner, which said the same
     * thing in a strip of its own and cost the lanes two lines of the screen. Asserted as the
     * name *and* the banner string's absence, so neither "the title shows nothing" nor "the
     * banner came back" can pass quietly.
     */
    val aBoardWithAnActiveSprintNamesItInTheHeader: suspend ComposeUiTest.() -> Unit = {
        boardShowing(onOpenSwitcher = {}, activeSprint = ViewPreviewData.openSprint)

        onNodeWithTag(ProjectBoardTitleTag).assertTextEquals(ViewPreviewData.openSprint.name)
        onAllNodesWithText("Active Sprint", substring = true).assertCountEquals(0)
    }

    /** Every lane is still drawn, edge-to-edge layout notwithstanding. */
    val everyLaneIsStillDrawn: suspend ComposeUiTest.() -> Unit = {
        boardShowing()

        WorkItemStatus.boardOrder.forEach { status ->
            // Existence rather than display: the lane row scrolls, so the far lanes are
            // composed and laid out but off a phone viewport. The bounds assertion below is
            // what proves the row really scrolls rather than wrapping.
            onNodeWithTag(BoardColumnTag(status)).assertExists()
        }
        // Unclipped, because a lane scrolled past the viewport edge is *clipped* by the scrolling
        // row: its clipped right edge reports the viewport's edge, which says nothing about
        // whether the content is wider than the window. The unclipped right edge is the laid-out
        // position, which is the claim.
        val lastLaneRight = onNodeWithTag(BoardColumnTag(WorkItemStatus.Done))
            .getUnclippedBoundsInRoot().right.value
        val viewportWidth = onRoot().fetchSemanticsNode().size.width.toFloat()
        assertTrue(
            lastLaneRight > viewportWidth / 2,
            "and the last lane is off past the middle, i.e. the row really does scroll: " +
                "the Done lane ends at ${lastLaneRight}px of a ${viewportWidth}px viewport",
        )
    }

    /**
     * A running sprint hides every lane's "+" — adding mid-sprint is against the logic, and a
     * task created this way would join the sprint's filtered board silently.
     *
     * Structural (no pixels): the property is the node's absence, which the semantics tree
     * answers directly. Kanban (null sprint) and scheduled sprints keep the button.
     */
    val aRunningSprintHidesEveryLaneAdd: suspend ComposeUiTest.() -> Unit = {
        // onAddTask is non-null on purpose: the lock must win even when a host wired the
        // callback. A scenario with null here would pass with the gate inverted (!canAddTask),
        // which the mutation check caught — Kanban went red, this stayed green.
        boardShowing(onAddTask = {}, activeSprint = ViewPreviewData.openSprint, canAddTask = false)

        WorkItemStatus.boardOrder.forEach { status ->
            onAllNodesWithTag(BoardColumnAddTaskTag(status)).assertCountEquals(0)
        }
    }

    /**
     * Kanban keeps every lane's "+": with no sprint the board is open to new tasks and the
     * added task lands on the same board it was created from.
     *
     * Existence rather than displayed: the lane row scrolls, so the far lanes exist but are
     * off-viewport (the same reason `everyLaneIsStillDrawn` asserts bounds, not display, for
     * the last lane). The lock is about presence — `assertCountEquals(0)` above vs exists
     * here — not about scroll position.
     */
    val kanbanKeepsEveryLaneAdd: suspend ComposeUiTest.() -> Unit = {
        boardShowing(onAddTask = {}, canAddTask = true)

        WorkItemStatus.boardOrder.forEach { status ->
            onAllNodesWithTag(BoardColumnAddTaskTag(status)).assertCountEquals(1)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.boardShowing(
    projection: BoardProjection = ViewPreviewData.boardProjection(),
    onOpenSwitcher: (() -> Unit)? = null,
    activeSprint: Sprint? = null,
    onAddTask: ((WorkItemStatus) -> Unit)? = null,
    canAddTask: Boolean = true,
    assigneeNames: Map<UserID, String> = emptyMap(),
) {
    setContent {
        Themed {
            ProjectBoardScreen(
                projection = projection,
                isLoading = false,
                errorMessage = null,
                movingItemID = null,
                onSelect = {},
                onFileAway = {},
                onMove = { _, _ -> },
                onDismissError = {},
                onOpenSwitcher = onOpenSwitcher,
                activeSprint = activeSprint,
                onAddTask = onAddTask,
                canAddTask = canAddTask,
                assigneeNames = assigneeNames,
            )
        }
    }
}

/**
 * [dp] in the density this test is running at.
 *
 * The scenarios above compare a `Dp` from the spacing ramp against a measured `Float` in root
 * coordinates, and root coordinates are pixels. Reading the density off the node that carries
 * the tag keeps the comparison honest at whatever density the host runs - a JVM host at 1.0 and
 * an Android host at 3.0 are both 1.0 and 3.0 respectively, and hardcoding either would fail on
 * the other.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.densityPixels(dp: Dp): Float = with(density) { dp.toPx() }
/** Six names, because the cap is four and the count chip has to have something to count. */
private val FIRST_NAMES = listOf("Amir", "Ana", "Priya", "Sam", "Wren", "Yusuf")

/** One fixed id per name above, so a scenario can point a card at a name deterministically. */
private val MANY_ASSIGNEE_IDS: List<UserID> = FIRST_NAMES.mapIndexed { index, _ ->
    UserID("0000000$index-0000-4000-8000-00000000000$index")
}

/** The display names the board resolves those ids through. */
private val MANY_ASSIGNEE_NAMES: Map<UserID, String> =
    MANY_ASSIGNEE_IDS.zip(FIRST_NAMES).toMap()

/**
 * Six named assignees on otherwise ordinary cards, for the cap.
 *
 * Drawn from [ViewPreviewData]'s own items with the assignee rewritten, rather than invented, so
 * the cards stay the ones every other board scenario renders - the stack is then the only thing
 * that differs between this and an ordinary board.
 *
 * Declared after [FIRST_NAMES] because these are top-level `val`s: initialisation order is the
 * order they are written in, so a list that reads another top-level `val` has to come second or
 * it reads it as null.
 */
private val MANY_ASSIGNEES: List<WorkItem> = ViewPreviewData.workItems
    .mapIndexed { index, item -> item.copy(assigneeID = MANY_ASSIGNEE_IDS[index % MANY_ASSIGNEE_IDS.size]) }

/** The same cards with nothing assigned, which is a state a real project can be in. */
private val UNASSIGNED: List<WorkItem> = ViewPreviewData.workItems.map { it.copy(assigneeID = null) }
