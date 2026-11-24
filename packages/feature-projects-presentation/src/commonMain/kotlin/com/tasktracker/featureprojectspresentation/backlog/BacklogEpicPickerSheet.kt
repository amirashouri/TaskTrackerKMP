package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttEntityIcons
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Epic
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_link_epic
import com.tasktracker.resources.backlog_link_epic_choice_hint
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
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    epics.forEach { epic ->
                        EpicChoiceRow(
                            epic = epic,
                            onClick = { onSelect(epic) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One choice as a glass card, not a line of text.
 *
 * The sheet used to draw plain text rows with no surface or trailing affordance, so nothing on
 * screen said they could be tapped. The card and the epic glyph match the app's other
 * pickers, where a row is visibly a control before its label is read.
 */
@Composable
private fun EpicChoiceRow(
    epic: Epic,
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
                role = Role.Button,
                onClick = onClick,
            )
            .padding(ttSpacing.xl)
            .testTag(BacklogEpicPickerRowTag(epic.id.stringValue)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(EPIC_CHOICE_BADGE_SIZE)
                .clip(CircleShape)
                .background(ttAccent().copy(alpha = EPIC_CHOICE_BADGE_FILL_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            ttIcon(
                image = ttEntityIcons.epic,
                contentDescription = null,
                tint = ttAccent(),
                modifier = Modifier.size(EPIC_CHOICE_GLYPH_SIZE),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            ttText(
                text = epic.title,
                style = ttTypography.callout,
                color = ttLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ttText(
                text = stringResource(Res.string.backlog_link_epic_choice_hint),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
    }
}

const val BacklogEpicPickerTag = "backlog.epic.picker"
const val BacklogEpicPickerRowsTag = "backlog.epic.picker.rows"

fun BacklogEpicPickerRowTag(epicID: String): String = "backlog.epic.picker.row.$epicID"

private val EPIC_CHOICE_BADGE_SIZE = 36.dp
private val EPIC_CHOICE_GLYPH_SIZE = 20.dp
private const val EPIC_CHOICE_BADGE_FILL_ALPHA = 0.12f
