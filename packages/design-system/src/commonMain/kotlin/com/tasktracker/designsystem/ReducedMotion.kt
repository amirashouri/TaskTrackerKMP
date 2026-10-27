package com.tasktracker.designsystem

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the UI should prefer reduced motion.
 *
 * Defaults to `false`, matching [LocalGlassIntensity]'s default-to-full pattern so that
 * existing tests and previews remain pixel-identical. `true` means *less motion*, not
 * *no animation* — transitions should snap to their targets rather than popping.
 *
 * Lives in composition so the app shell can read the platform Reduce Motion setting
 * (Android/iOS/system) and provide it at the root; this package does not auto-detect it.
 */
val LocalReducedMotion = compositionLocalOf { false }
