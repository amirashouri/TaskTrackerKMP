package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
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
    onPutOnBoard: (WorkItemID) -> Unit,
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
    val appliedQuery = rememberDebouncedQuery(query)
    // Keyed on the projection instance as well as the query: the model replaces the projection
    // on every reload, and a filter remembered against the query alone would keep showing the
    // previous load's rows.
    val shown = remember(projection, appliedQuery) { projection.matching(appliedQuery) }
    val focusManager = LocalFocusManager.current

    // Measured dynamically to accommodate conditional banners and variable typography scaling
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val firstRowInset = with(LocalDensity.current) {
        headerHeightPx.toDp()
    } + ttSpacing.lg

    ClipboardCopyHost(
        modifier = modifier.testTag(ProjectBacklogScreenTag),
    ) { onCopy ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Tags live on each list individually: testTag overwrites rather than merges in Compose
            when (currentSection) {
                BacklogSection.Backlog -> BacklogSectionList(
                    projection = shown,
                    sortOrder = currentSortOrder,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    searchQuery = appliedQuery,
                    onSelect = onSelect,
                    onPutOnBoard = onPutOnBoard,
                    onFileAway = onFileAway,
                    onCopy = onCopy,
                    contentTopInset = firstRowInset,
                    tabBarInset = tabBarInset,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ttSpacing.xxl),
                )
                BacklogSection.Kanban -> KanbanSectionList(
                    projection = shown,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    searchQuery = appliedQuery,
                    onSelect = onSelect,
                    onFileAway = onFileAway,
                    onCopy = onCopy,
                    contentTopInset = firstRowInset,
                    tabBarInset = tabBarInset,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ttSpacing.xxl),
                )
                BacklogSection.Archived -> ArchivedSectionList(
                    projection = shown,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    searchQuery = appliedQuery,
                    onSelect = onSelect,
                    onUnarchive = onUnarchive,
                    onCopy = onCopy,
                    contentTopInset = firstRowInset,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ttSpacing.xxl),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height }
                    .padding(horizontal = ttSpacing.xxl)
                    .padding(top = ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                // One row whether or not there is a switcher to fill its left side: the search
                // toggle has to be reachable either way, and the + keeps the trailing edge it
                // has always had, so the toggle is inserted before it rather than after.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    if (onOpenSwitcher != null) {
                        ProjectSwitcherButton(
                            project = projection.project,
                            onClick = onOpenSwitcher,
                            modifier = Modifier.weight(1f),
                            projects = projects,
                            onSelectProject = onSelectProject,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
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
                            // Closing resets the query too, for the reason the board's doc gives:
                            // a filter that outlives its field leaves rows missing with nothing
                            // on screen to say why.
                            if (!searchOpen) {
                                query = ""
                                focusManager.clearFocus()
                            }
                        },
                        tint = if (searchOpen) ttAccent() else ttLabel(),
                        modifier = Modifier.testTag(ProjectBacklogSearchToggleTag),
                    )
                    onAddTask?.let { addTask ->
                        ttGlassIconButton(
                            image = ttActionIcons.add,
                            contentDescription = stringResource(Res.string.board_add_task),
                            onClick = addTask,
                            modifier = Modifier.testTag(BacklogAddTaskTag),
                        )
                    }
                }
                // Inside the measured column, so the list's top inset grows with the field and
                // the first row still starts below the header rather than behind it. The reveal
                // is the reason the inset is measured at all: it changes while the field unfolds.
                SearchReveal(visible = searchOpen) {
                    ttSearchField(
                        value = query,
                        onValueChange = { query = it },
                        hint = stringResource(Res.string.work_item_search_placeholder),
                        modifier = Modifier.fillMaxWidth().testTag(ProjectBacklogSearchFieldTag),
                        fieldModifier = Modifier.testTag(ProjectBacklogSearchInputTag),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                }
                errorMessage?.let { message ->
                    ttBanner(message = message, onDismiss = onDismissError)
                }
                TTGlassSegmentedPicker(
                    options = listOf(BacklogSection.Backlog, BacklogSection.Kanban, BacklogSection.Archived),
                    selection = currentSection,
                    onSelect = { currentSection = it },
                    modifier = Modifier.fillMaxWidth().horizontalGutter().testTag(ProjectBacklogSectionsTag),
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
                    }
                )
                if (currentSection == BacklogSection.Backlog) {
                    BacklogSortControls(
                        order = currentSortOrder,
                        onToggle = { basis ->
                            // Update first, report second: the list has to re-read in the same
                            // frame as the tap, and the report is what remembers it, which may
                            // outlive this screen.
                            val next = currentSortOrder.toggled(basis)
                            currentSortOrder = next
                            onSortOrderChanged(next)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BacklogSectionList(
    projection: BacklogSectionedProjection,
    sortOrder: BacklogSortOrder,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    searchQuery: String,
    onSelect: (WorkItemID) -> Unit,
    onPutOnBoard: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
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
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.testTag(ProjectBacklogListTag),
            contentPadding = PaddingValues(
                top = contentTopInset,
                bottom = ttSpacing.xl + tabBarInset,
            ),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(backlog, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = { onPutOnBoard(item.id) },
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
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    searchQuery: String,
    onSelect: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
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
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.testTag(ProjectBacklogKanbanListTag),
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
                )
            }
        }
    }
}

@Composable
private fun ArchivedSectionList(
    projection: BacklogSectionedProjection,
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
private fun EmptyBacklogSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_empty_title),
        body = stringResource(Res.string.backlog_empty_body),
        modifier = modifier,
        image = ttTabIcons.backlog,
    )
}

@Composable
private fun EmptyKanbanSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_kanban_empty_title),
        body = stringResource(Res.string.backlog_kanban_empty_body),
        modifier = modifier,
        image = ttTabIcons.board,
    )
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

private fun Modifier.horizontalGutter(): Modifier = padding(horizontal = ttSpacing.xxl)

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

fun SectionIndicatorTag(label: String): String = "backlog.section.$label"