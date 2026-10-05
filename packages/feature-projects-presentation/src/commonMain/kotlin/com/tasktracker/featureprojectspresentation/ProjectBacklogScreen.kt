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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.featureprojectsdomain.BacklogProjection
import com.tasktracker.featureprojectsdomain.BacklogSection
import com.tasktracker.featureprojectsdomain.BacklogSectionedProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_archived_empty_body
import com.tasktracker.resources.backlog_archived_empty_title
import com.tasktracker.resources.backlog_empty_body
import com.tasktracker.resources.backlog_empty_title
import com.tasktracker.resources.backlog_put_on_board
import com.tasktracker.resources.backlog_section_archived
import com.tasktracker.resources.backlog_section_backlog
import com.tasktracker.resources.board_add_task
import org.jetbrains.compose.resources.stringResource

/**
 * The backlog tab: the work not yet on a board, and the work that was filed away from it.
 *
 * ## Why two sections
 *
 * The backlog tab is read as two piles: what still needs doing, and what was done here.
 * That is two different questions, and the tab answers both without leaving it, so the screen
 * renders them as two sections the user can switch between rather than one list that folds the
 * past into the present.
 *
 * ## Why the sections are a switch, not a split
 *
 * A split keeps both lists on screen simultaneously, and a backlog with anything filed away
 * is then exactly as tall as the longer of the two — which, on a project with a long archive,
 * is the archive, and a user scrolling the backlog then has to fight the archive's tail. A
 * switch keeps each list the height of its own content and is the cheaper screen to read.
 *
 * ## Why archived is closed-only, newest first
 *
 * The backlog's file-away verb moves a task to [WorkItemStatus.Closed], and the backlog tab's
 * "what was done here" is that verb's result, not the full month-grouped archive the separate
 * archive tab already renders. Keeping this tab's archived section flat and newest-first is the
 * shape that matches a backlog log: the last thing filed is the first thing you look at.
 *
 * ## Why each section keeps its own empty state
 *
 * The backlog emptiness and the archive emptiness are not the same sentence. One means "nothing
 * still to do" and the other means "nothing filed away yet", and a user who files the last
 * backlogged task away has just done something worth acknowledging in the words. Two empty
 * states also make the section switch testable without imagining which list was in front.
 *
 * ## Why the busy item survives a section switch
 *
 * Filing away and putting on board are both writes that take a moment, and the card in flight
 * is the row the user tapped, not the row currently in front. The busy id is the same id the
 * row uses to show the wait, so switching sections while a write is in flight keeps the wait on
 * whichever section the item is really in.
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
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSwitcher: (() -> Unit)? = null,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    onAddTask: (() -> Unit)? = null,
) {
    val tabBarInset = LocalTabBarScrollUnderInset.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProjectBacklogScreenTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top only: bottom padding here would shrink the *list's viewport* and stop it short of the
                // screen's bottom edge. The clearance that keeps the last row off the glass is the
                // list's `contentPadding`.
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            onOpenSwitcher?.let { openSwitcher ->
                // The add action rides the switcher's row rather than floating over the list.
                // It used to float at the bottom trailing corner, over a list that reserved only
                // the tab bar's inset, and it sat on the *rows* - which are clickable across their
                // full width - so tapping the bottom right of a backlog did nothing. It did not
                // cover "Put on board" or "File away" as one might assume: this row lays those out
                // with no `Alignment.End`, so they sit at the leading edge, well clear of the
                // corner. `weight(1f)` keeps the switcher at its own content width rather than
                // stretching it under the action. See `ProjectsHeader` for the projects list.
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
            var currentSection by remember { mutableStateOf(projection.currentSection) }
            TTGlassSegmentedPicker(
                options = listOf(BacklogSection.Backlog, BacklogSection.Archived),
                selection = currentSection,
                onSelect = { currentSection = it },
                modifier = Modifier.fillMaxWidth().horizontalGutter().testTag(ProjectBacklogSectionsTag),
                title = { option ->
                    when (option) {
                        BacklogSection.Backlog -> stringResource(Res.string.backlog_section_backlog)
                        BacklogSection.Archived -> stringResource(Res.string.backlog_section_archived)
                    }
                },
                testTag = { option ->
                    when (option) {
                        BacklogSection.Backlog -> SectionIndicatorTag(stringResource(Res.string.backlog_section_backlog))
                        BacklogSection.Archived -> SectionIndicatorTag(stringResource(Res.string.backlog_section_archived))
                    }
                }
            )
            when (currentSection) {
                BacklogSection.Backlog -> BacklogSectionList(
                    projection = projection,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    onSelect = onSelect,
                    onPutOnBoard = onPutOnBoard,
                    onFileAway = onFileAway,
                    tabBarInset = tabBarInset,
                    modifier = Modifier.fillMaxSize().testTag(ProjectBacklogListTag),
                )
                BacklogSection.Archived -> ArchivedSectionList(
                    projection = projection,
                    busyItemID = busyItemID,
                    isLoading = isLoading,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxSize().testTag(ProjectBacklogArchivedListTag),
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
        onDismissError = {},
    )
}



/**
 * The backlog section's list: the untriaged work, each row offering "Put on board" and "File away".
 *
 * The backlog section is the one the tab is named after, and it is the only section where the
 * backlog's two verbs — put on board and file away — both make sense. The archived section shows
 * closed work for reading, with no put-on-board action and no file-away action.
 */
@Composable
private fun BacklogSectionList(
    projection: BacklogSectionedProjection,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    onSelect: (WorkItemID) -> Unit,
    onPutOnBoard: (WorkItemID) -> Unit,
    onFileAway: (WorkItemID) -> Unit,
    tabBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.backlogIsEmpty) {
        EmptyBacklogSection(
            modifier = modifier.testTag(ProjectBacklogEmptyBacklogTag),
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = ttSpacing.xl + tabBarInset),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(projection.backlog, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = { onPutOnBoard(item.id) },
                    onFileAway = { onFileAway(item.id) },
                    isBusy = busyItemID == item.id,
                )
            }
        }
    }
}

/**
 * The archived section's list: closed work, newest first, for reading.
 *
 * No put-on-board and no file-away, because every item here is already `Closed` — the backlog's
 * file-away verb refuses on `Closed`, and "put on board" from `Closed` would be re-opening a
 * task the user chose to file away. The section is a log of what was filed, not a place to file.
 */
@Composable
private fun ArchivedSectionList(
    projection: BacklogSectionedProjection,
    busyItemID: WorkItemID?,
    isLoading: Boolean,
    onSelect: (WorkItemID) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isLoading && projection.archivedIsEmpty) {
        EmptyArchivedSection(
            modifier = modifier.testTag(ProjectBacklogEmptyArchivedTag),
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            items(projection.archived, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    onPutOnBoard = { },
                    onFileAway = { },
                    isBusy = busyItemID == item.id,
                )
            }
        }
    }
}

/**
 * The backlog section's empty state: backlog is empty but the tab may still have an archive.
 *
 * Shown when the backlog section is selected and the backlog section is empty, regardless of the
 * archived section — the archived section gets its own empty state, and mixing the two would make
 * the section switch untestable without imagining which list was in front.
 */
@Composable
private fun EmptyBacklogSection(modifier: Modifier = Modifier) {
    ProjectsEmptyState(
        title = stringResource(Res.string.backlog_empty_title),
        body = stringResource(Res.string.backlog_empty_body),
        modifier = modifier,
    )
}

/**
 * The archived section's empty state: nothing has been filed away from this backlog yet.
 *
 * A different sentence from the backlog's empty state, because the two sections are different
 * questions — one is "nothing still to do" and the other is "nothing filed away yet" — and a
 * user who just filed the last backlogged task away has done something worth acknowledging in the
 * words.
 */
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
const val ProjectBacklogArchivedListTag = "backlog.archivedRows"
const val ProjectBacklogEmptyBacklogTag = "backlog.emptyBacklog"
const val ProjectBacklogEmptyArchivedTag = "backlog.emptyArchived"
const val BacklogAddTaskTag = "backlog.addTask"

/** The section indicator chip for one section, tagged with the section's label so a test can reach either. */
fun SectionIndicatorTag(label: String): String = "backlog.section.$label"
