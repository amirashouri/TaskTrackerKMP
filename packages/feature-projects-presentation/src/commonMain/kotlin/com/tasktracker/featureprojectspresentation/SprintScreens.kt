package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.designsystem.TTDateField
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTStoryPoints
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.ttAccent
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
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.domain.SprintSummary
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.sprintSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_save
import com.tasktracker.resources.sprint_achieved
import com.tasktracker.resources.sprint_committed
import com.tasktracker.resources.sprint_editor_edit_title
import com.tasktracker.resources.sprint_editor_new_title
import com.tasktracker.resources.sprint_end_before_start_field
import com.tasktracker.resources.sprint_end_label
import com.tasktracker.resources.sprint_finish
import com.tasktracker.resources.sprint_finished
import com.tasktracker.resources.sprint_goal_label
import com.tasktracker.resources.sprint_goal_placeholder
import com.tasktracker.resources.sprint_items_empty
import com.tasktracker.resources.sprint_items_title
import com.tasktracker.resources.sprint_list_empty_body
import com.tasktracker.resources.sprint_list_empty_title
import com.tasktracker.resources.sprint_list_new
import com.tasktracker.resources.sprint_list_title
import com.tasktracker.resources.sprint_name_label
import com.tasktracker.resources.sprint_open
import com.tasktracker.resources.sprint_owner_label
import com.tasktracker.resources.sprint_owner_none
import com.tasktracker.resources.sprint_owner_only
import com.tasktracker.resources.sprint_progress
import com.tasktracker.resources.sprint_reopen
import com.tasktracker.resources.sprint_start_label
import com.tasktracker.resources.sprint_summary_done
import com.tasktracker.resources.sprint_summary_empty
import com.tasktracker.resources.sprint_summary_hit
import com.tasktracker.resources.sprint_summary_incomplete
import com.tasktracker.resources.sprint_summary_miss
import com.tasktracker.resources.sprint_summary_no_goal
import com.tasktracker.resources.sprint_summary_none
import com.tasktracker.resources.sprint_summary_title
import com.tasktracker.resources.sprint_view_summary
import org.jetbrains.compose.resources.stringResource

/**
 * A project's sprints, as a list.
 *
 * ## Why the create action is *absent* when a sprint is open, rather than disabled
 *
 * The one-open rule is the store's to enforce, and it refuses with a message naming the
 * sprint that is still open. Showing a greyed-out button for that state is worse than
 * showing nothing: it makes the screen look broken without saying why, and the reason is
 * one sentence away — "X is still open". A user who needs to create a sprint is told
 * which one to finish, which is the thing they actually have to do next.
 *
 * So [canCreate] is a fact the list already holds and the button is drawn from it. The
 * store's refusal is still there for the race — another device opened one between this
 * read and the press — and it arrives as the usual banner.
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
    rows: List<SprintListRow>,
    isLoading: Boolean,
    isEmpty: Boolean?,
    canCreate: Boolean,
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
                // Absent rather than disabled when a sprint is already open — see the class
                // comment. A disabled control with no explanation is worse than no control.
                if (canCreate) {
                    ttGlassIconButton(
                        image = ttActionIcons.add,
                        contentDescription = stringResource(Res.string.sprint_list_new),
                        onClick = onNewSprint,
                        modifier = Modifier.testTag(SprintListNewTag),
                    )
                }
            }
            errorMessage?.let { message ->
                ttBanner(message = message, onDismiss = onDismissError)
            }
            when {
                // `null` is still loading, and it is kept distinct from `true` because a
                // screen that claimed "no sprints" during the first read would flash an
                // empty state over a populated list on every cold start.
                isEmpty == true -> ProjectsEmptyState(
                    title = stringResource(Res.string.sprint_list_empty_title),
                    body = stringResource(Res.string.sprint_list_empty_body),
                )

                else -> LazyColumn(
                    modifier = Modifier.testTag(SprintListTag),
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
        }
    }
}

/**
 * One sprint, as a row: name, window, progress, and whether it is still open.
 *
 * ## The progress bar is a bar and the numbers are beside it
 *
 * The bar answers "how far along, roughly" at a glance and the two numbers answer it
 * exactly. Neither is redundant with the other: a bar that runs to 100% cannot say whether
 * that is 12 of 12 or 40 of 40, and a pair of numbers cannot be scanned down a column of
 * eight sprints in the time a bar takes.
 *
 * The fill is [ttAccent] and the track is [ttSeparator] — both existing tokens, no new
 * styling primitive. The bar carries no text, so the contrast requirement that applies to
 * the numerals beside it does not apply to it: it is a shape, read by length.
 *
 * [SprintListRow.progressFraction] being `null` — a sprint with no goal — draws the track
 * and no fill, which is the honest reading: there is no progress to show, and an empty bar
 * would look like a sprint that achieved nothing.
 */
@Composable
private fun SprintListRowView(
    row: SprintListRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .clickable(onClick = onClick)
            .padding(ttSpacing.xl)
            .testTag(SprintRowTag(row.sprint.id)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = row.sprint.name,
                style = ttTypography.headline,
                color = ttLabel(),
                maxLines = SPRINT_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SprintStatusBadge(isFinished = row.sprint.isFinished)
        }
        ttText(
            text = ttDateRange(row.startDate, row.endDate),
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
        )
        SprintProgressBar(fraction = row.progressFraction)
        ttText(
            // The two numbers rather than a percentage: `sprint_progress`'s KDoc gives the
            // reason, and it is that 5 of 12 is not 42% of anything.
            text = stringResource(
                Res.string.sprint_progress,
                row.achievedStoryPoints,
                row.sprint.goalStoryPoints,
            ),
            style = ttTypography.caption,
            color = ttBodyLabel(),
        )
    }
}

/**
 * The open/finished badge.
 *
 * A word, not a colour or a dot. "Open" and "Finished" are the two states a sprint is
 * read by, and a coloured dot is a legend the user has to learn; a word is the state. It
 * takes [ttLabelSecondary] rather than an accent because it is metadata beside the name,
 * not an action, and an accent badge would read as something to tap.
 */
@Composable
private fun SprintStatusBadge(
    isFinished: Boolean,
    modifier: Modifier = Modifier,
) {
    ttText(
        text = stringResource(
            if (isFinished) Res.string.sprint_finished else Res.string.sprint_open,
        ),
        style = ttTypography.captionSmall,
        color = ttLabelSecondary(),
        modifier = modifier.testTag(SprintStatusBadgeTag),
    )
}

/**
 * A progress bar, or an empty track when there is no goal.
 *
 * [fraction] is `null` for "no goal set" and may exceed `1f` for a sprint that beat its
 * target — see [SprintListRow.progressFraction] for why it is not clamped. The fill's
 * width is therefore allowed past the track's end, and the track is drawn first so the
 * fill reads as sitting *on* it rather than being cut off by it.
 */
@Composable
private fun SprintProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SPRINT_PROGRESS_BAR_HEIGHT)
            .clip(RoundedCornerShape(SPRINT_PROGRESS_BAR_RADIUS))
            .background(ttSeparator())
            .testTag(SprintProgressBarTag),
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtMost(SPRINT_PROGRESS_BAR_MAX_WIDTH))
                    .height(SPRINT_PROGRESS_BAR_HEIGHT)
                    .clip(RoundedCornerShape(SPRINT_PROGRESS_BAR_RADIUS))
                    .background(ttAccent()),
            )
        }
    }
}

/**
 * The create-and-edit form, as a sheet.
 *
 * ## One sheet for both modes
 *
 * Creating a sprint and editing one are the same four fields, and a user who has just
 * created a sprint is usually straight into changing its window — so they are one
 * composable with a mode flag, the way [NewProjectSheet] is. The title and the confirm
 * label follow the mode, for the same reason [ProjectEditorScreen] does it that way: a
 * user who cannot tell which form is open will save an edit expecting a new sprint.
 *
 * ## The dates are two `TTDateField`s and the error is under them
 *
 * The window rule ([SprintEditorModel.endIsBeforeStart]) is shown against the **end**
 * field rather than as a banner. A banner says something went wrong; a message under the
 * field says which field is wrong, and this is the only rule in the form that is about the
 * relationship between two inputs rather than about either one alone.
 */
@Composable
fun SprintEditorSheet(
    name: String,
    goalStoryPoints: Int?,
    startDate: LocalDate?,
    endDate: LocalDate?,
    owner: String,
    isCreating: Boolean,
    canSave: Boolean,
    endIsBeforeStart: Boolean?,
    onNameChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onStartDateChanged: (LocalDate) -> Unit,
    onEndDateChanged: (LocalDate) -> Unit,
    onOwnerChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
    today: LocalDate? = null,
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SprintEditorSheetTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
        ) {
            ttText(
                text = if (isCreating) {
                    stringResource(Res.string.sprint_editor_new_title)
                } else {
                    stringResource(Res.string.sprint_editor_edit_title, name)
                },
                style = ttTypography.title,
                color = ttLabel(),
            )
            ProjectsTextField(
                title = stringResource(Res.string.sprint_name_label),
                value = name,
                onValueChange = onNameChanged,
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(SprintEditorNameTag),
            )
            ProjectsTextField(
                title = stringResource(Res.string.sprint_goal_label),
                value = goalStoryPoints?.toString().orEmpty(),
                onValueChange = onGoalChanged,
                placeholder = stringResource(Res.string.sprint_goal_placeholder),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(SprintEditorGoalTag),
            )
            TTDateField(
                label = stringResource(Res.string.sprint_start_label),
                value = startDate,
                onValueChange = onStartDateChanged,
                today = today,
                modifier = Modifier.testTag(SprintEditorStartDateTag),
            )
            Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
                TTDateField(
                    label = stringResource(Res.string.sprint_end_label),
                    value = endDate,
                    onValueChange = onEndDateChanged,
                    today = today,
                    modifier = Modifier.testTag(SprintEditorEndDateTag),
                )
                // Under the field, not in a banner: this is the only rule in the form about
                // the relation between two inputs, so it belongs beside the one the user has
                // to change. A banner would say *what* is wrong and not *which field*.
                if (endIsBeforeStart == true) {
                    ttText(
                        text = stringResource(Res.string.sprint_end_before_start_field),
                        style = ttTypography.footnote,
                        color = ttError(),
                        modifier = Modifier.testTag(SprintEditorWindowErrorTag),
                    )
                }
            }
            ProjectsTextField(
                title = stringResource(Res.string.sprint_owner_label),
                value = owner,
                onValueChange = onOwnerChanged,
                imeAction = ImeAction.Done,
                modifier = Modifier.testTag(SprintEditorOwnerTag),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = onCancel,
                    modifier = Modifier.testTag(SprintEditorCancelTag),
                )
                ttGlassButton(
                    label = stringResource(Res.string.common_save),
                    onClick = onSave,
                    enabled = canSave && !isBusy,
                    modifier = Modifier.testTag(SprintEditorSaveTag),
                    style = TTGlassButtonStyle(prominent = true),
                )
            }
        }
    }
}

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge)
            .testTag(SprintDetailScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = detail.sprint.name,
            style = ttTypography.largeTitle,
            color = ttLabel(),
        )
        errorMessage?.let { message ->
            ttBanner(message = message, onDismiss = onDismissError)
        }
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
        ttText(
            text = stringResource(Res.string.sprint_items_title),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        if (detail.items.isEmpty()) {
            ttText(
                text = stringResource(Res.string.sprint_items_empty),
                style = ttTypography.footnote,
                // `ttBodyLabel`, for the reason `ProjectsEmptyState` gives: this is the only
                // thing on the screen when the sprint is empty, and it carries its meaning.
                color = ttBodyLabel(),
                modifier = Modifier.testTag(SprintDetailItemsEmptyTag),
            )
        } else {
            detail.items.forEach { item ->
                SprintItemRow(
                    key = item.key.toString(),
                    title = item.title,
                    storyPoints = item.storyPoints,
                )
            }
        }
    }
}

/**
 * One of the two totals, side by side.
 *
 * [SprintSummary]'s numbers rather than the summary's own goal — the goal is a target, not
 * an achievement, so it belongs beside both rather than being one of them. The points are
 * read through [TTStoryPoints] so a "no estimate" reading would look the same here as it
 * does on a card, rather than this screen inventing a fourth treatment of the absence.
 */
@Composable
private fun SprintTotal(
    label: String,
    points: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
    ) {
        ttText(text = label, style = ttTypography.captionSmall, color = ttLabelSecondary())
        ttText(
            text = points.toString(),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
    }
}

/** One task inside the sprint: its key, its title, and its estimate. */
@Composable
private fun SprintItemRow(
    key: String,
    title: String,
    storyPoints: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttText(
            text = key,
            style = ttTypography.key,
            color = ttLabelSecondary(),
        )
        ttText(
            text = title,
            style = ttTypography.callout,
            color = ttLabel(),
            maxLines = SPRINT_ITEM_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TTStoryPoints(points = storyPoints)
    }
}

/**
 * What a finished sprint came to.
 *
 * ## Every number here is read off [SprintSummary]
 *
 * This screen takes the domain's value and renders it. It does not compare
 * [SprintSummary.achievedStoryPoints] to [Sprint.sprint]'s goal to decide whether the goal
 * was hit — [SprintSummary.didHitGoal] is that answer, computed once in `:packages:domain`,
 * and `SprintGoal.didHitGoal`'s own KDoc names exactly this as the mistake it exists to
 * prevent ("that is the case a `>` gets wrong").
 *
 * **Three outcomes, not two.** `didHitGoal` is `null` when no goal was set, and that is a
 * third state rather than a `false`: a sprint nobody set a target for has not missed it.
 * A two-way rendering would have to claim one of the two, and the claim would be invented.
 */
@Composable
fun SprintSummaryScreen(
    summary: SprintSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge)
            .testTag(SprintSummaryScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttGlassIconButton(
                image = ttActionIcons.chevronLeft,
                contentDescription = stringResource(Res.string.common_cancel),
                onClick = onBack,
                modifier = Modifier.testTag(SprintSummaryBackTag),
            )
            ttText(
                text = stringResource(Res.string.sprint_summary_title, summary.sprint.name),
                style = ttTypography.largeTitle,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            SprintTotal(
                label = stringResource(Res.string.sprint_committed),
                points = summary.committedStoryPoints,
                modifier = Modifier.weight(1f),
            )
            SprintTotal(
                label = stringResource(Res.string.sprint_achieved),
                points = summary.achievedStoryPoints,
                modifier = Modifier.weight(1f),
            )
        }
        ttText(
            // The verdict, read from the value via [sprintVerdict] — see that function for
            // why the mapping is not written inline here. `ttOnAccent` would be wrong: it
            // is the ink *for* the accent fill, and this is ink on the canvas, so the
            // accent is only used as a tint for the "hit" case.
            text = when (sprintVerdict(summary.didHitGoal)) {
                SprintVerdict.Hit -> stringResource(Res.string.sprint_summary_hit)
                SprintVerdict.Miss -> stringResource(Res.string.sprint_summary_miss)
                SprintVerdict.NoGoal -> stringResource(Res.string.sprint_summary_no_goal)
            },
            style = ttTypography.titleSmall,
            color = when (sprintVerdict(summary.didHitGoal)) {
                SprintVerdict.Hit -> ttAccent()
                else -> ttLabelSecondary()
            },
            modifier = Modifier.testTag(SprintSummaryVerdictTag),
        )
        if (summary.isEmpty) {
            ttText(
                text = stringResource(Res.string.sprint_summary_empty),
                style = ttTypography.footnote,
                color = ttBodyLabel(),
                modifier = Modifier.testTag(SprintSummaryEmptyTag),
            )
        } else {
            SprintSummarySection(
                title = stringResource(Res.string.sprint_summary_done),
                items = summary.done,
                modifier = Modifier.testTag(SprintSummaryDoneTag),
            )
            SprintSummarySection(
                title = stringResource(Res.string.sprint_summary_incomplete),
                items = summary.incomplete,
                modifier = Modifier.testTag(SprintSummaryIncompleteTag),
            )
        }
    }
}

/** One of the summary's two lists. Renders its own "none" rather than an empty column. */
@Composable
private fun SprintSummarySection(
    title: String,
    items: List<WorkItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(text = title, style = ttTypography.titleSmall, color = ttLabel())
        if (items.isEmpty()) {
            ttText(
                text = stringResource(Res.string.sprint_summary_none),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        } else {
            items.forEach { item ->
                SprintItemRow(
                    key = item.key.toString(),
                    title = item.title,
                    storyPoints = item.storyPoints,
                )
            }
        }
    }
}

/**
 * What a sprint's summary says about its goal, as a decision rather than a sentence.
 *
 * ## Why this is a function and not a `when` inside the screen
 *
 * 23.5.4's whole promise is that the hit/miss is **read from [SprintSummary.didHitGoal]** and
 * never recomputed in the view. A `when` written inline in a composable states that
 * promise in a way only a rendered frame can check — and a rendered frame can only check
 * the wording, not the *source* of the verdict. So the mapping is a pure function here and
 * the test below asserts it directly.
 *
 * It is also where the third state has to be named. `didHitGoal` is `Boolean?`, and a
 * two-way rendering has to invent an answer for the `null`: claiming "missed" reports a
 * failure nobody committed to, and claiming "met" reports a success. Neither is true, so
 * [NoGoal] is a distinct outcome with its own string.
 */
internal enum class SprintVerdict {

    /** The sprint reached or passed its goal. */
    Hit,

    /** The sprint finished short of a goal it did set. */
    Miss,

    /** The sprint set no goal, so it neither met nor missed one. */
    NoGoal,
}

/**
 * Reads the verdict out of the summary's answer.
 *
 * The input is [SprintSummary.didHitGoal] and nothing else — no `achievedStoryPoints`, no
 * `goalStoryPoints`. That is the point: a function that took the two numbers as well would
 * be free to compare them, and the `>` that gets "exactly at the goal is a miss" wrong
 * would be back. `SprintGoal.didHitGoal`'s KDoc names that as the mistake the named
 * function exists to prevent.
 */
internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict = when (didHitGoal) {
    true -> SprintVerdict.Hit
    false -> SprintVerdict.Miss
    null -> SprintVerdict.NoGoal
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

private const val SPRINT_NAME_MAX_LINES = 2
private const val SPRINT_ITEM_TITLE_MAX_LINES = 2

/**
 * The bar's dimensions. Not `const`, because `dp` is a value class over a `Float` and
 * `const val` accepts only primitives and `String` — a progress bar's height as a plain
 * `6.dp` at each call site is two copies waiting to differ, so it is named here even
 * though the binding is not compile-time.
 */
private val SPRINT_PROGRESS_BAR_HEIGHT = 6.dp
private val SPRINT_PROGRESS_BAR_RADIUS = 3.dp

/** `fillMaxWidth` takes a fraction of 0..1; past 1 the layout would throw. */
private const val SPRINT_PROGRESS_BAR_MAX_WIDTH = 1f

/** The list screen, and its rows. */
const val SprintListScreenTag = "sprints.list"
const val SprintListTag = "sprints.list.rows"
const val SprintListNewTag = "sprints.list.new"
const val SprintStatusBadgeTag = "sprints.row.badge"
const val SprintProgressBarTag = "sprints.row.progress"

/** A sprint's own screen. */
const val SprintDetailScreenTag = "sprints.detail"
const val SprintDetailOwnerTag = "sprints.detail.owner"
const val SprintDetailOwnerNoteTag = "sprints.detail.ownerNote"
const val SprintDetailFinishTag = "sprints.detail.finish"
const val SprintDetailReopenTag = "sprints.detail.reopen"
const val SprintDetailSummaryTag = "sprints.detail.summary"
const val SprintDetailItemsEmptyTag = "sprints.detail.itemsEmpty"

/** The editor sheet. */
const val SprintEditorSheetTag = "sprints.editor"
const val SprintEditorNameTag = "sprints.editor.name"
const val SprintEditorGoalTag = "sprints.editor.goal"
const val SprintEditorStartDateTag = "sprints.editor.start"
const val SprintEditorEndDateTag = "sprints.editor.end"
const val SprintEditorWindowErrorTag = "sprints.editor.windowError"
const val SprintEditorOwnerTag = "sprints.editor.owner"
const val SprintEditorSaveTag = "sprints.editor.save"
const val SprintEditorCancelTag = "sprints.editor.cancel"

/** The summary screen. */
const val SprintSummaryScreenTag = "sprints.summary"
const val SprintSummaryBackTag = "sprints.summary.back"
const val SprintSummaryVerdictTag = "sprints.summary.verdict"
const val SprintSummaryEmptyTag = "sprints.summary.empty"
const val SprintSummaryDoneTag = "sprints.summary.done"
const val SprintSummaryIncompleteTag = "sprints.summary.incomplete"

/** A row's own tag, so a test means *this* sprint and not the list. */
fun SprintRowTag(sprintID: SprintID): String = "sprints.row.${sprintID.stringValue}"

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------
//
// Every preview here pins its scheme rather than using `ThemeScheme.System`, for the reason
// `ProjectsPreview`'s KDoc gives: a system-driven preview changes with the machine, and a
// screenshot taken on a light-mode machine cannot then be compared with one taken on a
// dark-mode one. The fixtures come from `ViewPreviewData.sprints` and never from
// `seedSampleDataIfNeeded` — 23.8 forbids extending the store's own sample data while
// `SeedingTest` is red, and a preview reaching for it would put that dependency back.

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

@Preview(name = "Sprint list", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintListScreenPreview() = ProjectsPreview(ThemeScheme.Light) {
    SprintListScreen(
        rows = previewSprintRows(),
        isLoading = false,
        isEmpty = false,
        canCreate = true,
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
        rows = emptyList(),
        isLoading = false,
        isEmpty = true,
        canCreate = true,
        errorMessage = null,
        onOpen = {},
        onNewSprint = {},
        onDismissError = {},
    )
}

@Preview(name = "Sprint editor, new", showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun SprintEditorSheetPreview() = ProjectsPreview(ThemeScheme.Light) {
    SprintEditorSheet(
        name = "Sprint 8",
        goalStoryPoints = 13,
        startDate = LocalDate(2026, 3, 30),
        endDate = LocalDate(2026, 4, 10),
        owner = "Amir",
        isCreating = true,
        canSave = true,
        endIsBeforeStart = false,
        onNameChanged = {},
        onGoalChanged = {},
        onStartDateChanged = {},
        onEndDateChanged = {},
        onOwnerChanged = {},
        onSave = {},
        onCancel = {},
        today = LocalDate(2026, 3, 16),
    )
}

@Preview(name = "Sprint editor, window backwards", showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun SprintEditorSheetInvalidPreview() = ProjectsPreview(ThemeScheme.Dark) {
    SprintEditorSheet(
        name = "Sprint 8",
        goalStoryPoints = 13,
        startDate = LocalDate(2026, 4, 10),
        endDate = LocalDate(2026, 4, 3),
        owner = "",
        isCreating = true,
        canSave = false,
        // The third state the rule has: a chosen end that is before a chosen start.
        endIsBeforeStart = true,
        onNameChanged = {},
        onGoalChanged = {},
        onStartDateChanged = {},
        onEndDateChanged = {},
        onOwnerChanged = {},
        onSave = {},
        onCancel = {},
        today = LocalDate(2026, 3, 16),
    )
}

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
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
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
        ),
        isBusy = false,
        errorMessage = null,
        onFinish = {},
        onReopen = {},
        onViewSummary = {},
        onDismissError = {},
    )
}

@Preview(name = "Sprint summary", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintSummaryScreenPreview() = ProjectsPreview(ThemeScheme.Light) {
    val sprint = ViewPreviewData.finishedSprint
    SprintSummaryScreen(
        summary = sprintSummary(sprint, ViewPreviewData.sprintWorkItems(sprint)),
        onBack = {},
    )
}

@Preview(name = "Sprint summary, empty", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun SprintSummaryScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Dark) {
    SprintSummaryScreen(summary = sprintSummary(ViewPreviewData.finishedSprint, emptyList()), onBack = {})
}
