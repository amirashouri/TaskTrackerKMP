package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tasktracker.resources.Res
import com.tasktracker.resources.notifications_unread_count
import org.jetbrains.compose.resources.stringResource

/**
 * A small accent count on a navigation target.
 *
 * Sized and drawn inside the caller's icon box so it cannot change that target's measured
 * size — the tab bar and rail derive their lens from the cell geometry, and a badge that
 * added a few dp of height would move the lens rather than sit on top of it.
 *
 * Zero draws nothing rather than an empty pill: a target with no unread rows is not "0
 * unread", it is simply not announcing anything.
 *
 * The count is capped at `99+` so a three-digit inbox cannot widen the pill past the icon
 * it annotates.
 */
@Composable
fun ttCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val label = if (count > 99) "99+" else count.toString()
    val description = stringResource(Res.string.notifications_unread_count, count)

    Box(
        modifier = modifier
            .testTag(CountBadgeTag)
            .clearAndSetSemantics { contentDescription = description }
            .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
            .clip(CircleShape)
            .background(ttAccent())
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = label,
            style = ttTypography.captionSmall,
            color = ttOnAccent,
            maxLines = 1,
        )
    }
}

/** One badge node, shared by the tab bar, rail and sidebar. */
const val CountBadgeTag = "shell.badge"
