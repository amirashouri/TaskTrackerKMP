package com.tasktracker.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Shared animated bottom-sheet frame for modal forms and centered windows. */
@Composable
fun TTBottomSheetScaffold(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetModifier: Modifier = Modifier,
    onDismissed: () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val presentation = LocalSheetPresentation.current

    val isWindow = presentation == SheetPresentation.Window
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    val scrollState = rememberScrollState()

    LaunchedEffect(transitionState.isIdle, transitionState.currentState) {
        if (transitionState.isIdle && !transitionState.currentState && !visible) {
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
                    .then(
                        if (isWindow) {
                            Modifier
                                .widthIn(max = 560.dp)
                                .heightIn(max = 760.dp)
                                .padding(horizontal = ttSpacing.xl, vertical = 72.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md)
                        }
                    )
                    .then(if (keyboardVisible) Modifier.imePadding() else Modifier.navigationBarsPadding())
                    .padding(bottom = LocalTabBarScrollUnderInset.current)
                    .animateEnterExit(
                        enter = if (isWindow) {
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

/** Modal display presentation mode (Bottom sheet vs Centered window). */
enum class SheetPresentation {
    BottomSheet,
    Window,
    ;
}

val LocalSheetPresentation: ProvidableCompositionLocal<SheetPresentation> =
    compositionLocalOf { SheetPresentation.BottomSheet }

private const val SHEET_FADE_MILLIS = 160
private const val WINDOW_SCALE = 0.94f
private val SHEET_SPRING = spring<IntOffset>(dampingRatio = 0.86f, stiffness = 420f)