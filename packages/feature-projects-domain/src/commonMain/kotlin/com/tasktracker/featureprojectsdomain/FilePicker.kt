package com.tasktracker.featureprojectsdomain

/**
 * File picked by the user, including metadata and bytes.
 */
class PickedFile(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        return fileName == other.fileName && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String = "PickedFile(fileName=$fileName, mimeType=$mimeType, ${bytes.size} bytes)"
}

/**
 * Platform file picker port.
 */
interface FilePicker {
    suspend fun pick(): PickedFile?
}

expect fun createFilePicker(host: Any? = null): FilePicker

internal fun guessMimeType(fileName: String): String? {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt", "text" -> "text/plain"
        "md" -> "text/markdown"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "zip" -> "application/zip"
        else -> null
    }
}
