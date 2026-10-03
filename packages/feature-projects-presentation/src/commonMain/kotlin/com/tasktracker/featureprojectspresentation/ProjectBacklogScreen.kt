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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.featureprojectsdomain.BacklogProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_empty_body
import com.tasktracker.resources.backlog_empty_title
import com.tasktracker.resources.board_add_task
import org.jetbrains.compose.resources.stringResource

/**
 * The backlog: the tasks that are not on the board yet.
 *
 * ## Why this is a `LazyColumn` and the board is not
 *
 * A backlog is one unbounded list. It has no columns to keep in step and nothing beside it
 * to keep aligned, so a single lazy container is both correct and cheap - and it is the
 * shape Compose's gesture handling is happiest with. The board, which has columns that must
 * not reflow, gives up laziness for a scrolling `Row` instead; see
 * [ProjectBoardScreen] for why that trade is worth making there and not here.
 *
 * ## Why only the untriaged are listed
 *
 * [BacklogProjection.untriaged], not every item in the project. A backlog is the queue of
 * things nobody has started, and rendering a `Done` task in it would be showing work that
 * is finished in a list whose whole purpose is "not started yet". The projection already
 * does the grouping; the screen does not second-guess it.
 */
@Composable
fun ProjectBacklogScreen(
    projection: BacklogProjection,
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
                ProjectsErrorBanner(message = message, onDismiss = onDismissError)
            }
            if (projection.isEmpty && !isLoading) {
                ProjectsEmptyState(
                    title = stringResource(Res.string.backlog_empty_title),
                    body = stringResource(Res.string.backlog_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(ProjectBacklogListTag),
                    contentPadding = PaddingValues(bottom = ttSpacing.xl + tabBarInset),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    items(projection.untriaged, key = { it.id.stringValue }) { item ->
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
    }
}

@Preview
@Composable
private fun ProjectBacklogScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectBacklogScreen(
        projection = ViewPreviewData.backlogProjection(),
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
        projection = BacklogProjection(project = ViewPreviewData.seededProject, items = emptyList()),
        isLoading = false,
        errorMessage = null,
        busyItemID = null,
        onSelect = {},
        onPutOnBoard = {},
        onFileAway = {},
        onDismissError = {},
    )
}

const val ProjectBacklogScreenTag = "backlog.screen"
const val ProjectBacklogListTag = "backlog.rows"
const val BacklogAddTaskTag = "backlog.addTask"
