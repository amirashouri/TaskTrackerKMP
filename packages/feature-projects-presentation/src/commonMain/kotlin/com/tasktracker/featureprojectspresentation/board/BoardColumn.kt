package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.ProjectTag
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.core.WorkItemKey
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_empty_column
import com.tasktracker.resources.board_add_task
import com.tasktracker.resources.board_sort_by_date
import com.tasktracker.resources.board_sort_by_priority
import org.jetbrains.compose.resources.stringResource

/**
 * One lane of the board: a heading, a count, and the cards in it.
 *
 * ## Why an empty lane still draws
 *
 * Every lane in [WorkItemStatus.boardOrder] is rendered even with nothing in it, and the
 * column shows [board_empty_column] rather than collapsing. Drag-and-drop needs a target
 * to drop onto, and a lane that only appears once it has a card in it means the first card
 * of a new column has nowhere to go. It also means the board does not reflow as work
 * progresses, which is the thing that makes a board readable: a card's neighbours stay
 * where they were.
 *
 * The alternative - hiding empty lanes - is what the original did, and the result was a
 * board that grew and shrank under the user's hand.
 *
 * ## Why the count is in the heading and not on a badge
 *
 * The count is the column's own summary and nothing else uses it. A badge would compete
 * with a card's priority badge for the same visual weight at a smaller size, and the two
 * are not comparable - one counts, one grades.
 *
 * ## Why the heading floats on its own glass, and why the add action lives in it
 *
 * The heading used to be the first child of the lane's scroll, so it scrolled away the
 * moment a lane had more than a screenful, and the lane's only "Add" control was a
 * full-width button pinned to the bottom of the box. Once the lane's scroll region was
 * extended to the bottom of the screen (so its cards pass *under* the tab bar - see
 * `LocalTabBarScrollUnderInset`), that bottom button ended up sitting in the same band the
 * tab bar owns, which is what put it in the wrong place.
 *
 * So the heading is now an overlay: a translucent glass slab pinned to the top of the lane,
 * with the cards scrolling *underneath* it. It carries its own [ttGlassSurface] rather than
 * relying on the lane's, and that is load-bearing rather than decorative - a card passing
 * behind plain text is unreadable, and the same material the tab bar uses for the same job
 * is what keeps the title legible over it. The slab's measured height becomes the scroll's
 * top inset, so the first card rests below it while later ones pass behind it.
 *
 * The add action is a compact circular "+" at the slab's trailing edge. The scroll's bottom
 * inset is unchanged and still does not depend on whether [onAddTask] is given; it is
 * reserved for the tab bar, not for the button that used to sit there.
 */
@Composable
fun BoardColumnView(
    column: BoardColumn,
    onSelect: (WorkItem) -> Unit,
    modifier: Modifier = Modifier,
    onAddTask: (() -> Unit)? = null,
    /**
     * The action slot for each card in this column.
     *
     * Taken as a slot rather than a callback so the column stays ignorant of what the
     * actions *are*: the column knows which cards it holds, and [ProjectBoardScreen] knows
     * what a card can be done to. Passing `onFileAway` and `onMove` down here instead would
     * have made the column a second place where board verbs are decided, which is how the
     * two filing surfaces came to disagree in the first place.
     */
    cardTrailing: @Composable (WorkItem) -> Unit = {},
    /**
     * Resolves a card's assignee id to the name the card should draw, or `null` when there
     * is no id or the directory cannot name it. See [WorkItemCard.assigneeName].
     */
    assigneeName: (WorkItem) -> String? = { null },
    /**
     * Resolves a card's blockers to the keys it should draw. Defaults to none, so a host that
     * does not wire dependencies renders exactly what it did before — see
     * [WorkItemCard.blockedByKeys].
     */
    blockedByKeys: (WorkItem) -> List<WorkItemKey> = { emptyList() },
    /**
     * The project's tag catalog, handed to every card in this lane. Defaults to none for the
     * same reason as [blockedByKeys]; a host without tags renders what it did before.
     */
    projectTags: List<ProjectTag> = emptyList(),
    /**
     * Hands a card's copy text to the host, which owns the clipboard and the confirmation.
     *
     * Only the text travels, not the item: the card has already decided what a copy contains
     * ([clipboardText]), and the host's job is to put that string on the clipboard and say so.
     * Defaults to nothing for the same reason the other resolvers do - a preview or a host that
     * has not wired copy still draws a column.
     */
    onCopy: (String) -> Unit = {},
    /**
     * The drag this lane takes part in, or `null` when its cards are not draggable.
     *
     * The lane reports the gesture and draws the two ends of it - the original card dimmed,
     * the lane outlined while a card hovers over it. Where the card actually lands is not its
     * decision: only the board knows the other lanes and which one the pointer is over, so
     * that stays in [ProjectBoardScreen]. Null is the default so previews and hosts that do
     * not wire dragging render exactly what they did before.
     */
    drag: BoardCardDrag? = null,
    /** Whether a lifted card is currently over this lane, which outlines it as the drop target. */
    isDropTarget: Boolean = false,
) {
    Box(
        modifier = modifier
            .width(columnWidth)
            .fillMaxHeight()
            .ttGlassCard()
            // Drawn after the glass so the lane's own surface cannot swallow it. Two points is
            // the one thing that says "release here" while cards are passing behind the lane.
            .then(
                if (isDropTarget) {
                    Modifier.border(
                        width = 2.dp,
                        color = ttAccent(),
                        shape = RoundedCornerShape(ttRadius.surface),
                    )
                } else {
                    Modifier
                }
            )
            .testTag(BoardColumnTag(column.status)),
    ) {
        // The slab's laid-out height, so the scroll can reserve exactly the room it covers.
        // Zero on the first frame - nothing has been measured yet - which is the same trade
        // the tab-bar inset makes: one recomposition later the content starts below the slab.
        var headerHeightPx by remember { mutableStateOf(0) }
        var sortOrder by remember(column.status) { mutableStateOf(ColumnSortOrder.ByDate) }
        val tabBarInset = LocalTabBarScrollUnderInset.current
        val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
        // Read only from inside the gesture below, never while composing this lane: the board
        // rebuilds the holder on every drag frame, and a composition read of the updated state
        // would make each rebuild invalidate the lane that just wrote it.
        val currentDrag by rememberUpdatedState(drag)

        val sortedItems = remember(column.items, sortOrder) {
            when (sortOrder) {
                ColumnSortOrder.ByDate -> column.items.sortedWith(
                    compareBy<WorkItem> { it.dueDate == null }.thenBy { it.dueDate }
                )
                ColumnSortOrder.ByPriority -> column.items.sortedByDescending { it.priority.ordinal }
            }
        }

        // Underneath, and first in the box so it draws below the slab. Full height, so the
        // cards reach the bottom of the screen and can be scrolled up out from under both the
        // slab and the floating tab bar - that is scroll *range* rather than viewport inset.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ttSpacing.xl)
                .padding(top = headerHeight + ttSpacing.sm)
                .padding(bottom = ttSpacing.xl + tabBarInset),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            if (column.isEmpty) {
                ttText(
                    text = stringResource(Res.string.board_empty_column),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(BoardColumnEmptyTag(column.status)),
                )
            } else {
                sortedItems.forEach { item ->
                    // The card's live coordinates, not a snapshot rectangle: a lane that has
                    // been scrolled since the last placement still reports the right position
                    // when the press lands, because boundsInRoot is computed on demand.
                    var cardCoordinates by remember(item.id) { mutableStateOf<LayoutCoordinates?>(null) }
                    WorkItemCard(
                        item = item,
                        onClick = { onSelect(item) },
                        onCopy = onCopy,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The card stays in the lane, faded: removing it would make the
                            // lane's cards jump while the ghost is still under the finger.
                            .alpha(if (drag?.draggingItemID == item.id) 0.35f else 1f)
                            .then(
                                if (drag == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .onGloballyPositioned { cardCoordinates = it }
                                        // Held, not dragged: a plain vertical swipe belongs to
                                        // the lane's scroll, and only a press that has stayed
                                        // still for the long-press timeout lifts a card. That is
                                        // what keeps the whole column from becoming a drag handle.
                                        .pointerInput(item.id) {
                                            var active: BoardCardDrag? = null
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { at ->
                                                    val coordinates = cardCoordinates
                                                    active = if (coordinates != null && coordinates.isAttached) {
                                                        currentDrag?.also {
                                                            it.onStart(item, coordinates.boundsInRoot(), at)
                                                        }
                                                    } else {
                                                        null
                                                    }
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    active?.onDrag(amount)
                                                },
                                                onDragEnd = {
                                                    active?.onEnd()
                                                    active = null
                                                },
                                                onDragCancel = {
                                                    active?.onCancel()
                                                    active = null
                                                },
                                            )
                                        }
                                }
                            ),
                        trailing = { cardTrailing(item) },
                        assigneeName = assigneeName(item),
                        blockedByKeys = blockedByKeys(item),
                        projectTags = projectTags,
                    )
                }
            }
        }

        // On top: the floating title, on its own glass so a card scrolling behind it cannot
        // swallow it. `onSizeChanged` sits outside the padding chain, so the height it reports
        // is the whole slab including its outer margin - exactly the inset the scroll wants.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = ttSpacing.sm)
                .padding(top = ttSpacing.sm)
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))
                .padding(
                    start = ttSpacing.md,
                    end = ttSpacing.xs,
                    top = ttSpacing.xs,
                    bottom = ttSpacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText(
                text = statusLabel(column.status),
                style = ttTypography.titleSmall,
                color = ttLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Takes the remaining width, which is what pins the count and the "+" to
                // the trailing edge however long the title is.
                modifier = Modifier.weight(1f),
            )
            ttText(
                text = column.count.toString(),
                style = ttTypography.caption,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(BoardColumnCountTag(column.status)),
            )
            ttGlassIconButton(
                image = ttActionIcons.sort,
                contentDescription = stringResource(
                    when (sortOrder) {
                        ColumnSortOrder.ByDate -> Res.string.board_sort_by_date
                        ColumnSortOrder.ByPriority -> Res.string.board_sort_by_priority
                    }
                ),
                onClick = {
                    sortOrder = when (sortOrder) {
                        ColumnSortOrder.ByDate -> ColumnSortOrder.ByPriority
                        ColumnSortOrder.ByPriority -> ColumnSortOrder.ByDate
                    }
                },
                tint = ttAccent(),
                modifier = Modifier.testTag(BoardColumnSortTag(column.status)),
            )
            onAddTask?.let { addTask ->
                ttGlassIconButton(
                    image = ttActionIcons.add,
                    contentDescription = stringResource(Res.string.board_add_task),
                    onClick = addTask,
                    modifier = Modifier.testTag(BoardColumnAddTaskTag(column.status)),
                )
            }
        }
    }
}

enum class ColumnSortOrder {
    ByDate,
    ByPriority,
}

/**
 * The board's half of a card drag, handed to every lane.
 *
 * A holder rather than one callback per seam: the lane needs to know which card is in the air
 * so it can fade the original, and the board needs the start position in root coordinates so
 * the ghost under the finger starts exactly where the card was. Keeping those on one object
 * means the two halves cannot disagree about which card is lifted.
 *
 * [onStart] receives the card's bounds in root coordinates and the grab point inside it,
 * which is what lets the board place the ghost before the first move arrives.
 */
class BoardCardDrag(
    val draggingItemID: WorkItemID?,
    val onStart: (item: WorkItem, bounds: Rect, grab: Offset) -> Unit,
    val onDrag: (delta: Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

@Preview
@Composable
private fun BoardColumnPreview() = ProjectsPreview(ThemeScheme.Dark) {
    Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
        WorkItemStatus.boardOrder.forEach { status ->
            BoardColumnView(
                column = BoardColumn(
                    status = status,
                    items = ViewPreviewData.boardProjection().items.filter { it.status == status },
                ),
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun BoardColumnLightPreview() = ProjectsPreview(ThemeScheme.Light) {
    Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
        BoardColumnView(
            column = BoardColumn(status = WorkItemStatus.Todo, items = emptyList()),
            onSelect = {},
        )
    }
}

const val BoardColumnTagPrefix = "board.column"

/** Suffixed with the status's wire value, so a test can find a lane by its column. */
fun BoardColumnTag(status: WorkItemStatus): String = "$BoardColumnTagPrefix.${status.wireValue}"

fun BoardColumnCountTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.count"

fun BoardColumnEmptyTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.empty"

fun BoardColumnAddTaskTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.add"

fun BoardColumnSortTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.sort"

/**
 * The lane's width.
 *
 * Fixed rather than weighted, because a card's content decides how much room it needs and a
 * proportion would make "In Progress" narrower than "To Do" on a phone. [ttRadius.surface] is
 * the same radius a card uses, which is what makes a column and a card read as the same
 * kind of floating surface.
 */
internal val columnWidth = 300.dp

/**
 * The distance from one lane's leading edge to the next one's.
 *
 * Internal rather than a public constant because it exists for one caller - the board row's
 * snap behaviour, which has to know where the lanes *land* - and it is deliberately defined
 * here, next to [columnWidth], rather than in the row that lays the lanes out. Two places
 * would each be free to say "300dp wide, 8dp apart" and disagree, and the disagreement would
 * not look like a disagreement: the row would look right and the snap would land a third of a
 * lane off.
 *
 * The row's `Arrangement.spacedBy` reads the same gap, so the stride follows the layout rather
 * than predicting it.
 */
internal val boardLaneStride: Dp = columnWidth + ttSpacing.md
