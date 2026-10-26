package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.database.applicationSupportDirectory
import com.tasktracker.database.createParentDirectory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Storage port for attachment binary blobs.
 */
interface AttachmentBlobStore {
    fun write(id: AttachmentID, bytes: ByteArray): String
    fun read(relativeName: String): ByteArray?
    fun delete(relativeName: String)
}

/**
 * On-disk attachment blob store under application support directory.
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
        val name = relativeName.substringAfterLast('/')
        if (name.isBlank() || name == "." || name == ".." || name.contains("..")) return null
        return try {
            readBlobFile("${rootDirectory()}/$name")
        } catch (_: Exception) {
            null
        }
    }

    override fun delete(relativeName: String) {
        val name = relativeName.substringAfterLast('/')
        if (name.isBlank() || name == "." || name == ".." || name.contains("..")) return
        try {
            deleteBlobFile("${rootDirectory()}/$name")
        } catch (_: Exception) {
        }
    }
}

internal expect fun writeBlobFile(path: String, bytes: ByteArray)
internal expect fun readBlobFile(path: String): ByteArray?
internal expect fun deleteBlobFile(path: String)
