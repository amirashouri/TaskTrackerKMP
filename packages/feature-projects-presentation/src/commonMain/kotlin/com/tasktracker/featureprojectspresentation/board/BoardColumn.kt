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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
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
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_add_task
import com.tasktracker.resources.board_empty_column
import com.tasktracker.resources.board_sort_by_date
import com.tasktracker.resources.board_sort_by_priority
import org.jetbrains.compose.resources.stringResource

/**
 * One board column lane rendering header, count, sort/add actions, and scrollable cards.
 * Empty lanes render explicitly as drag-and-drop targets; the glass header floats over scrolled cards.
 */
@Composable
fun BoardColumnView(
    column: BoardColumn,
    onSelect: (WorkItem) -> Unit,
    modifier: Modifier = Modifier,
    onAddTask: (() -> Unit)? = null,
    /** Custom trailing action slot rendered on each card. */
    cardTrailing: @Composable (WorkItem) -> Unit = {},
    /** Resolves assignee ID to display name. */
    assigneeName: (WorkItem) -> String? = { null },
    /** Resolves blocking work item IDs to human-readable keys. */
    blockedByKeys: (WorkItem) -> List<WorkItemKey> = { emptyList() },
    /** Project tag definitions passed to cards for tag icon resolution. */
    projectTags: List<ProjectTag> = emptyList(),
    /** Dispatches card summary text to clipboard. */
    onCopy: (String) -> Unit = {},
    /** Active drag coordinator, or null if drag gestures are disabled. */
    drag: BoardCardDrag? = null,
    /** Outlines column as active drop target when a dragged card hovers over it. */
    isDropTarget: Boolean = false,
) {
    Box(
        modifier = modifier
            .width(columnWidth)
            .fillMaxHeight()
            .ttGlassCard()
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
        var headerHeightPx by remember { mutableStateOf(0) }
        var sortOrder by remember(column.status) { mutableStateOf(ColumnSortOrder.ByDate) }
        val tabBarInset = LocalTabBarScrollUnderInset.current
        val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }

        // Read only inside gesture callbacks to prevent drag coordinates from triggering recomposition
        val currentDrag by rememberUpdatedState(drag)

        val sortedItems = remember(column.items, sortOrder) {
            when (sortOrder) {
                ColumnSortOrder.ByDate -> column.items.sortedWith(
                    compareBy<WorkItem> { it.dueDate == null }.thenBy { it.dueDate }
                )
                ColumnSortOrder.ByPriority -> column.items.sortedByDescending { it.priority.ordinal }
            }
        }

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
                    var cardCoordinates by remember(item.id) { mutableStateOf<LayoutCoordinates?>(null) }

                    // Card drag uses custom 300ms timeout rather than platform context menu 500ms
                    val platformViewConfiguration = LocalViewConfiguration.current
                    val cardViewConfiguration = remember(platformViewConfiguration) {
                        object : ViewConfiguration by platformViewConfiguration {
                            override val longPressTimeoutMillis: Long = BoardCardLiftTimeoutMillis
                        }
                    }
                    CompositionLocalProvider(LocalViewConfiguration provides cardViewConfiguration) {
                        WorkItemCard(
                            item = item,
                            onClick = { onSelect(item) },
                            onCopy = onCopy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (drag?.draggingItemID == item.id) 0.35f else 1f)
                                .then(
                                    if (drag == null) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .onGloballyPositioned { cardCoordinates = it }
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
        }

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

/** Coordinates card dragging state and callbacks between lanes and board host. */
class BoardCardDrag(
    val draggingItemID: WorkItemID?,
    val onStart: (item: WorkItem, bounds: Rect, grab: Offset) -> Unit,
    val onDrag: (delta: Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

const val BoardColumnTagPrefix = "board.column"

fun BoardColumnTag(status: WorkItemStatus): String = "$BoardColumnTagPrefix.${status.wireValue}"
fun BoardColumnCountTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.count"
fun BoardColumnEmptyTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.empty"
fun BoardColumnAddTaskTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.add"
fun BoardColumnSortTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.sort"

/** Timeout before long press lifts card (300ms prevents collision with vertical scroll swipes). */
internal const val BoardCardLiftTimeoutMillis = 300L

internal val columnWidth = 300.dp

/** Stride distance between adjacent columns used for horizontal snap calculations. */
internal val boardLaneStride: Dp = columnWidth + ttSpacing.md