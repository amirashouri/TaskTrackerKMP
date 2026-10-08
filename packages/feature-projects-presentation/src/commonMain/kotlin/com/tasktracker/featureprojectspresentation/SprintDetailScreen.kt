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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.SprintStatus
import kotlin.time.Instant
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttDateRange
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.sprintSummary
import kotlin.time.Duration.Companion.days
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
import com.tasktracker.resources.sprint_owner_none
import com.tasktracker.resources.sprint_owner_only
import com.tasktracker.resources.sprint_remove
import com.tasktracker.resources.sprint_remove_confirm
import com.tasktracker.resources.sprint_remove_confirm_body
import com.tasktracker.resources.sprint_reopen
import com.tasktracker.resources.sprint_view_summary
import org.jetbrains.compose.resources.stringResource

/**
 * One sprint: its goal, its window, its totals, and the tasks inside it.
 *
 * ## The totals are the summary's, and the screen does no arithmetic
 *
 * [SprintDetail.summary] arrives already computed by the domain, and this renders its two
 * numbers. A screen that re-summed them would be a second place for the arithmetic to
 * live, and the two would disagree the first time the done/incomplete split changed.
 *
 * ## The action button's condition is the model's, not the screen's
 *
 * [SprintDetail.status] is the sprint's lifecycle status — scheduled, in-progress,
 * finished, archived — derived from [Sprint.statusAt] on the read that built this value.
 * The screen renders the button that matches: remove for a planned sprint, finish for one
 * under way, archive for one that has closed, and nothing for one already filed away.
 *
 * Each action is gated behind a confirmation, because the three destructive outcomes —
 * removing a planned sprint, finishing one in progress, archiving a finished one — are
 * not reversible short of reopening or recreating, and a tap that does the wrong one of
 * those is the shape of a report that comes back as "I did not mean to".
 *
 * ## The details are a card, and the tasks are not
 *
 * The window, the two totals, the bar, the goal's sentence, the owner and the actions are
 * one fact — *this* sprint — and [ttGlassCard] is how this app groups one fact visually.
 * The edit affordance sits inside the card beside the window for the same reason: what it
 * opens edits is the card. The tasks below are
 * a separate list of separate things, so they keep the same card-per-row treatment they
 * have everywhere else rather than being folded into the sprint's own card, where a long
 * title would push the totals off the top of it.
 *
 * ## The add button is disabled, not absent — and the rows go inert with it
 *
 * [SprintDetail.canEditItems] is false for an active or finished sprint, and the header's
 * plus is dimmed and dead rather than hidden, unlike [SprintListScreen]'s create button.
 * The list hides its button because nothing on that screen explains a disabled control;
 * here the explanation is on the page — the window says the sprint is under way, and every
 * row below renders without its actions — so the dimmed button and the inert rows are one
 * legible state: *this sprint is frozen*. Both halves are gated from the same value, which
 * is why [onOpenTask] and [onRemoveTask] are dropped at this seam rather than left for the
 * destination to remember: a caller cannot offer an edit the rows do not have.
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
    onAddTasks: () -> Unit = {},
    /**
     * Opens the editor over this sprint. On the card rather than in the header, because
     * the card is what the edit changes: it holds the window, the totals from the goal, and
     * now the goal's own sentence — the header holds the name, which is one field of the
     * five and was not worth a second affordance for.
     *
     * Offered in every state, including finished: name, prose goal and owner stay editable
     * always (the window and the cap freeze with the start, and that lock lives in the
     * form — see [SprintEditorModel.windowLocked]). Gated only on [isBusy], for the same
     * reason the finish button is: a write in flight and a second sheet opening are a race.
     */
    onEdit: () -> Unit = {},
    onOpenTask: ((WorkItemID) -> Unit)? = null,
    onRemoveTask: ((WorkItemID) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // One reading of the gate for the whole screen: the button, the rows' taps and the
    // rows' remove verb all answer to it, so a state change cannot be applied to two of
    // the three.
    val editable = detail.canEditItems

    // The action the user has asked to confirm, or `null` when no dialog is showing.
    // Stored as a `SprintStatus` so the dialog can word itself for the right outcome
    // without the screen knowing the details of each one.
    var pendingAction by remember { mutableStateOf<SprintStatus?>(null) }

    // The confirmation dialog, drawn inline so it is part of the same semantics tree
    // and a UI test finds it with a tag like anything else.
    pendingAction?.let { action ->
        SprintActionConfirmation(
            sprintName = detail.sprint.name,
            action = action,
            onConfirm = {
                when (action) {
                    SprintStatus.Scheduled -> onRemove()
                    SprintStatus.InProgress -> onFinish()
                    SprintStatus.Finished -> onArchive()
                    SprintStatus.Archived -> {}
                }
                pendingAction = null
            },
            onDismiss = {
                pendingAction = null
                onDismissError()
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge)
            .testTag(SprintDetailScreenTag),
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
                // The goal in prose, absent rather than a placeholder when there is none:
                // a dash where a sentence would be reads as a missing value, and a sprint
                // with no goal sentence has not failed to have one — it never had one to
                // show. The number the bar below measures against is the cap, not this.
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
                    // The owner, or the fact that there is none. `sprint_owner_none` is a claim
                    // about what may be done with this sprint ("anyone can finish"), not merely a
                    // dash for a missing value — see `Sprint.owner`'s KDoc on why `null` is not a
                    // lock.
                    text = detail.sprint.owner ?: stringResource(Res.string.sprint_owner_none),
                    style = ttTypography.footnote,
                    color = ttBodyLabel(),
                    modifier = Modifier.testTag(SprintDetailOwnerTag),
                )
                if (!detail.sprint.isFinished && detail.hasOwner && !detail.isViewerTheOwner) {
                    ttText(
                        text = stringResource(
                            Res.string.sprint_owner_only,
                            detail.sprint.owner.orEmpty(),
                        ),
                        style = ttTypography.footnote,
                        color = ttBodyLabel(),
                        modifier = Modifier.testTag(SprintDetailOwnerNoteTag),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    when (detail.status) {
                        SprintStatus.Scheduled -> {
                            ttGlassButton(
                                label = stringResource(Res.string.sprint_remove),
                                onClick = { pendingAction = SprintStatus.Scheduled },
                                enabled = !isBusy,
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
                        SprintStatus.Archived -> {
                            // No action: an archived sprint is filed away. The reopen button
                            // is the reader's way back, on the summary screen.
                        }
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
                    // `ttBodyLabel`, for the reason `ProjectsEmptyState` gives: this is the only
                    // thing on the screen when the sprint is empty, and it carries its meaning.
                    color = ttBodyLabel(),
                    modifier = Modifier.testTag(SprintDetailItemsEmptyTag),
                )
            }
        } else {
            items(items = detail.items, key = { it.id.stringValue }) { item ->
                WorkItemBacklogRow(
                    item = item,
                    // The backlog's verbs are not offered in a sprint — see
                    // `WorkItemBacklogRow`'s KDoc on sprint mode — so the callbacks are
                    // present-but-inert and the remove verb carries the row.
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
}

/**
 * The confirmation dialog for the sprint's destructive actions.
 *
 * Drawn inline rather than through `Dialog`, for the same reason `RemoveProjectConfirmation`
 * is: Compose Multiplatform's `Dialog` does not render under `runComposeUiTest` on every
 * target, and a confirmation a test cannot see is a confirmation that cannot be asserted.
 *
 * The dialog words itself from the action, so the screen does not need to know which
 * sentence goes with which button — adding a fourth action is a new `when` branch here
 * and a new string, not a new parameter through the screen.
 */
@Composable
private fun SprintActionConfirmation(
    sprintName: String,
    action: SprintStatus,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.surface)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = shape)
            .padding(ttSpacing.formStack)
            .testTag(SprintActionConfirmationTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = when (action) {
                SprintStatus.Scheduled -> stringResource(Res.string.sprint_remove_confirm, sprintName)
                SprintStatus.InProgress -> stringResource(Res.string.sprint_finish_confirm, sprintName)
                SprintStatus.Finished -> stringResource(Res.string.sprint_archive_confirm, sprintName)
                SprintStatus.Archived -> sprintName
            },
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        ttText(
            text = when (action) {
                SprintStatus.Scheduled -> stringResource(Res.string.sprint_remove_confirm_body, sprintName)
                SprintStatus.InProgress -> stringResource(Res.string.sprint_finish_confirm_body, sprintName)
                SprintStatus.Finished -> stringResource(Res.string.sprint_archive_confirm_body, sprintName)
                SprintStatus.Archived -> sprintName
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
                label = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.testTag(SprintActionCancelTag),
            )
            ttGlassButton(
                label = when (action) {
                    SprintStatus.Scheduled -> stringResource(Res.string.sprint_remove)
                    SprintStatus.InProgress -> stringResource(Res.string.sprint_finish)
                    SprintStatus.Finished -> stringResource(Res.string.sprint_archive)
                    SprintStatus.Archived -> sprintName
                },
                onClick = onConfirm,
                modifier = Modifier.testTag(SprintActionConfirmTag),
                style = TTGlassButtonStyle(tint = ttError(), prominent = true),
            )
        }
    }
}

/**
 * The detail screen's progress, on the same rule as a list row's.
 *
 * `null` when the sprint set no goal — an open sprint with no target has no progress to
 * measure, and a bar at zero would say it achieved nothing.
 */
private fun sprintProgressFraction(detail: SprintDetail): Float? =
    if (detail.sprint.goalStoryPoints <= 0) {
        null
    } else {
        detail.summary.achievedStoryPoints.toFloat() / detail.sprint.goalStoryPoints.toFloat()
    }

/** A sprint's own screen. */
const val SprintDetailScreenTag = "sprints.detail"
const val SprintDetailCardTag = "sprints.detail.card"
const val SprintDetailAddTag = "sprints.detail.add"
const val SprintDetailEditTag = "sprints.detail.edit"
const val SprintDetailGoalTag = "sprints.detail.goal"
const val SprintDetailOwnerTag = "sprints.detail.owner"
const val SprintDetailOwnerNoteTag = "sprints.detail.ownerNote"
const val SprintDetailFinishTag = "sprints.detail.finish"
const val SprintDetailReopenTag = "sprints.detail.reopen"
const val SprintDetailSummaryTag = "sprints.detail.summary"
const val SprintDetailItemsEmptyTag = "sprints.detail.itemsEmpty"
const val SprintDetailRemoveTag = "sprints.detail.remove"
const val SprintDetailArchiveTag = "sprints.detail.archive"
const val SprintActionConfirmationTag = "sprints.detail.actionConfirmation"
const val SprintActionConfirmTag = "sprints.detail.actionConfirm"
const val SprintActionCancelTag = "sprints.detail.actionCancel"

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

// Pinned schemes, not `ThemeScheme.System`, and fixtures from `ViewPreviewData` rather than
// `seedSampleDataIfNeeded` — see `ProjectsPreview`'s KDoc and 23.8.

@Preview(name = "Sprint detail, open", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintDetailScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    val sprint = ViewPreviewData.openSprint
    val items = ViewPreviewData.sprintWorkItems(sprint)
    SprintDetailScreen(
        detail = SprintDetail(
            sprint = sprint,
            startDate = sprintLocalDate(sprint.startDate, ViewPreviewData.previewZone),
            endDate = sprintLocalDate(sprint.endDate, ViewPreviewData.previewZone),
            items = items,
            summary = sprintSummary(sprint, items),
            isViewerTheOwner = true,
            // A preview has no clock to ask, and the static window is shown as one still
            // to come — so the sheet's affordances render in their editable state.
            isActive = false,
            status = SprintStatus.Scheduled,
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
        onRemove = {},
        onArchive = {},
        // Present rather than defaulted away, so the preview shows the sprint-mode rows
        // (remove verb, tappable body) the real destination wires up.
        onOpenTask = {},
        onRemoveTask = {},
    )
}

@Preview(name = "Sprint detail, not the owner", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintDetailScreenOtherOwnerPreview() = ProjectsPreview(ThemeScheme.Light) {
    val sprint = ViewPreviewData.openSprint
    SprintDetailScreen(
        detail = SprintDetail(
            sprint = sprint,
            startDate = sprintLocalDate(sprint.startDate, ViewPreviewData.previewZone),
            endDate = sprintLocalDate(sprint.endDate, ViewPreviewData.previewZone),
            items = emptyList(),
            summary = sprintSummary(sprint, emptyList()),
            // The state that hides Finish and shows the note naming the owner instead.
            isViewerTheOwner = false,
            isActive = false,
            status = SprintStatus.InProgress,
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
        onRemove = {},
        onArchive = {},
    )
}

@Preview(name = "Sprint detail, finished", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintDetailScreenFinishedPreview() = ProjectsPreview(ThemeScheme.Dark) {
    val sprint = ViewPreviewData.finishedSprint
    SprintDetailScreen(
        detail = SprintDetail(
            sprint = sprint,
            startDate = sprintLocalDate(sprint.startDate, ViewPreviewData.previewZone),
            endDate = sprintLocalDate(sprint.endDate, ViewPreviewData.previewZone),
            items = emptyList(),
            summary = sprintSummary(sprint, emptyList()),
            isViewerTheOwner = true,
            isActive = false,
            status = SprintStatus.Finished,
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
        onRemove = {},
        onArchive = {},
    )
}
