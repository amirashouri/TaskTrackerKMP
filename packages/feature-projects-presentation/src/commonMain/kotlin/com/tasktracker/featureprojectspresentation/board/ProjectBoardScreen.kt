package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.LocalLeadingChromeScrollUnderInset
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSearchField
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.BoardProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_archive_selection
import com.tasktracker.resources.board_kanban
import com.tasktracker.resources.board_move_prompt_body
import com.tasktracker.resources.board_move_prompt_title
import com.tasktracker.resources.board_move_selection
import com.tasktracker.resources.board_my_tasks
import com.tasktracker.resources.board_selection_clear
import com.tasktracker.resources.board_selection_count
import com.tasktracker.resources.board_selection_working
import com.tasktracker.resources.board_no_project_body
import com.tasktracker.resources.board_no_project_title
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_search_close
import com.tasktracker.resources.work_item_search_empty_body
import com.tasktracker.resources.work_item_search_empty_title
import com.tasktracker.resources.work_item_search_open
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
 *
 * The field is closed by default and revealed by the header's search toggle, which is why the
 * board can be read without spending a row on a box most visits never use. Closing it clears the
 * query as well as hiding the field: the alternative leaves the lanes short of cards with no
 * visible control to explain why.
 *
 * ## Selection is one lane's, and the screen only binds it
 *
 * [isSelecting], [selectionLane] and [selection] are model state; each lane reports through the
 * same callbacks with its own status id, so the screen never decides which lane a tap belongs to.
 * While the mode is on the search field goes away and drag is switched off: a query could hide a
 * ticked card, and a drag would lift a card out of a lane whose set is being built. The action bar
 * takes the header's place under the lanes' header row, because both lanes' affordances need room
 * and the bar is what acts on the set.
 */
@Composable
fun ProjectBoardScreen(
    projection: BoardProjection,
    isLoading: Boolean,
    errorMessage: String?,
    movingItemID: WorkItemID?,
    onSelect: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onMove: (WorkItemID, String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSwitcher: (() -> Unit)? = null,
    /** Enables accessible fallback move buttons on cards for non-drag environments. */
    showMoveActions: Boolean = false,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    onAddTask: ((String) -> Unit)? = null,
    showOnlyMyTasks: Boolean = false,
    onToggleMyTasks: () -> Unit = {},
    activeSprint: Sprint? = null,
    /** Map of user IDs to resolved display names. */
    assigneeNames: Map<UserID, String> = emptyMap(),
    /** Map of item IDs to resolved blocking work item keys. */
    blockedByKeys: Map<WorkItemID, List<WorkItemKey>> = emptyMap(),
    /** Disables adding tasks directly to lanes when an active sprint is InProgress. */
    canAddTask: Boolean = true,
    /** True while one lane's cards are being ticked. */
    isSelecting: Boolean = false,
    /** The lane the selection belongs to, or null when nothing is ticked. */
    selectionLane: String? = null,
    /** The ticked cards. Membership is what draws each card's box. */
    selection: Set<WorkItemID> = emptySet(),
    /** True while a batch move or archive runs; the action bar disables itself and says so. */
    isBatchRunning: Boolean = false,
    /** Enters select mode on one lane. Null hides every lane's Select control. */
    onStartSelecting: ((String) -> Unit)? = null,
    /** Ticks or unticks one card of the selection lane. */
    onToggleSelected: (String, WorkItemID) -> Unit = { _, _ -> },
    /** Selects every visible card of one lane. */
    onSelectAll: (String, List<WorkItemID>) -> Unit = { _, _ -> },
    /** Leaves select mode and drops every tick. */
    onClearSelection: () -> Unit = {},
    /** Moves the selection into one lane id. */
    onMoveSelection: (String) -> Unit = {},
    /** Archives the selection: closing a Done lane, filing away a live one. */
    onArchiveSelection: () -> Unit = {},
) {
    val dragState = remember { BoardDragState() }

    // Screen state, not a preference: both shells render this same screen, so the query belongs
    // where the destination is rather than in `ShellPreferences`. Leaving the board and coming
    // back starts from an empty field, which is what a search box on a tab is expected to do.
    var query by remember { mutableStateOf("") }
    // Closed by default, and screen state for the same reason the query is: a board is mostly
    // read, not searched, so an always-open field spends a row of the header on the rarer
    // activity. Leaving the destination and coming back starts closed, which matches the empty
    // query it starts with.
    var searchOpen by remember { mutableStateOf(false) }
    // The compact overlay stops before its toggle, so the close control stays tappable. Measured
    // rather than inferred: the assignee stack and switcher are optional, so the toggle's place
    // in the header row is not a fixed sum of known widths.
    var searchToggleLeftPx by remember { mutableStateOf(0) }
    // The "Move to" picker's visibility. Screen state like the query: it is a visit, not a
    // preference, and dismissing it must not touch the selection.
    var moveSheetOpen by remember { mutableStateOf(false) }
    val appliedQuery = rememberDebouncedQuery(query)
    // Keyed on the projection *instance* as well as the query: the model replaces the projection
    // on every reload and on every My Tasks toggle, and a filter remembered against the query
    // alone would keep showing the previous load's cards.
    val shown = remember(projection, appliedQuery) { projection.matching(appliedQuery) }
    val focusManager = LocalFocusManager.current

    /**
     * Enters select mode on [laneID], closing the search first.
     *
     * A query can hide a card, and a hidden card that stays ticked is a count the user cannot
     * account for — the same rule the backlog's entry point follows. The field is not composed
     * again until the mode ends, so no filter can be applied halfway through a selection.
     */
    fun enterSelecting(laneID: String) {
        if (query.isNotEmpty()) {
            query = ""
            focusManager.clearFocus()
        }
        searchOpen = false
        onStartSelecting?.invoke(laneID)
    }

    // Every column but the selection's own: moving a card into the lane it is already in is a
    // command that changes nothing, so it is not offered as a destination.
    val moveDestinations = shown.columns
        .map { it.status }
        .filter { it.id != selectionLane }

    ClipboardCopyHost(modifier = modifier) { onCopy ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Measured without the gutters below, so the threshold is the one the backlog uses: the
            // desktop header needs 700dp of room for the project identity, the field and the two
            // actions before it stops fitting on one row.
            val wideHeader = maxWidth >= BoardHeaderWideMinimumWidth + ttSpacing.xxl * 2
            val compactSearchFieldWidth = with(LocalDensity.current) {
                (searchToggleLeftPx - ttSpacing.xxl.roundToPx() - ttSpacing.sm.roundToPx()).toDp()
            }.coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = ttSpacing.screenGutterLarge)
                    .testTag(ProjectBoardScreenTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                val assignees = boardAssigneeNames(projection.items, assigneeNames)
                val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    ttSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = stringResource(Res.string.work_item_search_placeholder),
                        modifier = fieldModifier.testTag(ProjectBoardSearchFieldTag),
                        fieldModifier = Modifier.testTag(ProjectBoardSearchInputTag),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                }
                // The bar is grouped with the header rather than left as a sibling: the outer
                // Column's `spacedBy` counts every child, so a composed-but-empty bar would spend a
                // gap even with the mode off. Inside this column the bar's own top padding supplies
                // the gap only while the reveal has height to give.
                Column {
                    // A wide field is taller than the 44dp icon buttons. Fixing the slot at the
                    // field's height means opening search cannot grow the header and push the
                    // lanes down; centring keeps the toggle on one line in both states.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalGutter()
                            .then(if (wideHeader) Modifier.height(ttSpacing.fieldMinHeight) else Modifier)
                            .testTag(ProjectBoardHeaderTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            // The trailing controls keep their order: the search toggle is inserted
                            // *before* My Tasks rather than after it, so the button that was already
                            // here does not move when this one appears. No toggle without a project —
                            // it would reveal a field over "no project selected", which filters a list
                            // that does not exist.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (projection.project != null && !isSelecting) {
                                    // On a wide header the field unfolds *beside* its button, inside the
                                    // trailing row: it takes width from the title rather than pushing
                                    // the lanes down, which is the same arrangement the backlog header
                                    // uses. A fixed width, because this row wraps its content — a
                                    // weighted child in a wrap-content row receives no space to grow
                                    // into.
                                    if (wideHeader) {
                                        SearchRevealBeside(visible = searchOpen) {
                                            searchField(Modifier.width(BoardSearchFieldWidth))
                                        }
                                    }
                                    ttGlassIconButton(
                                        image = ttActionIcons.search,
                                        contentDescription = stringResource(
                                            if (searchOpen) {
                                                Res.string.work_item_search_close
                                            } else {
                                                Res.string.work_item_search_open
                                            },
                                        ),
                                        onClick = {
                                            searchOpen = !searchOpen
                                            // Closing is also a reset. A query that outlives its field
                                            // would leave the board filtered by a control that is no
                                            // longer on screen, with the lanes simply missing cards and
                                            // nothing to explain why.
                                            if (!searchOpen) {
                                                query = ""
                                                focusManager.clearFocus()
                                            }
                                        },
                                        tint = if (searchOpen) ttAccent() else ttLabel(),
                                        modifier = Modifier
                                            .onGloballyPositioned { searchToggleLeftPx = it.boundsInRoot().left.toInt() }
                                            .testTag(ProjectBoardSearchToggleTag),
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
                        }
                        // A compact field is an overlay in this row, not another child of the
                        // outer column: giving it height would move the lanes on every frame of
                        // the reveal. Its measured width stops it before the close toggle.
                        if (projection.project != null && !wideHeader && !isSelecting) {
                            SearchReveal(
                                visible = searchOpen,
                                modifier = Modifier.matchParentSize(),
                                overlay = true,
                            ) { fieldModifier ->
                                Box(fieldModifier.width(compactSearchFieldWidth)) {
                                    searchField(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    // Only with a project on screen: a field over "no project selected" would
                    // filter nothing and offer a control whose list does not exist yet.
                    // Immediately under the header row and above the banner, so the header's own
                    // order is identity first, then the control over the list it filters.
                    // The selection bar stands in the header's second row for the same reason the
                    // backlog's does: the count and the two verbs need the width, and both lanes'
                    // headers are one control row already. It unfolds rather than popping in, so
                    // the lanes below keep their place while the new row is made.
                    SelectionBarReveal(visible = isSelecting) {
                        BoardSelectionActions(
                            count = selection.size,
                            isBusy = isBatchRunning,
                            canMove = moveDestinations.isNotEmpty(),
                            onMove = { moveSheetOpen = true },
                            onArchive = onArchiveSelection,
                            onClear = onClearSelection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalGutter()
                                .padding(top = ttSpacing.lg),
                        )
                    }
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
                    val leadingChromeInset = LocalLeadingChromeScrollUnderInset.current
                    val gutterDp = ttSpacing.xxl + leadingChromeInset
                    val scrollState = rememberScrollState()
                    val laneCount = shown.columns.size
                    val flingBehavior = rememberLaneSnapFlingBehavior(scrollState, laneCount)
                    val density = LocalDensity.current
                    val gutterPx = with(density) { gutterDp.toPx() }
                    val stridePx = with(density) { boardLaneStride.toPx() }
                    val laneWidthPx = with(density) { columnWidth.toPx() }
                    val edgePx = with(density) { 56.dp.toPx() }
                    val scrollStepPx = with(density) { 10.dp.toPx() }

                    var viewport by remember { mutableStateOf(Rect.Zero) }

                    /** Maps pointer offset to target column status key, or null if outside drop boundaries. */
                    fun laneAt(pointer: Offset): String? {
                        val row = viewport
                        if (row.width <= 0f || pointer.y < row.top || pointer.y > row.bottom) return null
                        val contentX = pointer.x - row.left - gutterPx + scrollState.value
                        if (contentX < 0f) return null
                        val index = floor(contentX / stridePx).toInt()
                        if (index !in 0 until laneCount) return null
                        return if (contentX - index * stridePx <= laneWidthPx) {
                            shown.columns[index].status.id
                        } else {
                            null
                        }
                    }

                    val drag = BoardCardDrag(
                        draggingItemID = dragState.item?.id,
                        onStart = { item, bounds, grab ->
                            // Terminal items cannot be dragged onto the board; a batch in flight
                            // owns the cards; and a selection is built by taps, not drags.
                            if (movingItemID == null && !item.status.isTerminal && !isSelecting) {
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
                                // The card's lane is the catalog entry it resolves to, or its
                                // category's key on a catalog-free board: comparing against the
                                // raw statusKey alone makes a card with no key look like it moved.
                                val currentLane = shown.project?.statusFor(dragged)?.id
                                    ?: dragged.statusKey
                                    ?: dragged.status.wireValue
                                if (target != null && target != currentLane) {
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
                            .then(
                                if (leadingChromeInset > 0.dp) {
                                    Modifier.layout { measurable, constraints ->
                                        val extraWidth = leadingChromeInset.roundToPx()
                                        val placeable = measurable.measure(
                                            constraints.copy(
                                                maxWidth = constraints.maxWidth + extraWidth,
                                            ),
                                        )
                                        layout(placeable.width - extraWidth, placeable.height) {
                                            placeable.placeRelative(-extraWidth, 0)
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
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
                            LaneRowSpacer(gutterDp)
                            shown.columns.forEachIndexed { index, column ->
                                if (index > 0) LaneRowSpacer(ttSpacing.md)
                                BoardColumnView(
                                    column = column,
                                    onSelect = { onSelect(it.id) },
                                    onCopy = onCopy,
                                    onAddTask = if (canAddTask && !isSelecting) {
                                        onAddTask?.let { addTask -> { addTask(column.status.id) } }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier,
                                    assigneeName = { item ->
                                        item.assigneeID?.let { assigneeNames[it] }
                                    },
                                    blockedByKeys = { item -> blockedByKeys[item.id].orEmpty() },
                                    projectTags = projection.project?.tags.orEmpty(),
                                    isSelecting = isSelecting,
                                    isSelectionLane = selectionLane == column.status.id,
                                    selection = selection,
                                    onStartSelecting = if (onStartSelecting != null) {
                                        { enterSelecting(column.status.id) }
                                    } else {
                                        null
                                    },
                                    onToggleSelected = { itemID ->
                                        onToggleSelected(column.status.id, itemID)
                                    },
                                    onSelectAll = { itemIDs ->
                                        onSelectAll(column.status.id, itemIDs)
                                    },
                                    onClearSelection = onClearSelection,
                                    cardTrailing = { item ->
                                        BoardCardActions(
                                            item = item,
                                            movingItemID = movingItemID,
                                            showMoveActions = showMoveActions,
                                            onFileAway = onFileAway,
                                            onMove = onMove,
                                            destinations = shown.columns.map { it.status },
                                        )
                                    },
                                    // No drag while selecting: a lifted card would leave the lane
                                    // whose set is being built, and the long-press that lifts it is
                                    // also the gesture a user might mean as a select.
                                    drag = if (isSelecting) null else drag,
                                    isDropTarget = dragState.target == column.status.id,
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
                                        destinations = shown.columns.map { it.status },
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

            // The "Move to" picker: same sheet shape as the backlog's, different title and body.
            // `visible` tracks `moveSheetOpen`, so dismissing it leaves the selection untouched
            // and the user can choose Archive instead.
            if (moveSheetOpen) {
                PutOnBoardSheet(
                    title = stringResource(Res.string.board_move_prompt_title),
                    body = stringResource(Res.string.board_move_prompt_body, selection.size),
                    statuses = moveDestinations,
                    onConfirm = { definition ->
                        onMoveSelection(definition.id)
                        moveSheetOpen = false
                    },
                    onDismissRequest = { moveSheetOpen = false },
                )
            }
        }
    }
}

/**
 * The action bar shown under the header while a lane's selection is being built.
 *
 * The count is a live region so a screen reader announces it as cards are ticked, and it is the
 * only thing naming the set. Both verbs refuse at zero and while a batch runs, so the bar never
 * reflows under the finger that just unticked the last card; "Move to" also refuses when there
 * is nowhere else to move.
 */
@Composable
private fun BoardSelectionActions(
    count: Int,
    isBusy: Boolean,
    canMove: Boolean,
    onMove: () -> Unit,
    onArchive: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = count > 0 && !isBusy
    Row(
        modifier = modifier.testTag(ProjectBoardSelectionBarTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = stringResource(Res.string.board_selection_count, count),
            style = ttTypography.caption,
            color = ttLabel(),
            modifier = Modifier
                .testTag(ProjectBoardSelectionCountTag)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (isBusy) {
            ttText(
                text = stringResource(Res.string.board_selection_working),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
        Spacer(Modifier.weight(1f))
        ttGlassButton(
            label = stringResource(Res.string.board_move_selection),
            onClick = { if (enabled && canMove) onMove() },
            modifier = Modifier.testTag(ProjectBoardSelectionMoveTag),
            style = if (enabled && canMove) {
                TTGlassButtonStyle(tint = ttAccent(), prominent = true)
            } else {
                TTGlassButtonStyle()
            },
        )
        ttGlassButton(
            label = stringResource(Res.string.board_archive_selection),
            onClick = { if (enabled) onArchive() },
            modifier = Modifier.testTag(ProjectBoardSelectionArchiveTag),
            style = if (enabled) TTGlassButtonStyle(tint = ttAccent(), prominent = true) else TTGlassButtonStyle(),
        )
        ttGlassIconButton(
            image = ttActionIcons.close,
            contentDescription = stringResource(Res.string.board_selection_clear),
            onClick = onClear,
            modifier = Modifier.testTag(ProjectBoardSelectionClearTag),
        )
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
    var target by mutableStateOf<String?>(null)
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

    fun retarget(status: String?) {
        if (item != null) target = status
    }

    fun end(): Pair<WorkItem, String?>? {
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
    onMove: (WorkItemID, String) -> Unit,
    destinations: List<StatusDefinition>,
) {
    // `Closed` has no next step and the archive refuses it; `Done` does, and closing it is what
    // lets the Done lane be cleared from the card as well as from a selection.
    if (item.status == WorkItemStatus.Closed) return
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
                destinations = destinations,
                item = item,
                onMove = { key -> onMove(item.id, key) },
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
    destinations: List<StatusDefinition>,
    item: WorkItem,
    onMove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(BoardMoveActionsTag(item)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
    ) {
        destinations.filter { it.id != item.statusKey }.forEach { definition ->
            ttGlassButton(
                label = statusLabel(definition),
                onClick = { onMove(definition.id) },
                modifier = Modifier.testTag(BoardMoveTag(item, definition.id)),
                style = TTGlassButtonStyle(tint = ttAccent()),
            )
        }
    }
}

const val ProjectBoardScreenTag = "board.screen"
const val ProjectBoardHeaderTag = "board.header"
const val ProjectBoardColumnsTag = "board.columns"
const val ProjectBoardMyTasksTag = "board.myTasks"
const val ProjectBoardSearchToggleTag = "board.search.toggle"
const val ProjectBoardTitleTag = "board.title"
const val ProjectBoardSearchFieldTag = "board.search"
const val ProjectBoardSearchInputTag = "board.search.input"
const val ProjectBoardEmptySearchTag = "board.emptySearch"
const val ProjectBoardSelectionBarTag = "board.selection.bar"
const val ProjectBoardSelectionCountTag = "board.selection.count"
const val ProjectBoardSelectionMoveTag = "board.selection.move"
const val ProjectBoardSelectionArchiveTag = "board.selection.archive"
const val ProjectBoardSelectionClearTag = "board.selection.clear"
const val BoardFileAwayTagPrefix = "board.card.fileAway"
const val BoardMoveActionsTagPrefix = "board.card.moveActions"

fun BoardFileAwayTag(item: WorkItem): String = "$BoardFileAwayTagPrefix.${item.key}"
fun BoardMoveActionsTag(item: WorkItem): String = "$BoardMoveActionsTagPrefix.${item.key}"
fun BoardMoveTag(item: WorkItem, statusKey: String): String =
    "${BoardMoveActionsTag(item)}.$statusKey"

private val MIN_FLANGE_VELOCITY_DP = 400.dp

/**
 * The width at which the board header keeps the project identity and the two actions on one
 * row with room for a field between them. The backlog header uses the same number, so the two
 * screens switch arrangement together.
 */
private val BoardHeaderWideMinimumWidth = 700.dp

/** How much of a wide header the field takes when open: a search box, not a second title. */
private val BoardSearchFieldWidth = 300.dp