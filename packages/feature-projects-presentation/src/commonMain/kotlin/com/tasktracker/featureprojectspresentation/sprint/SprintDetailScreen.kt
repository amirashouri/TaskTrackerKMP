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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttDateRange
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.SprintStatus
import com.tasktracker.domain.sprintSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_achieved
import com.tasktracker.resources.sprint_add_task
import com.tasktracker.resources.sprint_archive
import com.tasktracker.resources.sprint_archive_confirm
import com.tasktracker.resources.sprint_archive_confirm_body
import com.tasktracker.resources.sprint_committed
import com.tasktracker.resources.sprint_edit
import com.tasktracker.resources.sprint_finish
import com.tasktracker.resources.sprint_finish_confirm
import com.tasktracker.resources.sprint_finish_confirm_body
import com.tasktracker.resources.sprint_items_empty
import com.tasktracker.resources.sprint_items_title
import com.tasktracker.resources.sprint_lock
import com.tasktracker.resources.sprint_locked_note
import com.tasktracker.resources.sprint_owner_none
import com.tasktracker.resources.sprint_owner_only
import com.tasktracker.resources.sprint_remove
import com.tasktracker.resources.sprint_remove_confirm
import com.tasktracker.resources.sprint_remove_confirm_body
import com.tasktracker.resources.sprint_unlock
import com.tasktracker.resources.sprint_view_summary
import org.jetbrains.compose.resources.stringResource

/**
 * Sprint detail screen displaying timeline window, goal, story point progress,
 * lifecycle management actions (lock/finish/archive), and assigned work items.
 */
@Composable
fun SprintDetailScreen(
    detail: SprintDetail,
    isBusy: Boolean,
    errorMessage: String?,
    onFinish: () -> Unit,
    onReopen: () -> Unit,
    onViewSummary: () -> Unit,
    onDismissError: () -> Unit,
    onRemove: () -> Unit,
    onArchive: () -> Unit,
    /** Toggles sprint lock state. */
    onToggleLock: () -> Unit = {},
    onAddTasks: () -> Unit = {},
    /** Opens the sprint editor sheet. */
    onEdit: () -> Unit = {},
    onOpenTask: ((WorkItemID) -> Unit)? = null,
    onRemoveTask: ((WorkItemID) -> Unit)? = null,
    /** Display name of the sprint owner, or null if unassigned/unresolved. */
    ownerName: String? = null,
    modifier: Modifier = Modifier,
) {
    val editable = detail.canEditItems
    var pendingAction by remember { mutableStateOf<SprintStatus?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge)
                .testTag(SprintDetailScreenTag),
            contentPadding = PaddingValues(bottom = LocalTabBarScrollUnderInset.current + ttSpacing.screenGutterLarge + ttSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    ttText(
                        text = detail.sprint.name,
                        style = ttTypography.largeTitle,
                        color = ttLabel(),
                        modifier = Modifier.weight(1f),
                    )
                    ttGlassIconButton(
                        image = ttActionIcons.add,
                        contentDescription = stringResource(Res.string.sprint_add_task),
                        onClick = onAddTasks,
                        enabled = editable && !isBusy,
                        modifier = Modifier.testTag(SprintDetailAddTag),
                    )
                }
            }
            errorMessage?.let { message ->
                item { ttBanner(message = message, onDismiss = onDismissError) }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(ttRadius.row))
                        .ttGlassCard()
                        .padding(ttSpacing.xl)
                        .testTag(SprintDetailCardTag),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ttText(
                            text = ttDateRange(detail.startDate, detail.endDate),
                            style = ttTypography.callout,
                            color = ttLabelSecondary(),
                            modifier = Modifier.weight(1f),
                        )
                        ttGlassIconButton(
                            image = ttActionIcons.edit,
                            contentDescription = stringResource(Res.string.sprint_edit),
                            onClick = onEdit,
                            enabled = !isBusy,
                            modifier = Modifier.testTag(SprintDetailEditTag),
                        )
                    }
                    detail.sprint.goal?.let { goal ->
                        ttText(
                            text = goal,
                            style = ttTypography.callout,
                            color = ttLabel(),
                            modifier = Modifier.testTag(SprintDetailGoalTag),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    ) {
                        SprintTotal(
                            label = stringResource(Res.string.sprint_committed),
                            points = detail.summary.committedStoryPoints,
                            modifier = Modifier.weight(1f),
                        )
                        SprintTotal(
                            label = stringResource(Res.string.sprint_achieved),
                            points = detail.summary.achievedStoryPoints,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SprintProgressBar(fraction = sprintProgressFraction(detail))
                    ttText(
                        text = ownerName ?: stringResource(Res.string.sprint_owner_none),
                        style = ttTypography.footnote,
                        color = ttBodyLabel(),
                        modifier = Modifier.testTag(SprintDetailOwnerTag),
                    )
                    if (!detail.sprint.isFinished && detail.hasOwner && !detail.isViewerTheOwner) {
                        ttText(
                            text = stringResource(
                                Res.string.sprint_owner_only,
                                ownerName.orEmpty(),
                            ),
                            style = ttTypography.footnote,
                            color = ttBodyLabel(),
                            modifier = Modifier.testTag(SprintDetailOwnerNoteTag),
                        )
                    }
                    if (detail.sprint.isLocked && !detail.sprint.isFinished) {
                        ttText(
                            text = stringResource(Res.string.sprint_locked_note),
                            style = ttTypography.footnote,
                            color = ttBodyLabel(),
                            modifier = Modifier.testTag(SprintDetailLockedNoteTag),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                    ) {
                        if (!detail.sprint.isFinished && detail.canManageLock) {
                            ttGlassButton(
                                label = stringResource(
                                    if (detail.sprint.isLocked) {
                                        Res.string.sprint_unlock
                                    } else {
                                        Res.string.sprint_lock
                                    },
                                ),
                                onClick = onToggleLock,
                                enabled = !isBusy,
                                modifier = Modifier.testTag(SprintDetailLockTag),
                            )
                        }
                        when (detail.status) {
                            SprintStatus.Scheduled -> {
                                ttGlassButton(
                                    label = stringResource(Res.string.sprint_remove),
                                    onClick = { pendingAction = SprintStatus.Scheduled },
                                    enabled = !isBusy && !detail.sprint.isLocked,
                                    modifier = Modifier.testTag(SprintDetailRemoveTag),
                                )
                            }
                            SprintStatus.InProgress -> {
                                ttGlassButton(
                                    label = stringResource(Res.string.sprint_finish),
                                    onClick = { pendingAction = SprintStatus.InProgress },
                                    enabled = !isBusy,
                                    modifier = Modifier.testTag(SprintDetailFinishTag),
                                )
                            }
                            SprintStatus.Finished -> {
                                ttGlassButton(
                                    label = stringResource(Res.string.sprint_archive),
                                    onClick = { pendingAction = SprintStatus.Finished },
                                    enabled = !isBusy,
                                    modifier = Modifier.testTag(SprintDetailArchiveTag),
                                )
                                if (detail.hasSummaryToShow) {
                                    ttGlassButton(
                                        label = stringResource(Res.string.sprint_view_summary),
                                        onClick = onViewSummary,
                                        modifier = Modifier.testTag(SprintDetailSummaryTag),
                                    )
                                }
                            }
                            SprintStatus.Archived -> {}
                        }
                    }
                }
            }
            item {
                ttText(
                    text = stringResource(Res.string.sprint_items_title),
                    style = ttTypography.titleSmall,
                    color = ttLabel(),
                )
            }
            if (detail.items.isEmpty()) {
                item {
                    ttText(
                        text = stringResource(Res.string.sprint_items_empty),
                        style = ttTypography.footnote,
                        color = ttBodyLabel(),
                        modifier = Modifier.testTag(SprintDetailItemsEmptyTag),
                    )
                }
            } else {
                items(items = detail.items, key = { it.id.stringValue }) { item ->
                    WorkItemBacklogRow(
                        item = item,
                        onPutOnBoard = {},
                        onFileAway = {},
                        isBusy = isBusy,
                        onOpen = if (editable) onOpenTask?.let { open -> { open(item.id) } } else null,
                        onRemoveFromSprint = if (editable) {
                            onRemoveTask?.let { remove -> { remove(item.id) } }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        pendingAction?.let { action ->
            ConfirmPopup(
                title = when (action) {
                    SprintStatus.Scheduled -> stringResource(Res.string.sprint_remove_confirm, detail.sprint.name)
                    SprintStatus.InProgress -> stringResource(Res.string.sprint_finish_confirm, detail.sprint.name)
                    SprintStatus.Finished -> stringResource(Res.string.sprint_archive_confirm, detail.sprint.name)
                    SprintStatus.Archived -> detail.sprint.name
                },
                body = when (action) {
                    SprintStatus.Scheduled -> stringResource(Res.string.sprint_remove_confirm_body, detail.sprint.name)
                    SprintStatus.InProgress -> stringResource(Res.string.sprint_finish_confirm_body, detail.sprint.name)
                    SprintStatus.Finished -> stringResource(Res.string.sprint_archive_confirm_body, detail.sprint.name)
                    SprintStatus.Archived -> detail.sprint.name
                },
                confirmLabel = when (action) {
                    SprintStatus.Scheduled -> stringResource(Res.string.sprint_remove)
                    SprintStatus.InProgress -> stringResource(Res.string.sprint_finish)
                    SprintStatus.Finished -> stringResource(Res.string.sprint_archive)
                    SprintStatus.Archived -> detail.sprint.name
                },
                onConfirm = {
                    when (action) {
                        SprintStatus.Scheduled -> onRemove()
                        SprintStatus.InProgress -> onFinish()
                        SprintStatus.Finished -> onArchive()
                        SprintStatus.Archived -> {}
                    }
                    pendingAction = null
                },
                onCancel = {
                    pendingAction = null
                    onDismissError()
                },
                confirmTint = ttError(),
                tag = SprintActionConfirmationTag,
            )
        }
    }
}

/** Returns story point progress ratio, or null if no goal was set. */
private fun sprintProgressFraction(detail: SprintDetail): Float? =
    if (detail.sprint.goalStoryPoints <= 0) {
        null
    } else {
        detail.summary.achievedStoryPoints.toFloat() / detail.sprint.goalStoryPoints.toFloat()
    }

const val SprintDetailScreenTag = "sprints.detail"
const val SprintDetailCardTag = "sprints.detail.card"
const val SprintDetailAddTag = "sprints.detail.add"
const val SprintDetailEditTag = "sprints.detail.edit"
const val SprintDetailGoalTag = "sprints.detail.goal"
const val SprintDetailOwnerTag = "sprints.detail.owner"
const val SprintDetailOwnerNoteTag = "sprints.detail.ownerNote"
const val SprintDetailLockTag = "sprints.detail.lock"
const val SprintDetailLockedNoteTag = "sprints.detail.lockedNote"
const val SprintDetailFinishTag = "sprints.detail.finish"
const val SprintDetailReopenTag = "sprints.detail.reopen"
const val SprintDetailSummaryTag = "sprints.detail.summary"
const val SprintDetailItemsEmptyTag = "sprints.detail.itemsEmpty"
const val SprintDetailRemoveTag = "sprints.detail.remove"
const val SprintDetailArchiveTag = "sprints.detail.archive"
const val SprintActionConfirmationTag = "sprints.detail.actionConfirmation"