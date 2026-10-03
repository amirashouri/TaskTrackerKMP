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

/**
 * Initials in a disc: the one way this app draws a person or a project as a shape.
 *
 * ## Why this exists rather than a second one
 *
 * Two call sites needed the same circle before this file did - the project switcher's key prefix
 * and the board's stack of assignees - and the first of them had its own private copy with its own
 * two alpha constants. A disc drawn twice is a disc that has already drifted once: the alphas
 * live wherever the first caller put them, so the second caller can only copy the numbers or
 * import something that does not exist. Three call sites share this one now.
 *
 * ## Why the fill is a wash and the rim is not
 *
 * A wash, not a solid: these discs sit on glass, on the page, and inside one another, and a solid
 * fill at this size is the loudest thing on a card that is meant to be read. The rim is a
 * full-strength hairline for the opposite reason - it is what keeps a disc legible where it
 * overlaps something. Same split as [ttGlass]'s surfaces: the fill is atmosphere, the border is
 * the fact.
 */
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
            // Full width, so [TextAlign.Center] has something to centre *in*. Left to its own
            // measured width the glyphs sit wherever the string happens to end, and a two-letter
            // run in a 24dp disc has 11dp of slack to be off by.
            //
            // No `lineHeight` override: the vertical centring is the Box's, and `lineHeight` in
            // `sp` would grow with the user's text-size setting, which this design system refuses
            // everywhere a fixed tile is involved (see `AppMark`).
            style = ttTypography.captionEmphasised.copy(textAlign = TextAlign.Center),
            color = ttLabel(),
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = initialsInset),
        )
    }
}

/**
 * Up to [maxVisible] discs, each overlapping the one before it, then a `+N` chip.
 *
 * ## Why the overlap is negative spacing and not `offset`
 *
 * `Arrangement.spacedBy` takes a negative gap and lays the children out with it, so the row's own
 * measurement shrinks by the overlap and the stack's width is the number the caller lays out
 * against. `Modifier.offset` would paint the same pixels and measure as if nothing had moved,
 * leaving the row claiming the full sum of the discs - 24dp of phantom width at four avatars -
 * and the stack sitting 24dp further from the edge than it looks.
 *
 * ## Why a transparent gap and not a painted ring
 *
 * The discs are separated by *nothing*, which is what makes the overlap read as separate people.
 * The obvious alternative - a ring in the backdrop colour around each disc - cannot be done here:
 * the board's backdrop is [AmbientBackdrop], a gradient, so a single flat ring colour matches at
 * one point on the page and not at another. A gap needs no colour at all.
 *
 * ## Why each disc sits above the one to its left
 *
 * The stack is read left to right, and a disc is read from the part of it that is not covered.
 * With every disc above its left neighbour, each shows its leading
 * [ttAvatarStackDefaults.overlapVisible] and the *last* one is whole; the other order leaves the
 * first name legible and hides the rest behind it. `Arrangement` cannot express z-order, so this
 * is what the `zIndex` is for.
 */
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
    // Half the hidden part, so the initials land in the middle of the slice that is actually
    // visible rather than the middle of the disc. Centred in the disc, a two-letter run is
    // 13dp wide across a 24dp disc, so its trailing 2dp sits behind the next disc and the
    // second initial loses its stem.
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

/**
 * A disc's geometry and a stack's rules, named rather than inlined.
 *
 * A separate object because these are decisions that survive being read off the source, and a
 * test that reads them off the source costs nothing on any host.
 */
object ttAvatarStackDefaults {

    /**
     * Four discs, then a count.
     *
     * Four is a width decision, not a legibility one: the stride is 16dp, so five discs span
     * 88dp, which is a third of a card and most of the room a board header has beside the
     * switcher. The count chip is what keeps the number honest - a stack that silently dropped
     * names would be worse than one that admits it stopped.
     */
    val maxVisible: Int = 4

    /**
     * 16dp of a 24dp disc left showing.
     *
     * Two thirds, and the number that falls out of it is the stride: two discs 16dp apart show
     * 16dp of each other, so the stack's pitch and its visible width are the same figure. Any
     * more and the stack is a row of separate circles with the interesting part covered; any
     * less and it is a smear with no disc left to read.
     */
    val overlapVisible: Dp = 16.dp
}

/** One size for every disc, so a stack is made of discs rather than of circles. */
object ttAvatarSize {

    /** 24dp — a stack's disc, and big enough for two initials in `captionSmall`. */
    val avatar: Dp = 24.dp
}

/**
 * One tag per disc, and one for the count chip.
 *
 * Because the overlap cannot be asserted any other way: a disc emits no semantics of its own, so
 * the only nodes a caller can see are the initials *inside* it, and two adjacent discs' initials
 * do not touch even when the discs do. A test that measured those would be measuring the inset
 * rather than the stack.
 */
const val DISC_TAG = "tt.avatar.disc"

/** The chip that says how many names are not being drawn. See [DISC_TAG] for why it is tagged. */
const val OVERFLOW_TAG = "tt.avatar.overflow"

/**
 * The letters a name is reduced to.
 *
 * First letters of the first and last whitespace-separated token, uppercased: "Amir Reza" is `AR`
 * and "amir" alone is `A`. Two letters because one letter is not an initial - it is the whole name
 * abbreviated - and two is what fits the slice a stack leaves visible.
 *
 * A name of more than two tokens contributes only its first and last, so "Mary Jane Watson" is
 * `MW` rather than `MJW`: three letters at 24dp is a smudge and the middle initial is the one
 * nobody reads. Empty tokens are dropped before the ends are chosen, so "Amir " is `A` and "Amir
 * Reza" with a doubled space between the words is still `AR`.
 */
fun ttInitialsFor(name: String): String {
    val tokens = name.split(NAME_WHITESPACE).filter { it.isNotEmpty() }
    return when {
        tokens.isEmpty() -> ""
        tokens.size == 1 -> tokens.first().take(1)
        else -> tokens.first().take(1) + tokens.last().take(1)
    }.uppercase()
}

/** Names arrive as one free-text field, so the split is on every kind of space, not just `' '`. */
private val NAME_WHITESPACE = Regex("\\s+")

/** The two alphas a disc is drawn with, named because they were previously a private copy. */
private object ttAvatar {
    const val FILL_ALPHA = 0.18f
    const val RIM_ALPHA = 0.36f
}

/**
 * Describes a whole stack as one thing.
 *
 * Not decoration: an overlapping stack is several initials with no labels, and a screen reader
 * announcing them one node at a time is worse than announcing none. This is the seam a caller
 * merges its own wording into - a generic "initials" description would be a lie about all of
 * them - and the reason [ttInitialsStack] does not compose one itself.
 */
fun Modifier.ttAvatarDescription(description: String): Modifier = semantics {
    contentDescription = description
}