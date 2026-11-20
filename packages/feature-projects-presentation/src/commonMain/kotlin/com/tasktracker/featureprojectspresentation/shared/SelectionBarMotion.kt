package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.LocalReducedMotion

/**
 * How long the selection action bar takes to arrive and leave.
 *
 * One constant for both screens, for the same reason [SEARCH_REVEAL_MILLIS] is one constant: two
 * durations that happen to agree today are how two screens start disagreeing, and the difference
 * is invisible because each screen is only ever seen on its own. Slightly longer than the search
 * reveal because the bar carries the count and the batch verbs — the eye needs a beat to read it as
 * a new row rather than a flash of colour on the header.
 */
internal const val SELECTION_BAR_REVEAL_MILLIS = 220

/** Leaving is quicker than arriving: the tap that ends the mode should feel immediate, and there
 * is nothing new to read on the way out. */
private const val SELECTION_BAR_EXIT_MILLIS = 160

/** The fade settles before the travel finishes, which hides the clipping edge of a growing
 * container — the same reasoning as the search field's fade. */
private const val SELECTION_BAR_FADE_IN_MILLIS = 140
private const val SELECTION_BAR_FADE_OUT_MILLIS = 100

/**
 * Slides [content] into the column and back out when [visible] turns false.
 *
 * Used where the action bar is a *new* row (the board), so the rows below it are pushed down by a
 * height that grows rather than jumping by the bar's full height in one frame. The content must stay
 * composed by this call — hiding it behind an `if` would remove the exit, because a composable that
 * is no longer called cannot animate away.
 *
 * When the platform reports reduced motion the state change still happens, it just happens without
 * travel: the bar appears and disappears with no fade, no slide.
 */
@Composable
internal fun SelectionBarReveal(
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
            fadeIn(animationSpec = tween(SELECTION_BAR_FADE_IN_MILLIS)) +
                expandVertically(
                    animationSpec = tween(SELECTION_BAR_REVEAL_MILLIS),
                    expandFrom = Alignment.Top,
                )
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(SELECTION_BAR_FADE_OUT_MILLIS)) +
                shrinkVertically(
                    animationSpec = tween(SELECTION_BAR_EXIT_MILLIS),
                    shrinkTowards = Alignment.Top,
                )
        },
    ) {
        content()
    }
}

/**
 * Cross-fades [bar] with the row it replaces while [selecting] flips.
 *
 * The backlog puts the action bar in the slot the sort row (or the search field) occupies rather
 * than under it, so there is no extra row to unfold: the two swap places. `AnimatedContent` is what
 * keeps both composed for the length of the swap and animates the slot's height through it, which
 * matters because the backlog header is measured to inset the list below it — a size jump there is
 * a jump of every row on screen.
 *
 * Reduced motion removes the fade and slide but keeps the swap, and the size transform snaps so the
 * header does not grow at a speed the user asked not to see.
 */
@Composable
internal fun SelectionBarSwap(
    selecting: Boolean,
    modifier: Modifier = Modifier,
    bar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current

    AnimatedContent(
        targetState = selecting,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                ContentTransform(
                    targetContentEnter = EnterTransition.None,
                    initialContentExit = ExitTransition.None,
                    sizeTransform = SizeTransform(clip = true) { _, _ -> snap() },
                )
            } else {
                ContentTransform(
                    targetContentEnter = fadeIn(animationSpec = tween(SELECTION_BAR_FADE_IN_MILLIS)) +
                        slideInVertically(
                            animationSpec = tween(SELECTION_BAR_REVEAL_MILLIS),
                        ) { -it / 4 },
                    initialContentExit = fadeOut(animationSpec = tween(SELECTION_BAR_FADE_OUT_MILLIS)) +
                        slideOutVertically(
                            animationSpec = tween(SELECTION_BAR_EXIT_MILLIS),
                        ) { -it / 4 },
                    sizeTransform = SizeTransform(clip = true) { _, _ ->
                        tween(SELECTION_BAR_REVEAL_MILLIS)
                    },
                )
            }
        },
        label = "selection-bar",
    ) { active ->
        if (active) bar() else content()
    }
}
