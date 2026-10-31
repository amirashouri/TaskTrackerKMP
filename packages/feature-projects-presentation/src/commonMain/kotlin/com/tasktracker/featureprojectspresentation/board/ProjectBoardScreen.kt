package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSearchField
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_kanban
import com.tasktracker.resources.board_my_tasks
import com.tasktracker.resources.board_no_project_body
import com.tasktracker.resources.board_no_project_title
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_search_empty_body
import com.tasktracker.resources.work_item_search_empty_title
import com.tasktracker.resources.work_item_search_placeholder
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Renders the project board as a horizontally scrolling row of columns.
 * Eagerly composes all columns to ensure consistent lane heights and stable drag gestures.
 *
 * ## Search narrows the cards, not the board
 *
 * The field sits under the header and filters what the model already handed over — the sprint
 * filter and My Tasks have run before this screen sees the projection, so a query composes with
 * them as an intersection and neither resets the other. What the query changes is the *cards*:
 * the six lanes, their order and the horizontal snap geometry stay exactly as they are, and the
 * header's assignee stack still reads the unfiltered items, because typing does not change who
 * the board belongs to.
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
    /** Enables accessible fallback move buttons on cards for non-drag environments. */
    showMoveActions: Boolean = false,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    onAddTask: ((WorkItemStatus) -> Unit)? = null,
    showOnlyMyTasks: Boolean = false,
    onToggleMyTasks: () -> Unit = {},
    activeSprint: Sprint? = null,
    /** Map of user IDs to resolved display names. */
    assigneeNames: Map<UserID, String> = emptyMap(),
    /** Map of item IDs to resolved blocking work item keys. */
    blockedByKeys: Map<WorkItemID, List<WorkItemKey>> = emptyMap(),
    /** Disables adding tasks directly to lanes when an active sprint is InProgress. */
    canAddTask: Boolean = true,
) {
    val dragState = remember { BoardDragState() }

    // Screen state, not a preference: both shells render this same screen, so the query belongs
    // where the destination is rather than in `ShellPreferences`. Leaving the board and coming
    // back starts from an empty field, which is what a search box on a tab is expected to do.
    var query by remember { mutableStateOf("") }
    val appliedQuery = rememberDebouncedQuery(query)
    // Keyed on the projection *instance* as well as the query: the model replaces the projection
    // on every reload and on every My Tasks toggle, and a filter remembered against the query
    // alone would keep showing the previous load's cards.
    val shown = remember(projection, appliedQuery) { projection.matching(appliedQuery) }
    val focusManager = LocalFocusManager.current

    ClipboardCopyHost(modifier = modifier) { onCopy ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = ttSpacing.screenGutterLarge)
                .testTag(ProjectBoardScreenTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            val assignees = boardAssigneeNames(projection.items, assigneeNames)
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
                            showName = false,
                        )
                    }
                    if (assignees.isNotEmpty()) {
                        AssigneeStack(
                            names = assignees,
                            modifier = Modifier.testTag(ProjectBoardAssigneesTag),
                        )
                    }
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

            // Only with a project on screen: a field over "no project selected" would filter
            // nothing and offer a control whose list does not exist yet. Immediately under the
            // header row and above the banner, so the header's own order is identity first,
            // then the control over the list it filters.
            if (projection.project != null) {
                ttSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = stringResource(Res.string.work_item_search_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalGutter()
                        .testTag(ProjectBoardSearchFieldTag),
                    fieldModifier = Modifier.testTag(ProjectBoardSearchInputTag),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
            } else if (appliedQuery.isNotBlank() && shown.isEmpty) {
                // A query that matches nothing replaces the lane row rather than emptying it:
                // six empty lanes read as "this board is empty", which is a different and false
                // statement. With a blank query the lanes are untouched, empty ones included.
                ProjectsEmptyState(
                    title = stringResource(Res.string.work_item_search_empty_title),
                    body = stringResource(Res.string.work_item_search_empty_body),
                    modifier = Modifier.horizontalGutter().testTag(ProjectBoardEmptySearchTag),
                )
            } else {
                val scrollState = rememberScrollState()
                val laneCount = shown.columns.size
                val flingBehavior = rememberLaneSnapFlingBehavior(scrollState, laneCount)
                val density = LocalDensity.current
                val gutterPx = with(density) { ttSpacing.xxl.toPx() }
                val stridePx = with(density) { boardLaneStride.toPx() }
                val laneWidthPx = with(density) { columnWidth.toPx() }
                val edgePx = with(density) { 56.dp.toPx() }
                val scrollStepPx = with(density) { 10.dp.toPx() }

                var viewport by remember { mutableStateOf(Rect.Zero) }

                /** Maps pointer offset to target column status, or null if outside drop boundaries. */
                fun laneAt(pointer: Offset): WorkItemStatus? {
                    val row = viewport
                    if (row.width <= 0f || pointer.y < row.top || pointer.y > row.bottom) return null
                    val contentX = pointer.x - row.left - gutterPx + scrollState.value
                    if (contentX < 0f) return null
                    val index = floor(contentX / stridePx).toInt()
                    if (index !in 0 until laneCount) return null
                    return if (contentX - index * stridePx <= laneWidthPx) {
                        shown.columns[index].status
                    } else {
                        null
                    }
                }

                val drag = BoardCardDrag(
                    draggingItemID = dragState.item?.id,
                    onStart = { item, bounds, grab ->
                        // Terminal items cannot be dragged onto the board; ignore if already busy
                        if (movingItemID == null && !item.status.isTerminal) {
                            dragState.start(item, bounds, grab)
                            dragState.retarget(laneAt(dragState.pointer))
                        }
                    },
                    onDrag = { delta ->
                        dragState.move(delta)
                        dragState.retarget(laneAt(dragState.pointer))
                    },
                    onEnd = {
                        val ended = dragState.end()
                        if (ended != null) {
                            val (dragged, target) = ended
                            if (target != null && target != dragged.status) {
                                onMove(dragged.id, target)
                            }
                        }
                    },
                    onCancel = { dragState.cancel() },
                )

                // Auto-scroll horizontal row when dragging near the viewport edges
                LaunchedEffect(dragState.isDragging) {
                    if (!dragState.isDragging) return@LaunchedEffect
                    while (true) {
                        withFrameNanos { }
                        if (!dragState.isDragging) break
                        val pointer = dragState.pointer
                        val row = viewport
                        if (row.width <= 0f) continue
                        val dx = when {
                            pointer.x < row.left + edgePx -> -scrollStepPx
                            pointer.x > row.right - edgePx -> scrollStepPx
                            else -> 0f
                        }
                        if (dx != 0f) {
                            val consumed = scrollState.scrollBy(dx)
                            if (consumed != 0f) dragState.retarget(laneAt(pointer))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .onGloballyPositioned { viewport = it.boundsInRoot() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(state = scrollState, flingBehavior = flingBehavior)
                            .testTag(ProjectBoardColumnsTag),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LaneRowSpacer(ttSpacing.xxl)
                        shown.columns.forEachIndexed { index, column ->
                            if (index > 0) LaneRowSpacer(ttSpacing.md)
                            BoardColumnView(
                                column = column,
                                onSelect = { onSelect(it.id) },
                                onCopy = onCopy,
                                onAddTask = if (canAddTask) onAddTask?.let { addTask -> { addTask(column.status) } } else null,
                                modifier = Modifier,
                                assigneeName = { item ->
                                    item.assigneeID?.let { assigneeNames[it] }
                                },
                                blockedByKeys = { item -> blockedByKeys[item.id].orEmpty() },
                                projectTags = projection.project?.tags.orEmpty(),
                                cardTrailing = { item ->
                                    BoardCardActions(
                                        item = item,
                                        movingItemID = movingItemID,
                                        showMoveActions = showMoveActions,
                                        onFileAway = onFileAway,
                                        onMove = onMove,
                                    )
                                },
                                drag = drag,
                                isDropTarget = dragState.target == column.status,
                            )
                        }
                        LaneRowSpacer(ttSpacing.xxl)
                    }

                    // Ghost card rendered outside lane hierarchy to prevent cross-column clipping
                    val dragged = dragState.item
                    if (dragged != null && dragState.cardSize.width > 0) {
                        val ghostTopLeft = dragState.pointer - viewport.topLeft - dragState.grab
                        WorkItemCard(
                            item = dragged,
                            onClick = {},
                            onCopy = {},
                            modifier = Modifier
                                .offset {
                                    IntOffset(ghostTopLeft.x.roundToInt(), ghostTopLeft.y.roundToInt())
                                }
                                .width(with(density) { dragState.cardSize.width.toDp() })
                                .graphicsLayer {
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 24.dp.toPx()
                                    shape = RoundedCornerShape(ttRadius.surface)
                                    clip = false
                                },
                            trailing = {
                                BoardCardActions(
                                    item = dragged,
                                    movingItemID = movingItemID,
                                    showMoveActions = showMoveActions,
                                    onFileAway = onFileAway,
                                    onMove = onMove,
                                )
                            },
                            assigneeName = dragged.assigneeID?.let { assigneeNames[it] },
                            blockedByKeys = blockedByKeys[dragged.id].orEmpty(),
                            projectTags = projection.project?.tags.orEmpty(),
                        )
                    }
                }
            }
        }
    }
}

/** Tracks active card drag gesture and calculates root-relative ghost coordinates. */
private class BoardDragState {
    var item by mutableStateOf<WorkItem?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grab by mutableStateOf(Offset.Zero)
        private set
    var cardSize by mutableStateOf(IntSize.Zero)
        private set
    var target by mutableStateOf<WorkItemStatus?>(null)
        private set

    val isDragging: Boolean get() = item != null

    fun start(item: WorkItem, bounds: Rect, grabAt: Offset) {
        this.item = item
        grab = grabAt
        cardSize = IntSize(bounds.width.toInt(), bounds.height.toInt())
        pointer = bounds.topLeft + grabAt
        target = null
    }

    fun move(delta: Offset) {
        if (item == null) return
        pointer += delta
    }

    fun retarget(status: WorkItemStatus?) {
        if (item != null) target = status
    }

    fun end(): Pair<WorkItem, WorkItemStatus?>? {
        val dragged = item ?: return null
        val at = target
        clear()
        return dragged to at
    }

    fun cancel() = clear()

    private fun clear() {
        item = null
        pointer = Offset.Zero
        grab = Offset.Zero
        cardSize = IntSize.Zero
        target = null
    }
}

/** Spacing element between board lanes maintaining zero height to prevent layout expansion. */
@Composable
private fun LaneRowSpacer(width: Dp) {
    Spacer(Modifier.size(width = width, height = 0.dp))
}

private fun Modifier.horizontalGutter(): Modifier = padding(horizontal = ttSpacing.xxl)

/** Configures fixed-stride snap fling behavior for eager horizontal board scrolling. */
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

@Composable
internal fun BoardFileAwayAction(
    item: WorkItem,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ttGlassIconButton(
        image = ttActionIcons.archive,
        contentDescription = stringResource(Res.string.work_item_archive),
        onClick = { if (!isBusy) onClick() },
        tint = ttError(),
        modifier = modifier.testTag(BoardFileAwayTag(item)),
    )
}

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

const val ProjectBoardScreenTag = "board.screen"
const val ProjectBoardHeaderTag = "board.header"
const val ProjectBoardColumnsTag = "board.columns"
const val ProjectBoardMyTasksTag = "board.myTasks"
const val ProjectBoardTitleTag = "board.title"
const val ProjectBoardSearchFieldTag = "board.search"
const val ProjectBoardSearchInputTag = "board.search.input"
const val ProjectBoardEmptySearchTag = "board.emptySearch"
const val BoardFileAwayTagPrefix = "board.card.fileAway"
const val BoardMoveActionsTagPrefix = "board.card.moveActions"

fun BoardFileAwayTag(item: WorkItem): String = "$BoardFileAwayTagPrefix.${item.key}"
fun BoardMoveActionsTag(item: WorkItem): String = "$BoardMoveActionsTagPrefix.${item.key}"
fun BoardMoveTag(item: WorkItem, status: WorkItemStatus): String =
    "${BoardMoveActionsTag(item)}.${status.wireValue}"

private val MIN_FLANGE_VELOCITY_DP = 400.dp