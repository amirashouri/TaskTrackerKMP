package com.tasktracker.sharedui

import androidx.compose.runtime.Composable

/**
 * The platform's Reduce Transparency preference, as this host reports it.
 *
 * The counterpart to `platformReducedMotion()` in [ShellMotion.kt], and deliberately in the
 * shell rather than `:packages:design-system`: a design system that reads the OS on its own
 * cannot be rendered by a preview, a screenshot test, or a second app with a different
 * opinion. The shell asks, and the root passes the answer down as an intensity.
 *
 * ## Asked once per composition, not observed
 *
 * Like Reduce Motion, this is read when the root composes rather than subscribed to. iOS
 * exposes the value as a function and posts
 * `UIAccessibilityReduceTransparencyStatusDidChange` for observers, but a shell that has to
 * change one float does not need a notification: the switch is flipped in Settings, which
 * backgrounds the app, and the next foreground composition reads the new answer. A user who
 * changes it mid-session keeps the old answer until then, which is the same behaviour Reduce
 * Motion already has and is documented rather than hidden.
 */
@Composable
internal expect fun platformReduceTransparency(): Boolean

/**
 * What the glass material should actually be given.
 *
 * The OS switch **wins over** the slider but never writes to it: [stored] is the user's
 * persisted value, so turning the switch back off restores their previous intensity without
 * a migration step. `0f` is the material's own opaque end — `ttTheme` already clamps it, and
 * `GlassIntensityRenderTest` renders it — so there is no second notion of "off" here.
 *
 * The choice about the settings readout is to keep reporting the user's stored percentage.
 * The slider still says 60% while the OS forces opaque glass, and moving it still changes the
 * stored value that returns when the switch is off. Reporting "Reduced" instead would need
 * the flag threaded into a presentation module that does not otherwise know about the OS, and
 * would make the control look broken when its value silently refuses to move.
 */
internal fun effectiveGlassIntensity(stored: Float, reduceTransparency: Boolean): Float =
    if (reduceTransparency) 0f else stored
