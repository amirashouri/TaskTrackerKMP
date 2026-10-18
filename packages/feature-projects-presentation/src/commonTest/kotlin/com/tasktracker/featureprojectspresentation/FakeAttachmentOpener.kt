package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.featureprojectsdomain.AttachmentOpener

/**
 * An opener that records what it was handed instead of handing it anywhere.
 *
 * [opens] holds one entry per call, so a test can assert on the *name and type* the row
 * carried as well as on the bytes — the two are what the platform uses to pick an app, and a
 * row that opened the right bytes under the wrong name would pass a bytes-only assertion.
 *
 * [failure] is thrown from every open, which is how "the press failed" is separated from
 * "there was nothing to press": a `Validation` stands in for the platform's own "no app can
 * open this", and the model must surface it rather than swallow it.
 */
class FakeAttachmentOpener(
    var failure: AppError? = null,
) : AttachmentOpener {

    /** Every open, in order. */
    val opens = mutableListOf<Open>()

    /**
     * Runs inside [open], before [failure] is thrown.
     *
     * Suspending on purpose: it is how a test observes the model *mid-open* — the re-entrancy
     * guard can only be exercised from inside the call it is guarding, and a non-suspending
     * hook would have nothing to call.
     */
    var onOpen: (suspend () -> Unit)? = null

    data class Open(val fileName: String, val mimeType: String?, val bytes: ByteArray)

    override suspend fun open(fileName: String, mimeType: String?, bytes: ByteArray) {
        opens += Open(fileName, mimeType, bytes)
        onOpen?.invoke()
        failure?.let { throw it }
    }
}
