package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttOnAccent
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
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.archive_reopen
import com.tasktracker.resources.backlog_put_on_board
import com.tasktracker.resources.sprint_remove_from_sprint
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_copy
import org.jetbrains.compose.resources.stringResource

/**
 * Full-width task row used across Backlog, Kanban, Archive, and Sprint views.
 * Action buttons adapt to context: Backlog (Put on Board, Archive), Kanban (Archive), Archive
 * (Reopen), Sprint (Remove).
 *
 * A terminal row (`Done` or `Closed`) never offers "File away" — it is already filed away, and
 * `ArchiveWorkItem` refuses the write. Where a reopen handler is supplied it offers Reopen
 * instead, so the only way out of the archive is the one that works.
 *
 * ## Selecting
 *
 * In select mode the row is a checkbox, not a button: [selectable] gives it the role and the
 * `selected` state assistive technology reads, and the actions inside the card disappear because a
 * tap is now a tick and a row whose buttons still work would be two answers to one tap. The
 * per-row actions come back the moment the mode ends; nothing about them is lost, only hidden.
 * Long-press is the touch shortcut into the mode and stays off the accessibility tree, since a
 * screen reader's own "select" gesture is the checkbox this row already is.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    /** Invoked with item summary for clipboard copy; null draws no copy control. */
    onCopy: ((String) -> Unit)? = null,
    /** Project tag definitions used to resolve [WorkItem.tagIDs] into icons. */
    projectTags: List<ProjectTag> = emptyList(),
    /** Displays workflow status badge; enabled strictly for Kanban section rows. */
    showStatusBadge: Boolean = false,
    /** True while the list is in multi-select mode: taps tick instead of opening the editor. */
    isSelecting: Boolean = false,
    /** Whether this row is ticked. Only meaningful while [isSelecting]. */
    isSelected: Boolean = false,
    /**
     * Touch shortcut into select mode, seeded with this row. Null where the list has no selection
     * (Kanban, Archived, Sprint) and the gesture would do nothing.
     */
    onLongPress: (() -> Unit)? = null,
    /** Invoked by a tap while [isSelecting]. */
    onToggleSelected: (() -> Unit)? = null,
    /**
     * The owning project's status catalog, used to resolve a stored [WorkItem.statusKey] into the
     * coarse [WorkItemStatus] the badge and the action controls draw. Without it (the backlog,
     * archive and sprint rows pass none) the row falls back to the item's raw [WorkItem.status],
     * which is the right read there — those sections key off `status` directly. The Kanban section
     * is the one that can carry a `statusKey` whose lane disagrees with `status`, so only it passes
     * a project, and only there can the two diverge.
     */
    project: Project? = null,
) {
    // The same resolution the editor and the board grouping use: a stored lane id wins, the raw
    // status is the fallback. Reading `item.status` directly — what the badge used to do — is the
    // one path that disagreed with the editor and the board for an item whose two fields desynced.
    val effectiveStatus = remember(item, project) {
        project?.statusFor(item)?.category ?: item.status
    }
    val interactionSource = remember { MutableInteractionSource() }
    // Select mode answers the tap with a tick. `selectable` rather than `clickable` so the row
    // carries `Role.Checkbox` and a `selected` state, which is what a screen reader reads as
    // "checked" — the state a sighted user gets from the filled box at the trailing edge.
    val tapModifier = if (isSelecting) {
        Modifier.selectable(
            selected = isSelected,
            interactionSource = interactionSource,
            indication = null,
            enabled = !isBusy && onToggleSelected != null,
            role = Role.Checkbox,
            onClick = { onToggleSelected?.invoke() },
        )
    } else {
        Modifier.combinedClickable(
            enabled = !isBusy && (onOpen != null || onLongPress != null),
            interactionSource = interactionSource,
            indication = null,
            onClick = { onOpen?.invoke() },
            onLongClick = onLongPress,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .then(tapModifier)
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
                // Two lines here, where the board card keeps one: a row has the width and the
                // room to say what the item actually is, and the button row below it is what
                // needs the space to stay clear.
                //
                // The padding is what separates the snippet from its neighbours. The key and the
                // title are one block at `xs`; the snippet sits a step further from the title
                // than the title sits from the key, and a step further again from whatever the
                // row draws underneath it — the due date, the tag strip, or the buttons directly.
                item.details.trim().takeIf { it.isNotEmpty() }?.let { description ->
                    ttText(
                        text = description,
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        maxLines = BACKLOG_DESCRIPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = ttSpacing.sm, bottom = ttSpacing.sm)
                            .testTag(BacklogDescriptionTag(item)),
                    )
                }
            }
            PriorityBadge(priority = item.priority)
            if (showStatusBadge) {
                WorkItemStatusBadge(status = effectiveStatus)
            }
            if (isSelecting) {
                SelectionTickBox(
                    isSelected = isSelected,
                    modifier = Modifier.testTag(BacklogSelectionTickTag(item)),
                )
            }
        }
        item.dueDate?.let { due ->
            ttText(
                text = dueDateLabel(due),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
        WorkItemTagIcons(tags = projectTags.resolveFor(item.tagIDs))
        // While selecting, the card is one checkbox: per-row actions would be a second answer to
        // the tap that just ticked it, and a disabled cluster would read as an error.
        if (!isSelecting) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRemoveFromSprint != null) {
                    BacklogAction(
                        label = stringResource(Res.string.sprint_remove_from_sprint),
                        isBusy = isBusy,
                        onClick = onRemoveFromSprint,
                        testTag = BacklogRemoveFromSprintTag(item),
                    )
                } else {
                    if (effectiveStatus == WorkItemStatus.Backlog) {
                        BacklogAction(
                            label = stringResource(Res.string.backlog_put_on_board),
                            isBusy = isBusy,
                            onClick = onPutOnBoard,
                            testTag = BacklogPutOnBoardTag(item),
                        )
                    }
                    // Terminal items are already filed away, so "File away" would only ever be
                    // refused; a done task's way back is Reopen, the same as a closed one's.
                    if (effectiveStatus.isTerminal && onUnarchive != null) {
                        BacklogAction(
                            label = stringResource(Res.string.archive_reopen),
                            isBusy = isBusy,
                            onClick = onUnarchive,
                            testTag = BacklogUnarchiveTag(item),
                        )
                    } else if (!effectiveStatus.isTerminal) {
                        BacklogAction(
                            label = stringResource(Res.string.work_item_archive),
                            isBusy = isBusy,
                            onClick = onFileAway,
                            testTag = BacklogFileAwayTag(item),
                        )
                    }
                }
                // Copy stays active while busy: reads in-memory text without racing pending write mutations
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
}

/**
 * The tick at the trailing edge of a row in select mode.
 *
 * Drawn rather than built from a text glyph because it has to show three things at 20dp: the box
 * while unticked, the accent fill while ticked, and a tick that stays crisp at any density. An
 * empty rounded box is the same shape the outline glyph uses, so the pair reads as one control
 * whether or not it is ticked.
 */
@Composable
private fun SelectionTickBox(isSelected: Boolean, modifier: Modifier = Modifier) {
    val accent = ttAccent()
    val outline = ttLabelSecondary()
    Canvas(modifier = modifier.size(SELECTION_TICK_SIZE)) {
        val corner = CornerRadius(SELECTION_TICK_CORNER.toPx())
        if (isSelected) {
            drawRoundRect(color = accent, cornerRadius = corner)
            val tick = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.74f, size.height * 0.34f)
            }
            drawPath(
                path = tick,
                color = ttOnAccent,
                style = Stroke(
                    width = SELECTION_TICK_STROKE.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            drawRoundRect(
                color = outline,
                cornerRadius = corner,
                style = Stroke(width = SELECTION_TICK_BORDER.toPx()),
                size = Size(size.width, size.height),
            )
        }
    }
}

@Composable
private fun BacklogAction(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    // ttGlassButton lacks an enabled parameter; clicks are swallowed when busy
    ttGlassButton(
        label = label,
        onClick = { if (!isBusy) onClick() },
        modifier = modifier.testTag(testTag),
        style = if (isBusy) TTGlassButtonStyle() else TTGlassButtonStyle(tint = ttAccent(), prominent = true),
    )
}

const val WorkItemBacklogRowTagPrefix = "backlog.row"

fun WorkItemBacklogRowTag(item: WorkItem): String = "$WorkItemBacklogRowTagPrefix.${item.key}"
fun BacklogPutOnBoardTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.putOnBoard"
fun BacklogFileAwayTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.fileAway"
fun BacklogUnarchiveTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.unarchive"
fun BacklogRemoveFromSprintTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.removeFromSprint"
fun BacklogCopyTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.copy"
fun BacklogDescriptionTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.description"
fun BacklogSelectionTickTag(item: WorkItem): String = "${WorkItemBacklogRowTag(item)}.selected"

private val SELECTION_TICK_SIZE = 20.dp
private val SELECTION_TICK_CORNER = 5.dp
private val SELECTION_TICK_STROKE = 2.dp
private val SELECTION_TICK_BORDER = 1.5.dp

private const val BACKLOG_TITLE_MAX_LINES = 2

/** Two lines: the row is wider than a board card and is where the item gets read. */
private const val BACKLOG_DESCRIPTION_MAX_LINES = 2