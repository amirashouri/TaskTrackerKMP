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
import androidx.compose.ui.unit.Dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttSpacing
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
 * Backlog screen: renders untriaged work, active kanban tasks, and archived items in 3 tabs.
 * The header floats over the list; list top content padding is dynamically derived from header height.
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height }
                    .padding(horizontal = ttSpacing.xxl)
                    .padding(top = ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                onOpenSwitcher?.let { openSwitcher ->
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

@Composable
private fun EmptyBacklogSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_empty_title),
        body = stringResource(Res.string.backlog_empty_body),
        modifier = modifier,
    )
}

@Composable
private fun EmptyKanbanSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_kanban_empty_title),
        body = stringResource(Res.string.backlog_kanban_empty_body),
        modifier = modifier,
    )
}

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

fun SectionIndicatorTag(label: String): String = "backlog.section.$label"