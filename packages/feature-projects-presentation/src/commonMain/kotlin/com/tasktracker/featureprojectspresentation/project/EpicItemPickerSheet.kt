package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import com.tasktracker.resources.epic_subtasks_empty
import org.jetbrains.compose.resources.stringResource

/**
 * Picks work items to link to the saved epic.
 *
 * Candidates are already scoped to the projects the epic spans and exclude items linked to
 * this same epic; an item in *another* epic is offered, because the server accepts a link that
 * replaces the old one and the picker should not be narrower than the rule. The sheet stays
 * open across picks so a story can collect several tasks in one visit.
 */
@Composable
fun EpicItemPickerSheet(
    items: List<WorkItem>,
    visible: Boolean,
    onPick: (WorkItemID) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
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
                .testTag(EpicItemPickerTag)
                .padding(ttSpacing.formStack),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = stringResource(Res.string.epic_link_items),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            if (items.isEmpty()) {
                ttText(
                    text = stringResource(Res.string.epic_subtasks_empty),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag(EpicItemPickerRowsTag),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    role = Role.Button,
                                    onClick = { onPick(item.id) },
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
                            ttText(
                                text = item.title,
                                style = ttTypography.callout,
                                color = ttLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
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

fun EpicItemPickerRowTag(workItemID: String): String = "projects.epic.picker.items.row.$workItemID"
