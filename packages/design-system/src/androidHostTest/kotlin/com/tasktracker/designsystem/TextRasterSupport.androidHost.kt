package com.tasktracker.designsystem

/**
 * The Android host cannot host a Compose frame at all: its `android.jar` is a stub, Compose's
 * idling strategy dereferences a null `Build` field, and the tests in `commonTest` reach this
 * target without a Robolectric runner. See the common declaration for the whole of it.
 */
internal actual fun thisHostRasterisesText(): Boolean = false
