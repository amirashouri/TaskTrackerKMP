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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBodyLabel
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
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_achieved
import com.tasktracker.resources.sprint_add_task
import com.tasktracker.resources.sprint_committed
import com.tasktracker.resources.sprint_finish
import com.tasktracker.resources.sprint_items_empty
import com.tasktracker.resources.sprint_items_title
import com.tasktracker.resources.sprint_owner_none
import com.tasktracker.resources.sprint_owner_only
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
 * ## The Finish button's condition is the model's, not the screen's
 *
 * [SprintDetail.canFinish] carries both halves of the rule — not already finished, and
 * either unowned or owned by the viewer. The screen renders what it is told, and the note
 * beside the button says *why* it is unavailable rather than leaving a greyed-out control
 * to be guessed at.
 *
 * The note names the owner, which is what makes an unavailable button legible: "only X can
 * finish this sprint" tells the reader what to do next, and "anybody can" tells them the
 * sprint has not been claimed.
 *
 * ## The details are a card, and the tasks are not
 *
 * The window, the two totals, the bar, the owner and the actions are one fact — *this*
 * sprint — and [ttGlassCard] is how this app groups one fact visually. The tasks below are
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
    onAddTasks: () -> Unit = {},
    onOpenTask: ((WorkItemID) -> Unit)? = null,
    onRemoveTask: ((WorkItemID) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // One reading of the gate for the whole screen: the button, the rows' taps and the
    // rows' remove verb all answer to it, so a state change cannot be applied to two of
    // the three.
    val editable = detail.canEditItems
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
                ttText(
                    text = ttDateRange(detail.startDate, detail.endDate),
                    style = ttTypography.callout,
                    color = ttLabelSecondary(),
                )
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
                    if (detail.canFinish) {
                        ttGlassButton(
                            label = stringResource(Res.string.sprint_finish),
                            onClick = onFinish,
                            enabled = !isBusy,
                            modifier = Modifier.testTag(SprintDetailFinishTag),
                        )
                    }
                    if (detail.sprint.isFinished) {
                        ttGlassButton(
                            label = stringResource(Res.string.sprint_reopen),
                            onClick = onReopen,
                            enabled = !isBusy,
                            modifier = Modifier.testTag(SprintDetailReopenTag),
                        )
                        if (detail.hasSummaryToShow) {
                            ttGlassButton(
                                label = stringResource(Res.string.sprint_view_summary),
                                onClick = onViewSummary,
                                modifier = Modifier.testTag(SprintDetailSummaryTag),
                            )
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
const val SprintDetailOwnerTag = "sprints.detail.owner"
const val SprintDetailOwnerNoteTag = "sprints.detail.ownerNote"
const val SprintDetailFinishTag = "sprints.detail.finish"
const val SprintDetailReopenTag = "sprints.detail.reopen"
const val SprintDetailSummaryTag = "sprints.detail.summary"
const val SprintDetailItemsEmptyTag = "sprints.detail.itemsEmpty"

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
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
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
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
    )
}
