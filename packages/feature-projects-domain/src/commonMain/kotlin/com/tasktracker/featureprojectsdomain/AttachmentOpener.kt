package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import kotlin.coroutines.cancellation.CancellationException

/**
 * Platform file opener for viewing attachment bytes.
 */
interface AttachmentOpener {

    @Throws(AppError::class, CancellationException::class)
    suspend fun open(fileName: String, mimeType: String?, bytes: ByteArray)
}

expect fun createAttachmentOpener(host: Any? = null): AttachmentOpener

internal fun handOffFileName(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
    if (base.isEmpty() || base == "." || base == ".." || base.contains("..")) return "attachment"
    return base
}
