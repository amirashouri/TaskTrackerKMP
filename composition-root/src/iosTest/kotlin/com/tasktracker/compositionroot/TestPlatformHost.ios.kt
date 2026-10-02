package com.tasktracker.compositionroot

/**
 * iOS needs no host, and [Unit] says so.
 *
 * Not a limitation worked around: the iOS keychain and preferences backends take nothing, and
 * `DatabaseConfiguration.inMemory` builds without a context on every target but Android. The
 * marker is never read — [PlatformBindings] only hands it to the backends, and on this target
 * they ignore it — so what matters is that a real one is passed rather than `null`, which
 * would be the value that means "a platform that needs one was not given it".
 */
internal actual fun testPlatformHost(): Any = Unit
