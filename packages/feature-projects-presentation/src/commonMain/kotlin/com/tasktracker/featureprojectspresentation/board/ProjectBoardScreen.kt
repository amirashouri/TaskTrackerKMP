package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.Sprint
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_my_tasks
import com.tasktracker.resources.board_kanban
import com.tasktracker.resources.board_no_project_body
import com.tasktracker.resources.board_no_project_title
import com.tasktracker.resources.work_item_archive
import org.jetbrains.compose.resources.stringResource

/**
 * The board: one column per [WorkItemStatus] that appears on it, side by side.
 *
 * ## Why a scrolling `Row` of `Column`s and not a `LazyRow` of `LazyColumn`s
 *
 * Both would work, and the difference is what happens when a column has more cards than fit.
 *
 * A `LazyColumn` inside a `LazyRow` is two lazy containers nested on different axes. That
 * is the configuration where gesture arbitration goes wrong: a diagonal drag has to be
 * claimed by one container or the other, and which one wins is decided by Compose internals
 * rather than by this app. Worse, a lazy column only measures the children it has composed,
 * so a column's *height* depends on how far the user has scrolled the outer row - and a
 * card that jumps as the neighbouring column is dragged past is the single most noticeable
 * thing a board can do wrong.
 *
 * Composing every card costs memory on a project with a great many tasks. The trade is
 * deliberate: a board is a *layout*, and the cost of getting it wrong is visible on every
 * drag. A backlog, which is one unbounded vertical list, is the opposite shape and does get
 * a `LazyColumn` - see [ProjectBacklogScreen].
 *
 * ## Why filing away is on the card and not in a menu
 *
 * The backlog's row shows "File Away" as a button, and a card here shows the same verb in its
 * action slot - as a glyph, because a 300dp card has no room for the label - rather than both
 * hiding it behind a long-press menu. The two surfaces have to agree about what filing away
 * means - 12.5's test exists precisely because archiving is a status move and a disagreement
 * would be silent. Two always-visible controls that do the same thing are easier to keep in
 * step than two menus.
 *
 * ## Why the row runs to the edges and the gutter is inside it
 *
 * The screen gutter is applied *per child* rather than to the whole column, so that the lane
 * row's viewport is the full width of the screen. One gutter on the column looked correct and
 * was not: it shrank the scroll viewport by 16dp at each end, so a lane arriving from the
 * right stopped short of the edge with a strip of bare canvas beside it, and the page
 * background read as a panel sitting on top of the lanes instead of behind them. Inset the
 * content that is inset - the switcher, the error banner, the empty state - and leave the
 * thing that scrolls to run the whole way to the edge.
 *
 * The row keeps a leading spacer inside its own content, which is what a `LazyRow`'s
 * `contentPadding` would have done: lane 0 rests with the gutter visible at the left rather
 * than jammed against the edge, and the gutter scrolls away with everything else. A trailing
 * spacer exists for the same reason at the far end, which is the only end that can be
 * scrolled away from.
 */
@Composable
fun ProjectBoardScreen(
    projection: BoardProjection,
    isLoading: Boolean,
    errorMessage: String?,
    movingItemID: WorkItemID?,
    onSelect: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onMove: (WorkItemID, WorkItemStatus) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSwitcher: (() -> Unit)? = null,
    /**
     * Whether each card also offers the move verbs as buttons.
     *
     * Off by default, and that default is the design rather than an omission: a card is
     * 300dp wide, and filing away has already been given the card's bottom trailing corner -
     * a labelled button in the badge row would have taken a quarter of that width. Dragging a
     * card between lanes is the primary way to move work, and a row of three extra buttons
     * beside the badge and the due date competes with the card's own text for the rest of it -
     * so they are opt-in for a host that cannot offer dragging at all, such as a keyboard-only
     * or screen-reader path, where "there is no other way to do this" outweighs the
     * crowding.
     */
    showMoveActions: Boolean = false,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    onAddTask: ((WorkItemStatus) -> Unit)? = null,
    showOnlyMyTasks: Boolean = false,
    onToggleMyTasks: () -> Unit = {},
    activeSprint: Sprint? = null,
    /**
     * Whether lane-add is allowed. False while a sprint is InProgress: the column "+" hides.
     * True for Kanban (no sprint) and scheduled sprints.
     */
    canAddTask: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Vertically only. A horizontal gutter here became a *gap in the middle of the
            // page* once the row could scroll: the viewport ended short of each edge, so
            // a lane arriving from the right slid under a strip of empty canvas and stopped
            // there. The gutter belongs to the content that is inset from the edge, and the
            // row is not that - see [LaneRowSpacer].
            // Top only, and for the same reason the horizontal gutter is a `LaneRowSpacer` rather than
            // padding: padding on this Column shrinks the *lanes' viewport*, so the lanes stop
            // 32dp short of the bottom of the screen and no card can ever reach the edge, the
            // notch area, or the tab bar's glass. The clearance that keeps the last card clear of
            // the glass is inside the lane's scroll instead - see `BoardColumnView`'s
            // `padding(bottom = …)`.
            .padding(top = ttSpacing.screenGutterLarge)
            .testTag(ProjectBoardScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        // The header exists if *either* half of it has something to say, which is why this is
        // not an `onOpenSwitcher?.let`. The stack reports who is on the board, and that is a fact
        // about the board rather than about the switcher: a host that wires no switcher - a
        // preview, a screenshot, a keyboard-only surface - would otherwise lose the one part of
        // the header it had anything to say with.
        val assignees = boardAssigneeNames(projection.items)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalGutter()
                .testTag(ProjectBoardHeaderTag),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onOpenSwitcher?.let { openSwitcher ->
                    ProjectSwitcherButton(
                        project = projection.project,
                        onClick = openSwitcher,
                        projects = projects,
                        onSelectProject = onSelectProject,
                        // 24.1: the header's spare width belongs to the sprint/Kanban title, so the
                        // pill keeps just the monogram and chevron; the dropdown still lists names.
                        showName = false,
                    )
                }
                if (assignees.isNotEmpty()) {
                    AssigneeStack(
                        names = assignees,
                        modifier = Modifier.testTag(ProjectBoardAssigneesTag),
                    )
                }
                // 24.2: the leftover header width names the board. A running sprint is the board's
                // identity while it runs, and "Kanban" is the identity when there is none - the
                // same word a new task lands on. It fills and ellipsises rather than capping,
                // because unlike the switcher this is the title the empty space exists for.
                ttText(
                    text = activeSprint?.name ?: stringResource(Res.string.board_kanban),
                    style = ttTypography.titleSmall,
                    color = ttLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true).testTag(ProjectBoardTitleTag),
                )
            }
            ttGlassIconButton(
                image = ttActionIcons.person,
                contentDescription = stringResource(Res.string.board_my_tasks),
                onClick = onToggleMyTasks,
                tint = if (showOnlyMyTasks) ttAccent() else ttLabel(),
                modifier = Modifier.testTag(ProjectBoardMyTasksTag),
            )
        }
        errorMessage?.let { message ->
            ttBanner(
                message = message,
                onDismiss = onDismissError,
                modifier = Modifier.fillMaxWidth().horizontalGutter(),
            )
        }
        if (projection.project == null && !isLoading) {
            ProjectsEmptyState(
                title = stringResource(Res.string.board_no_project_title),
                body = stringResource(Res.string.board_no_project_body),
                modifier = Modifier.horizontalGutter(),
            )
        } else {
            val scrollState = rememberScrollState()
            val laneCount = projection.columns.size
            val flingBehavior = rememberLaneSnapFlingBehavior(scrollState, laneCount)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(state = scrollState, flingBehavior = flingBehavior)
                    .testTag(ProjectBoardColumnsTag),
                verticalAlignment = Alignment.Top,
            ) {
                // The gutter is a *spacer in the content*, not a padding on this row, and the
                // difference is the whole of the bug this replaced. Padding here would shrink
                // the scroll viewport, so the viewport - rather than the content - would stop
                // 16dp short of an edge and a lane arriving from the right would slide up to a
                // strip of bare canvas and stop there. A spacer is part of the content, so it
                // scrolls away with the lanes, the viewport stays the full width of the screen,
                // and lane 0 still rests with air at the left, which is what a `LazyRow`'s
                // `contentPadding` would have given.
                LaneRowSpacer(ttSpacing.xxl)
                projection.columns.forEachIndexed { index, column ->
                    if (index > 0) LaneRowSpacer(ttSpacing.md)
                    BoardColumnView(
                        column = column,
                        onSelect = { onSelect(it.id) },
                        onAddTask = if (canAddTask) onAddTask?.let { addTask -> { addTask(column.status) } } else null,
                        modifier = Modifier,
                        cardTrailing = { item ->
                            BoardCardActions(
                                item = item,
                                movingItemID = movingItemID,
                                showMoveActions = showMoveActions,
                                onFileAway = onFileAway,
                                onMove = onMove,
                            )
                        },
                    )
                }
                // The far end gets a gutter too, because it is the only end that can be scrolled
                // *away*: without it the last lane would rest flush against the right edge with
                // the row unable to reach any position that gave it air. The near end's gutter
                // is the one above - same width, and not because the ends are symmetric.
                LaneRowSpacer(ttSpacing.xxl)
            }
        }
    }
}

/**
 * A gap in the lane row, of exactly [width] across and nothing at all down.
 *
 * ## Why the row does not use `Arrangement.spacedBy`
 *
 * Because a `Spacer` is a layout, and an arrangement puts its spacing between *every pair of
 * layouts* - including the spacers. `Arrangement.spacedBy(ttSpacing.md)` with a leading and a
 * trailing gutter therefore made the gutter 24dp rather than the 16dp the rest of the screen
 * uses, and the row would have been inset by more than everything around it without anything
 * saying so. Spelling the gaps out costs one `if (index > 0)` and is the only way the content
 * adds up to the [boardLaneStride] the snap arithmetic assumes.
 *
 * The height is pinned to zero for the same kind of reason: a `Spacer` with only a `width` is
 * given the row's whole height, which would make the row as tall as the screen rather than as
 * tall as its lanes, for no visible gain.
 */
@Composable
private fun LaneRowSpacer(width: Dp) {
    Spacer(Modifier.size(width = width, height = 0.dp))
}

/** The screen gutter on the horizontal axis, for content inset from the edge of the page. */
private fun Modifier.horizontalGutter(): Modifier = padding(horizontal = ttSpacing.xxl)

/**
 * The board row's fling: coast to just short of a lane, then snap onto it.
 *
 * Compose ships this behaviour for `LazyList` and `LazyGrid` ([rememberSnapFlingBehavior]), and
 * it is exactly the behaviour wanted here - but both built-in providers read their snap
 * positions out of a *measured* lazy layout. This row is deliberately not lazy, for reasons in
 * [ProjectBoardScreen] that outlive any convenience, so the positions have to be derived from
 * the stride instead. [BoardLaneSnapLayoutInfo] is that derivation; this composable is only the
 * density conversion that [boardLaneSnapOffset] cannot do itself.
 *
 * The minimum fling velocity is spelled out here rather than imported because Compose's
 * equivalent, `MinFlingVelocityDp`, is `internal` to the foundation module. 400dp/ms is its
 * value, kept deliberately: this is the same threshold a `LazyRow` of lanes would have used,
 * so the gesture feels identical to a stock Compose pager.
 */
@Composable
private fun rememberLaneSnapFlingBehavior(
    scrollState: ScrollState,
    laneCount: Int,
): TargetedFlingBehavior {
    val density = LocalDensity.current
    val laneStridePx = with(density) { boardLaneStride.toPx() }
    val minFlingVelocityPx = with(density) { MIN_FLANGE_VELOCITY_DP.toPx() }
    val snapLayoutInfo = remember(scrollState, laneCount, laneStridePx, minFlingVelocityPx) {
        BoardLaneSnapLayoutInfo(
            scrollState = scrollState,
            columnCount = laneCount,
            laneStridePx = laneStridePx,
            minFlingVelocityPx = minFlingVelocityPx,
        )
    }
    return rememberSnapFlingBehavior(snapLayoutInfo)
}

/**
 * What one card offers, in its action slot.
 *
 * ## Why the board wires this rather than each column
 *
 * "File Away" and the move verbs mean the same thing here as they do in the backlog, and the
 * archive round-trip test exists because two surfaces offering the same verb can drift. The
 * rule that a filed-away task offers nothing - it is `Closed`, and the board has no `Closed`
 * lane - is therefore applied in one place, where the board's own statuses are known, and
 * not left to each card.
 *
 * Busy is per item and not per board: [movingItemID] is a single id, so a move in flight
 * disables that one card's verbs and leaves the rest of the board live. Locking the whole
 * board during a move would make an unrelated card's tap silently do nothing.
 */
@Composable
private fun BoardCardActions(
    item: WorkItem,
    movingItemID: WorkItemID?,
    showMoveActions: Boolean,
    onFileAway: (WorkItemID) -> Unit,
    onMove: (WorkItemID, WorkItemStatus) -> Unit,
) {
    if (item.status.isTerminal) return
    Column(
        // Wraps its content rather than filling the card, because the card's action slot puts
        // this at its trailing edge: a full-width column here would take the whole row, and
        // `Arrangement.End` would then have nothing to decide, so the glyph would sit at the
        // card's leading edge instead of its bottom trailing corner.
        modifier = Modifier.wrapContentWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
    ) {
        BoardFileAwayAction(
            item = item,
            isBusy = movingItemID == item.id,
            onClick = { onFileAway(item.id) },
        )
        if (showMoveActions) {
            BoardMoveActions(
                destinations = WorkItemStatus.boardOrder,
                item = item,
                onMove = { status -> onMove(item.id, status) },
            )
        }
    }
}

/**
 * The card action that files a task away, shown as a glyph in a card's action slot.
 *
 * Hidden for a task that is already filed away. The board has no `Closed` column, so a
 * filed-away task is not on the board at all - which means this only ever shows for
 * `Todo`, `InProgress`, `InReview` and `Done`, and the guard is here so a future column
 * that *does* include `Closed` does not offer to close it again.
 *
 * ## Why a glyph and not the backlog's labelled button
 *
 * The card is 300dp wide and its own text - key, title, badges, assignee - is what the lane is
 * scanned for. The backlog's row has a whole screen width of room for a "File Away" button
 * beside its content; a card does not, and at that width the label took a quarter of the card
 * to say what one drawn box says. So the card offers the archive glyph at its bottom trailing
 * corner, keeps [Res.string.work_item_archive] as the control's accessible name, and keeps the
 * error tint - a destructive verb should still look like one.
 *
 * The backlog row and the editor keep their buttons, and that is not an inconsistency to
 * reconcile: the glyph and the label are the same verb on the same status move, which is what
 * `ArchiveRoundTripScenarios` checks, rather than two independent implementations of it.
 */
@Composable
internal fun BoardFileAwayAction(
    item: WorkItem,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ttGlassIconButton(
        image = ttActionIcons.archive,
        // The button's own accessible name, and the reason it cannot go: an icon-only control
        // is announced by its content description or not at all.
        contentDescription = stringResource(Res.string.work_item_archive),
        onClick = { if (!isBusy) onClick() },
        tint = ttError(),
        modifier = modifier.testTag(BoardFileAwayTag(item)),
    )
}

/** The move verbs a card offers, so a board can be driven without drag-and-drop. */
@Composable
internal fun BoardMoveActions(
    destinations: List<WorkItemStatus>,
    item: WorkItem,
    onMove: (WorkItemStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(BoardMoveActionsTag(item)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
    ) {
        destinations.filter { it != item.status }.forEach { status ->
            ttGlassButton(
                label = statusLabel(status),
                onClick = { onMove(status) },
                modifier = Modifier.testTag(BoardMoveTag(item, status)),
                style = TTGlassButtonStyle(tint = ttAccent()),
            )
        }
    }
}

@Preview
@Composable
private fun ProjectBoardScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectBoardScreen(
        projection = ViewPreviewData.boardProjection(),
        isLoading = false,
        errorMessage = null,
        movingItemID = null,
        onSelect = {},
        onFileAway = {},
        onMove = { _, _ -> },
        onDismissError = {},
    )
}

@Preview
@Composable
private fun ProjectBoardScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectBoardScreen(
        projection = ViewPreviewData.emptyBoardProjection(),
        isLoading = false,
        errorMessage = null,
        movingItemID = null,
        onSelect = {},
        onFileAway = {},
        onMove = { _, _ -> },
        onDismissError = {},
    )
}

const val ProjectBoardScreenTag = "board.screen"
const val ProjectBoardHeaderTag = "board.header"
const val ProjectBoardColumnsTag = "board.columns"
const val ProjectBoardMyTasksTag = "board.myTasks"
const val ProjectBoardTitleTag = "board.title"
const val BoardFileAwayTagPrefix = "board.card.fileAway"
const val BoardMoveActionsTagPrefix = "board.card.moveActions"

/**
 * Both action tags carry the card's key as well as the verb.
 *
 * A board shows one of these per card, so a bare `board.card.fileAway` would match every
 * card on screen at once and any test using it would be asserting about an arbitrary one.
 * The key is the same handle the card's own tag uses, so "this card's button" is one
 * string the test already has.
 */
fun BoardFileAwayTag(item: WorkItem): String = "$BoardFileAwayTagPrefix.${item.key}"

fun BoardMoveActionsTag(item: WorkItem): String = "$BoardMoveActionsTagPrefix.${item.key}"

/** Suffixed with the status's wire value, matching every other status-keyed tag. */
fun BoardMoveTag(item: WorkItem, status: WorkItemStatus): String =
    "${BoardMoveActionsTag(item)}.${status.wireValue}"

// Compose's own `MinFlingVelocityDp`, which is `internal` to foundation. `val`, not `const
// val`: `Dp` is a value class, so `const val` is not allowed.
private val MIN_FLANGE_VELOCITY_DP = 400.dp
