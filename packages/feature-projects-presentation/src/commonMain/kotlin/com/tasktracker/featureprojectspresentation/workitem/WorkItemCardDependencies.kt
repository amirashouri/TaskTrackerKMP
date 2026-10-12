package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_depends_more
import org.jetbrains.compose.resources.stringResource

/**
 * The blockers a card is waiting on, as one hairline pill: a chain glyph, the keys it has
 * room to name, and a count for the ones it does not.
 *
 * ## Why this is not the editor's [WorkItemDependencyPills]
 *
 * The editor owns the *editing* of a dependency - opening it, removing it, adding another -
 * and does it with a full-width wrapped row of pills. A card has none of those affordances:
 * it is a summary, it is tapped to open the editor, and there is no room for a button on it.
 * So this is a different shape of the same fact rather than a smaller version of the same
 * component, and the two do not share a file or a tag.
 *
 * ## Why the same construction as the assignee pill and the due-date chip
 *
 * All three are transparent fill, hairline rim, [ttRadius.row] and the same vertical padding -
 * and that is a duplication of *construction* that [WorkItemCard]'s `AssigneeLabel` KDoc
 * already names as the right fix (one pill primitive in the design system). Until that
 * exists, all three are deliberately the same object rather than three that drift, and this
 * is the third. It is also what keeps colour meaningful on the card: none of the three is
 * filled, so the only tinted thing on a card is still [com.tasktracker.designsystem.ttError]
 * on a *blocked* pill or an overdue date.
 *
 * ## Why two keys, then a count
 *
 * A card is scanned, not read. Naming every blocker would make a five-blocker card taller
 * than the editor row it is a summary of, on the one surface that has to stay a grid. So
 * [maxCardBlockerKeys] keys are named and the rest are counted, and the count is honest about
 * what it is: "+2" says there are two more, it does not pretend the row is the whole list.
 * The editor is where the complete set is readable.
 *
 * ## Nothing is drawn for an item with no blockers
 *
 * The early return below is the whole rule, and it lives here rather than at the call site so
 * that it cannot be forgotten by the next card that wants the row. A composable that emits
 * nothing is not a child, so [WorkItemCard]'s `spacedBy` is not charged a gap for it either —
 * which is why the card calls this unconditionally rather than behind an `if` of its own. That
 * was measured, not assumed: guarding the call site as well and deleting the guard leaves every
 * scenario in `WorkItemCardDependenciesScenarios` green, so the call-site guard was removed
 * rather than left in as decoration.
 */
@Composable
fun WorkItemCardDependencies(
    blockerKeys: List<WorkItemKey>,
    modifier: Modifier = Modifier,
) {
    if (blockerKeys.isEmpty()) return

    val named = blockerKeys.take(maxCardBlockerKeys)
    val overflow = blockerKeys.size - named.size
    val shape = RoundedCornerShape(ttRadius.row)

    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Transparent)
            .border(ttSeparatorWidth, ttSeparator(), shape)
            .padding(horizontal = ttSpacing.sm, vertical = CHIP_PADDING_V)
            .testTag(WorkItemCardDependsTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `null`: the keys beside it already say what the row is, and a description on both
        // would announce the row twice. See `ttIcon`'s note on that parameter.
        ttIcon(
            image = ttActionIcons.link,
            contentDescription = null,
            tint = ttLabelSecondary(),
            // [ttIconSize.close] rather than [ttIconSize.tab]: this glyph is a detail inside a
            // pill whose name is set in caption text, which is precisely what that size's KDoc
            // describes, and at 22dp the chain would be larger than the keys it labels.
            modifier = Modifier.size(ttIconSize.close),
        )
        ttText(
            text = named.joinToString(separator = ", ") { it.toString() },
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag(WorkItemCardDependsKeysTag),
        )
        if (overflow > 0) {
            ttText(
                text = stringResource(Res.string.work_item_depends_more, overflow),
                style = ttTypography.captionSmall,
                color = ttLabel(),
                modifier = Modifier.testTag(WorkItemCardDependsMoreTag),
            )
        }
    }
}

@Preview(name = "Card dependencies, dark", showBackground = true, widthDp = 280)
@Composable
private fun WorkItemCardDependenciesPreview() = ProjectsPreview(ThemeScheme.Dark) {
    WorkItemCardDependencies(blockerKeys = listOf(WorkItemKey("TT-1"), WorkItemKey("TT-7")))
}

@Preview(name = "Card dependencies, light, overflowing", showBackground = true, widthDp = 280)
@Composable
private fun WorkItemCardDependenciesOverflowPreview() = ProjectsPreview(ThemeScheme.Light) {
    WorkItemCardDependencies(
        blockerKeys = listOf(WorkItemKey("TT-1"), WorkItemKey("TT-7"), WorkItemKey("TT-9"), WorkItemKey("TT-12")),
    )
}

/**
 * How many blockers a card names before it counts the rest.
 *
 * Two, because a card's lane is 300dp wide and its own padding takes 32 of that. A key is
 * `TT-142` at six characters in caption text, so two keys and a separator fit in the
 * remaining width with room for the glyph and the pill's own padding, and a third would be
 * ellipsised by the `weight(1f)` above — which would mean the row shows a truncated key *and*
 * a count, the worst of both readings.
 */
private const val maxCardBlockerKeys = 2

/**
 * Shared with [WorkItemCard]'s own chips so the three pills on a card sit on one baseline.
 *
 * 2dp rather than 0: a hairline rim drawn at zero vertical padding is 1dp of box around a
 * caption, which reads as a clipped row rather than a pill.
 */
private val CHIP_PADDING_V = 2.dp

const val WorkItemCardDependsTag = "workItem.card.depends"
const val WorkItemCardDependsKeysTag = "workItem.card.depends.keys"
const val WorkItemCardDependsMoreTag = "workItem.card.depends.more"

/**
 * The keys of the tasks [item] waits on, resolved against [items].
 *
 * ## Why a join here and not a stored key on the card
 *
 * `blockedByIDs` holds ids, and a card shows keys. Resolving one into the other is a read-side
 * join over data this device already has — the project's whole work list — so storing the key
 * as well would be a second copy of a fact that can go stale the moment a key is ever changed,
 * and 1.5's own line says no new persistence.
 *
 * ## Why the caller must pass the *unfiltered* list
 *
 * This is the whole reason the function takes `items` rather than reading the board's own
 * visible cards: a blocker is frequently **not** on the board you are looking at. It may be
 * in another sprint, or assigned to somebody else while the My Tasks toggle is on, or sitting
 * in a terminal column. Joining against `projection.items` would therefore resolve fewer and
 * fewer blockers as filters narrow, and a card would quietly claim a task waits on two things
 * when it waits on four — a wrong answer that looks like a correct one. Passing the project's
 * whole list makes the join independent of what is visible, which is the only way the row on
 * the card and the row in the editor can agree.
 *
 * ## Ids with no matching item are dropped, not rendered raw
 *
 * A blocker deleted on another device leaves an id nothing resolves. The card does not print
 * the id: [WorkItemCard]'s assignee row already establishes that this surface does not invent
 * a label for something it cannot name, and a raw `0195…` in a list of `TT-142` is noise rather
 * than information. Dropping it costs the card one entry and keeps the count honest.
 *
 * Order is [WorkItem.blockedByIDs]'s own, not the board's. A blocker list is short and its
 * order is the user's — they added them in an order — whereas re-sorting by the board's key
 * would make the row reshuffle whenever an unrelated card moved.
 */
internal fun dependencyKeysFor(
    items: List<WorkItem>,
    item: WorkItem,
): List<WorkItemKey> {
    if (item.blockedByIDs.isEmpty()) return emptyList()
    val byID = items.associateBy { it.id }
    return item.blockedByIDs.mapNotNull { byID[it]?.key }
}