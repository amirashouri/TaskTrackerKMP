package com.tasktracker.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset

/**
 * Shared animated bottom-sheet frame for modal forms.
 *
 * The same surface, content inset, outside-tap handling and slide transition wrap project and
 * task forms. The form content stays responsible for its fields and actions; this component
 * owns only the sheet and its presentation.
 *
 * ## Why a modal sheet has to go through this and not draw its own background
 *
 * The surface here is a lens *over* a solid base — `ttSurface()` at `1f - LENS_ALPHA` — and
 * that base is what stops the page showing through a modal form. A sheet that calls
 * [ttGlassSurface] with no `base` instead gets the fill ramp: shipped alpha
 * `FILL_ALPHA * FILL_ALPHA` = 0.3025, tinted toward the accent. That is a translucent,
 * accent-coloured panel, which is the correct vocabulary for a chip sitting on a background
 * and the wrong one for something the user has committed to reading and tapping.
 *
 * The two look close enough together to pass review, which is how the project picker ended up
 * with one while the project and task editors had the other — same rounded rect, same hairline,
 * same name, ~3x the transmission. Anything that wants a modal sheet's frame takes it from here.
 */
@Composable
fun TTBottomSheetScaffold(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetModifier: Modifier = Modifier,
    onDismissed: () -> Unit = {},

    /**
     * Whether the sheet body scrolls as one surface, or the body scrolls itself.
     *
     * The scaffold scrolls its content so that a form taller than the viewport — a project
     * editor with a long description, a task form on a small phone — stays reachable instead
     * of running off the top of the screen. That is right for a `Column` body and fatal for a
     * `LazyColumn` one: Compose refuses to measure a scrollable inside a scrollable of the
     * same axis, and it refuses at measure time rather than at compile time, so the failure is
     * a crash on the first frame that happens to overflow rather than an error from the build.
     *
     * So a body that brings its own scrolling passes `false` and keeps its `LazyColumn`. The
     * scaffold still owns everything that makes the sheet a sheet — the frame, the padding,
     * the insets, the scrim, the transition — so opting out of this one modifier is not
     * opting out of the shared language.
     *
     * Defaults to `true` because a plain `Column` body is the common case, and a body that
     * forgets to say so should land on the arrangement that scrolls rather than on one that
     * can crash.
     */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val transitionState = remember { MutableTransitionState(false) }
    var hasBeenVisible by remember { mutableStateOf(false) }
    // Remembered unconditionally, and applied conditionally below. A `remember` inside the
    // `if` would be a composable call in a branch, which is legal but throws the state away on
    // the branch that skips it, so toggling [scrollable] would restart the scroll position.
    val scrollState = rememberScrollState()

    SideEffect { transitionState.targetState = visible }
    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (transitionState.targetState) hasBeenVisible = true
        if (hasBeenVisible && !transitionState.currentState && !transitionState.targetState) {
            onDismissed()
        }
    }

    AnimatedVisibility(
        visibleState = transitionState,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(durationMillis = SHEET_FADE_MILLIS)),
        exit = fadeOut(animationSpec = tween(durationMillis = SHEET_FADE_MILLIS)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismissRequest,
                    )
                    .testTag(TTBottomSheetScrimTag),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md)
                    // Apply one bottom inset at a time: the keyboard already covers the
                    // navigation area, and adding both shortens the form's viewport.
                    .then(if (keyboardVisible) Modifier.imePadding() else Modifier.navigationBarsPadding())
                    // The shell draws its tab bar over the top of this surface, so a sheet
                    // that does not clear it puts its action row behind the bar — and the bar
                    // takes the touch, which silently dismissed the form instead of submitting.
                    .padding(bottom = LocalTabBarScrollUnderInset.current)
                    .animateEnterExit(
                        enter = slideInVertically(
                            animationSpec = SHEET_SPRING,
                            initialOffsetY = { height -> height },
                        ),
                        exit = slideOutVertically(
                            animationSpec = SHEET_SPRING,
                            targetOffsetY = { height -> height },
                        ),
                    )
                    .then(sheetModifier)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                    .ttGlassSurface(
                        shape = RoundedCornerShape(ttRadius.surface),
                        base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                    )
                    .padding(horizontal = ttSpacing.md, vertical = ttSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
                content = content,
            )
        }
    }
}

const val TTBottomSheetScrimTag = "design.bottomSheet.scrim"

private const val SHEET_FADE_MILLIS = 160
private val SHEET_SPRING = spring<IntOffset>(dampingRatio = 0.86f, stiffness = 420f)
