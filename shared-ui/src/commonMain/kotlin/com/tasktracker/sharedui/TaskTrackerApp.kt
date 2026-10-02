package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.di.Resolver

/**
 * The Compose root shared by Android and the iOS `UIViewControllerRepresentable` shells.
 *
 * Both platforms call this same function, which is the point of `:shared-ui`: the screen tree,
 * its state and its navigation live in Kotlin, and the platform layer contributes only the
 * entry point. [RootScreen] paints the backdrop edge-to-edge and applies safe-area insets to
 * foreground content, so a pinned dark appearance also paints the area behind the notch dark.
 *
 * ## Everything else moved to [RootScreen]
 *
 * This used to resolve the session, subscribe to it, drive the router from it, apply a hardcoded
 * `ThemeScheme.System`, and draw the signed-in tree. All of that is [RootScreen]'s now, and the
 * reasons are worth keeping here because they are the reasons *this* function is allowed to be
 * this small:
 *
 *  - the session→router transition belongs to the graph's `SessionCoordinator`, so a screen
 *    cannot hold a second subscription to the transition that is tearing it down;
 *  - the appearance belongs to `ShellPreferences` and has to be applied above the splash that
 *    covers its own restore, which is inside the root rather than out here;
 *  - insets are a *window* property, and every screen inherits this one.
 *
 * What remains is the shared entry point both platforms call. Insets are applied inside
 * [RootScreen], after its full-window backdrop has been drawn.
 */
@Composable
fun TaskTrackerApp(
    resolver: Resolver,
    modifier: Modifier = Modifier,
    /**
     * The splash's minimum hold, forwarded to [RootScreen].
     *
     * A parameter rather than a constant so a test can hold for one frame. It exists on both
     * functions for the same reason and not because either of them needs it: production passes
     * nothing, and threading it through here means a test never has to reach past the entry
     * point into `RootScreen` to keep a suite of signed-in scenarios from paying 700ms each.
     */
    splashMinimumHoldMillis: Long = SPLASH_MINIMUM_HOLD_MILLIS,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        RootScreen(
            resolver = resolver,
            splashMinimumHoldMillis = splashMinimumHoldMillis,
        )
    }
}
