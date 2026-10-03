package com.tasktracker.compositionroot

/**
 * The JVM opens a real, file-or-memory Room store with `sqlite-bundled`'s native library
 * resolved from the host's own classpath. See the common declaration for why the Android host
 * answers differently.
 */
internal actual fun thisHostOpensRoomStores(): Boolean = true
