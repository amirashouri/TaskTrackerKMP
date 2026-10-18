package com.tasktracker.featureprojectsdata

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Foundation.stringByDeletingLastPathComponent
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * iOS blob writes through `NSFileManager`.
 *
 * Atomic writes so an interrupted write cannot leave a half-blob the row then points
 * at: a missing file is a `Storage` failure at read time, while a *corrupt* file would
 * open as damaged content with no error anywhere.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun writeBlobFile(path: String, bytes: ByteArray) {
    val fileManager = NSFileManager.defaultManager
    val parent = (path as NSString).stringByDeletingLastPathComponent
    if (parent.isNotEmpty()) {
        fileManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
    }
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    } ?: throw IllegalStateException("Could not materialise ${bytes.size} bytes for $path.")
    if (!data.writeToFile(path, atomically = true)) {
        throw IllegalStateException("Could not write the blob at $path.")
    }
}

/** A missing file is not a failure: the write may never have happened, or a previous delete may have won. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteBlobFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}

/** `null` for a missing file, which is how "not cached here" reaches the caller. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun readBlobFile(path: String): ByteArray? {
    val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
    val size = data.length.toInt()
    if (size == 0) return ByteArray(0)
    // `getBytes` is absent under this Kotlin version's `NSData` projection — the same gap
    // `FilePicker.ios` and `AttachmentOpener.ios` document — so the read goes through `bytes`
    // and `memcpy`. This compiled as `getBytes` in no build until one was actually run for
    // iOS, which is how it survived: the module's iOS target had never been compiled.
    val source = data.bytes ?: return ByteArray(0)
    return ByteArray(size).also { out ->
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, size.toULong())
        }
    }
}
