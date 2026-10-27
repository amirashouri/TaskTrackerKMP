package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.di.Resolver

/**
 * Shared Compose application entry point called by Android and iOS host shells.
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