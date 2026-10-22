package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.featureprojectsdomain.BacklogSection
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.resources.Res
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
import org.jetbrains.compose.resources.stringResource

/**
 * The backlog tab: untriaged work, the board, and filed-away work, as three switchable
 * sections rather than one split column — a split would be as tall as the longest list (a long
 * archive wins), and a switch keeps each list to its own height. Each section owns its own empty
 * state (three different sentences, also testable without guessing which list was in front), and
 * the busy id tracks the row the user tapped rather than the section now showing, so a write in
 * flight keeps its wait on whatever section the item is really in.
 *
 * ## The header floats over the list
 *
 * The switcher, banner and section picker are drawn over the list rather than above it, so
 * scrolled rows pass behind their glass instead of stopping at a band. The header's height is
 * measured and given back as the list's top `contentPadding`: the viewport runs to the top of
 * the screen, the first row still starts below the header, and the last row keeps the tab bar's
 * inset at the bottom.
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
) {
    val tabBarInset = LocalTabBarScrollUnderInset.current
    var currentSection by remember { mutableStateOf(projection.currentSection) }

    // The header floats over the list, so its own height is the only thing that can keep the
    // first row out from under it. Measured rather than summed from tokens: the banner is
    // conditional and the controls grow with the font setting, so a token sum would be wrong on
    // at least one host. The same "measurement, not a token" reasoning as the shell's tab bar.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val firstRowInset = with(LocalDensity.current) {
        headerHeightPx.toDp()
    } + ttSpacing.lg

    ClipboardCopyHost(
        modifier = modifier.testTag(ProjectBacklogScreenTag),
    ) { onCopy ->
        Box(modifier = Modifier.fillMaxSize()) {
            // The list keeps the whole screen height. Its horizontal gutter is on the list itself
            // because it no longer sits inside the header's `Column`; its top clearance is
            // `contentPadding`, not a viewport inset, which is what lets scrolled rows travel
            // under the glass instead of stopping at a band below it.
            when (currentSection) {
                // Section tags live on each list, not the shared container: a chained
                // `testTag(a).testTag(b)` keeps only the first value in this Compose version, so
                // a container tag would hide the empty states from separate queries.
                BacklogSection.Backlog -> BacklogSectionList(
                    projection = projection,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    onSelect = onSelect,
                    onPutOnBoard = onPutOnBoard,
                    onFileAway = onFileAway,
                    onCopy = onCopy,
                    contentTopInset = firstRowInset,
                    tabBarInset = tabBarInset,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ttSpacing.xxl),
                )
                BacklogSection.Kanban -> KanbanSectionList(
                    projection = projection,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    onSelect = onSelect,
                    onFileAway = onFileAway,
                    onCopy = onCopy,
                    contentTopInset = firstRowInset,
                    tabBarInset = tabBarInset,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ttSpacing.xxl),
                )
                BacklogSection.Archived -> ArchivedSectionList(
                    projection = projection,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    onSelect = onSelect,
                    onUnarchive = onUnarchive,
                    onCopy = onCopy,
                    contentTopInset = firstRowInset,
                    modifier = Modifier.fillMaxSize().padding(horizontal = ttSpacing.xxl),
                )
            }

            // Drawn after the list and with no fill of its own. The list's rows passing behind
            // the translucent switcher and picker is the effect; a background band here would
            // put an opaque strip between the glass and the content.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height }
                    .padding(horizontal = ttSpacing.xxl)
                    .padding(top = ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                onOpenSwitcher?.let { openSwitcher ->
                    // The add action rides the switcher's row rather than floating over the list;
                    // `weight(1f)` keeps the switcher at its own width, clear of the action.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                    ) {
                        ProjectSwitcherButton(
                            project = projection.project,
                            onClick = openSwitcher,
                            modifier = Modifier.weight(1f),
                            projects = projects,
                            onSelectProject = onSelectProject,
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
            }
        }
    }
}

@Preview
@Composable
private fun ProjectBacklogScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectBacklogScreen(
        projection = ViewPreviewData.backlogSectionedProjection(),
        isLoading = false,
        errorMessage = null,
        busyItemID = null,
        onSelect = {},
        onPutOnBoard = {},
        onFileAway = {},
        onUnarchive = {},
        onDismissError = {},
    )
}

@Preview
@Composable
private fun ProjectBacklogScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectBacklogScreen(
        projection = BacklogSectionedProjection(
            project = ViewPreviewData.seededProject,
            items = emptyList(),
        ),
        isLoading = false,
        errorMessage = null,
        busyItemID = null,
        onSelect = {},
        onPutOnBoard = {},
        onFileAway = {},
        onUnarchive = {},
        onDismissError = {},
    )
}



/**
 * The backlog section: untriaged work, each row offering "Put on board" and "File away" and
 * opening the task on tap — the only section where both verbs apply.
 */
@Composable
private fun BacklogSectionList(
    projection: BacklogSectionedProjection,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    onSelect: (WorkItemID) -> Unit,
    onPutOnBoard: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
    contentTopInset: Dp,
    tabBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.backlogIsEmpty) {
        EmptyBacklogSection(
            modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptyBacklogTag),
        )
    } else {
        LazyColumn(
            modifier = modifier.testTag(ProjectBacklogListTag),
            contentPadding = PaddingValues(
                top = contentTopInset,
                bottom = ttSpacing.xl + tabBarInset,
            ),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(projection.backlog, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = { onPutOnBoard(item.id) },
                    onFileAway = { onFileAway(item.id) },
                    isBusy = busyItemID == item.id,
                    onOpen = { onSelect(item.id) },
                    onCopy = onCopy,
                )
            }
        }
    }
}

/**
 * The Kanban section: the board as rows, each offering "File away" and opening on tap. Every item
 * is already on a board, so the row drops its put-on-board action. This duplicates
 * `BacklogSectionList` on purpose: the two differ by one handler and one empty state, and folding
 * them together would add a parameter nobody else uses.
 */
@Composable
private fun KanbanSectionList(
    projection: BacklogSectionedProjection,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    onSelect: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
    contentTopInset: Dp,
    tabBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.kanbanIsEmpty) {
        EmptyKanbanSection(
            modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptyKanbanTag),
        )
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
                )
            }
        }
    }
}

/**
 * The archived section: closed work, newest first, read-only. No put-on-board or file-away — every
 * item is already `Closed`, so the section shows Reopen instead, which returns the item to the
 * backlog and clears its resolution date.
 */
@Composable
private fun ArchivedSectionList(
    projection: BacklogSectionedProjection,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    onSelect: (WorkItemID) -> Unit,
    onUnarchive: (WorkItemID) -> Unit,
    onCopy: (String) -> Unit,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.archivedIsEmpty) {
        EmptyArchivedSection(
            modifier = modifier.padding(top = contentTopInset).testTag(ProjectBacklogEmptyArchivedTag),
        )
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
                    onPutOnBoard = { },
                    onFileAway = { },
                    isBusy = busyItemID == item.id,
                    onOpen = { onSelect(item.id) },
                    onUnarchive = { onUnarchive(item.id) },
                    onCopy = onCopy,
                )
            }
        }
    }
}

/** The backlog section's empty state. Kept separate from the archive's so the sections stay testable. */
@Composable
private fun EmptyBacklogSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_empty_title),
        body = stringResource(Res.string.backlog_empty_body),
        modifier = modifier,
    )
}

/** The Kanban section's empty state: the board has no on-board tasks yet. */
@Composable
private fun EmptyKanbanSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_kanban_empty_title),
        body = stringResource(Res.string.backlog_kanban_empty_body),
        modifier = modifier,
    )
}

/** The archived section's empty state: nothing has been filed away from this backlog yet. */
@Composable
private fun EmptyArchivedSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_archived_empty_title),
        body = stringResource(Res.string.backlog_archived_empty_body),
        modifier = modifier,
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
const val BacklogAddTaskTag = "backlog.addTask"

/** The section indicator chip for one section, tagged with the section's label so a test can reach either. */
fun SectionIndicatorTag(label: String): String = "backlog.section.$label"
