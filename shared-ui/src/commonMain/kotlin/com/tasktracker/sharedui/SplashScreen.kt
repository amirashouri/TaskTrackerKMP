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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.AppMark
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.splash_tagline
import com.tasktracker.resources.splash_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The launch screen, held until the session has answered.
 *
 * ## It has two jobs, and the second is the one that is easy to miss
 *
 * This is the app's first composed frame, so it carries the mark and the name. And it is
 * what covers a session still being read, which the shell otherwise cannot tell apart from
 * "signed out": both are `Router.root == Auth`, so without this the app shows the sign-in
 * form to somebody who is already signed in and replaces it with their board a moment later.
 *
 * ## On the ambient backdrop, and that is load-bearing rather than decorative
 *
 * The sign-in screen sits on the same backdrop, so the cross-fade between the two moves the
 * mark and the form while the background stays perfectly still. A launch screen that painted
 * its own background would leave a visible seam at exactly the moment the app is trying to
 * look continuous. Both screens call [AmbientBackdrop] rather than one of them wrapping the
 * other, because the cross-fade has to move *the content*, and an ancestor that redraws
 * during the animation moves the background too.
 *
 * ## [isWorking] only drives the spinner
 *
 * The *hold* belongs to [RootScreen], which is the thing that knows both halves of the gate.
 * A second owner of the hold would be a second answer to "is the app ready", and the two
 * would disagree for exactly one case — a session that answers after the floor elapses.
 */
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

/**
 * The delayed progress indicator.
 *
 * Deliberately late. The keychain read this screen covers finishes in milliseconds, so a
 * spinner shown immediately would almost always be reporting work that is already done;
 * past [SPLASH_SPINNER_DELAY_MILLIS] it is genuinely still running, which is the case where
 * a stalled-looking screen would otherwise be the whole story.
 *
 * Kept in the hierarchy at zero alpha rather than removed, so its *arriving* cannot nudge the
 * mark and the title upwards — the original's note, and the reason this is alpha and not a
 * `when` around the composable.
 */
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
        // Only *when there is work to wait for*. The default is false, so a splash that
        // appears after the session has already answered never shows a spinner at all —
        // including for the first 900ms, which is why this is keyed on `isWorking` rather
        // than on the composition.
        if (!isWorking) return@LaunchedEffect
        delay(SPLASH_SPINNER_DELAY_MILLIS)
        showsProgress = true
    }

    // Drawn rather than imported: the design system ships no icon set, and
    // `CircularProgressIndicator` is a Material3 component this build deliberately does not
    // have.
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
                // A partial arc rather than a full ring: a complete circle reads as static,
                // which is the one thing a loading indicator must never do.
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

/** The splash as a whole, so a test can assert the launch screen is on screen at all. */
const val SplashScreenTag = "shell.splash"

/** The app name on the splash — the handle a test can find, per the original's rationale. */
const val SplashTitleTag = "splash.title"

/** The spinner, so a test can assert progress is *not* shown early and *is* shown late. */
const val SplashSpinnerTag = "splash.spinner"

/** The mark, so a test can assert the launch screen drew a brand rather than a blank frame. */
const val SplashMarkTag = "splash.mark"

// MARK: - Numbers, named and in one place

/** 0.86 — the mark's entrance scale. */
private const val SPLASH_MARK_ENTRANCE_SCALE = 0.86f
private const val SPLASH_MARK_ENTRANCE_MILLIS = 600

private const val SPLASH_CONTENT_ENTRANCE_MILLIS = 450
private const val SPLASH_CONTENT_ENTRANCE_DELAY_MILLIS = 120

/** 10dp — the stack's rise as it arrives. A distance, so dp rather than px. */
private val SPLASH_CONTENT_ENTRANCE_OFFSET = 10.dp

/** 700ms — how long the launch screen is held even once the session has answered. */
internal const val SPLASH_MINIMUM_HOLD_MILLIS = 700L

/** 900ms — before a spinner is believable. See [SplashSpinner]. */
internal const val SPLASH_SPINNER_DELAY_MILLIS = 900L
private const val SPLASH_SPINNER_FADE_MILLIS = 250
private const val SPLASH_SPINNER_PERIOD_MILLIS = 900
private const val SPLASH_SPINNER_SWEEP_DEGREES = 280f
private val SPLASH_SPINNER_STROKE = 2.dp
private val SPLASH_SPINNER_SIZE = 24.dp
private val SPLASH_SPINNER_TOP_PADDING = 8.dp

/** 480dp — the original's `.frame(maxWidth: 480)`. */
private val SPLASH_STACK_MAX_WIDTH = 480.dp

@Preview(name = "Splash, session still reading", showBackground = true)
@Composable
private fun SplashWorkingPreview() {
    ttTheme(ThemeScheme.System) {
        SplashScreen(isWorking = true)
    }
}

@Preview(name = "Splash, session answered", showBackground = true)
@Composable
private fun SplashSettledPreview() {
    ttTheme(ThemeScheme.System) {
        SplashScreen(isWorking = false)
    }
}
