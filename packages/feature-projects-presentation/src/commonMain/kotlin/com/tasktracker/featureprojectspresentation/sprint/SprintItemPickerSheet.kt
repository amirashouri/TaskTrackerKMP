package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
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
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.sprint_picker_add
import com.tasktracker.resources.sprint_picker_empty
import com.tasktracker.resources.sprint_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * Multi-select bottom sheet for adding unassigned backlog items to an active sprint.
 * Selections are committed in a single batch via [onConfirm].
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
        // Disable scaffold outer scroll so nested LazyColumn manages its own scroll and recycling
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
            // weight(1f, fill = false) bounds list height while ensuring action row remains visible
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
                enabled = selectedIDs.isNotEmpty() && !isBusy,
            )
        }
    }
}

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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
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
        ttText(
            text = statusLabel(item.status),
            style = ttTypography.caption,
            color = ttLabelSecondary(),
        )
        if (isSelected) {
            ttText(
                text = "✓",
                style = ttTypography.callout,
                color = ttAccent(),
            )
        }
    }
}

const val SprintPickerSheetTag = "sprints.picker"
const val SprintPickerListTag = "sprints.picker.rows"
const val SprintPickerEmptyTag = "sprints.picker.empty"
const val SprintPickerAddTag = "sprints.picker.add"
const val SprintPickerCancelTag = "sprints.picker.cancel"

fun SprintPickerRowTag(item: WorkItem): String = "sprints.picker.row.${item.key}"

private const val PICKER_TITLE_MAX_LINES = 2