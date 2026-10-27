package com.tasktracker.sharedui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.AppMark
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.splash_tagline
import com.tasktracker.resources.splash_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun SplashScreen(
    isWorking: Boolean,
    modifier: Modifier = Modifier,
) {
    // One flag for the whole entrance, not one per element. Three staggered elements with
    // three flags is three things to keep in step, and the stagger is expressed by the
    // animation delays instead — which is the original's arrangement too.
    var hasAppeared by remember { mutableStateOf(false) }

    // The mark scales up as it arrives, so the first frame is not a full-size logo that
    // blinks into existence. A `tween` over [SPLASH_MARK_ENTRANCE_MILLIS] rather than a
    // `spring`: a spring on an entrance has no settle point to aim at, and on a launch mark
    // the tail is read as a bounce rather than as the app settling into place.
    val markScale by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else SPLASH_MARK_ENTRANCE_SCALE,
        animationSpec = tween(durationMillis = SPLASH_MARK_ENTRANCE_MILLIS),
        label = "splashMarkScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else 0f,
        animationSpec = tween(
            durationMillis = SPLASH_CONTENT_ENTRANCE_MILLIS,
            delayMillis = SPLASH_CONTENT_ENTRANCE_DELAY_MILLIS,
        ),
        label = "splashContentAlpha",
    )
    // A rise as well as a fade: three elements appearing flat is three appearances, and one
    // gesture reads as one.
    // `animateDpAsState`, not `animateFloatAsState`: the value is an `offset` in dp, and
    // converting outside the animation would mean recomputing it on every frame.
    val contentOffset by animateDpAsState(
        targetValue = if (hasAppeared) 0.dp else SPLASH_CONTENT_ENTRANCE_OFFSET,
        animationSpec = tween(
            durationMillis = SPLASH_CONTENT_ENTRANCE_MILLIS,
            delayMillis = SPLASH_CONTENT_ENTRANCE_DELAY_MILLIS,
        ),
        label = "splashContentOffset",
    )

    LaunchedEffect(Unit) { hasAppeared = true }

    Box(modifier = modifier.fillMaxSize().testTag(SplashScreenTag)) {
        AmbientBackdrop()

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = ttSpacing.xxxl)
                // `widthIn`, not `fillMaxWidth`: the original caps the stack at 480pt so the
                // mark and the title stay a readable pair on a tablet instead of drifting
                // to opposite edges of a very wide column.
                .widthIn(max = SPLASH_STACK_MAX_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ttSpacing.splashStack),
        ) {
            AppMark(
                modifier = Modifier
                    .scale(markScale)
                    .alpha(contentAlpha)
                    .testTag(SplashMarkTag),
            )

            Column(
                modifier = Modifier.alpha(contentAlpha).offset(y = contentOffset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                // The tag is on the title rather than on the container, and the reason is the
                // original's: the splash's own box is not an accessibility element, so an
                // identifier on it would resolve to nothing and read as a launch screen that
                // never appeared.
                ttText(
                    text = stringResource(Res.string.splash_title),
                    style = ttTypography.largeTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(SplashTitleTag),
                )
                ttText(
                    text = stringResource(Res.string.splash_tagline),
                    style = ttTypography.subheadline,
                    color = ttLabelSecondary(),
                    textAlign = TextAlign.Center,
                )
            }

            SplashSpinner(isWorking = isWorking)
        }
    }
}

@Composable
private fun SplashSpinner(isWorking: Boolean) {
    var showsProgress by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (showsProgress) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_SPINNER_FADE_MILLIS),
        label = "splashSpinnerAlpha",
    )

    LaunchedEffect(isWorking) {
        showsProgress = false
        if (!isWorking) return@LaunchedEffect
        delay(SPLASH_SPINNER_DELAY_MILLIS)
        showsProgress = true
    }

    val rotation by rememberInfiniteTransition(label = "splashSpinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPLASH_SPINNER_PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )
    val accent = ttAccent()
    Box(
        modifier = Modifier
            .alpha(alpha)
            .padding(top = SPLASH_SPINNER_TOP_PADDING)
            .size(SPLASH_SPINNER_SIZE)
            .testTag(SplashSpinnerTag),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = SPLASH_SPINNER_STROKE.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            drawArc(
                color = accent,
                startAngle = rotation,
                sweepAngle = SPLASH_SPINNER_SWEEP_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}


// MARK: - Tag

const val SplashScreenTag = "shell.splash"
const val SplashTitleTag = "splash.title"
const val SplashSpinnerTag = "splash.spinner"
const val SplashMarkTag = "splash.mark"

// MARK: - Numbers, named and in one place

private const val SPLASH_MARK_ENTRANCE_SCALE = 0.86f
private const val SPLASH_MARK_ENTRANCE_MILLIS = 600

private const val SPLASH_CONTENT_ENTRANCE_MILLIS = 450
private const val SPLASH_CONTENT_ENTRANCE_DELAY_MILLIS = 120

private val SPLASH_CONTENT_ENTRANCE_OFFSET = 10.dp

internal const val SPLASH_MINIMUM_HOLD_MILLIS = 700L

internal const val SPLASH_SPINNER_DELAY_MILLIS = 900L
private const val SPLASH_SPINNER_FADE_MILLIS = 250
private const val SPLASH_SPINNER_PERIOD_MILLIS = 900
private const val SPLASH_SPINNER_SWEEP_DEGREES = 280f
private val SPLASH_SPINNER_STROKE = 2.dp
private val SPLASH_SPINNER_SIZE = 24.dp
private val SPLASH_SPINNER_TOP_PADDING = 8.dp

private val SPLASH_STACK_MAX_WIDTH = 480.dp

