package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * A continuous glass slider, for a value the user is meant to feel rather than pick.
 *
 * Built here rather than taken from Material because this package does not use Material, and
 * built here rather than in the settings feature because a control that paints a fill, a rim
 * and a thumb out of glass tokens is exactly what a feature may not hand-roll — see the
 * Liquid Glass rule in `AGENTS.md`.
 *
 * ## Why the gesture is not `anchoredDraggable`
 *
 * The value is a fraction rather than a member of a list, so there is nothing to anchor to: a
 * pointer's x mapped to a fraction *is* the whole interaction. Fling and rubber-banding are
 * deliberately absent — this sets an appearance, and a control that keeps moving after the
 * finger has left is one whose final value the user cannot predict.
 *
 * ## Why there are two callbacks
 *
 * [onValueChange] fires on every frame of a drag and [onValueChangeFinished] fires exactly
 * once when the gesture ends, and the split is the point: the appearance has to follow the
 * thumb *while* it moves, but the value it settles on is the one worth persisting, and a disk
 * write per frame is not.
 *
 * [onValueChangeFinished] is handed the value rather than taking none, and that is not
 * convenience. The obvious no-argument shape makes the caller close over its own `value`, which
 * only reports the settled number if the caller has already recomposed — so the write depends
 * on a recomposition that a caller holding its state in a `StateFlow` will do and a caller
 * holding it in a plain parameter will not. The slider knows the last value it emitted, so it
 * says so.
 *
 * ## Haptic feedback at milestones
 *
 * When [hapticMilestones] is non-empty (default: 25%, 50%, 75%), the slider fires a light
 * haptic tick each time the thumb crosses a milestone during a drag. The haptic fires on the
 * *crossing* — not on every frame at the milestone — so a slow drag across 50% fires once.
 * The final commit also fires if it crosses a milestone not already hit during the drag.
 *
 * ## Why the semantics are not optional
 *
 * The accessibility contract is written onto the node rather than left to the visual: a slider
 * that only answers a drag is unusable with a screen reader and untestable, and both of those
 * are defects rather than omissions. `progressBarRangeInfo` gives the announced percentage and
 * range; `setProgress` gives assistive technology a way to move it with no pointer at all,
 * which is also what the test host drives.
 */
@Composable
fun ttSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    hapticMilestones: List<Float> = listOf(0.25f, 0.5f, 0.75f),
) {
    var trackWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptics = HapticFeedback()

    fun fractionAt(xPx: Float): Float =
        if (trackWidthPx > 0) (xPx / trackWidthPx).coerceIn(0f, 1f) else value

    // Track which milestones have been crossed during the current gesture
    var crossedMilestones by remember { mutableStateOf(mutableSetOf<Float>()) }

    fun checkAndFireHaptic(newValue: Float, oldValue: Float) {
        if (hapticMilestones.isNotEmpty()) {
            val sortedMilestones = hapticMilestones.sorted()
            for (milestone in sortedMilestones) {
                if (milestone !in crossedMilestones) {
                    val crossed = if (newValue > oldValue) {
                        // Dragging forward: crossed if newValue >= milestone and old value < milestone
                        newValue >= milestone && oldValue < milestone
                    } else {
                        // Dragging backward: crossed if newValue <= milestone and old value > milestone
                        newValue <= milestone && oldValue > milestone
                    }
                    if (crossed) {
                        crossedMilestones.add(milestone)
                        haptics.tick()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ttSpacing.iconButtonSize)
            .onSizeChanged { trackWidthPx = it.width }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { requested ->
                    val clamped = requested.coerceIn(0f, 1f)
                    onValueChange(clamped)
                    onValueChangeFinished(clamped)
                    true
                }
            }
            .pointerInput(trackWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastEmitted = fractionAt(down.position.x)
                    crossedMilestones.clear()
                    // Check if initial tap crosses a milestone
                    checkAndFireHaptic(lastEmitted, value)
                    onValueChange(lastEmitted)
                    down.consume()

                    var previousEmitted = lastEmitted
                    try {
                        var pointer = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointer } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                lastEmitted = fractionAt(change.position.x)
                                checkAndFireHaptic(lastEmitted, previousEmitted)
                                previousEmitted = lastEmitted
                                onValueChange(lastEmitted)
                            }
                        }
                    } finally {
                        // Fire haptic for any milestone crossed by the final value but not during drag
                        checkAndFireHaptic(lastEmitted, previousEmitted)
                        onValueChangeFinished(lastEmitted)
                    }
                }
            },
    ) {
        // Track: a glass pill with the accent filled up to the value. The fill is a child of the
        // track rather than a second track, so the two cannot disagree about where they end.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ttSpacing.sm)
                .align(Alignment.Center)
                .clip(CircleShape)
                .ttGlassSurface(shape = CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(ttSpacing.sm)
                    .clip(CircleShape)
                    .background(ttAccent()),
            )
        }

        // Thumb, placed by layout offset rather than by a `Box` alignment, because the value is
        // continuous and there is no discrete end to align to. The travel is inset by a thumb
        // at each end, which is what keeps the thumb's *centre* on the value rather than its
        // leading edge.
        val thumbPx = with(density) { ttSliderThumbSize.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        x = (value.coerceIn(0f, 1f) * (trackWidthPx - thumbPx)).roundToInt(),
                        y = 0,
                    )
                }
                .size(ttSliderThumbSize)
                .ttGlassSurface(shape = CircleShape, tint = ttAccent()),
        )
    }
}

/**
 * The thumb's diameter.
 *
 * Borrowed from [ttSpacing.xxxl] rather than given a literal: 24dp is a spacing step that
 * already exists, and a new number for a control that needed no new size is a second place for
 * the app's dimensions to drift.
 */
private val ttSliderThumbSize = ttSpacing.xxxl