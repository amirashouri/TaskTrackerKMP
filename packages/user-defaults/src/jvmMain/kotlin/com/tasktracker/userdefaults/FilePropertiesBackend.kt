package com.tasktracker.userdefaults

import com.tasktracker.core.AppError
import java.io.File
import java.util.Properties
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JVM preferences, backed by a properties file under the app-support directory.
 *
 * Replaces the in-memory [JvmPropertiesBackend] on Desktop so that device-scoped
 * preferences — the appearance scheme and the last-visited project — survive a
 * restart. The file lives in the same directory the database uses
 * (`~/Library/Application Support/TaskTracker/` on macOS; `%APPDATA%/TaskTracker`
 * elsewhere), so the app owns one preferences directory.
 *
 * ## Why file, not in-memory
 *
 * The in-memory backend is faithful for the JVM *test host*, where a file would
 * leave state between test runs and a throwaway suite is the cleaner choice. On
 * Desktop that choice is wrong: a restart must not lose the appearance or the
 * last-visited project, because both are properties of the device, not of the
 * session, and sign-out deliberately preserves them.
 *
 * ## What this is not
 *
 * Not a keychain. Credentials stay in [com.tasktracker.keychain], which on JVM
 * is still the in-memory stub by design. This file holds only non-sensitive
 * UI/device preferences, the same shape the iOS `NSUserDefaultsBackend` and the
 * Android `SharedPreferencesBackend` hold.
 *
 * ## Failure mode
 *
 * A preferences read that fails opens as [com.tasktracker.core.AppError.Storage]
 * and propagates up — the same shape a real keychain failure would take, but at
 * the preferences layer rather than the auth layer. A corrupt preference is an
 * app-level failure, not a reason to invent a default: the caller (the appearance
 * resolver) has its own fallback (`system`) and must not have two.
 */
public class FilePropertiesBackend(
    private val file: File,
) : PreferencesBackend {

    private val mutex = Mutex()

    private fun load(): Properties {
        if (!file.exists()) return Properties()
        return try {
            Properties().apply {
                file.inputStream().use { input -> load(input) }
            }
        } catch (failure: Throwable) {
            throw com.tasktracker.core.AppError.Storage(
                "Could not read the preferences file: ${failure.message}",
            )
        }
    }

    private fun store(properties: Properties) {
        file.parentFile?.let { parent ->
            // A parent that is not a directory is the one failure mode the original's test
            // used, and a properties file should fail here rather than surface as an opaque
            // read failure later.
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                throw com.tasktracker.core.AppError.Storage(
                    "Could not create the preferences directory: ${parent.path}",
                )
            }
        }
        try {
            file.outputStream().use { output -> properties.store(output, null) }
        } catch (failure: Throwable) {
            throw com.tasktracker.core.AppError.Storage(
                "Could not write the preferences file: ${failure.message}",
            )
        }
    }

    override suspend fun bytes(key: String): ByteArray? = mutex.withLock {
        val value = load().getProperty(key) ?: return null
        value.encodeToByteArray()
    }

    override suspend fun put(bytes: ByteArray, key: String): Unit = mutex.withLock {
        val properties = load()
        properties.setProperty(key, bytes.decodeToString())
        store(properties)
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        val properties = load()
        properties.remove(key)
        store(properties)
    }

    override suspend fun contains(key: String): Boolean = mutex.withLock {
        load().containsKey(key)
    }
}

/**
 * The preferences file for the Desktop app.
 *
 * Lives in the same directory the database uses, so the app owns one preferences
 * directory. The file name is fixed here rather than passed in so the app does not
 * have to know it.
 *
 * The directory lookup is owned here rather than delegated to `:packages:database`,
 * because `user-defaults` is a cross-cutting module that deliberately sits below it
 * in the architecture graph and so cannot depend on it. The shape is the same one
 * the database module uses on JVM — `user.home` plus an `%APPDATA%` override —
 * reimplemented because `java.io` has no Foundation equivalent.
 */
/**
 * The preferences file for the Desktop app.
 *
 * Public so the Desktop shell can read the same path the backend writes without
 * reaching across the architecture boundary into `:packages:database`. The directory
 * lookup itself stays private to this module — `user-defaults` owns the JVM app-support
 * directory locally for exactly that reason.
 */
public fun preferencesFile(): File {
    val parent = applicationSupportDirectory()
    return File(parent, "preferences.properties")
}

/**
 * The app's on-disk store location on JVM.
 *
 * Mirrors the JVM actual in `:packages:database` so the database and the preferences
 * file share the same directory without `user-defaults` needing to depend on it.
 *
 * `$HOME/Library/Application Support/TaskTracker` on macOS; `%APPDATA%/TaskTracker`
 * elsewhere.
 */
private fun applicationSupportDirectory(): String {
    val home = System.getProperty("user.home")
        ?: throw AppError.Storage("Could not locate a home directory for the preferences file.")
    val appData = System.getenv("APPDATA")
    return if (appData != null) "$appData/TaskTracker" else "$home/Library/Application Support"
}
