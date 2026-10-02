package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.BoardColumn
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_empty_column
import com.tasktracker.resources.board_add_task
import org.jetbrains.compose.resources.stringResource

/**
 * One lane of the board: a heading, a count, and the cards in it.
 *
 * ## Why an empty lane still draws
 *
 * Every lane in [WorkItemStatus.boardOrder] is rendered even with nothing in it, and the
 * column shows [board_empty_column] rather than collapsing. Drag-and-drop needs a target
 * to drop onto, and a lane that only appears once it has a card in it means the first card
 * of a new column has nowhere to go. It also means the board does not reflow as work
 * progresses, which is the thing that makes a board readable: a card's neighbours stay
 * where they were.
 *
 * The alternative - hiding empty lanes - is what the original did, and the result was a
 * board that grew and shrank under the user's hand.
 *
 * ## Why the count is in the heading and not on a badge
 *
 * The count is the column's own summary and nothing else uses it. A badge would compete
 * with a card's priority badge for the same visual weight at a smaller size, and the two
 * are not comparable - one counts, one grades.
 */
@Composable
fun BoardColumnView(
    column: BoardColumn,
    onSelect: (WorkItem) -> Unit,
    modifier: Modifier = Modifier,
    onAddTask: (() -> Unit)? = null,
    /**
     * The action slot for each card in this column.
     *
     * Taken as a slot rather than a callback so the column stays ignorant of what the
     * actions *are*: the column knows which cards it holds, and [ProjectBoardScreen] knows
     * what a card can be done to. Passing `onFileAway` and `onMove` down here instead would
     * have made the column a second place where board verbs are decided, which is how the
     * two filing surfaces came to disagree in the first place.
     */
    cardTrailing: @Composable (WorkItem) -> Unit = {},
) {
    Box(
        modifier = modifier
            .width(columnWidth)
            .fillMaxHeight()
            .ttGlassCard()
            .testTag(BoardColumnTag(column.status)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(ttSpacing.xl)
                .padding(bottom = if (onAddTask == null) ttSpacing.xl else ttSpacing.boardBottomInset),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ttText(
                    text = statusLabel(column.status),
                    style = ttTypography.titleSmall,
                    color = ttLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ttText(
                    text = column.count.toString(),
                    style = ttTypography.caption,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(BoardColumnCountTag(column.status)),
                )
            }
            if (column.isEmpty) {
                ttText(
                    text = stringResource(Res.string.board_empty_column),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(BoardColumnEmptyTag(column.status)),
                )
            } else {
                column.items.forEach { item ->
                    WorkItemCard(
                        item = item,
                        onClick = { onSelect(item) },
                        modifier = Modifier.fillMaxWidth(),
                        trailing = { cardTrailing(item) },
                    )
                }
            }
        }
        onAddTask?.let { addTask ->
            ttGlassButton(
                label = stringResource(Res.string.board_add_task),
                onClick = addTask,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md)
                    .testTag(BoardColumnAddTaskTag(column.status)),
            )
        }
    }
}

@Preview
@Composable
private fun BoardColumnPreview() = ProjectsPreview(ThemeScheme.Dark) {
    Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
        WorkItemStatus.boardOrder.forEach { status ->
            BoardColumnView(
                column = BoardColumn(
                    status = status,
                    items = ViewPreviewData.boardProjection().items.filter { it.status == status },
                ),
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun BoardColumnLightPreview() = ProjectsPreview(ThemeScheme.Light) {
    Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
        BoardColumnView(
            column = BoardColumn(status = WorkItemStatus.Todo, items = emptyList()),
            onSelect = {},
        )
    }
}

const val BoardColumnTagPrefix = "board.column"

/** Suffixed with the status's wire value, so a test can find a lane by its column. */
fun BoardColumnTag(status: WorkItemStatus): String = "$BoardColumnTagPrefix.${status.wireValue}"

fun BoardColumnCountTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.count"

fun BoardColumnEmptyTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.empty"

fun BoardColumnAddTaskTag(status: WorkItemStatus): String = "${BoardColumnTag(status)}.add"

/**
 * The lane's width.
 *
 * Fixed rather than weighted, because a card's content decides how much room it needs and a
 * proportion would make "In Progress" narrower than "To Do" on a phone. [ttRadius.surface] is
 * the same radius a card uses, which is what makes a column and a card read as the same
 * kind of floating surface.
 */
internal val columnWidth = 300.dp

/**
 * The distance from one lane's leading edge to the next one's.
 *
 * Internal rather than a public constant because it exists for one caller - the board row's
 * snap behaviour, which has to know where the lanes *land* - and it is deliberately defined
 * here, next to [columnWidth], rather than in the row that lays the lanes out. Two places
 * would each be free to say "300dp wide, 8dp apart" and disagree, and the disagreement would
 * not look like a disagreement: the row would look right and the snap would land a third of a
 * lane off.
 *
 * The row's `Arrangement.spacedBy` reads the same gap, so the stride follows the layout rather
 * than predicting it.
 */
internal val boardLaneStride: Dp = columnWidth + ttSpacing.md
