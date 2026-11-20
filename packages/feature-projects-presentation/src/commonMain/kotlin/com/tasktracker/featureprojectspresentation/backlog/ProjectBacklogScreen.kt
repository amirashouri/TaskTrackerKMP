package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.designsystem.ttSearchField
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.featureprojectsdomain.BacklogSection
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.resources.Res
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.resources.backlog_archived_empty_body
import com.tasktracker.resources.backlog_archived_empty_title
import com.tasktracker.resources.backlog_empty_body
import com.tasktracker.resources.backlog_empty_title
import com.tasktracker.resources.backlog_kanban_empty_body
import com.tasktracker.resources.backlog_kanban_empty_title
import com.tasktracker.resources.backlog_put_on_board_prompt_body
import com.tasktracker.resources.backlog_put_on_board_prompt_body_many
import com.tasktracker.resources.backlog_section_archived
import com.tasktracker.resources.backlog_section_backlog
import com.tasktracker.resources.backlog_section_kanban
import com.tasktracker.resources.backlog_selection_archive
import com.tasktracker.resources.backlog_selection_clear
import com.tasktracker.resources.backlog_selection_count
import com.tasktracker.resources.backlog_selection_put_on_board
import com.tasktracker.resources.backlog_selection_working
import com.tasktracker.resources.backlog_select_open
import com.tasktracker.resources.board_add_task
import com.tasktracker.resources.work_item_search_close
import com.tasktracker.resources.work_item_search_empty_body
import com.tasktracker.resources.work_item_search_empty_title
import com.tasktracker.resources.work_item_search_open
import com.tasktracker.resources.work_item_search_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * Backlog screen: renders untriaged work, active kanban tasks, and archived items in 3 tabs.
 * The header floats over the list; list top content padding is dynamically derived from header height.
 *
 * ## The sort order is the host's, and the screen only reports taps
 *
 * [sortOrder] seeds the selection and [onSortOrderChanged] reports every change to it. That
 * pair exists because the ordering is a *preference* rather than screen state: a `remember`
 * here died with the destination, so leaving the tab and coming back — or relaunching —
 * silently reset the list to priority-first. The host owns the value (the shell's
 * `ShellPreferences` keeps it in memory and on disk) and hands it down; this screen keeps a
 * local copy only so the list re-reads in the same frame as the tap, and re-seeds from the
 * parameter whenever the host hands back a different one.
 *
 * Both are defaulted, so a caller that does not persist anything — a preview, a focused
 * screen test — still gets a working, self-contained list.
 *
 * ## Selection is the host's too
 *
 * [isSelecting] and [selection] are model state passed down, and every gesture reports through a
 * callback rather than ticking a local copy: rows, the header's Select control and the action bar
 * all have to agree, and a local set would drift the moment the model pruned it after a batch.
 * The two entry points — the header control and a long-press on a row — both go through the same
 * `enterSelecting` scope, which also closes the search: a query that hides a row must never leave
 * it ticked and invisible.
 */
@Composable
fun ProjectBacklogScreen(
    projection: BacklogSectionedProjection,
    isLoading: Boolean,
    errorMessage: String?,
    busyItemID: WorkItemID?,
    onSelect: (WorkItemID) -> Unit,
    onPutOnBoard: (WorkItemID, String?) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onUnarchive: (WorkItemID) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSwitcher: (() -> Unit)? = null,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    onAddTask: (() -> Unit)? = null,
    sortOrder: BacklogSortOrder = BacklogSortOrder(),
    onSortOrderChanged: (BacklogSortOrder) -> Unit = {},
    /**
     * The shell-remembered "Put on board" target for this project, or null when none has been
     * chosen. Defaulted so a preview or focused screen test still boards a task without the shell.
     * See the screen's resolver: a remembered key the catalog no longer contains is treated as
     * null and the user is re-asked.
     */
    putOnBoardDefaultStatusKey: String? = null,
    /** Persists the column the user chose, so the next boarding in this project skips the prompt. */
    onPutOnBoardDefaultStatusChosen: (String) -> Unit = {},
    /** True while the model is in multi-select mode. */
    isSelecting: Boolean = false,
    /** The ticked items. Membership is what draws each row's box. */
    selection: Set<WorkItemID> = emptySet(),
    /** True while a batch command runs; the action bar disables itself and says so. */
    isBatchRunning: Boolean = false,
    /** Enters select mode from the header. Null draws no Select control (previews, locked hosts). */
    onStartSelecting: (() -> Unit)? = null,
    /** Ticks or unticks one row. */
    onToggleSelected: (WorkItemID) -> Unit = {},
    /** Leaves select mode and drops every tick. */
    onClearSelection: () -> Unit = {},
    /** Boards every ticked item into one lane; `null` is the legacy `Todo` lane. */
    onPutSelectionOnBoard: (String?) -> Unit = {},
    /** Files every ticked item away. */
    onArchiveSelection: () -> Unit = {},
) {
    val tabBarInset = LocalTabBarScrollUnderInset.current
    var currentSection by remember { mutableStateOf(projection.currentSection) }
    // Keyed on the parameter so a restored or externally changed order replaces this copy,
    // while a tap's own echo back from the host is an equal value and leaves it alone.
    var currentSortOrder by remember(sortOrder) { mutableStateOf(sortOrder) }

    // Screen state, like `currentSection`: it survives a segment switch and a sort change, and
    // it is gone when the destination is. Deliberately not a `ShellPreferences` value — the
    // backlog sort is remembered because it is a preference about how the user likes to read a
    // list; a query is about right now.
    var query by remember { mutableStateOf("") }
    // Closed by default and screen state, like the query: a list is mostly read, and the field
    // returns closed on the next visit rather than remembering that it was open once.
    var searchOpen by remember { mutableStateOf(false) }
    // The sort sheet's visibility. Screen state for the same reason: it is a visit, not a
    // preference — the order it edits is the preference, and that is reported on every change.
    var sortSheetOpen by remember { mutableStateOf(false) }
    val appliedQuery = rememberDebouncedQuery(query)
    // Keyed on the projection instance as well as the query: the model replaces the projection
    // on every reload, and a filter remembered against the query alone would keep showing the
    // previous load's rows.
    val shown = remember(projection, appliedQuery) { projection.matching(appliedQuery) }
    val focusManager = LocalFocusManager.current

    // The board columns this project exposes. Empty when the project has no catalog, in which case
    // boarding falls back to the legacy `Todo` lane with no key.
    val boardStatuses = projection.project?.boardStatuses.orEmpty()

    // The request waiting for a column choice, or null when the picker is closed. Set by the
    // resolver below only when it cannot decide on its own — i.e. several columns and no usable
    // default. One sheet serves the single row and the selection: the question is the same one.
    var pendingPutOnBoard by remember { mutableStateOf<BacklogPutOnBoardRequest?>(null) }

    fun commitPutOnBoard(item: WorkItem, definition: StatusDefinition?) {
        onPutOnBoard(item.id, definition?.id)
        definition?.id?.let(onPutOnBoardDefaultStatusChosen)
    }

    /** Commits the selection to [definition], remembering it when the project has a catalog. */
    fun commitPutSelectionOnBoard(definition: StatusDefinition?) {
        onPutSelectionOnBoard(definition?.id)
        definition?.id?.let(onPutOnBoardDefaultStatusChosen)
    }

    /**
     * The lane decision shared by the single row and the selection.
     *
     * No board columns at all is the legacy catalog-less project: `Todo`, no key, no prompt.
     * One column needs no question. A remembered default still in the catalog is the user's own
     * earlier answer. Anything else is a genuine choice, and only then does the sheet open.
     */
    fun chooseLane(): BacklogLaneChoice {
        val remembered = putOnBoardDefaultStatusKey
        return when {
            boardStatuses.isEmpty() -> BacklogLaneChoice.Apply(null)
            boardStatuses.size == 1 -> BacklogLaneChoice.Apply(boardStatuses.first())
            remembered != null && boardStatuses.any { it.id == remembered } ->
                BacklogLaneChoice.Apply(boardStatuses.first { it.id == remembered })
            else -> BacklogLaneChoice.Ask
        }
    }

    /**
     * Enters select mode, closing the search first.
     *
     * A query can hide a row, and a hidden row that stays ticked is a count the user cannot
     * account for, so the mode starts with the whole backlog on screen — the field closes and its
     * query is dropped. The field is not composed again until the mode ends, which is what keeps
     * a filter from being applied halfway through a selection.
     */
    fun enterSelecting() {
        if (query.isNotEmpty()) {
            query = ""
            focusManager.clearFocus()
        }
        onStartSelecting?.invoke()
    }

    /** A row tap or long-press while selecting: the first enters the mode, both tick the row. */
    fun toggleRow(item: WorkItem) {
        if (!isSelecting) enterSelecting()
        onToggleSelected(item.id)
    }

    /**
     * Updates the list first, reports second: the rows have to re-read in the same frame as the
     * change, and the report is what remembers the order, which may outlive this screen.
     */
    fun commitSortOrder(next: BacklogSortOrder) {
        currentSortOrder = next
        onSortOrderChanged(next)
    }

    /**
     * Decides where a backlog item lands when "Put on board" is tapped, then asks the model.
     *
     * - No catalog (or none of its statuses appear on the board): legacy `Todo`, null key.
     * - Exactly one board column: that column, with no prompt.
     * - A remembered default the catalog still contains: that column, with no prompt.
     * - Anything else — several columns and no usable default — opens the picker for an on-tap choice.
     *
     * The remembered default is the shell preference [putOnBoardDefaultStatusKey]; when we commit a
     * choice we also report it through [onPutOnBoardDefaultStatusChosen] so the shell persists it
     * and the next boarding in this project skips the prompt.
     */
    fun requestPutOnBoard(item: WorkItem) {
        when (val choice = chooseLane()) {
            is BacklogLaneChoice.Apply -> commitPutOnBoard(item, choice.definition)
            BacklogLaneChoice.Ask -> pendingPutOnBoard = BacklogPutOnBoardRequest.Single(item)
        }
    }

    /** The selection's version of the same resolution, with one lane for the whole set. */
    fun requestPutSelectionOnBoard() {
        when (val choice = chooseLane()) {
            is BacklogLaneChoice.Apply -> commitPutSelectionOnBoard(choice.definition)
            BacklogLaneChoice.Ask -> pendingPutOnBoard = BacklogPutOnBoardRequest.Selection(selection.size)
        }
    }

    /**
     * A query edit can hide a ticked row, and a hidden tick is a count the user cannot account
     * for, so editing the field drops the selection. It is the field's own callback rather than
     * an effect on the debounced query: entering select mode resets the query itself, and an
     * effect would read that reset as an edit and close the mode that just opened.
     */
    val updateQuery: (String) -> Unit = { next ->
        if (next != query) {
            if (selection.isNotEmpty()) onClearSelection()
            query = next
        }
    }

    // Measured dynamically to accommodate conditional banners and variable typography scaling
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val firstRowInset = with(LocalDensity.current) {
        headerHeightPx.toDp()
    } + ttSpacing.lg

    // One scroll position per segment: the housing has to read the list that is actually on
    // screen, and a shared position would carry the backlog's offset onto the archive.
    val backlogListState = rememberLazyListState()
    val kanbanListState = rememberLazyListState()
    val archivedListState = rememberLazyListState()
    val activeListState = when (currentSection) {
        BacklogSection.Backlog -> backlogListState
        BacklogSection.Kanban -> kanbanListState
        BacklogSection.Archived -> archivedListState
    }

    // True only while a row's top has passed the housing's bottom edge. Rows rest one `lg`
    // below the card, so at scroll position zero nothing is behind it and the housing stays
    // absent — the effect marks content passing underneath, not the header's presence.
    //
    // `LazyListItemInfo.offset` is measured from the viewport minus the list's content padding,
    // not from the top of the screen: at rest the first row reports 0 even though it is drawn
    // below the header. So the card's bottom edge is the reserved `lg` gap, and a row is under
    // the housing once it has been scrolled up into that gap.
    val rowGapPx = with(LocalDensity.current) { ttSpacing.lg.toPx() }
    val rowsUnderHeader by remember(activeListState) {
        derivedStateOf {
            val firstVisible = activeListState.layoutInfo.visibleItemsInfo.firstOrNull()
            firstVisible != null && firstVisible.offset.toFloat() < -rowGapPx
        }
    }
    val housingAlpha by animateFloatAsState(
        targetValue = if (rowsUnderHeader) 1f else 0f,
        animationSpec = if (LocalReducedMotion.current) {
            snap()
        } else {
            tween(durationMillis = HEADER_HOUSING_FADE_MILLIS)
        },
        label = "backlogHeaderHousing",
    )

    ClipboardCopyHost(
        modifier = modifier.testTag(ProjectBacklogScreenTag),
    ) { onCopy ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= BacklogWideMinimumWidth
            val listContentModifier = if (isWide) {
                Modifier
                    .widthIn(max = BacklogMaxContentWidth)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = ttSpacing.xxl)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = ttSpacing.xxl)
            }
            val headerWidthModifier = if (isWide) {
                Modifier.widthIn(max = BacklogMaxContentWidth)
            } else {
                Modifier.fillMaxWidth()
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                // Tags live on each list individually: testTag overwrites rather than merges in Compose
                when (currentSection) {
                    BacklogSection.Backlog -> BacklogSectionList(
                        projection = shown,
                        listState = backlogListState,
                        sortOrder = currentSortOrder,
                        busyItemID = busyItemID,
                        isLoading = isLoading,
                        searchQuery = appliedQuery,
                        onSelect = onSelect,
                        onPutOnBoard = { requestPutOnBoard(it) },
                        onFileAway = onFileAway,
                        onCopy = onCopy,
                        onAddTask = onAddTask,
                        isSelecting = isSelecting,
                        selection = selection,
                        onToggleRow = { toggleRow(it) },
                        contentTopInset = firstRowInset,
                        tabBarInset = tabBarInset,
                        modifier = listContentModifier,
                    )
                    BacklogSection.Kanban -> KanbanSectionList(
                        projection = shown,
                        listState = kanbanListState,
                        busyItemID = busyItemID,
                        isLoading = isLoading,
                        searchQuery = appliedQuery,
                        onSelect = onSelect,
                        onFileAway = onFileAway,
                        onCopy = onCopy,
                        onAddTask = onAddTask,
                        contentTopInset = firstRowInset,
                        tabBarInset = tabBarInset,
                        modifier = listContentModifier,
                    )
                    BacklogSection.Archived -> ArchivedSectionList(
                        projection = shown,
                        listState = archivedListState,
                        busyItemID = busyItemID,
                        isLoading = isLoading,
                        searchQuery = appliedQuery,
                        onSelect = onSelect,
                        onUnarchive = onUnarchive,
                        onCopy = onCopy,
                        contentTopInset = firstRowInset,
                        modifier = listContentModifier,
                    )
                }

                // The header's housing card. It is always in the layout but only painted while a row
                // is behind it, so its appearance shifts nothing. The card's outer inset and its own
                // inner padding add up to `xxl`, keeping every control on the leading edge the rows
                // use; the bottom inset is extra room the list's measured top inset accounts for.
                Box(
                    modifier = headerWidthModifier
                        .onSizeChanged { headerHeightPx = it.height }
                        .padding(horizontal = ttSpacing.md)
                        .padding(top = ttSpacing.screenGutterLarge - ttSpacing.md),
                ) {
                    if (housingAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(housingAlpha)
                                // Thin, because the trackers inside are already glass: this is a
                                // backdrop for them, not a second full-strength surface.
                                .ttGlassSurface(
                                    shape = RoundedCornerShape(ttRadius.surface),
                                    thickness = ttGlass.GlassThickness.THIN,
                                )
                                .testTag(ProjectBacklogHeaderHousingTag),
                        )
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // A wide window puts the section picker on the selector's row and the search
                        // field beside the sort row; below this width there is not enough room for the
                        // picker and the two icon actions without squeezing the project name, so the
                        // header keeps its stacked phone arrangement.
                        val wideHeader = maxWidth >= BacklogHeaderWideMinimumWidth
                        val sectionPicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                            TTGlassSegmentedPicker(
                                options = listOf(BacklogSection.Backlog, BacklogSection.Kanban, BacklogSection.Archived),
                                selection = currentSection,
                                onSelect = {
                                    currentSection = it
                                    // Archived rows are history, never a selection target.
                                    if (it != BacklogSection.Backlog) onClearSelection()
                                },
                                modifier = pickerModifier.testTag(ProjectBacklogSectionsTag),
                                title = { option ->
                                    when (option) {
                                        BacklogSection.Backlog -> stringResource(Res.string.backlog_section_backlog)
                                        BacklogSection.Kanban -> stringResource(Res.string.backlog_section_kanban)
                                        BacklogSection.Archived -> stringResource(Res.string.backlog_section_archived)
                                    }
                                },
                                testTag = { option ->
                                    when (option) {
                                        BacklogSection.Backlog -> SectionIndicatorTag(stringResource(Res.string.backlog_section_backlog))
                                        BacklogSection.Kanban -> SectionIndicatorTag(stringResource(Res.string.backlog_section_kanban))
                                        BacklogSection.Archived -> SectionIndicatorTag(stringResource(Res.string.backlog_section_archived))
                                    }
                                },
                            )
                        }
                        val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
                            ttSearchField(
                                value = query,
                                onValueChange = updateQuery,
                                hint = stringResource(Res.string.work_item_search_placeholder),
                                modifier = fieldModifier
                                    .heightIn(min = BacklogFilterRowHeight)
                                    .testTag(ProjectBacklogSearchFieldTag),
                                fieldModifier = Modifier.testTag(ProjectBacklogSearchInputTag),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            )
                        }
                        val searchToggle: @Composable (Modifier) -> Unit = { toggleModifier ->
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
                                    // Closing resets the query too, for the reason the board's doc gives:
                                    // a filter that outlives its field leaves rows missing with nothing
                                    // on screen to say why.
                                    if (!searchOpen) {
                                        updateQuery("")
                                        focusManager.clearFocus()
                                    }
                                },
                                tint = if (searchOpen) ttAccent() else ttLabel(),
                                modifier = toggleModifier.testTag(ProjectBacklogSearchToggleTag),
                            )
                        }
                        val openSortSheet: () -> Unit = { sortSheetOpen = true }
                        // The header's way in and out of select mode. One control, two states:
                        // entering and leaving are the same gesture in the same place, so a user
                        // who entered by accident does not have to hunt for the exit.
                        val selectAction: @Composable (Modifier) -> Unit = { actionModifier ->
                            val showSelect = currentSection == BacklogSection.Backlog &&
                                onStartSelecting != null &&
                                (isSelecting || !shown.backlogIsEmpty)
                            if (showSelect) {
                                if (isSelecting) {
                                    ttGlassIconButton(
                                        image = ttActionIcons.close,
                                        contentDescription = stringResource(Res.string.backlog_selection_clear),
                                        onClick = onClearSelection,
                                        modifier = actionModifier.testTag(ProjectBacklogSelectionClearTag),
                                    )
                                } else {
                                    ttGlassIconButton(
                                        image = ttActionIcons.select,
                                        contentDescription = stringResource(Res.string.backlog_select_open),
                                        onClick = { enterSelecting() },
                                        modifier = actionModifier.testTag(ProjectBacklogSelectTag),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ttSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
                        ) {
                            // One row whether or not there is a switcher to fill its left side: the search
                            // toggle has to be reachable either way, and the + keeps the trailing edge it
                            // has always had, so the toggle is inserted before it rather than after.
                            //
                            // On a wide header the section picker joins this row, between the project
                            // selector and the two icon actions: it is a destination switch, so it belongs
                            // beside the project it switches within, not below the actions.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                            ) {
                                if (onOpenSwitcher != null) {
                                    ProjectSwitcherButton(
                                        project = projection.project,
                                        onClick = onOpenSwitcher,
                                        // On a wide header the switcher wraps its own content and
                                        // the picker's weighted box takes the rest: weighting the
                                        // switcher instead would leave its unused share as a gap
                                        // *before* the box, so the picker would centre in the wrong
                                        // space. On a compact header it still fills the row.
                                        modifier = if (wideHeader) Modifier else Modifier.weight(1f),
                                        projects = projects,
                                        onSelectProject = onSelectProject,
                                    )
                                } else {
                                    Spacer(if (wideHeader) Modifier else Modifier.weight(1f))
                                }
                                if (wideHeader) {
                                    // Centred in the gap between the selector and the add action:
                                    // the box spans exactly that gap, so centring the capped picker
                                    // inside it leaves equal space at either end. The search toggle
                                    // is not in this row — it sits in the sort row's trailing slot
                                    // below, so the field grows where the toggle already was.
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        sectionPicker(Modifier)
                                    }
                                }
                                selectAction(Modifier)
                                if (!wideHeader && !isSelecting) {
                                    searchToggle(Modifier)
                                }
                                onAddTask?.let { addTask ->
                                    ttGlassIconButton(
                                        image = ttActionIcons.add,
                                        contentDescription = stringResource(Res.string.board_add_task),
                                        onClick = addTask,
                                        modifier = Modifier.testTag(BacklogAddTaskTag),
                                    )
                                }
                            }
                            errorMessage?.let { message ->
                                ttBanner(message = message, onDismiss = onDismissError)
                            }
                            // Compact header: the picker keeps its own row under the actions, the sort
                            // row fills the column, and the field unfolds below it. The wide branch below
                            // is not a wider version of this one — it rearranges the same controls.
                            if (!wideHeader) {
                                sectionPicker(Modifier)
                            }
                            if (wideHeader) {
                                // One row for every section, not one per section: the sort row is a
                                // Backlog control, but the search slot beside it is not. Keeping the row
                                // composed means switching to Kanban or Archived leaves the field in the
                                // row it was opened in, and it also lets the reveal animate in every
                                // section. With no sort row the spacer gives the field the full width,
                                // because there is nothing else in the row to share it with.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    // Centre, now that the sort row and the field share one height: the
                                    // toggle is the one shorter control, and centring it puts its glyph on
                                    // the row's centre line instead of four dp above it.
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                                ) {
                                    if (isSelecting) {
                                        BacklogSelectionActions(
                                            count = selection.size,
                                            isBusy = isBatchRunning,
                                            onPutOnBoard = { requestPutSelectionOnBoard() },
                                            onArchive = onArchiveSelection,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else if (currentSection == BacklogSection.Backlog) {
                                        BacklogSortControls(
                                            order = currentSortOrder,
                                            onOpen = openSortSheet,
                                            // Whatever the field and toggle do not take: closed, that
                                            // is the whole header width, which is what "fill the
                                            // width" means here.
                                            modifier = Modifier.weight(1f),
                                        )
                                        SearchRevealBeside(visible = searchOpen) {
                                            searchField(Modifier.fillMaxWidth(DESKTOP_SEARCH_FIELD_FRACTION))
                                        }
                                    } else {
                                        // No sort row: the field is the row, not a portion of it. The
                                        // weighted box gives it everything the toggle does not use,
                                        // and still lets the reveal travel from zero width. The leading
                                        // inset mirrors the row's gap to the toggle, so the field is
                                        // not flush against the header edge on one side and spaced on
                                        // the other.
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .padding(start = ttSpacing.sm),
                                        ) {
                                            SearchRevealBeside(visible = searchOpen) {
                                                searchField(Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                    // The toggle keeps the trailing edge, under the add action, and
                                    // becomes the close control while the field is open — it never
                                    // leaves the row, so the field grows beside it instead of over the
                                    // only way to dismiss the search.
                                    //
                                    // Hidden while selecting: the mode closed the query, and a search
                                    // field would be a filter that unticks rows the user just chose.
                                    if (!isSelecting) searchToggle(Modifier)
                                }
                            } else {
                                if (isSelecting) {
                                    BacklogSelectionActions(
                                        count = selection.size,
                                        isBusy = isBatchRunning,
                                        onPutOnBoard = { requestPutSelectionOnBoard() },
                                        onArchive = onArchiveSelection,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    if (currentSection == BacklogSection.Backlog) {
                                        BacklogSortControls(
                                            order = currentSortOrder,
                                            onOpen = openSortSheet,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    // Inside the measured column, so the list's top inset grows with the field
                                    // and the first row still starts below the header rather than behind it.
                                    // The reveal is the reason the inset is measured at all: it changes while
                                    // the field unfolds.
                                    SearchReveal(visible = searchOpen) {
                                        searchField(Modifier.widthIn(max = ttSpacing.pickerMaxWidth))
                                    }
                                }
                            }
                        }
                    }
                }
            // The sort sheet: one trigger in the header, and every key's state edited here.
            // Mounted on the screen rather than inside the header row so its scrim covers the
            // whole list, and so switching section cannot leave it stranded inside a row that
            // is no longer composed.
            BacklogSortSheet(
                order = currentSortOrder,
                onOrderChange = { next -> commitSortOrder(next) },
                onDismissRequest = { sortSheetOpen = false },
                visible = sortSheetOpen,
            )
            // The column picker, open only while the resolver has decided it cannot board without
            // a choice. `visible` tracks `pendingPutOnBoard`, so dismissing sets it null and the
            // sheet animates out on its own.
            pendingPutOnBoard?.let { request ->
                PutOnBoardSheet(
                    body = when (request) {
                        is BacklogPutOnBoardRequest.Single ->
                            stringResource(Res.string.backlog_put_on_board_prompt_body, request.item.title)
                        is BacklogPutOnBoardRequest.Selection ->
                            stringResource(Res.string.backlog_put_on_board_prompt_body_many, request.count)
                    },
                    statuses = boardStatuses,
                    onConfirm = { definition ->
                        when (request) {
                            is BacklogPutOnBoardRequest.Single -> commitPutOnBoard(request.item, definition)
                            is BacklogPutOnBoardRequest.Selection -> commitPutSelectionOnBoard(definition)
                        }
                        pendingPutOnBoard = null
                    },
                    onDismissRequest = { pendingPutOnBoard = null },
                )
            }
            }
        }
    }
}

@Composable
private fun BacklogSectionList(
    projection: BacklogSectionedProjection,
    listState: LazyListState,
    sortOrder: BacklogSortOrder,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    searchQuery: String,
    onSelect: (WorkItemID) -> Unit,
    onPutOnBoard: (WorkItem) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
    onAddTask: (() -> Unit)?,
    isSelecting: Boolean,
    selection: Set<WorkItemID>,
    onToggleRow: (WorkItem) -> Unit,
    contentTopInset: Dp,
    tabBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    val backlog = remember(projection.items, sortOrder) { projection.backlogIn(sortOrder) }
    if (!isLoading && backlog.isEmpty()) {
        if (searchQuery.isNotBlank()) {
            EmptySearchSection(
                modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptySearchTag),
            )
        } else {
            EmptyBacklogSection(
                modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptyBacklogTag),
                onAddTask = onAddTask,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.testTag(ProjectBacklogListTag),
            state = listState,
            contentPadding = PaddingValues(
                top = contentTopInset,
                bottom = ttSpacing.xl + tabBarInset,
            ),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(backlog, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = { onPutOnBoard(item) },
                    onFileAway = { onFileAway(item.id) },
                    isBusy = busyItemID == item.id,
                    onOpen = { onSelect(item.id) },
                    onCopy = onCopy,
                    projectTags = projection.project?.tags.orEmpty(),
                    isSelecting = isSelecting,
                    isSelected = item.id in selection,
                    onLongPress = { onToggleRow(item) },
                    onToggleSelected = { onToggleRow(item) },
                )
            }
        }
    }
}

@Composable
private fun KanbanSectionList(
    projection: BacklogSectionedProjection,
    listState: LazyListState,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    searchQuery: String,
    onSelect: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
    onAddTask: (() -> Unit)?,
    contentTopInset: Dp,
    tabBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.kanbanIsEmpty) {
        if (searchQuery.isNotBlank()) {
            EmptySearchSection(
                modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptySearchTag),
            )
        } else {
            EmptyKanbanSection(
                modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptyKanbanTag),
                onAddTask = onAddTask,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.testTag(ProjectBacklogKanbanListTag),
            state = listState,
            contentPadding = PaddingValues(
                top = contentTopInset,
                bottom = ttSpacing.xl + tabBarInset,
            ),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(projection.kanban, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = {},
                    onFileAway = { onFileAway(item.id) },
                    isBusy = busyItemID == item.id,
                    onOpen = { onSelect(item.id) },
                    onCopy = onCopy,
                    projectTags = projection.project?.tags.orEmpty(),
                    showStatusBadge = true,
                    project = projection.project,
                )
            }
        }
    }
}

@Composable
private fun ArchivedSectionList(
    projection: BacklogSectionedProjection,
    listState: LazyListState,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    searchQuery: String,
    onSelect: (WorkItemID) -> Unit,
    onUnarchive: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.archivedIsEmpty) {
        if (searchQuery.isNotBlank()) {
            EmptySearchSection(
                modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptySearchTag),
            )
        } else {
            EmptyArchivedSection(
                modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptyArchivedTag),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.testTag(ProjectBacklogArchivedListTag),
            state = listState,
            contentPadding = PaddingValues(
                top = contentTopInset,
                bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current,
            ),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(projection.archived, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = {},
                    onFileAway = {},
                    isBusy = busyItemID == item.id,
                    onOpen = { onSelect(item.id) },
                    onUnarchive = { onUnarchive(item.id) },
                    onCopy = onCopy,
                    projectTags = projection.project?.tags.orEmpty(),
                )
            }
        }
    }
}

/**
 * The state a segment shows when a query matched nothing in it.
 *
 * Deliberately not the segment's own empty copy: "The backlog is empty" is a statement about the
 * project, and it is false while a query is narrowing it. One state for all three segments,
 * because "no matches" is the same sentence in each and the way back is the same field.
 */
@Composable
private fun EmptySearchSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.work_item_search_empty_title),
        body = stringResource(Res.string.work_item_search_empty_body),
        modifier = modifier,
        image = ttActionIcons.search,
    )
}

@Composable
private fun EmptyBacklogSection(
    modifier: Modifier = Modifier,
    onAddTask: (() -> Unit)? = null,
) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_empty_title),
        body = stringResource(Res.string.backlog_empty_body),
        modifier = modifier,
        image = ttTabIcons.backlog,
        action = addTaskAction(onAddTask),
    )
}

@Composable
private fun EmptyKanbanSection(
    modifier: Modifier = Modifier,
    onAddTask: (() -> Unit)? = null,
) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_kanban_empty_title),
        body = stringResource(Res.string.backlog_kanban_empty_body),
        modifier = modifier,
        image = ttTabIcons.board,
        action = addTaskAction(onAddTask),
    )
}

/**
 * The one thing an empty backlog can offer: the create sheet the header's `+` already opens.
 *
 * A `@Composable` function rather than a lambda at each call site, because the action slot is
 * nullable and both sections need the identical button; a null [onAddTask] stays null, so a
 * host that locked creation (an archived project) gets no button rather than a dead one.
 */
@Composable
private fun addTaskAction(onAddTask: (() -> Unit)?): (@Composable () -> Unit)? {
    val addTask = onAddTask ?: return null
    return {
        ttGlassButton(
            label = stringResource(Res.string.board_add_task),
            onClick = addTask,
            style = TTGlassButtonStyle(prominent = true),
        )
    }
}

@Composable
private fun EmptyArchivedSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_archived_empty_title),
        body = stringResource(Res.string.backlog_archived_empty_body),
        modifier = modifier,
        image = ttActionIcons.archive,
    )
}

const val ProjectBacklogScreenTag = "backlog.screen"
const val ProjectBacklogListTag = "backlog.rows"
const val ProjectBacklogSectionsTag = "backlog.sections"
const val ProjectBacklogKanbanListTag = "backlog.kanbanRows"
const val ProjectBacklogArchivedListTag = "backlog.archivedRows"
const val ProjectBacklogEmptyBacklogTag = "backlog.emptyBacklog"
const val ProjectBacklogEmptyKanbanTag = "backlog.emptyKanban"
const val ProjectBacklogEmptyArchivedTag = "backlog.emptyArchived"
const val ProjectBacklogEmptySearchTag = "backlog.emptySearch"
const val ProjectBacklogSearchFieldTag = "backlog.search"
const val ProjectBacklogSearchToggleTag = "backlog.search.toggle"
const val ProjectBacklogSearchInputTag = "backlog.search.input"
const val BacklogAddTaskTag = "backlog.addTask"
const val ProjectBacklogHeaderHousingTag = "backlog.header.housing"
const val ProjectBacklogSelectTag = "backlog.select"
const val ProjectBacklogSelectionClearTag = "backlog.selection.clear"
const val ProjectBacklogSelectionBarTag = "backlog.selection.bar"
const val ProjectBacklogSelectionCountTag = "backlog.selection.count"
const val ProjectBacklogSelectionPutOnBoardTag = "backlog.selection.putOnBoard"
const val ProjectBacklogSelectionArchiveTag = "backlog.selection.archive"

/** What asked for a column: one row, or the whole selection. */
private sealed interface BacklogPutOnBoardRequest {
    data class Single(val item: WorkItem) : BacklogPutOnBoardRequest
    data class Selection(val count: Int) : BacklogPutOnBoardRequest
}

/** The lane the screen can commit on its own, or a genuine choice for the sheet. */
private sealed interface BacklogLaneChoice {
    /** [definition] null means the legacy `Todo` lane with no catalog key. */
    data class Apply(val definition: StatusDefinition?) : BacklogLaneChoice
    data object Ask : BacklogLaneChoice
}

/**
 * The action bar shown in the sort row's place while selecting.
 *
 * The count is a live region so a screen reader announces it as rows are ticked, and it is the
 * only thing that names the set — the buttons act on "the selection", which is whatever the count
 * says. Both actions are refused at zero rather than hidden, so the bar does not reflow under the
 * finger that just unticked the last row; while a batch runs they are refused too, and the bar
 * says what is happening instead of leaving two dead controls unexplained.
 */
@Composable
private fun BacklogSelectionActions(
    count: Int,
    isBusy: Boolean,
    onPutOnBoard: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = count > 0 && !isBusy
    Row(
        modifier = modifier.testTag(ProjectBacklogSelectionBarTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = stringResource(Res.string.backlog_selection_count, count),
            style = ttTypography.caption,
            color = ttLabel(),
            modifier = Modifier
                .testTag(ProjectBacklogSelectionCountTag)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (isBusy) {
            ttText(
                text = stringResource(Res.string.backlog_selection_working),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
        Spacer(Modifier.weight(1f))
        ttGlassButton(
            label = stringResource(Res.string.backlog_selection_put_on_board),
            onClick = { if (enabled) onPutOnBoard() },
            modifier = Modifier.testTag(ProjectBacklogSelectionPutOnBoardTag),
            style = if (enabled) TTGlassButtonStyle(tint = ttAccent(), prominent = true) else TTGlassButtonStyle(),
        )
        ttGlassButton(
            label = stringResource(Res.string.backlog_selection_archive),
            onClick = { if (enabled) onArchive() },
            modifier = Modifier.testTag(ProjectBacklogSelectionArchiveTag),
            style = if (enabled) TTGlassButtonStyle(tint = ttAccent(), prominent = true) else TTGlassButtonStyle(),
        )
    }
}

/** How long the header's housing takes to fade in or out as rows pass under it. */
private const val HEADER_HOUSING_FADE_MILLIS = 160

/** Minimum width at which the backlog switches to wide desktop layout clamping. */
val BacklogWideMinimumWidth: Dp = 840.dp

/** Maximum readable content width for the backlog list and floating header. */
val BacklogMaxContentWidth: Dp = 960.dp

/**
 * The header width at which the picker joins the selector's row.
 *
 * Above the picker's own `pickerMaxWidth` cap plus two icon actions and a usable project name.
 * The shell calls a window wide from 840dp; a sidebar takes 280dp of that, so the narrowest
 * wide window still leaves this much content column. Below it the header keeps the stacked
 * phone arrangement rather than squeezing the project name to a few characters.
 */
private val BacklogHeaderWideMinimumWidth = 700.dp

/**
 * The share of the desktop header row the search field takes when it is open.
 *
 * Large enough for a query with a clear button, small enough that the sort row it sits beside
 * still shows its summary. Expressed as a fraction rather than a width so the split travels with
 * the window instead of leaving a fixed gap on one side.
 */
private const val DESKTOP_SEARCH_FIELD_FRACTION = 0.42f

fun SectionIndicatorTag(label: String): String = "backlog.section.$label"