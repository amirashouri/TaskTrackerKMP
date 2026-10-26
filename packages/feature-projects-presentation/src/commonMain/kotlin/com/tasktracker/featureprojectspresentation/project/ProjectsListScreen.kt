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
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTSwipeToRevealRow
import com.tasktracker.designsystem.ThemeScheme
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
) {
    var pendingRemovalID by remember { mutableStateOf<ProjectID?>(null) }
    val tabBarInset = LocalTabBarScrollUnderInset.current

    Box(
        modifier = modifier.fillMaxSize().testTag(ProjectsListScreenTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            ProjectsHeader(onNewProject = onNewProject)
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
            if (rows.isEmpty() && !isLoading) {
                ProjectsEmptyState(
                    title = stringResource(Res.string.projects_empty_title),
                    body = stringResource(Res.string.projects_empty_body),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
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

@Composable
private fun ProjectsHeader(
    onNewProject: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttText(
            text = stringResource(Res.string.tab_projects),
            style = ttTypography.largeTitle,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        if (onNewProject != null) {
            ttGlassIconButton(
                image = ttActionIcons.add,
                contentDescription = stringResource(Res.string.project_new),
                onClick = onNewProject,
                modifier = Modifier.testTag(ProjectsListNewTag),
            )
        }
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

const val ProjectsListScreenTag = "projects.list"
const val ProjectsListTag = "projects.list.rows"
const val ProjectsListNewTag = "projects.list.new"
const val RemoveProjectConfirmationTag = "projects.remove.confirmation"
const val RemoveProjectConfirmTag = "projects.remove.confirm"
const val RemoveProjectCancelTag = "projects.remove.cancel"