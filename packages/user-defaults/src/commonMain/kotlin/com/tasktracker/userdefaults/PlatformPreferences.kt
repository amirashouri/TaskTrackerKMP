package com.tasktracker.userdefaults

/**
 * The platform's real preference store.
 *
 * An `expect` factory so the compiler proves each target can produce a backend; an
 * interface alone would let a platform ship no implementation and fail only at the
 * first write.
 *
 * [host] carries the `Context` Android needs to reach `SharedPreferences`, and is
 * ignored by the iOS and JVM implementations. It is `Any?` because common code cannot
 * name `Context`; the Android actual asserts the type and fails with a message naming
 * the fix.
 */
expect fun platformPreferences(host: Any? = null): PreferencesBackend
