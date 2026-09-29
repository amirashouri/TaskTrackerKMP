package com.tasktracker.userdefaults

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [PreferencesBackend] for tests and previews.
 *
 * The original got test isolation from a unique `UserDefaults` suite name per test and
 * had to clean the domain up afterwards; two stores sharing one instance here is both
 * simpler and more precise, because the test *chooses* the isolation rather than
 * deriving it from a UUID it then has to remember to tear down.
 */
class InMemoryPreferences(
    seed: Map<String, ByteArray> = emptyMap(),
) : PreferencesBackend {

    private val mutex = Mutex()
    private val storage = LinkedHashMap<String, ByteArray>(seed)

    override suspend fun bytes(key: String): ByteArray? = mutex.withLock {
        storage[key]?.copyOf()
    }

    override suspend fun put(bytes: ByteArray, key: String): Unit = mutex.withLock {
        storage[key] = bytes.copyOf()
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        storage.remove(key)
    }

    override suspend fun contains(key: String): Boolean = mutex.withLock {
        storage.containsKey(key)
    }

    /** Sorted key list, for asserting exactly what a store wrote. */
    suspend fun keys(): List<String> = mutex.withLock { storage.keys.sorted() }
}
