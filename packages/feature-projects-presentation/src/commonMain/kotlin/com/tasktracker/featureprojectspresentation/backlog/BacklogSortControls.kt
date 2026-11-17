package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.BacklogSortBasis
import com.tasktracker.domain.BacklogSortKey
import com.tasktracker.domain.BacklogSortOrder
import com.tasktracker.resources.Res
import com.tasktracker.resources.backlog_sort_label_created_asc
import com.tasktracker.resources.backlog_sort_label_created_desc
import com.tasktracker.resources.backlog_sort_label_one
import com.tasktracker.resources.backlog_sort_label_pair
import com.tasktracker.resources.backlog_sort_label_priority_asc
import com.tasktracker.resources.backlog_sort_label_priority_desc
import org.jetbrains.compose.resources.stringResource

/**
 * The backlog's order, stated in one line, and the way into [BacklogSortSheet].
 *
 * The row is a summary and a button, nothing else: it names every key in the order it applies
 * and opens the sheet where those keys are edited. The selection itself used to live here as two
 * 44dp chips, which had to encode which field, on or off, which way, and which key leads all in
 * fill and glyph — four facts per chip, and a tap that resolved on/off, direction and leadership
 * at once. None of that survives the move to a control that speaks.
 *
 * The label stays at the leading edge, so the row's own inset is still where this control's text
 * starts; the glyph at the trailing edge is the affordance that says it opens something. The
 * label is not tagged: the whole row is one button, and the order it names is the button's own
 * text rather than a second node beside it.
 */
@Composable
fun BacklogSortControls(
    order: BacklogSortOrder,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BacklogSortControlsTag)
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = BacklogFilterRowHeight)
            .padding(horizontal = ttSpacing.md, vertical = ttSpacing.xs),
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
                .padding(end = ttSpacing.sm),
        )
        ttIcon(
            image = ttActionIcons.sort,
            contentDescription = null,
            tint = ttAccent(),
            modifier = Modifier.size(ttIconSize.tab),
        )
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

/**
 * The height every control in the filter row is given.
 *
 * The summary row's own height: the space a [ttSpacing.iconButtonSize] control needs plus its
 * vertical padding. The search field's default minimum is shorter than that, and beside the sort
 * row the two controls read as misaligned — a 48dp field against 52dp of glass — even though
 * their tops agree. One height for both means their centres do too.
 */
internal val BacklogFilterRowHeight: Dp = ttSpacing.iconButtonSize + ttSpacing.xs * 2

const val BacklogSortControlsTag = "backlog.sort.controls"
