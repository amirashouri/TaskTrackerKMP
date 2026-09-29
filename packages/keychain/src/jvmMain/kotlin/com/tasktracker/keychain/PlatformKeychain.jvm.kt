package com.tasktracker.keychain

/**
 * JVM has no system keychain.
 *
 * Returns an [InMemoryKeychain] rather than failing: the JVM target exists so the
 * shared `commonTest` suite has a host, and a test that reached for a real keychain on
 * the JVM would be testing the JDK, not TaskTracker. The one thing this gives up is the
 * guarantee that a credential survives a restart — which is exactly the guarantee
 * `InMemoryKeychain` is documented not to offer, and why production wiring lives in the
 * Android and iOS composition roots rather than here.
 *
 * [host] is unused.
 */
actual fun systemKeychain(host: Any?): KeychainAccess = InMemoryKeychain()
