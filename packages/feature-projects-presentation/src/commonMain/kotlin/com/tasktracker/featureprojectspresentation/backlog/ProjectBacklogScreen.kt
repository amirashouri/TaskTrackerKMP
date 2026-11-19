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
import com.tasktracker.resources.backlog_section_archived
import com.tasktracker.resources.backlog_section_backlog
import com.tasktracker.resources.backlog_section_kanban
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

    // The item waiting for a column choice, or null when the picker is closed. Set by the resolver
    // below only when it cannot decide on its own — i.e. several columns and no usable default.
    var pendingPutOnBoard by remember { mutableStateOf<WorkItem?>(null) }

    fun commitPutOnBoard(item: WorkItem, definition: StatusDefinition) {
        onPutOnBoard(item.id, definition.id)
        onPutOnBoardDefaultStatusChosen(definition.id)
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
        when {
            boardStatuses.isEmpty() -> onPutOnBoard(item.id, null)
            boardStatuses.size == 1 -> commitPutOnBoard(item, boardStatuses.first())
            putOnBoardDefaultStatusKey != null &&
                boardStatuses.any { it.id == putOnBoardDefaultStatusKey } ->
                commitPutOnBoard(item, boardStatuses.first { it.id == putOnBoardDefaultStatusKey })
            else -> pendingPutOnBoard = item
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
                                onSelect = { currentSection = it },
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
                                onValueChange = { query = it },
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
                                        query = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                tint = if (searchOpen) ttAccent() else ttLabel(),
                                modifier = toggleModifier.testTag(ProjectBacklogSearchToggleTag),
                            )
                        }
                        val openSortSheet: () -> Unit = { sortSheetOpen = true }

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
                                } else {
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
                                    if (currentSection == BacklogSection.Backlog) {
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
                                    searchToggle(Modifier)
                                }
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
            pendingPutOnBoard?.let { item ->
                PutOnBoardSheet(
                    item = item,
                    statuses = boardStatuses,
                    onConfirm = { definition ->
                        commitPutOnBoard(item, definition)
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