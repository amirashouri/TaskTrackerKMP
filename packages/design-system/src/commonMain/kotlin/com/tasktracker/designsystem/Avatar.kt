package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Single initials avatar disc with translucent fill wash and hairline border. */
@Composable
fun ttInitialsAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = ttAvatarSize.avatar,
    tint: Color? = null,
    initialsInset: Dp = 0.dp,
) {
    val accent = tint ?: ttAccent()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = ttAvatar.FILL_ALPHA))
            .border(ttGlass.hairlineWidth, accent.copy(alpha = ttAvatar.RIM_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = initials,
            style = ttTypography.captionEmphasised.copy(textAlign = TextAlign.Center),
            color = ttLabel(),
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = initialsInset),
        )
    }
}

/** Horizontal overlapping stack of avatar discs with an overflow counter chip. */
@Composable
fun ttInitialsStack(
    initials: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = ttAvatarStackDefaults.maxVisible,
    size: Dp = ttAvatarSize.avatar,
) {
    if (initials.isEmpty()) return

    val shown = initials.take(maxVisible)
    val overflow = initials.size - shown.size
    val inset = -(size - ttAvatarStackDefaults.overlapVisible) / 2

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(-(size - ttAvatarStackDefaults.overlapVisible)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, value ->
            ttInitialsAvatar(
                initials = value,
                size = size,
                modifier = Modifier
                    .zIndex((shown.size - index).toFloat())
                    .testTag(DISC_TAG),
                initialsInset = inset,
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

object ttAvatarStackDefaults {
    val maxVisible: Int = 4
    val overlapVisible: Dp = 16.dp
}

object ttAvatarSize {
    val avatar: Dp = 24.dp
}

const val DISC_TAG = "tt.avatar.disc"
const val OVERFLOW_TAG = "tt.avatar.overflow"

/** Extracts first and last initials from space-separated name tokens (e.g. "Mary Jane Watson" -> "MW"). */
fun ttInitialsFor(name: String): String {
    val tokens = name.split(NAME_WHITESPACE).filter { it.isNotEmpty() }
    return when {
        tokens.isEmpty() -> ""
        tokens.size == 1 -> tokens.first().take(1)
        else -> tokens.first().take(1) + tokens.last().take(1)
    }.uppercase()
}

private val NAME_WHITESPACE = Regex("\\s+")

private object ttAvatar {
    const val FILL_ALPHA = 0.18f
    const val RIM_ALPHA = 0.36f
}

fun Modifier.ttAvatarDescription(description: String): Modifier = semantics {
    contentDescription = description
}