package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.sign

/**
 * Calculates snap settlement offset for horizontal board lane scrolling.
 * Uses fixed-stride geometry rather than dynamic lazy measurements because the board composes
 * all columns eagerly to keep uniform lane heights.
 */
internal fun boardLaneSnapOffset(
    scrollValue: Float,
    maxScrollValue: Float,
    velocity: Float,
    columnCount: Int,
    laneStridePx: Float,
    minFlingVelocityPx: Float,
): Float {
    if (columnCount <= 1 || laneStridePx <= 0f || maxScrollValue <= 0f) return 0f

    fun restingPosition(index: Int): Float =
        (index.coerceIn(0, columnCount - 1) * laneStridePx).coerceIn(0f, maxScrollValue)

    // Clamp explicitly to last lane at scroll bound to prevent off-by-one back-fling lag
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

/** Provides snap settlement calculations to Compose snap fling behavior from active [ScrollState]. */
internal class BoardLaneSnapLayoutInfo(
    private val scrollState: ScrollState,
    private val columnCount: Int,
    private val laneStridePx: Float,
    private val minFlingVelocityPx: Float,
) : SnapLayoutInfoProvider {

    // Withhold one stride of decay distance so natural coasting smoothly hands off to snap phase
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