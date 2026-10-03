package com.tasktracker.sharedui

/**
 * The JVM renders through skiko against a real window surface, so a Compose frame here is a
 * frame a test can read pixels out of. See the common declaration for why the Android host
 * answers differently.
 */
internal actual fun thisHostRasterisesText(): Boolean = true
