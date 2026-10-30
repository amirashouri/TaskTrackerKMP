package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.BacklogSortBasis
import com.tasktracker.domain.BacklogSortKey
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_sort_by_created
import com.tasktracker.resources.backlog_sort_by_priority
import com.tasktracker.resources.backlog_sort_highest_first
import com.tasktracker.resources.backlog_sort_label_created_asc
import com.tasktracker.resources.backlog_sort_label_created_desc
import com.tasktracker.resources.backlog_sort_label_one
import com.tasktracker.resources.backlog_sort_label_pair
import com.tasktracker.resources.backlog_sort_label_priority_asc
import com.tasktracker.resources.backlog_sort_label_priority_desc
import com.tasktracker.resources.backlog_sort_lowest_first
import com.tasktracker.resources.backlog_sort_newest_first
import com.tasktracker.resources.backlog_sort_off
import com.tasktracker.resources.backlog_sort_oldest_first
import org.jetbrains.compose.resources.stringResource

/**
 * The backlog's two sort keys: priority and created date, plus the label naming the order.
 *
 * Both chips can be on at once. The label is the only place the ordering is stated — the chips
 * are glyphs — so it names every active key in the order it is applied, and a chip that is only
 * breaking ties is named second. A chip that is off says so, and one that leads is the only one
 * wearing the accent.
 *
 * The tap semantics live in [BacklogSortOrder.toggled]; this file only reports the state.
 */
@Composable
fun BacklogSortControls(
    order: BacklogSortOrder,
    onToggle: (BacklogSortBasis) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = sortSummary(order),
            style = ttTypography.caption,
            color = ttLabelSecondary(),
            // Two lines is what the longest label ("lowest priority and oldest added") needs
            // on a narrow phone. The header is measured for the list's top inset, so a label
            // that wraps pushes the rows down instead of overlapping them.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = ttSpacing.sm)
                .testTag(BacklogSortLabelTag),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
            SortButton(
                image = ttActionIcons.priority,
                order = order,
                basis = BacklogSortBasis.Priority,
                contentDescription = stringResource(Res.string.backlog_sort_by_priority),
                onClick = { onToggle(BacklogSortBasis.Priority) },
                modifier = Modifier.testTag(BacklogSortPriorityTag),
            )
            SortButton(
                image = ttActionIcons.createdAt,
                order = order,
                basis = BacklogSortBasis.CreatedAt,
                contentDescription = stringResource(Res.string.backlog_sort_by_created),
                onClick = { onToggle(BacklogSortBasis.CreatedAt) },
                modifier = Modifier.testTag(BacklogSortCreatedTag),
            )
        }
    }
}

/**
 * "Sort by priority", or "Sort by priority and newly added" when a second key is active.
 *
 * Built from the active keys rather than from the two chips' own state, so a key that is off
 * cannot leak into the sentence and a key that leads is always named first.
 */
@Composable
private fun sortSummary(order: BacklogSortOrder): String {
    val leading = sortKeyName(order.leadingKey)
    val tieBreaker = order.tieBreakerKey?.let { sortKeyName(it) }
    return if (tieBreaker == null) {
        stringResource(Res.string.backlog_sort_label_one, leading)
    } else {
        stringResource(Res.string.backlog_sort_label_pair, leading, tieBreaker)
    }
}

@Composable
private fun sortKeyName(key: BacklogSortKey): String = stringResource(
    when {
        key.basis == BacklogSortBasis.Priority && key.descending ->
            Res.string.backlog_sort_label_priority_desc
        key.basis == BacklogSortBasis.Priority -> Res.string.backlog_sort_label_priority_asc
        key.descending -> Res.string.backlog_sort_label_created_desc
        else -> Res.string.backlog_sort_label_created_asc
    },
)

@Composable
private fun SortButton(
    image: ImageVector,
    order: BacklogSortOrder,
    basis: BacklogSortBasis,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = order.keyFor(basis)
    val direction = when {
        key == null -> stringResource(Res.string.backlog_sort_off)
        key.descending -> stringResource(
            if (basis == BacklogSortBasis.Priority) {
                Res.string.backlog_sort_highest_first
            } else {
                Res.string.backlog_sort_newest_first
            },
        )
        else -> stringResource(
            if (basis == BacklogSortBasis.Priority) {
                Res.string.backlog_sort_lowest_first
            } else {
                Res.string.backlog_sort_oldest_first
            },
        )
    }
    ttGlassIconButton(
        image = image,
        contentDescription = contentDescription,
        onClick = onClick,
        // Three weights, because the row has three states to tell apart: the key that decides
        // the order, the key that only breaks ties, and a key that is not part of it. Tint is
        // the only lever the glass button exposes, so the middle state takes the full label
        // colour — present, but not the one leading.
        tint = when {
            order.isLeading(basis) -> ttAccent()
            key != null -> ttLabel()
            else -> ttLabelSecondary()
        },
        modifier = modifier.semantics {
            selected = key != null
            stateDescription = direction
        },
    )
}

const val BacklogSortLabelTag = "backlog.sort.label"
const val BacklogSortPriorityTag = "backlog.sort.priority"
const val BacklogSortCreatedTag = "backlog.sort.created"
