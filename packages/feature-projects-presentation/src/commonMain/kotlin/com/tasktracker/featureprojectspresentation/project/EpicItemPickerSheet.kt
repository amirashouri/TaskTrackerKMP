package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.EpicID
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_done
import com.tasktracker.resources.epic_link_items
import com.tasktracker.resources.epic_link_other
import com.tasktracker.resources.epic_link_other_named
import com.tasktracker.resources.epic_link_search_empty
import com.tasktracker.resources.epic_link_search_placeholder
import com.tasktracker.resources.epic_subtasks_empty
import org.jetbrains.compose.resources.stringResource

/**
 * Toggles work items linked to the saved epic.
 *
 * Candidates are the items in the projects the epic spans that are eligible to join it: terminal
 * and filed-away items are absent unless they are already linked here, so a finished subtask can
 * still be seen and unlinked. Items already linked here carry a checkmark and tapping one unlinks
 * it; items in *another* epic are offered unchecked, because the server accepts a link that
 * replaces the old one and the picker should not be narrower than the rule. Those rows say so
 * under their title, and a search field narrows the list by key or title. The sheet stays open
 * across taps so a story can revise its set in one visit.
 */
@Composable
fun EpicItemPickerSheet(
    items: List<WorkItem>,
    selectedEpicID: EpicID?,
    visible: Boolean,
    onToggle: (WorkItemID, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    epicTitles: Map<EpicID, String> = emptyMap(),
) {
    // Cleared on close so reopening the picker starts from the whole eligible set.
    var query by remember(visible) { mutableStateOf("") }
    val trimmed = query.trim()
    val shownItems = remember(items, trimmed) {
        if (trimmed.isEmpty()) {
            items
        } else {
            items.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.key.rawValue.contains(trimmed, ignoreCase = true)
            }
        }
    }
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EpicItemPickerTag)
                .padding(ttSpacing.formStack),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = stringResource(Res.string.epic_link_items),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ProjectsTextField(
                title = stringResource(Res.string.epic_link_search_placeholder),
                value = query,
                onValueChange = { query = it },
                imeAction = ImeAction.Search,
                modifier = Modifier.testTag(EpicItemPickerSearchTag),
            )
            if (shownItems.isEmpty()) {
                ttText(
                    text = stringResource(
                        if (trimmed.isEmpty()) {
                            Res.string.epic_subtasks_empty
                        } else {
                            Res.string.epic_link_search_empty
                        },
                    ),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
            } else {
                // The scaffold scrolls the card, so this list must not scroll again: the nested
                // scroll is measured with unbounded height and throws on open.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(EpicItemPickerRowsTag),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    shownItems.forEach { item ->
                        val isSelected = selectedEpicID != null && item.epicID == selectedEpicID
                        val otherEpicTitle = if (!isSelected) {
                            item.epicID?.let { epicTitles[it] }
                        } else {
                            null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    role = Role.Checkbox,
                                    onClick = { onToggle(item.id, isSelected) },
                                )
                                .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.md)
                                .testTag(EpicItemPickerRowTag(item.id.stringValue)),
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ttText(
                                text = item.key.rawValue,
                                style = ttTypography.captionEmphasised,
                                color = ttLabelSecondary(),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
                            ) {
                                ttText(
                                    text = item.title,
                                    style = ttTypography.callout,
                                    color = ttLabel(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.epicID != null && !isSelected) {
                                    ttText(
                                        text = if (otherEpicTitle != null) {
                                            stringResource(Res.string.epic_link_other_named, otherEpicTitle)
                                        } else {
                                            stringResource(Res.string.epic_link_other)
                                        },
                                        style = ttTypography.captionSmall,
                                        color = ttLabelSecondary(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag(
                                            EpicItemPickerOtherEpicTag(item.id.stringValue),
                                        ),
                                    )
                                }
                            }
                            if (isSelected) {
                                ttText(
                                    text = "\u2713",
                                    style = ttTypography.callout,
                                    color = ttLabel(),
                                    modifier = Modifier.testTag(
                                        EpicItemPickerSelectedTag(item.id.stringValue),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_done),
                    onClick = onDismissRequest,
                    style = TTGlassButtonStyle(prominent = true),
                    modifier = Modifier.testTag(EpicItemPickerDoneTag),
                )
            }
        }
    }
}

const val EpicItemPickerTag = "projects.epic.picker.items"
const val EpicItemPickerRowsTag = "projects.epic.picker.items.rows"
const val EpicItemPickerDoneTag = "projects.epic.picker.items.done"
const val EpicItemPickerSearchTag = "projects.epic.picker.items.search"

fun EpicItemPickerOtherEpicTag(workItemID: String): String =
    "projects.epic.picker.items.other.$workItemID"

fun EpicItemPickerRowTag(workItemID: String): String = "projects.epic.picker.items.row.$workItemID"
fun EpicItemPickerSelectedTag(workItemID: String): String = "projects.epic.picker.items.selected.$workItemID"
