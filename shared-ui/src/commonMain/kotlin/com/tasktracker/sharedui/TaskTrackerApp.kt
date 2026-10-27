package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.di.Resolver

/**
 * Shared Compose application entry point called by Android and iOS host shells.
 *
 * The shell's motion inputs are installed here, above every screen rather than inside one: the
 * platform's Reduce Motion preference is one answer for the whole app, and the device-motion
 * controller must be one sensor instance for every glass surface the app puts on screen. See
 * [ShellMotionProvider] for why the shell is the thing that reads them.
 */
@Composable
fun TaskTrackerApp(
    resolver: Resolver,
    modifier: Modifier = Modifier,
    /** Minimum splash duration; configurable for testing. */
    splashMinimumHoldMillis: Long = SPLASH_MINIMUM_HOLD_MILLIS,
    /** Deep link source stream for pairing and navigation routes. */
    deepLinks: DeepLinkSource = appDeepLinks,
) {
    ShellMotionProvider {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            RootScreen(
                resolver = resolver,
                splashMinimumHoldMillis = splashMinimumHoldMillis,
                deepLinks = deepLinks,
            )
        }
    }
}
