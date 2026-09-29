package com.tasktracker.keychain

/**
 * The platform's real secret store.
 *
 * An `expect` factory rather than an `expect interface`, so the compiler proves every
 * target can actually produce a keychain — an interface alone would let a platform
 * silently ship no implementation and fail only on the first token write.
 *
 * [host] carries the `Context` Android needs to reach `SharedPreferences` and the
 * `KeyStore`, and is ignored by the iOS and JVM implementations. It is `Any?` because
 * common code cannot name `Context`; the Android actual asserts the type and fails
 * loudly with a message naming the fix rather than a bare `ClassCastException`.
 */
expect fun systemKeychain(host: Any? = null): KeychainAccess
