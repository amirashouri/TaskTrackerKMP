package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTGlassBackButton
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
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
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_projects_empty
import com.tasktracker.resources.admin_projects_loading
import com.tasktracker.resources.admin_projects_segment_active
import com.tasktracker.resources.admin_projects_segment_archived
import com.tasktracker.resources.admin_projects_title
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_delete
import com.tasktracker.resources.project_new
import com.tasktracker.resources.project_remove_archive
import com.tasktracker.resources.project_remove_body_clear
import com.tasktracker.resources.project_remove_body_outstanding
import com.tasktracker.resources.project_remove_title
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Admin screen displaying server-wide projects segmented by [ProjectAdminStatus] (Active/Archived).
 * Direct online queries via [AdminProjectsPort].
 *
 * The row's trash removes the project through the admin API rather than through the member
 * list's local removal: an admin may not be a member of the project they are deleting, and the
 * server applies the delete through the sync engine so every member pulls it. The confirmation
 * is inline for the same reason the member list's is — it is testable on every host without a
 * platform dialog.
 */
/** The row a confirmation was opened for, with the unfinished count that chose its action. */
private data class PendingRemoval(
    val project: AdminProject,
    val openItemCount: Int,
)

@Composable
fun AdminProjectsScreen(
    port: AdminProjectsPort,
    onProjectSelected: (ProjectID) -> Unit,
    onBack: () -> Unit,
    onOpenAddForm: () -> Unit,
    modifier: Modifier = Modifier,
    showsAddAction: Boolean = true,
    /** Trigger key to re-fetch projects from server (preserves newest-first ordering). */
    refreshKey: Int = 0,
) {
    var status by remember { mutableStateOf(ProjectAdminStatus.Active) }
    var projects by remember { mutableStateOf<List<AdminProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<AppError?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var pendingRemoval by remember { mutableStateOf<PendingRemoval?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(status, refreshKey, reloadKey) {
        isLoading = true
        failure = null
        projects = try {
            port.projects(status)
        } catch (cancelled: CancellationException) {
            // Re-throw to allow coroutine cancellation to propagate cleanly
            throw cancelled
        } catch (error: AppError) {
            failure = error
            emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(AdminProjectsScreenTag)
            .padding(ttSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        AdminProjectsHeader(
            onBack = onBack,
            onOpenAddForm = onOpenAddForm,
            showsAddAction = showsAddAction,
        )

        TTGlassSegmentedPicker(
            options = ProjectAdminStatus.entries,
            selection = status,
            onSelect = { next -> status = next },
            title = { option ->
                when (option) {
                    ProjectAdminStatus.Active ->
                        stringResource(Res.string.admin_projects_segment_active)
                    ProjectAdminStatus.Archived ->
                        stringResource(Res.string.admin_projects_segment_archived)
                }
            },
        )

        val error = failure
        when {
            isLoading -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_loading),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            error != null -> {
                ttText(
                    text = error.message.orEmpty(),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            projects.isEmpty() -> {
                ttText(
                    text = stringResource(Res.string.admin_projects_empty, status.wireValue),
                    style = ttTypography.body,
                    color = ttLabel(),
                    modifier = Modifier.padding(ttSpacing.xl),
                )
            }
            else -> {
                // Weight, not fillMaxSize: the confirmation is a sibling below the list, and
                // a list that claimed the whole column would place it past the bottom edge —
                // present in the tree, never on screen, which is what the scenario caught.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(
                        items = projects,
                        key = { adminProject -> adminProject.project.id.stringValue },
                    ) { adminProject ->
                        ProjectRowView(
                            project = ProjectSummary(adminProject.project, itemCount = 0),
                            onOpen = { onProjectSelected(adminProject.project.id) },
                            onRemove = {
                                // The count decides what the confirmation may offer, so it
                                // is read before the card opens rather than after a tap the
                                // admin has already committed to.
                                val target = adminProject
                                scope.launch {
                                    try {
                                        pendingRemoval = PendingRemoval(
                                            project = target,
                                            openItemCount = port.openItemCount(target.project.id),
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (countFailure: AppError) {
                                        failure = countFailure
                                    }
                                }
                            },
                            onEdit = { onProjectSelected(adminProject.project.id) },
                        )
                    }
                }
            }
        }

        val pending = pendingRemoval
        if (pending != null) {
            AdminProjectRemovalConfirmation(
                projectName = pending.project.project.name,
                openItemCount = pending.openItemCount,
                onConfirm = {
                    // Cleared before the call, so a failed removal does not leave a
                    // confirmation card that reads as though the tap never landed; the
                    // failure surfaces through `failure` on the next read.
                    pendingRemoval = null
                    scope.launch {
                        try {
                            if (pending.openItemCount > 0) {
                                // Unfinished work is archived, not destroyed: the same rule
                                // the member list applies, and the reason this is a PATCH of
                                // the row the list already read rather than a delete.
                                port.updateProject(
                                    project = pending.project.project.copy(isArchived = true),
                                    version = pending.project.version,
                                )
                            } else {
                                port.deleteProject(pending.project.project.id)
                            }
                            reloadKey++
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (removalFailure: AppError) {
                            failure = removalFailure
                        }
                    }
                },
                onCancel = { pendingRemoval = null },
            )
        }
    }
}

/** Rendered inline rather than via a platform dialog, so it is testable on every host. */
@Composable
private fun AdminProjectRemovalConfirmation(
    projectName: String,
    openItemCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasOutstanding = openItemCount > 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.surface))
            .padding(ttSpacing.formStack)
            .testTag(AdminProjectRemoveConfirmationTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.project_remove_title, projectName),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        ttText(
            text = if (hasOutstanding) {
                stringResource(Res.string.project_remove_body_outstanding, openItemCount)
            } else {
                stringResource(Res.string.project_remove_body_clear)
            },
            style = ttTypography.callout,
            color = ttLabelSecondary(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onCancel,
                modifier = Modifier.testTag(AdminProjectRemoveCancelTag),
            )
            ttGlassButton(
                label = stringResource(
                    if (hasOutstanding) Res.string.project_remove_archive else Res.string.common_delete,
                ),
                onClick = onConfirm,
                modifier = Modifier.testTag(AdminProjectRemoveConfirmTag),
                style = TTGlassButtonStyle(tint = ttError(), prominent = true),
            )
        }
    }
}

@Composable
private fun AdminProjectsHeader(
    onBack: () -> Unit,
    onOpenAddForm: () -> Unit,
    showsAddAction: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        TTGlassBackButton(onBack = onBack)
        ttText(
            text = stringResource(Res.string.admin_projects_title),
            style = ttTypography.titleSmall,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        if (showsAddAction) {
            ttGlassIconButton(
                image = ttActionIcons.add,
                contentDescription = stringResource(Res.string.project_new),
                onClick = onOpenAddForm,
                modifier = Modifier.testTag(AdminProjectsAddTag),
            )
        }
    }
}

const val AdminProjectsScreenTag = "adminProjects.screen"
const val AdminProjectsAddTag = "adminProjects.add"
const val AdminProjectRemoveConfirmationTag = "adminProjects.remove.confirmation"
const val AdminProjectRemoveConfirmTag = "adminProjects.remove.confirm"
const val AdminProjectRemoveCancelTag = "adminProjects.remove.cancel"
