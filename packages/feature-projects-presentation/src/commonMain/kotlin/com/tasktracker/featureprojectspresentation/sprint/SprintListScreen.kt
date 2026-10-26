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
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.SprintSection
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_list_empty_body
import com.tasktracker.resources.sprint_list_empty_title
import com.tasktracker.resources.sprint_list_new
import com.tasktracker.resources.sprint_section_finished
import com.tasktracker.resources.sprint_section_in_progress
import com.tasktracker.resources.sprint_section_scheduled
import org.jetbrains.compose.resources.stringResource

/**
 * Screen displaying project sprints segmented into Active (In-Progress, Scheduled, Finished) and Archived lists.
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
            when (section) {
                // null represents initial load in flight; true triggers empty state
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

const val SprintListScreenTag = "sprints.list"
const val SprintListTag = "sprints.list.rows"
const val SprintListNewTag = "sprints.list.new"

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

@Composable
private fun SprintSectionList(
    rows: List<SprintListRow>,
    onOpen: (SprintID) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(SprintListTag),
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