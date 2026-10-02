package com.tasktracker.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A row that can be dragged aside to reveal an action behind it, and springs back.
 *
 * ## Why this is hand-written
 *
 * Compose Multiplatform's swipe-to-dismiss lives in Material3, and this build has no
 * Material3 - the app is built on its own glass design system, and adding Material3 for one
 * gesture would bring a second, competing set of tokens onto the same screen. Foundation has
 * the gesture ([detectHorizontalDragGestures]) and the spring
 * ([Animatable]); what it does not have is the assembled behaviour, which is what this is.
 *
 * ## The offset is a plain state, and only the spring is an animation
 *
 * The offset is held in a `mutableFloatStateOf` and written **synchronously** as the finger
 * moves. The first version held it in the [Animatable] and called `snapTo` from a launched
 * coroutine on every drag event, which broke in a way worth recording.
 *
 * The commit decision - has the drag passed the threshold? - was read from `offset.value` in
 * `onDragEnd`, but every write to that value was still sitting in a coroutine that had not
 * run. So the answer depended on how far the finger had moved *and* on whether the scheduler
 * had caught up: a 120px drag committed and an 80px drag did not, when the threshold was
 * 64px and 80 > 64. A destructive gesture whose trigger is a race rather than a distance is
 * the worst possible shape for one, and no test written against a fixed distance would have
 * found it - the same 80px can commit or not depending on frame timing.
 *
 * So the drag position is plain state, written as the events arrive, which also means the
 * row tracks the finger exactly instead of easing towards it. The [Animatable] is left to do
 * the one thing it is good at: animating a return to zero, where the start velocity is unknown
 * and a fixed-duration tween either overshoots a slow release or looks sluggish after a fast
 * one.
 *
 * ## Revealing is not acting
 *
 * Passing the threshold reports [onRevealed] and then waits. It does **not** run the action,
 * and that is the design rather than a missing step.
 *
 * The action behind a swipe on a project row is destructive and *conditional* - a project
 * with unfinished work is archived, one without is deleted for good - so what it will do
 * cannot be known at the row and has to be worded by whoever owns the project. A swipe that
 * deleted a project and its history outright, with nothing said first, is the failure this
 * avoids. Callers are expected to put their existing confirmation in front of the action and
 * pass [isRevealed] while it is up.
 *
 * The host therefore owns the revealed state rather than this holding it, so a dialog
 * dismissed by a back gesture, or the row leaving the list, all close the row instead of
 * leaving it stuck open over something that is no longer there.
 *
 * ## The threshold is absolute, not a fraction of the row - but the finger travels slop further
 *
 * A fraction of the row would make the gesture easier on a tablet and impossible on a narrow
 * phone, which is backwards: the same flick should mean the same commitment at every width.
 * So [threshold] is a distance.
 *
 * It is measured from where the drag *begins*, and the drag only begins once the finger has
 * travelled the platform's horizontal touch slop, which the first version of this measured
 * the hard way. Probing the boundary one pixel at a time put the commit at 82px of finger
 * travel against a 64px threshold: 64 plus 18dp of slop that
 * [detectHorizontalDragGestures] consumes before it reports the first drag event. That is
 * ordinary for this kind of gesture and is not worth working around - a gesture that has not
 * yet cleared slop has not started, so it has not travelled anywhere - but it does mean the
 * number in [SwipeDefaults.threshold] is the travel *after* the slop, and a caller reasoning
 * about how far somebody has to push should add roughly 18dp to it.
 *
 * ## Movement below the touch slop is a tap
 *
 * [detectHorizontalDragGestures] waits for horizontal slop before consuming anything, so a
 * short nudge is not a drag at all - it is a tap, and the row underneath is entitled to
 * behave like one. That is the platform's arbitration rather than this component's choice,
 * and it is why a "did not commit" test has to drag *past* slop to be testing anything: a
 * 12px nudge proves only that a tap still works.
 *
 * ## Why a horizontal drag does not fight the list
 *
 * This is normally where a gesture inside a scrolling list goes wrong, and the reason it
 * does not here is that the two are on different axes with separate slop checks. Nothing in
 * here consumes a pointer event speculatively, so a vertical flick is left to the list and a
 * horizontal one is left to this.
 */
@Composable
fun TTSwipeToRevealRow(
    revealLabel: String,
    isRevealed: Boolean,
    onRevealed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    threshold: Dp = SwipeDefaults.threshold,
    maxTravel: Dp = SwipeDefaults.maxTravel,
    revealColor: Color = ttError(),
    content: @Composable () -> Unit,
) {
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val spring = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val maxTravelPx = with(density) { maxTravel.toPx() }
    val shape = RoundedCornerShape(ttRadius.row)

    // Animates the *state*, rather than being the state. See the class KDoc for why the two
    // roles are split: a launched `snapTo` per drag event put the commit decision in a race.
    fun CoroutineScope.animateOffsetTo(target: Float) {
        launch {
            spring.snapTo(offsetPx)
            spring.animateTo(target, animationSpec = SwipeDefaults.snapSpec) { offsetPx = value }
        }
    }

    // Held open by the host, closed by the host, and keyed on the flag so a reveal that came
    // from a drag and one that came from a state change animate identically.
    LaunchedEffect(isRevealed, enabled) {
        if (!enabled || !isRevealed) {
            spring.snapTo(offsetPx)
            spring.animateTo(0f, animationSpec = SwipeDefaults.snapSpec) { offsetPx = value }
        } else {
            spring.snapTo(offsetPx)
            spring.animateTo(maxTravelPx, animationSpec = SwipeDefaults.snapSpec) { offsetPx = value }
        }
    }

    Box(modifier = modifier.clip(shape).background(revealColor)) {
        // At the leading edge - the side the row moves *from*, so it is uncovered where the
        // finger started rather than where it ended.
        ttText(
            text = revealLabel,
            style = ttTypography.body,
            color = ttCanvas(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = ttSpacing.xl),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offsetPx }
                .background(ttSurface())
                .pointerInput(enabled, thresholdPx, maxTravelPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Read synchronously. Whatever the finger actually did is in
                            // `offsetPx` by now, with no coroutine in between.
                            if (offsetPx >= thresholdPx) onRevealed()
                            // Always closes, committed or not: the reveal is a press and not
                            // a latch. Leaving it open would need the row to be told the
                            // action was dealt with, and the one thing it must never do is
                            // sit open over a project whose fate is still undecided.
                            scope.animateOffsetTo(0f)
                        },
                        onDragCancel = { scope.animateOffsetTo(0f) },
                    ) { change, dragAmount ->
                        change.consume()
                        // Rightward only, and never past the cap. A leftward drag on a row
                        // with nothing behind it is a mis-swipe, and honouring it would pull
                        // the row off its own content; travelling past the cap would hide
                        // the label the reveal exists to show.
                        offsetPx = (offsetPx + dragAmount).coerceIn(0f, maxTravelPx)
                    }
                },
        ) {
            content()
        }
    }
}

/**
 * No test tag and no `contentDescription` on purpose.
 *
 * A tag here would have to be derived from something, and both candidates are wrong: the
 * label makes it user-facing text, so renaming the string breaks the tests, and it is
 * identical for every row, so it matches all of them at once. A `contentDescription` on the
 * wrapper would *replace* the row's own semantics for a screen reader, making a project row
 * announce as "Delete" - the component would be hiding the thing it is attached to. The
 * caller already tags the row it wraps, which is the stable, per-row handle a test wants.
 */

/** The shared defaults, named so a call site can override one without restating the rest. */
object SwipeDefaults {

    /**
     * 64dp of travel to commit.
     *
     * A short flick rather than a deliberate haul. The gesture should be reachable, and the
     * confirmation is what makes reaching it safe, so making the gesture itself hard would
     * only push people towards deleting without being asked.
     */
    val threshold: Dp = 64.dp

    /** 120dp, enough to show the reveal label clear of the row's own leading edge. */
    val maxTravel: Dp = 120.dp

    /**
     * The spring every close uses.
     *
     * `dampingRatio` just under 1 gives the small settle that reads as "this was refused"
     * rather than "this was queued".
     */
    val snapSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
