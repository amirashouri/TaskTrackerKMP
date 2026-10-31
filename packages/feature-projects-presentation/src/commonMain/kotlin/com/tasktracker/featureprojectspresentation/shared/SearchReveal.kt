package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.LocalReducedMotion

/**
 * How long the search field takes to unfold and fold away.
 *
 * One constant for both screens, for the same reason [SEARCH_DEBOUNCE_MILLIS] is one constant:
 * two durations that happen to agree today are how two screens start disagreeing, and the
 * difference is invisible because each screen is only ever seen on its own.
 *
 * 200ms is a compromise between the two things the motion has to do. Shorter and the row does
 * not read as *unfolding* — it flickers into place. Longer and the field arrives after the
 * finger has already moved on, which makes the tap feel unresponsive even though the work
 * started immediately.
 */
internal const val SEARCH_REVEAL_MILLIS = 200

/** The fade is deliberately shorter than the unfold: opacity settling early hides the clipping
 * edge of a growing container, which is the part that looks cheap if it is seen. */
private const val SEARCH_FADE_IN_MILLIS = 120
private const val SEARCH_FADE_OUT_MILLIS = 90

/**
 * Reveals [content] by unfolding it vertically, and folds it away again when [visible] is false.
 *
 * ## Why the field is not simply hidden with `if`
 *
 * A field that pops into existence moves everything below it by its own height in a single
 * frame. On these screens that is the list, which means the user's eye is following rows that
 * jump. Unfolding from the top instead keeps the list anchored under the header while the
 * space is made, and shrinking back gives the row back rather than deleting it.
 *
 * ## Reduced motion
 *
 * When the platform reports reduced motion, the transitions are `None`: the field still appears
 * and disappears, it just does so without travel. The state change is not optional — only the
 * motion is. Hiding the field behind an animation the user has asked not to see would be a
 * worse accessibility outcome than the jump this exists to avoid.
 */
@Composable
internal fun SearchReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(SEARCH_FADE_IN_MILLIS)) +
                expandVertically(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    expandFrom = Alignment.Top,
                )
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(SEARCH_FADE_OUT_MILLIS)) +
                shrinkVertically(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    shrinkTowards = Alignment.Top,
                )
        },
    ) {
        content()
    }
}
