package com.tasktracker.featuresettingspresentation

internal actual fun thisHostRasterisesText(): Boolean = true

/** No-op: this host rasterises, so there is nothing to skip. */
internal actual fun assumeHostRasterisesText() = Unit