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

/**
 * Horizontal overlapping stack of avatar discs with an overflow counter chip.
 *
 * [tints] colours the discs positionally: disc *i* is drawn in `tints[i]`, and a disc past the
 * end of the list falls back to the accent. The overlapping stack is the one place where two
 * neighbouring discs are read as a pair — they are 16dp apart and 8dp of one is under the
 * other — so a caller with a set of *people* passes one tint per person (see [ttAvatarTints]) and
 * the discs stop reading as a single blue blur. `null` keeps the old single-accent look for the
 * callers that are showing one thing rather than a set.
 *
 * The overflow chip keeps the accent deliberately: it is not a person, and giving it a hue out
 * of the same palette would make "+2 more" look like a member.
 */
@Composable
fun ttInitialsStack(
    initials: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = ttAvatarStackDefaults.maxVisible,
    size: Dp = ttAvatarSize.avatar,
    tints: List<Color>? = null,
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
                tint = tints?.getOrNull(index),
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

/**
 * One tint per disc for a stack of [count] people, resolved for the appearance being drawn.
 *
 * ## Positional, and that is a decision
 *
 * The alternative — a colour derived from the member's id — is stable as the list changes, and
 * it cannot promise what this is for: two people byte-identical in nothing but their hash
 * collide, and a collision lands *beside itself* in a sorted stack, which is the one place the
 * eye compares two discs. Cycling a palette by position makes "the disc next to this one is a
 * different colour" true by construction, for any count, at any position.
 *
 * ## The hues, and why they are [TagTint]'s
 *
 * They are the same closed set the tags use, resolved through [ttTagTint] so each hue has its
 * light-appearance and dark-appearance value and the avatar cannot pick a colour that only
 * works on one canvas. Gray is left out: it is the one hue in that set that reads as "no
 * colour", and a stack of them would look like the single-accent version with extra steps.
 *
 * The order alternates colour families rather than walking the enum, so the first four discs
 * of a small board are blue, orange, green and purple — not four neighbouring hues that a
 * 24dp disc washes out into the same blue-grey.
 */
@Composable
fun ttAvatarTints(count: Int): List<Color> = List(count) { index ->
    ttTagTint(AVATAR_TINTS[index % AVATAR_TINTS.size])
}

/** The cycling order [ttAvatarTints] draws from. See its note on the order. */
private val AVATAR_TINTS: List<TagTint> = listOf(
    TagTint.Blue,
    TagTint.Orange,
    TagTint.Green,
    TagTint.Purple,
    TagTint.Teal,
    TagTint.Pink,
    TagTint.Indigo,
    TagTint.Amber,
    TagTint.Red,
)

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