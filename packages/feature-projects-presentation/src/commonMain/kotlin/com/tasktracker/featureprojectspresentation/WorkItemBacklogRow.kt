package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ThemeScheme
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
import com.tasktracker.resources.backlog_put_on_board
import com.tasktracker.resources.work_item_archive
import org.jetbrains.compose.resources.stringResource

/**
 * A task as a row — the backlog's or, with [onOpen] set, a board's.
 *
 * ## Why this is not [WorkItemCard]
 *
 * A backlog item has not been started, so it has no column, no status worth showing, and -
 * in practice - a title long enough that a fixed-width card would either truncate it or
 * grow taller than the row it sits in. A full-width row lets a long title use the width
 * actually available, which on a phone is most of the screen.
 *
 * The action is the whole point of the row, so both of the backlog's verbs are on it rather
 * than behind a selection. Triage is two decisions, and a user holding a phone makes them
 * one after the other per item; making them require selecting, then acting, then
 * deselecting doubles the taps for the common case.
 *
 * ## Why the row doubles as the Kanban section's row
 *
 * The backlog tab's Kanban section shows the same full-width rows, but the verbs are
 * different: a task is already on a board, so "put on board" is meaningless, and its open
 * is the whole row's tap. [onOpen] makes the row open on tap in both sections — the screen
 * passes it for the board's rows *and* the backlog's — and the put-on-board action shows
 * for untriaged items only, `item.status == WorkItemStatus.Backlog`, so it cannot leak into
 * the Kanban or archived sections.
 *
 * The row's *body* is never a triage target. "Put on board" is always the explicit button:
 * a tap that silently filed the item out of the list would look like the task vanished, and
 * [onPutOnBoard] would be wired to a gesture the user did not intend. The two rows share the
 * glass card and the key/priority header because they are the same row read in two piles.
 */
@Composable
fun WorkItemBacklogRow(
    item: WorkItem,
    onPutOnBoard: () -> Unit,
    onFileAway: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    onOpen: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .clickable(enabled = !isBusy, onClick = onOpen ?: {})
            .padding(ttSpacing.xl)
            .testTag(WorkItemBacklogRowTag(item)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.Top,
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
                    color = ttLabel(),
                    maxLines = BACKLOG_TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PriorityBadge(priority = item.priority)
        }
        item.dueDate?.let { due ->
            ttText(
                text = dueDateLabel(due),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            if (item.status == WorkItemStatus.Backlog) {
                BacklogAction(
                    label = stringResource(Res.string.backlog_put_on_board),
                    isBusy = isBusy,
                    onClick = onPutOnBoard,
                    testTag = BacklogPutOnBoardTag(item),
                )
            }
            BacklogAction(
                label = stringResource(Res.string.work_item_archive),
                isBusy = isBusy,
                onClick = onFileAway,
                testTag = BacklogFileAwayTag(item),
            )
        }
    }
}

/**
 * One of the row's two buttons.
 *
 * A glass button rather than a text link, because both are the row's only other tap
 * targets and a link would be easy to miss next to the row's own tap handler. [isBusy]
 * disables both rather than just the one in flight: the two actions conflict, and a user
 * who can still press "file away" while a put-on-board is saving is being offered a race.
 */
@Composable
private fun BacklogAction(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    // `ttGlassButton` takes no `enabled`, so a disabled press is swallowed here rather
    // than styled into looking disabled while still firing. A button that *looks* inert
    // and is not is worse than one that looks live and briefly does nothing.
    ttGlassButton(
        label = label,
        onClick = { if (!isBusy) onClick() },
        modifier = modifier.testTag(testTag),
        style = if (isBusy) TTGlassButtonStyle() else TTGlassButtonStyle(tint = ttAccent(), prominent = true),
    )
}

@Preview
@Composable
private fun WorkItemBacklogRowPreview() = ProjectsPreview(ThemeScheme.Dark) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ViewPreviewData.backlogProjection().items
            .filter { it.status == com.tasktracker.domain.WorkItemStatus.Backlog }
            .forEach { workItem ->
            WorkItemBacklogRow(
                item = workItem,
                onPutOnBoard = {},
                onFileAway = {},
            )
        }
    }
}

@Preview
@Composable
private fun WorkItemBacklogRowBusyPreview() = ProjectsPreview(ThemeScheme.Light) {
    WorkItemBacklogRow(
        item = ViewPreviewData.workItems.first(),
        onPutOnBoard = {},
        onFileAway = {},
        isBusy = true,
    )
}

const val WorkItemBacklogRowTagPrefix = "backlog.row"

/** Suffixed with the key, for the same reason a card's tag is. */
fun WorkItemBacklogRowTag(item: WorkItem): String = "$WorkItemBacklogRowTagPrefix.${item.key}"

fun BacklogPutOnBoardTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.putOnBoard"

fun BacklogFileAwayTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.fileAway"

private const val BACKLOG_TITLE_MAX_LINES = 2
