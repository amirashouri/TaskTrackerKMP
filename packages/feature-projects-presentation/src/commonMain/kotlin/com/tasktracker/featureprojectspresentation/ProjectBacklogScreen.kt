package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.tasktracker.designsystem.ttGlassAddButton
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
                .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            onOpenSwitcher?.let { openSwitcher ->
                ProjectSwitcherButton(
                    project = projection.project,
                    onClick = openSwitcher,
                    modifier = Modifier.fillMaxWidth(),
                    projects = projects,
                    onSelectProject = onSelectProject,
                )
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
                    contentPadding = PaddingValues(bottom = tabBarInset),
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
        onAddTask?.let { addTask ->
            ttGlassAddButton(
                contentDescription = stringResource(Res.string.board_add_task),
                onClick = addTask,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = ttSpacing.xxl,
                        bottom = ttSpacing.boardBottomInset + tabBarInset,
                    )
                    .testTag(BacklogAddTaskTag),
            )
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
