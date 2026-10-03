package com.tasktracker.sharedui

/**
 * False, and the reason is this host's stub `android.jar` rather than anything about the code
 * under test — Compose's `RobolectricIdlingStrategy` dereferences `Build.FINGERPRINT`, which is
 * null here, and `runComposeUiTest` dies in its own setup before a test body can take a pixel.
 * The full account is on the common declaration.
 */
internal actual fun thisHostRasterisesText(): Boolean = false
