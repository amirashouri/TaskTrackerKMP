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
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.featureprojectsdomain.SprintSection
import com.tasktracker.domain.Project
import com.tasktracker.domain.sprintSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_list_empty_body
import com.tasktracker.resources.sprint_list_empty_title
import com.tasktracker.resources.sprint_list_new
import com.tasktracker.resources.sprint_section_finished
import com.tasktracker.resources.sprint_section_in_progress
import com.tasktracker.resources.sprint_section_scheduled
import org.jetbrains.compose.resources.stringResource

/**
 * A project's sprints, as a list.
 *
 * ## The create action is always present, and that is the rule rather than an oversight
 *
 * Two unfinished sprints are allowed as long as their windows do not overlap, so "there is
 * already an open sprint" is not a reason to withhold the button — it is the normal state
 * of a project that plans ahead. What the store refuses is an **overlap**, and it refuses
 * it after a window has been chosen, naming the sprint in the way. Hiding the button would
 * hide the one action this screen is for, to prevent an error the window itself announces.
 *
 * The button is therefore unconditional; the form's own `blockedBySprintName` is where a
 * conflicting window is caught before the write, and the store's refusal is still there
 * for the race — another device created one between this read and the press — arriving as
 * the usual banner.
 *
 * ## Why this is a `LazyColumn` and the board is not
 *
 * The same reason [ProjectBacklogScreen] gives: this is one unbounded list with nothing
 * beside it to keep in step, which is the shape Compose's gesture handling is happiest
 * with. The board has columns that must not reflow and gives up laziness for a scrolling
 * `Row` instead.
 */
@Composable
fun SprintListScreen(
    inProgressRows: List<SprintListRow>,
    scheduledRows: List<SprintListRow>,
    finishedRows: List<SprintListRow>,
    archivedRows: List<SprintListRow>,
    activeIsEmpty: Boolean?,
    archivedIsEmpty: Boolean?,
    errorMessage: String?,
    project: Project? = null,
    onOpen: (SprintID) -> Unit,
    onNewSprint: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSwitcher: (() -> Unit)? = null,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(SprintListScreenTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                onOpenSwitcher?.let { openSwitcher ->
                    ProjectSwitcherButton(
                        project = project,
                        onClick = openSwitcher,
                        modifier = Modifier.weight(1f),
                        projects = projects,
                        onSelectProject = onSelectProject,
                    )
                }
                // Always present, whatever the list holds: overlapping windows are what is
                // refused, not the second sprint — see the class comment.
                ttGlassIconButton(
                    image = ttActionIcons.add,
                    contentDescription = stringResource(Res.string.sprint_list_new),
                    onClick = onNewSprint,
                    modifier = Modifier.testTag(SprintListNewTag),
                )
            }
            errorMessage?.let { message ->
                ttBanner(message = message, onDismiss = onDismissError)
            }
            var section by remember { mutableStateOf(SprintSection.Active) }
            TTGlassSegmentedPicker(
                options = listOf(SprintSection.Active, SprintSection.Archived),
                selection = section,
                onSelect = { section = it },
                modifier = Modifier.fillMaxWidth(),
                title = { option ->
                    when (option) {
                        SprintSection.Active -> "Active"
                        SprintSection.Archived -> "Archive"
                    }
                },
            )
            // Active is the three partitions of a sprint's window; Archive is one flat list.
            // The picker only chooses between the two, and the switch is screen-local state
            // rather than a reload — the model already holds both, so changing segment must
            // not re-read the store.
            when (section) {
                // `null` is still loading, and it is kept distinct from `true` because a
                // screen that claimed "no sprints" during the first read would flash an
                // empty state over a populated list on every cold start.
                SprintSection.Active -> if (activeIsEmpty == true) {
                    ProjectsEmptyState(
                        title = stringResource(Res.string.sprint_list_empty_title),
                        body = stringResource(Res.string.sprint_list_empty_body),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
                    ) {
                        InProgressSection(
                            rows = inProgressRows,
                            onOpen = onOpen,
                        )
                        ScheduledSection(
                            rows = scheduledRows,
                            onOpen = onOpen,
                        )
                        FinishedSection(
                            rows = finishedRows,
                            onOpen = onOpen,
                        )
                    }
                }

                // No headers here: archived sprints are filed away, not "scheduled",
                // "in progress" or "finished" — see `SprintListModel.archivedRows`.
                SprintSection.Archived -> if (archivedIsEmpty == true) {
                    ProjectsEmptyState(
                        title = stringResource(Res.string.sprint_list_empty_title),
                        body = stringResource(Res.string.sprint_list_empty_body),
                    )
                } else {
                    SprintSectionList(
                        rows = archivedRows,
                        onOpen = onOpen,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** The list screen, and its rows. */
const val SprintListScreenTag = "sprints.list"
const val SprintListTag = "sprints.list.rows"
const val SprintListNewTag = "sprints.list.new"

// ---------------------------------------------------------------------------------------------
// Section helpers
// ---------------------------------------------------------------------------------------------

/** A section header with a label and the count of items in it. */
@Composable
private fun SprintSectionHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = label,
            style = ttTypography.subheadlineSelected,
            color = ttLabel(),
        )
        ttText(
            text = "($count)",
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
        )
    }
}

/** An empty section placeholder. */
@Composable
private fun EmptySection(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ttSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = title,
            style = ttTypography.body,
            color = ttLabelSecondary(),
        )
    }
}

/** A section's list of sprint rows. */
@Composable
private fun SprintSectionList(
    rows: List<SprintListRow>,
    onOpen: (SprintID) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .testTag(SprintListTag),
        contentPadding = PaddingValues(bottom = ttSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        items(items = rows, key = { it.sprint.id.stringValue }) { row ->
            SprintListRowView(
                row = row,
                onClick = { onOpen(row.sprint.id) },
            )
        }
    }
}

/** The in-progress section: started sprints still under way. */
@Composable
private fun InProgressSection(
    rows: List<SprintListRow>,
    onOpen: (SprintID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SprintSectionHeader(
            label = stringResource(Res.string.sprint_section_in_progress),
            count = rows.size,
        )
        if (rows.isEmpty()) {
            EmptySection(title = stringResource(Res.string.sprint_section_in_progress))
        } else {
            SprintSectionList(rows = rows, onOpen = onOpen)
        }
    }
}

/** The scheduled section: future sprints not yet started. */
@Composable
private fun ScheduledSection(
    rows: List<SprintListRow>,
    onOpen: (SprintID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SprintSectionHeader(
            label = stringResource(Res.string.sprint_section_scheduled),
            count = rows.size,
        )
        if (rows.isEmpty()) {
            EmptySection(title = stringResource(Res.string.sprint_section_scheduled))
        } else {
            SprintSectionList(rows = rows, onOpen = onOpen)
        }
    }
}

/** The finished section: closed sprints with their summaries. */
@Composable
private fun FinishedSection(
    rows: List<SprintListRow>,
    onOpen: (SprintID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SprintSectionHeader(
            label = stringResource(Res.string.sprint_section_finished),
            count = rows.size,
        )
        if (rows.isEmpty()) {
            EmptySection(title = stringResource(Res.string.sprint_section_finished))
        } else {
            SprintSectionList(rows = rows, onOpen = onOpen)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

// Pinned schemes, not `ThemeScheme.System`, and fixtures from `ViewPreviewData` rather than
// `seedSampleDataIfNeeded` — see `ProjectsPreview`'s KDoc and 23.8.

/** Rows built from the preview fixtures, with the domain's own arithmetic. */
private fun previewSprintRows(): List<SprintListRow> {
    val items = ViewPreviewData.sprintWorkItems()
    return ViewPreviewData.sprints.map { sprint ->
        val summary = sprintSummary(sprint, items)
        SprintListRow(
            sprint = sprint,
            startDate = sprintLocalDate(sprint.startDate, ViewPreviewData.previewZone),
            endDate = sprintLocalDate(sprint.endDate, ViewPreviewData.previewZone),
            committedStoryPoints = summary.committedStoryPoints,
            achievedStoryPoints = summary.achievedStoryPoints,
        )
    }
}

/** Categorise preview rows into the three sections. */
private fun previewSectionedRows(): Triple<List<SprintListRow>, List<SprintListRow>, List<SprintListRow>> {
    val allRows = previewSprintRows()
    val inProgress = allRows.filter { !it.sprint.isFinished && it.sprint.startDate <= ViewPreviewData.instant }
    val scheduled = allRows.filter { !it.sprint.isFinished && it.sprint.startDate > ViewPreviewData.instant }
    val finished = allRows.filter { it.sprint.isFinished }
    return Triple(inProgress, scheduled, finished)
}

@Preview(name = "Sprint list", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintListScreenPreview() = ProjectsPreview(ThemeScheme.Light) {
    val (inProgress, scheduled, finished) = previewSectionedRows()
    SprintListScreen(
        inProgressRows = inProgress,
        scheduledRows = scheduled,
        finishedRows = finished,
        archivedRows = emptyList(),
        activeIsEmpty = false,
        archivedIsEmpty = true,
        errorMessage = null,
        onOpen = {},
        onNewSprint = {},
        onDismissError = {},
    )
}

@Preview(name = "Sprint list, empty", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
private fun SprintListScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Dark) {
    SprintListScreen(
        inProgressRows = emptyList(),
        scheduledRows = emptyList(),
        finishedRows = emptyList(),
        archivedRows = emptyList(),
        activeIsEmpty = true,
        archivedIsEmpty = true,
        errorMessage = null,
        onOpen = {},
        onNewSprint = {},
        onDismissError = {},
    )
}
