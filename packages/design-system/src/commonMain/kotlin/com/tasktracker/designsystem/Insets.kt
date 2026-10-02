package com.tasktracker.designsystem

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much of the bottom of the current screen the shell's floating tab bar covers.
 *
 * Published by `NavigationHost` and consumed by anything that has to stay *reachable* rather
 * than merely visible: a scroll range's end padding, a floating action's offset, a sheet's
 * bottom margin. It is the one number that makes "scrolls under the glass" and "the last row is
 * still tappable" the same layout rather than two conflicting ones.
 *
 * **It is a measurement, not a token.** `NavigationHost` reads the bar's own laid-out height with
 * `onSizeChanged`, because the height is not derivable: the bar carries `navigationBarsPadding()`,
 * so it grows by the home-indicator inset on a device and does not on a host without one, and its
 * labels scale with the font setting. A token would have to be wrong on at least one of those.
 * Measured on the JVM host (1024x768, density 1) the bar is **60px** tall and starts at y=708 —
 * a value no spacing scale contains.
 *
 * **The first composition reads `0.dp`**, because the bar has not been measured when its
 * provider is created. Every consumer therefore has to be laid out from its value rather than
 * baked into it; that is why each use is a `Modifier` read inside composition and not a value
 * captured in `remember`. The cost is one extra recomposition per consumer, once.
 *
 * **Content inset, not viewport inset.** A screen that applies this to its *root* stops short of
 * the glass, which is the pre-14.9 layout and defeats the point. It belongs in a scroll range's
 * `contentPadding`, which moves the end of the *content* under the bar while the viewport still
 * runs to the edge of the screen.
 *
 * Defaulted rather than required, so a screen rendered outside the signed-in shell — a preview,
 * `MissingProjectEnvironment`, the sign-in tree — reads `0.dp` and lays out exactly as it did
 * before the shell existed.
 */
val LocalTabBarScrollUnderInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }