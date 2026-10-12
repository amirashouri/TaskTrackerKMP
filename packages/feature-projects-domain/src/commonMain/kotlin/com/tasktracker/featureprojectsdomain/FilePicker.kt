package com.tasktracker.featureprojectsdomain

/**
 * One file the user picked, as the platform handed it over.
 *
 * The bytes are read eagerly into memory rather than streamed: an attachment is capped
 * by [AttachFile]'s limit, so the worst case is bounded, and a port that returned a
 * handle would force every caller to know how to open a platform URL — which is exactly
 * the knowledge this seam exists to hide.
 */
class PickedFile(
    /** The name the file is shown under; never used as a path. */
    val fileName: String,
    /** Best-effort, from the platform; `null` when it could not say. */
    val mimeType: String?,
    val bytes: ByteArray,
) {
    // Not a `data class`: [bytes] is a `ByteArray`, whose generated equality compares by
    // identity — the same trap [com.tasktracker.database.OutboxEntry] documents. Two picks
    // of the same file are equal only here, explicitly.
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
 * The platform half of "attach a file": open the system picker, hand back the choice.
 *
 * The shape mirrors [com.tasktracker.featurenotificationsdomain.LocalNotifier] — a
 * minimal interface with an `expect` factory — because the same reasoning holds: the
 * domain decides *what* may be attached ([AttachFile]'s guards) and the platform decides
 * *how* a file is chosen, and neither can substitute for the other.
 *
 * The picker is resolved once per graph, like the other platform seams, rather than
 * created per call: Compose Multiplatform 1.12.1 exposes no `LocalContext` outside
 * androidMain, so a shared call site has nothing platform-specific to hand a factory.
 */
interface FilePicker {

    /**
     * Opens the system picker and suspends until the user chooses or cancels.
     *
     * @return the picked file, or `null` when the user cancelled. A platform failure is
     *   an [com.tasktracker.core.AppError], not a silent `null` — "I picked a file and
     *   nothing happened" must never be indistinguishable from "I pressed Cancel".
     */
    suspend fun pick(): PickedFile?
}

/**
 * The platform's file picker.
 *
 * @param host Android's application `Context` — the picker tracks the foreground
 *   activity through lifecycle callbacks, because the graph is started from the
 *   `Application` and an activity-result registration belongs to an Activity. Ignored
 *   elsewhere, the same way [com.tasktracker.featurenotificationsdomain.createLocalNotifier]
 *   ignores it outside Android.
 */
expect fun createFilePicker(host: Any? = null): FilePicker

/**
 * Best-effort mime type from a file name, `null` when the name says nothing.
 *
 * One shared table rather than three platform guesses: the value is stored as metadata
 * and nothing branches on it, so a wrong `null` is strictly better than three platforms
 * each inventing a different string for the same extension.
 */
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
