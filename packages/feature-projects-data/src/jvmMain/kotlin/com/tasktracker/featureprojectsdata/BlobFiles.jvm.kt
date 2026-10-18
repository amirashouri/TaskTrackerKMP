package com.tasktracker.featureprojectsdata

import java.io.File

/**
 * JVM blob writes: plain `java.io` under a root the store resolved.
 *
 * Creating missing parents is what makes this work on a fresh install — see
 * `createParentDirectory`'s KDoc for why the store must not assume the directory exists.
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

/** `null` for a missing file, which is how "not cached here" reaches the caller. */
internal actual fun readBlobFile(path: String): ByteArray? {
    val file = File(path)
    return if (file.isFile) file.readBytes() else null
}
