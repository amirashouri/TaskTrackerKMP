package com.tasktracker.featureprojectsdata

import java.io.File

/**
 * Android blob writes: `java.io` against the app-private files directory.
 *
 * The root comes from `applicationSupportDirectory(host)`, whose host is the application
 * `Context` — an Activity must never be pinned here, because the blob store outlives any
 * single screen by the length of the process.
 */
internal actual fun writeBlobFile(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
}

/** A missing file is not a failure: the write may never have happened, or a previous delete may have won. */
internal actual fun deleteBlobFile(path: String) {
    File(path).delete()
}
