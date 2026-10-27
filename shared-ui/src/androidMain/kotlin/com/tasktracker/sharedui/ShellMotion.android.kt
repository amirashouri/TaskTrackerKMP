package com.tasktracker.sharedui

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable

/**
 * Android's answer: animators are off.
 *
 * `ValueAnimator.areAnimatorsEnabled()` is false when the animator duration scale is zero, which
 * is what Accessibility's "Remove animations" and the Developer-options scale both set — the
 * platform's own switch for applications that animate. Reading that rather than a preference of
 * this app's means the material is only ever stilled when the platform has already told every
 * other animation in the process to stop, which is the condition the setting is about.
 *
 * The call needs no `Context` and no permission, and API 26 is both its floor and this project's
 * `minSdk`, so every host that can run the shell has it.
 */
@Composable
actual fun platformReducedMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()
