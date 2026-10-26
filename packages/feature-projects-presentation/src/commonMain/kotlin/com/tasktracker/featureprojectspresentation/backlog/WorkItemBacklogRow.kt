package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectspresentation.WorkItemStatusBadge
import com.tasktracker.featureprojectspresentation.statusLabel
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_put_on_board
import com.tasktracker.resources.sprint_remove_from_sprint
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_copy
import com.tasktracker.resources.archive_reopen
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
 *
 * ## Archived items get Reopen instead of File away
 *
 * An archived item has no put-on-board and no file-away — it is already closed. The only
 * action that makes sense is to reopen it back into the backlog, which [onUnarchive] provides
 * when the caller is the archived section. The row stays the same shape; only the action row
 * swaps its second button for Reopen.
 *
 * ## In a sprint the verb is Remove, and it is the only one
 *
 * [onRemoveFromSprint] switches the action row into the sprint detail's mode: one button
 * that sends the task back to the backlog, and none of the backlog's verbs. They would be
 * meaningless there — "put on board" is triage for an unassigned task, and this one is
 * committed — and "file away" (archive) is a backlog/board decision the sprint's own list
 * has no business making. The caller passes `null` for every callback it does not want,
 * and a locked sprint (active or finished) passes [onRemoveFromSprint] as `null` too, so
 * the row renders read-only rather than offering a press that would be refused.
 *
 * ## Copy is an icon, and why it wraps
 *
 * Copy acts on the item's own text rather than triaging it, so it trails the action row as a
 * 44dp icon instead of joining the two labelled verbs. The width is the reason: at 320pt the
 * row has 264dp of content, the two labels' outer widths total ~223dp, and the icon plus one
 * 6dp gap needs 273dp. Forcing all three onto one row measured the icon at 35dp — under the
 * 44dp tap target — so the row is a `FlowRow`: the icon stays on the same line, at the trailing
 * edge, when there is room (a 360pt phone has 304dp), and wraps to its own line at the leading
 * edge when there is not. [onCopy] is nullable so a surface that does not own a clipboard — the
 * sprint detail — draws no dead control.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkItemBacklogRow(
    item: WorkItem,
    onPutOnBoard: () -> Unit,
    onFileAway: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onRemoveFromSprint: (() -> Unit)? = null,
    /**
     * Hands the row's text to the host, which owns the clipboard and the confirmation.
     *
     * `null` on a surface that has no clipboard to write to — the sprint detail — so the row
     * draws no copy control there rather than a press that silently does nothing.
     */
    onCopy: ((String) -> Unit)? = null,
    /**
     * The project's tag catalog, resolved by the host.
     *
     * The row resolves its own [WorkItem.tagIDs] against it, so the icons follow a rename or
     * recolour an administrator makes without rewriting the item. Empty draws nothing, which
     * is the same shape [onCopy]'s null uses: no row, no gap.
     */
    projectTags: List<ProjectTag> = emptyList(),
    /**
     * Whether to show the workflow status badge.
     *
     * Only the Kanban section sets this to true — backlog items are triaged (not worked),
     * and archived items are already resolved. Defaults to false so existing callers
     * (Backlog, Archived, Sprint) do not render it.
     */
    showStatusBadge: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            // Disabled, not inert: a locked sprint's row has no `onOpen`, and a press that
            // ripples and does nothing reads as a broken app rather than a locked one.
            .clickable(
                enabled = onOpen != null && !isBusy,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOpen ?: {},
            )
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
            if (showStatusBadge) {
                WorkItemStatusBadge(status = item.status)
            }
        }
        item.dueDate?.let { due ->
            ttText(
                text = dueDateLabel(due),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
        // The tag strip, leading-aligned above the actions: the icons are a property of the
        // task, and the row's last line stays reserved for what you can do with it.
        WorkItemTagIcons(tags = projectTags.resolveFor(item.tagIDs))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (onRemoveFromSprint != null) {
                // Sprint mode replaces the whole action row rather than joining it: see
                // the class KDoc. `null` is the switch, so the backlog's own branches below
                // are unreachable while a sprint caller holds the row.
                BacklogAction(
                    label = stringResource(Res.string.sprint_remove_from_sprint),
                    isBusy = isBusy,
                    onClick = onRemoveFromSprint,
                    testTag = BacklogRemoveFromSprintTag(item),
                )
            } else {
                if (item.status == WorkItemStatus.Backlog) {
                    BacklogAction(
                        label = stringResource(Res.string.backlog_put_on_board),
                        isBusy = isBusy,
                        onClick = onPutOnBoard,
                        testTag = BacklogPutOnBoardTag(item),
                    )
                }
                if (item.status == WorkItemStatus.Closed && onUnarchive != null) {
                    BacklogAction(
                        label = stringResource(Res.string.archive_reopen),
                        isBusy = isBusy,
                        onClick = onUnarchive,
                        testTag = BacklogUnarchiveTag(item),
                    )
                } else if (item.status != WorkItemStatus.Closed) {
                    BacklogAction(
                        label = stringResource(Res.string.work_item_archive),
                        isBusy = isBusy,
                        onClick = onFileAway,
                        testTag = BacklogFileAwayTag(item),
                    )
                }
            }
            // Trailing when the line has room: the weight spacer takes the slack, so copy sits at
            // the far edge. When it does not (320pt), the icon wraps rather than being squeezed
            // below its 44dp tap target. It stays live while [isBusy] because, unlike the triage
            // pair, copying does not race the write in flight - the text is already in memory.
            if (onCopy != null) {
                Spacer(Modifier.weight(1f))
                ttGlassIconButton(
                    image = ttActionIcons.copy,
                    contentDescription = stringResource(Res.string.work_item_copy),
                    onClick = { onCopy(clipboardText(item)) },
                    modifier = Modifier.testTag(BacklogCopyTag(item)),
                )
            }
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

const val WorkItemBacklogRowTagPrefix = "backlog.row"

/** Suffixed with the key, for the same reason a card's tag is. */
fun WorkItemBacklogRowTag(item: WorkItem): String = "$WorkItemBacklogRowTagPrefix.${item.key}"

fun BacklogPutOnBoardTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.putOnBoard"

fun BacklogFileAwayTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.fileAway"

fun BacklogUnarchiveTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.unarchive"

/** The sprint detail's one verb, for the same reason the others are named. */
fun BacklogRemoveFromSprintTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.removeFromSprint"

/**
 * The row's copy control.
 *
 * Suffixed with the key, unlike the card's: a backlog composes many rows at once, so a scenario
 * has to name the row it means.
 */
fun BacklogCopyTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.copy"

private const val BACKLOG_TITLE_MAX_LINES = 2
