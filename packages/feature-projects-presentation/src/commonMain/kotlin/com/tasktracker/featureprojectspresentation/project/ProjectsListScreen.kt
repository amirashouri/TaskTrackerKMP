package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.EpicID
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTSwipeToRevealRow
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBannerTone
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_delete
import com.tasktracker.resources.project_already_archived
import com.tasktracker.resources.project_archived_notice_counted
import com.tasktracker.resources.project_deleted_notice
import com.tasktracker.resources.project_new
import com.tasktracker.resources.project_remove_archive
import com.tasktracker.resources.project_remove_body_clear
import com.tasktracker.resources.project_remove_body_outstanding
import com.tasktracker.resources.project_remove_title
import com.tasktracker.designsystem.ttEntityIcons
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.resources.epic_new
import com.tasktracker.resources.epic_empty_body
import com.tasktracker.resources.epic_empty_title
import com.tasktracker.resources.projects_segment_epics
import com.tasktracker.resources.projects_segment_projects
import com.tasktracker.resources.projects_empty_body
import com.tasktracker.resources.projects_empty_title
import com.tasktracker.resources.tab_projects
import org.jetbrains.compose.resources.stringResource

/**
 * Main project list screen supporting swipe-to-reveal actions and contextual removal confirmations
 * (distinguishing between project archival and permanent deletion based on outstanding tasks).
 */
@Composable
fun ProjectsListScreen(
    rows: List<ProjectRow>,
    notice: RemovalNotice?,
    isLoading: Boolean,
    errorMessage: String?,
    onOpen: (ProjectID) -> Unit,
    onConfirmRemove: (ProjectID) -> Unit,
    onDismissRemove: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onNewProject: (() -> Unit)? = null,
    onEditProject: (ProjectID) -> Unit = {},
    /** Which half of the tab is showing; held by the shell so a tab switch cannot reset it. */
    segment: ProjectsSegment = ProjectsSegment.Projects,
    onSegmentChanged: (ProjectsSegment) -> Unit = {},
    epicRows: List<EpicListRow> = emptyList(),
    epicsLoading: Boolean = false,
    onNewEpic: (() -> Unit)? = null,
    onOpenEpic: (EpicID) -> Unit = {},
    onEditEpic: (EpicID) -> Unit = {},
    onArchiveEpic: (EpicID, Boolean) -> Unit = { _, _ -> },
    onUnarchiveEpic: (EpicID) -> Unit = {},
) {
    var pendingRemovalID by remember { mutableStateOf<ProjectID?>(null) }
    var pendingArchiveID by remember { mutableStateOf<EpicID?>(null) }
    val tabBarInset = LocalTabBarScrollUnderInset.current
    // The add action creates for the segment that is showing, so it is only offered when that
    // segment has an editor; a button for the other list would open a form the user did not ask for.
    val canCreate = when (segment) {
        ProjectsSegment.Projects -> onNewProject != null
        ProjectsSegment.Epics -> onNewEpic != null
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag(ProjectsListScreenTag),
    ) {
        val isWide = maxWidth >= ProjectsWideMinimumWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Same arrangement as the backlog header: a wide window puts the segment on the
                // title's row, centred in the space the title and the add action leave; below
                // this width the title keeps the row and the segment gets its own full-width
                // row, where the picker's own cap lets it span the padded column.
                val wideHeader = maxWidth >= ProjectsHeaderWideMinimumWidth
                val segmentPicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                    TTGlassSegmentedPicker(
                        options = ProjectsSegment.entries,
                        selection = segment,
                        onSelect = onSegmentChanged,
                        modifier = pickerModifier.testTag(ProjectsSegmentTag),
                        testTag = { ProjectsSegmentOptionTag(it) },
                        title = {
                            stringResource(
                                when (it) {
                                    ProjectsSegment.Projects -> Res.string.projects_segment_projects
                                    ProjectsSegment.Epics -> Res.string.projects_segment_epics
                                },
                            )
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    ) {
                        // Wrap the title on a wide header so the weighted box spans exactly the
                        // gap to the add action; weighting the title too would centre the picker
                        // in a share of the row rather than in the visible gap.
                        ttText(
                            text = stringResource(Res.string.tab_projects),
                            style = ttTypography.largeTitle,
                            color = ttLabel(),
                            modifier = if (wideHeader) Modifier else Modifier.weight(1f),
                        )
                        if (wideHeader) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                segmentPicker(Modifier)
                            }
                        }
                        if (canCreate) {
                            ttGlassIconButton(
                                image = ttActionIcons.add,
                                contentDescription = stringResource(
                                    when (segment) {
                                        ProjectsSegment.Projects -> Res.string.project_new
                                        ProjectsSegment.Epics -> Res.string.epic_new
                                    },
                                ),
                                onClick = {
                                    when (segment) {
                                        ProjectsSegment.Projects -> onNewProject?.invoke()
                                        ProjectsSegment.Epics -> onNewEpic?.invoke()
                                    }
                                },
                                modifier = Modifier.testTag(ProjectsListNewTag),
                            )
                        }
                    }
                    if (!wideHeader) {
                        segmentPicker(Modifier)
                    }
                }
            }
            errorMessage?.let { message ->
                ttBanner(message = message, onDismiss = onDismissError)
            }
            notice?.let { outcome ->
                ttBanner(
                    tone = ttBannerTone.Accent,
                    message = when (outcome) {
                        is RemovalNotice.ArchivedInstead -> stringResource(
                            Res.string.project_archived_notice_counted,
                            outcome.projectName,
                            outcome.outstandingCount,
                        )
                        is RemovalNotice.Deleted -> stringResource(
                            Res.string.project_deleted_notice,
                            outcome.projectName,
                        )
                        is RemovalNotice.AlreadyArchived -> stringResource(
                            Res.string.project_already_archived,
                            outcome.projectName,
                        )
                    },
                    onDismiss = onDismissNotice,
                )
            }
            if (segment == ProjectsSegment.Epics && epicRows.isEmpty() && !epicsLoading) {
                ProjectsEmptyState(
                    title = stringResource(Res.string.epic_empty_title),
                    body = stringResource(Res.string.epic_empty_body),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    image = ttEntityIcons.epic,
                    action = if (onNewEpic != null) {
                        {
                            ttGlassButton(
                                label = stringResource(Res.string.epic_new),
                                onClick = onNewEpic,
                                style = TTGlassButtonStyle(prominent = true),
                            )
                        }
                    } else {
                        null
                    },
                )
            } else if (segment == ProjectsSegment.Epics) {
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag(ProjectsEpicListTag),
                    contentPadding = PaddingValues(bottom = ttSpacing.xl + tabBarInset),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    items(epicRows, key = { it.epic.id.stringValue }) { row ->
                        EpicRowView(
                            row = row,
                            onOpen = { onOpenEpic(row.epic.id) },
                            onEdit = { onEditEpic(row.epic.id) },
                            onToggleArchived = {
                                if (row.epic.isArchived) {
                                    onUnarchiveEpic(row.epic.id)
                                } else {
                                    pendingArchiveID = row.epic.id
                                }
                            },
                        )
                    }
                }
            } else if (rows.isEmpty() && !isLoading) {
                ProjectsEmptyState(
                    title = stringResource(Res.string.projects_empty_title),
                    body = stringResource(Res.string.projects_empty_body),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    image = ttTabIcons.projects,
                    action = if (onNewProject != null) {
                        {
                            ttGlassButton(
                                label = stringResource(Res.string.project_new),
                                onClick = onNewProject,
                                style = TTGlassButtonStyle(prominent = true),
                            )
                        }
                    } else {
                        null
                    },
                )
            } else if (isWide) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = ProjectCardMinimumWidth),
                    modifier = Modifier.weight(1f).testTag(ProjectsListTag),
                    contentPadding = PaddingValues(
                        bottom = ttSpacing.xl + tabBarInset,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    items(rows, key = { it.summary.id.stringValue }) { row ->
                        TTSwipeToRevealRow(
                            revealLabel = stringResource(Res.string.common_delete),
                            isRevealed = pendingRemovalID == row.summary.id,
                            onRevealed = { pendingRemovalID = row.summary.id },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ProjectRowView(
                                project = row.summary,
                                onOpen = { onOpen(row.summary.id) },
                                onRemove = { pendingRemovalID = row.summary.id },
                                onEdit = { onEditProject(row.summary.id) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag(ProjectsListTag),
                    contentPadding = PaddingValues(
                        bottom = ttSpacing.xl + tabBarInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    items(rows, key = { it.summary.id.stringValue }) { row ->
                        TTSwipeToRevealRow(
                            revealLabel = stringResource(Res.string.common_delete),
                            isRevealed = pendingRemovalID == row.summary.id,
                            onRevealed = { pendingRemovalID = row.summary.id },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ProjectRowView(
                                project = row.summary,
                                onOpen = { onOpen(row.summary.id) },
                                onRemove = { pendingRemovalID = row.summary.id },
                                onEdit = { onEditProject(row.summary.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingRow = pendingRemovalID?.let { wanted -> rows.firstOrNull { it.summary.id == wanted } }
    val pendingProjectVanished = pendingRemovalID != null && pendingRow == null

    // Reset pending state in an effect if the target project disappeared mid-confirmation
    if (pendingProjectVanished) {
        LaunchedEffect(pendingRemovalID, pendingProjectVanished) {
            if (pendingProjectVanished) {
                pendingRemovalID = null
                onDismissRemove()
            }
        }
    }

    pendingArchiveID?.let { id ->
        epicRows.firstOrNull { it.epic.id == id }?.let { row ->
            EpicArchiveSheet(
                epicTitle = row.epic.title,
                unfinishedCount = row.unfinishedCount,
                visible = true,
                onArchiveOnly = {
                    pendingArchiveID = null
                    onArchiveEpic(id, false)
                },
                onArchiveAll = {
                    pendingArchiveID = null
                    onArchiveEpic(id, true)
                },
                onDismissRequest = { pendingArchiveID = null },
                onDismissed = { pendingArchiveID = null },
            )
        }
    }

    pendingRow?.let { row ->
        RemoveProjectConfirmation(
            row = row,
            onConfirm = {
                onConfirmRemove(row.summary.id)
                pendingRemovalID = null
            },
            onDismiss = {
                pendingRemovalID = null
                onDismissRemove()
            },
        )
    }
}

/** Rendered inline rather than via platform Dialog to ensure testability across all multiplatform hosts. */
@Composable
private fun RemoveProjectConfirmation(
    row: ProjectRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.surface)
    val hasOutstanding = row.outstandingCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = shape)
            .padding(ttSpacing.formStack)
            .testTag(RemoveProjectConfirmationTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.project_remove_title, row.summary.name),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        ttText(
            text = if (hasOutstanding) {
                stringResource(Res.string.project_remove_body_outstanding, row.outstandingCount)
            } else {
                stringResource(Res.string.project_remove_body_clear)
            },
            style = ttTypography.callout,
            color = ttLabelSecondary(),
            textAlign = TextAlign.Start,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag(RemoveProjectCancelTag),
            )
            ttGlassButton(
                label = stringResource(
                    if (hasOutstanding) Res.string.project_remove_archive else Res.string.common_delete,
                ),
                onClick = onConfirm,
                modifier = Modifier.testTag(RemoveProjectConfirmTag),
                style = TTGlassButtonStyle(tint = ttError(), prominent = true),
            )
        }
    }
}

/** The width at which the segment joins the title row instead of taking its own row. */
private val ProjectsHeaderWideMinimumWidth = 700.dp

const val ProjectsListScreenTag = "projects.list"
const val ProjectsListTag = "projects.list.rows"
const val ProjectsEpicListTag = "projects.list.epics"
const val ProjectsListNewTag = "projects.list.new"
const val ProjectsSegmentTag = "projects.segment"

fun ProjectsSegmentOptionTag(segment: ProjectsSegment): String =
    "projects.segment.${segment.wireValue}"
const val RemoveProjectConfirmationTag = "projects.remove.confirmation"
const val RemoveProjectConfirmTag = "projects.remove.confirm"
const val RemoveProjectCancelTag = "projects.remove.cancel"

/**
 * Threshold width at which the project list switches from a single-column
 * swipe-to-reveal list to an adaptive multi-column card grid.
 * Aligns with the shell's expanded breakpoint (840.dp).
 */
val ProjectsWideMinimumWidth: Dp = 840.dp

/** Minimum card width in the adaptive grid. Yields 2 columns at 840dp and 3 columns at 1200dp+. */
val ProjectCardMinimumWidth: Dp = 340.dp
