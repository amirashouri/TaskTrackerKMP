package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A task as a card on the board.
 *
 * ## What is on it, and why nothing else is
 *
 * Key, title, priority, assignee and a due date. Not the details - a card is a summary, and
 * the details are one tap away. Not the status either, because the card is *in* a column
 * labelled with the status; printing it again is the kind of duplication that goes stale
 * the moment a drag fails halfway.
 *
 * ## Why the strike-through is on the title and not the whole card
 *
 * A `Done` card is finished with. Strikethrough on the title says "this is done" while
 * leaving the key, the badge and the assignee readable, which a whole-card strike-through
 * cannot: greying out everything also greys out the one fact the user might still want,
 * the key, when they go looking for it in a list of finished work.
 */
@Composable
fun WorkItemCard(
    item: WorkItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .testTag(WorkItemCardTag(item))
            .clip(RoundedCornerShape(ttRadius.surface))
            .ttGlassCard()
            .clickable(onClick = onClick)
            .padding(ttSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = item.key.toString(),
            style = ttTypography.key,
            color = ttLabelSecondary(),
        )
        ttText(
            text = item.title,
            style = if (isDelivered(item)) {
                ttTypography.callout.copy(textDecoration = TextDecoration.LineThrough)
            } else {
                ttTypography.callout
            },
            color = ttLabel(),
            maxLines = CARD_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PriorityBadge(priority = item.priority)
            item.dueDate?.let { due ->
                DueDateChip(
                    dueDate = due,
                    isOverdue = isOverdue(item, due),
                )
            }
            // The card's own action slot. A card knows how to be a card and nothing about
            // what a board lets you do to it, so the file-away affordance lives with the
            // board - which is the surface that has to agree with the backlog about what
            // filing away means.
            trailing?.invoke()
        }
        item.assignee?.takeIf { it.isNotBlank() }?.let { assignee ->
            ttText(
                text = assignee,
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                maxLines = 1,
                modifier = Modifier.testTag(WorkItemCardAssigneeTag),
            )
        }
    }
}

/**
 * Whether a task was *delivered*, which is what earns the strike-through.
 *
 * [WorkItemStatus.isComplete] and not [WorkItemStatus.isTerminal]: a `Closed` task was
 * abandoned rather than finished, and striking its title through claims a delivery that
 * never happened. This is the whole rule, extracted, because it is the one part of the card
 * the semantics tree cannot see - `TextDecoration` is not published as a semantics property
 * in Compose Multiplatform 1.12, so a UI test cannot assert a strike-through. It can be
 * unit-tested here instead, and a rule that cannot be tested at the level that draws it
 * should at least be testable at the level that decides it.
 */
internal fun isDelivered(item: WorkItem): Boolean = item.status.isComplete

/**
 * A due date, flagged when it has passed and the task is not finished with.
 *
 * Overdue is [WorkItemStatus.isComplete] rather than [WorkItemStatus.isTerminal]: a
 * `Closed` task is finished with, so a closed task with a due date last Tuesday is not
 * something to feel bad about, and marking it red would train the user to ignore the flag.
 */
@Composable
private fun DueDateChip(
    dueDate: Instant,
    isOverdue: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isOverdue) ttLabel() else ttLabelSecondary()
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (isOverdue) ttLabelSecondary().copy(alpha = OVERDUE_FILL_ALPHA) else Color.Transparent)
            .border(1.dp, if (isOverdue) ttLabelSecondary() else ttSeparator(), shape)
            .padding(horizontal = ttSpacing.sm, vertical = CHIP_PADDING_V)
            .testTag(WorkItemCardDueTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = dueDateLabel(dueDate),
            style = ttTypography.captionSmall,
            color = tint,
        )
    }
}

/**
 * The badge on an unfinished task whose due date has passed.
 *
 * The caller passes the answer in rather than this reading a clock: the view has no clock,
 * and a preview with a due date of "today" would silently change which cards look overdue
 * depending on when it was opened.
 */
internal fun isOverdue(item: WorkItem, now: Instant): Boolean {
    val due = item.dueDate ?: return false
    return !item.status.isComplete && due < now
}

/**
 * A due date as `d MMM`.
 *
 * Month abbreviations come from [kotlinx.datetime.Month.name] rather than a locale format,
 * so a card's width does not change with the device's language, and a UI test can assert on
 * `3 Mar` instead of matching a pattern that a locale could break. The ISO date would be
 * unambiguous but is a worse thing to read at a glance.
 */
@Composable
internal fun dueDateLabel(dueDate: Instant): String {
    val local = dueDate.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.day} ${local.month.name.take(MONTH_ABBREVIATION_LENGTH)}"
}

@Preview
@Composable
private fun WorkItemCardPreview() = ProjectsPreview(ThemeScheme.Dark) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        modifier = Modifier.width(CARD_PREVIEW_WIDTH),
    ) {
        ViewPreviewData.workItems.take(4).forEach { item ->
            WorkItemCard(item = item, onClick = {})
        }
    }
}

@Preview
@Composable
private fun WorkItemCardLightPreview() = ProjectsPreview(ThemeScheme.Light) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        modifier = Modifier.width(CARD_PREVIEW_WIDTH),
    ) {
        ViewPreviewData.workItems.take(4).forEach { item ->
            WorkItemCard(item = item, onClick = {})
        }
    }
}

/** Surfaces, not ids. */
const val WorkItemCardTagPrefix = "workItem.card"
const val WorkItemCardDueTag = "workItem.card.dueDate"
const val WorkItemCardAssigneeTag = "workItem.card.assignee"

/**
 * The card's tag.
 *
 * Suffixes the *key* rather than the id: a key is stable, human-meaningful, and already
 * what the user sees on the card, so a failing assertion can name the card it meant. An id
 * would work and read as noise.
 */
fun WorkItemCardTag(item: WorkItem): String = "$WorkItemCardTagPrefix.${item.key}"

private const val CARD_TITLE_MAX_LINES = 3
private const val MONTH_ABBREVIATION_LENGTH = 3
private const val OVERDUE_FILL_ALPHA = 0.16f
private val CHIP_PADDING_V = 2.dp
private val CARD_PREVIEW_WIDTH = 280.dp
