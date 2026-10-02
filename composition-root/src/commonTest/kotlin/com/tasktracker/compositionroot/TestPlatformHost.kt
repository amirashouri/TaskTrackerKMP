package com.tasktracker.compositionroot

import com.tasktracker.database.DatabaseConfiguration
import com.tasktracker.keychain.InMemoryKeychain
import com.tasktracker.keychain.KeychainAccess
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.PreferencesBackend

/**
 * The platform host a graph test can hand to [PlatformBindings] on this target, or a value
 * meaning "this target needs none".
 *
 * ## Why this is an `expect` in a test source set
 *
 * [PlatformBindings.database] defaults to [DatabaseConfiguration.applicationDefault], and the
 * in-memory configuration still needs a host on Android: Room's `inMemoryDatabaseBuilder` takes
 * a `Context` and refuses to open without one, on the *test* host as much as on a device. A
 * graph test that resolves `SessionCache`, `OutboxQueue` or `SyncMetadataStore` therefore
 * cannot build a database on `testAndroidHostTest` — and it can be read, incorrectly, as "the
 * graph is broken on Android".
 *
 * The fix cannot be "annotate the test with Robolectric", because these assertions live in
 * `commonTest` and a runner annotation is not expressible in a common source set. It cannot be
 * "return a fake Context", either: the test needs a real one, and inventing one would be a
 * second, worse kind of substitution. So the host is asked of the platform, and the honest
 * answer is sometimes "there is none" — see the `androidHostTest` actual.
 */
internal expect fun testPlatformHost(): Any

/**
 * [PlatformBindings] with every backend this target can actually provide.
 *
 * Built in one place so no test assembles the substitution itself, and so the target's host
 * cannot be forgotten by a test added later. `startServices` is a parameter because it is a
 * claim about loops: a test that makes no claim about them should still be able to say so,
 * and one that resolves the looping services should not have to.
 *
 * `keychain` and `preferences` are parameters for the opposite reason. A relaunch test needs
 * *the same* in-memory stores in two graphs — fresh ones would assert that a session survives
 * nothing at all, and would pass, which is why those tests pass them explicitly.
 */
internal fun testBindings(
    startServices: Boolean = false,
    keychain: KeychainAccess = InMemoryKeychain(),
    preferences: PreferencesBackend = InMemoryPreferences(),
): PlatformBindings = PlatformBindings(
    host = testPlatformHost(),
    keychain = keychain,
    preferences = preferences,
    database = DatabaseConfiguration.inMemory,
    startServices = startServices,
)
