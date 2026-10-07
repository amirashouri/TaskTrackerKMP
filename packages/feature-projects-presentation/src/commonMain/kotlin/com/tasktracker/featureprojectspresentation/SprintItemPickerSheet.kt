package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.sprint_picker_add
import com.tasktracker.resources.sprint_picker_empty
import com.tasktracker.resources.sprint_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * The multi-select sheet for putting tasks *into* a sprint.
 *
 * ## Why this confirms and [ProjectPickerSheet] does not
 *
 * The project switcher is one choice with no side effects beyond where you are looking, so
 * it applies on tap. This one writes membership: every selected row that gets added is a row
 * the sprint now owns, and the model refuses the write on a locked sprint. Committing on
 * each tap would mean the sheet is silently saving on every gesture, with no way to pick
 * three tasks without performing three writes (and three sync pushes) on the way. A confirm
 * row makes the addition one deliberate act over a selection the user can review first.
 *
 * The empty state is the picker saying the filter did its job rather than that something
 * is broken: [SprintDetailModel] only offers tasks with no sprint and not closed, so a
 * finished backlog has nothing to add.
 *
 * The frame comes from [TTBottomSheetScaffold], the same scaffold the project and task
 * editors take — there is no second spelling of "a modal sheet" in this package to drift.
 */
@Composable
fun SprintItemPickerSheet(
    items: List<WorkItem>,
    selectedIDs: Set<WorkItemID>,
    onToggle: (WorkItemID) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
        // A `LazyColumn`, for the same reason the project picker is one: the backlog is
        // unbounded in principle, and a `Column` in the scaffold's scroll would hold every
        // row's composition at once.
        scrollable = false,
    ) {
        SprintItemPickerContent(
            items = items,
            selectedIDs = selectedIDs,
            onToggle = onToggle,
            onConfirm = onConfirm,
            isBusy = isBusy,
            onDismissRequest = onDismissRequest,
        )
    }
}

/** The sheet's body, and only its body: the scaffold draws the frame and pads it. */
@Composable
private fun SprintItemPickerContent(
    items: List<WorkItem>,
    selectedIDs: Set<WorkItemID>,
    onToggle: (WorkItemID) -> Unit,
    onConfirm: () -> Unit,
    isBusy: Boolean,
    onDismissRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SprintPickerSheetTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.sprint_picker_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        if (items.isEmpty()) {
            ttText(
                text = stringResource(Res.string.sprint_picker_empty),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(SprintPickerEmptyTag),
            )
        } else {
            // Bounded by what the sheet has *left*, not by the sheet itself — and
            // `fill = false` so a short list still wraps its content and the sheet stays
            // compact instead of stretching to the budget with a hole under the rows.
            // Unweighted, a long backlog lets the LazyColumn claim the whole height
            // budget, the confirm row is then measured against the remainder (zero), and
            // Cancel/Add collapse to 0x0 — invisible, and a click on the node lands at
            // the root origin on the scrim, dismissing the sheet without writing
            // anything. The user's only way out is then the scrim or back, with the
            // selection gone.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag(SprintPickerListTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                items(items, key = { it.id.stringValue }) { item ->
                    SprintPickerRow(
                        item = item,
                        isSelected = item.id in selectedIDs,
                        onClick = { onToggle(item.id) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismissRequest,
                modifier = Modifier.testTag(SprintPickerCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.sprint_picker_add),
                onClick = onConfirm,
                modifier = Modifier.testTag(SprintPickerAddTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                // Dimmed rather than swallowed at press time, because "nothing selected"
                // and "still saving" are two states the user should be able to *see* before
                // pressing — a live-looking Add that ignores the tap reads as a broken app.
                enabled = selectedIDs.isNotEmpty() && !isBusy,
            )
        }
    }
}

/**
 * One pickable task: key and title, with the selection carried by weight, a checkmark, and
 * the `Selected` semantics — not by colour alone.
 */
@Composable
private fun SprintPickerRow(
    item: WorkItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .clickable(onClick = onClick)
            .semantics { selected = isSelected }
            .padding(ttSpacing.xl)
            .testTag(SprintPickerRowTag(item)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            ttText(
                text = item.key.toString(),
                style = ttTypography.key,
                color = ttLabelSecondary(),
            )
            ttText(
                text = item.title,
                style = ttTypography.callout,
                color = if (isSelected) ttAccent() else ttLabel(),
                maxLines = PICKER_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            ttText(
                text = "✓",
                style = ttTypography.callout,
                color = ttAccent(),
            )
        }
    }
}

@Preview
@Composable
private fun SprintItemPickerSheetPreview() = ProjectsPreview(ThemeScheme.Dark) {
    SprintItemPickerSheet(
        items = ViewPreviewData.workItems.filter { it.status != WorkItemStatus.Closed },
        selectedIDs = setOf(ViewPreviewData.workItems.first().id),
        onToggle = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun SprintItemPickerSheetEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    SprintItemPickerSheet(
        items = emptyList(),
        selectedIDs = emptySet(),
        onToggle = {},
        onConfirm = {},
    )
}

const val SprintPickerSheetTag = "sprints.picker"
const val SprintPickerListTag = "sprints.picker.rows"
const val SprintPickerEmptyTag = "sprints.picker.empty"
const val SprintPickerAddTag = "sprints.picker.add"
const val SprintPickerCancelTag = "sprints.picker.cancel"

/** Suffixed with the key, because two tasks can share a title and the key is unique per project. */
fun SprintPickerRowTag(item: WorkItem): String = "sprints.picker.row.${item.key}"

private const val PICKER_TITLE_MAX_LINES = 2
