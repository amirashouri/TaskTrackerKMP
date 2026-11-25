package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSurface

/**
 * How long the search field takes to appear or disappear.
 *
 * One constant for both screens, for the same reason [SEARCH_DEBOUNCE_MILLIS] is one constant:
 * two durations that happen to agree today are how two screens start disagreeing, and the
 * difference is invisible because each screen is only ever seen on its own.
 *
 * 200ms is a compromise between the two things the motion has to do. Shorter and the field
 * flickers into place; longer and the field arrives after the finger has already moved on,
 * which makes the tap feel unresponsive even though the work started immediately.
 */
internal const val SEARCH_REVEAL_MILLIS = 200

/** The fade settles before the scale, so the field is legible before it finishes moving. */
private const val SEARCH_FADE_IN_MILLIS = 120
private const val SEARCH_FADE_OUT_MILLIS = 90

/**
 * How much of [ttSurface] backs an overlay field.
 *
 * The field's own glass is translucent, and a compact header has a project title under the
 * field's slot. Backing it with a near-opaque surface keeps that title from ghosting through
 * the input; the last few percent stay transparent so the glass still reads as glass.
 */
private const val SEARCH_OVERLAY_BASE_ALPHA = 0.94f

/**
 * Reveals [content] vertically, or in place when [overlay] is true.
 *
 * ## The two modes are not interchangeable
 *
 * A page whose field is a row above its list (the Epic list) can use the default in-flow mode:
 * the field takes space, and `expandVertically` gives that space back frame by frame.
 *
 * The board and the backlog use [overlay] instead. Their field used to unfold between the
 * header and the list, which animated a layout dimension: every frame handed a new height to
 * the lanes below it, and on the backlog the measured header height feeds the list's top
 * inset, so every frame recomputed that padding too. Tweaking the fade or the easing only
 * changed how fast the content moved. An overlay gives the field no say in layout: the caller
 * gives it a fixed slot, and only alpha and scale animate. The field is drawn over the header
 * row it belongs to, ending before its toggle.
 *
 * ## Reduced motion
 *
 * With reduced motion the transitions are `None` in either mode: the field still appears and
 * disappears, it just does so without travel. Hiding the field behind an animation the user
 * asked not to see would be a worse accessibility outcome than the movement this avoids.
 */
@Composable
internal fun SearchReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val enter: EnterTransition = when {
        reducedMotion -> EnterTransition.None
        overlay -> fadeIn(animationSpec = tween(SEARCH_FADE_IN_MILLIS)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(SEARCH_REVEAL_MILLIS))
        else -> expandVertically(
            animationSpec = tween(SEARCH_REVEAL_MILLIS),
            expandFrom = Alignment.Top,
        )
    }
    val exit: ExitTransition = when {
        reducedMotion -> ExitTransition.None
        overlay -> fadeOut(animationSpec = tween(SEARCH_FADE_OUT_MILLIS)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(SEARCH_REVEAL_MILLIS))
        else -> shrinkVertically(
            animationSpec = tween(SEARCH_REVEAL_MILLIS),
            shrinkTowards = Alignment.Top,
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
    ) {
        if (!overlay) {
            content(Modifier)
        } else {
            Box(
                // The slot is the header row's height, which can be shorter than the field's
                // own minimum. `unbounded` lets the field measure itself and overflow the slot
                // by a few pixels instead of being squeezed into an undersized tap target.
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.CenterStart, unbounded = true),
            ) {
                content(
                    Modifier.background(
                        color = ttSurface().copy(alpha = SEARCH_OVERLAY_BASE_ALPHA),
                        shape = RoundedCornerShape(ttRadius.control),
                    ),
                )
            }
        }
    }
}

/**
 * The same reveal, unfolding sideways into a row rather than over a fixed slot.
 *
 * A wide header puts the sort row and the search field beside each other, so opening the
 * field has to make room for it rather than cover anything. `expandHorizontally` grows the
 * field's slot from zero width while the sort row's `weight` gives the space back frame by
 * frame, which is the shrink the eye reads as one control making room for another. No row
 * below that header changes height, so this travel cannot push the list.
 *
 * Durations and the reduced-motion rule are shared with [SearchReveal] on purpose: the two
 * layouts of the same field must not unfold at different speeds, and reduced motion removes
 * the travel rather than the field.
 */
@Composable
internal fun SearchRevealBeside(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(SEARCH_FADE_IN_MILLIS)) +
                expandHorizontally(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    expandFrom = Alignment.End,
                )
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(SEARCH_FADE_OUT_MILLIS)) +
                shrinkHorizontally(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    shrinkTowards = Alignment.End,
                )
        },
    ) {
        content()
    }
}
