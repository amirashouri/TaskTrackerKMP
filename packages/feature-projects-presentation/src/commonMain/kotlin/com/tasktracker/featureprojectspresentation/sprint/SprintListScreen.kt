package com.tasktracker.featureprojectspresentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.SprintSection
import com.tasktracker.resources.Res
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.resources.sprint_list_empty_body
import com.tasktracker.resources.sprint_list_empty_title
import com.tasktracker.resources.sprint_list_new
import com.tasktracker.resources.sprint_in_progress_empty_body
import com.tasktracker.resources.sprint_in_progress_empty_title
import com.tasktracker.resources.sprint_scheduled_empty_body
import com.tasktracker.resources.sprint_scheduled_empty_title
import com.tasktracker.resources.sprint_section_finished
import com.tasktracker.resources.sprint_section_in_progress
import com.tasktracker.resources.sprint_section_scheduled
import org.jetbrains.compose.resources.stringResource

/**
 * Screen displaying project sprints segmented into Active (In-Progress, Scheduled, Finished) and Archived lists.
 *
 * On a wide window the Active/Archive picker shares the header's first row, between the project
 * switcher and the add action — the same arrangement as the backlog's section picker — and falls
 * back to its own row below the actions when the header is too narrow to hold both.
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
    // Hoisted above the measured header so the picker can live in either arrangement without
    // its selection being reconstructed when the window crosses the wide threshold.
    var section by remember { mutableStateOf(SprintSection.Active) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(SprintListScreenTag),
    ) {
        // The same reading-width rule as the backlog: past the shell's expanded breakpoint the
        // header and every section are one column, so the cap goes on the column that holds
        // both. Below it the content keeps the window's width and the phone header arrangement.
        val isWide = maxWidth >= SprintWideMinimumWidth
        val contentModifier = if (isWide) {
            Modifier
                .widthIn(max = SprintMaxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight()
        } else {
            Modifier.fillMaxSize()
        }

        BoxWithConstraints(
            modifier = contentModifier
                .align(Alignment.TopCenter)
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
        ) {
            // The same rule as the backlog header: a wide window puts the section picker on the
            // selector's row, between the project switcher and the add action. Below this width
            // there is not enough room for the picker and the action without squeezing the project
            // name, so the header keeps its stacked phone arrangement.
            val wideHeader = maxWidth >= SprintHeaderWideMinimumWidth
            val sectionPicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                TTGlassSegmentedPicker(
                    options = listOf(SprintSection.Active, SprintSection.Archived),
                    selection = section,
                    onSelect = { section = it },
                    modifier = pickerModifier.testTag(SprintListSectionsTag),
                    title = { option ->
                        when (option) {
                            SprintSection.Active -> "Active"
                            SprintSection.Archived -> "Archive"
                        }
                    },
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                // One row whether or not there is a switcher to fill its left side: the + keeps
                // the trailing edge it has always had.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    if (onOpenSwitcher != null) {
                        ProjectSwitcherButton(
                            project = project,
                            onClick = onOpenSwitcher,
                            // On a wide header the switcher wraps its own content and the
                            // picker's weighted box takes the rest: weighting the switcher
                            // instead would leave its unused share as a gap *before* the box,
                            // so the picker would centre in the wrong space. On a compact
                            // header it still fills the row.
                            modifier = if (wideHeader) Modifier else Modifier.weight(1f),
                            projects = projects,
                            onSelectProject = onSelectProject,
                        )
                    } else {
                        Spacer(if (wideHeader) Modifier else Modifier.weight(1f))
                    }
                    if (wideHeader) {
                        // Centred in the gap between the selector and the add action: the box
                        // spans exactly that gap, so centring the capped picker inside it leaves
                        // equal space at either end.
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            sectionPicker(Modifier)
                        }
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
                // Compact header: the picker keeps its own row under the actions.
                if (!wideHeader) {
                    sectionPicker(Modifier)
                }
                when (section) {
                    // null represents initial load in flight; true triggers empty state
                    SprintSection.Active -> if (activeIsEmpty == true) {
                        ProjectsEmptyState(
                            title = stringResource(Res.string.sprint_list_empty_title),
                            body = stringResource(Res.string.sprint_list_empty_body),
                            image = ttTabIcons.sprints,
                            action = {
                                ttGlassButton(
                                    label = stringResource(Res.string.sprint_list_new),
                                    onClick = onNewSprint,
                                    style = TTGlassButtonStyle(prominent = true),
                                )
                            },
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(SprintListTag),
                            contentPadding = PaddingValues(bottom = ttSpacing.xxxl),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        ) {
                            item(key = "in_progress_header") {
                                SprintSectionHeader(
                                    label = stringResource(Res.string.sprint_section_in_progress),
                                    count = inProgressRows.size,
                                    modifier = Modifier.padding(top = ttSpacing.lg),
                                )
                            }
                            if (inProgressRows.isEmpty()) {
                                item(key = "in_progress_empty") {
                                    ProjectsEmptyState(
                                        title = stringResource(Res.string.sprint_in_progress_empty_title),
                                        body = stringResource(Res.string.sprint_in_progress_empty_body),
                                        image = ttTabIcons.sprints,
                                    )
                                }
                            } else {
                                items(items = inProgressRows, key = { "ip_${it.sprint.id.stringValue}" }) { row ->
                                    SprintListRowView(
                                        row = row,
                                        onClick = { onOpen(row.sprint.id) },
                                    )
                                }
                            }

                            item(key = "scheduled_header") {
                                SprintSectionHeader(
                                    label = stringResource(Res.string.sprint_section_scheduled),
                                    count = scheduledRows.size,
                                    modifier = Modifier.padding(top = ttSpacing.lg),
                                )
                            }
                            if (scheduledRows.isEmpty()) {
                                item(key = "scheduled_empty") {
                                    ProjectsEmptyState(
                                        title = stringResource(Res.string.sprint_scheduled_empty_title),
                                        body = stringResource(Res.string.sprint_scheduled_empty_body),
                                        image = ttTabIcons.sprints,
                                    )
                                }
                            } else {
                                items(items = scheduledRows, key = { "sch_${it.sprint.id.stringValue}" }) { row ->
                                    SprintListRowView(
                                        row = row,
                                        onClick = { onOpen(row.sprint.id) },
                                    )
                                }
                            }

                            item(key = "finished_header") {
                                SprintSectionHeader(
                                    label = stringResource(Res.string.sprint_section_finished),
                                    count = finishedRows.size,
                                    modifier = Modifier.padding(top = ttSpacing.lg),
                                )
                            }
                            if (finishedRows.isEmpty()) {
                                item(key = "finished_empty") {
                                    EmptySection(title = stringResource(Res.string.sprint_section_finished))
                                }
                            } else {
                                items(items = finishedRows, key = { "fin_${it.sprint.id.stringValue}" }) { row ->
                                    SprintListRowView(
                                        row = row,
                                        onClick = { onOpen(row.sprint.id) },
                                    )
                                }
                            }
                        }
                    }

                    SprintSection.Archived -> if (archivedIsEmpty == true) {
                        ProjectsEmptyState(
                            title = stringResource(Res.string.sprint_list_empty_title),
                            body = stringResource(Res.string.sprint_list_empty_body),
                            image = ttTabIcons.sprints,
                            action = {
                                ttGlassButton(
                                    label = stringResource(Res.string.sprint_list_new),
                                    onClick = onNewSprint,
                                    style = TTGlassButtonStyle(prominent = true),
                                )
                            },
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
}

const val SprintListScreenTag = "sprints.list"
const val SprintListTag = "sprints.list.rows"
const val SprintListNewTag = "sprints.list.new"
const val SprintListSectionsTag = "sprints.list.sections"

/** Minimum width at which the sprint list switches to wide desktop layout clamping. */
val SprintWideMinimumWidth: Dp = 840.dp

/** Maximum readable content width for the sprint header and its sections. */
val SprintMaxContentWidth: Dp = 960.dp

/**
 * The header width at which the Active/Archive picker joins the selector's row.
 *
 * Above the picker's own `pickerMaxWidth` cap plus the add action and a usable project name.
 * Below it the header keeps the stacked phone arrangement rather than squeezing the project name
 * to a few characters. Matches the backlog header's threshold so the two tabs switch arrangement
 * at the same window width.
 */
private val SprintHeaderWideMinimumWidth = 700.dp

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