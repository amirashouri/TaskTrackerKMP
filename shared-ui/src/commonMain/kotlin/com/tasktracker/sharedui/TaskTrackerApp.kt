package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.di.Resolver

/**
 * The Compose root shared by Android and the iOS `UIViewControllerRepresentable` shells.
 *
 * Both platforms call this same function, which is the point of `:shared-ui`: the screen tree,
 * its state and its navigation live in Kotlin, and the platform layer contributes only system
 * chrome — the window insets it *produces* — plus the entry point.
 *
 * ## It applies insets and nothing else
 *
 * `MainActivity` calls `enableEdgeToEdge()` and that is the whole of the platform's share: the
 * tree draws under the system bars, and this is what moves the content back below them. The
 * split is deliberate — a platform layer can only hand Compose the insets, and
 * `Modifier.windowInsetsPadding` is the same API on both platforms, so the padding belongs next
 * to the tree it protects rather than being written twice.
 *
 * `safeDrawing` rather than `systemBars`, because a camera cutout is *not* a system bar and on
 * the test device the cutout is the taller of the two: 156px against a 72px status bar. Asking
 * for `statusBars` would have left the content under the cutout anyway. Top only, because the
 * bottom inset is a separate question — a list should scroll *under* the gesture bar and only
 * its last row needs clearance, so the answer there is `contentPadding` on the scrolling
 * container, not padding on the root.
 *
 * On the root rather than per screen, because a per-screen inset is a per-screen bug waiting to
 * happen, and because `windowInsetsPadding` consumes: children see an inset already applied and
 * cannot double it.
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
 * What remains is the window's job: safe area, and the composable both platforms call.
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
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        RootScreen(
            resolver = resolver,
            splashMinimumHoldMillis = splashMinimumHoldMillis,
        )
    }
}