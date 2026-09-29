package com.tasktracker.keychain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory stand-in for the real keychain.
 *
 * Also supports fault injection, because the interesting auth paths are the ones where
 * the keychain *fails* — device locked, entitlement missing, disk error — and those are
 * exactly the paths a real keychain makes awkward to test.
 *
 * Guarded by a [Mutex] rather than leaning on the caller: the [KeychainAccess]
 * contract is `suspend`, so two coroutines on different threads can be inside
 * [data] at once, and a plain `MutableMap` would be a data race that only shows up on
 * a device. Swift's `actor` gave this for free; the lock is the equivalent.
 */
class InMemoryKeychain(seed: Map<String, ByteArray> = emptyMap()) : KeychainAccess {

    private val mutex = Mutex()
    private val storage = LinkedHashMap<String, ByteArray>(seed)
    private var failure: Throwable? = null

    /** Makes every subsequent operation throw, to exercise error handling. */
    suspend fun failWith(error: Throwable) = mutex.withLock { failure = error }

    suspend fun stopFailing() = mutex.withLock { failure = null }

    /** Sorted, so a test can assert the exact set of keys the store wrote. */
    suspend fun keys(): List<String> = mutex.withLock { storage.keys.sorted() }

    override suspend fun data(key: String): ByteArray? = mutex.withLock {
        failure?.let { throw it }
        storage[key]?.copyOf()
    }

    override suspend fun set(data: ByteArray, key: String): Unit = mutex.withLock {
        failure?.let { throw it }
        storage[key] = data.copyOf()
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        failure?.let { throw it }
        storage.remove(key)
    }

    override suspend fun removeAll(): Unit = mutex.withLock {
        failure?.let { throw it }
        storage.clear()
    }
}
