package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.tasktracker.designsystem.ttSeparatorWidth
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
 * Key, priority, title, assignee and a due date. Not the details - a card is a summary, and
 * the details are one tap away. Not the status either, because the card is *in* a column
 * labelled with the status; printing it again is the kind of duplication that goes stale
 * the moment a drag fails halfway.
 *
 * ## The order, and why it is that order
 *
 * Top to bottom: the key with the priority badge beside it, the title, the assignee, then the
 * due date and the card's own action.
 *
 * The badge shares the key's row because those two are what a lane is scanned for - "is this
 * the one, and how urgent is it" - and a badge two rows down from the key it belongs to makes
 * the user read the card twice. The key takes the row's remaining width rather than sitting
 * beside the badge, so a long key ellipsises instead of pushing the badge off the card.
 *
 * The due date sits with the action rather than with the assignee because of what the two are:
 * a date is a *fact about the task*, and so is an assignee, but the action is a *thing you can
 * do*, and a card read top to bottom should end on the things you can do.
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
    /**
     * The assignee's display name, resolved by the host from [WorkItem.assigneeID] via
     * the directory. `null` — unassigned, or an id the directory cannot name — draws no
     * pill: the card must not invent a label for an identity it cannot produce, and an
     * id on a card is noise rather than a name.
     */
    assigneeName: String? = null,
) {
    Column(
        modifier = modifier
            .testTag(WorkItemCardTag(item))
            .clip(RoundedCornerShape(ttRadius.surface))
            .ttGlassCard()
            .clickable(onClick = onClick)
            .padding(ttSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = item.key.toString(),
                style = ttTypography.key,
                color = ttLabelSecondary(),
                // Takes what the badge leaves, which is what pins the badge to the trailing
                // edge and ellipsises a long key instead of pushing the badge off the card.
                modifier = Modifier.weight(1f),
            )
            PriorityBadge(priority = item.priority)
        }
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
        assigneeName?.takeIf { it.isNotBlank() }?.let { assignee ->
            AssigneeLabel(firstName = assigneeFirstName(assignee))
        }
        // The due date and the card's own action, on one row: the date at the leading edge and
        // whatever the surface put in the action slot at the trailing one, so the action stays
        // in the bottom corner whether or not the task has a date.
        //
        // The action is a *task*, not a property of the task, which is why it lives with the
        // board rather than in this file: a card knows how to be a card and nothing about what
        // a board lets you do to it. The board is the surface that has to agree with the
        // backlog about what filing away means.
        //
        // Composed only when it has something in it. An always-present row would leave an
        // empty row behind on a card with neither a date nor an action, and the column's
        // `spacedBy` would still charge it a gap - 8dp of nothing at the bottom of the card.
        if (item.dueDate != null || trailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.dueDate?.let { due ->
                    DueDateChip(
                        dueDate = due,
                        isOverdue = isOverdue(item, due),
                    )
                }
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
        }
    }
}

/**
 * Who the task is with, as a first name in a pill.
 *
 * ## Why a pill, and why not a filled one
 *
 * A pill because it is a *fact about the task*, and the badge next to it is a fact too - two
 * facts of the same kind should look the same, or the card has three visual weights instead of
 * two. It is **not** filled, which is the part doing the work: [PriorityBadge] tints the two
 * levels that need acting on and leaves the other three as hairline outlines precisely so that
 * colour on this card means urgency. A washed assignee pill would spend that channel on a name.
 *
 * So this is built exactly like [DueDateChip]'s resting state - transparent fill, hairline rim,
 * [ttRadius.row], the chip's own padding - rather than with a second set of numbers. That is a
 * duplication of *construction* across three call sites ([PriorityBadge], [DueDateChip], this),
 * and the right fix is one pill primitive in `:packages:design-system`; until that exists, all
 * three are deliberately the same object rather than three that drift.
 */
@Composable
private fun AssigneeLabel(
    firstName: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Transparent)
            .border(ttSeparatorWidth, ttSeparator(), shape)
            .padding(horizontal = ttSpacing.sm, vertical = CHIP_PADDING_V)
            .testTag(WorkItemCardAssigneeTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = firstName,
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
        )
    }
}

/**
 * The name a card shows for its assignee: the first word.
 *
 * `substringBefore` rather than a `split`, because the answer is the first token and the whole
 * string when there is no delimiter - the same rule the auth session applies to an email
 * address. A handle, a team or an address has no space in it and so comes through whole.
 * Trimmed first so an untrimmed `" Amir"` cannot produce an empty pill.
 *
 * ## What it costs
 *
 * Anything with a space in it loses everything after the first one, so "Ana van der Berg" is
 * "Ana" and "Amir + Priya" is "Amir". Both are wrong as people. Both are the price of a card
 * showing one word of a free-text field, and the editor - one tap away - is where the whole
 * name is read. Recorded here rather than discovered later, because a rule with a cost that
 * is only written down as a benefit is how the cost becomes a surprise.
 *
 * Extracted rather than inlined because it is a rule, and this file's convention is that a rule
 * gets a name and a test: the semantics tree can see *that* a name is on the card, but not that
 * it is the shortened one, so `WorkItemCardRulesTest` holds it to the shortening.
 */
internal fun assigneeFirstName(assignee: String): String = assignee.trim().substringBefore(' ')

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
            .border(ttSeparatorWidth, if (isOverdue) ttLabelSecondary() else ttSeparator(), shape)
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
            WorkItemCard(
                item = item,
                onClick = {},
                assigneeName = item.assigneeID?.let { ViewPreviewData.previewUserNames[it] },
            )
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
            WorkItemCard(
                item = item,
                onClick = {},
                assigneeName = item.assigneeID?.let { ViewPreviewData.previewUserNames[it] },
            )
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
