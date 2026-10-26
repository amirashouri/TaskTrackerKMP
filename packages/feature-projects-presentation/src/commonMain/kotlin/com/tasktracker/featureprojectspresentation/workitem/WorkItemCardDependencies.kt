package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassCard
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
 * Hairline pill displaying dependency link glyph, leading blocker keys, and overflow count for work item cards.
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
        ttIcon(
            image = ttActionIcons.link,
            contentDescription = null,
            tint = ttLabelSecondary(),
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

/** Maximum number of blocker keys displayed on card before showing an overflow count. */
private const val maxCardBlockerKeys = 2

private val CHIP_PADDING_V = 2.dp

const val WorkItemCardDependsTag = "workItem.card.depends"
const val WorkItemCardDependsKeysTag = "workItem.card.depends.keys"
const val WorkItemCardDependsMoreTag = "workItem.card.depends.more"

/**
 * Resolves [item]'s `blockedByIDs` against the *unfiltered* project item list ([items])
 * so cross-sprint or filtered-out blockers still render correctly.
 */
internal fun dependencyKeysFor(
    items: List<WorkItem>,
    item: WorkItem,
): List<WorkItemKey> {
    if (item.blockedByIDs.isEmpty()) return emptyList()
    val byID = items.associateBy { it.id }
    return item.blockedByIDs.mapNotNull { byID[it]?.key }
}