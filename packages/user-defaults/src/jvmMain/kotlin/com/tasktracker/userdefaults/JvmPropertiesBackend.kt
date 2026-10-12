package com.tasktracker.userdefaults

import com.tasktracker.core.isDesktopApp
import java.io.File
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
public class JvmPropertiesBackend(
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

/**
 * Actual JVM preferences, selected on [isDesktopApp].
 *
 * Returns the file-backed backend on Desktop so device-scoped preferences survive a
 * restart, and the in-memory one on the JVM test host so a test that wrote to a file
 * would not leave state between runs.
 *
 * [host] is unused — the JVM backend does not need a platform hint the way the iOS
 * and Android ones do.
 */
public actual fun platformPreferences(host: Any?): PreferencesBackend {
    // The Desktop app uses the file-backed backend; the JVM test host uses the
    // in-memory one, because a test that wrote to a file would leave state between
    // runs and the JVM target exists to host the shared `commonTest` suite.
    return if (isDesktopApp) {
        FilePropertiesBackend(preferencesFile())
    } else {
        JvmPropertiesBackend()
    }
}
