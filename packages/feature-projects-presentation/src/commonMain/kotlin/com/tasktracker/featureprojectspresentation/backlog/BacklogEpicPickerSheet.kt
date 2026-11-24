package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Epic
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_link_epic
import com.tasktracker.resources.backlog_link_epic_empty
import com.tasktracker.resources.backlog_link_epic_loading
import org.jetbrains.compose.resources.stringResource

/**
 * Picks the epic a backlog item belongs to.
 *
 * The list is the host's: only active epics that span the item's project are offered, which is
 * the same rule `SetWorkItemEpic` enforces on the way in. Choosing one closes the sheet; there
 * is no separate confirmation because the row tap is the whole decision.
 *
 * A sheet rather than a page: this is one choice over the list the user is already reading, and
 * it has no fields of its own.
 */
@Composable
fun BacklogEpicPickerSheet(
    epics: List<Epic>,
    visible: Boolean,
    isLoading: Boolean,
    onSelect: (Epic) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit = {},
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
                .padding(ttSpacing.formStack)
                .testTag(BacklogEpicPickerTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = stringResource(Res.string.backlog_link_epic),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            when {
                isLoading -> ttText(
                    text = stringResource(Res.string.backlog_link_epic_loading),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
                epics.isEmpty() -> ttText(
                    text = stringResource(Res.string.backlog_link_epic_empty),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BacklogEpicPickerRowsTag),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    epics.forEach { epic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    role = Role.Button,
                                    onClick = { onSelect(epic) },
                                )
                                .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.md)
                                .testTag(BacklogEpicPickerRowTag(epic.id.stringValue)),
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ttText(
                                text = epic.title,
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
        }
    }
}

const val BacklogEpicPickerTag = "backlog.epic.picker"
const val BacklogEpicPickerRowsTag = "backlog.epic.picker.rows"

fun BacklogEpicPickerRowTag(epicID: String): String = "backlog.epic.picker.row.$epicID"
