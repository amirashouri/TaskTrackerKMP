package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.database.applicationSupportDirectory
import com.tasktracker.database.createParentDirectory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where an attachment's bytes live, and how the row finds them again.
 *
 * The store is relative by construction: rows persist only the file name, and every
 * operation joins it against the attachments root at call time. An absolute path in a
 * row would survive a reinstall that moves the app's container, and then point at a
 * directory that no longer exists — which is why this class refuses to produce one.
 * The name is the attachment's id, never the picked file name: a name is display text
 * and may contain separators, duplicates, or anything else a path must not.
 */
interface AttachmentBlobStore {

    /** Writes the blob, returning the relative name the row stores. */
    fun write(id: AttachmentID, bytes: ByteArray): String

    /**
     * Reads a blob, or `null` when it is not on this device.
     *
     * A missing file is a normal answer rather than a failure: the row can arrive from the
     * server before its bytes do, and a re-install starts with rows and no files. The
     * caller decides whether to download.
     */
    fun read(relativeName: String): ByteArray?

    /**
     * Removes a blob, best-effort: a leftover file costs disk space, while a failed
     * delete that threw would have stopped a teardown that already committed its rows.
     */
    fun delete(relativeName: String)
}

/**
 * The on-disk store, under the app's support directory.
 *
 * The root is resolved per call rather than cached, because on Android the location is
 * derived from the host `Context` and the class must never pin a shorter-lived context
 * than the application.
 */
class FileBlobStore(
    private val host: Any? = null,
) : AttachmentBlobStore {

    private fun rootDirectory(): String = applicationSupportDirectory(host) + "/attachments"

    override fun write(id: AttachmentID, bytes: ByteArray): String {
        val name = "${id.stringValue}.blob"
        val path = "${rootDirectory()}/$name"
        try {
            createParentDirectory(path)
            writeBlobFile(path, bytes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw AppError.Storage("Could not store the attachment. ${failure.message.orEmpty()}")
        }
        return name
    }

    override fun read(relativeName: String): ByteArray? {
        // The same traversal guard as `delete`: a row holding a separator or `..` is a
        // corrupted row, and reading outside the root would turn a data bug into a
        // file-system one.
        val name = relativeName.substringAfterLast('/')
        if (name.isBlank() || name == "." || name == ".." || name.contains("..")) return null
        return try {
            readBlobFile("${rootDirectory()}/$name")
        } catch (_: Exception) {
            // A read that failed is indistinguishable from a missing file to this API, and
            // both mean "not cached here" — which the caller answers by downloading.
            null
        }
    }

    override fun delete(relativeName: String) {
        // Names are this store's own output; a row holding anything else (a separator, a
        // traversal) is a corrupted row, and deleting outside the root would turn a data
        // bug into a file-system one.
        val name = relativeName.substringAfterLast('/')
        if (name.isBlank() || name == "." || name == ".." || name.contains("..")) return
        try {
            deleteBlobFile("${rootDirectory()}/$name")
        } catch (_: Exception) {
            // Best-effort by contract: the row is already gone.
        }
    }
}

/** Writes the bytes, throwing on failure. The repository maps the failure to storage. */
internal expect fun writeBlobFile(path: String, bytes: ByteArray)

/** Reads the file, or `null` when it is absent. Never throws. */
internal expect fun readBlobFile(path: String): ByteArray?

/** Removes the file if present; never throws. */
internal expect fun deleteBlobFile(path: String)
