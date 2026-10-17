package com.tasktracker.sharedui

/**
 * The iOS simulator rasterises for real, which is why these measurements are worth running on
 * two hosts rather than one — it is the host that disagrees with the JVM about glyph coverage.
 * See the common declaration for why the Android host answers differently.
 */
internal actual fun thisHostRasterisesText(): Boolean = true

/** No-op: this host rasterises, so there is nothing to skip. */
internal actual fun assumeHostRasterisesText() = Unit
