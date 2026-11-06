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
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.WorkItem
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
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
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_put_on_board_confirm
import com.tasktracker.resources.backlog_put_on_board_prompt_body
import com.tasktracker.resources.backlog_put_on_board_prompt_title
import com.tasktracker.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom-sheet prompt shown when "Put on board" needs a column chosen.
 *
 * The screen only opens this when the project has more than one board column and no remembered
 * default for it — see [ProjectBacklogScreen]. A single tap on a column is the commit: there is no
 * separate "done" because the question is exactly "which one", so a second control would be a click
 * the user did not need to make. Picking a column both boards the task there and, back in the
 * screen, remembers it for next time.
 */
@Composable
fun PutOnBoardSheet(
    item: WorkItem,
    statuses: List<StatusDefinition>,
    onConfirm: (StatusDefinition) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    visible: Boolean = true,
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        // Disable scaffold outer scroll so the inner list manages its own recycling.
        scrollable = false,
    ) {
        PutOnBoardContent(
            item = item,
            statuses = statuses,
            onConfirm = onConfirm,
            isBusy = isBusy,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
private fun PutOnBoardContent(
    item: WorkItem,
    statuses: List<StatusDefinition>,
    onConfirm: (StatusDefinition) -> Unit,
    isBusy: Boolean,
    onDismissRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PutOnBoardSheetTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.backlog_put_on_board_prompt_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ttText(
            text = stringResource(Res.string.backlog_put_on_board_prompt_body, item.title),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .testTag(PutOnBoardStatusListTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            items(statuses, key = { it.id }) { status ->
                PutOnBoardStatusRow(
                    definition = status,
                    onClick = { if (!isBusy) onConfirm(status) },
                    isBusy = isBusy,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismissRequest,
                modifier = Modifier.testTag(PutOnBoardCancelTag),
            )
        }
    }
}

@Composable
private fun PutOnBoardStatusRow(
    definition: StatusDefinition,
    onClick: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .then(
                if (isBusy) {
                    Modifier
                } else {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    )
                },
            )
            .semantics { selected = false }
            .padding(ttSpacing.xl)
            .testTag(PutOnBoardStatusRowTag(definition)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            ttText(
                text = statusLabel(definition),
                style = ttTypography.callout,
                color = if (isBusy) ttLabelSecondary() else ttLabel(),
                maxLines = PUT_ON_BOARD_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            ttText(
                text = statusLabel(definition.category),
                style = ttTypography.caption,
                color = ttLabelSecondary(),
            )
        }
    }
}

const val PutOnBoardSheetTag = "backlog.putOnBoardSheet"
const val PutOnBoardStatusListTag = "backlog.putOnBoardSheet.statuses"
const val PutOnBoardCancelTag = "backlog.putOnBoardSheet.cancel"

fun PutOnBoardStatusRowTag(definition: StatusDefinition): String = "backlog.putOnBoardSheet.status.${definition.id}"

private const val PUT_ON_BOARD_TITLE_MAX_LINES = 2
