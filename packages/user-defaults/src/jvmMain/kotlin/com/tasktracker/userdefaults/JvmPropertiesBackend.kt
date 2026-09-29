package com.tasktracker.userdefaults

import java.util.Properties
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JVM preferences, backed by an in-memory [Properties].
 *
 * A file would be the more faithful choice, but the JVM target exists only to host the
 * shared `commonTest` suite, and a test that wrote to a properties file would leave
 * state between runs. The original's tests used a throwaway `UserDefaults` suite for
 * exactly this reason; in-memory is the same idea with less ceremony.
 *
 * [host] is unused.
 */
class JvmPropertiesBackend(
    private val properties: Properties = Properties(),
) : PreferencesBackend {

    private val mutex = Mutex()

    override suspend fun bytes(key: String): ByteArray? = mutex.withLock {
        properties.getProperty(key)?.let { it.encodeToByteArray() }
    }

    override suspend fun put(bytes: ByteArray, key: String): Unit = mutex.withLock {
        properties.setProperty(key, bytes.decodeToString())
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        properties.remove(key)
    }

    override suspend fun contains(key: String): Boolean = mutex.withLock {
        properties.containsKey(key)
    }
}

actual fun platformPreferences(host: Any?): PreferencesBackend = JvmPropertiesBackend()
