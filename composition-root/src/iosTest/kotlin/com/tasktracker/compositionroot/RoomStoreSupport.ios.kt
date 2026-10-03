package com.tasktracker.compositionroot

/**
 * The iOS simulator links `sqlite-bundled`'s native library into the test binary, so a store
 * opens here. See the common declaration for why the Android host answers differently.
 */
internal actual fun thisHostOpensRoomStores(): Boolean = true
