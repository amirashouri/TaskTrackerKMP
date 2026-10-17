package com.tasktracker.designsystem

/**
 * The simulator renders through the same Skia-backed path the device does, and it is the host
 * whose rasterisation has caught real defects the JVM's did not. See the common declaration
 * for why the Android host answers differently.
 */
internal actual fun thisHostRasterisesText(): Boolean = true

/** No-op: this host rasterises, so there is nothing to skip. */
internal actual fun assumeHostRasterisesText() = Unit
