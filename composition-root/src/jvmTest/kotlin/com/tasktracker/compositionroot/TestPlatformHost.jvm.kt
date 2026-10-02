package com.tasktracker.compositionroot

/**
 * The JVM needs no host.
 *
 * Not a limitation worked around: the JVM keychain and preferences backends take nothing, and
 * `DatabaseConfiguration.inMemory` builds without a context on every target but Android. [Unit]
 * is the value that says "a host was supplied and nothing reads it", which is what lets the
 * same `testBindings()` call serve the JVM and the Android host without each graph test having
 * to know which target it is running on.
 */
internal actual fun testPlatformHost(): Any = Unit
