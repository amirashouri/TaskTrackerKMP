package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.sign

/**
 * How far the board's lane row still has to travel to come to rest on a lane boundary.
 *
 * A free fling across four 264dp lanes lands anywhere, including half-hidden at both ends of the
 * row, and a board that has to be *aimed* at is a board the user stops trusting. So the row
 * settles with one lane's leading edge against the row's own gutter.
 *
 * ## Why this is a free function rather than a `LazyListSnapLayoutInfoProvider`
 *
 * Every built-in provider answers "where is the next snap position" from a *lazy* layout's
 * measured item offsets, because that is the only place the positions exist. This row is not
 * lazy - [ProjectBoardScreen] composes every lane on purpose, because a lane's height must not
 * depend on how far the user has scrolled - so there is no `LazyListState` to ask, and no
 * measured offsets to read. The geometry is known instead: lanes are all the same width and
 * every gap is the same, so the resting position of lane *i* is exactly `i * stride`.
 *
 * Deriving it from constants rather than from measurement is only safe because the two cannot
 * drift, which is what [boardLaneStride] exists to guarantee: the row's layout and this
 * function both read it. Measuring would be the alternative and is the wrong trade - it would
 * let a snap position agree with a layout that a visual regression had already broken.
 *
 * ## Why the leading gutter is not in the arithmetic
 *
 * The row starts with a spacer the width of the screen gutter, so lane *i* sits at content
 * coordinate `gutter + i * stride`. Asking for lane *i* to rest against the gutter means
 * scrolling by `gutter + i * stride - gutter`, and the gutter cancels. That is why the resting
 * positions here are multiples of the stride and nothing else: one fewer number to keep
 * straight, and the positions are then the same whether or not the row has a gutter, which is
 * what makes them testable without a density.
 *
 * ## One call is one lane, and the fling asks more than once
 *
 * A fling goes to [next] and a back-fling to [previous]. One call therefore moves one lane,
 * *not* the whole distance the decay would have carried the row - and that distinction is the
 * point, because Compose asks this question repeatedly as a fling runs, with the velocity
 * falling each time. A hard flick is asked many times before it settles and so covers several
 * lanes; a gentle one is asked once or twice and covers one. The row's travel is therefore
 * proportional to the gesture in the way a carousel's is, and it always ends on a lane.
 *
 * The alternative - resolving the whole decay to a single destination in one call - sounds
 * tidier and is worse here: it would make the answer depend on a decay model this function
 * would have to own, duplicating the one `rememberSnapFlingBehavior` is already running. One
 * lane per step needs no model, and a target that can only ever be a lane boundary cannot be
 * wrong by more than half a lane.
 *
 * Below [minFlingVelocityPx] the velocity is no longer a direction but a rounding error, so
 * the nearest boundary wins instead. That is the case that covers the end of a fling, and it
 * is what lets a slow drag that stopped a few pixels past a lane come back to it.
 */
internal fun boardLaneSnapOffset(
    scrollValue: Float,
    maxScrollValue: Float,
    velocity: Float,
    columnCount: Int,
    laneStridePx: Float,
    minFlingVelocityPx: Float,
): Float {
    // Nothing to snap to: a single lane, a stride of nothing, or content that fits.
    if (columnCount <= 1 || laneStridePx <= 0f || maxScrollValue <= 0f) return 0f

    fun restingPosition(index: Int): Float =
        (index.coerceIn(0, columnCount - 1) * laneStridePx).coerceIn(0f, maxScrollValue)

    // Which lane the row is resting on, which is *not* the same as `scrollValue / stride`
    // rounded down once the end of the row clamps. The last lane's own boundary is past the
    // end of the scroll range, so the row comes to rest short of it - and at that resting
    // place the row is on the *last* lane, not the one the arithmetic names. Reading it as
    // the earlier lane costs the user a lane per back-fling at the end of the row: two
    // back-flings to get back to where one should have reached. So at the very end of the
    // range the index is the last lane outright.
    val index =
        if (scrollValue >= maxScrollValue) columnCount - 1 else floor(scrollValue / laneStridePx).toInt()
    val previous = restingPosition(index - 1)
    val current = restingPosition(index)
    val next = restingPosition(index + 1)

    val target = when {
        abs(velocity) < minFlingVelocityPx ->
            listOf(previous, current, next).minBy { abs(it - scrollValue) }
        velocity > 0f -> next
        else -> previous
    }
    return target - scrollValue
}

/**
 * Feeds [boardLaneSnapOffset] from a live [ScrollState].
 *
 * The provider is asked again as the snap animation runs, so it reads the state on every call
 * rather than caching it - `maxScrollValue` in particular is only known once the row has been
 * measured, and it changes when lanes of different heights change the row's own height.
 *
 * The two reads are widened to `Float` because [ScrollState] counts in whole pixels while a
 * snap target need not land on one. The row's own scrolling stays integral - a hand is not
 * that precise and Compose rounds every frame anyway - but a target computed from `Float`
 * inputs is what lets the last lane rest exactly on the end of the row instead of one pixel
 * shy of it.
 */
internal class BoardLaneSnapLayoutInfo(
    private val scrollState: ScrollState,
    private val columnCount: Int,
    private val laneStridePx: Float,
    private val minFlingVelocityPx: Float,
) : SnapLayoutInfoProvider {

    /**
     * Hands back less than the full decay so the fling coasts and then snaps.
     *
     * The last lane's worth of the decay is withheld because that is the distance the snap
     * phase covers on its own; a decay that ran to its full projection would arrive at a
     * resting position already, leaving the snap phase to stand still. Withheld by a lane and
     * not by a pixel, so the approach is the same length whichever way the row is flung.
     */
    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float =
        (decayOffset.absoluteValue - laneStridePx).coerceAtLeast(0f) * decayOffset.sign

    override fun calculateSnapOffset(velocity: Float): Float = boardLaneSnapOffset(
        scrollValue = scrollState.value.toFloat(),
        maxScrollValue = scrollState.maxValue.toFloat(),
        velocity = velocity,
        columnCount = columnCount,
        laneStridePx = laneStridePx,
        minFlingVelocityPx = minFlingVelocityPx,
    )
}