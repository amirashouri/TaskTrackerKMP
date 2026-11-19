package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.DISC_TAG
import com.tasktracker.designsystem.OVERFLOW_TAG
import com.tasktracker.designsystem.ttAvatarDescription
import com.tasktracker.designsystem.ttAvatarSize
import com.tasktracker.designsystem.ttAvatarStackDefaults
import com.tasktracker.designsystem.ttAvatarTints
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.board_assignees_description
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Overlapping initials stack representing active assignees on the board header.
 *
 * Expands on desktop hover or mobile tap to reveal the assignee's full name with a spring
 * animation. Automatically shrinks after 5 seconds on mobile or when pointer focus leaves on
 * desktop.
 */
@Composable
internal fun AssigneeStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = ttAvatarStackDefaults.maxVisible,
    size: Dp = ttAvatarSize.avatar,
) {
    if (names.isEmpty()) return

    val shown = names.take(maxVisible)
    val overflow = names.size - shown.size
    val tints = ttAvatarTints(names.size)

    Row(
        modifier = modifier.ttAvatarDescription(
            pluralStringResource(
                Res.plurals.board_assignees_description,
                names.size,
                names.size,
                names.joinToString(),
            ),
        ),
        horizontalArrangement = Arrangement.spacedBy(-(size - ttAvatarStackDefaults.overlapVisible)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, name ->
            ExpandableAssigneeAvatar(
                name = name,
                tint = tints.getOrElse(index) { tints.first() },
                zIndex = (shown.size - index).toFloat(),
                size = size,
            )
        }
        if (overflow > 0) {
            ttInitialsAvatar(
                initials = "+$overflow",
                size = size,
                modifier = Modifier
                    .zIndex(0f)
                    .testTag(OVERFLOW_TAG),
            )
        }
    }
}

@Composable
private fun ExpandableAssigneeAvatar(
    name: String,
    tint: Color,
    zIndex: Float,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isTapped by remember { mutableStateOf(false) }
    val isExpanded = isHovered || isTapped

    // On mobile tap, automatically shrink after 5 seconds
    LaunchedEffect(isTapped) {
        if (isTapped) {
            delay(5000)
            isTapped = false
        }
    }

    val initials = remember(name) { ttInitialsFor(name) }

    Row(
        modifier = modifier
            .zIndex(if (isExpanded) 100f else zIndex)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clip(CircleShape)
            .background(tint.copy(alpha = FILL_ALPHA))
            .border(HAIRLINE_WIDTH, tint.copy(alpha = RIM_ALPHA), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { isTapped = !isTapped },
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
            .testTag(DISC_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            ttText(
                text = initials,
                style = ttTypography.captionEmphasised.copy(textAlign = TextAlign.Center),
                color = ttLabel(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isExpanded) {
            ttText(
                text = name,
                style = ttTypography.captionEmphasised,
                color = ttLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = ttSpacing.xs, end = ttSpacing.md),
            )
        }
    }
}

private val HAIRLINE_WIDTH = 1.dp
private const val FILL_ALPHA = 0.18f
private const val RIM_ALPHA = 0.36f

/** Extracts, resolves, filters, and case-insensitively sorts unique assignee names from [items]. */
internal fun boardAssigneeNames(
    items: List<WorkItem>,
    names: Map<UserID, String> = emptyMap(),
): List<String> = items
    .mapNotNull { it.assigneeID }
    .mapNotNull { names[it] }
    .filter { it.isNotBlank() }
    .map { it.trim() }
    .distinct()
    .sortedBy { it.lowercase() }

const val ProjectBoardAssigneesTag = "board.assignees"
