package com.tasktracker.database

import com.tasktracker.core.AppError
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * `Application Support`, the same directory the original resolved through
 * `URL.applicationSupportDirectory`. `NSSearchPathForDirectoriesInDomains` is the
 * Foundation call that name wraps, and the mask is what distinguishes it from Documents
 * and Caches — this is the directory a database belongs in, excluded from iCloud
 * backups only by the user's choice rather than by policy.
 *
 * `NSFileManager` is used instead of `createDirectoryAtPath` with attributes, which needs
 * an `NSDictionary` that Kotlin cannot construct without a toll-free bridge.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun applicationSupportDirectory(host: Any?): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        // NSApplicationSupportDirectory == 14. A literal, because the constant is not
        // exposed to Kotlin and a named one would only hide the magic number.
        14u,
        // NSUserDomainMask == 1
        1u,
        true,
    )
    // The elements are plain strings, not `NSURL`s, despite the Objective-C signature
    // promising `NSArray<NSURL *> *`. Casting to `NSURL` compiles, returns null for every
    // element, and fails at runtime as "could not locate the application support directory"
    // — which is exactly what it did the first time.
    val path = paths?.firstOrNull() as? String
    return path
        ?: throw AppError.Storage("Could not locate the application support directory.")
}

/** The parent directory of [path], creating it if it is missing. */
@OptIn(ExperimentalForeignApi::class)
actual fun createParentDirectory(path: String) {
    val manager = NSFileManager.defaultManager
    val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
    if (parent.isEmpty()) return
    if (manager.fileExistsAtPath(parent, isDirectory = null)) return
    // `createDirectoryAtPath` returns false rather than throwing when the directory
    // already exists, so the result is checked against the filesystem instead — the same
    // two-step the JVM and Android implementations need, for the same reason.
    if (!manager.createDirectoryAtPath(parent, withIntermediateDirectories = true, null, null) &&
        !manager.fileExistsAtPath(parent, isDirectory = null)
    ) {
        throw AppError.Storage("Could not create the database directory: $parent")
    }
}

/** Removes [path] and its write-ahead log. Absent is not an error; see the declaration. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteStoreFile(path: String) {
    val manager = NSFileManager.defaultManager
    manager.removeItemAtPath(path, null)
    manager.removeItemAtPath("$path-wal", null)
    manager.removeItemAtPath("$path-shm", null)
}
