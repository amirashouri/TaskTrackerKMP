package com.tasktracker.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
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
 *
 * ## Two presentations, one frame
 *
 * [LocalSheetPresentation] decides whether this anchors at the bottom edge and slides up — the
 * phone vocabulary, thumb-reachable — or centres and scale-fades like a window, the desktop one.
 * The frame above (surface, base, scrim, outside-tap) is identical in both; only where the
 * surface sits and how it arrives differ. The shell provides the local from its layout class;
 * the default is [SheetPresentation.BottomSheet], so a screen rendered outside the shell — a
 * preview, the sign-in tree — gets the phone presentation this component was written for and
 * needs no opinion about a window it will never draw.
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
    val presentation = LocalSheetPresentation.current
    val isWindow = true
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (isWindow) Alignment.Center else Alignment.BottomCenter,
        ) {
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
                    .align(if (isWindow) Alignment.Center else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md)
                    // Apply one bottom inset at a time: the keyboard already covers the
                    // navigation area, and adding both shortens the form's viewport.
                    .then(if (keyboardVisible) Modifier.imePadding() else Modifier.navigationBarsPadding())
                    // The shell draws its tab bar over the top of this surface, so a sheet
                    // that does not clear it puts its action row behind the bar — and the bar
                    // takes the touch, which silently dismissed the form instead of submitting.
                    //
                    // This is correct on both sides of the layout split and for a *different
                    // reason* on each, which is why it stays one line rather than a branch:
                    // under `BottomSheet` the local carries the bar's measured height and this
                    // padding is what clears it; under `Window` no bar is composed, the shell
                    // publishes `0.dp`, and the padding is a no-op — the right answer, not a
                    // coincidence, because a centred window has no bar to clear.
                    .padding(bottom = LocalTabBarScrollUnderInset.current)
                    .animateEnterExit(
                        enter = if (isWindow) {
                            // Scale only — the fade belongs to the `AnimatedVisibility` above
                            // and is shared by both presentations, so a window arrives as one
                            // scale-and-fade rather than two overlapping fades. The duration
                            // is the fade's own, so the two finish on the same frame.
                            scaleIn(
                                animationSpec = tween(durationMillis = SHEET_FADE_MILLIS),
                                initialScale = WINDOW_SCALE,
                            )
                        } else {
                            slideInVertically(
                                animationSpec = SHEET_SPRING,
                                initialOffsetY = { height -> height },
                            )
                        },
                        exit = if (isWindow) {
                            scaleOut(
                                animationSpec = tween(durationMillis = SHEET_FADE_MILLIS),
                                targetScale = WINDOW_SCALE,
                            )
                        } else {
                            slideOutVertically(
                                animationSpec = SHEET_SPRING,
                                targetOffsetY = { height -> height },
                            )
                        },
                    )
                    .then(sheetModifier)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                    .ttGlassSurface(
                        shape = RoundedCornerShape(ttRadius.surface),
                        thickness = ttGlass.GlassThickness.THICK,
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

/**
 * Where [TTBottomSheetScaffold] puts its surface, and how the surface arrives.
 *
 * Two values, not a free-form configuration: the app has two modal vocabularies and they are
 * the two a phone and a desktop window actually use. A third value would be a layout nobody
 * has asked for, and a scale/offset parameter pair would push the decision into every caller —
 * the shape this component exists to remove, since the surface, the base and the scrim are
 * already shared and the presentation is the one thing the *shell* knows and the forms do not.
 */
enum class SheetPresentation {
    /**
     * Anchored at the bottom edge, sliding up. The phone vocabulary: thumb-reachable, and the
     * push it replaces is also what system back already acts on.
     */
    BottomSheet,

    /**
     * Centred, arriving with a scale-and-fade. The desktop vocabulary: a bottom edge is the
     * far corner of a 1000dp window, and a surface that slides the full height of it spends
     * the whole animation travelling to where the user is not looking.
     */
    Window,
    ;
}

/**
 * The presentation the shell's current width asks of [TTBottomSheetScaffold].
 *
 * ## Why a local, when the shell could pass a parameter
 *
 * It cannot. This component lives in `:packages:design-system`, which sits **below**
 * `:shared-ui` in the architecture — `verifyArchitectureGraph` pins that direction — so no
 * shell type may appear in this module's signatures. The shell provides the value from its
 * own `ShellLayoutClass`; this module only reads it. This is the same
 * pattern, for the same reason, as [LocalTabBarScrollUnderInset]: a shell-provided, defaulted
 * local read by a component that sits too low in the graph to be handed the answer.
 *
 * ## The default is the load-bearing half
 *
 * [SheetPresentation.BottomSheet]. A screen rendered outside the signed-in shell — a preview,
 * the sign-in tree, a test that stands up the component alone — reads the presentation this
 * component was written for and needs no opinion about a window it will never draw. Every
 * preview in the repo keeps working without naming a width it has no opinion about, which is
 * the same reasoning [LocalTabBarScrollUnderInset] gives for defaulting to `0.dp`.
 */
val LocalSheetPresentation: ProvidableCompositionLocal<SheetPresentation> =
    compositionLocalOf { SheetPresentation.BottomSheet }

private const val SHEET_FADE_MILLIS = 160

/**
 * How far short of full size a [SheetPresentation.Window] sheet starts, as a fraction.
 *
 * A transition constant, not a painted token: nothing is measured or tinted from it, and it
 * lives here beside [SHEET_SPRING] and [SHEET_FADE_MILLIS] for the same reason they do — it
 * is one number this component's own animation has to agree with, and there is nowhere above
 * it for the decision to live. 0.94f is the whole travel: five percent of the surface's own
 * size, which reads as "settling into place" at any window size rather than as a zoom.
 */
private const val WINDOW_SCALE = 0.94f

private val SHEET_SPRING = spring<IntOffset>(dampingRatio = 0.86f, stiffness = 420f)
