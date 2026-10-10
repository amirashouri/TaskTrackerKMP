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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.WorkItemID
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
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.sprint_picker_empty
import com.tasktracker.resources.work_item_depends_done
import com.tasktracker.resources.work_item_depends_picker_title
import com.tasktracker.resources.work_item_depends_search_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
fun WorkItemDependencyPickerSheet(
    candidates: List<WorkItem>,
    selectedIDs: Set<WorkItemID>,
    onToggle: (WorkItemID) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val filtered = remember(candidates, trimmed) {
        if (trimmed.isEmpty()) candidates
        else candidates.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.key.toString().contains(trimmed, ignoreCase = true)
        }
    }
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(WorkItemDependsPickerTag),
        onDismissed = onDismissed,
        // The list below is a `LazyColumn` and stays one. A blocker list is unbounded in
        // principle and long in practice, so it should not hold every row's composition the
        // way a `Column` in the scaffold's scroll would.
        scrollable = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
            ttText(
                text = stringResource(Res.string.work_item_depends_picker_title),
                style = ttTypography.title,
                color = ttLabel(),
            )
            ProjectsTextField(
                title = stringResource(Res.string.work_item_depends_search_placeholder),
                value = query,
                onValueChange = { query = it },
                imeAction = ImeAction.Search,
                modifier = Modifier.testTag(WorkItemDependsSearchTag),
            )
            if (filtered.isEmpty()) {
                ttText(
                    text = stringResource(Res.string.sprint_picker_empty),
                    style = ttTypography.caption,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(WorkItemDependsPickerEmptyTag),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag(WorkItemDependsPickerListTag),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    items(filtered, key = { it.id.stringValue }) { item ->
                        val isSelected = item.id in selectedIDs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ttRadius.row))
                                .ttGlassCard()
                                .clickable(onClick = { onToggle(item.id) })
                                .semantics { selected = isSelected }
                                .padding(ttSpacing.xl)
                                .testTag(WorkItemDependsPickerRowTag(item)),
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
                                    maxLines = 2,
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
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f).testTag(WorkItemDependsPickerCancelTag),
                )
                ttGlassButton(
                    label = stringResource(Res.string.work_item_depends_done),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).testTag(WorkItemDependsPickerAddTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                    enabled = selectedIDs.isNotEmpty() && !isBusy,
                )
            }
        }
    }
}

const val WorkItemDependsPickerTag = "workItem.depends.picker"
const val WorkItemDependsSearchTag = "workItem.depends.picker.search"
const val WorkItemDependsPickerListTag = "workItem.depends.picker.rows"
const val WorkItemDependsPickerEmptyTag = "workItem.depends.picker.empty"
const val WorkItemDependsPickerAddTag = "workItem.depends.picker.add"
const val WorkItemDependsPickerCancelTag = "workItem.depends.picker.cancel"

fun WorkItemDependsPickerRowTag(item: WorkItem): String = "workItem.depends.picker.row.${item.key}"
