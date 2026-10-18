package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import kotlin.coroutines.cancellation.CancellationException

/**
 * The platform half of "open this file": hand the bytes to whichever app the user has for
 * that kind of file.
 *
 * ## Why bytes, and not the cached path
 *
 * The cache layout is the blob store's business and nothing else's — it refuses to persist an
 * absolute path precisely because a path outlives the app container that gave it meaning. A
 * port that took a path would force the domain to know where the cache lives, and would hand a
 * caller a string it cannot check. This is the mirror of [FilePicker]: there the platform owns
 * the file and the domain wants only the bytes, here the domain owns the bytes and the platform
 * wants a file. Each side hands over a copy and keeps its own storage rules.
 *
 * The copy is the price of that boundary, and it is bounded — [AttachFile.MAX_ATTACHMENT_BYTES]
 * caps what can be in hand.
 *
 * ## Why `suspend`
 *
 * Every platform's hand-off blocks at least partly: a scratch-file write on all three, and on
 * iOS a UIKit presentation that must run on the main queue. Suspending keeps that from being
 * the caller's problem, and it is why this is a coroutine seam rather than a plain function
 * like the blob store's `write`.
 */
interface AttachmentOpener {

    /**
     * Opens [bytes] as a file named [fileName].
     *
     * @param mimeType the type recorded at attach time, or `null` when the pick could not say.
     *   A hint, not a contract: each platform falls back to the extension in [fileName], so a
     *   `null` here costs a less specific choice rather than a failed open.
     * @throws AppError when nothing on the device can open the type, or the hand-off copy
     *   cannot be written. Both are failures of a press the user made, so neither may be
     *   swallowed into a silent no-op — "I tapped the file and nothing happened" is
     *   indistinguishable from a broken row.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun open(fileName: String, mimeType: String?, bytes: ByteArray)
}

/**
 * The platform's file opener.
 *
 * @param host Android's application `Context` — the hand-off needs a `FileProvider` and a
 *   `startActivity`, both of which hang off it. Ignored elsewhere, the same way
 *   [createFilePicker] ignores it outside Android.
 */
expect fun createAttachmentOpener(host: Any? = null): AttachmentOpener

/**
 * The name the hand-off copy is written under, made safe to be a file name.
 *
 * A picked name is display text and may hold a separator, a traversal, or nothing at all —
 * exactly the cases the blob store's own guards exist for, on the other side of the same
 * boundary. Each platform writes into a directory of its own, so a name carrying `..` would
 * escape it; the guard is here, once, rather than three times with one of them wrong.
 *
 * Over-strict on purpose: `report..txt` is rejected along with `..`. A file called
 * `attachment` is a worse outcome than a legitimate name losing two dots.
 */
internal fun handOffFileName(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
    if (base.isEmpty() || base == "." || base == ".." || base.contains("..")) return "attachment"
    return base
}
